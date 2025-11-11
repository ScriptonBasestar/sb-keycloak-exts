package org.scriptonbasestar.kcexts.selfservice.profile

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

/**
 * User Profile Response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class UserProfile(
    val id: String,
    val username: String,
    val email: String?,
    val emailVerified: Boolean,
    val firstName: String?,
    val lastName: String?,
    val attributes: Map<String, List<String>>?,
    val avatarUrl: String?,
    val createdAt: Instant?,
)

/**
 * Update Profile Request
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class UpdateProfileRequest(
    val firstName: String?,
    val lastName: String?,
    val email: String?,
    val attributes: Map<String, String>?,
)
