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
package org.apache.asterix.external.input.stream.provider;

import java.net.ServerSocket;
import java.util.Map;

import org.apache.asterix.external.api.IInputStreamProvider;
import org.apache.asterix.external.input.stream.AInputStream;
import org.apache.asterix.external.input.stream.SocketInputStream;
import org.apache.asterix.external.util.FeedLogManager;

public class SocketInputStreamProvider implements IInputStreamProvider {
    private ServerSocket server;

    public SocketInputStreamProvider(ServerSocket server) {
        this.server = server;
    }

    @Override
    public AInputStream getInputStream() throws Exception {
        return new SocketInputStream(server);
    }

    @Override
    public void configure(Map<String, String> configuration) {
    }

    @Override
    public void setFeedLogManager(FeedLogManager feedLogManager) {
    }
}