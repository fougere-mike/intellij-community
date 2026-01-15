// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.connection

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.kotlin.idea.roku.device.model.RokuDevice
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Application-level service that manages telnet connections to Roku devices.
 *
 * CRITICAL: Ensures only ONE telnet connection exists per Roku device at any time.
 * Multiple callers can share the same connection via reference counting.
 */
@Service(Service.Level.APP)
class RokuConnectionManager : Disposable {

    private val LOG = Logger.getInstance(RokuConnectionManager::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Holds a connection and its reference count.
     * Only one ConnectionHolder exists per device IP address.
     */
    private data class ConnectionHolder(
        val connection: RokuTelnetConnection,
        val device: RokuDevice,
        val refCount: AtomicInteger = AtomicInteger(1)
    )

    // Keyed by device IP address to ensure single connection per device
    private val activeConnections = ConcurrentHashMap<String, ConnectionHolder>()

    /**
     * Acquires a connection to the specified device.
     *
     * If a connection already exists for this device, returns the existing connection
     * and increments its reference count. Otherwise, creates a new connection.
     *
     * Callers MUST call [releaseConnection] when done with the connection.
     *
     * @param device The Roku device to connect to
     * @return The telnet connection
     */
    fun acquireConnection(device: RokuDevice): RokuTelnetConnection {
        val key = device.ipAddress

        val holder = activeConnections.compute(key) { _, existing ->
            if (existing != null) {
                // Reuse existing connection, increment reference count
                val newCount = existing.refCount.incrementAndGet()
                LOG.info("Reusing existing connection to ${device.ipAddress}, refCount=$newCount")
                existing
            } else {
                // Create new connection
                LOG.info("Creating new connection to ${device.ipAddress}")
                val connection = RokuTelnetConnection(device.ipAddress, 8085, scope)
                ConnectionHolder(connection, device)
            }
        }!!

        // Note: Connection is NOT started here - callers must call connect() after
        // subscribing to the logs flow to avoid race conditions where logs are
        // emitted before the subscriber is ready.

        return holder.connection
    }

    /**
     * Releases a connection to a device.
     *
     * Decrements the reference count. When the count reaches 0, the connection
     * is closed and removed from the manager.
     *
     * @param device The device whose connection to release
     */
    fun releaseConnection(device: RokuDevice) {
        val key = device.ipAddress

        activeConnections.computeIfPresent(key) { _, holder ->
            val newCount = holder.refCount.decrementAndGet()
            LOG.info("Releasing connection to ${device.ipAddress}, refCount=$newCount")

            if (newCount <= 0) {
                LOG.info("Closing connection to ${device.ipAddress} (refCount=0)")
                holder.connection.disconnect()
                Disposer.dispose(holder.connection)
                null // Remove from map
            } else {
                holder
            }
        }
    }

    /**
     * Forcibly disconnects a device regardless of reference count.
     *
     * Use this when the user explicitly wants to disconnect or when
     * switching to a different device.
     *
     * @param device The device to disconnect
     */
    fun forceDisconnect(device: RokuDevice) {
        val key = device.ipAddress
        activeConnections.remove(key)?.let { holder ->
            LOG.info("Force disconnecting from ${device.ipAddress}")
            holder.connection.disconnect()
            Disposer.dispose(holder.connection)
        }
    }

    /**
     * Gets the connection state for a device, if a connection exists.
     *
     * @param device The device to check
     * @return The connection state flow, or null if no connection exists
     */
    fun getConnectionState(device: RokuDevice): StateFlow<RokuConnectionState>? {
        return activeConnections[device.ipAddress]?.connection?.state
    }

    /**
     * Gets the log flow for a device, if a connection exists.
     *
     * @param device The device to get logs from
     * @return The log flow, or null if no connection exists
     */
    fun getLogFlow(device: RokuDevice): SharedFlow<String>? {
        return activeConnections[device.ipAddress]?.connection?.logs
    }

    /**
     * Checks if a connection exists for a device.
     *
     * @param device The device to check
     * @return true if a connection exists
     */
    fun hasConnection(device: RokuDevice): Boolean {
        return activeConnections.containsKey(device.ipAddress)
    }

    /**
     * Gets the current reference count for a device's connection.
     *
     * @param device The device to check
     * @return The reference count, or 0 if no connection exists
     */
    fun getConnectionRefCount(device: RokuDevice): Int {
        return activeConnections[device.ipAddress]?.refCount?.get() ?: 0
    }

    override fun dispose() {
        LOG.info("Disposing RokuConnectionManager, closing ${activeConnections.size} connection(s)")
        activeConnections.values.forEach { holder ->
            holder.connection.disconnect()
            Disposer.dispose(holder.connection)
        }
        activeConnections.clear()
        scope.cancel()
    }

    companion object {
        @JvmStatic
        fun getInstance(): RokuConnectionManager = service()
    }
}
