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

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.keycloak.models.IdentityProviderModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.RoleModel
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.scriptonbasestar.kcexts.realm.hierarchy.model.RealmHierarchyNode
import org.scriptonbasestar.kcexts.realm.hierarchy.storage.RealmHierarchyStorage
import java.util.stream.Stream

@DisplayName("InheritanceManager Unit Tests")
class InheritanceManagerTest {
    private lateinit var session: KeycloakSession
    private lateinit var storage: RealmHierarchyStorage
    private lateinit var manager: InheritanceManager
    private lateinit var childRealm: RealmModel
    private lateinit var parentRealm: RealmModel

    @BeforeEach
    fun setUp() {
        session = mock()
        storage = mock()
        manager = InheritanceManager(session, storage)

        childRealm = createMockRealm("child-realm-id", "child-realm")
        parentRealm = createMockRealm("parent-realm-id", "parent-realm")
    }

    private fun createMockRealm(
        id: String,
        name: String,
    ): RealmModel {
        val realm = mock<RealmModel>()
        whenever(realm.id).thenReturn(id)
        whenever(realm.name).thenReturn(name)
        whenever(realm.identityProvidersStream).thenReturn(Stream.empty())
        whenever(realm.rolesStream).thenReturn(Stream.empty())
        whenever(realm.getAttribute(any())).thenReturn(null)
        whenever(realm.getAttributes()).thenReturn(mutableMapOf())
        return realm
    }

    @Test
    @DisplayName("부모 Realm이 없으면 상속하지 않음")
    fun testInheritFromParent_NoParent() {
        // Given
        whenever(storage.getParentRealm(childRealm)).thenReturn(null)

        // When
        assertDoesNotThrow {
            manager.inheritFromParent(childRealm)
        }

        // Then
        verify(storage).getParentRealm(childRealm)
        verify(storage, never()).getHierarchyNode(any())
    }

    @Test
    @DisplayName("Identity Provider 상속")
    fun testInheritIdentityProviders() {
        // Given
        val node =
            RealmHierarchyNode(
                realmId = childRealm.id,
                realmName = childRealm.name,
                parentRealmId = parentRealm.id,
                inheritIdp = true,
            )

        val parentIdp = mock<IdentityProviderModel>()
        whenever(parentIdp.alias).thenReturn("test-idp")
        whenever(parentIdp.providerId).thenReturn("oidc")
        whenever(parentIdp.config).thenReturn(mutableMapOf())

        whenever(storage.getParentRealm(childRealm)).thenReturn(parentRealm)
        whenever(storage.getHierarchyNode(childRealm)).thenReturn(node)
        whenever(parentRealm.identityProvidersStream).thenReturn(Stream.of(parentIdp))
        whenever(childRealm.getIdentityProviderByAlias("test-idp")).thenReturn(null)

        // When
        assertDoesNotThrow {
            manager.inheritFromParent(childRealm)
        }

        // Then
        verify(childRealm).addIdentityProvider(any())
    }

    @Test
    @DisplayName("Role 상속")
    fun testInheritRoles() {
        // Given
        val node =
            RealmHierarchyNode(
                realmId = childRealm.id,
                realmName = childRealm.name,
                parentRealmId = parentRealm.id,
                inheritRoles = true,
            )

        val parentRole = mock<RoleModel>()
        whenever(parentRole.name).thenReturn("test-role")
        whenever(parentRole.description).thenReturn("Test role description")

        val childRole = mock<RoleModel>()
        whenever(childRole.name).thenReturn("test-role")

        whenever(storage.getParentRealm(childRealm)).thenReturn(parentRealm)
        whenever(storage.getHierarchyNode(childRealm)).thenReturn(node)
        whenever(parentRealm.rolesStream).thenReturn(Stream.of(parentRole))
        whenever(childRealm.getRole("test-role")).thenReturn(null)
        whenever(childRealm.addRole("test-role")).thenReturn(childRole)

        // When
        assertDoesNotThrow {
            manager.inheritFromParent(childRealm)
        }

        // Then
        verify(childRealm).addRole("test-role")
        verify(childRole).description = "Test role description"
        verify(childRole).setAttribute("hierarchy.inherited", listOf("true"))
        verify(childRole).setAttribute("hierarchy.source_realm", listOf(parentRealm.name))
    }

    @Test
    @DisplayName("상속 설정이 없으면 상속하지 않음")
    fun testInheritFromParent_NoInheritanceSettings() {
        // Given
        val node =
            RealmHierarchyNode(
                realmId = childRealm.id,
                realmName = childRealm.name,
                parentRealmId = parentRealm.id,
                inheritIdp = false,
                inheritAuthFlow = false,
                inheritRoles = false,
            )

        whenever(storage.getParentRealm(childRealm)).thenReturn(parentRealm)
        whenever(storage.getHierarchyNode(childRealm)).thenReturn(node)

        // When
        assertDoesNotThrow {
            manager.inheritFromParent(childRealm)
        }

        // Then
        verify(childRealm, never()).addIdentityProvider(any())
        verify(childRealm, never()).addRole(any())
    }

    @Test
    @DisplayName("계층 구조 전체 동기화")
    fun testSynchronizeHierarchy() {
        // Given
        val child1 = createMockRealm("child1-id", "child1")
        val child2 = createMockRealm("child2-id", "child2")

        val node1 =
            RealmHierarchyNode(
                realmId = child1.id,
                realmName = child1.name,
                parentRealmId = parentRealm.id,
                inheritIdp = true,
            )
        val node2 =
            RealmHierarchyNode(
                realmId = child2.id,
                realmName = child2.name,
                parentRealmId = parentRealm.id,
                inheritRoles = true,
            )

        whenever(storage.getChildRealms(parentRealm)).thenReturn(listOf(child1, child2))
        whenever(storage.getChildRealms(child1)).thenReturn(emptyList())
        whenever(storage.getChildRealms(child2)).thenReturn(emptyList())
        whenever(storage.getHierarchyNode(child1)).thenReturn(node1)
        whenever(storage.getHierarchyNode(child2)).thenReturn(node2)
        whenever(storage.getParentRealm(child1)).thenReturn(parentRealm)
        whenever(storage.getParentRealm(child2)).thenReturn(parentRealm)

        // When
        assertDoesNotThrow {
            manager.synchronizeHierarchy(parentRealm)
        }

        // Then
        verify(storage).getChildRealms(parentRealm)
    }
}
