package org.scriptonbasestar.kcexts.metering.storage

import org.influxdb.InfluxDB
import org.influxdb.InfluxDBFactory
import org.influxdb.dto.BatchPoints
import org.influxdb.dto.Point
import org.scriptonbasestar.kcexts.metering.config.InfluxDBConfig
import org.scriptonbasestar.kcexts.metering.model.UserEventMetric
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * InfluxDB storage backend for time-series metrics
 *
 * InfluxDB is optimized for time-series data with:
 * - Fast writes
 * - Efficient compression
 * - Flexible retention policies
 * - Native downsampling
 */
class InfluxDBStorage(
    private val config: InfluxDBConfig,
) : StorageBackend {
    private val logger = LoggerFactory.getLogger(InfluxDBStorage::class.java)
    private val influxDB: InfluxDB

    init {
        influxDB =
            if (config.username != null && config.password != null) {
                InfluxDBFactory.connect(config.url, config.username, config.password)
            } else {
                InfluxDBFactory.connect(config.url)
            }

        influxDB.setDatabase(config.database)
        influxDB.setRetentionPolicy(config.retentionPolicy)
        influxDB.enableBatch(config.batchSize, config.flushIntervalMs, TimeUnit.MILLISECONDS)

        // Create database if it doesn't exist
        createDatabaseIfNeeded()

        logger.info("InfluxDB storage initialized: ${config.url}, database: ${config.database}")
    }

    override fun storeUserEvent(metric: UserEventMetric) {
        try {
            val point = createPointFromMetric(metric)
            influxDB.write(point)
        } catch (e: Exception) {
            logger.error("Failed to store user event metric", e)
            throw e
        }
    }

    override fun batchStoreUserEvents(metrics: List<UserEventMetric>) {
        if (metrics.isEmpty()) {
            return
        }

        try {
            val batchPoints =
                BatchPoints
                    .database(config.database)
                    .retentionPolicy(config.retentionPolicy)
                    .consistency(InfluxDB.ConsistencyLevel.ONE)
                    .build()

            for (metric in metrics) {
                batchPoints.point(createPointFromMetric(metric))
            }

            influxDB.write(batchPoints)
            logger.debug("Stored ${metrics.size} user event metrics in batch")
        } catch (e: Exception) {
            logger.error("Failed to batch store user event metrics", e)
            throw e
        }
    }

    override fun flush() {
        try {
            influxDB.flush()
        } catch (e: Exception) {
            logger.error("Failed to flush InfluxDB", e)
        }
    }

    override fun close() {
        try {
            flush()
            influxDB.close()
            logger.info("InfluxDB storage closed")
        } catch (e: Exception) {
            logger.error("Error closing InfluxDB connection", e)
        }
    }

    private fun createPointFromMetric(metric: UserEventMetric): Point {
        val pointBuilder =
            Point
                .measurement("keycloak_user_events")
                .time(metric.timestamp.toEpochMilli(), TimeUnit.MILLISECONDS)
                .tag("event_type", metric.eventType)
                .tag("realm_id", metric.realmId)
                .tag("success", metric.success.toString())

        // Optional tags
        metric.clientId?.let { pointBuilder.tag("client_id", it) }
        metric.userId?.let { pointBuilder.tag("user_id", it) }
        metric.ipAddress?.let { pointBuilder.tag("ip_address", it) }

        // Fields
        pointBuilder.addField("count", 1L)
        metric.sessionId?.let { pointBuilder.addField("session_id", it) }

        // Additional details as fields
        metric.details.forEach { (key, value) ->
            pointBuilder.addField("detail_$key", value)
        }

        return pointBuilder.build()
    }

    private fun createDatabaseIfNeeded() {
        try {
            val databases = influxDB.describeDatabases()
            if (!databases.contains(config.database)) {
                logger.info("Creating InfluxDB database: ${config.database}")
                influxDB.query(org.influxdb.dto.Query("CREATE DATABASE ${config.database}"))

                // Create retention policies
                createRetentionPolicies()
            }
        } catch (e: Exception) {
            logger.warn("Could not create database (may already exist or insufficient permissions)", e)
        }
    }

    private fun createRetentionPolicies() {
        try {
            // Default retention policy (infinite)
            influxDB.query(
                org.influxdb.dto.Query(
                    "CREATE RETENTION POLICY \"autogen\" ON \"${config.database}\" DURATION INF REPLICATION 1 DEFAULT",
                ),
            )

            // 30-day retention policy for high-resolution data
            influxDB.query(
                org.influxdb.dto.Query(
                    "CREATE RETENTION POLICY \"thirty_days\" ON \"${config.database}\" DURATION 30d REPLICATION 1",
                ),
            )

            // 90-day retention policy for medium-resolution data
            influxDB.query(
                org.influxdb.dto.Query(
                    "CREATE RETENTION POLICY \"ninety_days\" ON \"${config.database}\" DURATION 90d REPLICATION 1",
                ),
            )

            logger.info("Retention policies created")
        } catch (e: Exception) {
            logger.warn("Could not create retention policies (may already exist)", e)
        }
    }
}
