// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.ui.dialogs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import org.jetbrains.kotlin.idea.roku.RokuBundle
import org.jetbrains.kotlin.idea.roku.device.model.RokuDevice
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.event.ChangeEvent

/**
 * Dialog for editing a Roku device's settings, including password.
 */
class EditDeviceDialog(
    project: Project,
    private val device: RokuDevice
) : DialogWrapper(project) {

    private val friendlyNameField = JBTextField(device.friendlyName).apply {
        emptyText.text = RokuBundle.message("dialog.editDevice.friendlyName.prompt")
    }

    private val ipAddressLabel = JBLabel(device.ipAddress)

    private val changePasswordCheckbox = JBCheckBox(
        RokuBundle.message("dialog.editDevice.changePassword")
    )

    private val passwordField = JBPasswordField().apply {
        isEnabled = false
    }

    /** The entered friendly name */
    val friendlyName: String
        get() = friendlyNameField.text.trim()

    /** Whether the password should be changed */
    val shouldChangePassword: Boolean
        get() = changePasswordCheckbox.isSelected

    /** The new password (only valid if shouldChangePassword is true) */
    val newPassword: String
        get() = String(passwordField.password)

    init {
        title = RokuBundle.message("dialog.editDevice.title")

        changePasswordCheckbox.addChangeListener { _: ChangeEvent ->
            passwordField.isEnabled = changePasswordCheckbox.isSelected
            if (!changePasswordCheckbox.isSelected) {
                passwordField.text = ""
            }
        }

        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(
                RokuBundle.message("dialog.editDevice.friendlyName"),
                friendlyNameField
            )
            .addLabeledComponent(
                RokuBundle.message("dialog.editDevice.ipAddress"),
                ipAddressLabel
            )
            .addComponent(changePasswordCheckbox)
            .addLabeledComponent(
                RokuBundle.message("dialog.editDevice.password"),
                passwordField
            )
            .panel

        panel.preferredSize = Dimension(350, panel.preferredSize.height)
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent = friendlyNameField

    override fun doValidate(): ValidationInfo? {
        if (friendlyName.isBlank()) {
            return ValidationInfo(
                RokuBundle.message("dialog.editDevice.validation.nameRequired"),
                friendlyNameField
            )
        }

        if (changePasswordCheckbox.isSelected && newPassword.isBlank()) {
            return ValidationInfo(
                RokuBundle.message("dialog.editDevice.validation.passwordRequired"),
                passwordField
            )
        }

        return null
    }
}
