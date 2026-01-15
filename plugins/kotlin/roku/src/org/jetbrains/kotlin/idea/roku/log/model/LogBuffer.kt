// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.log.model

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe circular buffer for storing log entries.
 *
 * When the buffer reaches max capacity, oldest entries are removed to make room for new ones.
 *
 * @param maxSize Maximum number of entries to retain
 */
class LogBuffer(private val maxSize: Int = 50_000) {

    private val entries = ArrayDeque<LogEntry>(minOf(maxSize, 10000))
    private val lock = ReentrantReadWriteLock()
    private var nextLineNumber = 1

    /**
     * Adds an entry to the buffer.
     *
     * If the buffer is full, removes the oldest entry first.
     * Assigns a line number to the entry.
     *
     * @param entry The entry to add (lineNumber will be overwritten)
     * @return The entry with its assigned line number
     */
    fun add(entry: LogEntry): LogEntry {
        return lock.write {
            val numberedEntry = entry.copy(lineNumber = nextLineNumber++)

            if (entries.size >= maxSize) {
                entries.removeFirst()
            }
            entries.addLast(numberedEntry)

            numberedEntry
        }
    }

    /**
     * Gets all entries in the buffer.
     *
     * @return A copy of all entries, oldest first
     */
    fun getAll(): List<LogEntry> {
        return lock.read { entries.toList() }
    }

    /**
     * Gets entries matching a filter.
     *
     * @param predicate The filter predicate
     * @return Matching entries, oldest first
     */
    fun filter(predicate: (LogEntry) -> Boolean): List<LogEntry> {
        return lock.read { entries.filter(predicate) }
    }

    /**
     * Clears all entries from the buffer.
     *
     * Also resets the line number counter.
     */
    fun clear() {
        lock.write {
            entries.clear()
            nextLineNumber = 1
        }
    }

    /**
     * Gets the current number of entries in the buffer.
     */
    fun size(): Int {
        return lock.read { entries.size }
    }

    /**
     * Checks if the buffer is empty.
     */
    fun isEmpty(): Boolean {
        return lock.read { entries.isEmpty() }
    }

    /**
     * Gets the most recent entry, if any.
     */
    fun lastOrNull(): LogEntry? {
        return lock.read { entries.lastOrNull() }
    }

    /**
     * Gets entries added after a certain line number.
     *
     * @param afterLineNumber Only return entries with line number greater than this
     * @return Matching entries, oldest first
     */
    fun getEntriesAfter(afterLineNumber: Int): List<LogEntry> {
        return lock.read {
            entries.filter { it.lineNumber > afterLineNumber }
        }
    }
}
