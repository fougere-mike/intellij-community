// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.connection

/**
 * State of a telnet connection to a Roku device's debug console.
 */
enum class RokuConnectionState {
    /** Not connected */
    DISCONNECTED,

    /** Connection attempt in progress */
    CONNECTING,

    /** Successfully connected and receiving logs */
    CONNECTED,

    /** Connection attempt failed */
    CONNECTION_FAILED,

    /** Connection was lost, attempting to reconnect */
    RECONNECTING
}
