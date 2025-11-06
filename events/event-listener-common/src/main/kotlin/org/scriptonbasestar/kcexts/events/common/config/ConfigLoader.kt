package org.scriptonbasestar.kcexts.events.common.config

import org.keycloak.Config
import org.keycloak.models.KeycloakSession

/**
 * Unified configuration loader supporting multiple sources with priority:
 * 1. Realm Attributes (runtime, per-realm)
 * 2. Config.Scope (init-time, global)
 * 3. System Properties
 * 4. Default values
 *
 * This allows both static global configuration and dynamic per-realm configuration.
 */
class ConfigLoader(
    private val session: KeycloakSession? = null,
    private val configScope: Config.Scope? = null,
    private val prefix: String,
) {
    /**
     * Get a configuration value with fallback chain
     */
    fun getString(
        key: String,
        defaultValue: String? = null,
    ): String? {
        // 1. Try realm attributes (runtime, per-realm)
        session
            ?.context
            ?.realm
            ?.attributes
            ?.get("$prefix.$key")
            ?.let { return it }

        // 2. Try Config.Scope (init-time, global)
        configScope?.get(key)?.let { return it }

        // 3. Try system properties
        System.getProperty("$prefix.$key")?.let { return it }

        // 4. Return default
        return defaultValue
    }

    /**
     * Get a required string value, throws if not found
     */
    fun getRequiredString(key: String): String =
        getString(key) ?: throw IllegalStateException("Required configuration key not found: $prefix.$key")

    /**
     * Get an integer value
     */
    fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = getString(key)?.toIntOrNull() ?: defaultValue

    /**
     * Get a long value
     */
    fun getLong(
        key: String,
        defaultValue: Long,
    ): Long = getString(key)?.toLongOrNull() ?: defaultValue

    /**
     * Get a boolean value
     */
    fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = getString(key)?.toBoolean() ?: defaultValue

    /**
     * Get a comma-separated list of strings
     */
    fun getStringList(
        key: String,
        defaultValue: List<String> = emptyList(),
    ): List<String> {
        val value = getString(key)
        return if (value.isNullOrBlank()) {
            defaultValue
        } else {
            value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    /**
     * Get a set of strings from comma-separated value
     */
    fun getStringSet(
        key: String,
        defaultValue: Set<String> = emptySet(),
    ): Set<String> = getStringList(key, defaultValue.toList()).toSet()

    companion object {
        /**
         * Create a config loader for init-time (global) configuration
         */
        fun forInitTime(
            configScope: Config.Scope,
            prefix: String,
        ): ConfigLoader = ConfigLoader(session = null, configScope = configScope, prefix = prefix)

        /**
         * Create a config loader for runtime (per-session) configuration
         */
        fun forRuntime(
            session: KeycloakSession,
            configScope: Config.Scope?,
            prefix: String,
        ): ConfigLoader = ConfigLoader(session = session, configScope = configScope, prefix = prefix)
    }
}
