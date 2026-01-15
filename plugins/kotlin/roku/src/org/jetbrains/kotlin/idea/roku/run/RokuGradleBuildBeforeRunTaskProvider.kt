// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.run

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.RunConfigurationBeforeRunProvider
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.runBlocking
import org.jdom.Element
import org.jetbrains.kotlin.idea.roku.RokuBundle
import org.jetbrains.kotlin.idea.roku.RokuIcons
import org.jetbrains.kotlin.idea.roku.device.credentials.RokuCredentialsManager
import org.jetbrains.kotlin.idea.roku.device.model.RokuDevice
import org.jetbrains.kotlin.idea.roku.log.service.RokuLogService
import org.jetbrains.kotlin.idea.roku.ui.dialogs.PasswordDialog
import org.jetbrains.plugins.gradle.util.GradleConstants
import javax.swing.Icon

/**
 * Before-run task provider that runs Gradle build with device properties.
 *
 * This task:
 * 1. Gets the target Roku device from the execution target
 * 2. Authenticates with the device (checking keychain, local.properties, prompting)
 * 3. Runs the configured Gradle task with device IP and password properties
 */
class RokuGradleBuildBeforeRunTaskProvider(
    private val project: Project
) : BeforeRunTaskProvider<RokuGradleBuildBeforeRunTask>(), DumbAware {

    private val LOG = Logger.getInstance(RokuGradleBuildBeforeRunTaskProvider::class.java)
    private val credentialsManager = RokuCredentialsManager()

    override fun getId(): Key<RokuGradleBuildBeforeRunTask> = ID

    override fun getName(): String = RokuBundle.message("run.before.task.name")

    override fun getIcon(): Icon = RokuIcons.ROKU

    override fun getTaskIcon(task: RokuGradleBuildBeforeRunTask): Icon = RokuIcons.ROKU

    override fun createTask(runConfiguration: RunConfiguration): RokuGradleBuildBeforeRunTask? {
        if (runConfiguration !is RokuRunConfiguration) {
            return null
        }
        return RokuGradleBuildBeforeRunTask()
    }

    override fun isConfigurable(): Boolean = false

    override fun configureTask(
        runConfiguration: RunConfiguration,
        task: RokuGradleBuildBeforeRunTask
    ): Boolean = false

    override fun canExecuteTask(
        configuration: RunConfiguration,
        task: RokuGradleBuildBeforeRunTask
    ): Boolean {
        return configuration is RokuRunConfiguration
    }

    override fun getDescription(task: RokuGradleBuildBeforeRunTask): String {
        return RokuBundle.message("run.before.task.description")
    }

    override fun executeTask(
        context: DataContext,
        configuration: RunConfiguration,
        env: ExecutionEnvironment,
        task: RokuGradleBuildBeforeRunTask
    ): Boolean {
        if (configuration !is RokuRunConfiguration) {
            LOG.warn("Configuration is not a RokuRunConfiguration")
            return false
        }

        // Get target device
        val device = configuration.getTargetDevice(env)
        if (device == null) {
            LOG.error("No target device available for Roku run configuration")
            return false
        }

        // Authenticate with device
        val password = runBlocking {
            getDevicePassword(device)
        }

        if (password == null) {
            LOG.warn("Authentication cancelled or failed for device ${device.displayName}")
            return false
        }

        // Build Gradle task settings
        val gradleTask = configuration.gradleTask ?: "installRoku"
        val projectPath = project.basePath ?: return false

        val settings = ExternalSystemTaskExecutionSettings()
        settings.externalSystemIdString = GradleConstants.SYSTEM_ID.id
        settings.externalProjectPath = projectPath
        settings.taskNames = buildList {
            if (configuration.packageFirst) {
                add("packageRoku")
            }
            add(gradleTask)
        }
        settings.scriptParameters = buildString {
            append("-Proku.device.ip=${device.ipAddress}")
            append(" -Proku.device.password=$password")
            configuration.moduleName?.let { module ->
                if (module.isNotBlank()) {
                    append(" -Proku.module=$module")
                }
            }
        }

        LOG.info("Executing Gradle tasks: ${settings.taskNames} for device ${device.displayName}")

        // Create execution environment for Gradle
        val gradleEnv = ExternalSystemUtil.createExecutionEnvironment(
            project,
            GradleConstants.SYSTEM_ID,
            settings,
            DefaultRunExecutor.EXECUTOR_ID
        ) ?: run {
            LOG.error("Failed to create Gradle execution environment")
            return false
        }

        gradleEnv.executionId = env.executionId

        val runner: ProgramRunner<*> = gradleEnv.runner

        // Execute Gradle build synchronously
        val success = RunConfigurationBeforeRunProvider.doRunTask(
            DefaultRunExecutor.getRunExecutorInstance().id,
            gradleEnv,
            runner
        )

        // If Gradle build succeeded, activate the Roku Log window
        if (success) {
            LOG.info("Gradle build complete, activating Roku Log window")
            ApplicationManager.getApplication().invokeLater({
                val logService = RokuLogService.getInstance(project)
                logService.selectDevice(device)

                val toolWindowManager = ToolWindowManager.getInstance(project)
                val toolWindow = toolWindowManager.getToolWindow("Roku Log")
                toolWindow?.activate({
                    LOG.info("Roku Log window activated after Gradle build")
                }, true, true)
            }, ModalityState.nonModal())
        }

        return success
    }

    /**
     * Gets the password for the device using the authentication flow.
     *
     * Checks keychain first, then local.properties, prompts if needed.
     */
    private suspend fun getDevicePassword(device: RokuDevice): String? {
        val result = credentialsManager.authenticate(
            device = device,
            project = project,
            promptForPassword = {
                promptForPassword(device)
            }
        )

        return when (result) {
            is org.jetbrains.kotlin.idea.roku.device.credentials.AuthenticationResult.Success -> {
                result.credentials.password
            }
            else -> null
        }
    }

    /**
     * Shows password dialog on EDT.
     */
    @RequiresEdt
    private suspend fun promptForPassword(device: RokuDevice): String? {
        var password: String? = null

        ApplicationManager.getApplication().invokeAndWait {
            val dialog = PasswordDialog(
                project,
                device
            )
            if (dialog.showAndGet()) {
                password = dialog.password
            }
        }

        return password
    }

    companion object {
        @JvmStatic
        val ID = Key.create<RokuGradleBuildBeforeRunTask>("Roku.GradleBuildBeforeRunTask")
    }
}

/**
 * Before-run task for Roku Gradle builds.
 */
class RokuGradleBuildBeforeRunTask : BeforeRunTask<RokuGradleBuildBeforeRunTask>(
    RokuGradleBuildBeforeRunTaskProvider.ID
) {
    init {
        isEnabled = true
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
    }
}
