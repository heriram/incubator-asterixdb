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
package edu.uci.ics.asterix.file;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;

import edu.uci.ics.asterix.bootstrap.FeedLifecycleListener;
import edu.uci.ics.asterix.common.exceptions.AsterixException;
import edu.uci.ics.asterix.common.feeds.FeedConnectionId;
import edu.uci.ics.asterix.common.feeds.FeedId;
import edu.uci.ics.asterix.common.feeds.IFeedLifecycleListener.SubscriptionLocation;
import edu.uci.ics.asterix.metadata.declared.AqlMetadataProvider;
import edu.uci.ics.asterix.metadata.entities.PrimaryFeed;
import edu.uci.ics.hyracks.algebricks.common.constraints.AlgebricksPartitionConstraint;
import edu.uci.ics.hyracks.algebricks.common.constraints.AlgebricksPartitionConstraintHelper;
import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.common.utils.Pair;
import edu.uci.ics.hyracks.api.dataflow.IOperatorDescriptor;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.dataflow.std.connectors.OneToOneConnectorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.misc.NullSinkOperatorDescriptor;

/**
 * Provides helper method(s) for creating JobSpec for operations on a feed.
 */
public class FeedOperations {

    private static Logger LOGGER = Logger.getLogger(FeedOperations.class.getName());

    /**
     * Builds the job spec for ingesting a (primary) feed from its external source via the feed adaptor.
     * 
     * @param primaryFeed
     * @param metadataProvider
     * @return
     * @throws Exception
     */
    public static JobSpecification buildFeedIntakeJobSpec(PrimaryFeed primaryFeed, AqlMetadataProvider metadataProvider)
            throws Exception {

        JobSpecification spec = JobSpecificationUtils.createJobSpecification();
        IOperatorDescriptor feedIngestor;
        AlgebricksPartitionConstraint ingesterPc;

        try {
            Pair<IOperatorDescriptor, AlgebricksPartitionConstraint> p = metadataProvider.buildFeedIntakeRuntime(spec,
                    primaryFeed);
            feedIngestor = p.first;
            ingesterPc = p.second;
        } catch (AlgebricksException e) {
            throw new AsterixException(e);
        }

        AlgebricksPartitionConstraintHelper.setPartitionConstraintInJobSpec(spec, feedIngestor, ingesterPc);

        NullSinkOperatorDescriptor nullSink = new NullSinkOperatorDescriptor(spec);
        AlgebricksPartitionConstraintHelper.setPartitionConstraintInJobSpec(spec, nullSink, ingesterPc);
        spec.connect(new OneToOneConnectorDescriptor(spec), feedIngestor, 0, nullSink, 0);
        spec.addRoot(nullSink);
        return spec;

    }

    /**
     * Builds the job spec for sending message to an active feed to disconnect it from the
     * its source.
     * 
     * @param dataverseName
     * @param feedName
     * @param datasetName
     * @param metadataProvider
     * @param feedActivity
     * @return
     * @throws AsterixException
     * @throws AlgebricksException
     */
    public static JobSpecification buildDisconnectFeedJobSpec(AqlMetadataProvider metadataProvider,
            FeedConnectionId feedConnectionId) throws AsterixException, AlgebricksException {

        JobSpecification spec = JobSpecificationUtils.createJobSpecification();
        IOperatorDescriptor feedMessenger;
        AlgebricksPartitionConstraint messengerPc;

        try {
            Pair<SubscriptionLocation, List<FeedConnectionId>> subscriptions = FeedLifecycleListener.INSTANCE
                    .getFeedSubscriptions(feedConnectionId.getFeedId());
            boolean dependentSubscribers = (subscriptions != null && subscriptions.second.size() > 0);
            if (!dependentSubscribers) {
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.info("Feed connection " + feedConnectionId
                            + " can be removed as there are no subscribers to the connection.");
                }
                String[] locations = null;
                Pair<FeedId, SubscriptionLocation> sourceFeedInfo = FeedLifecycleListener.INSTANCE
                        .getSourceFeedInfo(feedConnectionId);
                switch (sourceFeedInfo.second) {
                    case SOURCE_FEED_INTAKE:
                        locations = FeedLifecycleListener.INSTANCE.getIntakeLocations(sourceFeedInfo.first);
                        break;
                    case SOURCE_FEED_COMPUTE:
                        locations = FeedLifecycleListener.INSTANCE.getComputeLocations(sourceFeedInfo.first);
                        break;
                }
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.info("Feed disconnect message would be sent to " + StringUtils.join(locations, ','));
                }

                Pair<IOperatorDescriptor, AlgebricksPartitionConstraint> p = metadataProvider
                        .buildDisconnectFeedMessengerRuntime(spec, feedConnectionId.getFeedId().getDataverse(),
                                feedConnectionId.getFeedId().getFeedName(), feedConnectionId.getDatasetName(),
                                locations);
                feedMessenger = p.first;
                messengerPc = p.second;
            } else {
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.info("Feed connection " + feedConnectionId + " has dependent subscribers to the connection.");
                }
                throw new AsterixException("Disconnecting a feed with subscribers is not supported");
            }

        } catch (AlgebricksException e) {
            throw new AsterixException(e);
        }

        AlgebricksPartitionConstraintHelper.setPartitionConstraintInJobSpec(spec, feedMessenger, messengerPc);
        NullSinkOperatorDescriptor nullSink = new NullSinkOperatorDescriptor(spec);
        AlgebricksPartitionConstraintHelper.setPartitionConstraintInJobSpec(spec, nullSink, messengerPc);
        spec.connect(new OneToOneConnectorDescriptor(spec), feedMessenger, 0, nullSink, 0);
        spec.addRoot(nullSink);
        return spec;

    }
}
