package org.scriptonbasestar.kcexts.events.common.model

import com.fasterxml.jackson.annotation.JsonProperty

data class KeycloakEvent(
    @JsonProperty("id")
    val id: String,
    @JsonProperty("time")
    val time: Long,
    @JsonProperty("type")
    val type: String,
    @JsonProperty("realmId")
    val realmId: String,
    @JsonProperty("clientId")
    val clientId: String?,
    @JsonProperty("userId")
    val userId: String?,
    @JsonProperty("sessionId")
    val sessionId: String?,
    @JsonProperty("ipAddress")
    val ipAddress: String?,
    @JsonProperty("details")
    val details: Map<String, String>?,
)
