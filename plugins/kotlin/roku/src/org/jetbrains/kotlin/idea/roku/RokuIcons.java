// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku;

import com.intellij.icons.AllIcons;
import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Icons for Roku IDE features.
 * Note: Until custom SVG icons are created, we use placeholder icons from AllIcons.
 */
public final class RokuIcons {
    private RokuIcons() {
    }

    // Placeholder icons - replace with custom Roku icons when available
    /** 16x16 icon for Roku device */
    public static final @NotNull Icon ROKU = AllIcons.RunConfigurations.Remote;

    /** 13x13 icon for tool windows */
    public static final @NotNull Icon ROKU_13 = AllIcons.RunConfigurations.Remote;

    /** 16x16 icon for connected Roku device */
    public static final @NotNull Icon ROKU_CONNECTED = AllIcons.RunConfigurations.TestState.Green2;

    /** 16x16 icon for disconnected Roku device */
    public static final @NotNull Icon ROKU_DISCONNECTED = AllIcons.RunConfigurations.TestState.Red2;

    /** 16x16 icon for Roku device requiring authentication */
    public static final @NotNull Icon ROKU_AUTH_REQUIRED = AllIcons.RunConfigurations.TestState.Yellow2;
}
