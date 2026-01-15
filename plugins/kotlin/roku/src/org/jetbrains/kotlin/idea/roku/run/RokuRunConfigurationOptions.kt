// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.run

import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.openapi.components.StoredProperty

/**
 * Options/state for a Roku run configuration.
 *
 * These are serialized to the run configuration XML.
 */
class RokuRunConfigurationOptions : RunConfigurationOptions() {

    /** Whether to use the device selected in the toolbar dropdown */
    private val useSelectedDeviceProperty: StoredProperty<Boolean> =
        property(true).provideDelegate(this, "useSelectedDevice")

    var useSelectedDevice: Boolean
        get() = useSelectedDeviceProperty.getValue(this)
        set(value) = useSelectedDeviceProperty.setValue(this, value)

    /** Override device IP if not using selected device */
    private val deviceIpProperty: StoredProperty<String?> =
        string("").provideDelegate(this, "deviceIp")

    var deviceIp: String?
        get() = deviceIpProperty.getValue(this)
        set(value) = deviceIpProperty.setValue(this, value)

    /** Gradle task to run for building (default: installRoku) */
    private val gradleTaskProperty: StoredProperty<String?> =
        string("installRoku").provideDelegate(this, "gradleTask")

    var gradleTask: String?
        get() = gradleTaskProperty.getValue(this)
        set(value) = gradleTaskProperty.setValue(this, value)

    /** Whether to package before installing */
    private val packageFirstProperty: StoredProperty<Boolean> =
        property(true).provideDelegate(this, "packageFirst")

    var packageFirst: Boolean
        get() = packageFirstProperty.getValue(this)
        set(value) = packageFirstProperty.setValue(this, value)

    /** Module name for the Roku app */
    private val moduleNameProperty: StoredProperty<String?> =
        string("").provideDelegate(this, "moduleName")

    var moduleName: String?
        get() = moduleNameProperty.getValue(this)
        set(value) = moduleNameProperty.setValue(this, value)
}
