// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.run

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import org.jetbrains.kotlin.idea.roku.RokuBundle
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings editor UI for Roku run configuration.
 */
class RokuRunConfigurationEditor(
    private val project: Project
) : SettingsEditor<RokuRunConfiguration>() {

    private val useSelectedDeviceCheckbox = JBCheckBox("Use device from toolbar")
    private val deviceIpField = JBTextField()
    private val gradleTaskField = JBTextField()
    private val packageFirstCheckbox = JBCheckBox("Package before install")
    private val moduleNameField = JBTextField()

    private val mainPanel: JPanel

    init {
        // Disable device IP field when using selected device
        useSelectedDeviceCheckbox.addActionListener {
            deviceIpField.isEnabled = !useSelectedDeviceCheckbox.isSelected
        }

        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(useSelectedDeviceCheckbox)
            .addLabeledComponent("Device IP:", deviceIpField)
            .addSeparator()
            .addLabeledComponent("Gradle task:", gradleTaskField)
            .addComponent(packageFirstCheckbox)
            .addLabeledComponent("Module:", moduleNameField)
            .panel
    }

    override fun createEditor(): JComponent = mainPanel

    override fun resetEditorFrom(config: RokuRunConfiguration) {
        useSelectedDeviceCheckbox.isSelected = config.useSelectedDevice
        deviceIpField.text = config.deviceIp ?: ""
        deviceIpField.isEnabled = !config.useSelectedDevice
        gradleTaskField.text = config.gradleTask ?: "installRokuApp"
        packageFirstCheckbox.isSelected = config.packageFirst
        moduleNameField.text = config.moduleName ?: ""
    }

    override fun applyEditorTo(config: RokuRunConfiguration) {
        config.useSelectedDevice = useSelectedDeviceCheckbox.isSelected
        config.deviceIp = deviceIpField.text.takeIf { it.isNotBlank() }
        config.gradleTask = gradleTaskField.text.takeIf { it.isNotBlank() }
        config.packageFirst = packageFirstCheckbox.isSelected
        config.moduleName = moduleNameField.text.takeIf { it.isNotBlank() }
    }
}
