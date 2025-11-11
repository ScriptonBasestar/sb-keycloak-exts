package org.scriptonbasestar.kcexts.selfservice.registration

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * User Registration Request
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RegistrationRequest(
    val username: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val password: String,
    val confirmPassword: String?,
    val consents: Map<String, Boolean>? = null,
    val attributes: Map<String, String>? = null,
)

/**
 * Registration Response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RegistrationResponse(
    val userId: String,
    val status: RegistrationStatus,
    val message: String,
    val verificationRequired: Boolean = true,
)

/**
 * Registration Status
 */
enum class RegistrationStatus {
    PENDING_VERIFICATION,
    VERIFIED,
    FAILED,
}

/**
 * Email Verification Response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class VerificationResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Resend Verification Request
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResendVerificationRequest(
    val email: String,
)
