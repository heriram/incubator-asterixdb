/*
 * Copyright 2009-2014 by The Regents of the University of California
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

import java.io.PrintWriter;
import java.io.StringReader;
import java.util.List;
import java.util.logging.Logger;

import edu.uci.ics.asterix.api.common.APIFramework.DisplayFormat;
import edu.uci.ics.asterix.api.common.SessionConfig;
import edu.uci.ics.asterix.aql.base.Statement;
import edu.uci.ics.asterix.aql.parser.AQLParser;
import edu.uci.ics.asterix.aql.translator.AqlTranslator;
import edu.uci.ics.asterix.common.config.AsterixFeedProperties;
import edu.uci.ics.asterix.common.exceptions.AsterixException;
import edu.uci.ics.asterix.common.feeds.api.ICentralFeedManager;
import edu.uci.ics.asterix.common.feeds.api.IFeedLoadManager;
import edu.uci.ics.asterix.common.feeds.api.IFeedTrackingManager;
import edu.uci.ics.asterix.metadata.feeds.SocketMessageListener;
import edu.uci.ics.asterix.om.util.AsterixAppContextInfo;
import edu.uci.ics.hyracks.api.client.IHyracksClientConnection;
import edu.uci.ics.hyracks.api.job.JobId;
import edu.uci.ics.hyracks.api.job.JobSpecification;

public class CentralFeedManager implements ICentralFeedManager {

    private static final Logger LOGGER = Logger.getLogger(CentralFeedManager.class.getName());

    private static final ICentralFeedManager centralFeedManager = new CentralFeedManager();
    private final IFeedLoadManager feedLoadManager;
    private final IFeedTrackingManager feedTrackingManager;

    public static ICentralFeedManager getInstance() {
        return centralFeedManager;
    }

    private final int port;
    private final SocketMessageListener messageListener;

    private CentralFeedManager() {
        AsterixFeedProperties feedProperties = AsterixAppContextInfo.getInstance().getFeedProperties();
        port = feedProperties.getFeedCentralManagerPort();
        feedLoadManager = new FeedLoadManager();
        feedTrackingManager = new FeedTrackingManager();
        messageListener = new SocketMessageListener(port, new FeedMessageReceiver(this));
    }

    @Override
    public void start() throws AsterixException {
        messageListener.start();
    }

    @Override
    public void stop() throws AsterixException {
        messageListener.stop();
    }

    public static JobId runJob(JobSpecification spec, boolean waitForCompletion) throws Exception {
        IHyracksClientConnection hcc = AsterixAppContextInfo.getInstance().getHcc();
        JobId jobId = hcc.startJob(spec);
        if (waitForCompletion) {
            hcc.waitForCompletion(jobId);
        }
        return jobId;
    }

    public static class AQLExecutor {

        private static final PrintWriter out = new PrintWriter(System.out, true);

        public static void executeAQL(String aql) throws Exception {
            AQLParser parser = new AQLParser(new StringReader(aql));
            List<Statement> statements;
            statements = parser.Statement();
            SessionConfig pc = new SessionConfig(true, false, false, false, false, false, true, true, false);
            AqlTranslator translator = new AqlTranslator(statements, out, pc, DisplayFormat.TEXT);
            translator.compileAndExecute(AsterixAppContextInfo.getInstance().getHcc(), null, false);
        }
    }

    public IFeedLoadManager getFeedLoadManager() {
        return feedLoadManager;
    }

    public IFeedTrackingManager getFeedTrackingManager() {
        return feedTrackingManager;
    }
}
