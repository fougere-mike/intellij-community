// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.device.ecp

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.roku.device.model.RokuDeviceInfo
import org.w3c.dom.Element
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
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
     * Roku developer mode uses HTTP Digest Auth on the developer web interface
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
            // Step 1: Make initial request to get the WWW-Authenticate challenge
            val url = URL("http://$ipAddress:80/")
            val initialConnection = url.openConnection() as HttpURLConnection

            val digestChallenge: DigestChallenge
            try {
                initialConnection.connectTimeout = CONNECT_TIMEOUT_MS
                initialConnection.readTimeout = READ_TIMEOUT_MS
                initialConnection.requestMethod = "GET"

                val responseCode = initialConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // No auth required - shouldn't happen but handle it
                    LOG.info("No authentication required for $ipAddress")
                    return@withContext AuthResult.Success
                }

                if (responseCode != HttpURLConnection.HTTP_UNAUTHORIZED) {
                    LOG.warn("Unexpected response from $ipAddress: HTTP $responseCode")
                    return@withContext AuthResult.Error("HTTP $responseCode")
                }

                // Parse WWW-Authenticate header
                val wwwAuth = initialConnection.getHeaderField("WWW-Authenticate")
                if (wwwAuth == null || !wwwAuth.startsWith("Digest ")) {
                    LOG.warn("Expected Digest auth but got: $wwwAuth")
                    return@withContext AuthResult.Error("Server doesn't support Digest authentication")
                }

                digestChallenge = parseDigestChallenge(wwwAuth)
            } finally {
                initialConnection.disconnect()
            }

            // Step 2: Make authenticated request with Digest auth
            val authConnection = url.openConnection() as HttpURLConnection
            try {
                authConnection.connectTimeout = CONNECT_TIMEOUT_MS
                authConnection.readTimeout = READ_TIMEOUT_MS
                authConnection.requestMethod = "GET"

                // Calculate and set the Authorization header
                val authHeader = calculateDigestAuthHeader(
                    username = username,
                    password = password,
                    method = "GET",
                    uri = "/",
                    challenge = digestChallenge
                )
                authConnection.setRequestProperty("Authorization", authHeader)

                when (authConnection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        LOG.info("Authentication successful for $ipAddress")
                        AuthResult.Success
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        LOG.info("Authentication failed for $ipAddress: Invalid credentials")
                        AuthResult.InvalidCredentials
                    }
                    else -> {
                        LOG.warn("Authentication failed for $ipAddress: HTTP ${authConnection.responseCode}")
                        AuthResult.Error("HTTP ${authConnection.responseCode}")
                    }
                }
            } finally {
                authConnection.disconnect()
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
     * Parses a Digest authentication challenge from the WWW-Authenticate header.
     */
    private fun parseDigestChallenge(header: String): DigestChallenge {
        val params = mutableMapOf<String, String>()

        // Remove "Digest " prefix and parse key=value pairs
        val content = header.removePrefix("Digest ").trim()

        // Parse parameters (handles both quoted and unquoted values)
        val regex = """(\w+)=(?:"([^"]+)"|([^,\s]+))""".toRegex()
        regex.findAll(content).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
            params[key] = value
        }

        return DigestChallenge(
            realm = params["realm"] ?: "",
            nonce = params["nonce"] ?: "",
            qop = params["qop"],
            opaque = params["opaque"],
            algorithm = params["algorithm"] ?: "MD5"
        )
    }

    /**
     * Calculates the Digest authentication header value per RFC 2617.
     */
    private fun calculateDigestAuthHeader(
        username: String,
        password: String,
        method: String,
        uri: String,
        challenge: DigestChallenge
    ): String {
        val nc = "00000001"
        val cnonce = generateCnonce()

        // Calculate HA1 = MD5(username:realm:password)
        val ha1 = md5Hex("$username:${challenge.realm}:$password")

        // Calculate HA2 = MD5(method:uri)
        val ha2 = md5Hex("$method:$uri")

        // Calculate response based on qop
        val response = if (challenge.qop != null) {
            // qop specified: response = MD5(HA1:nonce:nc:cnonce:qop:HA2)
            md5Hex("$ha1:${challenge.nonce}:$nc:$cnonce:${challenge.qop}:$ha2")
        } else {
            // No qop: response = MD5(HA1:nonce:HA2)
            md5Hex("$ha1:${challenge.nonce}:$ha2")
        }

        // Build Authorization header
        val sb = StringBuilder("Digest ")
        sb.append("""username="$username", """)
        sb.append("""realm="${challenge.realm}", """)
        sb.append("""nonce="${challenge.nonce}", """)
        sb.append("""uri="$uri", """)

        if (challenge.qop != null) {
            sb.append("""qop=${challenge.qop}, """)
            sb.append("""nc=$nc, """)
            sb.append("""cnonce="$cnonce", """)
        }

        sb.append("""response="$response"""")

        challenge.opaque?.let {
            sb.append(""", opaque="$it"""")
        }

        return sb.toString()
    }

    /**
     * Generates a client nonce for Digest auth.
     */
    private fun generateCnonce(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Calculates MD5 hash and returns as hex string.
     */
    private fun md5Hex(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
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
     * Digest authentication challenge parameters.
     */
    private data class DigestChallenge(
        val realm: String,
        val nonce: String,
        val qop: String?,
        val opaque: String?,
        val algorithm: String
    )

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
