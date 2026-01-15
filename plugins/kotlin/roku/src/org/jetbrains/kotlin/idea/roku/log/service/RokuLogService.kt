// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.log.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.kotlin.idea.roku.connection.RokuConnectionManager
import org.jetbrains.kotlin.idea.roku.connection.RokuConnectionState
import org.jetbrains.kotlin.idea.roku.device.model.RokuDevice
import org.jetbrains.kotlin.idea.roku.log.model.LogBuffer
import org.jetbrains.kotlin.idea.roku.log.model.LogEntry
import org.jetbrains.kotlin.idea.roku.log.model.LogLevel
import org.jetbrains.kotlin.idea.roku.log.model.LogParser

/**
 * Project-level service for managing Roku device logs.
 *
 * Handles:
 * - Device selection for log viewing
 * - Log collection from telnet connection
 * - Log buffering and filtering
 * - Clear functionality
 */
@Service(Service.Level.PROJECT)
class RokuLogService(
    private val project: Project
) : Disposable {

    private val LOG = Logger.getInstance(RokuLogService::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val logBuffer = LogBuffer(50_000)

    private val _selectedDevice = MutableStateFlow<RokuDevice?>(null)
    /** The device currently selected for log viewing */
    val selectedDevice: StateFlow<RokuDevice?> = _selectedDevice.asStateFlow()

    private val _filterText = MutableStateFlow("")
    /** Current filter text (supports regex) */
    val filterText: StateFlow<String> = _filterText.asStateFlow()

    private val _minLogLevel = MutableStateFlow(LogLevel.VERBOSE)
    /** Minimum log level to display */
    val minLogLevel: StateFlow<LogLevel> = _minLogLevel.asStateFlow()

    private val _newLogEntry = MutableSharedFlow<LogEntry>(extraBufferCapacity = 100)
    /** Stream of new log entries as they arrive */
    val newLogEntry: SharedFlow<LogEntry> = _newLogEntry.asSharedFlow()

    private val _clearedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emitted when the log buffer is cleared */
    val clearedEvent: SharedFlow<Unit> = _clearedEvent.asSharedFlow()

    private var logCollectionJob: Job? = null

    /**
     * Selects a device for log viewing.
     *
     * Releases the previous device's connection and acquires a new one.
     * If the same device is already selected and log collection is active,
     * this is a no-op to avoid interrupting the log stream.
     *
     * @param device The device to select, or null to disconnect
     */
    fun selectDevice(device: RokuDevice?) {
        val previousDevice = _selectedDevice.value

        // Skip if same device already selected and collection is running
        if (device != null && previousDevice?.id == device.id && logCollectionJob?.isActive == true) {
            LOG.info("Device ${device.displayName} already selected and collection active, skipping restart")
            return
        }

        // Release previous connection if different device
        if (previousDevice != null && previousDevice.id != device?.id) {
            LOG.info("Switching from ${previousDevice.displayName} to ${device?.displayName ?: "none"}")
            RokuConnectionManager.getInstance().releaseConnection(previousDevice)
            stopLogCollection()
        }

        // Clear log buffer when selecting a device to start fresh
        // This ensures we don't show stale logs from previous sessions
        if (device != null) {
            LOG.info("Clearing log buffer for fresh connection to ${device.displayName}")
            logBuffer.clear()
            // Notify UI to clear display
            scope.launch {
                _clearedEvent.emit(Unit)
            }
        }

        _selectedDevice.value = device

        if (device != null && logCollectionJob?.isActive != true) {
            startLogCollection(device)
        }
    }

    /**
     * Starts collecting logs from the specified device.
     */
    private fun startLogCollection(device: RokuDevice) {
        logCollectionJob?.cancel()

        logCollectionJob = scope.launch {
            val connectionManager = RokuConnectionManager.getInstance()
            val connection = connectionManager.acquireConnection(device)

            LOG.info("Starting log collection for ${device.displayName}, connectionState=${connection.state.value}")

            // Connect if needed and wait for connection to be established
            if (connection.state.value == RokuConnectionState.DISCONNECTED) {
                LOG.info("Connection disconnected, calling connect()")
                connection.connect()
                // Wait for connection to be established before collecting
                val finalState = connection.state.first {
                    it == RokuConnectionState.CONNECTED || it == RokuConnectionState.CONNECTION_FAILED
                }
                LOG.info("Connection state after wait: $finalState")
            }

            if (connection.state.value == RokuConnectionState.CONNECTED) {
                LOG.info("Collecting from connection logs flow")
                var lineCount = 0
                connection.logs.collect { rawLine ->
                    lineCount++
                    if (lineCount <= 5 || lineCount % 100 == 0) {
                        LOG.info("Received line #$lineCount: ${rawLine.take(60)}...")
                    }
                    val entry = LogParser.parse(rawLine)
                    val bufferedEntry = logBuffer.add(entry)
                    val subscriberCount = _newLogEntry.subscriptionCount.value
                    LOG.info("Emitting entry #$lineCount to _newLogEntry, subscribers=$subscriberCount")
                    _newLogEntry.emit(bufferedEntry)
                }
                LOG.info("Log collection flow ended after $lineCount lines")
            } else {
                LOG.warn("Cannot collect logs, connection state: ${connection.state.value}")
            }
        }
    }

    /**
     * Stops log collection.
     */
    private fun stopLogCollection() {
        logCollectionJob?.cancel()
        logCollectionJob = null
    }

    /**
     * Gets all logs in the buffer.
     *
     * @return All log entries, oldest first
     */
    fun getAllLogs(): List<LogEntry> {
        return logBuffer.getAll()
    }

    /**
     * Gets logs filtered by current filter text and log level.
     *
     * @return Filtered log entries, oldest first
     */
    fun getFilteredLogs(): List<LogEntry> {
        val filter = filterText.value
        val minLevel = minLogLevel.value

        return logBuffer.filter { entry ->
            entry.level.priority >= minLevel.priority &&
            (filter.isEmpty() || matchesFilter(entry, filter))
        }
    }

    /**
     * Checks if an entry matches the current filter.
     *
     * Supports regex patterns, falls back to substring match if regex is invalid.
     */
    private fun matchesFilter(entry: LogEntry, filter: String): Boolean {
        return try {
            val regex = Regex(filter, RegexOption.IGNORE_CASE)
            entry.rawLine.contains(regex)
        } catch (e: Exception) {
            // Invalid regex, fall back to contains
            entry.rawLine.contains(filter, ignoreCase = true)
        }
    }

    /**
     * Sets the filter text.
     *
     * @param filter The filter text (supports regex)
     */
    fun setFilter(filter: String) {
        _filterText.value = filter
    }

    /**
     * Sets the minimum log level to display.
     *
     * @param level The minimum log level
     */
    fun setMinLogLevel(level: LogLevel) {
        _minLogLevel.value = level
    }

    /**
     * Clears all logs from the buffer.
     *
     * New logs will continue to be collected and displayed.
     */
    fun clearLogs() {
        logBuffer.clear()
        scope.launch {
            _clearedEvent.emit(Unit)
        }
        LOG.info("Log buffer cleared")
    }

    /**
     * Gets the current connection state for the selected device.
     *
     * @return The connection state flow, or null if no device selected
     */
    fun getConnectionState(): StateFlow<RokuConnectionState>? {
        val device = _selectedDevice.value ?: return null
        return RokuConnectionManager.getInstance().getConnectionState(device)
    }

    /**
     * Gets the number of entries in the buffer.
     */
    fun getBufferSize(): Int {
        return logBuffer.size()
    }

    override fun dispose() {
        _selectedDevice.value?.let { device ->
            RokuConnectionManager.getInstance().releaseConnection(device)
        }
        logCollectionJob?.cancel()
        scope.cancel()
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): RokuLogService = project.service()
    }
}
