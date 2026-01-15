// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.log

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.kotlin.idea.roku.RokuBundle
import org.jetbrains.kotlin.idea.roku.connection.RokuConnectionManager
import org.jetbrains.kotlin.idea.roku.connection.RokuConnectionState
import org.jetbrains.kotlin.idea.roku.device.model.RokuDevice
import org.jetbrains.kotlin.idea.roku.device.service.RokuDeviceService
import org.jetbrains.kotlin.idea.roku.log.service.RokuLogService
import java.awt.Color
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.event.DocumentEvent

/**
 * Header panel for the Roku Log tool window.
 *
 * Contains:
 * - Device selection dropdown
 * - Filter text field with regex support
 * - Clear and auto-scroll toggle buttons
 */
class RokuLogHeaderPanel(
    private val project: Project,
    parentDisposable: Disposable
) : Disposable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val logService = RokuLogService.getInstance(project)
    private val deviceService = RokuDeviceService.getInstance()

    private val deviceComboBox = JComboBox<RokuDevice?>()
    private val connectionStatusLabel = JLabel()
    private val filterTextField = SearchTextField(true)
    private val rootPanel: JPanel

    private var autoScrollController: AutoScrollController? = null
    private var connectionStateJob: Job? = null

    /** The root component */
    val component: JComponent
        get() = rootPanel

    init {
        Disposer.register(parentDisposable, this)

        setupDeviceComboBox()
        setupFilterTextField()

        // Create toolbar with actions
        val actionGroup = DefaultActionGroup().apply {
            add(ReconnectAction())
            add(ClearAction())
            add(AutoScrollAction())
        }

        val toolbar = ActionManager.getInstance().createActionToolbar(
            "RokuLogToolbar",
            actionGroup,
            true
        )

        // Layout
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(JLabel("Device:"))
            add(deviceComboBox)
            add(connectionStatusLabel)
        }

        val centerPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyLeft(8)
            add(filterTextField, BorderLayout.CENTER)
        }

        rootPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4)
            add(leftPanel, BorderLayout.WEST)
            add(centerPanel, BorderLayout.CENTER)
            add(toolbar.component, BorderLayout.EAST)
        }

        // Set toolbar target after rootPanel is initialized
        toolbar.targetComponent = rootPanel

        // Subscribe to device list updates
        scope.launch {
            deviceService.devices.collectLatest { devices ->
                withContext(Dispatchers.EDT) {
                    updateDeviceComboBox(devices.values.toList())
                }
            }
        }

        // Subscribe to selected device changes from service
        scope.launch {
            logService.selectedDevice.collectLatest { device ->
                withContext(Dispatchers.EDT) {
                    if (deviceComboBox.selectedItem != device) {
                        deviceComboBox.selectedItem = device
                    }
                }
                // Subscribe to connection state for the selected device
                subscribeToConnectionState(device)
            }
        }
    }

    /**
     * Subscribes to connection state changes for the selected device.
     */
    private fun subscribeToConnectionState(device: RokuDevice?) {
        // Cancel previous subscription
        connectionStateJob?.cancel()

        if (device == null) {
            scope.launch {
                withContext(Dispatchers.EDT) {
                    connectionStatusLabel.text = ""
                }
            }
            return
        }

        connectionStateJob = scope.launch {
            val connectionManager = RokuConnectionManager.getInstance()

            // Show "Connecting..." immediately while connection is being established
            withContext(Dispatchers.EDT) {
                updateConnectionStatus(RokuConnectionState.CONNECTING, device)
            }

            // Poll for connection state with shorter interval for faster feedback
            var attempts = 0
            val maxAttempts = 50  // 5 seconds max wait (50 * 100ms)
            while (isActive && attempts < maxAttempts) {
                val stateFlow = connectionManager.getConnectionState(device)

                if (stateFlow != null) {
                    // We have a connection, subscribe to its state
                    stateFlow.collectLatest { state ->
                        withContext(Dispatchers.EDT) {
                            updateConnectionStatus(state, device)
                        }
                    }
                    // collectLatest only returns when cancelled, so if we get here the flow ended
                    break
                } else {
                    // No connection yet, wait and retry
                    delay(100)
                    attempts++
                }
            }

            // If we timed out waiting for connection, show disconnected
            if (attempts >= maxAttempts) {
                withContext(Dispatchers.EDT) {
                    updateConnectionStatus(RokuConnectionState.DISCONNECTED, device)
                }
            }
        }
    }

    /**
     * Updates the connection status label based on the current state.
     */
    private fun updateConnectionStatus(state: RokuConnectionState, device: RokuDevice) {
        when (state) {
            RokuConnectionState.CONNECTING -> {
                connectionStatusLabel.text = RokuBundle.message("log.status.connecting")
                connectionStatusLabel.foreground = Color.ORANGE
            }
            RokuConnectionState.CONNECTED -> {
                connectionStatusLabel.text = RokuBundle.message("log.status.connected")
                connectionStatusLabel.foreground = Color.GREEN.darker()
            }
            RokuConnectionState.DISCONNECTED -> {
                connectionStatusLabel.text = RokuBundle.message("log.status.disconnected")
                connectionStatusLabel.foreground = Color.GRAY
            }
            RokuConnectionState.CONNECTION_FAILED -> {
                connectionStatusLabel.text = RokuBundle.message("log.status.failed")
                connectionStatusLabel.foreground = Color.RED
            }
            RokuConnectionState.RECONNECTING -> {
                connectionStatusLabel.text = RokuBundle.message("log.status.reconnecting")
                connectionStatusLabel.foreground = Color.ORANGE
            }
        }
    }

    /**
     * Sets the controller for auto-scroll toggle.
     */
    fun setAutoScrollController(controller: AutoScrollController) {
        autoScrollController = controller
    }

    private fun setupDeviceComboBox() {
        deviceComboBox.apply {
            renderer = object : SimpleListCellRenderer<RokuDevice?>() {
                override fun customize(
                    list: JList<out RokuDevice?>,
                    value: RokuDevice?,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean
                ) {
                    text = value?.displayName ?: RokuBundle.message("log.device.none")
                }
            }

            addActionListener {
                val selected = selectedItem as? RokuDevice
                logService.selectDevice(selected)
            }
        }
    }

    private fun setupFilterTextField() {
        filterTextField.textEditor.emptyText.text = RokuBundle.message("log.filter.placeholder")

        filterTextField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                logService.setFilter(filterTextField.text)
            }
        })
    }

    private fun updateDeviceComboBox(devices: List<RokuDevice>) {
        val currentSelection = deviceComboBox.selectedItem as? RokuDevice
        val model = DefaultComboBoxModel<RokuDevice?>()

        // Add null option for "no device"
        model.addElement(null)

        // Add all devices
        devices.sortedBy { it.displayName }.forEach { model.addElement(it) }

        deviceComboBox.model = model

        // Restore selection
        if (currentSelection != null) {
            val matchingDevice = devices.find { it.id == currentSelection.id }
            deviceComboBox.selectedItem = matchingDevice
        }
    }

    private inner class ReconnectAction : AnAction(
        RokuBundle.message("action.reconnect"),
        RokuBundle.message("action.reconnect.description"),
        AllIcons.Actions.Refresh
    ), DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            val device = logService.selectedDevice.value ?: return
            RokuConnectionManager.getInstance().forceDisconnect(device)
            logService.selectDevice(device)  // Re-triggers connection
        }

        override fun update(e: AnActionEvent) {
            val device = logService.selectedDevice.value
            val state = device?.let {
                RokuConnectionManager.getInstance().getConnectionState(it)?.value
            }
            // Enable when connection failed or disconnected
            e.presentation.isEnabled = device != null && (
                state == RokuConnectionState.CONNECTION_FAILED ||
                state == RokuConnectionState.DISCONNECTED
            )
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

    private inner class ClearAction : AnAction(
        RokuBundle.message("action.clear"),
        RokuBundle.message("action.clear.description"),
        AllIcons.Actions.GC
    ), DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            logService.clearLogs()
        }
    }

    private inner class AutoScrollAction : ToggleAction(
        RokuBundle.message("action.autoScroll"),
        RokuBundle.message("action.autoScroll.description"),
        AllIcons.RunConfigurations.Scroll_down
    ), DumbAware {
        override fun isSelected(e: AnActionEvent): Boolean {
            return autoScrollController?.isAutoScrollEnabled ?: true
        }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            autoScrollController?.isAutoScrollEnabled = state
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

    override fun dispose() {
        scope.cancel()
    }
}

/**
 * Interface for controlling auto-scroll behavior.
 */
interface AutoScrollController {
    var isAutoScrollEnabled: Boolean
}
