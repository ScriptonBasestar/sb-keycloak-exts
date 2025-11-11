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

package org.scriptonbasestar.kcexts.realm.hierarchy.api

import org.keycloak.models.KeycloakSession
import org.keycloak.services.resource.RealmResourceProvider

/**
 * Realm Hierarchy Resource Provider
 * Provides REST API at: /realms/{realm}/hierarchy
 */
class RealmHierarchyResourceProvider(
    private val session: KeycloakSession,
) : RealmResourceProvider {
    override fun getResource(): Any {
        val realm = session.context.realm
        return RealmHierarchyResource(session, realm)
    }

    override fun close() {
        // No resources to close
    }
}
