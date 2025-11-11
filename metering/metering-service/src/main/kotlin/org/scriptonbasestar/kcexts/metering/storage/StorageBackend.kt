package org.scriptonbasestar.kcexts.metering.storage

import org.scriptonbasestar.kcexts.metering.model.UserEventMetric

/**
 * Storage backend interface for time-series metrics
 *
 * Implementations: InfluxDB, TimescaleDB
 */
interface StorageBackend : AutoCloseable {
    /**
     * Store user event metric
     */
    fun storeUserEvent(metric: UserEventMetric)

    /**
     * Batch store user events (more efficient)
     */
    fun batchStoreUserEvents(metrics: List<UserEventMetric>)

    /**
     * Flush any pending writes
     */
    fun flush()
}
