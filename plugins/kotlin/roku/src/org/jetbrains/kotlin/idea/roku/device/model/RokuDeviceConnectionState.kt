// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.device.model

/**
 * Represents the connection state of a Roku device.
 */
enum class RokuDeviceConnectionState {
    /** Initial state, device hasn't been checked yet */
    UNKNOWN,

    /** SSDP discovery in progress for this device */
    DISCOVERING,

    /** Attempting to connect to the device */
    CONNECTING,

    /** Successfully connected and (if required) authenticated */
    CONNECTED,

    /** Device was connected but is now unreachable */
    DISCONNECTED,

    /** Device is reachable but requires authentication */
    AUTH_REQUIRED,

    /** Authentication was attempted but failed */
    AUTH_FAILED,

    /** Connection error occurred */
    ERROR
}
