/*
 * Copyright 2009-2012 by The Regents of the University of California
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
package edu.uci.ics.asterix.tools.external.data.adapter;

import java.util.Map;

import edu.uci.ics.asterix.external.adapter.factory.IGenericDatasourceAdapterFactory;
import edu.uci.ics.asterix.external.dataset.adapter.IDatasourceAdapter;
import edu.uci.ics.asterix.om.types.ARecordType;
import edu.uci.ics.asterix.om.types.IAType;

public class RateControlledFileSystemBasedAdapterFactory implements IGenericDatasourceAdapterFactory {

    @Override
    public AdapterType getAdapterType() {
        return AdapterType.GENERIC;
    }

    @Override
    public IDatasourceAdapter createAdapter(Map<String, String> configuration, IAType type) throws Exception {
        return new RateControlledFileSystemBasedAdapter((ARecordType) type, configuration);
    }

}
