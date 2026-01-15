// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.device.ecp

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.roku.device.model.RokuDeviceInfo
import org.w3c.dom.Element
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Client for Roku's External Control Protocol (ECP).
 *
 * ECP is a RESTful API that allows control of Roku devices over the local network.
 * The API runs on port 8060 and provides endpoints for querying device info,
 * listing apps, sending remote control commands, etc.
 */
class EcpClient {
    private val LOG = Logger.getInstance(EcpClient::class.java)

    companion object {
        private const val ECP_PORT = 8060
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 10000
    }

    /**
     * Queries device information from a Roku device.
     *
     * @param ipAddress The IP address of the Roku device
     * @return Device information if successful
     * @throws EcpException if the request fails
     */
    suspend fun getDeviceInfo(ipAddress: String): RokuDeviceInfo = withContext(Dispatchers.IO) {
        val url = URL("http://$ipAddress:$ECP_PORT/query/device-info")
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw EcpException("Failed to get device info: HTTP ${connection.responseCode}")
            }

            val responseXml = connection.inputStream.bufferedReader().readText()
            parseDeviceInfo(responseXml)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Pings a Roku device to check connectivity.
     *
     * @param ipAddress The IP address of the Roku device
     * @return true if the device responds, false otherwise
     */
    suspend fun ping(ipAddress: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$ipAddress:$ECP_PORT/")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.responseCode == HttpURLConnection.HTTP_OK
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            LOG.debug("Ping failed for $ipAddress: ${e.message}")
            false
        }
    }

    /**
     * Tests authentication credentials against a Roku device.
     *
     * Roku developer mode uses HTTP Basic Auth on the developer web interface
     * which runs on port 80 (not the ECP port 8060).
     *
     * @param ipAddress The IP address of the Roku device
     * @param username The username (typically "rokudev")
     * @param password The developer password
     * @return The authentication result
     */
    suspend fun testAuthentication(
        ipAddress: String,
        username: String,
        password: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            // Roku developer installer web interface runs on port 80, not ECP port 8060
            val url = URL("http://$ipAddress:80/")
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.requestMethod = "GET"

                // Add basic auth header
                val credentials = "$username:$password"
                val encodedCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray())
                connection.setRequestProperty("Authorization", "Basic $encodedCredentials")

                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        LOG.info("Authentication successful for $ipAddress")
                        AuthResult.Success
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        LOG.info("Authentication failed for $ipAddress: Invalid credentials")
                        AuthResult.InvalidCredentials
                    }
                    else -> {
                        LOG.warn("Authentication failed for $ipAddress: HTTP ${connection.responseCode}")
                        AuthResult.Error("HTTP ${connection.responseCode}")
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: java.net.ConnectException) {
            LOG.warn("Developer mode may not be enabled on $ipAddress: ${e.message}")
            AuthResult.Error("Connection refused - is developer mode enabled?")
        } catch (e: Exception) {
            LOG.warn("Authentication test failed for $ipAddress", e)
            AuthResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Parses device info XML response.
     */
    private fun parseDeviceInfo(xml: String): RokuDeviceInfo {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(xml.byteInputStream())
        val root = document.documentElement

        return RokuDeviceInfo(
            friendlyName = getElementText(root, "friendly-device-name"),
            modelName = getElementText(root, "model-name"),
            modelNumber = getElementText(root, "model-number"),
            softwareVersion = getElementText(root, "software-version"),
            softwareBuild = getElementText(root, "software-build"),
            serialNumber = getElementText(root, "serial-number"),
            vendorName = getElementText(root, "vendor-name"),
            isDeveloperEnabled = getElementText(root, "developer-enabled") == "true",
            isKeepAliveModeEnabled = getElementText(root, "keyed-developer-id").isNotBlank(),
            networkType = getElementText(root, "network-type"),
            wifiMac = getElementText(root, "wifi-mac"),
            ethernetMac = getElementText(root, "ethernet-mac")
        )
    }

    /**
     * Gets text content of a child element.
     */
    private fun getElementText(parent: Element, tagName: String): String {
        val elements = parent.getElementsByTagName(tagName)
        return if (elements.length > 0) {
            elements.item(0).textContent ?: ""
        } else {
            ""
        }
    }

    /**
     * Result of an authentication test.
     */
    sealed class AuthResult {
        object Success : AuthResult()
        object InvalidCredentials : AuthResult()
        data class Error(val message: String) : AuthResult()
    }
}

/**
 * Exception thrown when an ECP request fails.
 */
class EcpException(message: String, cause: Throwable? = null) : Exception(message, cause)
