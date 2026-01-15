// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.log.model

/**
 * Represents a single log entry from a Roku device.
 *
 * @property lineNumber Sequential line number in the buffer
 * @property timestamp Timestamp from the log line (e.g., "14:23:45.123")
 * @property level The log level
 * @property component The component/channel that produced the log (e.g., "BRIGHTSCRIPT")
 * @property message The log message content
 * @property rawLine The original unprocessed log line
 */
data class LogEntry(
    val lineNumber: Int,
    val timestamp: String,
    val level: LogLevel,
    val component: String?,
    val message: String,
    val rawLine: String
) {
    /**
     * Creates a formatted display string for this log entry.
     */
    fun toDisplayString(): String {
        val componentPart = component?.let { "[$it] " } ?: ""
        val timestampPart = if (timestamp.isNotBlank()) "$timestamp " else ""
        return "$timestampPart${level.displayName} $componentPart$message"
    }
}
