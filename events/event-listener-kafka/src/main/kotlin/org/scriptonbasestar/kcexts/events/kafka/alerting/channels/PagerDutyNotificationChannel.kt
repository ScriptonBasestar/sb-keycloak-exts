package org.scriptonbasestar.kcexts.events.kafka.alerting.channels

import com.fasterxml.jackson.databind.ObjectMapper
import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.events.kafka.alerting.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * PagerDuty notification channel implementation
 */
class PagerDutyNotificationChannel(
    private val config: PagerDutyConfig,
    private val objectMapper: ObjectMapper
) : NotificationChannelImpl {
    
    companion object {
        private val logger = Logger.getLogger(PagerDutyNotificationChannel::class.java)
        private const val PAGERDUTY_EVENTS_V2_URL = "https://events.pagerduty.com/v2/enqueue"
    }
    
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    override fun sendNotification(notification: NotificationTask): NotificationResult {
        return try {
            val pagerDutyEvent = createPagerDutyEvent(notification)
            val response = sendPagerDutyEvent(pagerDutyEvent)
            
            if (response.statusCode() == 202) {
                logger.info("PagerDuty event sent successfully for alert: ${notification.alert.rule.name}")
                NotificationResult.Success("PagerDuty event sent successfully")
            } else {
                val error = "PagerDuty API returned status: ${response.statusCode()}, body: ${response.body()}"
                logger.error(error)
                NotificationResult.Failure(error)
            }
        } catch (e: Exception) {
            logger.error("Failed to send PagerDuty notification", e)
            NotificationResult.Failure("Failed to send PagerDuty event: ${e.message}")
        }
    }
    
    private fun createPagerDutyEvent(notification: NotificationTask): PagerDutyEvent {
        val alert = notification.alert
        val rule = alert.rule
        
        val eventAction = when (notification.type) {
            NotificationType.TRIGGERED -> "trigger"
            NotificationType.ESCALATED -> "trigger" // Escalation is still a trigger in PagerDuty
            NotificationType.RESOLVED -> "resolve"
        }
        
        val severity = when (rule.severity) {
            AlertSeverity.CRITICAL -> "critical"
            AlertSeverity.HIGH -> "error"
            AlertSeverity.MEDIUM -> "warning"
            AlertSeverity.LOW -> "info"
            AlertSeverity.INFO -> "info"
        }
        
        val dedupKey = generateDedupKey(alert)
        
        return PagerDutyEvent(
            routingKey = config.integrationKey,
            eventAction = eventAction,
            dedupKey = dedupKey,
            payload = PagerDutyPayload(
                summary = createSummary(notification),
                source = config.source,
                severity = severity,
                component = "keycloak-kafka-event-listener",
                group = config.serviceGroup,
                class_ = rule.metric.toString(),
                customDetails = createCustomDetails(notification)
            ),
            client = "Keycloak Kafka Event Listener",
            clientUrl = config.dashboardUrl,
            images = createImages(notification),
            links = createLinks(notification)
        )
    }
    
    private fun createSummary(notification: NotificationTask): String {
        val alert = notification.alert
        val rule = alert.rule
        
        return when (notification.type) {
            NotificationType.TRIGGERED -> 
                "[${rule.severity}] ${rule.name}: ${rule.metric} is ${String.format("%.2f", alert.currentValue)} (threshold: ${rule.threshold})"
            NotificationType.ESCALATED -> 
                "[ESCALATED] ${rule.name}: ${rule.metric} is ${String.format("%.2f", alert.currentValue)} (threshold: ${rule.threshold})"
            NotificationType.RESOLVED -> 
                "[RESOLVED] ${rule.name}: ${rule.metric} has returned to normal"
        }
    }
    
    private fun createCustomDetails(notification: NotificationTask): Map<String, Any> {
        val alert = notification.alert
        val rule = alert.rule
        
        val details = mutableMapOf<String, Any>()
        
        // Basic alert information
        details["alert_id"] = alert.id
        details["alert_name"] = rule.name
        details["alert_description"] = rule.description
        details["metric_name"] = rule.metric.toString()
        details["current_value"] = alert.currentValue
        details["threshold"] = rule.threshold
        details["condition"] = rule.condition.toString()
        details["severity"] = rule.severity.toString()
        details["status"] = alert.status.toString()
        details["environment"] = config.environment
        
        // Timestamps
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss UTC")
        details["triggered_at"] = alert.triggeredAt.atZone(java.time.ZoneOffset.UTC).format(formatter)
        details["last_updated"] = alert.lastUpdated.atZone(java.time.ZoneOffset.UTC).format(formatter)
        
        if (alert.resolvedAt != null) {
            details["resolved_at"] = alert.resolvedAt!!.atZone(java.time.ZoneOffset.UTC).format(formatter)
            val duration = Duration.between(alert.triggeredAt, alert.resolvedAt)
            details["duration_minutes"] = duration.toMinutes()
        }
        
        // Service information
        details["service"] = "keycloak-kafka-event-listener"
        details["instance"] = System.getenv("HOSTNAME") ?: "unknown"
        details["version"] = System.getProperty("service.version") ?: "unknown"
        
        // Recommended actions
        details["recommended_actions"] = getRecommendedActions(rule.metric)
        
        // Escalation information
        if (notification.type == NotificationType.ESCALATED) {
            details["escalation_level"] = "Level 2"
            details["escalation_reason"] = "Alert not acknowledged within specified time"
        }
        
        // Metric-specific details
        when (rule.metric) {
            AlertMetric.ERROR_RATE -> {
                details["impact"] = "Users may experience authentication failures"
                details["urgency"] = "High"
            }
            AlertMetric.LATENCY_P95, AlertMetric.LATENCY_P99 -> {
                details["impact"] = "Users may experience slow response times"
                details["urgency"] = "Medium"
            }
            AlertMetric.MEMORY_USAGE -> {
                details["impact"] = "Service may become unstable or crash"
                details["urgency"] = "High"
            }
            AlertMetric.CPU_USAGE -> {
                details["impact"] = "Service performance may degrade"
                details["urgency"] = "Medium"
            }
            AlertMetric.CONNECTION_FAILURES -> {
                details["impact"] = "Events may not be sent to Kafka"
                details["urgency"] = "High"
            }
            AlertMetric.CIRCUIT_BREAKER_OPEN -> {
                details["impact"] = "Service is failing fast to protect downstream"
                details["urgency"] = "Critical"
            }
            AlertMetric.BACKPRESSURE_ACTIVE -> {
                details["impact"] = "Event processing may be delayed"
                details["urgency"] = "Medium"
            }
            AlertMetric.KAFKA_LAG -> {
                details["impact"] = "Events may be processed with delay"
                details["urgency"] = "Medium"
            }
            AlertMetric.DISK_USAGE -> {
                details["impact"] = "Service may fail due to lack of disk space"
                details["urgency"] = "High"
            }
        }
        
        return details
    }
    
    private fun getRecommendedActions(metric: AlertMetric): List<String> {
        return when (metric) {
            AlertMetric.ERROR_RATE -> listOf(
                "Check Keycloak and Kafka logs for error patterns",
                "Verify Kafka broker connectivity and health",
                "Review authentication credentials and certificates",
                "Check circuit breaker status and reset if appropriate"
            )
            AlertMetric.LATENCY_P95, AlertMetric.LATENCY_P99 -> listOf(
                "Check system resource usage (CPU, memory, disk)",
                "Verify Kafka broker performance and network latency",
                "Review connection pool statistics and tuning",
                "Consider adjusting batch size and linger time"
            )
            AlertMetric.MEMORY_USAGE -> listOf(
                "Check for memory leaks in application logs",
                "Review JVM garbage collection logs",
                "Generate heap dump for analysis",
                "Consider increasing heap memory allocation"
            )
            AlertMetric.CPU_USAGE -> listOf(
                "Identify high CPU consuming processes",
                "Review thread pool utilization",
                "Check for inefficient algorithms or infinite loops",
                "Consider horizontal scaling"
            )
            AlertMetric.CONNECTION_FAILURES -> listOf(
                "Verify Kafka broker availability and health",
                "Check network connectivity and firewall rules",
                "Review SSL/TLS certificate validity",
                "Validate authentication configuration"
            )
            AlertMetric.CIRCUIT_BREAKER_OPEN -> listOf(
                "Check downstream service health (Kafka brokers)",
                "Review error logs for failure patterns",
                "Verify network connectivity to Kafka",
                "Consider manual circuit breaker reset after fixing issues"
            )
            AlertMetric.BACKPRESSURE_ACTIVE -> listOf(
                "Check system load and resource availability",
                "Review producer queue size and utilization",
                "Consider adjusting backpressure thresholds",
                "Verify Kafka broker capacity and performance"
            )
            AlertMetric.KAFKA_LAG -> listOf(
                "Check Kafka consumer group lag",
                "Verify Kafka broker partition distribution",
                "Review producer throughput settings",
                "Consider increasing partition count or consumer instances"
            )
            AlertMetric.DISK_USAGE -> listOf(
                "Clean up old log files and temporary data",
                "Check disk space on Kafka brokers",
                "Review log retention policies",
                "Plan for disk expansion if growth trend continues"
            )
        }
    }
    
    private fun createImages(notification: NotificationTask): List<PagerDutyImage> {
        if (config.dashboardUrl.isNullOrEmpty()) return emptyList()
        
        val alert = notification.alert
        val rule = alert.rule
        
        return listOf(
            PagerDutyImage(
                src = "${config.dashboardUrl}/render/d-solo/keycloak-kafka/alert-${rule.metric.toString().toLowerCase()}?orgId=1&width=1000&height=500&tz=UTC",
                href = config.dashboardUrl,
                alt = "Alert Dashboard - ${rule.name}"
            )
        )
    }
    
    private fun createLinks(notification: NotificationTask): List<PagerDutyLink> {
        val links = mutableListOf<PagerDutyLink>()
        
        // Dashboard link
        if (!config.dashboardUrl.isNullOrEmpty()) {
            links.add(
                PagerDutyLink(
                    href = config.dashboardUrl,
                    text = "View Dashboard"
                )
            )
        }
        
        // Runbook link
        if (!config.runbookUrl.isNullOrEmpty()) {
            links.add(
                PagerDutyLink(
                    href = config.runbookUrl,
                    text = "Troubleshooting Runbook"
                )
            )
        }
        
        // Logs link
        if (!config.logsUrl.isNullOrEmpty()) {
            links.add(
                PagerDutyLink(
                    href = config.logsUrl,
                    text = "View Logs"
                )
            )
        }
        
        return links
    }
    
    private fun generateDedupKey(alert: AlertInstance): String {
        // Use rule name and metric to deduplicate similar alerts
        return "keycloak-kafka-${alert.rule.name}-${alert.rule.metric}".toLowerCase()
            .replace("[^a-z0-9-]".toRegex(), "-")
            .replace("-+".toRegex(), "-")
            .trim('-')
    }
    
    private fun sendPagerDutyEvent(event: PagerDutyEvent): HttpResponse<String> {
        val json = objectMapper.writeValueAsString(event)
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create(PAGERDUTY_EVENTS_V2_URL))
            .header("Content-Type", "application/json")
            .header("User-Agent", "Keycloak-Kafka-Event-Listener/1.0")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .timeout(Duration.ofSeconds(30))
            .build()
        
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
}

/**
 * PagerDuty configuration
 */
data class PagerDutyConfig(
    val integrationKey: String,
    val source: String = "keycloak-kafka-event-listener",
    val serviceGroup: String = "authentication",
    val environment: String = "production",
    val dashboardUrl: String? = null,
    val runbookUrl: String? = null,
    val logsUrl: String? = null
)

/**
 * PagerDuty event structure
 */
data class PagerDutyEvent(
    val routingKey: String,
    val eventAction: String,
    val dedupKey: String,
    val payload: PagerDutyPayload,
    val client: String? = null,
    val clientUrl: String? = null,
    val images: List<PagerDutyImage> = emptyList(),
    val links: List<PagerDutyLink> = emptyList()
) {
    // Jackson serialization property names
    val routing_key = routingKey
    val event_action = eventAction
    val dedup_key = dedupKey
    val client_url = clientUrl
}

/**
 * PagerDuty payload
 */
data class PagerDutyPayload(
    val summary: String,
    val source: String,
    val severity: String,
    val component: String? = null,
    val group: String? = null,
    val class_: String? = null,
    val customDetails: Map<String, Any> = emptyMap()
) {
    // Jackson serialization property names
    val custom_details = customDetails
}

/**
 * PagerDuty image
 */
data class PagerDutyImage(
    val src: String,
    val href: String? = null,
    val alt: String? = null
)

/**
 * PagerDuty link
 */
data class PagerDutyLink(
    val href: String,
    val text: String? = null
)