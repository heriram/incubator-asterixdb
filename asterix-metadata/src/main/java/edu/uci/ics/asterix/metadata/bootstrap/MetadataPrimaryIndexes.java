/*
 * Copyright 2009-2010 by The Regents of the University of California
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

package edu.uci.ics.asterix.metadata.bootstrap;

import edu.uci.ics.asterix.metadata.MetadataException;
import edu.uci.ics.asterix.metadata.api.IMetadataIndex;
import edu.uci.ics.asterix.om.types.BuiltinType;
import edu.uci.ics.asterix.om.types.IAType;

/**
 * Contains static primary-index descriptors of all metadata datasets.
 */
public class MetadataPrimaryIndexes {

    public static IMetadataIndex DATAVERSE_DATASET;
    public static IMetadataIndex DATASET_DATASET;
    public static IMetadataIndex DATATYPE_DATASET;
    public static IMetadataIndex INDEX_DATASET;
    public static IMetadataIndex NODE_DATASET;
    public static IMetadataIndex NODEGROUP_DATASET;
    public static IMetadataIndex FUNCTION_DATASET;
    
    //The following resourceId value must be monotonically increasing value.
    //Instead of creating the value using the resourceIdSeed member variable of MetadataNode,
    //the value is assigned a static value.
    //Also, the MetadataSecondaryIndexes' resourceId is generated in the same manner.
    //(Please see the MetadataSecondaryIndexes.java.)
    //Therefore, the resourceIdSeed must be set to the MetadataSecondaryIndexes' the last resourceId + 1.
    public static final int DATAVERSE_DATASET_RESOURCE_ID = 1;
    public static final int DATASET_DATASET_RESOURCE_ID = 2;
    public static final int DATATYPE_DATASET_RESOURCE_ID = 3;
    public static final int INDEX_DATASET_RESOURCE_ID = 4;
    public static final int NODE_DATASET_RESOURCE_ID = 5;
    public static final int NODEGROUP_DATASET_RESOURCE_ID = 6;
    public static final int FUNCTION_DATASET_RESOURCE_ID = 7;
    
    /**
     * Create all metadata primary index descriptors. MetadataRecordTypes must
     * have been initialized before calling this init.
     * 
     * @throws MetadataException
     *             If MetadataRecordTypes have not been initialized.
     */
    public static void init() throws MetadataException {
        // Make sure the MetadataRecordTypes have been initialized.
        if (MetadataRecordTypes.DATASET_RECORDTYPE == null) {
            throw new MetadataException(
                    "Must initialize MetadataRecordTypes before initializing MetadataPrimaryIndexes");
        }

        DATAVERSE_DATASET = new MetadataIndex("Dataverse", null, 2, new IAType[] { BuiltinType.ASTRING },
                new String[] { "DataverseName" }, MetadataRecordTypes.DATAVERSE_RECORDTYPE, DATAVERSE_DATASET_RESOURCE_ID);

        DATASET_DATASET = new MetadataIndex("Dataset", null, 3,
                new IAType[] { BuiltinType.ASTRING, BuiltinType.ASTRING }, new String[] { "DataverseName",
                        "DatasetName" }, MetadataRecordTypes.DATASET_RECORDTYPE, DATASET_DATASET_RESOURCE_ID);

        DATATYPE_DATASET = new MetadataIndex("Datatype", null, 3, new IAType[] { BuiltinType.ASTRING,
                BuiltinType.ASTRING }, new String[] { "DataverseName", "DatatypeName" },
                MetadataRecordTypes.DATATYPE_RECORDTYPE, DATATYPE_DATASET_RESOURCE_ID);

        INDEX_DATASET = new MetadataIndex("Index", null, 4, new IAType[] { BuiltinType.ASTRING, BuiltinType.ASTRING,
                BuiltinType.ASTRING }, new String[] { "DataverseName", "DatasetName", "IndexName" },
                MetadataRecordTypes.INDEX_RECORDTYPE, INDEX_DATASET_RESOURCE_ID);

        NODE_DATASET = new MetadataIndex("Node", null, 2, new IAType[] { BuiltinType.ASTRING },
                new String[] { "NodeName" }, MetadataRecordTypes.NODE_RECORDTYPE, NODE_DATASET_RESOURCE_ID);

        NODEGROUP_DATASET = new MetadataIndex("Nodegroup", null, 2, new IAType[] { BuiltinType.ASTRING },
                new String[] { "GroupName" }, MetadataRecordTypes.NODEGROUP_RECORDTYPE, NODEGROUP_DATASET_RESOURCE_ID);
        
        FUNCTION_DATASET = new MetadataIndex("Function", null, 4,
				new IAType[] { BuiltinType.ASTRING, BuiltinType.ASTRING,
						BuiltinType.ASTRING }, new String[] { "DataverseName",
                        "FunctionName", "FunctionArity" }, MetadataRecordTypes.FUNCTION_RECORDTYPE, FUNCTION_DATASET_RESOURCE_ID);

    }
}