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

package org.scriptonbasestar.kcexts.realm.hierarchy.model

/**
 * Realm 계층 구조 노드
 *
 * @property realmId Realm ID
 * @property realmName Realm 이름
 * @property parentRealmId 부모 Realm ID (null이면 최상위)
 * @property tier 계층 등급 (enterprise, business, basic 등)
 * @property depth 계층 깊이 (0: 최상위, 1: 1단계 하위, ...)
 * @property path 계층 경로 (예: /enterprise-a/subsidiary-1/branch-x)
 * @property inheritIdp Identity Provider 상속 여부
 * @property inheritAuthFlow Authentication Flow 상속 여부
 * @property inheritRoles Role 상속 여부
 * @property createdAt 생성 시간
 * @property updatedAt 수정 시간
 */
data class RealmHierarchyNode(
    val realmId: String,
    val realmName: String,
    val parentRealmId: String? = null,
    val tier: String = "standard",
    val depth: Int = 0,
    val path: String = "/",
    val inheritIdp: Boolean = false,
    val inheritAuthFlow: Boolean = false,
    val inheritRoles: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /**
     * 최상위 Realm인지 확인
     */
    fun isRoot(): Boolean = parentRealmId == null

    /**
     * 자식 Realm인지 확인
     */
    fun hasParent(): Boolean = parentRealmId != null

    /**
     * 설정 상속 여부 확인
     */
    fun inheritsAnySettings(): Boolean = inheritIdp || inheritAuthFlow || inheritRoles

    companion object {
        const val ATTR_PARENT_REALM_ID = "hierarchy.parent_realm_id"
        const val ATTR_TIER = "hierarchy.tier"
        const val ATTR_DEPTH = "hierarchy.depth"
        const val ATTR_PATH = "hierarchy.path"
        const val ATTR_INHERIT_IDP = "hierarchy.inherit_idp"
        const val ATTR_INHERIT_AUTH_FLOW = "hierarchy.inherit_auth_flow"
        const val ATTR_INHERIT_ROLES = "hierarchy.inherit_roles"
    }
}
