/*
 * Copyright 2025 ScriptonBasestar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scriptonbasestar.kcexts.ratelimit.metrics

import java.util.concurrent.atomic.AtomicLong

/**
 * Rate Limiting Metrics Collector
 *
 * Collects metrics for Prometheus export:
 * - keycloak_ratelimit_events_total{realm, event_type, result}
 * - keycloak_ratelimit_permits_available{realm, strategy, key}
 *
 * Thread-safe using AtomicLong for counters.
 */
object RateLimitMetrics {
    // Event counters: realm -> event_type -> result -> count
    private val eventCounters =
        mutableMapOf<String, MutableMap<String, MutableMap<String, AtomicLong>>>()

    // Available permits: realm -> strategy -> key -> permits
    private val availablePermits =
        mutableMapOf<String, MutableMap<String, MutableMap<String, AtomicLong>>>()

    /**
     * Record an event (allowed or denied)
     *
     * @param realm Keycloak realm ID
     * @param eventType Event type (LOGIN, UPDATE_PROFILE, etc.)
     * @param result "allowed" or "denied"
     */
    fun recordEvent(
        realm: String,
        eventType: String,
        result: String,
    ) {
        synchronized(eventCounters) {
            val realmCounters = eventCounters.getOrPut(realm) { mutableMapOf() }
            val typeCounters = realmCounters.getOrPut(eventType) { mutableMapOf() }
            val resultCounter = typeCounters.getOrPut(result) { AtomicLong(0) }

            resultCounter.incrementAndGet()
        }
    }

    /**
     * Update available permits for a key
     *
     * @param realm Keycloak realm ID
     * @param strategy Rate limiting strategy (PER_USER, PER_CLIENT, etc.)
     * @param key Unique identifier (userId, clientId, ipAddress, etc.)
     * @param permits Number of permits available
     */
    fun updateAvailablePermits(
        realm: String,
        strategy: String,
        key: String,
        permits: Long,
    ) {
        synchronized(availablePermits) {
            val realmPermits = availablePermits.getOrPut(realm) { mutableMapOf() }
            val strategyPermits = realmPermits.getOrPut(strategy) { mutableMapOf() }
            val keyPermits = strategyPermits.getOrPut(key) { AtomicLong(0) }

            keyPermits.set(permits)
        }
    }

    /**
     * Get all event counters for Prometheus export
     *
     * @return Map of metric labels to counter values
     */
    fun getEventCounters(): Map<Map<String, String>, Long> {
        val result = mutableMapOf<Map<String, String>, Long>()

        synchronized(eventCounters) {
            eventCounters.forEach { (realm, realmCounters) ->
                realmCounters.forEach { (eventType, typeCounters) ->
                    typeCounters.forEach { (resultValue, counter) ->
                        val labels =
                            mapOf(
                                "realm" to realm,
                                "event_type" to eventType,
                                "result" to resultValue,
                            )
                        result[labels] = counter.get()
                    }
                }
            }
        }

        return result
    }

    /**
     * Get all available permits for Prometheus export
     *
     * @return Map of metric labels to permit values
     */
    fun getAvailablePermits(): Map<Map<String, String>, Long> {
        val result = mutableMapOf<Map<String, String>, Long>()

        synchronized(availablePermits) {
            availablePermits.forEach { (realm, realmPermits) ->
                realmPermits.forEach { (strategy, strategyPermits) ->
                    strategyPermits.forEach { (key, permits) ->
                        val labels =
                            mapOf(
                                "realm" to realm,
                                "strategy" to strategy,
                                "key" to key,
                            )
                        result[labels] = permits.get()
                    }
                }
            }
        }

        return result
    }

    /**
     * Get total denied events across all realms and event types
     *
     * @return Total denied count
     */
    fun getTotalDeniedEvents(): Long {
        var total = 0L

        synchronized(eventCounters) {
            eventCounters.values.forEach { realmCounters ->
                realmCounters.values.forEach { typeCounters ->
                    typeCounters["denied"]?.let { counter ->
                        total += counter.get()
                    }
                }
            }
        }

        return total
    }

    /**
     * Get total allowed events across all realms and event types
     *
     * @return Total allowed count
     */
    fun getTotalAllowedEvents(): Long {
        var total = 0L

        synchronized(eventCounters) {
            eventCounters.values.forEach { realmCounters ->
                realmCounters.values.forEach { typeCounters ->
                    typeCounters["allowed"]?.let { counter ->
                        total += counter.get()
                    }
                }
            }
        }

        return total
    }

    /**
     * Reset all metrics (for testing)
     */
    fun reset() {
        synchronized(eventCounters) {
            eventCounters.clear()
        }
        synchronized(availablePermits) {
            availablePermits.clear()
        }
    }

    /**
     * Get denial rate for specific event type in realm
     *
     * @param realm Keycloak realm ID
     * @param eventType Event type
     * @return Denial rate (0.0 to 1.0) or null if no events
     */
    fun getDenialRate(
        realm: String,
        eventType: String,
    ): Double? {
        synchronized(eventCounters) {
            val typeCounters =
                eventCounters[realm]?.get(eventType)
                    ?: return null

            val denied = typeCounters["denied"]?.get() ?: 0L
            val allowed = typeCounters["allowed"]?.get() ?: 0L
            val total = denied + allowed

            return if (total > 0) {
                denied.toDouble() / total.toDouble()
            } else {
                null
            }
        }
    }
}
