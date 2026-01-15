// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.log.model

import java.util.regex.Pattern

/**
 * Parser for Roku console log output.
 *
 * Attempts to extract timestamp, log level, component, and message from raw log lines.
 */
object LogParser {

    // Pattern for log lines with timestamp, level, and optional component
    // Examples:
    //   14:23:45.123 INFO [Main] Application started
    //   14:23:45.234 DEBUG Network: Connecting to server
    //   BRIGHTSCRIPT: Error in line 42
    private val LOG_PATTERN = Pattern.compile(
        """^(?:(\d{2}:\d{2}:\d{2}(?:\.\d{3})?)\s+)?""" +  // Optional timestamp
        """(?:(VERBOSE|DEBUG|INFO|WARN|WARNING|ERROR|V|D|I|W|E)\s+)?""" +  // Optional level
        """(?:\[([^\]]+)\]\s*)?""" +  // Optional [component]
        """(.*)$""",  // Message
        Pattern.CASE_INSENSITIVE
    )

    // Pattern for component prefix (e.g., "BRIGHTSCRIPT:")
    private val COMPONENT_PREFIX_PATTERN = Pattern.compile(
        """^([A-Z][A-Z0-9_]+):\s*(.*)$""",
        Pattern.CASE_INSENSITIVE
    )

    // Patterns for detecting log level from message content
    private val ERROR_INDICATORS = listOf(
        "error", "exception", "failed", "failure", "crash"
    )
    private val WARNING_INDICATORS = listOf(
        "warning", "warn", "deprecated"
    )

    /**
     * Parses a raw log line into a LogEntry.
     *
     * @param rawLine The raw log line from the device
     * @param lineNumber The line number to assign (0 means it will be assigned by LogBuffer)
     * @return A LogEntry with parsed fields
     */
    fun parse(rawLine: String, lineNumber: Int = 0): LogEntry {
        val trimmedLine = rawLine.trim()

        // First try the standard pattern
        val matcher = LOG_PATTERN.matcher(trimmedLine)
        if (matcher.matches()) {
            val timestamp = matcher.group(1) ?: ""
            val levelStr = matcher.group(2)
            var component = matcher.group(3)
            var message = matcher.group(4) ?: ""

            // Check for component prefix in message (e.g., "BRIGHTSCRIPT: ...")
            if (component == null && message.isNotEmpty()) {
                val prefixMatcher = COMPONENT_PREFIX_PATTERN.matcher(message)
                if (prefixMatcher.matches()) {
                    component = prefixMatcher.group(1)
                    message = prefixMatcher.group(2)
                }
            }

            // Determine level
            val level = when {
                levelStr != null -> LogLevel.fromString(levelStr)
                else -> inferLevelFromMessage(message)
            }

            return LogEntry(
                lineNumber = lineNumber,
                timestamp = timestamp,
                level = level,
                component = component,
                message = message,
                rawLine = rawLine
            )
        }

        // Fallback: treat entire line as message
        return LogEntry(
            lineNumber = lineNumber,
            timestamp = "",
            level = inferLevelFromMessage(trimmedLine),
            component = null,
            message = trimmedLine,
            rawLine = rawLine
        )
    }

    /**
     * Infers log level from message content.
     *
     * @param message The message to analyze
     * @return Inferred log level
     */
    private fun inferLevelFromMessage(message: String): LogLevel {
        val lowerMessage = message.lowercase()

        if (ERROR_INDICATORS.any { lowerMessage.contains(it) }) {
            return LogLevel.ERROR
        }
        if (WARNING_INDICATORS.any { lowerMessage.contains(it) }) {
            return LogLevel.WARNING
        }

        return LogLevel.INFO
    }
}
