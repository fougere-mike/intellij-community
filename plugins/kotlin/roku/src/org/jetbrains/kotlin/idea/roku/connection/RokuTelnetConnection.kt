// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.connection

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Telnet connection to a Roku device's debug console (port 8085).
 *
 * Provides a reactive stream of log lines and handles automatic reconnection
 * with exponential backoff.
 */
class RokuTelnetConnection(
    private val ipAddress: String,
    private val port: Int = 8085,
    private val scope: CoroutineScope
) : Disposable {

    private val LOG = Logger.getInstance(RokuTelnetConnection::class.java)

    private val _state = MutableStateFlow(RokuConnectionState.DISCONNECTED)
    /** Observable connection state */
    val state: StateFlow<RokuConnectionState> = _state.asStateFlow()

    private val _logs = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1000)
    /** Stream of log lines from the device */
    val logs: SharedFlow<String> = _logs.asSharedFlow()

    private var socket: Socket? = null
    private var connectionJob: Job? = null
    private val isDisposed = AtomicBoolean(false)

    private var reconnectAttempt = 0
    private val maxReconnectAttempts = 10
    private val baseReconnectDelayMs = 1000L
    private val maxReconnectDelayMs = 30000L
    private val connectionTimeoutMs = 5000

    /**
     * Starts the connection to the device.
     */
    fun connect() {
        if (isDisposed.get()) {
            LOG.warn("Cannot connect: connection is disposed")
            return
        }

        connectionJob?.cancel()
        connectionJob = scope.launch {
            doConnect()
        }
    }

    /**
     * Disconnects from the device.
     */
    fun disconnect() {
        connectionJob?.cancel()
        closeSocket()
        _state.value = RokuConnectionState.DISCONNECTED
        LOG.info("Disconnected from $ipAddress:$port")
    }

    private suspend fun doConnect() {
        _state.value = RokuConnectionState.CONNECTING
        LOG.info("Connecting to $ipAddress:$port")

        try {
            withContext(Dispatchers.IO) {
                socket = Socket().apply {
                    soTimeout = 0 // No read timeout - we want to block indefinitely
                    keepAlive = true
                    connect(java.net.InetSocketAddress(ipAddress, port), connectionTimeoutMs)
                }
            }

            _state.value = RokuConnectionState.CONNECTED
            reconnectAttempt = 0
            LOG.info("Connected to $ipAddress:$port")

            readLogs()

        } catch (e: Exception) {
            if (isDisposed.get()) return

            LOG.warn("Connection failed to $ipAddress:$port", e)
            _state.value = RokuConnectionState.CONNECTION_FAILED

            scheduleReconnect()
        }
    }

    private suspend fun readLogs() {
        val reader = withContext(Dispatchers.IO) {
            BufferedReader(InputStreamReader(socket!!.getInputStream()))
        }

        try {
            while (currentCoroutineContext().isActive && !isDisposed.get()) {
                val line = withContext(Dispatchers.IO) {
                    reader.readLine()
                }

                if (line == null) {
                    // Connection closed by remote
                    LOG.info("Connection closed by remote host")
                    break
                }

                _logs.emit(line)
            }
        } catch (e: SocketTimeoutException) {
            // Should not happen with soTimeout=0, but handle anyway
            LOG.debug("Socket timeout", e)
        } catch (e: SocketException) {
            if (!isDisposed.get()) {
                LOG.info("Socket exception: ${e.message}")
            }
        } catch (e: Exception) {
            if (!isDisposed.get()) {
                LOG.warn("Error reading logs", e)
            }
        } finally {
            closeSocket()
            if (!isDisposed.get()) {
                _state.value = RokuConnectionState.DISCONNECTED
                scheduleReconnect()
            }
        }
    }

    private suspend fun scheduleReconnect() {
        if (isDisposed.get()) return
        if (reconnectAttempt >= maxReconnectAttempts) {
            LOG.warn("Max reconnect attempts reached for $ipAddress:$port")
            _state.value = RokuConnectionState.CONNECTION_FAILED
            return
        }

        _state.value = RokuConnectionState.RECONNECTING

        // Exponential backoff with cap
        val delay = minOf(
            baseReconnectDelayMs * (1L shl reconnectAttempt),
            maxReconnectDelayMs
        )

        LOG.info("Scheduling reconnect to $ipAddress:$port in ${delay}ms (attempt ${reconnectAttempt + 1})")

        delay(delay)
        reconnectAttempt++

        if (!isDisposed.get()) {
            doConnect()
        }
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (e: Exception) {
            LOG.debug("Error closing socket", e)
        }
        socket = null
    }

    override fun dispose() {
        isDisposed.set(true)
        disconnect()
    }
}
