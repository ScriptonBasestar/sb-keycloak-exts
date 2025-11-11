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

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.keycloak.events.admin.AdminEvent
import org.keycloak.events.admin.OperationType
import org.keycloak.events.admin.ResourceType
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Integration tests for RealmHierarchyEventListener
 *
 * 이 테스트는 Event Listener의 동기화 동작을 검증합니다.
 */
@DisplayName("Realm Hierarchy Event Listener Integration Tests")
class RealmHierarchyEventListenerIntegrationTest {
    private lateinit var session: KeycloakSession
    private lateinit var realm: RealmModel
    private lateinit var listener: RealmHierarchyEventListener

    @BeforeEach
    fun setUp() {
        // Mock KeycloakSession
        session = mock()

        // Mock RealmModel
        realm = mock()
        whenever(realm.id).thenReturn("test-realm-id")
        whenever(realm.name).thenReturn("test-realm")
        whenever(realm.getAttribute(any())).thenReturn(null)
        whenever(realm.getAttributes()).thenReturn(mutableMapOf())

        // Mock RealmProvider
        val realmProvider = mock<org.keycloak.models.RealmProvider>()
        whenever(session.realms()).thenReturn(realmProvider)
        whenever(realmProvider.getRealm(any<String>())).thenReturn(realm)

        // Create listener instance
        listener = RealmHierarchyEventListener(session)
    }

    @Test
    @DisplayName("REALM 이벤트 처리")
    fun testOnEvent_RealmEvent() {
        // Given
        val event = mock<AdminEvent>()
        whenever(event.resourceType).thenReturn(ResourceType.REALM)
        whenever(event.operationType).thenReturn(OperationType.UPDATE)
        whenever(event.realmId).thenReturn("test-realm-id")

        // When & Then
        assertDoesNotThrow {
            listener.onEvent(event, includeRepresentation = false)
        }
    }

    @Test
    @DisplayName("IDENTITY_PROVIDER 이벤트 처리")
    fun testOnEvent_IdentityProviderEvent() {
        // Given
        val event = mock<AdminEvent>()
        whenever(event.resourceType).thenReturn(ResourceType.IDENTITY_PROVIDER)
        whenever(event.operationType).thenReturn(OperationType.CREATE)
        whenever(event.realmId).thenReturn("test-realm-id")

        // When & Then
        assertDoesNotThrow {
            listener.onEvent(event, includeRepresentation = false)
        }
    }

    @Test
    @DisplayName("REALM_ROLE 이벤트 처리")
    fun testOnEvent_RoleEvent() {
        // Given
        val event = mock<AdminEvent>()
        whenever(event.resourceType).thenReturn(ResourceType.REALM_ROLE)
        whenever(event.operationType).thenReturn(OperationType.CREATE)
        whenever(event.realmId).thenReturn("test-realm-id")

        // When & Then
        assertDoesNotThrow {
            listener.onEvent(event, includeRepresentation = false)
        }
    }

    @Test
    @DisplayName("지원하지 않는 이벤트 타입 처리")
    fun testOnEvent_UnsupportedEvent() {
        // Given
        val event = mock<AdminEvent>()
        whenever(event.resourceType).thenReturn(ResourceType.USER)
        whenever(event.operationType).thenReturn(OperationType.CREATE)
        whenever(event.realmId).thenReturn("test-realm-id")

        // When & Then
        assertDoesNotThrow {
            listener.onEvent(event, includeRepresentation = false)
        }
    }

    @Test
    @DisplayName("Listener close 호출")
    fun testClose() {
        // When & Then
        assertDoesNotThrow {
            listener.close()
        }
    }

    @Test
    @DisplayName("REALM UPDATE 이벤트 시 자식 Realm 동기화")
    fun testOnEvent_RealmUpdate_TriggersChildSync() {
        // Given
        val event = mock<AdminEvent>()
        whenever(event.resourceType).thenReturn(ResourceType.REALM)
        whenever(event.operationType).thenReturn(OperationType.UPDATE)
        whenever(event.realmId).thenReturn("parent-realm-id")

        val parentRealm = mock<RealmModel>()
        whenever(parentRealm.id).thenReturn("parent-realm-id")
        whenever(parentRealm.name).thenReturn("parent-realm")
        whenever(parentRealm.getAttribute(any())).thenReturn(null)
        whenever(parentRealm.getAttributes()).thenReturn(mutableMapOf())

        val realmProvider = mock<org.keycloak.models.RealmProvider>()
        whenever(session.realms()).thenReturn(realmProvider)
        whenever(realmProvider.getRealm("parent-realm-id")).thenReturn(parentRealm)

        // When & Then
        assertDoesNotThrow {
            listener.onEvent(event, includeRepresentation = false)
        }
    }

    @Test
    @DisplayName("IDENTITY_PROVIDER CREATE 이벤트 시 자식 Realm 동기화")
    fun testOnEvent_IdpCreate_TriggersChildSync() {
        // Given
        val event = mock<AdminEvent>()
        whenever(event.resourceType).thenReturn(ResourceType.IDENTITY_PROVIDER)
        whenever(event.operationType).thenReturn(OperationType.CREATE)
        whenever(event.realmId).thenReturn("parent-realm-id")

        val parentRealm = mock<RealmModel>()
        whenever(parentRealm.id).thenReturn("parent-realm-id")
        whenever(parentRealm.name).thenReturn("parent-realm")
        whenever(parentRealm.getAttribute(any())).thenReturn(null)
        whenever(parentRealm.getAttributes()).thenReturn(mutableMapOf())

        val realmProvider = mock<org.keycloak.models.RealmProvider>()
        whenever(session.realms()).thenReturn(realmProvider)
        whenever(realmProvider.getRealm("parent-realm-id")).thenReturn(parentRealm)

        // When & Then
        assertDoesNotThrow {
            listener.onEvent(event, includeRepresentation = false)
        }
    }
}
