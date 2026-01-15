// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.log.model

/**
 * Log levels for Roku console output.
 */
enum class LogLevel(val displayName: String, val priority: Int) {
    VERBOSE("V", 0),
    DEBUG("D", 1),
    INFO("I", 2),
    WARNING("W", 3),
    ERROR("E", 4);

    companion object {
        /**
         * Parses a log level from a string.
         *
         * @param level The level string (e.g., "INFO", "I", "ERROR", "E")
         * @return The corresponding LogLevel, defaults to INFO if unknown
         */
        fun fromString(level: String): LogLevel {
            return when (level.uppercase().trim()) {
                "V", "VERBOSE" -> VERBOSE
                "D", "DEBUG" -> DEBUG
                "I", "INFO" -> INFO
                "W", "WARN", "WARNING" -> WARNING
                "E", "ERR", "ERROR" -> ERROR
                else -> INFO
            }
        }
    }
}
