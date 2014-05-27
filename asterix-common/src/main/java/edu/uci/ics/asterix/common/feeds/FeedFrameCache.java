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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.uci.ics.asterix.common.feeds.FeedConstants.StatisticsConstants;
import edu.uci.ics.hyracks.api.comm.IFrameWriter;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAccessor;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAppender;

public class FeedFrameCache extends MessageReceiver<ByteBuffer> {

    private final Map<Integer, ByteBuffer> orderedCache;
    private final FrameTupleAccessor tupleAccessor;
    private final IFrameWriter frameWriter;

    public FeedFrameCache(FrameTupleAccessor tupleAccessor, IFrameWriter frameWriter) {
        this.tupleAccessor = tupleAccessor;
        this.frameWriter = frameWriter;
        this.orderedCache = new LinkedHashMap<Integer, ByteBuffer>();
    }

    @Override
    public void processMessage(ByteBuffer frame) throws Exception {
        int lastRecordId = getLastRecordId(frame);
        ByteBuffer clone = cloneFrame(frame);
        orderedCache.put(lastRecordId, clone);
    }

    public void dropTillRecordId(int recordId) {
        List<Integer> recordIds = new ArrayList<Integer>();
        for (Entry<Integer, ByteBuffer> entry : orderedCache.entrySet()) {
            int recId = entry.getKey();
            if (recId <= recordId) {
                recordIds.add(recId);
            } else {
                break;
            }
        }
        for (Integer r : recordIds) {
            orderedCache.remove(r);
        }
    }

    public void replayRecords(int startingRecordId) throws HyracksDataException {
        boolean replayPositionReached = false;
        for (Entry<Integer, ByteBuffer> entry : orderedCache.entrySet()) {
            int maxRecordIdInFrame = entry.getKey();
            if (!replayPositionReached) {
                if (maxRecordIdInFrame > startingRecordId) {
                    replayFrame(startingRecordId, entry.getValue());
                    replayPositionReached = true;
                }
            } else {
                replayFrame(entry.getValue());
            }
        }
    }

    /**
     * Replay the frame from the tuple (inclusive) with recordId as specified.
     * 
     * @param recordId
     * @param frame
     * @throws HyracksDataException
     */
    private void replayFrame(int recordId, ByteBuffer frame) throws HyracksDataException {
        tupleAccessor.reset(frame);
        int nTuples = tupleAccessor.getTupleCount();
        for (int i = 0; i < nTuples; i++) {
            int rid = getRecordIdAtTupleIndex(i, frame);
            if (rid == recordId) {
                ByteBuffer slicedFrame = splitFrame(i, frame);
                replayFrame(slicedFrame);
                break;
            }
        }
    }

    private ByteBuffer splitFrame(int beginTupleIndex, ByteBuffer frame) {
        ByteBuffer slicedFrame = ByteBuffer.allocate(frame.capacity());
        FrameTupleAppender appender = new FrameTupleAppender(frame.capacity());
        appender.reset(slicedFrame, true);
        int totalTuples = tupleAccessor.getTupleCount();
        for (int ti = beginTupleIndex; ti < totalTuples; ti++) {
            appender.append(tupleAccessor, ti);
        }
        return slicedFrame;
    }

    /**
     * Replay the frame
     * 
     * @param frame
     * @throws HyracksDataException
     */
    private void replayFrame(ByteBuffer frame) throws HyracksDataException {
        frameWriter.nextFrame(frame);
    }

    private int getLastRecordId(ByteBuffer frame) {
        tupleAccessor.reset(frame);
        int nTuples = tupleAccessor.getTupleCount();
        return getRecordIdAtTupleIndex(nTuples - 1, frame);
    }

    private int getRecordIdAtTupleIndex(int tupleIndex, ByteBuffer frame) {
        tupleAccessor.reset(frame);
        int recordStart = tupleAccessor.getTupleStartOffset(tupleIndex) + tupleAccessor.getFieldSlotsLength();
        int openPartOffset = frame.getInt(recordStart + 6);
        int numOpenFields = frame.getInt(openPartOffset);
        int recordIdOffset = frame.getInt(recordStart + openPartOffset + 4 + numOpenFields * 8
                + StatisticsConstants.INTAKE_TUPLEID.length() + 2 + 1);
        int lastRecordId = frame.getInt(recordStart + recordIdOffset);
        return lastRecordId;
    }

    private ByteBuffer cloneFrame(ByteBuffer frame) {
        ByteBuffer clone = ByteBuffer.allocate(frame.capacity());
        System.arraycopy(frame.array(), 0, clone.array(), 0, frame.limit());
        return clone;
    }
}