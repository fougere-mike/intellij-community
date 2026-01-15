// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.run

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

/**
 * Project-level service for persisting selected device per run configuration.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "RokuSelectedDevice",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class RokuSelectedDeviceService(
    private val project: Project
) : PersistentStateComponent<RokuSelectedDeviceService.State> {

    class State {
        /** Default selected device ID */
        var selectedDeviceId: String? = null

        /** Map of run configuration name to device ID */
        var configDeviceMap: MutableMap<String, String> = mutableMapOf()
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    /**
     * Gets the selected device ID for a run configuration.
     *
     * Falls back to the default selected device if no config-specific selection.
     */
    fun getSelectedDeviceForConfig(configName: String): String? {
        return state.configDeviceMap[configName] ?: state.selectedDeviceId
    }

    /**
     * Sets the selected device for a run configuration.
     *
     * Also updates the default selection.
     */
    fun setSelectedDeviceForConfig(configName: String, deviceId: String) {
        state.configDeviceMap[configName] = deviceId
        state.selectedDeviceId = deviceId
    }

    /**
     * Clears the selection for a run configuration.
     */
    fun clearSelectionForConfig(configName: String) {
        state.configDeviceMap.remove(configName)
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): RokuSelectedDeviceService = project.service()
    }
}
