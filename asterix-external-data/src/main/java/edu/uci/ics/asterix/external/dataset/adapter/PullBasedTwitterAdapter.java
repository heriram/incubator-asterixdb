/*
 * Copyright 2009-2011 by The Regents of the University of California
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
package edu.uci.ics.asterix.external.dataset.adapter;

import java.util.HashMap;
import java.util.Map;

import edu.uci.ics.asterix.feed.managed.adapter.IManagedFeedAdapter;
import edu.uci.ics.asterix.om.types.ARecordType;
import edu.uci.ics.asterix.om.types.BuiltinType;
import edu.uci.ics.asterix.om.types.IAType;
import edu.uci.ics.hyracks.algebricks.common.constraints.AlgebricksCountPartitionConstraint;
import edu.uci.ics.hyracks.algebricks.common.constraints.AlgebricksPartitionConstraint;
import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;

/**
 * An adapter that provides the functionality of receiving tweets from the
 * Twitter service in the form of ADM formatted records.
 */
@SuppressWarnings("serial")
public class PullBasedTwitterAdapter extends PullBasedAdapter implements IManagedFeedAdapter {

    public static final String QUERY = "query";
    public static final String INTERVAL = "interval";

    private int interval = 10; // 10 seconds
    private boolean stopRequested = false;
    private boolean alterRequested = false;
    private Map<String, String> alteredParams = new HashMap<String, String>();
    private ARecordType recordType;

    private PullBasedTwitterFeedClient tweetClient;

    @Override
    public IPullBasedFeedClient getFeedClient(int partition) {
        return tweetClient;
    }

    @Override
    public void configure(Map<String, String> arguments) throws Exception {
        configuration = arguments;
        interval = Integer.parseInt(arguments.get(INTERVAL));
        String[] fieldNames = { "id", "username", "location", "text", "timestamp" };
        IAType[] fieldTypes = { BuiltinType.ASTRING, BuiltinType.ASTRING, BuiltinType.ASTRING, BuiltinType.ASTRING,
                BuiltinType.ASTRING };
        recordType = new ARecordType("FeedRecordType", fieldNames, fieldTypes, false);
    }

    @Override
    public void initialize(IHyracksTaskContext ctx) throws Exception {
        this.ctx = ctx;
        tweetClient = new PullBasedTwitterFeedClient(ctx, this);
    }

    @Override
    public AdapterType getAdapterType() {
        return adapterType.READ;
    }

    @Override
    public void stop() throws Exception {
        stopRequested = true;
    }

    public boolean isStopRequested() {
        return stopRequested;
    }

    @Override
    public void alter(Map<String, String> properties) throws Exception {
        alterRequested = true;
        this.alteredParams = properties;
    }

    public boolean isAlterRequested() {
        return alterRequested;
    }

    public Map<String, String> getAlteredParams() {
        return alteredParams;
    }

    public void postAlteration() {
        alteredParams = null;
        alterRequested = false;
    }

    @Override
    public ARecordType getAdapterOutputType() {
        return recordType;
    }

    @Override
    public AlgebricksPartitionConstraint getPartitionConstraint() throws Exception {
        if (partitionConstraint == null) {
            partitionConstraint = new AlgebricksCountPartitionConstraint(1);
        }
        return partitionConstraint;
    }

}
