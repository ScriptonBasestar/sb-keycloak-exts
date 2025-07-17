package org.scriptonbasestar.kcexts.events.kafka.alerting.channels

import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.events.kafka.alerting.*
import java.util.*
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Email notification channel implementation
 */
class EmailNotificationChannel(
    private val config: EmailConfig
) : NotificationChannelImpl {
    
    companion object {
        private val logger = Logger.getLogger(EmailNotificationChannel::class.java)
    }
    
    private val session: Session by lazy {
        val props = Properties().apply {
            put("mail.smtp.host", config.smtpHost)
            put("mail.smtp.port", config.smtpPort)
            put("mail.smtp.auth", config.useAuth)
            put("mail.smtp.starttls.enable", config.useTls)
            put("mail.smtp.ssl.enable", config.useSsl)
            put("mail.smtp.connectiontimeout", config.connectionTimeoutMs)
            put("mail.smtp.timeout", config.readTimeoutMs)
        }
        
        if (config.useAuth) {
            Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(config.username, config.password)
                }
            })
        } else {
            Session.getInstance(props)
        }
    }
    
    override fun sendNotification(notification: NotificationTask): NotificationResult {
        return try {
            val message = createMessage(notification)
            Transport.send(message)
            
            logger.info("Email sent successfully for alert: ${notification.alert.rule.name}")
            NotificationResult.Success("Email sent successfully")
        } catch (e: Exception) {
            logger.error("Failed to send email notification", e)
            NotificationResult.Failure("Failed to send email: ${e.message}")
        }
    }
    
    private fun createMessage(notification: NotificationTask): Message {
        val alert = notification.alert
        val rule = alert.rule
        
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(config.fromAddress, config.fromName))
        
        // Set recipients
        val recipients = config.toAddresses.map { InternetAddress(it) }.toTypedArray()
        message.setRecipients(Message.RecipientType.TO, recipients)
        
        if (config.ccAddresses.isNotEmpty()) {
            val ccRecipients = config.ccAddresses.map { InternetAddress(it) }.toTypedArray()
            message.setRecipients(Message.RecipientType.CC, ccRecipients)
        }
        
        // Set subject
        val subject = when (notification.type) {
            NotificationType.TRIGGERED -> "[${rule.severity}] ${rule.name} - ALERT TRIGGERED"
            NotificationType.ESCALATED -> "[${rule.severity}] ${rule.name} - ESCALATED"
            NotificationType.RESOLVED -> "[RESOLVED] ${rule.name}"
        }
        message.subject = subject
        
        // Set body
        val body = createEmailBody(notification)
        message.setContent(body, "text/html; charset=utf-8")
        
        message.sentDate = Date()
        
        return message
    }
    
    private fun createEmailBody(notification: NotificationTask): String {
        val alert = notification.alert
        val rule = alert.rule
        
        val statusColor = when (notification.type) {
            NotificationType.TRIGGERED -> "#FF0000"
            NotificationType.ESCALATED -> "#FF8C00"
            NotificationType.RESOLVED -> "#00AA00"
        }
        
        val statusText = when (notification.type) {
            NotificationType.TRIGGERED -> "TRIGGERED"
            NotificationType.ESCALATED -> "ESCALATED"
            NotificationType.RESOLVED -> "RESOLVED"
        }
        
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <title>Keycloak Kafka Alert</title>
            <style>
                body { font-family: Arial, sans-serif; margin: 20px; }
                .header { background-color: $statusColor; color: white; padding: 15px; border-radius: 5px; }
                .content { margin: 20px 0; }
                .detail-table { border-collapse: collapse; width: 100%; }
                .detail-table th, .detail-table td { 
                    border: 1px solid #ddd; 
                    padding: 8px; 
                    text-align: left; 
                }
                .detail-table th { background-color: #f2f2f2; }
                .footer { margin-top: 30px; font-size: 12px; color: #666; }
            </style>
        </head>
        <body>
            <div class="header">
                <h2>Keycloak Kafka Event Listener Alert - $statusText</h2>
            </div>
            
            <div class="content">
                <h3>Alert Details</h3>
                <table class="detail-table">
                    <tr>
                        <th>Alert Name</th>
                        <td>${rule.name}</td>
                    </tr>
                    <tr>
                        <th>Description</th>
                        <td>${rule.description}</td>
                    </tr>
                    <tr>
                        <th>Severity</th>
                        <td>${rule.severity}</td>
                    </tr>
                    <tr>
                        <th>Status</th>
                        <td style="color: $statusColor; font-weight: bold;">$statusText</td>
                    </tr>
                    <tr>
                        <th>Metric</th>
                        <td>${rule.metric}</td>
                    </tr>
                    <tr>
                        <th>Current Value</th>
                        <td>${String.format("%.2f", alert.currentValue)}</td>
                    </tr>
                    <tr>
                        <th>Threshold</th>
                        <td>${rule.threshold} (${rule.condition})</td>
                    </tr>
                    <tr>
                        <th>Triggered At</th>
                        <td>${alert.triggeredAt}</td>
                    </tr>
                    ${if (alert.resolvedAt != null) 
                        "<tr><th>Resolved At</th><td>${alert.resolvedAt}</td></tr>" 
                      else ""}
                </table>
                
                ${if (notification.type != NotificationType.RESOLVED) """
                <h3>Recommended Actions</h3>
                <ul>
                    ${getRecommendedActions(rule.metric)}
                </ul>
                """ else ""}
                
                <h3>System Information</h3>
                <ul>
                    <li><strong>Environment:</strong> ${config.environment}</li>
                    <li><strong>Service:</strong> Keycloak Kafka Event Listener</li>
                    <li><strong>Alert ID:</strong> ${alert.id}</li>
                </ul>
            </div>
            
            <div class="footer">
                <p>This alert was generated by Keycloak Kafka Event Listener monitoring system.</p>
                <p>For more information, check the monitoring dashboard or contact the operations team.</p>
            </div>
        </body>
        </html>
        """.trimIndent()
    }
    
    private fun getRecommendedActions(metric: AlertMetric): String {
        return when (metric) {
            AlertMetric.ERROR_RATE -> """
                <li>Check Keycloak and Kafka logs for error patterns</li>
                <li>Verify Kafka broker connectivity and health</li>
                <li>Check authentication credentials and certificates</li>
                <li>Review circuit breaker status</li>
            """.trimIndent()
            
            AlertMetric.LATENCY_P95, AlertMetric.LATENCY_P99 -> """
                <li>Check system resource usage (CPU, memory, disk)</li>
                <li>Verify Kafka broker performance</li>
                <li>Review connection pool statistics</li>
                <li>Consider adjusting batch size and linger time</li>
            """.trimIndent()
            
            AlertMetric.MEMORY_USAGE -> """
                <li>Check for memory leaks in application logs</li>
                <li>Review JVM garbage collection logs</li>
                <li>Consider increasing heap memory allocation</li>
                <li>Verify connection pool configuration</li>
            """.trimIndent()
            
            AlertMetric.CPU_USAGE -> """
                <li>Check for high CPU consuming processes</li>
                <li>Review thread pool utilization</li>
                <li>Consider scaling horizontally</li>
                <li>Check for inefficient algorithms or loops</li>
            """.trimIndent()
            
            AlertMetric.CONNECTION_FAILURES -> """
                <li>Verify Kafka broker availability</li>
                <li>Check network connectivity and firewall rules</li>
                <li>Review SSL/TLS certificate validity</li>
                <li>Check authentication configuration</li>
            """.trimIndent()
            
            AlertMetric.CIRCUIT_BREAKER_OPEN -> """
                <li>Check downstream service health (Kafka)</li>
                <li>Review error logs for failure patterns</li>
                <li>Consider manual circuit breaker reset if appropriate</li>
                <li>Verify network connectivity</li>
            """.trimIndent()
            
            AlertMetric.BACKPRESSURE_ACTIVE -> """
                <li>Check system load and resource availability</li>
                <li>Review producer queue size and utilization</li>
                <li>Consider adjusting backpressure thresholds</li>
                <li>Verify Kafka broker capacity</li>
            """.trimIndent()
            
            AlertMetric.KAFKA_LAG -> """
                <li>Check Kafka consumer group lag</li>
                <li>Verify Kafka broker partition distribution</li>
                <li>Review producer throughput settings</li>
                <li>Consider increasing partition count</li>
            """.trimIndent()
            
            AlertMetric.DISK_USAGE -> """
                <li>Clean up old log files and temporary data</li>
                <li>Check disk space on Kafka brokers</li>
                <li>Review log retention policies</li>
                <li>Consider disk expansion if needed</li>
            """.trimIndent()
        }
    }
}

/**
 * Email configuration
 */
data class EmailConfig(
    val smtpHost: String,
    val smtpPort: Int = 587,
    val useAuth: Boolean = true,
    val useTls: Boolean = true,
    val useSsl: Boolean = false,
    val username: String = "",
    val password: String = "",
    val fromAddress: String,
    val fromName: String = "Keycloak Kafka Alerts",
    val toAddresses: List<String>,
    val ccAddresses: List<String> = emptyList(),
    val connectionTimeoutMs: Int = 30000,
    val readTimeoutMs: Int = 30000,
    val environment: String = "production"
)

/**
 * Notification channel interface
 */
interface NotificationChannelImpl {
    fun sendNotification(notification: NotificationTask): NotificationResult
}

/**
 * Notification result
 */
sealed class NotificationResult {
    data class Success(val message: String) : NotificationResult()
    data class Failure(val error: String) : NotificationResult()
}