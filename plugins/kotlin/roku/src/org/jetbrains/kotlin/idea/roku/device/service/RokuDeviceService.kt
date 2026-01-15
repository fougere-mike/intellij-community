// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.device.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.kotlin.idea.roku.device.discovery.SsdpDiscovery
import org.jetbrains.kotlin.idea.roku.device.ecp.EcpClient
import org.jetbrains.kotlin.idea.roku.device.model.RokuDevice
import org.jetbrains.kotlin.idea.roku.device.model.RokuDeviceConnectionState
import org.jetbrains.kotlin.idea.roku.device.model.SavedRokuDevice

/**
 * Application-level service for managing Roku devices.
 *
 * Handles:
 * - Device discovery via SSDP
 * - Device persistence
 * - Device state management
 * - Selection tracking
 */
@Service(Service.Level.APP)
class RokuDeviceService : Disposable {
    private val LOG = Logger.getInstance(RokuDeviceService::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settings = RokuDeviceSettings.getInstance()

    private val ssdpDiscovery = SsdpDiscovery()
    private val ecpClient = EcpClient()

    private val _devices = MutableStateFlow<Map<String, RokuDevice>>(emptyMap())
    /** Observable map of all known devices keyed by ID */
    val devices: StateFlow<Map<String, RokuDevice>> = _devices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<RokuDevice?>(null)
    /** The currently selected device for deployment */
    val selectedDevice: StateFlow<RokuDevice?> = _selectedDevice.asStateFlow()

    private var discoveryJob: Job? = null
    private var isInitialized = false

    init {
        // Load persisted devices on startup
        loadPersistedDevices()
        isInitialized = true

        // Start auto-discovery if enabled
        if (settings.autoDiscoveryEnabled) {
            startPeriodicDiscovery()
        }
    }

    /**
     * Loads devices from persistent storage.
     */
    private fun loadPersistedDevices() {
        val savedDevices = settings.savedDevices
        val deviceMap = savedDevices.associate { saved ->
            val device = saved.toRokuDevice()
            device.id to device
        }
        _devices.value = deviceMap

        // Restore selection
        settings.selectedDeviceId?.let { selectedId ->
            _selectedDevice.value = deviceMap[selectedId]
        }

        LOG.info("Loaded ${deviceMap.size} persisted device(s)")
    }

    /**
     * Persists current devices to storage.
     */
    private fun persistDevices() {
        if (!isInitialized) return

        val savedDevices = _devices.value.values.map { SavedRokuDevice.fromRokuDevice(it) }
        settings.savedDevices = savedDevices
        settings.selectedDeviceId = _selectedDevice.value?.id
    }

    /**
     * Starts periodic SSDP discovery.
     */
    fun startPeriodicDiscovery() {
        stopDiscovery()
        discoveryJob = scope.launch {
            while (isActive) {
                try {
                    discoverDevices()
                } catch (e: Exception) {
                    LOG.warn("Periodic discovery failed", e)
                }
                delay(settings.discoveryIntervalSeconds * 1000L)
            }
        }
        LOG.info("Started periodic device discovery (interval: ${settings.discoveryIntervalSeconds}s)")
    }

    /**
     * Stops periodic discovery.
     */
    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
    }

    /**
     * Performs a single SSDP discovery and updates the device list.
     */
    suspend fun discoverDevices() {
        LOG.info("Starting device discovery...")

        val discoveredDevices = ssdpDiscovery.discover(settings.ssdpDiscoveryTimeoutMs.toInt())

        discoveredDevices.forEach { device ->
            addOrUpdateDevice(device)
        }

        LOG.info("Discovery complete. Found ${discoveredDevices.size} device(s)")
    }

    /**
     * Manually adds a device by IP address.
     *
     * @param ipAddress The IP address of the device
     * @return The created or existing device
     */
    fun addManualDevice(ipAddress: String): RokuDevice {
        // Check if device already exists with this IP
        val existingDevice = _devices.value.values.find { it.ipAddress == ipAddress }
        if (existingDevice != null) {
            LOG.info("Device with IP $ipAddress already exists")
            return existingDevice
        }

        val device = RokuDevice.createManual(ipAddress)
        device.updateConnectionState(RokuDeviceConnectionState.CONNECTING)
        addOrUpdateDevice(device)

        // Fetch device info asynchronously
        scope.launch {
            refreshDeviceInfo(device)
        }

        return device
    }

    /**
     * Adds or updates a device in the device map.
     */
    private fun addOrUpdateDevice(device: RokuDevice) {
        val currentDevices = _devices.value.toMutableMap()
        val existingDevice = currentDevices[device.id]

        if (existingDevice != null) {
            // Update existing device with new info
            existingDevice.apply {
                if (device.friendlyName.isNotBlank()) friendlyName = device.friendlyName
                if (device.modelName.isNotBlank()) modelName = device.modelName
                if (device.modelNumber.isNotBlank()) modelNumber = device.modelNumber
                if (device.softwareVersion.isNotBlank()) softwareVersion = device.softwareVersion
                if (device.serialNumber.isNotBlank()) serialNumber = device.serialNumber
                lastSeen = System.currentTimeMillis()
            }
        } else {
            currentDevices[device.id] = device
        }

        _devices.value = currentDevices
        persistDevices()
    }

    /**
     * Removes a device from the device list.
     *
     * @param deviceId The ID of the device to remove
     */
    fun removeDevice(deviceId: String) {
        val currentDevices = _devices.value.toMutableMap()
        val removed = currentDevices.remove(deviceId)

        if (removed != null) {
            _devices.value = currentDevices

            // Clear selection if this was the selected device
            if (_selectedDevice.value?.id == deviceId) {
                _selectedDevice.value = null
            }

            persistDevices()
            LOG.info("Removed device: ${removed.displayName}")
        }
    }

    /**
     * Selects a device for deployment.
     *
     * @param device The device to select, or null to clear selection
     */
    fun selectDevice(device: RokuDevice?) {
        _selectedDevice.value = device
        persistDevices()
        LOG.info("Selected device: ${device?.displayName ?: "none"}")
    }

    /**
     * Gets a device by ID.
     *
     * @param deviceId The device ID
     * @return The device if found, null otherwise
     */
    fun getDevice(deviceId: String): RokuDevice? {
        return _devices.value[deviceId]
    }

    /**
     * Gets a device by IP address.
     *
     * @param ipAddress The IP address
     * @return The device if found, null otherwise
     */
    fun getDeviceByIp(ipAddress: String): RokuDevice? {
        return _devices.value.values.find { it.ipAddress == ipAddress }
    }

    /**
     * Gets all devices as a list.
     */
    fun getDeviceList(): List<RokuDevice> {
        return _devices.value.values.toList()
    }

    /**
     * Refreshes device information via ECP.
     *
     * @param device The device to refresh
     */
    suspend fun refreshDeviceInfo(device: RokuDevice) {
        device.updateConnectionState(RokuDeviceConnectionState.CONNECTING)

        try {
            val info = ecpClient.getDeviceInfo(device.ipAddress)
            info.applyTo(device)
            device.updateConnectionState(RokuDeviceConnectionState.CONNECTED)
            addOrUpdateDevice(device)
            LOG.info("Refreshed device info for ${device.displayName}")
        } catch (e: Exception) {
            LOG.warn("Failed to refresh device info for ${device.ipAddress}", e)
            device.updateConnectionState(RokuDeviceConnectionState.DISCONNECTED)
        }
    }

    /**
     * Pings a device to check connectivity.
     *
     * @param device The device to ping
     * @return true if reachable, false otherwise
     */
    suspend fun pingDevice(device: RokuDevice): Boolean {
        return ecpClient.ping(device.ipAddress)
    }

    /**
     * Refreshes all devices' connection status.
     */
    suspend fun refreshAllDevices() {
        _devices.value.values.forEach { device ->
            refreshDeviceInfo(device)
        }
    }

    override fun dispose() {
        scope.cancel()
    }

    companion object {
        @JvmStatic
        fun getInstance(): RokuDeviceService = service()
    }
}
