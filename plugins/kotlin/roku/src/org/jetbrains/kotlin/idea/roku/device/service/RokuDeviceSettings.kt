// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.device.service

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.kotlin.idea.roku.device.model.SavedRokuDevice

/**
 * Persistent settings for Roku devices.
 *
 * Stores the list of known devices and discovery preferences.
 * Settings are stored at the application level (shared across projects).
 */
@State(
    name = "RokuDeviceSettings",
    storages = [Storage(value = "roku-devices.xml", roamingType = RoamingType.DISABLED)]
)
@Service(Service.Level.APP)
class RokuDeviceSettings : PersistentStateComponent<RokuDeviceSettings.State> {

    private var myState = State()

    /**
     * Serializable state class.
     */
    class State {
        /** List of saved devices */
        var savedDevices: MutableList<SavedRokuDevice> = mutableListOf()

        /** ID of the currently selected device */
        var selectedDeviceId: String? = null

        /** Whether to auto-discover devices on startup */
        var autoDiscoveryEnabled: Boolean = true

        /** Whether to auto-connect to the last used device */
        var autoConnectToLastDevice: Boolean = true

        /** SSDP discovery timeout in milliseconds */
        var ssdpDiscoveryTimeoutMs: Long = 5000

        /** Discovery interval in seconds (for periodic discovery) */
        var discoveryIntervalSeconds: Int = 30

        /** ECP query timeout in milliseconds */
        var ecpQueryTimeoutMs: Long = 5000
    }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    var savedDevices: List<SavedRokuDevice>
        get() = myState.savedDevices.toList()
        set(value) {
            myState.savedDevices = value.toMutableList()
        }

    var selectedDeviceId: String?
        get() = myState.selectedDeviceId
        set(value) {
            myState.selectedDeviceId = value
        }

    var autoDiscoveryEnabled: Boolean
        get() = myState.autoDiscoveryEnabled
        set(value) {
            myState.autoDiscoveryEnabled = value
        }

    var autoConnectToLastDevice: Boolean
        get() = myState.autoConnectToLastDevice
        set(value) {
            myState.autoConnectToLastDevice = value
        }

    var ssdpDiscoveryTimeoutMs: Long
        get() = myState.ssdpDiscoveryTimeoutMs
        set(value) {
            myState.ssdpDiscoveryTimeoutMs = value
        }

    var discoveryIntervalSeconds: Int
        get() = myState.discoveryIntervalSeconds
        set(value) {
            myState.discoveryIntervalSeconds = value
        }

    var ecpQueryTimeoutMs: Long
        get() = myState.ecpQueryTimeoutMs
        set(value) {
            myState.ecpQueryTimeoutMs = value
        }

    companion object {
        @JvmStatic
        fun getInstance(): RokuDeviceSettings = service()
    }
}
