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

package org.scriptonbasestar.kcexts.realm.hierarchy.api.dto

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Realm 계층 구조 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class HierarchyResponse(
    val realmId: String,
    val realmName: String,
    val parentRealmId: String? = null,
    val parentRealmName: String? = null,
    val tier: String,
    val depth: Int,
    val path: String,
    val inheritIdp: Boolean,
    val inheritAuthFlow: Boolean,
    val inheritRoles: Boolean,
    val children: List<HierarchyResponse>? = null,
)

/**
 * 부모 Realm 설정 요청 DTO
 */
data class SetParentRequest(
    val parentRealmId: String?,
    val inheritIdp: Boolean = false,
    val inheritAuthFlow: Boolean = false,
    val inheritRoles: Boolean = false,
)

/**
 * 상속 설정 업데이트 요청 DTO
 */
data class UpdateInheritanceRequest(
    val inheritIdp: Boolean,
    val inheritAuthFlow: Boolean,
    val inheritRoles: Boolean,
)

/**
 * 계층 동기화 응답 DTO
 */
data class SynchronizeResponse(
    val message: String,
    val affectedRealms: List<String>,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * 에러 응답 DTO
 */
data class ErrorResponse(
    val error: String,
    val message: String,
    val details: String? = null,
)
