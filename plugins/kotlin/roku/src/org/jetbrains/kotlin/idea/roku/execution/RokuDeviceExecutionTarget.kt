// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.execution

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.configurations.RunConfiguration
import org.jetbrains.kotlin.idea.roku.RokuIcons
import org.jetbrains.kotlin.idea.roku.device.model.RokuDevice
import org.jetbrains.kotlin.idea.roku.device.model.RokuDeviceConnectionState
import org.jetbrains.kotlin.idea.roku.run.RokuRunConfiguration
import javax.swing.Icon

/**
 * Execution target representing a Roku device.
 *
 * This integrates with IntelliJ's execution target system to provide
 * a device selection dropdown in the toolbar.
 */
class RokuDeviceExecutionTarget(
    val device: RokuDevice
) : ExecutionTarget() {

    override fun getId(): String = "roku_device_${device.id}"

    override fun getDisplayName(): String = device.displayName

    override fun getIcon(): Icon {
        return when (device.currentConnectionState) {
            RokuDeviceConnectionState.CONNECTED -> RokuIcons.ROKU_CONNECTED
            RokuDeviceConnectionState.AUTH_REQUIRED,
            RokuDeviceConnectionState.AUTH_FAILED -> RokuIcons.ROKU_AUTH_REQUIRED
            else -> RokuIcons.ROKU_DISCONNECTED
        }
    }

    override fun canRun(configuration: RunConfiguration): Boolean {
        // Only run on Roku configurations
        return configuration is RokuRunConfiguration
    }

    override fun isReady(): Boolean {
        // Consider ready if connected (authentication may still be needed)
        return device.currentConnectionState == RokuDeviceConnectionState.CONNECTED ||
               device.currentConnectionState == RokuDeviceConnectionState.UNKNOWN
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RokuDeviceExecutionTarget) return false
        return device.id == other.device.id
    }

    override fun hashCode(): Int = device.id.hashCode()
}
