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
package org.apache.hcatalog.templeton.tool;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import org.apache.hadoop.conf.Configuration;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;

/**
 * The persistent state of a job.  The state is stored in ZooKeeper
 * where each field in a state is a separate znode.  For example, the
 * exit valiue could be contained in the znode
 *
 *     /templeton-hadoop/jobs/job_201112140012_0048/exitValue
 *
 * If a field is not set, null is returned for that field.  All fields
 * are encoded as utf-8 strings.
 */
public class JobState implements Watcher {
    public static final String JOB_ROOT = "/templeton-hadoop";
    public static final String JOB_PATH = JOB_ROOT + "/jobs";

    public static final String ZK_HOSTS = "templeton.zookeeper.hosts";
    public static final String ZK_SESSION_TIMEOUT
        = "templeton.zookeeper.session-timeout";

    public static final String ENCODING = "UTF-8";

    private String id;
    private ZooKeeper zk;

    public JobState(String id, String zkHosts, int zkSessionTimeout)
        throws IOException
    {
        this.id = id;
        zk = new ZooKeeper(zkHosts, zkSessionTimeout, this);
    }

    public JobState(String id, Configuration conf)
        throws IOException
    {
        this(id, conf.get(ZK_HOSTS), conf.getInt(ZK_SESSION_TIMEOUT, 30000));
    }

    /**
     * Create the parent znode for this job state.
     */
    public void create()
        throws IOException
    {
        try {
            String[] paths = {JOB_ROOT, JOB_PATH, makeZnode()};
            boolean wasCreated = false;
            for (String znode : paths) {
                try {
                    zk.create(znode, new byte[0],
                              Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                    wasCreated = true;
                } catch (KeeperException.NodeExistsException e) {
                }
            }
            if (zk.exists(makeZnode(), false) == null)
                throw new IOException("Unable to create " + makeZnode());
            if (wasCreated)
                setCreated(System.currentTimeMillis());
        } catch (KeeperException e) {
            throw new IOException("Creating " + id, e);
        } catch (InterruptedException e) {
            throw new IOException("Creating " + id, e);
        }
    }

    //
    // Properties
    //

    /**
     * The percent complete of a job
     */
    public String  getPercentComplete()
        throws IOException
    {
        return getField("percentComplete");
    }
    public void setPercentComplete(String percent)
        throws IOException
    {
        setField("percentComplete", percent);
    }

    /**
     * The child id of TempletonControllerJob
     */
    public String  getChildId()
        throws IOException
    {
        return getField("childid");
    }
    public void setChildId(String childid)
        throws IOException
    {
        setField("childid", childid);
    }

    /**
     * The system exit value of the job.
     */
    public Long getExitValue()
        throws IOException
    {
        return getLongField("exitValue");
    }
    public void setExitValue(long exitValue)
        throws IOException
    {
        setLongField("exitValue", exitValue);
    }

    /**
     * When this job was created.
     */
    public Long getCreated()
        throws IOException
    {
        return getLongField("created");
    }
    public void setCreated(long created)
        throws IOException
    {
        setLongField("created", created);
    }

    /**
     * The user who started this job.
     */
    public String getUser()
        throws IOException
    {
        return getField("user");
    }
    public void setUser(String user)
        throws IOException
    {
        setField("user", user);
    }

    /**
     * The url callback
     */
    public String getCallback()
        throws IOException
    {
        return getField("callback");
    }
    public void setCallback(String callback)
        throws IOException
    {
        setField("callback", callback);
    }

    //
    // Helpers
    //

    /**
     * Fetch an integer field from the ZK strore.
     */
    public Long getLongField(String name)
        throws IOException
    {
        String s = getField(name);
        if (s == null)
            return null;
        else {
            try {
                return new Long(s);
            } catch (NumberFormatException e) {
                System.err.println("templeton: bug " + name + " " + s + " : "+ e);
                return null;
            }
        }
    }

    /**
     * Store an integer field from the ZK store.
     */
    public void setLongField(String name, long val)
        throws IOException
    {
        setField(name, String.valueOf(val));
    }

    /**
     * Fetch a string for the ZK store.
     */
    public String getField(String name)
        throws IOException
    {
        try {
            byte[] b = zk.getData(makeFieldZnode(name), false, null);
            return new String(b, ENCODING);
        } catch(KeeperException.NoNodeException e) {
            return null;
        } catch(Exception e) {
            throw new IOException("Reading " + name, e);
        }
    }

    /**
     * Store a string in the ZK store.
     */
    public void setField(String name, String val)
        throws IOException
    {
        try {
            if (val != null) {
                create();
                setFieldData(name, val);
            }
        } catch(Exception e) {
            throw new IOException("Writing " + name + ": " + val, e);
        }
    }

    private void setFieldData(String name, String val)
        throws KeeperException, UnsupportedEncodingException, InterruptedException
    {
        try {
            zk.create(makeFieldZnode(name),
                      val.getBytes(ENCODING),
                      Ids.OPEN_ACL_UNSAFE,
                      CreateMode.PERSISTENT);
        } catch(KeeperException.NodeExistsException e) {
            zk.setData(makeFieldZnode(name),
                       val.getBytes(ENCODING),
                       -1);
        }
    }

    /**
     * Make a ZK path to the named field.
     */
    public String makeFieldZnode(String name) {
        return makeZnode() + "/" + name;
    }

    /**
     * Make a ZK path to job
     */
    public String makeZnode() {
        return JOB_PATH + "/" + id;
    }

    /**
     * The ZK watcher.  A no op.
     */
    @Override
    synchronized public void process(WatchedEvent event) {
    }
}
