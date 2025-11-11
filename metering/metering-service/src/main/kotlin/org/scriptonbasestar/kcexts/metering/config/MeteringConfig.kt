package org.scriptonbasestar.kcexts.metering.config

import com.typesafe.config.Config

/**
 * Metering service configuration
 *
 * Loaded from application.conf (HOCON format)
 */
data class MeteringConfig(
    val storageType: String,
    val kafka: KafkaConfig,
    val influxDB: InfluxDBConfig,
    val prometheusPort: Int,
) {
    companion object {
        fun fromConfig(config: Config): MeteringConfig =
            MeteringConfig(
                storageType = config.getString("metering.storage.type"),
                kafka = KafkaConfig.fromConfig(config.getConfig("metering.kafka")),
                influxDB = InfluxDBConfig.fromConfig(config.getConfig("metering.influxdb")),
                prometheusPort = config.getInt("metering.prometheus.port"),
            )
    }
}

data class KafkaConfig(
    val bootstrapServers: String,
    val eventTopic: String,
    val adminEventTopic: String,
    val groupId: String,
    val autoOffsetReset: String,
    val enableAutoCommit: Boolean,
    val maxPollRecords: Int,
) {
    companion object {
        fun fromConfig(config: Config): KafkaConfig =
            KafkaConfig(
                bootstrapServers = config.getString("bootstrap-servers"),
                eventTopic = config.getString("event-topic"),
                adminEventTopic = config.getString("admin-event-topic"),
                groupId = config.getString("group-id"),
                autoOffsetReset = config.getString("auto-offset-reset"),
                enableAutoCommit = config.getBoolean("enable-auto-commit"),
                maxPollRecords = config.getInt("max-poll-records"),
            )
    }
}

data class InfluxDBConfig(
    val url: String,
    val database: String,
    val username: String?,
    val password: String?,
    val retentionPolicy: String,
    val batchSize: Int,
    val flushIntervalMs: Int,
) {
    companion object {
        fun fromConfig(config: Config): InfluxDBConfig =
            InfluxDBConfig(
                url = config.getString("url"),
                database = config.getString("database"),
                username = if (config.hasPath("username")) config.getString("username") else null,
                password = if (config.hasPath("password")) config.getString("password") else null,
                retentionPolicy = config.getString("retention-policy"),
                batchSize = config.getInt("batch-size"),
                flushIntervalMs = config.getInt("flush-interval-ms"),
            )
    }
}
