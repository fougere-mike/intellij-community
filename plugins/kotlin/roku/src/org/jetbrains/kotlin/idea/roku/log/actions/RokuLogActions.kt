// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.log.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import org.jetbrains.kotlin.idea.roku.RokuBundle
import org.jetbrains.kotlin.idea.roku.log.service.RokuLogService

/**
 * Action to clear the Roku log buffer.
 */
class RokuLogClearAction : AnAction(
    RokuBundle.message("action.clear"),
    RokuBundle.message("action.clear.description"),
    AllIcons.Actions.GC
), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        RokuLogService.getInstance(project).clearLogs()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

/**
 * Toggle action for auto-scroll in the Roku log window.
 *
 * Note: This is a placeholder action. The actual auto-scroll state is managed
 * by the RokuLogEditorPanel through the AutoScrollController interface.
 * This action is registered for the toolbar but the panel handles the state.
 */
class RokuLogAutoScrollAction : ToggleAction(
    RokuBundle.message("action.autoScroll"),
    RokuBundle.message("action.autoScroll.description"),
    AllIcons.RunConfigurations.Scroll_down
), DumbAware {

    // State is managed at the panel level
    // This is a fallback implementation for when the action is used outside the panel
    private var autoScrollEnabled = true

    override fun isSelected(e: AnActionEvent): Boolean {
        return autoScrollEnabled
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        autoScrollEnabled = state
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
}
