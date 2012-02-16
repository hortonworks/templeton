/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hcatalog.templeton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

/**
 *  HDFS implementation of templeton storage. 
 * 
 *  This implementation assumes that all keys in key/value pairs are
 *  chosen such that they don't have any newlines in them.
 *
 */
public class HDFSStorage implements TempletonStorage {
	FileSystem fs = null;
	
	public static final String STORAGE_ROOT = "/user/templeton/storage";
    
    public static final String JOB_PATH = STORAGE_ROOT + "/jobs";
    public static final String JOB_TRACKINGPATH = STORAGE_ROOT + "/created";
    public static final String OVERHEAD_PATH = STORAGE_ROOT + "/overhead";
    
    private static final Log LOG = LogFactory.getLog(HDFSStorage.class);

    static {
        try {
            HDFSCleanup.startInstance(Main.getAppConfigInstance());
        } catch (IOException e) {
            // If cleanup isn't running, should the server run?
            LOG.error("ZookeeperCleanup failed to start: " + e.getMessage());
        }
    }
    
	@Override
	public void saveField(Type type, String id, String key, String val)
			throws NotFoundException {
		HashMap<String, String> vals = new HashMap<String, String>();
		Path file = new Path(getPath(type) + "/" + id);
		PrintWriter out = null;
		try {
			if (fs.exists(file)) {
				vals = (HashMap<String, String>) getFields(type, id);
			}
			// This will replace the old value if there is one
			vals.put(key, val);
			// Overwrite the existing file
			out = new PrintWriter(new OutputStreamWriter(fs.create(file)));
			for (String line : vals.keySet()) {
				out.write(line + "\n");
				out.write(preprocess(vals.get(line)) + "\n");
			}
		} catch (IOException e) {
			LOG.info("Couldn't write to " + getPath(type) + "/" + id + ": " 
					+ e.getMessage());
		} finally {
			try {
				out.flush();
				out.close();
			} catch (Exception e) {
				// fail
			}
		}
	}

	@Override
	public String getField(Type type, String id, String key) {
		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader
					(fs.open(new Path(getPath(type) + "/" + id))));
			String line = null;
			while ((line = in.readLine()) != null) {
				String val = in.readLine();
				if (line.equals(key)) {
					return postprocess(val);
				}
			}
		} catch (IOException e) {
			LOG.info("Couldn't find " + getPath(type) + "/" + id);
		} finally {
			try {
				in.close();
			} catch (Exception e) {
				// fail
			}
		}
		return null;
	}

	@Override
	public Map<String, String> getFields(Type type, String id) {
		HashMap<String, String> map = new HashMap<String, String>();
		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader
					(fs.open(new Path(getPath(type) + "/" + id))));
			String line = null;
			while ((line = in.readLine()) != null) {
				String val = in.readLine();
				map.put(line, postprocess(val));
			}
		} catch (IOException e) {
			LOG.info("Couldn't find " + getPath(type) + "/" + id);
		} finally {
			try {
				in.close();
			} catch (Exception e) {
				// fail
			}
		}
		return map;
	}

	@Override
	public boolean delete(Type type, String id) throws NotFoundException {
		try {
			fs.delete(new Path(getPath(type) + "/" + id), false);
		} catch (IOException e) {
			throw new NotFoundException("Node " + id + " was not found: " +
					e.getMessage());
		}
		return false;
	}

	@Override
	public List<String> getAll() {
		ArrayList<String> allNodes = new ArrayList<String>();
        for (Type type: Type.values()) {
            allNodes.addAll(getAllForType(type));
        }
        return allNodes;
	}

	@Override
	public List<String> getAllForType(Type type) {
		ArrayList<String> allNodes = new ArrayList<String>();
		try {
			for (FileStatus status : fs.listStatus(new Path(getPath(type)))) {
				allNodes.add(status.getPath().getName());
			}
			return null;
		} catch (Exception e) {
			LOG.info("Couldn't find children for type " + type.toString());
		}
		return allNodes;
	}

	@Override
	public List<String> getAllForKey(String key, String value) {
		ArrayList<String> allNodes = new ArrayList<String>();
		try {
			for (Type type : Type.values()) {
				allNodes.addAll(getAllForTypeAndKey(type, key, value));
			}
		} catch (Exception e) {
			LOG.info("Couldn't find children for key " + key + ": " +
					e.getMessage());
		}
		return allNodes;
	}

	@Override
	public List<String> getAllForTypeAndKey(Type type, String key, String value) {
		ArrayList<String> allNodes = new ArrayList<String>();
		HashMap<String, String> map = new HashMap<String, String>();
		try {
			for (FileStatus status : 
				fs.listStatus(new Path(getPath(type)))) {
					map = (HashMap<String, String>) 
							getFields(type, status.getPath().getName());
					if (postprocess(map.get(key))
							.equals(value)) {
						allNodes.add(status.getPath().getName());
					}
			}
		} catch (Exception e) {
			LOG.info("Couldn't find children for key " + key + ": " +
					e.getMessage());
		}
		return allNodes;
	}

	@Override
	public void openStorage(Configuration config) throws IOException {
		if (fs == null) {
			fs = FileSystem.get(config);
		}
	}

	@Override
	public void closeStorage() throws IOException {
		// Nothing to do here
	}
	
	/**
	 * Get the path to storage based on the type.
	 * @param type
	 * @return
	 */
	public static String getPath(Type type) {
    	String typepath = OVERHEAD_PATH;
    	switch (type) {
    	case JOB:
    		typepath = JOB_PATH;
    		break;
    	case JOBTRACKING:
    		typepath = JOB_TRACKINGPATH;
    		break;
    	}
    	return typepath;
	}

	/**
	 * Take newlines out of a string to store it correctly.
	 * 
	 * @param value
	 * @return
	 */
	private String preprocess(String value) {
		if (value == null) {
			return null;
		}
		return value.replaceAll("\\n", "\\\\n");
	}
	
	/**
	 * Put newlines back into a string to return it correctly.
	 * 
	 * @param value
	 * @return
	 */
	private String postprocess(String value) {
		if (value == null) {
			return null;
		}
		return value.replaceAll("\\\\n", "\n");
	}
}
