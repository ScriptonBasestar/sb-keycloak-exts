package org.scriptonbasestar.kcexts.selfservice.model

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Common API Response wrapper
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: ErrorDetail? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorDetail(
    val code: String,
    val message: String,
    val field: String? = null,
    val details: Map<String, Any>? = null,
)

/**
 * Helper functions for creating responses
 */
object ApiResponses {
    fun <T> success(
        data: T,
        message: String? = null,
    ): ApiResponse<T> = ApiResponse(success = true, data = data, message = message)

    fun <T> success(message: String): ApiResponse<T> = ApiResponse(success = true, message = message)

    fun <T> error(
        code: String,
        message: String,
        field: String? = null,
    ): ApiResponse<T> =
        ApiResponse(
            success = false,
            error = ErrorDetail(code = code, message = message, field = field),
        )

    fun <T> error(error: ErrorDetail): ApiResponse<T> = ApiResponse(success = false, error = error)
}
