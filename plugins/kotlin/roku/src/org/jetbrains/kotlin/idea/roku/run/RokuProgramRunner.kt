// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionManager
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.runners.executeState
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.concurrency.resolvedPromise

/**
 * Program runner for Roku applications.
 *
 * After the Gradle build completes (via BeforeRunTask), this runner:
 * 1. Activates the Roku Log tool window
 * 2. Selects the deployment device in the log window
 * 3. Returns a simple execution result
 */
class RokuProgramRunner : ProgramRunner<RunnerSettings> {

    private val LOG = Logger.getInstance(RokuProgramRunner::class.java)

    override fun getRunnerId(): String = RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == DefaultRunExecutor.EXECUTOR_ID && profile is RokuRunConfiguration
    }

    @Throws(ExecutionException::class)
    override fun execute(environment: ExecutionEnvironment) {
        val state = environment.state ?: return
        val project = environment.project

        // Execute the profile state (shows deployment complete message)
        // The Gradle BeforeRunTask runs during startRunProfile, and
        // RokuRunProfileState.execute() activates the log window after completion
        ExecutionManager.getInstance(project).startRunProfile(environment) {
            resolvedPromise(doExecute(state, environment))
        }
    }

    @Throws(ExecutionException::class)
    private fun doExecute(
        state: RunProfileState,
        environment: ExecutionEnvironment
    ): RunContentDescriptor? {
        return executeState(state, environment, this)
    }

    companion object {
        const val RUNNER_ID = "RokuProgramRunner"
    }
}
