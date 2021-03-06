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

package org.apache.flink.client.program;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.InvalidProgramException;
import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.JobSubmissionResult;
import org.apache.flink.api.common.Plan;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.optimizer.plan.OptimizedPlan;
import org.apache.flink.optimizer.plandump.PlanJSONDumpGenerator;
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings;

import java.net.URL;
import java.util.List;

/**
 * Execution Environment for remote execution with the Client.
 */
public class ContextEnvironment extends ExecutionEnvironment {

	private final ClusterClient<?> client;

	private final boolean detached;

	private final List<URL> jarFilesToAttach;

	private final List<URL> classpathsToAttach;

	private final ClassLoader userCodeClassLoader;

	private final SavepointRestoreSettings savepointSettings;

	private boolean alreadyCalled;

	public ContextEnvironment(ClusterClient<?> remoteConnection, List<URL> jarFiles, List<URL> classpaths,
				ClassLoader userCodeClassLoader, SavepointRestoreSettings savepointSettings, boolean detached) {
		this.client = remoteConnection;
		this.jarFilesToAttach = jarFiles;
		this.classpathsToAttach = classpaths;
		this.userCodeClassLoader = userCodeClassLoader;
		this.savepointSettings = savepointSettings;

		this.detached = detached;
		this.alreadyCalled = false;
	}

	@Override
	public JobExecutionResult execute(String jobName) throws Exception {
		verifyExecuteIsCalledOnceWhenInDetachedMode();

		final Plan plan = createProgramPlan(jobName);
		final JobWithJars job = new JobWithJars(plan, jarFilesToAttach, classpathsToAttach, userCodeClassLoader);
		final JobSubmissionResult jobSubmissionResult = client.run(job, getParallelism(), savepointSettings);

		lastJobExecutionResult = jobSubmissionResult.getJobExecutionResult();
		return lastJobExecutionResult;
	}

	@Override
	public String getExecutionPlan() throws Exception {
		Plan plan = createProgramPlan("unnamed job");

		OptimizedPlan op = ClusterClient.getOptimizedPlan(client.compiler, plan, getParallelism());
		PlanJSONDumpGenerator gen = new PlanJSONDumpGenerator();
		return gen.getOptimizerPlanAsJSON(op);
	}

	private void verifyExecuteIsCalledOnceWhenInDetachedMode() {
		if (alreadyCalled && detached) {
			throw new InvalidProgramException(DetachedJobExecutionResult.DETACHED_MESSAGE + DetachedJobExecutionResult.EXECUTE_TWICE_MESSAGE);
		}
		alreadyCalled = true;
	}

	@Override
	public String toString() {
		return "Context Environment (parallelism = " + (getParallelism() == ExecutionConfig.PARALLELISM_DEFAULT ? "default" : getParallelism()) + ")";
	}

	public ClusterClient<?> getClient() {
		return this.client;
	}

	public List<URL> getJars(){
		return jarFilesToAttach;
	}

	public List<URL> getClasspaths(){
		return classpathsToAttach;
	}

	public ClassLoader getUserCodeClassLoader() {
		return userCodeClassLoader;
	}

	public SavepointRestoreSettings getSavepointRestoreSettings() {
		return savepointSettings;
	}

	// --------------------------------------------------------------------------------------------

	static void setAsContext(ContextEnvironmentFactory factory) {
		initializeContextEnvironment(factory);
	}

	static void unsetContext() {
		resetContextEnvironment();
	}
}
