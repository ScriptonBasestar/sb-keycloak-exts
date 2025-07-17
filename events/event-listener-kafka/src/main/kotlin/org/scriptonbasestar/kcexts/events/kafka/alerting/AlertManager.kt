package org.scriptonbasestar.kcexts.events.kafka.alerting

import com.fasterxml.jackson.databind.ObjectMapper
import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.events.kafka.metrics.MetricsCollector
import java.time.Instant
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Alert Manager for Keycloak Kafka Event Listener
 * Handles alerting, escalation, and notification management
 */
class AlertManager(
    private val config: AlertConfig,
    private val metricsCollector: MetricsCollector,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val logger = Logger.getLogger(AlertManager::class.java)
    }

    private val executorService: ScheduledExecutorService =
        Executors.newScheduledThreadPool(
            config.threadPoolSize,
            ThreadFactory { r ->
                val thread = Thread(r, "alert-manager-${threadCounter.incrementAndGet()}")
                thread.isDaemon = true
                thread
            },
        )

    private val alertHistory = ConcurrentLinkedQueue<AlertInstance>()
    private val activeAlerts = ConcurrentHashMap<String, AlertInstance>()
    private val notificationQueue = LinkedBlockingQueue<NotificationTask>()
    private val suppressedAlerts = ConcurrentHashMap<String, Long>()

    private val isRunning = AtomicBoolean(false)
    private val totalAlertsGenerated = AtomicLong(0)
    private val totalNotificationsSent = AtomicLong(0)
    private val alertsSupressed = AtomicLong(0)

    @Volatile
    private var notificationWorker: Future<*>? = null

    init {
        start()
    }

    /**
     * Start the alert manager
     */
    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            logger.info("Starting Alert Manager...")

            // Start metric evaluation scheduler
            executorService.scheduleWithFixedDelay(
                ::evaluateMetrics,
                config.evaluationInterval.toSeconds(),
                config.evaluationInterval.toSeconds(),
                TimeUnit.SECONDS,
            )

            // Start notification worker
            notificationWorker = executorService.submit(::processNotifications)

            // Start alert cleanup scheduler
            executorService.scheduleWithFixedDelay(
                ::cleanupAlerts,
                60,
                60,
                TimeUnit.SECONDS,
            )

            logger.info("Alert Manager started successfully")
        }
    }

    /**
     * Stop the alert manager
     */
    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            logger.info("Stopping Alert Manager...")

            notificationWorker?.cancel(true)
            executorService.shutdown()

            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow()
                }
            } catch (e: InterruptedException) {
                executorService.shutdownNow()
                Thread.currentThread().interrupt()
            }

            logger.info("Alert Manager stopped")
        }
    }

    /**
     * Evaluate metrics against alert rules
     */
    private fun evaluateMetrics() {
        if (!isRunning.get()) return

        try {
            config.alertRules.forEach { rule ->
                evaluateRule(rule)
            }
        } catch (e: Exception) {
            logger.error("Error during metric evaluation", e)
        }
    }

    /**
     * Evaluate a specific alert rule
     */
    private fun evaluateRule(rule: AlertRule) {
        try {
            val currentValue =
                when (rule.metric) {
                    AlertMetric.ERROR_RATE -> calculateErrorRate()
                    AlertMetric.LATENCY_P95 -> calculateLatencyP95()
                    AlertMetric.LATENCY_P99 -> calculateLatencyP99()
                    AlertMetric.MEMORY_USAGE -> calculateMemoryUsage()
                    AlertMetric.CPU_USAGE -> calculateCpuUsage()
                    AlertMetric.CONNECTION_FAILURES -> calculateConnectionFailures()
                    AlertMetric.CIRCUIT_BREAKER_OPEN -> if (isCircuitBreakerOpen()) 1.0 else 0.0
                    AlertMetric.BACKPRESSURE_ACTIVE -> if (isBackpressureActive()) 1.0 else 0.0
                    AlertMetric.KAFKA_LAG -> calculateKafkaLag()
                    AlertMetric.DISK_USAGE -> calculateDiskUsage()
                }

            val alertKey = "${rule.name}-${rule.severity}"
            val existingAlert = activeAlerts[alertKey]

            when (rule.condition) {
                AlertCondition.GREATER_THAN -> {
                    if (currentValue > rule.threshold) {
                        handleAlertTriggered(rule, currentValue, existingAlert)
                    } else {
                        handleAlertResolved(rule, existingAlert)
                    }
                }
                AlertCondition.LESS_THAN -> {
                    if (currentValue < rule.threshold) {
                        handleAlertTriggered(rule, currentValue, existingAlert)
                    } else {
                        handleAlertResolved(rule, existingAlert)
                    }
                }
                AlertCondition.EQUALS -> {
                    if (currentValue == rule.threshold) {
                        handleAlertTriggered(rule, currentValue, existingAlert)
                    } else {
                        handleAlertResolved(rule, existingAlert)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error evaluating rule: ${rule.name}", e)
        }
    }

    /**
     * Handle alert triggered
     */
    private fun handleAlertTriggered(
        rule: AlertRule,
        currentValue: Double,
        existingAlert: AlertInstance?,
    ) {
        val alertKey = "${rule.name}-${rule.severity}"

        if (existingAlert != null) {
            // Update existing alert
            existingAlert.updateValue(currentValue)
            return
        }

        // Check if alert is suppressed
        val suppressUntil = suppressedAlerts[alertKey]
        if (suppressUntil != null && System.currentTimeMillis() < suppressUntil) {
            alertsSupressed.incrementAndGet()
            return
        }

        // Create new alert
        val alert =
            AlertInstance(
                id = generateAlertId(),
                rule = rule,
                currentValue = currentValue,
                triggeredAt = Instant.now(),
                status = AlertStatus.FIRING,
            )

        activeAlerts[alertKey] = alert
        alertHistory.offer(alert)
        totalAlertsGenerated.incrementAndGet()

        logger.warn(
            "Alert triggered: ${rule.name} (${rule.severity}) - Current: $currentValue, Threshold: ${rule.threshold}",
        )

        // Schedule notifications
        scheduleNotifications(alert)
    }

    /**
     * Handle alert resolved
     */
    private fun handleAlertResolved(
        rule: AlertRule,
        existingAlert: AlertInstance?,
    ) {
        if (existingAlert != null) {
            val alertKey = "${rule.name}-${rule.severity}"
            existingAlert.resolve()
            activeAlerts.remove(alertKey)

            logger.info("Alert resolved: ${rule.name} (${rule.severity})")

            // Send resolution notification
            if (rule.notifyOnResolve) {
                val notification =
                    NotificationTask(
                        alert = existingAlert,
                        channels = rule.notificationChannels,
                        type = NotificationType.RESOLVED,
                    )
                notificationQueue.offer(notification)
            }
        }
    }

    /**
     * Schedule notifications for an alert
     */
    private fun scheduleNotifications(alert: AlertInstance) {
        val rule = alert.rule

        // Immediate notification
        val immediateNotification =
            NotificationTask(
                alert = alert,
                channels = rule.notificationChannels,
                type = NotificationType.TRIGGERED,
            )
        notificationQueue.offer(immediateNotification)

        // Schedule escalation notifications
        rule.escalationLevels.forEach { escalation ->
            executorService.schedule({
                if (activeAlerts.containsKey("${rule.name}-${rule.severity}")) {
                    val escalationNotification =
                        NotificationTask(
                            alert = alert,
                            channels = escalation.channels,
                            type = NotificationType.ESCALATED,
                        )
                    notificationQueue.offer(escalationNotification)
                }
            }, escalation.delayMinutes.toLong(), TimeUnit.MINUTES)
        }
    }

    /**
     * Process notification queue
     */
    private fun processNotifications() {
        while (isRunning.get() && !Thread.currentThread().isInterrupted) {
            try {
                val notification = notificationQueue.poll(5, TimeUnit.SECONDS)
                if (notification != null) {
                    sendNotification(notification)
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                logger.error("Error processing notification", e)
            }
        }
    }

    /**
     * Send notification to configured channels
     */
    private fun sendNotification(notification: NotificationTask) {
        notification.channels.forEach { channel ->
            try {
                when (channel.type) {
                    NotificationChannelType.EMAIL -> sendEmailNotification(notification, channel)
                    NotificationChannelType.SLACK -> sendSlackNotification(notification, channel)
                    NotificationChannelType.WEBHOOK -> sendWebhookNotification(notification, channel)
                    NotificationChannelType.SMS -> sendSmsNotification(notification, channel)
                    NotificationChannelType.PAGERDUTY -> sendPagerDutyNotification(notification, channel)
                }
                totalNotificationsSent.incrementAndGet()
            } catch (e: Exception) {
                logger.error("Failed to send notification via ${channel.type}", e)
            }
        }
    }

    /**
     * Send email notification
     */
    private fun sendEmailNotification(
        notification: NotificationTask,
        channel: NotificationChannel,
    ) {
        // Email notification implementation would go here
        logger.info("Sending email notification: ${notification.alert.rule.name}")
    }

    /**
     * Send Slack notification
     */
    private fun sendSlackNotification(
        notification: NotificationTask,
        channel: NotificationChannel,
    ) {
        // Slack notification implementation would go here
        logger.info("Sending Slack notification: ${notification.alert.rule.name}")
    }

    /**
     * Send webhook notification
     */
    private fun sendWebhookNotification(
        notification: NotificationTask,
        channel: NotificationChannel,
    ) {
        // Webhook notification implementation would go here
        logger.info("Sending webhook notification: ${notification.alert.rule.name}")
    }

    /**
     * Send SMS notification
     */
    private fun sendSmsNotification(
        notification: NotificationTask,
        channel: NotificationChannel,
    ) {
        // SMS notification implementation would go here
        logger.info("Sending SMS notification: ${notification.alert.rule.name}")
    }

    /**
     * Send PagerDuty notification
     */
    private fun sendPagerDutyNotification(
        notification: NotificationTask,
        channel: NotificationChannel,
    ) {
        // PagerDuty notification implementation would go here
        logger.info("Sending PagerDuty notification: ${notification.alert.rule.name}")
    }

    /**
     * Cleanup old alerts and history
     */
    private fun cleanupAlerts() {
        val cutoffTime = System.currentTimeMillis() - config.alertHistoryRetention.toMillis()

        // Remove old alerts from history
        val iterator = alertHistory.iterator()
        while (iterator.hasNext()) {
            val alert = iterator.next()
            if (alert.triggeredAt.toEpochMilli() < cutoffTime) {
                iterator.remove()
            }
        }

        // Remove expired suppressions
        suppressedAlerts.entries.removeIf { it.value < System.currentTimeMillis() }
    }

    /**
     * Suppress alert for specified duration
     */
    fun suppressAlert(
        ruleName: String,
        severity: AlertSeverity,
        durationMinutes: Int,
    ) {
        val alertKey = "$ruleName-$severity"
        val suppressUntil = System.currentTimeMillis() + (durationMinutes * 60 * 1000)
        suppressedAlerts[alertKey] = suppressUntil
        logger.info("Alert suppressed: $alertKey for $durationMinutes minutes")
    }

    /**
     * Get current active alerts
     */
    fun getActiveAlerts(): List<AlertInstance> {
        return activeAlerts.values.toList()
    }

    /**
     * Get alert statistics
     */
    fun getStatistics(): AlertStatistics {
        return AlertStatistics(
            totalAlertsGenerated = totalAlertsGenerated.get(),
            activeAlertsCount = activeAlerts.size,
            totalNotificationsSent = totalNotificationsSent.get(),
            alertsSupressed = alertsSupressed.get(),
            alertHistorySize = alertHistory.size,
            suppressedAlertsCount = suppressedAlerts.size,
        )
    }

    // Metric calculation methods
    private fun calculateErrorRate(): Double {
        return metricsCollector.getErrorRate() ?: 0.0
    }

    private fun calculateLatencyP95(): Double {
        return metricsCollector.getLatencyPercentile(95.0) ?: 0.0
    }

    private fun calculateLatencyP99(): Double {
        return metricsCollector.getLatencyPercentile(99.0) ?: 0.0
    }

    private fun calculateMemoryUsage(): Double {
        return metricsCollector.getMemoryUsagePercent() ?: 0.0
    }

    private fun calculateCpuUsage(): Double {
        return metricsCollector.getCpuUsagePercent() ?: 0.0
    }

    private fun calculateConnectionFailures(): Double {
        return metricsCollector.getConnectionFailureRate() ?: 0.0
    }

    private fun isCircuitBreakerOpen(): Boolean {
        return metricsCollector.isCircuitBreakerOpen() ?: false
    }

    private fun isBackpressureActive(): Boolean {
        return metricsCollector.isBackpressureActive() ?: false
    }

    private fun calculateKafkaLag(): Double {
        return metricsCollector.getKafkaLag() ?: 0.0
    }

    private fun calculateDiskUsage(): Double {
        return metricsCollector.getDiskUsagePercent() ?: 0.0
    }

    private fun generateAlertId(): String {
        return "alert-${System.currentTimeMillis()}-${(1000..9999).random()}"
    }

    companion object {
        private val threadCounter = AtomicLong(0)
    }
}

/**
 * Alert configuration
 */
data class AlertConfig(
    val enabled: Boolean = true,
    val evaluationInterval: java.time.Duration = java.time.Duration.ofSeconds(30),
    val alertHistoryRetention: java.time.Duration = java.time.Duration.ofDays(7),
    val threadPoolSize: Int = 4,
    val alertRules: List<AlertRule> = emptyList(),
)

/**
 * Alert rule definition
 */
data class AlertRule(
    val name: String,
    val description: String,
    val metric: AlertMetric,
    val condition: AlertCondition,
    val threshold: Double,
    val severity: AlertSeverity,
    val notificationChannels: List<NotificationChannel>,
    val escalationLevels: List<EscalationLevel> = emptyList(),
    val notifyOnResolve: Boolean = true,
    val enabled: Boolean = true,
)

/**
 * Alert metrics
 */
enum class AlertMetric {
    ERROR_RATE,
    LATENCY_P95,
    LATENCY_P99,
    MEMORY_USAGE,
    CPU_USAGE,
    CONNECTION_FAILURES,
    CIRCUIT_BREAKER_OPEN,
    BACKPRESSURE_ACTIVE,
    KAFKA_LAG,
    DISK_USAGE,
}

/**
 * Alert conditions
 */
enum class AlertCondition {
    GREATER_THAN,
    LESS_THAN,
    EQUALS,
}

/**
 * Alert severity levels
 */
enum class AlertSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO,
}

/**
 * Alert status
 */
enum class AlertStatus {
    FIRING,
    RESOLVED,
    SUPPRESSED,
}

/**
 * Alert instance
 */
data class AlertInstance(
    val id: String,
    val rule: AlertRule,
    var currentValue: Double,
    val triggeredAt: Instant,
    var status: AlertStatus,
    var resolvedAt: Instant? = null,
    var lastUpdated: Instant = Instant.now(),
) {
    fun updateValue(newValue: Double) {
        currentValue = newValue
        lastUpdated = Instant.now()
    }

    fun resolve() {
        status = AlertStatus.RESOLVED
        resolvedAt = Instant.now()
        lastUpdated = Instant.now()
    }
}

/**
 * Notification channel
 */
data class NotificationChannel(
    val type: NotificationChannelType,
    val configuration: Map<String, String>,
)

/**
 * Notification channel types
 */
enum class NotificationChannelType {
    EMAIL,
    SLACK,
    WEBHOOK,
    SMS,
    PAGERDUTY,
}

/**
 * Escalation level
 */
data class EscalationLevel(
    val delayMinutes: Int,
    val channels: List<NotificationChannel>,
)

/**
 * Notification task
 */
data class NotificationTask(
    val alert: AlertInstance,
    val channels: List<NotificationChannel>,
    val type: NotificationType,
)

/**
 * Notification types
 */
enum class NotificationType {
    TRIGGERED,
    ESCALATED,
    RESOLVED,
}

/**
 * Alert statistics
 */
data class AlertStatistics(
    val totalAlertsGenerated: Long,
    val activeAlertsCount: Int,
    val totalNotificationsSent: Long,
    val alertsSupressed: Long,
    val alertHistorySize: Int,
    val suppressedAlertsCount: Int,
)
