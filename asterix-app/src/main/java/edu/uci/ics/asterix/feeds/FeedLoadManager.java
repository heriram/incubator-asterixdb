/*
 * Copyright 2009-2013 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.asterix.feeds;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONObject;

import edu.uci.ics.asterix.common.exceptions.AsterixException;
import edu.uci.ics.asterix.common.feeds.FeedCongestionMessage;
import edu.uci.ics.asterix.common.feeds.FeedConnectionId;
import edu.uci.ics.asterix.common.feeds.FeedJobInfo.FeedJobState;
import edu.uci.ics.asterix.common.feeds.FeedRuntimeId;
import edu.uci.ics.asterix.common.feeds.NodeLoadReport;
import edu.uci.ics.asterix.common.feeds.ScaleInReportMessage;
import edu.uci.ics.asterix.common.feeds.api.IFeedLoadManager;
import edu.uci.ics.asterix.common.feeds.api.IFeedRuntime.FeedRuntimeType;
import edu.uci.ics.asterix.file.FeedOperations;
import edu.uci.ics.asterix.metadata.feeds.FeedUtil;
import edu.uci.ics.asterix.metadata.feeds.PrepareStallMessage;
import edu.uci.ics.asterix.metadata.feeds.TerminateDataFlowMessage;
import edu.uci.ics.asterix.om.util.AsterixAppContextInfo;
import edu.uci.ics.hyracks.api.client.IHyracksClientConnection;
import edu.uci.ics.hyracks.api.job.JobId;
import edu.uci.ics.hyracks.api.job.JobSpecification;

public class FeedLoadManager implements IFeedLoadManager {

    private static final Logger LOGGER = Logger.getLogger(FeedLoadManager.class.getName());

    private final TreeSet<NodeLoadReport> nodeReports;

    public FeedLoadManager() {
        nodeReports = new TreeSet<NodeLoadReport>();
    }

    @Override
    public void submitNodeLoadReport(NodeLoadReport report) {
        nodeReports.remove(report);
        nodeReports.add(report);
    }

    @Override
    public void reportCongestion(FeedCongestionMessage message) throws AsterixException {
        FeedRuntimeId runtimeId = message.getRuntimeId();
        JobId jobId = FeedLifecycleListener.INSTANCE.getFeedCollectJobId(message.getConnectionId());
        FeedJobState jobState = FeedLifecycleListener.INSTANCE.getFeedJobState(jobId);
        if (jobState != null
                && (jobState.equals(FeedJobState.CONGESTION_REPORTED) || jobState.equals(FeedJobState.UNDER_RECOVERY))) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Ignoring congestion report from " + runtimeId + " as job is already under recovery");
            }
            return;
        } else {
            try {
                FeedLifecycleListener.INSTANCE.setJobState(jobId, FeedJobState.UNDER_RECOVERY);
                int inflowRate = message.getInflowRate();
                int outflowRate = message.getOutflowRate();
                List<String> currentComputeLocations = new ArrayList<String>();
                currentComputeLocations.addAll(FeedLifecycleListener.INSTANCE.getComputeLocations(message
                        .getConnectionId().getFeedId()));
                int computeCardinality = currentComputeLocations.size();
                int requiredCardinality = (int) Math
                        .ceil((double) ((computeCardinality * inflowRate) / (double) outflowRate)) + 1;
                int additionalComputeNodes = requiredCardinality - computeCardinality;
                List<String> helperComputeNodes = getNodeForSubstitution(additionalComputeNodes);

                // Step 1) Alter the original feed job to adjust the cardinality
                JobSpecification jobSpec = FeedLifecycleListener.INSTANCE.getCollectJobSpecification(message
                        .getConnectionId());
                helperComputeNodes.addAll(currentComputeLocations);
                List<String> newLocations = new ArrayList<String>();
                newLocations.addAll(currentComputeLocations);
                newLocations.addAll(helperComputeNodes);
                FeedUtil.increaseCardinality(jobSpec, FeedRuntimeType.COMPUTE, requiredCardinality, newLocations);

                // Step 2) send prepare to  stall message
                gracefullyTerminateDataFlow(message.getConnectionId(), Integer.MAX_VALUE);

                // Step 3) run the altered job specification 
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.info("New Job after adjusting to the workload " + jobSpec);
                }

                Thread.sleep(3000);
                runJob(jobSpec, false);

            } catch (Exception e) {
                e.printStackTrace();
                if (LOGGER.isLoggable(Level.SEVERE)) {
                    LOGGER.severe("Unable to form the required job for scaling in/out" + e.getMessage());
                }
                throw new AsterixException(e);
            }
        }
    }

    @Override
    public void submitScaleInPossibleReport(ScaleInReportMessage message) throws Exception {
        JobId jobId = FeedLifecycleListener.INSTANCE.getFeedCollectJobId(message.getConnectionId());
        FeedJobState jobState = FeedLifecycleListener.INSTANCE.getFeedJobState(jobId);
        if (jobState == null || (jobState.equals(FeedJobState.UNDER_RECOVERY))) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("JobState information for job " + "[" + jobId + "]" + " not found ");
            }
            return;
        } else {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Processing scale-in message " + message);
            }
            FeedLifecycleListener.INSTANCE.setJobState(jobId, FeedJobState.UNDER_RECOVERY);
            JobSpecification jobSpec = FeedLifecycleListener.INSTANCE.getCollectJobSpecification(message
                    .getConnectionId());
            int reducedCardinality = message.getReducedCardinaliy();
            List<String> currentComputeLocations = new ArrayList<String>();
            currentComputeLocations.addAll(FeedLifecycleListener.INSTANCE.getComputeLocations(message.getConnectionId()
                    .getFeedId()));
            FeedUtil.decreaseComputeCardinality(jobSpec, FeedRuntimeType.COMPUTE, reducedCardinality,
                    currentComputeLocations);

            gracefullyTerminateDataFlow(message.getConnectionId(), reducedCardinality - 1);
            Thread.sleep(3000);
            JobId newJobId = runJob(jobSpec, false);
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Launch modified job" + "[" + newJobId + "]" + "for scale-in \n" + jobSpec);
            }

        }
    }

    private void gracefullyTerminateDataFlow(FeedConnectionId connectionId, int computePartitionRetainLimit)
            throws Exception {
        // Step 1) send prepare to  stall message
        PrepareStallMessage stallMessage = new PrepareStallMessage(connectionId, computePartitionRetainLimit);
        String[] intakeLocations = FeedLifecycleListener.INSTANCE.getIntakeLocations(connectionId.getFeedId());
        List<String> computeLocations = FeedLifecycleListener.INSTANCE.getComputeLocations(connectionId.getFeedId());
        String[] storageLocations = FeedLifecycleListener.INSTANCE.getStoreLocations(connectionId);

        Set<String> operatorLocations = new HashSet<String>();
        for (String loc : intakeLocations) {
            operatorLocations.add(loc);
        }
        operatorLocations.addAll(computeLocations);
        for (String loc : storageLocations) {
            operatorLocations.add(loc);
        }

        JobSpecification messageJobSpec = FeedOperations.buildPrepareStallMessageJob(stallMessage,
                operatorLocations.toArray(new String[] {}));
        runJob(messageJobSpec, true);

        // Step 2)
        TerminateDataFlowMessage terminateMesg = new TerminateDataFlowMessage(connectionId);
        messageJobSpec = FeedOperations.buildTerminateFlowMessageJob(terminateMesg, intakeLocations);
        runJob(messageJobSpec, true);
    }

    private JobId runJob(JobSpecification spec, boolean waitForCompletion) throws Exception {
        IHyracksClientConnection hcc = AsterixAppContextInfo.getInstance().getHcc();
        JobId jobId = hcc.startJob(spec);
        if (waitForCompletion) {
            hcc.waitForCompletion(jobId);
        }
        return jobId;
    }

    @Override
    public void submitFeedRuntimeReport(JSONObject obj) {
        //TODO Implement this method
    }

    private List<String> getNodeForSubstitution(int nRequired) {
        List<String> nodeIds = new ArrayList<String>();
        Iterator<NodeLoadReport> it = null;
        int nAdded = 0;
        while (nAdded < nRequired) {
            it = nodeReports.iterator();
            while (it.hasNext()) {
                nodeIds.add(it.next().getNodeId());
                nAdded++;
            }
        }
        return nodeIds;
    }

}
