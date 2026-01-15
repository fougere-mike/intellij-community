// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.ui.toolwindow

import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.kotlin.idea.roku.RokuBundle
import org.jetbrains.kotlin.idea.roku.RokuIcons
import org.jetbrains.kotlin.idea.roku.device.model.RokuDevice
import org.jetbrains.kotlin.idea.roku.device.model.RokuDeviceConnectionState
import javax.swing.JList

/**
 * Custom cell renderer for Roku devices in the device list.
 *
 * Shows device name, IP address, model, and connection status with appropriate icons.
 */
class RokuDeviceListCellRenderer : ColoredListCellRenderer<RokuDevice>() {

    override fun customizeCellRenderer(
        list: JList<out RokuDevice>,
        value: RokuDevice,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        // Set icon based on connection state
        icon = when (value.currentConnectionState) {
            RokuDeviceConnectionState.AVAILABLE -> RokuIcons.ROKU_CONNECTED
            RokuDeviceConnectionState.AUTH_REQUIRED,
            RokuDeviceConnectionState.AUTH_FAILED -> RokuIcons.ROKU_AUTH_REQUIRED
            RokuDeviceConnectionState.UNAVAILABLE,
            RokuDeviceConnectionState.ERROR -> RokuIcons.ROKU_DISCONNECTED
            else -> RokuIcons.ROKU
        }

        // Display name
        append(value.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)

        // IP address
        append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        append(value.ipAddress, SimpleTextAttributes.GRAYED_ATTRIBUTES)

        // Model number if available
        if (value.modelNumber.isNotBlank()) {
            append(" (${value.modelNumber})", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        }

        // Status indicator
        val (statusText, statusAttrs) = when (value.currentConnectionState) {
            RokuDeviceConnectionState.UNKNOWN ->
                RokuBundle.message("device.state.unknown") to SimpleTextAttributes.GRAYED_ATTRIBUTES
            RokuDeviceConnectionState.DISCOVERING ->
                RokuBundle.message("device.state.discovering") to SimpleTextAttributes.GRAYED_ATTRIBUTES
            RokuDeviceConnectionState.CHECKING ->
                RokuBundle.message("device.state.checking") to SimpleTextAttributes.GRAYED_ATTRIBUTES
            RokuDeviceConnectionState.AVAILABLE ->
                RokuBundle.message("device.state.available") to SimpleTextAttributes.REGULAR_ATTRIBUTES
            RokuDeviceConnectionState.UNAVAILABLE ->
                RokuBundle.message("device.state.unavailable") to SimpleTextAttributes.GRAYED_ATTRIBUTES
            RokuDeviceConnectionState.AUTH_REQUIRED ->
                RokuBundle.message("device.state.authRequired") to SimpleTextAttributes.ERROR_ATTRIBUTES
            RokuDeviceConnectionState.AUTH_FAILED ->
                RokuBundle.message("device.state.authFailed") to SimpleTextAttributes.ERROR_ATTRIBUTES
            RokuDeviceConnectionState.ERROR ->
                RokuBundle.message("device.state.error") to SimpleTextAttributes.ERROR_ATTRIBUTES
        }

        append(" [$statusText]", statusAttrs)
    }
}
