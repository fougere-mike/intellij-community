// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.roku.device.service.RokuDeviceService
import org.jetbrains.kotlin.idea.roku.execution.RokuDeviceExecutionTarget

/**
 * Run configuration for building and deploying a Roku application.
 */
class RokuRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<RokuRunConfigurationOptions>(project, factory, name) {

    override fun getOptions(): RokuRunConfigurationOptions {
        return super.getOptions() as RokuRunConfigurationOptions
    }

    /** Whether to use the device selected in the toolbar */
    var useSelectedDevice: Boolean
        get() = options.useSelectedDevice
        set(value) { options.useSelectedDevice = value }

    /** Explicit device IP (used when useSelectedDevice is false) */
    var deviceIp: String?
        get() = options.deviceIp
        set(value) { options.deviceIp = value }

    /** Gradle task to execute */
    var gradleTask: String?
        get() = options.gradleTask
        set(value) { options.gradleTask = value }

    /** Whether to package before installing */
    var packageFirst: Boolean
        get() = options.packageFirst
        set(value) { options.packageFirst = value }

    /** Module name */
    var moduleName: String?
        get() = options.moduleName
        set(value) { options.moduleName = value }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return RokuRunConfigurationEditor(project)
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return RokuRunProfileState(environment, this)
    }

    override fun checkConfiguration() {
        // Validate that we have a way to get a device
        if (!useSelectedDevice && deviceIp.isNullOrBlank()) {
            throw RuntimeConfigurationError("No device specified. Either select a device from the toolbar or enter an IP address.")
        }

        // Validate Gradle task
        if (gradleTask.isNullOrBlank()) {
            throw RuntimeConfigurationError("No Gradle task specified.")
        }
    }

    /**
     * Gets the device to deploy to.
     *
     * Returns the device from the execution target if available,
     * otherwise falls back to explicit device IP or selected device.
     */
    fun getTargetDevice(environment: ExecutionEnvironment): org.jetbrains.kotlin.idea.roku.device.model.RokuDevice? {
        // First try to get from execution target
        val target = environment.executionTarget
        if (target is RokuDeviceExecutionTarget) {
            return target.device
        }

        // Fall back to explicit device or selected device
        return if (useSelectedDevice) {
            RokuDeviceService.getInstance().selectedDevice.value
        } else {
            deviceIp?.let { RokuDeviceService.getInstance().getDeviceByIp(it) }
        }
    }

}
