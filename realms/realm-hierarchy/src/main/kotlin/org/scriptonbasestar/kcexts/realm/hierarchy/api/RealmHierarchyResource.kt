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

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.logging.Logger
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.scriptonbasestar.kcexts.realm.hierarchy.api.dto.ErrorResponse
import org.scriptonbasestar.kcexts.realm.hierarchy.api.dto.HierarchyResponse
import org.scriptonbasestar.kcexts.realm.hierarchy.api.dto.SetParentRequest
import org.scriptonbasestar.kcexts.realm.hierarchy.api.dto.SynchronizeResponse
import org.scriptonbasestar.kcexts.realm.hierarchy.api.dto.UpdateInheritanceRequest
import org.scriptonbasestar.kcexts.realm.hierarchy.inheritance.InheritanceManager
import org.scriptonbasestar.kcexts.realm.hierarchy.storage.RealmHierarchyStorage

/**
 * Realm Hierarchy REST API
 *
 * Base Path: /realms/{realm}/hierarchy
 */
@Path("")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class RealmHierarchyResource(
    private val session: KeycloakSession,
    private val realm: RealmModel,
) {
    private val storage = RealmHierarchyStorage(session)
    private val inheritanceManager = InheritanceManager(session, storage)

    companion object {
        private val logger = Logger.getLogger(RealmHierarchyResource::class.java)
    }

    /**
     * GET /realms/{realm}/hierarchy
     * 현재 Realm의 계층 구조 정보 조회
     */
    @GET
    fun getHierarchy(): Response {
        return try {
            val node = storage.getHierarchyNode(realm)
            val parent = storage.getParentRealm(realm)
            val children = storage.getChildRealms(realm)

            val response =
                HierarchyResponse(
                    realmId = node.realmId,
                    realmName = node.realmName,
                    parentRealmId = parent?.id,
                    parentRealmName = parent?.name,
                    tier = node.tier,
                    depth = node.depth,
                    path = node.path,
                    inheritIdp = node.inheritIdp,
                    inheritAuthFlow = node.inheritAuthFlow,
                    inheritRoles = node.inheritRoles,
                    children =
                        children.map { child ->
                            val childNode = storage.getHierarchyNode(child)
                            HierarchyResponse(
                                realmId = childNode.realmId,
                                realmName = childNode.realmName,
                                parentRealmId = realm.id,
                                parentRealmName = realm.name,
                                tier = childNode.tier,
                                depth = childNode.depth,
                                path = childNode.path,
                                inheritIdp = childNode.inheritIdp,
                                inheritAuthFlow = childNode.inheritAuthFlow,
                                inheritRoles = childNode.inheritRoles,
                            )
                        },
                )

            Response.ok(response).build()
        } catch (e: Exception) {
            logger.error("Failed to get hierarchy", e)
            Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(
                    ErrorResponse(
                        error = "hierarchy_get_failed",
                        message = "Failed to get realm hierarchy",
                        details = e.message,
                    ),
                ).build()
        }
    }

    /**
     * GET /realms/{realm}/hierarchy/path
     * 현재 Realm부터 최상위까지의 전체 경로 조회
     */
    @GET
    @Path("/path")
    fun getHierarchyPath(): Response {
        return try {
            val path = storage.getHierarchyPath(realm)
            val responses =
                path.map { node ->
                    val realmModel = session.realms().getRealm(node.realmId)
                    val parent = storage.getParentRealm(realmModel)

                    HierarchyResponse(
                        realmId = node.realmId,
                        realmName = node.realmName,
                        parentRealmId = parent?.id,
                        parentRealmName = parent?.name,
                        tier = node.tier,
                        depth = node.depth,
                        path = node.path,
                        inheritIdp = node.inheritIdp,
                        inheritAuthFlow = node.inheritAuthFlow,
                        inheritRoles = node.inheritRoles,
                    )
                }

            Response.ok(responses).build()
        } catch (e: Exception) {
            logger.error("Failed to get hierarchy path", e)
            Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(
                    ErrorResponse(
                        error = "hierarchy_path_failed",
                        message = "Failed to get realm hierarchy path",
                        details = e.message,
                    ),
                ).build()
        }
    }

    /**
     * POST /realms/{realm}/hierarchy/parent
     * 부모 Realm 설정
     */
    @POST
    @Path("/parent")
    fun setParent(request: SetParentRequest): Response {
        return try {
            val parentRealm =
                if (request.parentRealmId != null) {
                    session.realms().getRealm(request.parentRealmId)
                        ?: return Response
                            .status(Response.Status.NOT_FOUND)
                            .entity(
                                ErrorResponse(
                                    error = "parent_realm_not_found",
                                    message = "Parent realm not found: ${request.parentRealmId}",
                                ),
                            ).build()
                } else {
                    null
                }

            // 부모 Realm 설정
            storage.setParentRealm(
                realm = realm,
                parentRealm = parentRealm,
                inheritSettings = request.inheritIdp || request.inheritAuthFlow || request.inheritRoles,
            )

            // 상속 설정 업데이트
            val node = storage.getHierarchyNode(realm)
            val updatedNode =
                node.copy(
                    inheritIdp = request.inheritIdp,
                    inheritAuthFlow = request.inheritAuthFlow,
                    inheritRoles = request.inheritRoles,
                    updatedAt = System.currentTimeMillis(),
                )
            storage.saveHierarchyNode(realm, updatedNode)

            // 설정 상속 실행
            if (parentRealm != null && updatedNode.inheritsAnySettings()) {
                inheritanceManager.inheritFromParent(realm)
            }

            logger.infof(
                "Parent realm set: %s -> %s",
                parentRealm?.name ?: "null",
                realm.name,
            )

            Response.ok(
                mapOf(
                    "message" to "Parent realm set successfully",
                    "parentRealmId" to request.parentRealmId,
                ),
            ).build()
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid parent realm configuration", e)
            Response
                .status(Response.Status.BAD_REQUEST)
                .entity(
                    ErrorResponse(
                        error = "invalid_parent_configuration",
                        message = e.message ?: "Invalid parent realm configuration",
                    ),
                ).build()
        } catch (e: Exception) {
            logger.error("Failed to set parent realm", e)
            Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(
                    ErrorResponse(
                        error = "set_parent_failed",
                        message = "Failed to set parent realm",
                        details = e.message,
                    ),
                ).build()
        }
    }

    /**
     * PUT /realms/{realm}/hierarchy/inheritance
     * 상속 설정 업데이트
     */
    @PUT
    @Path("/inheritance")
    fun updateInheritance(request: UpdateInheritanceRequest): Response {
        return try {
            val node = storage.getHierarchyNode(realm)
            val updatedNode =
                node.copy(
                    inheritIdp = request.inheritIdp,
                    inheritAuthFlow = request.inheritAuthFlow,
                    inheritRoles = request.inheritRoles,
                    updatedAt = System.currentTimeMillis(),
                )

            storage.saveHierarchyNode(realm, updatedNode)

            // 상속 활성화 시 즉시 실행
            if (updatedNode.hasParent() && updatedNode.inheritsAnySettings()) {
                inheritanceManager.inheritFromParent(realm)
            }

            logger.infof("Inheritance settings updated for realm: %s", realm.name)

            Response.ok(
                mapOf(
                    "message" to "Inheritance settings updated successfully",
                    "inheritIdp" to request.inheritIdp,
                    "inheritAuthFlow" to request.inheritAuthFlow,
                    "inheritRoles" to request.inheritRoles,
                ),
            ).build()
        } catch (e: Exception) {
            logger.error("Failed to update inheritance settings", e)
            Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(
                    ErrorResponse(
                        error = "update_inheritance_failed",
                        message = "Failed to update inheritance settings",
                        details = e.message,
                    ),
                ).build()
        }
    }

    /**
     * POST /realms/{realm}/hierarchy/synchronize
     * 계층 구조 동기화 (현재 Realm의 모든 하위 Realm)
     */
    @POST
    @Path("/synchronize")
    fun synchronize(): Response {
        return try {
            val affectedRealms = mutableListOf<String>()

            fun syncRecursive(currentRealm: RealmModel) {
                val children = storage.getChildRealms(currentRealm)
                children.forEach { child ->
                    val node = storage.getHierarchyNode(child)
                    if (node.inheritsAnySettings()) {
                        inheritanceManager.inheritFromParent(child)
                        affectedRealms.add(child.name)
                    }
                    syncRecursive(child)
                }
            }

            syncRecursive(realm)

            logger.infof("Hierarchy synchronized for realm: %s, affected: %d", realm.name, affectedRealms.size)

            Response.ok(
                SynchronizeResponse(
                    message = "Hierarchy synchronized successfully",
                    affectedRealms = affectedRealms,
                ),
            ).build()
        } catch (e: Exception) {
            logger.error("Failed to synchronize hierarchy", e)
            Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(
                    ErrorResponse(
                        error = "synchronize_failed",
                        message = "Failed to synchronize hierarchy",
                        details = e.message,
                    ),
                ).build()
        }
    }

    /**
     * DELETE /realms/{realm}/hierarchy
     * 계층 구조 삭제 (부모-자식 관계 해제)
     */
    @DELETE
    fun removeHierarchy(): Response {
        return try {
            storage.removeHierarchy(realm)

            logger.infof("Hierarchy removed for realm: %s", realm.name)

            Response.ok(
                mapOf(
                    "message" to "Hierarchy removed successfully",
                ),
            ).build()
        } catch (e: Exception) {
            logger.error("Failed to remove hierarchy", e)
            Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(
                    ErrorResponse(
                        error = "remove_hierarchy_failed",
                        message = "Failed to remove hierarchy",
                        details = e.message,
                    ),
                ).build()
        }
    }
}
