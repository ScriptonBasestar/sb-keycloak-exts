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

package org.scriptonbasestar.kcexts.realm.hierarchy

import org.jboss.logging.Logger
import org.keycloak.events.Event
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.admin.AdminEvent
import org.keycloak.events.admin.OperationType
import org.keycloak.events.admin.ResourceType
import org.keycloak.models.KeycloakSession
import org.scriptonbasestar.kcexts.realm.hierarchy.inheritance.InheritanceManager
import org.scriptonbasestar.kcexts.realm.hierarchy.storage.RealmHierarchyStorage

/**
 * Realm 계층 구조 이벤트 리스너
 * Realm 변경 사항을 감지하여 계층 구조 동기화
 */
class RealmHierarchyEventListener(
    private val session: KeycloakSession,
) : EventListenerProvider {
    private val storage = RealmHierarchyStorage(session)
    private val inheritanceManager = InheritanceManager(session, storage)

    companion object {
        private val logger = Logger.getLogger(RealmHierarchyEventListener::class.java)
    }

    override fun onEvent(event: Event) {
        // User 이벤트는 처리하지 않음
    }

    override fun onEvent(
        event: AdminEvent,
        includeRepresentation: Boolean,
    ) {
        try {
            when (event.resourceType) {
                ResourceType.REALM -> handleRealmEvent(event)
                ResourceType.IDENTITY_PROVIDER -> handleIdentityProviderEvent(event)
                // ResourceType.AUTHENTICATION_FLOW -> handleAuthenticationFlowEvent(event)
                ResourceType.REALM_ROLE -> handleRoleEvent(event)
                else -> {}
            }
        } catch (e: Exception) {
            logger.error("Error handling admin event", e)
        }
    }

    /**
     * Realm 이벤트 처리
     */
    private fun handleRealmEvent(event: AdminEvent) {
        when (event.operationType) {
            OperationType.CREATE -> {
                logger.infof("Realm created: %s", event.resourcePath)
                // 새로운 Realm 생성 시 부모 Realm 설정 확인
            }

            OperationType.UPDATE -> {
                logger.debugf("Realm updated: %s", event.resourcePath)
                val realm = session.realms().getRealm(event.realmId) ?: return

                // 계층 구조 속성이 변경되었는지 확인
                val node = storage.getHierarchyNode(realm)
                if (node.hasParent() && node.inheritsAnySettings()) {
                    inheritanceManager.inheritFromParent(realm)
                }
            }

            OperationType.DELETE -> {
                logger.infof("Realm deleted: %s", event.resourcePath)
            }

            else -> {}
        }
    }

    /**
     * Identity Provider 이벤트 처리
     */
    private fun handleIdentityProviderEvent(event: AdminEvent) {
        when (event.operationType) {
            OperationType.CREATE, OperationType.UPDATE -> {
                logger.debugf("Identity Provider changed: %s", event.resourcePath)
                val realm = session.realms().getRealm(event.realmId) ?: return

                // 하위 Realm에 변경사항 전파
                propagateToChildren(realm)
            }

            else -> {}
        }
    }

    /**
     * Authentication Flow 이벤트 처리
     */
    private fun handleAuthenticationFlowEvent(event: AdminEvent) {
        when (event.operationType) {
            OperationType.CREATE, OperationType.UPDATE -> {
                logger.debugf("Authentication Flow changed: %s", event.resourcePath)
                val realm = session.realms().getRealm(event.realmId) ?: return

                // 하위 Realm에 변경사항 전파
                propagateToChildren(realm)
            }

            else -> {}
        }
    }

    /**
     * Role 이벤트 처리
     */
    private fun handleRoleEvent(event: AdminEvent) {
        when (event.operationType) {
            OperationType.CREATE, OperationType.UPDATE -> {
                logger.debugf("Role changed: %s", event.resourcePath)
                val realm = session.realms().getRealm(event.realmId) ?: return

                // 하위 Realm에 변경사항 전파
                propagateToChildren(realm)
            }

            else -> {}
        }
    }

    /**
     * 변경사항을 하위 Realm에 전파
     */
    private fun propagateToChildren(realm: org.keycloak.models.RealmModel) {
        val children = storage.getChildRealms(realm)
        if (children.isEmpty()) return

        logger.infof("Propagating changes to %d child realms", children.size)
        children.forEach { child ->
            val node = storage.getHierarchyNode(child)
            if (node.inheritsAnySettings()) {
                inheritanceManager.inheritFromParent(child)
            }
        }
    }

    override fun close() {
        // No resources to close
    }
}
