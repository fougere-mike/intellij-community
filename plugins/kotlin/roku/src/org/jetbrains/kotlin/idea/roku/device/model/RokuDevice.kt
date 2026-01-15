// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.device.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents a Roku device that can be used for deployment and debugging.
 *
 * @property id Unique identifier for the device (serial number or generated ID)
 * @property ipAddress The IP address of the device
 * @property friendlyName User-friendly name of the device
 * @property modelName Model name (e.g., "Roku Ultra")
 * @property modelNumber Model number (e.g., "4670X")
 * @property softwareVersion Current software version
 * @property serialNumber Device serial number
 * @property isManuallyAdded Whether this device was manually added by IP
 */
data class RokuDevice(
    val id: String,
    val ipAddress: String,
    var friendlyName: String = "",
    var modelName: String = "",
    var modelNumber: String = "",
    var softwareVersion: String = "",
    var serialNumber: String = "",
    var isManuallyAdded: Boolean = false,
    var lastSeen: Long = System.currentTimeMillis()
) {
    @Transient
    private val _connectionState = MutableStateFlow(RokuDeviceConnectionState.UNKNOWN)

    /** Observable connection state of this device */
    @Transient
    val connectionState: StateFlow<RokuDeviceConnectionState> = _connectionState.asStateFlow()

    /** Updates the connection state of this device */
    fun updateConnectionState(state: RokuDeviceConnectionState) {
        _connectionState.value = state
    }

    /** The current connection state value */
    val currentConnectionState: RokuDeviceConnectionState
        get() = _connectionState.value

    /** Display name for UI, falls back to model name and IP if friendly name not set */
    val displayName: String
        get() = friendlyName.ifBlank {
            if (modelName.isNotBlank()) "$modelName ($ipAddress)"
            else ipAddress
        }

    /** Whether this device is currently available */
    val isAvailable: Boolean
        get() = _connectionState.value == RokuDeviceConnectionState.AVAILABLE

    /** Whether this device requires authentication */
    val needsAuth: Boolean
        get() = _connectionState.value == RokuDeviceConnectionState.AUTH_REQUIRED ||
                _connectionState.value == RokuDeviceConnectionState.AUTH_FAILED

    /** Whether this device should be shown in the device list */
    fun hasBeenContacted(): Boolean {
        // Device has info from ECP query
        if (friendlyName.isNotBlank() || modelName.isNotBlank()) return true

        // Device has an IP address and is being checked or has a result
        if (ipAddress.isNotBlank()) {
            return when (currentConnectionState) {
                RokuDeviceConnectionState.CHECKING,
                RokuDeviceConnectionState.AVAILABLE,
                RokuDeviceConnectionState.UNAVAILABLE,
                RokuDeviceConnectionState.AUTH_REQUIRED,
                RokuDeviceConnectionState.AUTH_FAILED,
                RokuDeviceConnectionState.ERROR -> true
                else -> false
            }
        }

        return false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RokuDevice) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    companion object {
        /**
         * Creates a device with an auto-generated ID based on IP address.
         * Used for manually added devices before we know the serial number.
         */
        fun createManual(ipAddress: String): RokuDevice {
            return RokuDevice(
                id = "manual-$ipAddress",
                ipAddress = ipAddress,
                isManuallyAdded = true
            )
        }

        /**
         * Creates a device from SSDP discovery data.
         */
        fun createFromSsdp(ipAddress: String, serialNumber: String?): RokuDevice {
            val id = serialNumber ?: "ssdp-$ipAddress"
            return RokuDevice(
                id = id,
                ipAddress = ipAddress,
                serialNumber = serialNumber ?: "",
                isManuallyAdded = false
            )
        }
    }
}

/**
 * Device information retrieved from ECP (External Control Protocol).
 */
data class RokuDeviceInfo(
    val friendlyName: String = "",
    val modelName: String = "",
    val modelNumber: String = "",
    val softwareVersion: String = "",
    val softwareBuild: String = "",
    val serialNumber: String = "",
    val vendorName: String = "",
    val isDeveloperEnabled: Boolean = false,
    val isKeepAliveModeEnabled: Boolean = false,
    val networkType: String = "",
    val wifiMac: String = "",
    val ethernetMac: String = ""
) {
    /** Applies this device info to a RokuDevice, updating its properties */
    fun applyTo(device: RokuDevice) {
        device.friendlyName = friendlyName
        device.modelName = modelName
        device.modelNumber = modelNumber
        device.softwareVersion = softwareVersion
        if (serialNumber.isNotBlank() && device.serialNumber.isBlank()) {
            device.serialNumber = serialNumber
        }
    }
}

/**
 * Serializable version of RokuDevice for persistence.
 * This avoids issues with StateFlow serialization.
 * Note: Uses var properties for IntelliJ XML serialization compatibility.
 */
data class SavedRokuDevice(
    var id: String = "",
    var ipAddress: String = "",
    var friendlyName: String = "",
    var modelName: String = "",
    var modelNumber: String = "",
    var softwareVersion: String = "",
    var serialNumber: String = "",
    var isManuallyAdded: Boolean = false
) {
    fun toRokuDevice(): RokuDevice {
        return RokuDevice(
            id = id,
            ipAddress = ipAddress,
            friendlyName = friendlyName,
            modelName = modelName,
            modelNumber = modelNumber,
            softwareVersion = softwareVersion,
            serialNumber = serialNumber,
            isManuallyAdded = isManuallyAdded
        )
    }

    companion object {
        fun fromRokuDevice(device: RokuDevice): SavedRokuDevice {
            return SavedRokuDevice(
                id = device.id,
                ipAddress = device.ipAddress,
                friendlyName = device.friendlyName,
                modelName = device.modelName,
                modelNumber = device.modelNumber,
                softwareVersion = device.softwareVersion,
                serialNumber = device.serialNumber,
                isManuallyAdded = device.isManuallyAdded
            )
        }
    }
}
