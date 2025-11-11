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

package org.scriptonbasestar.kcexts.realm.hierarchy.inheritance

import org.jboss.logging.Logger
import org.keycloak.models.IdentityProviderModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.scriptonbasestar.kcexts.realm.hierarchy.storage.RealmHierarchyStorage

/**
 * Realm 설정 상속 관리자
 */
class InheritanceManager(
    private val session: KeycloakSession,
    private val storage: RealmHierarchyStorage,
) {
    companion object {
        private val logger = Logger.getLogger(InheritanceManager::class.java)
        private const val ATTR_INHERITED = "hierarchy.inherited"
        private const val ATTR_SOURCE_REALM = "hierarchy.source_realm"
    }

    /**
     * 부모 Realm의 설정을 상속
     */
    fun inheritFromParent(realm: RealmModel) {
        val parent = storage.getParentRealm(realm) ?: return
        val node = storage.getHierarchyNode(realm)

        logger.infof("Inheriting settings from parent realm: %s -> %s", parent.name, realm.name)

        if (node.inheritIdp) {
            inheritIdentityProviders(realm, parent)
        }

        if (node.inheritAuthFlow) {
            inheritAuthenticationFlows(realm, parent)
        }

        if (node.inheritRoles) {
            inheritRoles(realm, parent)
        }
    }

    /**
     * Identity Provider 상속
     */
    private fun inheritIdentityProviders(
        childRealm: RealmModel,
        parentRealm: RealmModel,
    ) {
        logger.debugf("Inheriting Identity Providers: %s -> %s", parentRealm.name, childRealm.name)

        parentRealm.identityProvidersStream
            .filter { isInheritable(it) }
            .forEach { parentIdp ->
                // 이미 존재하는지 확인
                val existingIdp = childRealm.getIdentityProviderByAlias(parentIdp.alias)

                if (existingIdp == null) {
                    // 새로운 IdP 추가
                    val childIdp = cloneIdentityProvider(parentIdp, parentRealm.name)
                    childRealm.addIdentityProvider(childIdp)
                    logger.infof("Inherited IdP: %s", parentIdp.alias)
                } else if (isInherited(existingIdp)) {
                    // 이미 상속된 IdP 업데이트
                    updateIdentityProvider(existingIdp, parentIdp, parentRealm.name)
                    childRealm.updateIdentityProvider(existingIdp)
                    logger.debugf("Updated inherited IdP: %s", parentIdp.alias)
                }
            }
    }

    /**
     * Authentication Flow 상속
     */
    private fun inheritAuthenticationFlows(
        childRealm: RealmModel,
        parentRealm: RealmModel,
    ) {
        logger.debugf("Inheriting Authentication Flows: %s -> %s", parentRealm.name, childRealm.name)

        // 현재는 로그만 출력 (실제 구현은 복잡함)
        // Authentication Flow 복제는 많은 관련 엔티티(Execution, Authenticator Config 등)를 함께 처리해야 함
        logger.debugf("Authentication Flow inheritance not yet implemented for: %s", childRealm.name)
    }

    /**
     * Role 상속
     */
    private fun inheritRoles(
        childRealm: RealmModel,
        parentRealm: RealmModel,
    ) {
        logger.debugf("Inheriting Roles: %s -> %s", parentRealm.name, childRealm.name)

        parentRealm.rolesStream
            .forEach { parentRole ->
                val existingRole = childRealm.getRole(parentRole.name)

                if (existingRole == null) {
                    val childRole = childRealm.addRole(parentRole.name)
                    childRole.description = parentRole.description
                    childRole.setAttribute(ATTR_INHERITED, listOf("true"))
                    childRole.setAttribute(ATTR_SOURCE_REALM, listOf(parentRealm.name))
                    logger.infof("Inherited Role: %s", parentRole.name)
                }
            }
    }

    /**
     * Identity Provider 복제
     */
    private fun cloneIdentityProvider(
        source: IdentityProviderModel,
        sourceRealmName: String,
    ): IdentityProviderModel {
        val cloned = IdentityProviderModel(source)
        cloned.config[ATTR_INHERITED] = "true"
        cloned.config[ATTR_SOURCE_REALM] = sourceRealmName
        return cloned
    }

    /**
     * Identity Provider 업데이트
     */
    private fun updateIdentityProvider(
        target: IdentityProviderModel,
        source: IdentityProviderModel,
        sourceRealmName: String,
    ) {
        // 기본 설정 복사
        target.providerId = source.providerId
        target.isEnabled = source.isEnabled
        target.displayName = source.displayName

        // Config 복사 (상속 메타데이터 유지)
        source.config.forEach { (key, value) ->
            if (key !in listOf(ATTR_INHERITED, ATTR_SOURCE_REALM)) {
                target.config[key] = value
            }
        }
    }

    /**
     * 상속 가능한 Identity Provider인지 확인
     */
    private fun isInheritable(idp: IdentityProviderModel): Boolean = idp.config[ATTR_INHERITED] != "false"

    /**
     * 상속된 Identity Provider인지 확인
     */
    private fun isInherited(idp: IdentityProviderModel): Boolean = idp.config[ATTR_INHERITED] == "true"

    /**
     * 전체 계층 구조에서 설정 동기화
     */
    fun synchronizeHierarchy(rootRealm: RealmModel) {
        logger.infof("Synchronizing hierarchy from root: %s", rootRealm.name)

        val children = storage.getChildRealms(rootRealm)
        children.forEach { child ->
            inheritFromParent(child)
            // 재귀적으로 하위 계층 동기화
            synchronizeHierarchy(child)
        }
    }
}
