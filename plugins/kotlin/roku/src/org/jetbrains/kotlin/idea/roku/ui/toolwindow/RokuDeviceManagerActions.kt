// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.*
import org.jetbrains.kotlin.idea.roku.RokuBundle
import org.jetbrains.kotlin.idea.roku.RokuIcons
import org.jetbrains.kotlin.idea.roku.device.credentials.AuthenticationResult
import org.jetbrains.kotlin.idea.roku.device.credentials.RokuCredentialsManager
import org.jetbrains.kotlin.idea.roku.device.model.RokuDeviceConnectionState
import org.jetbrains.kotlin.idea.roku.device.service.RokuDeviceService
import org.jetbrains.kotlin.idea.roku.ui.dialogs.AddDeviceDialog
import org.jetbrains.kotlin.idea.roku.ui.dialogs.PasswordDialog

/**
 * Action to refresh/discover Roku devices on the network.
 */
class RokuRefreshDevicesAction : AnAction(
    RokuBundle.message("action.refresh"),
    RokuBundle.message("action.refresh.description"),
    AllIcons.Actions.Refresh
), DumbAware {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun actionPerformed(e: AnActionEvent) {
        scope.launch {
            RokuDeviceService.getInstance().discoverDevices()
        }
    }
}

/**
 * Action to manually add a Roku device by IP address.
 */
class RokuAddDeviceAction : AnAction(
    RokuBundle.message("action.add"),
    RokuBundle.message("action.add.description"),
    AllIcons.General.Add
), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dialog = AddDeviceDialog(project)
        if (dialog.showAndGet()) {
            val ipAddress = dialog.ipAddress
            if (ipAddress.isNotBlank()) {
                RokuDeviceService.getInstance().addManualDevice(ipAddress)
            }
        }
    }
}

/**
 * Action to remove the selected Roku device.
 */
class RokuRemoveDeviceAction : AnAction(
    RokuBundle.message("action.remove"),
    RokuBundle.message("action.remove.description"),
    AllIcons.General.Remove
), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val deviceService = RokuDeviceService.getInstance()
        val selectedDevice = deviceService.selectedDevice.value ?: return
        deviceService.removeDevice(selectedDevice.id)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = RokuDeviceService.getInstance().selectedDevice.value != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

/**
 * Action to test authentication with the selected Roku device.
 */
class RokuTestAuthAction : AnAction(
    RokuBundle.message("action.testAuth"),
    RokuBundle.message("action.testAuth.description"),
    RokuIcons.ROKU_CONNECTED
), DumbAware {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val credentialsManager = RokuCredentialsManager()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val deviceService = RokuDeviceService.getInstance()
        val selectedDevice = deviceService.selectedDevice.value ?: return

        scope.launch {
            val result = credentialsManager.authenticate(
                device = selectedDevice,
                project = project,
                promptForPassword = {
                    withContext(Dispatchers.EDT) {
                        PasswordDialog.showAndGet(project, selectedDevice)
                    }
                }
            )

            withContext(Dispatchers.EDT) {
                when (result) {
                    is AuthenticationResult.Success -> {
                        selectedDevice.updateConnectionState(RokuDeviceConnectionState.AVAILABLE)
                        Messages.showInfoMessage(
                            project,
                            RokuBundle.message("auth.success.message", selectedDevice.displayName),
                            RokuBundle.message("auth.success.title")
                        )
                    }
                    is AuthenticationResult.InvalidCredentials -> {
                        selectedDevice.updateConnectionState(RokuDeviceConnectionState.AUTH_FAILED)
                        Messages.showErrorDialog(
                            project,
                            RokuBundle.message("auth.invalid.message"),
                            RokuBundle.message("auth.invalid.title")
                        )
                    }
                    is AuthenticationResult.Error -> {
                        selectedDevice.updateConnectionState(RokuDeviceConnectionState.ERROR)
                        Messages.showErrorDialog(
                            project,
                            RokuBundle.message("auth.error.message", result.message),
                            RokuBundle.message("auth.error.title")
                        )
                    }
                    is AuthenticationResult.Cancelled -> {
                        // User cancelled, do nothing
                    }
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = RokuDeviceService.getInstance().selectedDevice.value != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
