// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Main panel for the Roku Log tool window.
 *
 * Contains a header panel with device selection and filter controls,
 * and an editor panel for displaying logs.
 */
class RokuLogMainPanel(
    private val project: Project,
    parentDisposable: Disposable
) : Disposable {

    private val headerPanel: RokuLogHeaderPanel
    private val editorPanel: RokuLogEditorPanel
    private val rootPanel: JPanel

    /** The root component to add to the tool window */
    val component: JComponent
        get() = rootPanel

    /** The component that should receive focus */
    val preferredFocusComponent: JComponent
        get() = editorPanel.component

    init {
        Disposer.register(parentDisposable, this)

        headerPanel = RokuLogHeaderPanel(project, this)
        editorPanel = RokuLogEditorPanel(project, this)

        // Connect header panel to editor panel for auto-scroll control
        headerPanel.setAutoScrollController(editorPanel)

        rootPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty()
            add(headerPanel.component, BorderLayout.NORTH)
            add(editorPanel.component, BorderLayout.CENTER)
        }
    }

    override fun dispose() {
        // Cleanup handled by child components
    }
}
