package org.scriptonbasestar.kcexts.events.kafka.security

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.jboss.logging.Logger
import java.io.File
import java.util.*

/**
 * Kafka Security Configuration
 * Handles SSL/TLS encryption and SASL authentication setup
 */
class KafkaSecurityConfig {
    
    companion object {
        private val logger = Logger.getLogger(KafkaSecurityConfig::class.java)
        
        // Security protocol constants
        const val SECURITY_PROTOCOL_PLAINTEXT = "PLAINTEXT"
        const val SECURITY_PROTOCOL_SSL = "SSL"
        const val SECURITY_PROTOCOL_SASL_PLAINTEXT = "SASL_PLAINTEXT"
        const val SECURITY_PROTOCOL_SASL_SSL = "SASL_SSL"
        
        // SASL mechanism constants
        const val SASL_MECHANISM_PLAIN = "PLAIN"
        const val SASL_MECHANISM_SCRAM_SHA_256 = "SCRAM-SHA-256"
        const val SASL_MECHANISM_SCRAM_SHA_512 = "SCRAM-SHA-512"
        const val SASL_MECHANISM_GSSAPI = "GSSAPI"
        const val SASL_MECHANISM_OAUTHBEARER = "OAUTHBEARER"
        
        // SSL/TLS defaults
        const val DEFAULT_SSL_PROTOCOL = "TLSv1.2"
        const val DEFAULT_SSL_ENDPOINT_ALGORITHM = "https"
        const val DEFAULT_KEYSTORE_TYPE = "JKS"
        const val DEFAULT_TRUSTSTORE_TYPE = "JKS"
    }
    
    /**
     * Build secure Kafka producer properties
     */
    fun buildSecureProducerConfig(
        securityConfig: SecurityConfiguration,
        baseConfig: Properties
    ): Properties {
        val secureConfig = Properties()
        secureConfig.putAll(baseConfig)
        
        // Set security protocol
        secureConfig[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = securityConfig.securityProtocol
        logger.info("Configured Kafka security protocol: ${securityConfig.securityProtocol}")
        
        // Configure SSL if enabled
        if (isSSLEnabled(securityConfig.securityProtocol)) {
            configureSsl(secureConfig, securityConfig.sslConfig)
        }
        
        // Configure SASL if enabled
        if (isSASLEnabled(securityConfig.securityProtocol)) {
            configureSasl(secureConfig, securityConfig.saslConfig)
        }
        
        // Add additional security configurations
        configureAdditionalSecurity(secureConfig, securityConfig)
        
        logger.info("Kafka security configuration completed")
        return secureConfig
    }
    
    /**
     * Configure SSL/TLS settings
     */
    private fun configureSsl(config: Properties, sslConfig: SslConfiguration) {
        logger.info("Configuring Kafka SSL/TLS encryption")
        
        // SSL protocol version
        config[SslConfigs.SSL_PROTOCOL_CONFIG] = sslConfig.protocol ?: DEFAULT_SSL_PROTOCOL
        
        // SSL endpoint identification algorithm
        config[SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG] = 
            sslConfig.endpointIdentificationAlgorithm ?: DEFAULT_SSL_ENDPOINT_ALGORITHM
        
        // Truststore configuration
        if (!sslConfig.truststoreLocation.isNullOrEmpty()) {
            validateKeystoreFile(sslConfig.truststoreLocation!!, "truststore")
            config[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = sslConfig.truststoreLocation
            config[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = sslConfig.truststorePassword ?: ""
            config[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = sslConfig.truststoreType ?: DEFAULT_TRUSTSTORE_TYPE
            logger.info("Truststore configured: ${sslConfig.truststoreLocation}")
        }
        
        // Keystore configuration (for client authentication)
        if (!sslConfig.keystoreLocation.isNullOrEmpty()) {
            validateKeystoreFile(sslConfig.keystoreLocation!!, "keystore")
            config[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = sslConfig.keystoreLocation
            config[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = sslConfig.keystorePassword ?: ""
            config[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = sslConfig.keystoreType ?: DEFAULT_KEYSTORE_TYPE
            
            if (!sslConfig.keyPassword.isNullOrEmpty()) {
                config[SslConfigs.SSL_KEY_PASSWORD_CONFIG] = sslConfig.keyPassword
            }
            
            logger.info("Keystore configured: ${sslConfig.keystoreLocation}")
        }
        
        // Cipher suites (optional)
        if (!sslConfig.cipherSuites.isNullOrEmpty()) {
            config[SslConfigs.SSL_CIPHER_SUITES_CONFIG] = sslConfig.cipherSuites!!.joinToString(",")
            logger.info("Custom cipher suites configured")
        }
        
        // Enabled protocols (optional)
        if (!sslConfig.enabledProtocols.isNullOrEmpty()) {
            config[SslConfigs.SSL_ENABLED_PROTOCOLS_CONFIG] = sslConfig.enabledProtocols!!.joinToString(",")
            logger.info("Custom enabled protocols configured")
        }
        
        logger.info("SSL/TLS configuration completed successfully")
    }
    
    /**
     * Configure SASL authentication
     */
    private fun configureSasl(config: Properties, saslConfig: SaslConfiguration) {
        logger.info("Configuring Kafka SASL authentication")
        
        // SASL mechanism
        config[SaslConfigs.SASL_MECHANISM] = saslConfig.mechanism
        logger.info("SASL mechanism: ${saslConfig.mechanism}")
        
        // SASL JAAS configuration
        when (saslConfig.mechanism) {
            SASL_MECHANISM_PLAIN -> {
                val jaasConfig = buildPlainJaasConfig(saslConfig.username, saslConfig.password)
                config[SaslConfigs.SASL_JAAS_CONFIG] = jaasConfig
                logger.info("SASL PLAIN authentication configured")
            }
            
            SASL_MECHANISM_SCRAM_SHA_256, SASL_MECHANISM_SCRAM_SHA_512 -> {
                val jaasConfig = buildScramJaasConfig(saslConfig.username, saslConfig.password)
                config[SaslConfigs.SASL_JAAS_CONFIG] = jaasConfig
                logger.info("SASL SCRAM authentication configured")
            }
            
            SASL_MECHANISM_GSSAPI -> {
                if (!saslConfig.jaasConfig.isNullOrEmpty()) {
                    config[SaslConfigs.SASL_JAAS_CONFIG] = saslConfig.jaasConfig
                    logger.info("SASL GSSAPI authentication configured")
                } else {
                    throw IllegalArgumentException("JAAS configuration required for GSSAPI mechanism")
                }
            }
            
            SASL_MECHANISM_OAUTHBEARER -> {
                if (!saslConfig.jaasConfig.isNullOrEmpty()) {
                    config[SaslConfigs.SASL_JAAS_CONFIG] = saslConfig.jaasConfig
                    logger.info("SASL OAUTHBEARER authentication configured")
                } else {
                    throw IllegalArgumentException("JAAS configuration required for OAUTHBEARER mechanism")
                }
            }
            
            else -> {
                throw IllegalArgumentException("Unsupported SASL mechanism: ${saslConfig.mechanism}")
            }
        }
        
        logger.info("SASL authentication configuration completed successfully")
    }
    
    /**
     * Configure additional security settings
     */
    private fun configureAdditionalSecurity(config: Properties, securityConfig: SecurityConfiguration) {
        // Connection settings for security
        config[ProducerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG] = 540000L // 9 minutes
        config[ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG] = 60000L // 1 minute
        config[ProducerConfig.RETRY_BACKOFF_MS_CONFIG] = 100L
        config[ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG] = 50L
        config[ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG] = 1000L
        
        // Security-related producer settings
        config[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        config[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 1
        config[ProducerConfig.ACKS_CONFIG] = "all"
        config[ProducerConfig.RETRIES_CONFIG] = Int.MAX_VALUE
        
        logger.info("Additional security configurations applied")
    }
    
    /**
     * Build JAAS configuration for PLAIN mechanism
     */
    private fun buildPlainJaasConfig(username: String?, password: String?): String {
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
            throw IllegalArgumentException("Username and password required for SASL PLAIN authentication")
        }
        
        return """org.apache.kafka.common.security.plain.PlainLoginModule required 
            |username="$username" 
            |password="$password";""".trimMargin()
    }
    
    /**
     * Build JAAS configuration for SCRAM mechanisms
     */
    private fun buildScramJaasConfig(username: String?, password: String?): String {
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
            throw IllegalArgumentException("Username and password required for SASL SCRAM authentication")
        }
        
        return """org.apache.kafka.common.security.scram.ScramLoginModule required 
            |username="$username" 
            |password="$password";""".trimMargin()
    }
    
    /**
     * Validate keystore/truststore file exists and is readable
     */
    private fun validateKeystoreFile(filePath: String, type: String) {
        val file = File(filePath)
        
        if (!file.exists()) {
            throw IllegalArgumentException("$type file does not exist: $filePath")
        }
        
        if (!file.canRead()) {
            throw IllegalArgumentException("$type file is not readable: $filePath")
        }
        
        if (file.length() == 0L) {
            throw IllegalArgumentException("$type file is empty: $filePath")
        }
        
        logger.debug("$type file validation passed: $filePath")
    }
    
    /**
     * Check if SSL is enabled for the given security protocol
     */
    private fun isSSLEnabled(securityProtocol: String): Boolean {
        return securityProtocol == SECURITY_PROTOCOL_SSL || 
               securityProtocol == SECURITY_PROTOCOL_SASL_SSL
    }
    
    /**
     * Check if SASL is enabled for the given security protocol
     */
    private fun isSASLEnabled(securityProtocol: String): Boolean {
        return securityProtocol == SECURITY_PROTOCOL_SASL_PLAINTEXT || 
               securityProtocol == SECURITY_PROTOCOL_SASL_SSL
    }
    
    /**
     * Create a default insecure configuration (for development only)
     */
    fun createInsecureConfig(): SecurityConfiguration {
        logger.warn("Creating insecure Kafka configuration - DO NOT USE IN PRODUCTION")
        
        return SecurityConfiguration(
            securityProtocol = SECURITY_PROTOCOL_PLAINTEXT,
            sslConfig = SslConfiguration(),
            saslConfig = SaslConfiguration()
        )
    }
    
    /**
     * Create SSL configuration
     */
    fun createSslConfig(
        truststoreLocation: String,
        truststorePassword: String,
        keystoreLocation: String? = null,
        keystorePassword: String? = null,
        keyPassword: String? = null
    ): SecurityConfiguration {
        return SecurityConfiguration(
            securityProtocol = SECURITY_PROTOCOL_SSL,
            sslConfig = SslConfiguration(
                truststoreLocation = truststoreLocation,
                truststorePassword = truststorePassword,
                keystoreLocation = keystoreLocation,
                keystorePassword = keystorePassword,
                keyPassword = keyPassword
            ),
            saslConfig = SaslConfiguration()
        )
    }
    
    /**
     * Create SASL_SSL configuration
     */
    fun createSaslSslConfig(
        truststoreLocation: String,
        truststorePassword: String,
        saslMechanism: String,
        username: String,
        password: String
    ): SecurityConfiguration {
        return SecurityConfiguration(
            securityProtocol = SECURITY_PROTOCOL_SASL_SSL,
            sslConfig = SslConfiguration(
                truststoreLocation = truststoreLocation,
                truststorePassword = truststorePassword
            ),
            saslConfig = SaslConfiguration(
                mechanism = saslMechanism,
                username = username,
                password = password
            )
        )
    }
}

/**
 * Main security configuration container
 */
data class SecurityConfiguration(
    val securityProtocol: String,
    val sslConfig: SslConfiguration,
    val saslConfig: SaslConfiguration
)

/**
 * SSL/TLS configuration
 */
data class SslConfiguration(
    val protocol: String? = KafkaSecurityConfig.DEFAULT_SSL_PROTOCOL,
    val endpointIdentificationAlgorithm: String? = KafkaSecurityConfig.DEFAULT_SSL_ENDPOINT_ALGORITHM,
    val truststoreLocation: String? = null,
    val truststorePassword: String? = null,
    val truststoreType: String? = KafkaSecurityConfig.DEFAULT_TRUSTSTORE_TYPE,
    val keystoreLocation: String? = null,
    val keystorePassword: String? = null,
    val keystoreType: String? = KafkaSecurityConfig.DEFAULT_KEYSTORE_TYPE,
    val keyPassword: String? = null,
    val cipherSuites: List<String>? = null,
    val enabledProtocols: List<String>? = null
)

/**
 * SASL authentication configuration
 */
data class SaslConfiguration(
    val mechanism: String = KafkaSecurityConfig.SASL_MECHANISM_PLAIN,
    val username: String? = null,
    val password: String? = null,
    val jaasConfig: String? = null
)