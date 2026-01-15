// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.run

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import org.jetbrains.kotlin.idea.roku.RokuBundle
import org.jetbrains.kotlin.idea.roku.RokuIcons

/**
 * Run configuration type for Roku applications.
 *
 * Creates "Roku Application" configurations that build and deploy to Roku devices.
 */
class RokuRunConfigurationType : SimpleConfigurationType(
    "RokuApplicationConfigurationType",
    RokuBundle.message("run.config.name"),
    RokuBundle.message("run.config.description"),
    NotNullLazyValue.createValue { RokuIcons.ROKU }
) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        val config = RokuRunConfiguration(project, this, "Roku Application")

        // Add the Gradle build before-run task automatically
        val beforeRunProvider = BeforeRunTaskProvider.getProvider(project, RokuGradleBuildBeforeRunTaskProvider.ID)
        if (beforeRunProvider != null) {
            val task = beforeRunProvider.createTask(config)
            if (task != null) {
                task.isEnabled = true
                config.beforeRunTasks = listOf(task)
            }
        }

        return config
    }

    override fun isDumbAware(): Boolean = true

    override fun getOptionsClass(): Class<RokuRunConfigurationOptions> =
        RokuRunConfigurationOptions::class.java

    companion object {
        const val ID = "RokuApplicationConfigurationType"

        fun getInstance(): RokuRunConfigurationType {
            return ConfigurationType.CONFIGURATION_TYPE_EP.extensionList
                .filterIsInstance<RokuRunConfigurationType>()
                .first()
        }
    }
}
