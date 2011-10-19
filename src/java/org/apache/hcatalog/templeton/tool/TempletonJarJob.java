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
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RunningJob;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

/**
 * Submit a jar job from the command line.  The default Hadoop tool
 * submits the job and waits for completion and we want to just put it
 * in the queue and return a job id.
 */
public class TempletonJarJob extends Configured implements Tool {
    /**
     * Enqueue the job and print out the job id for later collection.
     */
    @Override
    public int run(String[] args) throws IOException {
//         JobConf conf = StreamJob.createJob(args);
//         JobClient client = new JobClient(conf);
//         RunningJob job = client.submitJob(conf);
//        System.out.println(TempletonStreamJob.JOB_ID_TAG + job.getJobID());
//        return 0;
        return 1;
    }

    public static void main(String[] args) throws Exception {
        int ret = ToolRunner.run(new TempletonJarJob(), args);
        if (ret != 0)
            System.err.println("TempletonJarJob failed!");
        System.exit(ret);
    }
}
