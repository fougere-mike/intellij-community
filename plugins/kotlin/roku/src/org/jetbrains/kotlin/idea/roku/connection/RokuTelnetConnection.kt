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

    private val _logs = MutableSharedFlow<String>(extraBufferCapacity = 1000)
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
                // Debug: Log network environment
                LOG.info("DEBUG: Thread = ${Thread.currentThread().name}")
                LOG.info("DEBUG: Coroutine context = ${currentCoroutineContext()}")

                // Debug: Check IP address resolution
                val inetAddress = java.net.InetAddress.getByName(ipAddress)
                LOG.info("DEBUG: Resolved address = $inetAddress")
                LOG.info("DEBUG: Address class = ${inetAddress.javaClass.name}")
                LOG.info("DEBUG: Host address = ${inetAddress.hostAddress}")

                // Debug: Log network interfaces
                java.net.NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                    LOG.info("DEBUG: Network interface: ${ni.name} - ${ni.inetAddresses.toList()}")
                }

                // Debug: Check socket factory
                val socketFactory = javax.net.SocketFactory.getDefault()
                LOG.info("DEBUG: Socket factory = ${socketFactory.javaClass.name}")

                // Debug: Check system properties
                LOG.info("DEBUG: socksProxyHost = ${System.getProperty("socksProxyHost")}")
                LOG.info("DEBUG: socksProxyPort = ${System.getProperty("socksProxyPort")}")
                LOG.info("DEBUG: http.proxyHost = ${System.getProperty("http.proxyHost")}")
                LOG.info("DEBUG: java.net.preferIPv4Stack = ${System.getProperty("java.net.preferIPv4Stack")}")

                // Debug: Create socket with detailed error handling
                LOG.info("DEBUG: Creating Socket()...")
                val newSocket = Socket()
                LOG.info("DEBUG: Socket created, local address before connect = ${newSocket.localAddress}")

                LOG.info("DEBUG: Creating InetSocketAddress...")
                val socketAddress = java.net.InetSocketAddress(inetAddress, port)
                LOG.info("DEBUG: InetSocketAddress = $socketAddress, isUnresolved = ${socketAddress.isUnresolved}")

                LOG.info("DEBUG: Calling connect() with timeout ${connectionTimeoutMs}ms...")
                newSocket.connect(socketAddress, connectionTimeoutMs)
                LOG.info("DEBUG: connect() succeeded!")

                LOG.info("DEBUG: Setting soTimeout...")
                newSocket.soTimeout = 0
                LOG.info("DEBUG: Setting keepAlive...")
                newSocket.keepAlive = true

                socket = newSocket
            }

            _state.value = RokuConnectionState.CONNECTED
            reconnectAttempt = 0
            LOG.info("Connected to $ipAddress:$port")

            readLogs()

        } catch (e: Exception) {
            if (isDisposed.get()) return

            LOG.warn("Connection failed to $ipAddress:$port", e)
            LOG.warn("DEBUG: Exception class = ${e.javaClass.name}")
            LOG.warn("DEBUG: Exception message = ${e.message}")
            LOG.warn("DEBUG: Exception cause = ${e.cause}")
            _state.value = RokuConnectionState.CONNECTION_FAILED

            scheduleReconnect()
        }
    }

    private suspend fun readLogs() {
        LOG.info("readLogs() starting for $ipAddress:$port")
        val reader = withContext(Dispatchers.IO) {
            BufferedReader(InputStreamReader(socket!!.getInputStream()))
        }

        var lineCount = 0
        try {
            while (currentCoroutineContext().isActive && !isDisposed.get()) {
                val line = withContext(Dispatchers.IO) {
                    reader.readLine()
                }

                if (line == null) {
                    // Connection closed by remote
                    LOG.info("Connection closed by remote host after $lineCount lines")
                    break
                }

                lineCount++
                if (lineCount <= 3 || lineCount % 100 == 0) {
                    LOG.info("readLogs: Emitting line #$lineCount to flow")
                }
                _logs.emit(line)
            }
        } catch (e: SocketTimeoutException) {
            // Should not happen with soTimeout=0, but handle anyway
            LOG.debug("Socket timeout after $lineCount lines", e)
        } catch (e: SocketException) {
            if (!isDisposed.get()) {
                LOG.info("Socket exception after $lineCount lines: ${e.message}")
            }
        } catch (e: Exception) {
            if (!isDisposed.get()) {
                LOG.warn("Error reading logs after $lineCount lines", e)
            }
        } finally {
            LOG.info("readLogs() ending for $ipAddress:$port, total lines: $lineCount")
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
