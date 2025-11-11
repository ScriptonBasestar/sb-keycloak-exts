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

import org.keycloak.Config
import org.keycloak.events.EventListenerProvider
import org.keycloak.events.EventListenerProviderFactory
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory

/**
 * Realm Hierarchy Event Listener Factory
 */
class RealmHierarchyEventListenerFactory : EventListenerProviderFactory {
    companion object {
        const val PROVIDER_ID = "realm-hierarchy"
    }

    override fun create(session: KeycloakSession): EventListenerProvider {
        return RealmHierarchyEventListener(session)
    }

    override fun init(config: Config.Scope) {
        // No initialization needed
    }

    override fun postInit(factory: KeycloakSessionFactory) {
        // No post-initialization needed
    }

    override fun close() {
        // No resources to close
    }

    override fun getId(): String = PROVIDER_ID
}
