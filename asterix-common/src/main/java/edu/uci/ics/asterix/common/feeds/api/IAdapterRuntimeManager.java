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
package edu.uci.ics.asterix.common.feeds.api;

import edu.uci.ics.asterix.common.feeds.FeedId;

public interface IAdapterRuntimeManager {

    public enum State {
        /**
         * Indicates that AsterixDB is maintaining the flow of data from external source into its storage.
         */
        ACTIVE_INGESTION,

        /**
         * Indicates that data from external source is being buffered and not
         * pushed downstream
         */

        INACTIVE_INGESTION,
        /**
         * Indicates that feed ingestion activity has finished.
         */
        FINISHED_INGESTION
    }

    /**
     * Start feed ingestion
     * 
     * @throws Exception
     */
    public void start() throws Exception;

    /**
     * Stop feed ingestion.
     * 
     * @throws Exception
     */
    public void stop() throws Exception;

    /**
     * @return feedId associated with the feed that is being ingested
     */
    public FeedId getFeedId();

    /**
     * @return the instance of the feed adapter (an implementation of {@code IFeedAdapter}) in use.
     */
    public IFeedAdapter getFeedAdapter();

    /**
     * @return state associated with the AdapterRuntimeManager. See {@code State}.
     */
    public State getState();

    /**
     * @param state
     */
    public void setState(State state);

    public IIntakeProgressTracker getProgressTracker();

}