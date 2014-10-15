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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.uci.ics.asterix.common.feeds.api.IFeedManager;
import edu.uci.ics.asterix.common.feeds.api.IFeedOperatorOutputSideHandler;
import edu.uci.ics.asterix.common.feeds.api.IFeedOperatorOutputSideHandler.Type;
import edu.uci.ics.asterix.common.feeds.api.IFeedRuntime.FeedRuntimeType;
import edu.uci.ics.hyracks.api.comm.IFrameWriter;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAccessor;

/**
 * Provides mechanism for distributing the frames, as received from an operator to a
 * set of registered readers. Each reader typically operates at a different pace. Readers
 * are isolated from each other to ensure that a slow reader does not impact the progress of
 * others.
 **/
public class DistributeFeedFrameWriter implements IFrameWriter {

    private static final Logger LOGGER = Logger.getLogger(DistributeFeedFrameWriter.class.getName());

    /** A unique identifier for the feed to which the incoming tuples belong. **/
    private final FeedId feedId;

    /** An instance of FrameDistributor that provides the mechanism for distributing a frame to multiple readers, each operating in isolation. **/
    private final FrameDistributor frameDistributor;

    /** The original frame writer instantiated as part of job creation **/
    private IFrameWriter writer;

    /** The feed operation whose output is being distributed by the DistributeFeedFrameWriter **/
    private final FeedRuntimeType feedRuntimeType;

    /** The value of the partition 'i' if this is the i'th instance of the associated operator **/
    private final int partition;

    public DistributeFeedFrameWriter(FeedId feedId, IFrameWriter writer, FeedRuntimeType feedRuntimeType,
            int partition, FrameTupleAccessor fta, IFeedManager feedManager, int frameSize) throws IOException {
        this.feedId = feedId;
        this.frameDistributor = new FrameDistributor(feedId, feedRuntimeType, partition, true,
                feedManager.getFeedMemoryManager(), frameSize, fta);
        this.feedRuntimeType = feedRuntimeType;
        this.partition = partition;
        this.writer = writer;
    }

    public FeedFrameCollector subscribeFeed(FeedPolicyAccessor fpa, IFrameWriter frameWriter,
            FeedConnectionId connectionId) throws Exception {
        FeedFrameCollector collector = null;
        if (!frameDistributor.isRegistered(frameWriter)) {
            collector = new FeedFrameCollector(frameDistributor, fpa, frameWriter, connectionId);
            frameDistributor.registerFrameCollector(collector);
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Registered subscriber, new mode " + frameDistributor.getMode());
            }
            return collector;
        } else {
            throw new IllegalStateException("subscriber " + feedId + " already registered");
        }
    }

    public void unsubscribeFeed(IFrameWriter recipientFeedFrameWriter) throws Exception {
        boolean success = frameDistributor.deregisterFrameCollector(recipientFeedFrameWriter);
        if (!success) {
            throw new IllegalStateException("Invalid attempt to unregister FeedFrameWriter " + recipientFeedFrameWriter
                    + " not registered.");
        }
    }

    public void notifyEndOfFeed() {
        frameDistributor.notifyEndOfFeed();
    }

    @Override
    public void close() throws HyracksDataException {
        frameDistributor.close();
        writer.close();
    }

    @Override
    public void fail() throws HyracksDataException {
        writer.fail();
    }

    @Override
    public void nextFrame(ByteBuffer frame) throws HyracksDataException {
        frameDistributor.nextFrame(frame);
    }

    @Override
    public void open() throws HyracksDataException {
        writer.open();
    }

    public Map<IFrameWriter, FeedFrameCollector> getRegisteredReaders() {
        return frameDistributor.getRegisteredReaders();
    }

    public void setWriter(IFrameWriter writer) {
        this.writer = writer;
    }

    public Type getType() {
        return IFeedOperatorOutputSideHandler.Type.DISTRIBUTE_FEED_OUTPUT_HANDLER;
    }

    @Override
    public String toString() {
        return feedId.toString() + feedRuntimeType + "[" + partition + "]";
    }

    public FrameDistributor.DistributionMode getDistributionMode() {
        return frameDistributor.getDistributionMode();
    }
}
