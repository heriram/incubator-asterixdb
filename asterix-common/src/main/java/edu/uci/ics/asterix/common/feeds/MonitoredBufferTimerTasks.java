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
package edu.uci.ics.asterix.common.feeds;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.uci.ics.asterix.common.feeds.api.IFeedManager;
import edu.uci.ics.asterix.common.feeds.api.IFeedRuntime.FeedRuntimeType;
import edu.uci.ics.asterix.common.feeds.api.IFeedRuntime.Mode;
import edu.uci.ics.asterix.common.feeds.api.IFrameEventCallback;
import edu.uci.ics.asterix.common.feeds.api.IFrameEventCallback.FrameEvent;
import edu.uci.ics.asterix.common.feeds.message.ScaleInReportMessage;
import edu.uci.ics.asterix.common.feeds.message.StorageReportFeedMessage;

public class MonitoredBufferTimerTasks {

    private static final Logger LOGGER = Logger.getLogger(MonitoredBufferDataFlowRateMeasureTimerTask.class.getName());

    public static class MonitoredBufferStorageTimerTask extends TimerTask {

        private final MonitoredBuffer mBuffer;
        private final IFeedManager feedManager;
        private final int partition;
        private final FeedConnectionId connectionId;
        private final FeedPolicyAccessor policyAccessor;
        private final StorageFrameHandler storageFromeHandler;

        private Map<Integer, Integer> maxIntakeBaseCovered;
        private int countDelayExceeded = 0;
        private final StorageReportFeedMessage storageReportMessage;
        private final FeedTupleCommitAckMessage tupleCommitAckMessage;

        private static final int PERSISTENCE_DELAY_VIOLATION_MAX = 5;

        public MonitoredBufferStorageTimerTask(MonitoredBuffer mBuffer, IFeedManager feedManager,
                FeedConnectionId connectionId, int partition, FeedPolicyAccessor policyAccessor,
                StorageFrameHandler storageFromeHandler) {
            this.mBuffer = mBuffer;
            this.feedManager = feedManager;
            this.connectionId = connectionId;
            this.partition = partition;
            this.policyAccessor = policyAccessor;
            this.storageFromeHandler = storageFromeHandler;
            this.storageReportMessage = new StorageReportFeedMessage(this.connectionId, this.partition, 0, false, 0, 0);
            this.tupleCommitAckMessage = new FeedTupleCommitAckMessage(this.connectionId, 0, 0, null);
            this.maxIntakeBaseCovered = new HashMap<Integer, Integer>();
        }

        @Override
        public void run() {
            Set<Integer> partitions = storageFromeHandler.getPartitionsWithStats();
            for (int intakePartition : partitions) {
                Map<Integer, IntakePartitionStatistics> baseAcks = storageFromeHandler
                        .getBaseAcksForPartition(intakePartition);
                for (Entry<Integer, IntakePartitionStatistics> entry : baseAcks.entrySet()) {
                    int base = entry.getKey();
                    IntakePartitionStatistics stats = entry.getValue();
                    if (maxIntakeBaseCovered.get(intakePartition) != null
                            && maxIntakeBaseCovered.get(intakePartition) < base) {
                        tupleCommitAckMessage.reset(intakePartition, base, stats.getAckInfo());
                        feedManager.getFeedMessageService().sendMessage(tupleCommitAckMessage);
                    }
                }
            }

            long avgDelayPersistence = storageFromeHandler.getAvgDelayPersistence();
            if (avgDelayPersistence > policyAccessor.getMaxDelayRecordPersistence()) {
                countDelayExceeded++;
                if (countDelayExceeded > PERSISTENCE_DELAY_VIOLATION_MAX) {
                    storageReportMessage.reset(0, false, mBuffer.getAvgDelayRecordPersistence());
                    feedManager.getFeedMessageService().sendMessage(storageReportMessage);
                }
            } else {
                countDelayExceeded = 0;
            }

        }

        public void receiveCommitAckResponse(FeedTupleCommitResponseMessage message) {
            System.out.println("Received " + message);
            maxIntakeBaseCovered.put(message.getIntakePartition(), message.getMaxWindowAcked());
        }
    }

    public static class MonitoredBufferDataFlowRateMeasureTimerTask extends TimerTask {

        private static final int PENDING_WORK_THRESHOLD = 10;

        private static final int MAX_SUCCESSIVE_THRESHOLD_PERIODS = 2;

        private final MonitoredBuffer mBuffer;
        private int pendingWorkExceedCount = 0;
        private final IFrameEventCallback callback;
        private FrameEvent lastEvent = FrameEvent.NO_OP;

        public MonitoredBufferDataFlowRateMeasureTimerTask(MonitoredBuffer mBuffer, IFrameEventCallback callback) {
            this.mBuffer = mBuffer;
            this.callback = callback;
        }

        @Override
        public void run() {
            int pendingWork = mBuffer.getWorkSize();
            if (mBuffer.getMode().equals(Mode.PROCESS_SPILL) || mBuffer.getMode().equals(Mode.PROCESS_BACKLOG)) {
                return;
            }

            if (LOGGER.isLoggable(Level.INFO)) {
                int outflowRate = mBuffer.getOutflowRate();
                int inflowRate = mBuffer.getInflowRate();
                LOGGER.info(mBuffer.getRuntimeId() + " " + "Inflow rate:" + inflowRate + " Outflow Rate:" + outflowRate
                        + " Pending Work " + pendingWork);

            }

            switch (lastEvent) {
                case NO_OP:
                case PENDING_WORK_DONE:
                case FINISHED_PROCESSING_SPILLAGE:
                    if (pendingWork > PENDING_WORK_THRESHOLD) {
                        pendingWorkExceedCount++;
                        if (pendingWorkExceedCount > MAX_SUCCESSIVE_THRESHOLD_PERIODS) {
                            pendingWorkExceedCount = 0;
                            lastEvent = FrameEvent.PENDING_WORK_THRESHOLD_REACHED;
                            callback.frameEvent(lastEvent);
                        }
                    }
                    break;
                case PENDING_WORK_THRESHOLD_REACHED:
                    if (pendingWork == 0) {
                        lastEvent = FrameEvent.PENDING_WORK_DONE;
                        callback.frameEvent(lastEvent);
                    }
                    break;
                case FINISHED_PROCESSING:
                    break;

            }
        }
    }

    /**
     * A timer task to measure and compare the processing rate and inflow rate
     * to look for possibility to scale-in, that is reduce the degree of cardinality
     * of the compute operator.
     */
    public static class MonitoredBufferProcessRateTimerTask extends TimerTask {

        private final MonitoredBuffer mBuffer;
        private final IFeedManager feedManager;
        private int nPartitions;
        private ScaleInReportMessage sMessage;
        private boolean proposedChange;

        public MonitoredBufferProcessRateTimerTask(MonitoredBuffer mBuffer, IFeedManager feedManager,
                FeedConnectionId connectionId, int nPartitions) {
            this.mBuffer = mBuffer;
            this.feedManager = feedManager;
            this.nPartitions = nPartitions;
            this.sMessage = new ScaleInReportMessage(connectionId, FeedRuntimeType.COMPUTE, 0, 0);
            this.proposedChange = false;
        }

        public int getNumberOfPartitions() {
            return nPartitions;
        }

        public void setNumberOfPartitions(int nPartitions) {
            this.nPartitions = nPartitions;
            proposedChange = false;
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Reset the number of partitions for " + mBuffer.getRuntimeId() + " to " + nPartitions);
            }
        }

        @Override
        public void run() {
            if (!proposedChange) {
                int inflowRate = mBuffer.getInflowRate();
                int procRate = mBuffer.getProcessingRate();
                if (inflowRate > 0 && procRate > 0) {
                    if (inflowRate < procRate) {
                        int possibleCardinality = (int) Math.ceil(nPartitions * inflowRate / (double) procRate);
                        if (possibleCardinality < nPartitions) {
                            sMessage.reset(nPartitions, possibleCardinality);
                            feedManager.getFeedMessageService().sendMessage(sMessage);
                            proposedChange = true;
                            if (LOGGER.isLoggable(Level.INFO)) {
                                LOGGER.info("Proposed scale-in " + sMessage);
                            }
                        }
                    } else {
                        if (LOGGER.isLoggable(Level.INFO)) {
                            LOGGER.info("Processing Rate exceeds Inflow Rate");
                        }
                    }
                }
            } else {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.warning("Waiting for earlier proposal to scale in to be applied");
                }
            }
        }
    }
}