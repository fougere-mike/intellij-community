// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.idea.roku.device.model.RokuDevice
import java.io.OutputStream

/**
 * Run profile state for Roku application execution.
 *
 * This state is used after the BeforeRunTask (Gradle build) completes.
 * It primarily sets up post-deployment actions like switching to the log window.
 */
class RokuRunProfileState(
    private val environment: ExecutionEnvironment,
    private val configuration: RokuRunConfiguration
) : RunProfileState {

    private val LOG = Logger.getInstance(RokuRunProfileState::class.java)

    override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult {
        val project = environment.project
        val device = configuration.getTargetDevice(environment)
            ?: throw ExecutionException("No Roku device selected")

        LOG.info("Executing Roku run configuration for device: ${device.displayName}")

        // Create a simple process handler for the execution result
        val processHandler = RokuDeploymentProcessHandler(device)

        // Create console
        val console = TextConsoleBuilderFactory.getInstance()
            .createBuilder(project)
            .console
        console.attachToProcess(processHandler)

        // Start the "process" (deployment notification)
        processHandler.startNotify()

        // Notify deployment complete (terminates the process)
        // Note: Tool window activation is handled by RokuGradleBuildBeforeRunTaskProvider
        // after the Gradle build actually completes
        processHandler.notifyDeploymentComplete()

        return DefaultExecutionResult(console, processHandler)
    }
}

/**
 * Simple process handler for Roku deployment.
 *
 * This doesn't run an actual process - it's just used to provide
 * a process lifecycle for the run tool window.
 */
class RokuDeploymentProcessHandler(
    private val device: RokuDevice
) : ProcessHandler() {

    override fun destroyProcessImpl() {
        notifyProcessTerminated(0)
    }

    override fun detachProcessImpl() {
        notifyProcessDetached()
    }

    override fun detachIsDefault(): Boolean = false

    override fun getProcessInput(): OutputStream? = null

    fun notifyDeploymentComplete() {
        notifyTextAvailable("Deployment to ${device.displayName} (${device.ipAddress}) initiated.\n", ProcessOutputTypes.SYSTEM)
        notifyTextAvailable("Build completed. Switching to Roku Log window...\n", ProcessOutputTypes.SYSTEM)
        notifyProcessTerminated(0)
    }
}
