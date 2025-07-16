package org.scriptonbasestar.kcexts.events.kafka.model

import com.fasterxml.jackson.annotation.JsonProperty

data class KeycloakAdminEvent(
    @JsonProperty("id")
    val id: String,
    @JsonProperty("time")
    val time: Long,
    @JsonProperty("operationType")
    val operationType: String,
    @JsonProperty("realmId")
    val realmId: String,
    @JsonProperty("authDetails")
    val authDetails: AuthDetails,
    @JsonProperty("resourcePath")
    val resourcePath: String?,
    @JsonProperty("representation")
    val representation: String?,
)

data class AuthDetails(
    @JsonProperty("realmId")
    val realmId: String,
    @JsonProperty("clientId")
    val clientId: String?,
    @JsonProperty("userId")
    val userId: String?,
    @JsonProperty("ipAddress")
    val ipAddress: String?,
)
