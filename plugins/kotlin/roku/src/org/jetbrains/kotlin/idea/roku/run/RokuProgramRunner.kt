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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.kotlin.idea.roku.execution.RokuDeviceExecutionTarget
import org.jetbrains.kotlin.idea.roku.log.service.RokuLogService

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
        val configuration = environment.runProfile as? RokuRunConfiguration ?: return

        // Get the target device
        val device = configuration.getTargetDevice(environment)

        if (device != null) {
            LOG.info("Roku deployment complete for device: ${device.displayName}")

            // Select device in log service before switching to log window
            ApplicationManager.getApplication().invokeLater {
                val logService = RokuLogService.getInstance(project)
                logService.selectDevice(device)

                // Switch to Roku Log tool window
                activateRokuLogToolWindow(project)
            }
        } else {
            LOG.warn("No target device found after Roku deployment")
        }

        // Execute the profile state (shows deployment complete message)
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

    /**
     * Activates the Roku Log tool window.
     */
    private fun activateRokuLogToolWindow(project: Project) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow(ROKU_LOG_TOOL_WINDOW_ID)

        if (toolWindow != null) {
            toolWindow.activate(null, true, true)
            LOG.info("Activated Roku Log tool window")
        } else {
            LOG.warn("Roku Log tool window not found")
        }
    }

    companion object {
        const val RUNNER_ID = "RokuProgramRunner"
        const val ROKU_LOG_TOOL_WINDOW_ID = "Roku Log"
    }
}
