package org.scriptonbasestar.kcexts.events.kafka.alerting.channels

import com.fasterxml.jackson.databind.ObjectMapper
import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.events.kafka.alerting.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Webhook notification channel implementation
 */
class WebhookNotificationChannel(
    private val config: WebhookConfig,
    private val objectMapper: ObjectMapper
) : NotificationChannelImpl {
    
    companion object {
        private val logger = Logger.getLogger(WebhookNotificationChannel::class.java)
    }
    
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(config.connectionTimeoutSeconds.toLong()))
        .build()
    
    override fun sendNotification(notification: NotificationTask): NotificationResult {
        return try {
            val webhookPayload = createWebhookPayload(notification)
            val response = sendWebhookRequest(webhookPayload)
            
            if (response.statusCode() in 200..299) {
                logger.info("Webhook notification sent successfully for alert: ${notification.alert.rule.name}")
                NotificationResult.Success("Webhook request successful: ${response.statusCode()}")
            } else {
                val error = "Webhook returned status: ${response.statusCode()}, body: ${response.body()}"
                logger.error(error)
                NotificationResult.Failure(error)
            }
        } catch (e: Exception) {
            logger.error("Failed to send webhook notification", e)
            NotificationResult.Failure("Failed to send webhook: ${e.message}")
        }
    }
    
    private fun createWebhookPayload(notification: NotificationTask): WebhookPayload {
        val alert = notification.alert
        val rule = alert.rule
        
        return WebhookPayload(
            version = "1.0",
            timestamp = System.currentTimeMillis(),
            event = WebhookEvent(
                type = when (notification.type) {
                    NotificationType.TRIGGERED -> "alert.triggered"
                    NotificationType.ESCALATED -> "alert.escalated"
                    NotificationType.RESOLVED -> "alert.resolved"
                },
                source = "keycloak-kafka-event-listener",
                environment = config.environment
            ),
            alert = WebhookAlert(
                id = alert.id,
                name = rule.name,
                description = rule.description,
                severity = rule.severity.toString(),
                status = alert.status.toString(),
                metric = WebhookMetric(
                    name = rule.metric.toString(),
                    currentValue = alert.currentValue,
                    threshold = rule.threshold,
                    condition = rule.condition.toString()
                ),
                timestamps = WebhookTimestamps(
                    triggeredAt = alert.triggeredAt.toString(),
                    resolvedAt = alert.resolvedAt?.toString(),
                    lastUpdated = alert.lastUpdated.toString()
                ),
                labels = createLabels(rule, alert),
                annotations = createAnnotations(rule, alert, notification)
            ),
            context = WebhookContext(
                service = "keycloak-kafka-event-listener",
                version = getServiceVersion(),
                instance = getInstanceId(),
                dashboardUrl = config.dashboardUrl,
                runbookUrl = config.runbookUrl
            )
        )
    }
    
    private fun createLabels(rule: AlertRule, alert: AlertInstance): Map<String, String> {
        return mapOf(
            "alertname" to rule.name,
            "severity" to rule.severity.toString(),
            "metric" to rule.metric.toString(),
            "service" to "keycloak-kafka-event-listener",
            "environment" to config.environment,
            "team" to config.teamName
        )
    }
    
    private fun createAnnotations(rule: AlertRule, alert: AlertInstance, notification: NotificationTask): Map<String, String> {
        val annotations = mutableMapOf<String, String>()
        
        annotations["summary"] = when (notification.type) {
            NotificationType.TRIGGERED -> "${rule.name} has been triggered"
            NotificationType.ESCALATED -> "${rule.name} has been escalated"
            NotificationType.RESOLVED -> "${rule.name} has been resolved"
        }
        
        annotations["description"] = rule.description
        annotations["current_value"] = String.format("%.2f", alert.currentValue)
        annotations["threshold"] = rule.threshold.toString()
        annotations["condition"] = rule.condition.toString()
        annotations["runbook_url"] = config.runbookUrl ?: "https://docs.company.com/runbooks/keycloak-kafka"
        annotations["dashboard_url"] = config.dashboardUrl ?: "http://grafana:3000/d/keycloak-kafka"
        
        // Add metric-specific annotations
        when (rule.metric) {
            AlertMetric.ERROR_RATE -> {
                annotations["recommended_action"] = "Check logs and verify Kafka connectivity"
                annotations["escalation_path"] = "Platform Team → Engineering Team"
            }
            AlertMetric.LATENCY_P95, AlertMetric.LATENCY_P99 -> {
                annotations["recommended_action"] = "Check system resources and network performance"
                annotations["escalation_path"] = "Platform Team → Performance Team"
            }
            AlertMetric.MEMORY_USAGE -> {
                annotations["recommended_action"] = "Check for memory leaks and consider scaling"
                annotations["escalation_path"] = "Platform Team → Engineering Team"
            }
            AlertMetric.CPU_USAGE -> {
                annotations["recommended_action"] = "Check system load and consider horizontal scaling"
                annotations["escalation_path"] = "Platform Team → Infrastructure Team"
            }
            AlertMetric.CONNECTION_FAILURES -> {
                annotations["recommended_action"] = "Verify Kafka broker health and network connectivity"
                annotations["escalation_path"] = "Platform Team → Infrastructure Team → Kafka Team"
            }
            AlertMetric.CIRCUIT_BREAKER_OPEN -> {
                annotations["recommended_action"] = "Check downstream services and reset circuit breaker if appropriate"
                annotations["escalation_path"] = "Platform Team → Engineering Team"
            }
            AlertMetric.BACKPRESSURE_ACTIVE -> {
                annotations["recommended_action"] = "Check system capacity and drain backpressure buffer"
                annotations["escalation_path"] = "Platform Team → Performance Team"
            }
            AlertMetric.KAFKA_LAG -> {
                annotations["recommended_action"] = "Check Kafka consumer lag and partition distribution"
                annotations["escalation_path"] = "Platform Team → Kafka Team"
            }
            AlertMetric.DISK_USAGE -> {
                annotations["recommended_action"] = "Clean up logs and expand storage if needed"
                annotations["escalation_path"] = "Platform Team → Infrastructure Team"
            }
        }
        
        return annotations
    }
    
    private fun sendWebhookRequest(payload: WebhookPayload): HttpResponse<String> {
        val json = objectMapper.writeValueAsString(payload)
        
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(config.url))
            .header("Content-Type", "application/json")
            .header("User-Agent", "Keycloak-Kafka-Event-Listener/1.0")
            .timeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
        
        // Add custom headers
        config.headers.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }
        
        // Add signature if secret is provided
        if (config.secret.isNotEmpty()) {
            val signature = generateSignature(json, config.secret)
            requestBuilder.header("X-Signature-SHA256", "sha256=$signature")
        }
        
        // Add authentication if provided
        if (config.authentication != null) {
            when (config.authentication.type) {
                WebhookAuthType.BEARER -> {
                    requestBuilder.header("Authorization", "Bearer ${config.authentication.token}")
                }
                WebhookAuthType.BASIC -> {
                    val credentials = Base64.getEncoder().encodeToString(
                        "${config.authentication.username}:${config.authentication.password}".toByteArray()
                    )
                    requestBuilder.header("Authorization", "Basic $credentials")
                }
                WebhookAuthType.API_KEY -> {
                    requestBuilder.header(config.authentication.headerName ?: "X-API-Key", config.authentication.token)
                }
            }
        }
        
        val request = requestBuilder
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()
        
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
    
    private fun generateSignature(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        mac.init(secretKey)
        val signature = mac.doFinal(payload.toByteArray())
        return signature.joinToString("") { "%02x".format(it) }
    }
    
    private fun getServiceVersion(): String {
        return System.getProperty("service.version") ?: "unknown"
    }
    
    private fun getInstanceId(): String {
        return System.getProperty("instance.id") ?: System.getenv("HOSTNAME") ?: "unknown"
    }
}

/**
 * Webhook configuration
 */
data class WebhookConfig(
    val url: String,
    val secret: String = "",
    val headers: Map<String, String> = emptyMap(),
    val authentication: WebhookAuthentication? = null,
    val connectionTimeoutSeconds: Int = 10,
    val timeoutSeconds: Int = 30,
    val retryAttempts: Int = 3,
    val retryDelaySeconds: Int = 5,
    val environment: String = "production",
    val teamName: String = "platform",
    val dashboardUrl: String? = null,
    val runbookUrl: String? = null
)

/**
 * Webhook authentication
 */
data class WebhookAuthentication(
    val type: WebhookAuthType,
    val token: String = "",
    val username: String = "",
    val password: String = "",
    val headerName: String? = null
)

/**
 * Webhook authentication types
 */
enum class WebhookAuthType {
    BEARER, BASIC, API_KEY
}

/**
 * Webhook payload structure
 */
data class WebhookPayload(
    val version: String,
    val timestamp: Long,
    val event: WebhookEvent,
    val alert: WebhookAlert,
    val context: WebhookContext
)

/**
 * Webhook event
 */
data class WebhookEvent(
    val type: String,
    val source: String,
    val environment: String
)

/**
 * Webhook alert
 */
data class WebhookAlert(
    val id: String,
    val name: String,
    val description: String,
    val severity: String,
    val status: String,
    val metric: WebhookMetric,
    val timestamps: WebhookTimestamps,
    val labels: Map<String, String>,
    val annotations: Map<String, String>
)

/**
 * Webhook metric
 */
data class WebhookMetric(
    val name: String,
    val currentValue: Double,
    val threshold: Double,
    val condition: String
)

/**
 * Webhook timestamps
 */
data class WebhookTimestamps(
    val triggeredAt: String,
    val resolvedAt: String?,
    val lastUpdated: String
)

/**
 * Webhook context
 */
data class WebhookContext(
    val service: String,
    val version: String,
    val instance: String,
    val dashboardUrl: String?,
    val runbookUrl: String?
)