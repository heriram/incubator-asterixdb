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
package org.apache.hyracks.storage.am.lsm.common.impls;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.data.std.api.IPointable;
import org.apache.hyracks.data.std.api.IValueReference;
import org.apache.hyracks.data.std.util.ArrayBackedValueStorage;
import org.apache.hyracks.storage.am.common.api.IMetadataPageManager;
import org.apache.hyracks.storage.am.common.api.ITreeIndexMetadataFrame;
import org.apache.hyracks.storage.am.lsm.common.api.IComponentMetadata;

public class MemoryComponentMetadata implements IComponentMetadata {
    private static final byte[] empty = new byte[0];
    private final List<org.apache.commons.lang3.tuple.Pair<IValueReference, ArrayBackedValueStorage>> store =
            new ArrayList<>();

    /**
     * Note: for memory metadata, it is expected that the key will be constant
     */
    @Override
    public void put(IValueReference key, IValueReference value) {
        ArrayBackedValueStorage stored = get(key);
        if (stored == null) {
            stored = new ArrayBackedValueStorage();
        }
        stored.assign(value);
        store.add(Pair.of(key, stored));
    }

    /**
     * Note: for memory metadata, it is expected that the key will be constant
     */
    @Override
    public void get(IValueReference key, IPointable value) {
        value.set(empty, 0, 0);
        ArrayBackedValueStorage stored = get(key);
        if (stored != null) {
            value.set(stored);
        }
    }

    @Override
    public ArrayBackedValueStorage get(IValueReference key) {
        for (Pair<IValueReference, ArrayBackedValueStorage> pair : store) {
            if (pair.getKey().equals(key)) {
                return pair.getValue();
            }
        }
        return null;
    }

    public void copy(IMetadataPageManager mdpManager) throws HyracksDataException {
        ITreeIndexMetadataFrame frame = mdpManager.createMetadataFrame();
        for (Pair<IValueReference, ArrayBackedValueStorage> pair : store) {
            mdpManager.put(frame, pair.getKey(), pair.getValue());
        }
    }

    public void copy(DiskComponentMetadata metadata) throws HyracksDataException {
        metadata.put(this);
    }

    public void reset() {
        store.clear();
    }
}
