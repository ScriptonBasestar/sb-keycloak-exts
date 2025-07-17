package org.scriptonbasestar.kcexts.events.kafka.security

import org.jboss.logging.Logger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Secret Manager for encrypting and decrypting sensitive information
 * Used to protect passwords, keys, and other sensitive configuration data
 */
class SecretManager {
    companion object {
        private val logger = Logger.getLogger(SecretManager::class.java)

        // Encryption constants
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 16
        private const val GCM_IV_LENGTH = 12
        private const val KEY_LENGTH = 256

        // Environment variable names
        private const val MASTER_KEY_ENV = "KEYCLOAK_KAFKA_MASTER_KEY"
        private const val ENCRYPTION_ENABLED_ENV = "KEYCLOAK_KAFKA_ENCRYPTION_ENABLED"

        // Prefix for encrypted values
        private const val ENCRYPTED_PREFIX = "ENC("
        private const val ENCRYPTED_SUFFIX = ")"
    }

    private val secureRandom = SecureRandom()
    private var masterKey: SecretKey? = null
    private var encryptionEnabled: Boolean = false

    init {
        initializeSecretManager()
    }

    /**
     * Initialize the secret manager
     */
    private fun initializeSecretManager() {
        encryptionEnabled = System.getenv(ENCRYPTION_ENABLED_ENV)?.toBoolean() ?: false

        if (encryptionEnabled) {
            val masterKeyString = System.getenv(MASTER_KEY_ENV)

            if (!masterKeyString.isNullOrEmpty()) {
                masterKey = loadMasterKey(masterKeyString)
                logger.info("Secret manager initialized with provided master key")
            } else {
                masterKey = generateMasterKey()
                logger.warn("Secret manager initialized with generated master key - store this key securely!")
                logger.warn("Master key (base64): ${Base64.getEncoder().encodeToString(masterKey!!.encoded)}")
            }
        } else {
            logger.info("Secret encryption is disabled")
        }
    }

    /**
     * Generate a new master key for encryption
     */
    private fun generateMasterKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(ALGORITHM)
        keyGenerator.init(KEY_LENGTH, secureRandom)
        return keyGenerator.generateKey()
    }

    /**
     * Load master key from string (base64 encoded)
     */
    private fun loadMasterKey(keyString: String): SecretKey {
        return try {
            val keyBytes = Base64.getDecoder().decode(keyString)
            SecretKeySpec(keyBytes, ALGORITHM)
        } catch (e: Exception) {
            logger.error("Failed to load master key from string, generating new key", e)
            generateMasterKey()
        }
    }

    /**
     * Encrypt a sensitive value
     */
    fun encrypt(plaintext: String?): String? {
        if (!encryptionEnabled || plaintext.isNullOrEmpty()) {
            return plaintext
        }

        if (isEncrypted(plaintext)) {
            logger.debug("Value is already encrypted")
            return plaintext
        }

        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)

            // Generate random IV
            val iv = ByteArray(GCM_IV_LENGTH)
            secureRandom.nextBytes(iv)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)

            cipher.init(Cipher.ENCRYPT_MODE, masterKey, gcmSpec)

            val plaintextBytes = plaintext.toByteArray(StandardCharsets.UTF_8)
            val ciphertext = cipher.doFinal(plaintextBytes)

            // Combine IV and ciphertext
            val encryptedData = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, encryptedData, 0, iv.size)
            System.arraycopy(ciphertext, 0, encryptedData, iv.size, ciphertext.size)

            val encoded = Base64.getEncoder().encodeToString(encryptedData)
            return "$ENCRYPTED_PREFIX$encoded$ENCRYPTED_SUFFIX"
        } catch (e: Exception) {
            logger.error("Failed to encrypt value", e)
            throw SecurityException("Encryption failed", e)
        }
    }

    /**
     * Decrypt a sensitive value
     */
    fun decrypt(encryptedValue: String?): String? {
        if (!encryptionEnabled || encryptedValue.isNullOrEmpty()) {
            return encryptedValue
        }

        if (!isEncrypted(encryptedValue)) {
            logger.debug("Value is not encrypted")
            return encryptedValue
        }

        try {
            // Extract base64 data
            val base64Data =
                encryptedValue.substring(
                    ENCRYPTED_PREFIX.length,
                    encryptedValue.length - ENCRYPTED_SUFFIX.length,
                )

            val encryptedData = Base64.getDecoder().decode(base64Data)

            // Extract IV and ciphertext
            val iv = ByteArray(GCM_IV_LENGTH)
            val ciphertext = ByteArray(encryptedData.size - GCM_IV_LENGTH)

            System.arraycopy(encryptedData, 0, iv, 0, iv.size)
            System.arraycopy(encryptedData, iv.size, ciphertext, 0, ciphertext.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, gcmSpec)

            val plaintext = cipher.doFinal(ciphertext)
            return String(plaintext, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            logger.error("Failed to decrypt value", e)
            throw SecurityException("Decryption failed", e)
        }
    }

    /**
     * Check if a value is encrypted
     */
    fun isEncrypted(value: String?): Boolean {
        return value != null &&
            value.startsWith(ENCRYPTED_PREFIX) &&
            value.endsWith(ENCRYPTED_SUFFIX)
    }

    /**
     * Hash a value using SHA-256 (for non-reversible hashing)
     */
    fun hash(value: String?): String? {
        if (value.isNullOrEmpty()) {
            return value
        }

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(value.toByteArray(StandardCharsets.UTF_8))
            return Base64.getEncoder().encodeToString(hashBytes)
        } catch (e: Exception) {
            logger.error("Failed to hash value", e)
            throw SecurityException("Hashing failed", e)
        }
    }

    /**
     * Encrypt configuration properties that contain sensitive data
     */
    fun encryptSensitiveProperties(properties: Properties): Properties {
        if (!encryptionEnabled) {
            return properties
        }

        val encryptedProperties = Properties()
        encryptedProperties.putAll(properties)

        // List of sensitive property keys
        val sensitiveKeys =
            listOf(
                "password",
                "truststore.password",
                "keystore.password",
                "key.password",
                "sasl.jaas.config",
                "ssl.truststore.password",
                "ssl.keystore.password",
                "ssl.key.password",
            )

        sensitiveKeys.forEach { key ->
            val value = encryptedProperties.getProperty(key)
            if (!value.isNullOrEmpty()) {
                encryptedProperties.setProperty(key, encrypt(value) ?: value)
                logger.debug("Encrypted sensitive property: $key")
            }
        }

        return encryptedProperties
    }

    /**
     * Decrypt configuration properties
     */
    fun decryptSensitiveProperties(properties: Properties): Properties {
        if (!encryptionEnabled) {
            return properties
        }

        val decryptedProperties = Properties()

        properties.forEach { key, value ->
            val decryptedValue =
                if (value is String) {
                    decrypt(value) ?: value
                } else {
                    value
                }
            decryptedProperties[key] = decryptedValue
        }

        return decryptedProperties
    }

    /**
     * Generate a secure random password
     */
    fun generateSecurePassword(length: Int = 32): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#\$%^&*"
        val password = StringBuilder()

        repeat(length) {
            password.append(chars[secureRandom.nextInt(chars.length)])
        }

        return password.toString()
    }

    /**
     * Clear sensitive data from memory
     */
    fun clearSensitiveData() {
        masterKey = null
        logger.info("Sensitive data cleared from memory")
    }

    /**
     * Check if encryption is enabled
     */
    fun isEncryptionEnabled(): Boolean = encryptionEnabled

    /**
     * Security configuration validator
     */
    fun validateSecurityConfiguration(config: Properties): List<SecurityIssue> {
        val issues = mutableListOf<SecurityIssue>()

        // Check for plaintext passwords
        val sensitiveKeys =
            listOf(
                "password",
                "truststore.password",
                "keystore.password",
                "key.password",
            )

        sensitiveKeys.forEach { key ->
            val value = config.getProperty(key)
            if (!value.isNullOrEmpty() && !isEncrypted(value)) {
                issues.add(
                    SecurityIssue(
                        severity = SecuritySeverity.HIGH,
                        message = "Sensitive property '$key' is stored in plaintext",
                        recommendation = "Encrypt the value using SecretManager.encrypt()",
                    ),
                )
            }
        }

        // Check for weak encryption settings
        val securityProtocol = config.getProperty("security.protocol")
        if (securityProtocol == "PLAINTEXT") {
            issues.add(
                SecurityIssue(
                    severity = SecuritySeverity.MEDIUM,
                    message = "Using plaintext communication protocol",
                    recommendation = "Use SSL or SASL_SSL for encrypted communication",
                ),
            )
        }

        // Check SSL configuration
        if (securityProtocol?.contains("SSL") == true) {
            val truststoreLocation = config.getProperty("ssl.truststore.location")
            if (truststoreLocation.isNullOrEmpty()) {
                issues.add(
                    SecurityIssue(
                        severity = SecuritySeverity.HIGH,
                        message = "SSL enabled but no truststore configured",
                        recommendation = "Configure ssl.truststore.location and ssl.truststore.password",
                    ),
                )
            }
        }

        return issues
    }
}

/**
 * Security issue data class
 */
data class SecurityIssue(
    val severity: SecuritySeverity,
    val message: String,
    val recommendation: String,
)

/**
 * Security issue severity levels
 */
enum class SecuritySeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}
