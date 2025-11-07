package org.scriptonbasestar.kcexts.events.mqtt

import org.scriptonbasestar.kcexts.events.common.model.EventMeta

/**
 * MQTT event message wrapper for batch processing.
 *
 * Wraps MQTT-specific publish parameters with event metadata for batch and retry operations.
 */
data class MqttEventMessage(
    val topic: String,
    val message: String,
    val qos: Int = 1,
    val retained: Boolean = false,
    val meta: EventMeta,
)
