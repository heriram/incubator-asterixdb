/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.asterix.dataflow.data.nontagged.serde;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.asterix.om.base.AInterval;
import org.apache.hyracks.api.dataflow.value.ISerializerDeserializer;
import org.apache.hyracks.api.exceptions.HyracksDataException;

public class AIntervalSerializerDeserializer implements ISerializerDeserializer<AInterval> {

    private static final long serialVersionUID = 1L;

    private static final int INTERVAL_START_POINT_OFFSET = 0;
    private static final int INTERVAL_END_POINT_OFFSET = INTERVAL_START_POINT_OFFSET + Long.BYTES;
    private static final int INTERVAL_TAG_OFFSET = INTERVAL_END_POINT_OFFSET + Long.BYTES;

    public static final AIntervalSerializerDeserializer INSTANCE = new AIntervalSerializerDeserializer();

    private AIntervalSerializerDeserializer() {
    }

    @Override
    public AInterval deserialize(DataInput in) throws HyracksDataException {
        try {
            return new AInterval(in.readLong(), in.readLong(), in.readByte());
        } catch (IOException e) {
            throw new HyracksDataException(e);
        }

    }

    @Override
    public void serialize(AInterval instance, DataOutput out) throws HyracksDataException {
        try {
            out.writeLong(instance.getIntervalStart());
            out.writeLong(instance.getIntervalEnd());
            out.writeByte(instance.getIntervalType());
        } catch (IOException e) {
            throw new HyracksDataException(e);
        }
    }

    public static long getIntervalStart(byte[] data, int offset) {
        return AInt64SerializerDeserializer.getLong(data, offset + getIntervalStartOffset());
    }

    public static long getIntervalEnd(byte[] data, int offset) {
        return AInt64SerializerDeserializer.getLong(data, offset + getIntervalEndOffset());
    }

    public static int getIntervalStartOffset() {
        return INTERVAL_START_POINT_OFFSET;
    }

    public static int getIntervalEndOffset() {
        return INTERVAL_END_POINT_OFFSET;
    }

    public static int getIntervalTagOffset() {
        return INTERVAL_TAG_OFFSET;
    }

    public static byte getIntervalTimeType(byte[] data, int offset) {
        return data[offset + 8 * 2];
    }

}
