// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roku.device.credentials

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.roku.device.ecp.EcpClient
import org.jetbrains.kotlin.idea.roku.device.model.RokuDevice
import java.io.File
import java.util.Properties

/**
 * Manages Roku device credentials with the following priority:
 * 1. Check PasswordSafe (keychain)
 * 2. Fallback to project's local.properties
 * 3. Prompt user if no credentials found
 *
 * On successful authentication, always save to PasswordSafe (never to local.properties).
 */
class RokuCredentialsManager {
    private val LOG = Logger.getInstance(RokuCredentialsManager::class.java)

    companion object {
        private const val SERVICE_NAME = "RokuStudio"
        const val ROKU_USERNAME = "rokudev"  // Fixed username for Roku dev mode
        private const val LOCAL_PROPERTIES_PASSWORD_KEY = "roku.devicePassword"
    }

    private val passwordSafe: PasswordSafe
        get() = PasswordSafe.instance

    private val ecpClient = EcpClient()

    /**
     * Gets credentials for a device, checking PasswordSafe first, then local.properties.
     *
     * @param device The Roku device
     * @param project The current project (for reading local.properties)
     * @return Credentials if found, null otherwise
     */
    suspend fun getCredentials(device: RokuDevice, project: Project?): DeviceCredentials? {
        // Step 1: Check PasswordSafe (keychain)
        val keychainPassword = getPasswordFromKeychain(device)
        if (keychainPassword != null) {
            LOG.debug("Found password in keychain for device ${device.id}")
            return DeviceCredentials(ROKU_USERNAME, keychainPassword, CredentialSource.KEYCHAIN)
        }

        // Step 2: Check project's local.properties as fallback
        if (project != null) {
            val localPropertiesPassword = getPasswordFromLocalProperties(project)
            if (localPropertiesPassword != null) {
                LOG.debug("Found password in local.properties for project ${project.name}")
                return DeviceCredentials(ROKU_USERNAME, localPropertiesPassword, CredentialSource.LOCAL_PROPERTIES)
            }
        }

        return null
    }

    /**
     * Tests authentication against the device and saves to keychain on success.
     *
     * @param device The Roku device
     * @param password The password to test
     * @return The result of the authentication test
     */
    suspend fun testAndSaveCredentials(
        device: RokuDevice,
        password: String
    ): TestAuthResult {
        val result = ecpClient.testAuthentication(device.ipAddress, ROKU_USERNAME, password)

        return when (result) {
            is EcpClient.AuthResult.Success -> {
                // Save to keychain on success
                savePasswordToKeychain(device, password)
                TestAuthResult.Success
            }
            is EcpClient.AuthResult.InvalidCredentials -> {
                TestAuthResult.InvalidCredentials
            }
            is EcpClient.AuthResult.Error -> {
                TestAuthResult.Error(result.message)
            }
        }
    }

    /**
     * Full authentication flow: check keychain/local.properties, test, prompt if needed.
     *
     * @param device The Roku device
     * @param project The current project
     * @param promptForPassword Callback to prompt user for password
     * @return The authentication result
     */
    suspend fun authenticate(
        device: RokuDevice,
        project: Project?,
        promptForPassword: suspend () -> String?
    ): AuthenticationResult {
        // Try existing credentials first
        val existingCredentials = getCredentials(device, project)

        if (existingCredentials != null) {
            val testResult = ecpClient.testAuthentication(
                device.ipAddress,
                existingCredentials.username,
                existingCredentials.password
            )

            when (testResult) {
                is EcpClient.AuthResult.Success -> {
                    // If from local.properties, save to keychain for future use
                    if (existingCredentials.source == CredentialSource.LOCAL_PROPERTIES) {
                        savePasswordToKeychain(device, existingCredentials.password)
                        LOG.info("Migrated password from local.properties to keychain for device ${device.id}")
                    }
                    return AuthenticationResult.Success(existingCredentials)
                }
                is EcpClient.AuthResult.InvalidCredentials -> {
                    // Clear invalid keychain entry
                    if (existingCredentials.source == CredentialSource.KEYCHAIN) {
                        clearKeychainPassword(device)
                        LOG.info("Cleared invalid keychain password for device ${device.id}")
                    }
                    // Fall through to prompt
                }
                is EcpClient.AuthResult.Error -> {
                    return AuthenticationResult.Error(testResult.message)
                }
            }
        }

        // Prompt for password
        val password = promptForPassword() ?: return AuthenticationResult.Cancelled

        val testResult = testAndSaveCredentials(device, password)
        return when (testResult) {
            is TestAuthResult.Success -> {
                AuthenticationResult.Success(
                    DeviceCredentials(ROKU_USERNAME, password, CredentialSource.KEYCHAIN)
                )
            }
            is TestAuthResult.InvalidCredentials -> {
                AuthenticationResult.InvalidCredentials
            }
            is TestAuthResult.Error -> {
                AuthenticationResult.Error(testResult.message)
            }
        }
    }

    /**
     * Gets password from the secure keychain.
     */
    private fun getPasswordFromKeychain(device: RokuDevice): String? {
        val attributes = createCredentialAttributes(device)
        return passwordSafe.get(attributes)?.getPasswordAsString()
    }

    /**
     * Saves password to the secure keychain.
     */
    private fun savePasswordToKeychain(device: RokuDevice, password: String) {
        val attributes = createCredentialAttributes(device)
        val credentials = Credentials(device.id, password)
        passwordSafe.set(attributes, credentials)
        LOG.info("Saved credentials to keychain for device: ${device.displayName}")
    }

    /**
     * Clears password from the keychain.
     */
    private fun clearKeychainPassword(device: RokuDevice) {
        val attributes = createCredentialAttributes(device)
        passwordSafe.set(attributes, null)
    }

    /**
     * Checks if a password exists in the keychain for a device.
     */
    fun hasKeychainPassword(device: RokuDevice): Boolean {
        val attributes = createCredentialAttributes(device)
        return passwordSafe.get(attributes) != null
    }

    /**
     * Creates credential attributes for PasswordSafe storage.
     */
    private fun createCredentialAttributes(device: RokuDevice): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName(SERVICE_NAME, device.id)
        )
    }

    /**
     * Reads password from project's local.properties file.
     */
    private suspend fun getPasswordFromLocalProperties(project: Project): String? =
        withContext(Dispatchers.IO) {
            val basePath = project.basePath ?: return@withContext null
            val localFile = File(basePath, "local.properties")

            if (!localFile.exists()) {
                return@withContext null
            }

            try {
                val properties = Properties()
                localFile.inputStream().use { properties.load(it) }
                properties.getProperty(LOCAL_PROPERTIES_PASSWORD_KEY)
            } catch (e: Exception) {
                LOG.warn("Failed to read local.properties", e)
                null
            }
        }
}

/**
 * Device credentials with source information.
 */
data class DeviceCredentials(
    val username: String,
    val password: String,
    val source: CredentialSource
)

/**
 * Where credentials were found.
 */
enum class CredentialSource {
    KEYCHAIN,
    LOCAL_PROPERTIES
}

/**
 * Result of a test authentication attempt.
 */
sealed class TestAuthResult {
    object Success : TestAuthResult()
    object InvalidCredentials : TestAuthResult()
    data class Error(val message: String) : TestAuthResult()
}

/**
 * Result of the full authentication flow.
 */
sealed class AuthenticationResult {
    data class Success(val credentials: DeviceCredentials) : AuthenticationResult()
    object InvalidCredentials : AuthenticationResult()
    object Cancelled : AuthenticationResult()
    data class Error(val message: String) : AuthenticationResult()
}
