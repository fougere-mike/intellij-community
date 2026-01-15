// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.ui.dialogs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBPasswordField
import com.intellij.util.ui.FormBuilder
import org.jetbrains.kotlin.idea.roku.RokuBundle
import org.jetbrains.kotlin.idea.roku.device.model.RokuDevice
import java.awt.Dimension
import javax.swing.JComponent

/**
 * Dialog for entering a Roku developer password.
 */
class PasswordDialog(
    project: Project,
    private val device: RokuDevice
) : DialogWrapper(project) {

    private val passwordField = JBPasswordField()

    /** The entered password */
    val password: String
        get() = String(passwordField.password)

    init {
        title = RokuBundle.message("dialog.password.title")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(
                RokuBundle.message("dialog.password.message", device.displayName),
                passwordField
            )
            .panel

        panel.preferredSize = Dimension(350, panel.preferredSize.height)
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent = passwordField

    override fun doValidate(): ValidationInfo? {
        if (password.isBlank()) {
            return ValidationInfo("Password is required", passwordField)
        }
        return null
    }

    companion object {
        /**
         * Shows the password dialog and returns the entered password.
         *
         * @param project The project
         * @param device The device requiring authentication
         * @return The entered password, or null if cancelled
         */
        fun showAndGet(project: Project, device: RokuDevice): String? {
            val dialog = PasswordDialog(project, device)
            return if (dialog.showAndGet()) {
                dialog.password
            } else {
                null
            }
        }
    }
}
