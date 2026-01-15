// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.log

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import org.jetbrains.kotlin.idea.roku.RokuBundle
import org.jetbrains.kotlin.idea.roku.device.service.RokuDeviceService

/**
 * Factory for the Roku Log tool window.
 *
 * Creates the tool window that displays real-time logs from connected Roku devices.
 */
class RokuLogToolWindowFactory : ToolWindowFactory, DumbAware {

    companion object {
        const val TOOL_WINDOW_ID = "Roku Log"

        /**
         * Activates the Roku Log tool window and optionally selects a device.
         */
        fun activate(project: Project, device: org.jetbrains.kotlin.idea.roku.device.model.RokuDevice? = null) {
            val toolWindowManager = ToolWindowManager.getInstance(project)
            val toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID) ?: return

            toolWindow.show {
                if (device != null) {
                    org.jetbrains.kotlin.idea.roku.log.service.RokuLogService.getInstance(project).selectDevice(device)
                }
            }
        }
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val mainPanel = RokuLogMainPanel(project, toolWindow.disposable)

        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(
            mainPanel.component,
            null,
            false
        )
        content.isCloseable = false
        content.preferredFocusableComponent = mainPanel.preferredFocusComponent

        toolWindow.contentManager.addContent(content)

        // Start device discovery when tool window is created
        RokuDeviceService.getInstance().startPeriodicDiscovery()
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        // Always available - could be restricted to Roku/BRS projects in the future
        return true
    }
}
