// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.ui.dialogs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import org.jetbrains.kotlin.idea.roku.RokuBundle
import java.awt.Dimension
import java.util.regex.Pattern
import javax.swing.JComponent

/**
 * Dialog for manually adding a Roku device by IP address.
 */
class AddDeviceDialog(project: Project) : DialogWrapper(project) {

    private val ipAddressField = JBTextField().apply {
        emptyText.text = RokuBundle.message("dialog.addDevice.ipAddress.prompt")
    }

    /** The entered IP address */
    val ipAddress: String
        get() = ipAddressField.text.trim()

    init {
        title = RokuBundle.message("dialog.addDevice.title")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(
                RokuBundle.message("dialog.addDevice.ipAddress"),
                ipAddressField
            )
            .panel

        panel.preferredSize = Dimension(300, panel.preferredSize.height)
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent = ipAddressField

    override fun doValidate(): ValidationInfo? {
        val ip = ipAddress
        if (ip.isBlank()) {
            return ValidationInfo(
                "IP address is required",
                ipAddressField
            )
        }

        // Basic IP address validation
        val ipPattern = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        )
        if (!ipPattern.matcher(ip).matches()) {
            return ValidationInfo(
                "Please enter a valid IP address (e.g., 192.168.1.100)",
                ipAddressField
            )
        }

        return null
    }
}
