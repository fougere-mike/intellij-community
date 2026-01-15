// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.ui.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import org.jetbrains.kotlin.idea.roku.RokuBundle

/**
 * Factory for the Roku Device Manager tool window.
 *
 * Creates the tool window that displays all discovered and manually added Roku devices.
 */
class RokuDeviceManagerToolWindowFactory : ToolWindowFactory, DumbAware {

    companion object {
        const val TOOL_WINDOW_ID = "Roku Devices"
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = RokuDeviceManagerPanel(project, toolWindow.disposable)

        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(
            panel,
            RokuBundle.message("toolwindow.devices.title"),
            false
        )
        content.isCloseable = false

        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        // Always available - could be restricted to Roku/BRS projects in the future
        return true
    }
}
