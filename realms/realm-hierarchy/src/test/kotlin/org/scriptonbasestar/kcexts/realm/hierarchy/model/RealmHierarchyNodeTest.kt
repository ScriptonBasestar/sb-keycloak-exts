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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RealmHierarchyNodeTest {
    @Test
    fun `should create root realm node`() {
        val node =
            RealmHierarchyNode(
                realmId = "realm-1",
                realmName = "master",
                parentRealmId = null,
                tier = "enterprise",
                depth = 0,
                path = "/master",
            )

        assertTrue(node.isRoot())
        assertFalse(node.hasParent())
        assertEquals("realm-1", node.realmId)
        assertEquals("master", node.realmName)
        assertEquals(0, node.depth)
    }

    @Test
    fun `should create child realm node`() {
        val node =
            RealmHierarchyNode(
                realmId = "realm-2",
                realmName = "subsidiary",
                parentRealmId = "realm-1",
                tier = "business",
                depth = 1,
                path = "/master/subsidiary",
                inheritIdp = true,
                inheritAuthFlow = true,
            )

        assertFalse(node.isRoot())
        assertTrue(node.hasParent())
        assertTrue(node.inheritsAnySettings())
        assertEquals("realm-2", node.realmId)
        assertEquals("realm-1", node.parentRealmId)
        assertEquals(1, node.depth)
    }

    @Test
    fun `should not inherit any settings by default`() {
        val node =
            RealmHierarchyNode(
                realmId = "realm-3",
                realmName = "test",
            )

        assertFalse(node.inheritsAnySettings())
        assertFalse(node.inheritIdp)
        assertFalse(node.inheritAuthFlow)
        assertFalse(node.inheritRoles)
    }

    @Test
    fun `should have default values`() {
        val node =
            RealmHierarchyNode(
                realmId = "realm-4",
                realmName = "default",
            )

        assertEquals("standard", node.tier)
        assertEquals(0, node.depth)
        assertEquals("/", node.path)
        assertTrue(node.isRoot())
    }
}
