// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/** Icons for Roku IDE features. */
public final class RokuIcons {
    private RokuIcons() {}

    /** 16x16 icon for Roku device. */
    public static final @NotNull Icon ROKU = IconLoader.getIcon("/icons/roku_16.svg", RokuIcons.class);

    /** 13x13 icon for tool windows. */
    public static final @NotNull Icon ROKU_13 = IconLoader.getIcon("/icons/roku_13.svg", RokuIcons.class);

    /** 16x16 icon for connected Roku device. */
    public static final @NotNull Icon ROKU_CONNECTED = AllIcons.RunConfigurations.TestState.Green2;

    /** 16x16 icon for disconnected Roku device. */
    public static final @NotNull Icon ROKU_DISCONNECTED = AllIcons.RunConfigurations.TestState.Red2;

    /** 16x16 icon for Roku device requiring authentication. */
    public static final @NotNull Icon ROKU_AUTH_REQUIRED = AllIcons.RunConfigurations.TestState.Yellow2;

    /** 13x13 icon for the Roku Log tool window. */
    public static final @NotNull Icon DEVICE_LOG = IconLoader.getIcon("/icons/roku_log_13.svg", RokuIcons.class);

    /** 13x13 icon for the Roku test results panel. */
    public static final @NotNull Icon TEST_RESULTS = IconLoader.getIcon("/icons/roku_test_13.svg", RokuIcons.class);

    /** 16x16 icon for .brs source files. */
    public static final @NotNull Icon BRS_FILE = IconLoader.getIcon("/icons/brs_file_16.svg", RokuIcons.class);
}
