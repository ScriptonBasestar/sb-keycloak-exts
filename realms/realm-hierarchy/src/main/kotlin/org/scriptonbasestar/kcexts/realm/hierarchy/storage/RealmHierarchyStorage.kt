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

package org.scriptonbasestar.kcexts.realm.hierarchy.storage

import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.scriptonbasestar.kcexts.realm.hierarchy.model.RealmHierarchyNode

/**
 * Realm 계층 구조 저장소
 * Realm의 Attribute를 사용하여 계층 구조 메타데이터 저장
 */
class RealmHierarchyStorage(
    private val session: KeycloakSession,
) {
    /**
     * Realm의 계층 구조 정보 조회
     */
    fun getHierarchyNode(realm: RealmModel): RealmHierarchyNode {
        return RealmHierarchyNode(
            realmId = realm.id,
            realmName = realm.name,
            parentRealmId = realm.getAttribute(RealmHierarchyNode.ATTR_PARENT_REALM_ID),
            tier = realm.getAttribute(RealmHierarchyNode.ATTR_TIER) ?: "standard",
            depth = realm.getAttribute(RealmHierarchyNode.ATTR_DEPTH)?.toIntOrNull() ?: 0,
            path = realm.getAttribute(RealmHierarchyNode.ATTR_PATH) ?: "/${realm.name}",
            inheritIdp = realm.getAttribute(RealmHierarchyNode.ATTR_INHERIT_IDP)?.toBoolean() ?: false,
            inheritAuthFlow = realm.getAttribute(RealmHierarchyNode.ATTR_INHERIT_AUTH_FLOW)?.toBoolean() ?: false,
            inheritRoles = realm.getAttribute(RealmHierarchyNode.ATTR_INHERIT_ROLES)?.toBoolean() ?: false,
        )
    }

    /**
     * Realm의 계층 구조 정보 저장
     */
    fun saveHierarchyNode(
        realm: RealmModel,
        node: RealmHierarchyNode,
    ) {
        realm.setAttribute(RealmHierarchyNode.ATTR_PARENT_REALM_ID, node.parentRealmId ?: "")
        realm.setAttribute(RealmHierarchyNode.ATTR_TIER, node.tier)
        realm.setAttribute(RealmHierarchyNode.ATTR_DEPTH, node.depth.toString())
        realm.setAttribute(RealmHierarchyNode.ATTR_PATH, node.path)
        realm.setAttribute(RealmHierarchyNode.ATTR_INHERIT_IDP, node.inheritIdp.toString())
        realm.setAttribute(RealmHierarchyNode.ATTR_INHERIT_AUTH_FLOW, node.inheritAuthFlow.toString())
        realm.setAttribute(RealmHierarchyNode.ATTR_INHERIT_ROLES, node.inheritRoles.toString())
    }

    /**
     * 부모 Realm 조회
     */
    fun getParentRealm(realm: RealmModel): RealmModel? {
        val parentId = realm.getAttribute(RealmHierarchyNode.ATTR_PARENT_REALM_ID)
        return if (parentId.isNullOrBlank()) null else session.realms().getRealm(parentId)
    }

    /**
     * 자식 Realm 목록 조회
     */
    fun getChildRealms(realm: RealmModel): List<RealmModel> {
        return session.realms().realmsStream
            .filter { it.getAttribute(RealmHierarchyNode.ATTR_PARENT_REALM_ID) == realm.id }
            .toList()
    }

    /**
     * 계층 구조 경로 조회 (현재 Realm부터 최상위까지)
     */
    fun getHierarchyPath(realm: RealmModel): List<RealmHierarchyNode> {
        val path = mutableListOf<RealmHierarchyNode>()
        var current: RealmModel? = realm

        while (current != null) {
            path.add(getHierarchyNode(current))
            current = getParentRealm(current)

            // 순환 참조 방지 (최대 10단계)
            if (path.size > 10) {
                throw IllegalStateException("Circular reference detected in realm hierarchy")
            }
        }

        return path
    }

    /**
     * 부모 Realm 설정
     */
    fun setParentRealm(
        realm: RealmModel,
        parentRealm: RealmModel?,
        inheritSettings: Boolean = false,
    ) {
        // 순환 참조 검증
        if (parentRealm != null) {
            validateNoCircularReference(realm, parentRealm)
        }

        val node = getHierarchyNode(realm)
        val depth = if (parentRealm != null) {
            getHierarchyNode(parentRealm).depth + 1
        } else {
            0
        }

        val path = if (parentRealm != null) {
            "${getHierarchyNode(parentRealm).path}/${realm.name}"
        } else {
            "/${realm.name}"
        }

        val updatedNode =
            node.copy(
                parentRealmId = parentRealm?.id,
                depth = depth,
                path = path,
                inheritIdp = inheritSettings,
                inheritAuthFlow = inheritSettings,
                inheritRoles = inheritSettings,
                updatedAt = System.currentTimeMillis(),
            )

        saveHierarchyNode(realm, updatedNode)
    }

    /**
     * 순환 참조 검증
     */
    private fun validateNoCircularReference(
        realm: RealmModel,
        parentRealm: RealmModel,
    ) {
        var current: RealmModel? = parentRealm
        var depth = 0

        while (current != null) {
            if (current.id == realm.id) {
                throw IllegalArgumentException(
                    "Circular reference detected: ${realm.name} cannot be a parent of itself",
                )
            }

            current = getParentRealm(current)
            depth++

            if (depth > 10) {
                throw IllegalStateException("Realm hierarchy too deep (max: 10 levels)")
            }
        }
    }

    /**
     * 계층 구조 삭제 (Realm 자체는 삭제하지 않음)
     */
    fun removeHierarchy(realm: RealmModel) {
        realm.removeAttribute(RealmHierarchyNode.ATTR_PARENT_REALM_ID)
        realm.removeAttribute(RealmHierarchyNode.ATTR_TIER)
        realm.removeAttribute(RealmHierarchyNode.ATTR_DEPTH)
        realm.removeAttribute(RealmHierarchyNode.ATTR_PATH)
        realm.removeAttribute(RealmHierarchyNode.ATTR_INHERIT_IDP)
        realm.removeAttribute(RealmHierarchyNode.ATTR_INHERIT_AUTH_FLOW)
        realm.removeAttribute(RealmHierarchyNode.ATTR_INHERIT_ROLES)
    }
}
