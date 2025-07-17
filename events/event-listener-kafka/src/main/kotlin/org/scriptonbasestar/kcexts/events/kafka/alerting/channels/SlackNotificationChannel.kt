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

/**
 * Slack notification channel implementation
 */
class SlackNotificationChannel(
    private val config: SlackConfig,
    private val objectMapper: ObjectMapper
) : NotificationChannelImpl {
    
    companion object {
        private val logger = Logger.getLogger(SlackNotificationChannel::class.java)
    }
    
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    override fun sendNotification(notification: NotificationTask): NotificationResult {
        return try {
            val slackMessage = createSlackMessage(notification)
            val response = sendSlackMessage(slackMessage)
            
            if (response.statusCode() == 200) {
                logger.info("Slack notification sent successfully for alert: ${notification.alert.rule.name}")
                NotificationResult.Success("Slack message sent successfully")
            } else {
                val error = "Slack API returned status: ${response.statusCode()}, body: ${response.body()}"
                logger.error(error)
                NotificationResult.Failure(error)
            }
        } catch (e: Exception) {
            logger.error("Failed to send Slack notification", e)
            NotificationResult.Failure("Failed to send Slack message: ${e.message}")
        }
    }
    
    private fun createSlackMessage(notification: NotificationTask): SlackMessage {
        val alert = notification.alert
        val rule = alert.rule
        
        val color = when (notification.type) {
            NotificationType.TRIGGERED -> when (rule.severity) {
                AlertSeverity.CRITICAL -> "#FF0000"
                AlertSeverity.HIGH -> "#FF4500"
                AlertSeverity.MEDIUM -> "#FFA500"
                AlertSeverity.LOW -> "#FFFF00"
                AlertSeverity.INFO -> "#00BFFF"
            }
            NotificationType.ESCALATED -> "#FF8C00"
            NotificationType.RESOLVED -> "#00AA00"
        }
        
        val statusEmoji = when (notification.type) {
            NotificationType.TRIGGERED -> when (rule.severity) {
                AlertSeverity.CRITICAL -> ":rotating_light:"
                AlertSeverity.HIGH -> ":warning:"
                AlertSeverity.MEDIUM -> ":exclamation:"
                AlertSeverity.LOW -> ":information_source:"
                AlertSeverity.INFO -> ":bulb:"
            }
            NotificationType.ESCALATED -> ":double_vertical_bar:"
            NotificationType.RESOLVED -> ":white_check_mark:"
        }
        
        val statusText = when (notification.type) {
            NotificationType.TRIGGERED -> "TRIGGERED"
            NotificationType.ESCALATED -> "ESCALATED"
            NotificationType.RESOLVED -> "RESOLVED"
        }
        
        val attachment = SlackAttachment(
            color = color,
            fallback = "${rule.name} - $statusText",
            title = "$statusEmoji ${rule.name}",
            text = rule.description,
            fields = createSlackFields(notification),
            footer = "Keycloak Kafka Event Listener",
            footerIcon = "https://www.keycloak.org/resources/images/keycloak_icon_128px.png",
            timestamp = alert.triggeredAt.epochSecond
        )
        
        val text = when (notification.type) {
            NotificationType.TRIGGERED -> "*Alert Triggered*: ${rule.name} (${rule.severity})"
            NotificationType.ESCALATED -> "*Alert Escalated*: ${rule.name} (${rule.severity})"
            NotificationType.RESOLVED -> "*Alert Resolved*: ${rule.name}"
        }
        
        return SlackMessage(
            channel = config.channel,
            username = config.botName,
            iconEmoji = config.iconEmoji,
            text = text,
            attachments = listOf(attachment)
        )
    }
    
    private fun createSlackFields(notification: NotificationTask): List<SlackField> {
        val alert = notification.alert
        val rule = alert.rule
        
        val fields = mutableListOf<SlackField>()
        
        // Status
        val statusText = when (notification.type) {
            NotificationType.TRIGGERED -> "🔥 TRIGGERED"
            NotificationType.ESCALATED -> "⏫ ESCALATED"
            NotificationType.RESOLVED -> "✅ RESOLVED"
        }
        fields.add(SlackField("Status", statusText, true))
        
        // Severity
        fields.add(SlackField("Severity", rule.severity.toString(), true))
        
        // Metric and value
        fields.add(SlackField("Metric", rule.metric.toString(), true))
        fields.add(SlackField("Current Value", String.format("%.2f", alert.currentValue), true))
        fields.add(SlackField("Threshold", "${rule.threshold} (${rule.condition})", true))
        
        // Timing
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        fields.add(SlackField("Triggered At", alert.triggeredAt.atZone(java.time.ZoneId.systemDefault()).format(timeFormatter), true))
        
        if (alert.resolvedAt != null) {
            fields.add(SlackField("Resolved At", alert.resolvedAt!!.atZone(java.time.ZoneId.systemDefault()).format(timeFormatter), true))
        }
        
        // Environment
        fields.add(SlackField("Environment", config.environment, true))
        
        // Quick actions (for active alerts)
        if (notification.type != NotificationType.RESOLVED) {
            val actions = getQuickActions(rule.metric)
            if (actions.isNotEmpty()) {
                fields.add(SlackField("Quick Actions", actions, false))
            }
        }
        
        return fields
    }
    
    private fun getQuickActions(metric: AlertMetric): String {
        return when (metric) {
            AlertMetric.ERROR_RATE -> 
                "• Check logs: `kubectl logs -f deployment/keycloak-kafka`\n" +
                "• View dashboard: ${config.dashboardUrl}\n" +
                "• Reset circuit breaker: `curl -X POST http://localhost:9090/admin/circuit-breaker/reset`"
            
            AlertMetric.LATENCY_P95, AlertMetric.LATENCY_P99 -> 
                "• Check resource usage: `kubectl top pods`\n" +
                "• View performance dashboard: ${config.dashboardUrl}\n" +
                "• Review connection pool: `curl http://localhost:9090/metrics | grep pool`"
            
            AlertMetric.MEMORY_USAGE -> 
                "• Check memory: `kubectl top pods`\n" +
                "• Generate heap dump: `kubectl exec -it pod -- jcmd 1 GC.heap_dump /tmp/heap.hprof`\n" +
                "• Force GC: `kubectl exec -it pod -- jcmd 1 GC.run`"
            
            AlertMetric.CPU_USAGE -> 
                "• Check CPU: `kubectl top pods`\n" +
                "• Thread dump: `kubectl exec -it pod -- jstack 1`\n" +
                "• Scale up: `kubectl scale deployment keycloak-kafka --replicas=3`"
            
            AlertMetric.CONNECTION_FAILURES -> 
                "• Test connectivity: `kubectl exec -it pod -- telnet kafka 9092`\n" +
                "• Check certificates: `kubectl exec -it pod -- openssl s_client -connect kafka:9093`\n" +
                "• Restart pod: `kubectl delete pod -l app=keycloak-kafka`"
            
            AlertMetric.CIRCUIT_BREAKER_OPEN -> 
                "• Check downstream: `curl -f http://kafka:8080/health`\n" +
                "• Reset circuit breaker: `curl -X POST http://localhost:9090/admin/circuit-breaker/reset`\n" +
                "• View metrics: ${config.dashboardUrl}"
            
            AlertMetric.BACKPRESSURE_ACTIVE -> 
                "• Check queue: `curl http://localhost:9090/metrics | grep backpressure`\n" +
                "• Drain buffer: `curl -X POST http://localhost:9090/admin/backpressure/drain`\n" +
                "• Scale up: `kubectl scale deployment keycloak-kafka --replicas=3`"
            
            AlertMetric.KAFKA_LAG -> 
                "• Check consumer lag: `kafka-consumer-groups.sh --describe --group keycloak`\n" +
                "• Reset offsets: `kafka-consumer-groups.sh --reset-offsets --to-latest`\n" +
                "• Check Kafka health: `curl -f http://kafka:8080/health`"
            
            AlertMetric.DISK_USAGE -> 
                "• Check disk: `kubectl exec -it pod -- df -h`\n" +
                "• Clean logs: `kubectl exec -it pod -- find /opt/keycloak/logs -name '*.log' -mtime +7 -delete`\n" +
                "• Expand storage: Contact infrastructure team"
        }
    }
    
    private fun sendSlackMessage(message: SlackMessage): HttpResponse<String> {
        val json = objectMapper.writeValueAsString(message)
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create(config.webhookUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .timeout(Duration.ofSeconds(30))
            .build()
        
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
}

/**
 * Slack configuration
 */
data class SlackConfig(
    val webhookUrl: String,
    val channel: String,
    val botName: String = "Keycloak Kafka Alerts",
    val iconEmoji: String = ":warning:",
    val environment: String = "production",
    val dashboardUrl: String = "http://grafana:3000/d/keycloak-kafka"
)

/**
 * Slack message structure
 */
data class SlackMessage(
    val channel: String,
    val username: String,
    val iconEmoji: String? = null,
    val text: String,
    val attachments: List<SlackAttachment> = emptyList()
) {
    // Jackson serialization property names
    val icon_emoji = iconEmoji
}

/**
 * Slack attachment
 */
data class SlackAttachment(
    val color: String,
    val fallback: String,
    val title: String,
    val text: String? = null,
    val fields: List<SlackField> = emptyList(),
    val footer: String? = null,
    val footerIcon: String? = null,
    val timestamp: Long? = null
) {
    // Jackson serialization property names
    val footer_icon = footerIcon
}

/**
 * Slack field
 */
data class SlackField(
    val title: String,
    val value: String,
    val short: Boolean = false
)