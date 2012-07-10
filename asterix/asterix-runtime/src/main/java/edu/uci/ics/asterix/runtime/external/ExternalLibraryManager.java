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
package edu.uci.ics.asterix.runtime.external;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ExternalLibraryManager {

    private static Map<String, ClassLoader> libraryClassLoaders = new HashMap<String, ClassLoader>();

    public static void registerLibraryClassLoader(String dataverseName, String libraryName, ClassLoader classLoader) {
        String key = dataverseName + "." + libraryName;
        synchronized (libraryClassLoaders) {
            if (libraryClassLoaders.get(dataverseName) != null) {
                throw new IllegalStateException("library class loader already registered!");
            }
            libraryClassLoaders.put(key, classLoader);
        }
    }

    public static ClassLoader getLibraryClassLoader(String dataverseName, String libraryName) {
        String key = dataverseName + "." + libraryName;
        synchronized (libraryClassLoaders) {
            return libraryClassLoaders.get(key);
        }
    }

    public static File getExternalLibraryDeployDir(String nodeId) {
        String filePath = null;
        if (nodeId != null) {
            filePath = "edu.uci.ics.hyracks.control.nc.NodeControllerService" + "/" + nodeId + "/"
                    + "applications/asterix/expanded/external-lib/libraries";
        } else {
            filePath = "ClusterControllerService" + "/" + "applications/asterix/expanded/external-lib/libraries";

        }
        return new File(filePath);
    }

}