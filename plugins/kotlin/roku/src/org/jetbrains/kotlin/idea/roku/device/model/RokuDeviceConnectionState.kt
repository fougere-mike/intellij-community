// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.device.model

/**
 * Represents the availability state of a Roku device.
 */
enum class RokuDeviceConnectionState {
    /** Initial state, device hasn't been checked yet */
    UNKNOWN,

    /** SSDP discovery in progress for this device */
    DISCOVERING,

    /** Checking device availability */
    CHECKING,

    /** Device is reachable and (if required) authenticated */
    AVAILABLE,

    /** Device is not reachable */
    UNAVAILABLE,

    /** Device is reachable but requires authentication */
    AUTH_REQUIRED,

    /** Authentication was attempted but failed */
    AUTH_FAILED,

    /** Error occurred while checking device */
    ERROR
}
