package org.scriptonbasestar.kcexts.selfservice.password

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Password Policy Response
 *
 * Represents the realm's password policy requirements
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PasswordPolicy(
    val minLength: Int = 8,
    val minUpperCase: Int? = null,
    val minLowerCase: Int? = null,
    val minDigits: Int? = null,
    val minSpecialChars: Int? = null,
    val notUsername: Boolean = false,
    val notEmail: Boolean = false,
    val passwordHistory: Int? = null,
    val hashAlgorithm: String? = null,
    val hashIterations: Int? = null,
    val expiryDays: Int? = null,
    val requirements: List<String> = emptyList(),
)

/**
 * Change Password Request
 *
 * Request model for password change operation
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
    val confirmPassword: String,
)

/**
 * Password Validation Result
 *
 * Result of password policy validation
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PasswordValidationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

/**
 * Change Password Response
 *
 * Response for password change operation
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ChangePasswordResponse(
    val success: Boolean,
    val message: String,
    val nextPasswordChangeDate: String? = null,
)
