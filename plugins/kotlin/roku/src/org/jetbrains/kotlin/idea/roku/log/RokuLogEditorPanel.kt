// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.kotlin.idea.roku.log.model.LogEntry
import org.jetbrains.kotlin.idea.roku.log.model.LogLevel
import org.jetbrains.kotlin.idea.roku.log.service.RokuLogService
import java.awt.BorderLayout
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Editor-based panel for displaying Roku logs.
 *
 * Features:
 * - Efficient text appending to editor document
 * - Auto-scroll that disables when user scrolls back
 * - Manual re-enable of auto-scroll via toggle button
 */
class RokuLogEditorPanel(
    private val project: Project,
    parentDisposable: Disposable
) : Disposable, AutoScrollController {

    private val LOG = Logger.getInstance(RokuLogEditorPanel::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val logService = RokuLogService.getInstance(project)

    private val document = DocumentImpl("", true)
    private val editor: EditorEx
    private val rootPanel: JPanel

    private val _autoScrollEnabled = AtomicBoolean(true)
    private val userScrolledBack = AtomicBoolean(false)

    /** The root component */
    val component: JComponent
        get() = rootPanel

    override var isAutoScrollEnabled: Boolean
        get() = _autoScrollEnabled.get()
        set(value) {
            _autoScrollEnabled.set(value)
            if (value) {
                scrollToEnd()
                userScrolledBack.set(false)
            }
        }

    init {
        Disposer.register(parentDisposable, this)

        editor = createEditor()

        rootPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty()
            add(editor.component, BorderLayout.CENTER)
        }

        // Track user scroll position to auto-disable auto-scroll
        editor.scrollingModel.addVisibleAreaListener { _ ->
            if (!_autoScrollEnabled.get()) return@addVisibleAreaListener

            // Check if user scrolled away from bottom
            val scrollPane = editor.scrollPane
            val viewport = scrollPane.viewport
            val viewRect = viewport.viewRect
            val viewSize = viewport.viewSize

            val atBottom = viewRect.y + viewRect.height >= viewSize.height - 50

            if (!atBottom && !userScrolledBack.get()) {
                userScrolledBack.set(true)
                // User scrolled back - disable auto-scroll
                _autoScrollEnabled.set(false)
            }
        }

        // Subscribe to new log entries
        scope.launch {
            LOG.info("RokuLogEditorPanel: Starting newLogEntry subscription")
            var entryCount = 0
            logService.newLogEntry.collect { entry ->
                entryCount++
                LOG.info("RokuLogEditorPanel: Received entry #$entryCount (buffer size: ${logService.getBufferSize()})")
                appendLogEntry(entry)
            }
        }

        // Subscribe to clear events
        scope.launch {
            logService.clearedEvent.collectLatest {
                clearDocument()
            }
        }

        // Subscribe to filter changes to re-render
        scope.launch {
            logService.filterText.collectLatest {
                // Re-render filtered logs
                reloadFilteredLogs()
            }
        }

        scope.launch {
            logService.minLogLevel.collectLatest {
                // Re-render filtered logs
                reloadFilteredLogs()
            }
        }
    }

    private fun createEditor(): EditorEx {
        val factory = EditorFactory.getInstance()
        val editor = factory.createViewer(document, project, EditorKind.CONSOLE) as EditorEx

        editor.apply {
            setCaretEnabled(false)
            setCaretVisible(false)

            settings.apply {
                isLineNumbersShown = true
                isLineMarkerAreaShown = false
                isFoldingOutlineShown = false
                isRightMarginShown = false
                additionalLinesCount = 0
                additionalColumnsCount = 0
                isUseSoftWraps = true
            }

            // Apply console colors
            colorsScheme = EditorColorsManager.getInstance().globalScheme

            // Readonly
            isRendererMode = true
        }

        Disposer.register(this) {
            factory.releaseEditor(editor)
        }

        return editor
    }

    private fun appendLogEntry(entry: LogEntry) {
        ApplicationManager.getApplication().invokeLater {
            val text = formatLogEntry(entry)

            // Check filter before appending
            val filter = logService.filterText.value
            val minLevel = logService.minLogLevel.value

            if (entry.level.priority >= minLevel.priority &&
                (filter.isEmpty() || matchesFilter(entry, filter))) {
                LOG.info("RokuLogEditorPanel: appendLogEntry() on EDT, inserting at position ${document.textLength}, entry: ${entry.rawLine.take(40)}...")
                document.insertString(document.textLength, text)

                if (_autoScrollEnabled.get()) {
                    scrollToEnd()
                }
            }
        }
    }

    private fun matchesFilter(entry: LogEntry, filter: String): Boolean {
        return try {
            val regex = Regex(filter, RegexOption.IGNORE_CASE)
            entry.rawLine.contains(regex)
        } catch (e: Exception) {
            entry.rawLine.contains(filter, ignoreCase = true)
        }
    }

    private fun formatLogEntry(entry: LogEntry): String {
        val levelPrefix = when (entry.level) {
            LogLevel.ERROR -> "E"
            LogLevel.WARNING -> "W"
            LogLevel.INFO -> "I"
            LogLevel.DEBUG -> "D"
            LogLevel.VERBOSE -> "V"
        }

        val component = entry.component?.let { "[$it] " } ?: ""
        val timestamp = if (entry.timestamp.isNotBlank()) "${entry.timestamp} " else ""

        return "$timestamp$levelPrefix $component${entry.message}\n"
    }

    private fun clearDocument() {
        LOG.info("RokuLogEditorPanel: clearDocument() called")
        ApplicationManager.getApplication().invokeLater {
            LOG.info("RokuLogEditorPanel: clearDocument() executing on EDT, current length=${document.textLength}")
            document.setText("")
            userScrolledBack.set(false)
        }
    }

    private fun reloadFilteredLogs() {
        LOG.info("RokuLogEditorPanel: reloadFilteredLogs() called")
        ApplicationManager.getApplication().invokeLater {
            val filteredLogs = logService.getFilteredLogs()
            LOG.info("RokuLogEditorPanel: reloadFilteredLogs() on EDT, ${filteredLogs.size} logs, current docLength=${document.textLength}")
            document.setText("")

            val text = filteredLogs.joinToString("") { formatLogEntry(it) }
            document.setText(text)
            LOG.info("RokuLogEditorPanel: reloadFilteredLogs() done, new docLength=${document.textLength}")

            if (_autoScrollEnabled.get()) {
                scrollToEnd()
            }
        }
    }

    private fun scrollToEnd() {
        ApplicationManager.getApplication().invokeLater {
            val scrollingModel = editor.scrollingModel
            scrollingModel.scrollTo(
                editor.offsetToLogicalPosition(document.textLength),
                ScrollType.MAKE_VISIBLE
            )
            userScrolledBack.set(false)
        }
    }

    override fun dispose() {
        scope.cancel()
    }
}
