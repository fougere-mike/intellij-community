// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.kotlin.idea.roku.RokuBundle
import org.jetbrains.kotlin.idea.roku.RokuIcons
import org.jetbrains.kotlin.idea.roku.device.credentials.AuthenticationResult
import org.jetbrains.kotlin.idea.roku.device.credentials.RokuCredentialsManager
import org.jetbrains.kotlin.idea.roku.device.model.RokuDevice
import org.jetbrains.kotlin.idea.roku.device.model.RokuDeviceConnectionState
import org.jetbrains.kotlin.idea.roku.device.service.RokuDeviceService
import org.jetbrains.kotlin.idea.roku.ui.dialogs.AddDeviceDialog
import org.jetbrains.kotlin.idea.roku.ui.dialogs.PasswordDialog
import java.awt.BorderLayout
import javax.swing.*

/**
 * Panel for the Device Manager tool window.
 *
 * Displays a list of Roku devices with actions for discovery, adding,
 * removing, and testing authentication.
 */
class RokuDeviceManagerPanel(
    private val project: Project,
    parentDisposable: Disposable
) : JPanel(BorderLayout()), Disposable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val deviceService = RokuDeviceService.getInstance()
    private val credentialsManager = RokuCredentialsManager()

    private val deviceListModel = DefaultListModel<RokuDevice>()
    private val deviceList = JBList(deviceListModel).apply {
        cellRenderer = RokuDeviceListCellRenderer()
        selectionMode = ListSelectionModel.SINGLE_SELECTION
    }

    init {
        Disposer.register(parentDisposable, this)

        add(createToolbar(), BorderLayout.NORTH)
        add(JBScrollPane(deviceList), BorderLayout.CENTER)

        // Handle selection changes
        deviceList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selected = deviceList.selectedValue
                deviceService.selectDevice(selected)
            }
        }

        // Subscribe to device list updates
        scope.launch {
            deviceService.devices.collectLatest { devices ->
                withContext(Dispatchers.EDT) {
                    updateDeviceList(devices.values.toList())
                }
            }
        }

        // Subscribe to selected device changes
        scope.launch {
            deviceService.selectedDevice.collectLatest { selected ->
                withContext(Dispatchers.EDT) {
                    if (selected != null && deviceList.selectedValue?.id != selected.id) {
                        val index = (0 until deviceListModel.size).find {
                            deviceListModel.getElementAt(it).id == selected.id
                        }
                        if (index != null) {
                            deviceList.selectedIndex = index
                        }
                    }
                }
            }
        }
    }

    private fun createToolbar(): JComponent {
        val actionGroup = DefaultActionGroup().apply {
            add(RefreshAction())
            add(AddDeviceAction())
            add(RemoveDeviceAction())
            addSeparator()
            add(TestAuthAction())
        }

        val toolbar = ActionManager.getInstance().createActionToolbar(
            "RokuDeviceManager",
            actionGroup,
            true
        )
        toolbar.targetComponent = this
        return toolbar.component
    }

    private fun updateDeviceList(devices: List<RokuDevice>) {
        val selectedDevice = deviceList.selectedValue
        val sortedDevices = devices.sortedBy { it.displayName }

        deviceListModel.clear()
        sortedDevices.forEach { deviceListModel.addElement(it) }

        // Restore selection if device still exists
        if (selectedDevice != null) {
            val index = sortedDevices.indexOfFirst { it.id == selectedDevice.id }
            if (index >= 0) {
                deviceList.selectedIndex = index
            }
        }
    }

    private inner class RefreshAction : AnAction(
        RokuBundle.message("action.refresh"),
        RokuBundle.message("action.refresh.description"),
        AllIcons.Actions.Refresh
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            scope.launch {
                deviceService.discoverDevices()
            }
        }
    }

    private inner class AddDeviceAction : AnAction(
        RokuBundle.message("action.add"),
        RokuBundle.message("action.add.description"),
        AllIcons.General.Add
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            val dialog = AddDeviceDialog(project)
            if (dialog.showAndGet()) {
                val ipAddress = dialog.ipAddress
                if (ipAddress.isNotBlank()) {
                    deviceService.addManualDevice(ipAddress)
                }
            }
        }
    }

    private inner class RemoveDeviceAction : AnAction(
        RokuBundle.message("action.remove"),
        RokuBundle.message("action.remove.description"),
        AllIcons.General.Remove
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            val selectedDevice = deviceList.selectedValue ?: return
            deviceService.removeDevice(selectedDevice.id)
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = deviceList.selectedValue != null
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

    private inner class TestAuthAction : AnAction(
        RokuBundle.message("action.testAuth"),
        RokuBundle.message("action.testAuth.description"),
        RokuIcons.ROKU_CONNECTED
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            val selectedDevice = deviceList.selectedValue ?: return

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
                            selectedDevice.updateConnectionState(RokuDeviceConnectionState.CONNECTED)
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
            e.presentation.isEnabled = deviceList.selectedValue != null
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

    override fun dispose() {
        scope.cancel()
    }
}
