// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.execution

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.ExecutionTargetProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.roku.device.service.RokuDeviceService
import org.jetbrains.kotlin.idea.roku.run.RokuRunConfiguration

/**
 * Provides Roku device execution targets to the IntelliJ platform.
 *
 * This enables the device selection dropdown in the toolbar when a
 * Roku run configuration is selected.
 */
class RokuExecutionTargetProvider : ExecutionTargetProvider() {
    private val LOG = Logger.getInstance(RokuExecutionTargetProvider::class.java)

    override fun getTargets(project: Project, configuration: RunConfiguration): List<ExecutionTarget> {
        LOG.warn("RokuExecutionTargetProvider.getTargets called for config: ${configuration.javaClass.simpleName}")

        // Only provide targets for Roku configurations
        if (configuration !is RokuRunConfiguration) {
            LOG.warn("Not a RokuRunConfiguration, returning empty list")
            return emptyList()
        }

        val deviceService = RokuDeviceService.getInstance()
        val devices = deviceService.getDeviceList()
        LOG.warn("Found ${devices.size} device(s): ${devices.map { it.displayName }}")

        return devices.map { RokuDeviceExecutionTarget(it) }
    }
}
