// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.device.discovery

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.roku.device.model.RokuDevice
import java.net.*
import java.nio.charset.StandardCharsets

/**
 * SSDP (Simple Service Discovery Protocol) implementation for discovering Roku devices on the network.
 *
 * Roku devices respond to M-SEARCH requests with the "roku:ecp" search target.
 */
class SsdpDiscovery {
    private val LOG = Logger.getInstance(SsdpDiscovery::class.java)

    companion object {
        private const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val ROKU_SEARCH_TARGET = "roku:ecp"
        private const val DEFAULT_DISCOVERY_TIMEOUT_MS = 5000

        private val M_SEARCH_MESSAGE = """
            M-SEARCH * HTTP/1.1
            Host: $SSDP_MULTICAST_ADDRESS:$SSDP_PORT
            Man: "ssdp:discover"
            ST: $ROKU_SEARCH_TARGET
            MX: 3

        """.trimIndent().replace("\n", "\r\n")
    }

    /**
     * Discovers Roku devices on the local network using SSDP.
     *
     * @param timeoutMs Maximum time to wait for responses in milliseconds
     * @return List of discovered Roku devices
     */
    suspend fun discover(timeoutMs: Int = DEFAULT_DISCOVERY_TIMEOUT_MS): List<RokuDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<RokuDevice>()
        val seenIps = mutableSetOf<String>()

        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 1000 // Short timeout for individual receives
                socket.reuseAddress = true
                socket.broadcast = true

                // Send M-SEARCH request to multicast address
                val requestBytes = M_SEARCH_MESSAGE.toByteArray(StandardCharsets.UTF_8)
                val multicastAddress = InetAddress.getByName(SSDP_MULTICAST_ADDRESS)
                val requestPacket = DatagramPacket(
                    requestBytes,
                    requestBytes.size,
                    multicastAddress,
                    SSDP_PORT
                )

                LOG.info("Sending SSDP M-SEARCH request for Roku devices")
                socket.send(requestPacket)

                // Collect responses until timeout
                val buffer = ByteArray(2048)
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    try {
                        val responsePacket = DatagramPacket(buffer, buffer.size)
                        socket.receive(responsePacket)

                        val response = String(responsePacket.data, 0, responsePacket.length, StandardCharsets.UTF_8)
                        val ipAddress = responsePacket.address.hostAddress

                        // Skip if we've already seen this IP
                        if (ipAddress != null && ipAddress !in seenIps) {
                            parseResponse(response, ipAddress)?.let { device ->
                                devices.add(device)
                                seenIps.add(ipAddress)
                                LOG.info("Discovered Roku device at $ipAddress")
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        // Continue waiting for more responses
                    }
                }
            }
        } catch (e: Exception) {
            LOG.warn("SSDP discovery error", e)
        }

        LOG.info("SSDP discovery complete. Found ${devices.size} device(s)")
        devices
    }

    /**
     * Parses an SSDP response to extract Roku device information.
     *
     * @param response The raw SSDP response string
     * @param ipAddress The IP address of the responding device
     * @return A RokuDevice if the response is from a Roku, null otherwise
     */
    private fun parseResponse(response: String, ipAddress: String): RokuDevice? {
        // Only process Roku ECP responses
        if (!response.contains(ROKU_SEARCH_TARGET, ignoreCase = true)) {
            return null
        }

        // Parse headers from response
        val headers = response.lines()
            .filter { it.contains(":") }
            .associate { line ->
                val colonIndex = line.indexOf(':')
                val key = line.substring(0, colonIndex).trim().lowercase()
                val value = line.substring(colonIndex + 1).trim()
                key to value
            }

        // Extract serial number from USN header
        // USN format: uuid:roku:ecp:<serial-number>
        val usn = headers["usn"] ?: ""
        val serialNumber = extractSerialNumber(usn)

        return RokuDevice.createFromSsdp(ipAddress, serialNumber)
    }

    /**
     * Extracts the serial number from a Roku USN header.
     *
     * @param usn The USN header value
     * @return The serial number if found, null otherwise
     */
    private fun extractSerialNumber(usn: String): String? {
        // USN format: uuid:roku:ecp:<serial-number>
        val regex = Regex("uuid:roku:ecp:([^:]+)")
        return regex.find(usn)?.groupValues?.get(1)
    }
}
