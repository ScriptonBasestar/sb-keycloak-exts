package org.scriptonbasestar.kcexts.idp.github

import com.fasterxml.jackson.databind.JsonNode
import org.jboss.logging.Logger
import org.keycloak.broker.oidc.AbstractOAuth2IdentityProvider
import org.keycloak.broker.provider.BrokeredIdentityContext
import org.keycloak.broker.provider.IdentityBrokerException
import org.keycloak.broker.provider.util.SimpleHttp
import org.keycloak.broker.social.SocialIdentityProvider
import org.keycloak.models.KeycloakSession
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class GitHubIdentityProvider(
    keycloakSession: KeycloakSession,
    config: GitHubIdentityProviderConfig,
) : AbstractOAuth2IdentityProvider<GitHubIdentityProviderConfig>(
        keycloakSession,
        config,
    ),
    SocialIdentityProvider<GitHubIdentityProviderConfig> {
    companion object {
        private val logger = Logger.getLogger(GitHubIdentityProvider::class.java)
        private val rateLimitMap = ConcurrentHashMap<String, RateLimitInfo>()
        private val RATE_LIMIT_WINDOW = TimeUnit.MINUTES.toMillis(1) // 1 minute window
        private val MAX_REQUESTS_PER_MINUTE = 60

        private data class RateLimitInfo(
            var requestCount: Int = 0,
            var windowStart: Long = System.currentTimeMillis(),
        )
    }

    override fun getDefaultScopes(): String =
        when {
            config.isVerifyOrgMembership && config.requiredTeam.isNotBlank() -> GitHubConstant.TEAM_SCOPE
            config.isVerifyOrgMembership -> GitHubConstant.ORG_SCOPE
            config.isSyncRepositories -> GitHubConstant.REPO_SCOPE
            else -> GitHubConstant.DEFAULT_SCOPE
        }

    override fun doGetFederatedIdentity(accessToken: String): BrokeredIdentityContext {
        try {
            // Rate limiting check
            checkRateLimit()

            val userProfile = getUserProfile(accessToken)
            val context = extractIdentityFromProfile(null, userProfile)

            // Organization membership verification
            if (config.isVerifyOrgMembership) {
                verifyOrganizationMembership(accessToken, context)
            }

            // Team membership verification
            if (config.requiredTeam.isNotBlank()) {
                verifyTeamMembership(accessToken, context)
            }

            // Security verifications
            if (config.isVerifySSHKeys) {
                verifySSHKeys(accessToken, context)
            }

            if (config.isVerify2FA) {
                verify2FAStatus(accessToken, context)
            }

            // Synchronize additional information
            if (config.isSyncTeams) {
                syncTeamMemberships(accessToken, context)
            }

            if (config.isSyncRepositories) {
                syncRepositoryAccess(accessToken, context)
            }

            // Enhanced developer profile attributes
            enhanceDeveloperProfile(accessToken, context)

            // Enterprise integration features
            if (config.isGitHubEnterprise) {
                performEnterpriseIntegration(accessToken, context)
            }

            // Audit logging
            logUserAuthentication(context, accessToken)

            return context
        } catch (e: Exception) {
            logger.error("Failed to get federated identity from GitHub", e)
            throw IdentityBrokerException("Could not obtain user profile from GitHub: ${e.message}")
        }
    }

    private fun checkRateLimit() {
        val clientIp = session.getContext().connection.remoteAddr
        val now = System.currentTimeMillis()

        val rateLimitInfo = rateLimitMap.computeIfAbsent(clientIp) { RateLimitInfo() }

        synchronized(rateLimitInfo) {
            if (now - rateLimitInfo.windowStart > RATE_LIMIT_WINDOW) {
                rateLimitInfo.requestCount = 1
                rateLimitInfo.windowStart = now
            } else {
                rateLimitInfo.requestCount++
                if (rateLimitInfo.requestCount > MAX_REQUESTS_PER_MINUTE) {
                    logger.warnf("Rate limit exceeded for IP: %s", clientIp)
                    throw IdentityBrokerException("Rate limit exceeded. Please try again later.")
                }
            }
        }
    }

    private fun getUserProfile(accessToken: String): JsonNode {
        val url = config.getApiUrl("/user")
        val response =
            SimpleHttp
                .doGet(url, session)
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Keycloak GitHub Identity Provider")
                .asJson()

        logger.debugf("GitHub user profile retrieved from: %s", url)
        return response
    }

    private fun verifyOrganizationMembership(
        accessToken: String,
        context: BrokeredIdentityContext,
    ) {
        if (config.requiredOrganization.isBlank()) return

        val orgsUrl = config.getApiUrl("/user/orgs")
        val organizations =
            SimpleHttp
                .doGet(orgsUrl, session)
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Keycloak GitHub Identity Provider")
                .asJson()

        val orgNames = organizations.map { it.get("login").asText() }

        if (!orgNames.contains(config.requiredOrganization)) {
            logger.warnf(
                "User %s is not a member of required organization: %s",
                context.username,
                config.requiredOrganization,
            )
            throw IdentityBrokerException("User is not a member of the required organization")
        }

        context.setUserAttribute("github_organizations", orgNames)
        logger.debugf("Organization membership verified for user: %s", context.username)
    }

    private fun verifyTeamMembership(
        accessToken: String,
        context: BrokeredIdentityContext,
    ) {
        val teamsUrl = config.getApiUrl("/user/teams")
        val teams =
            SimpleHttp
                .doGet(teamsUrl, session)
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Keycloak GitHub Identity Provider")
                .asJson()

        val teamNames = teams.map { "${it.get("organization").get("login").asText()}/${it.get("name").asText()}" }

        if (!teamNames.contains(config.requiredTeam)) {
            logger.warnf(
                "User %s is not a member of required team: %s",
                context.username,
                config.requiredTeam,
            )
            throw IdentityBrokerException("User is not a member of the required team")
        }

        context.setUserAttribute("github_teams", teamNames)
        logger.debugf("Team membership verified for user: %s", context.username)
    }

    private fun verifySSHKeys(
        accessToken: String,
        context: BrokeredIdentityContext,
    ) {
        val sshKeysUrl = config.getApiUrl("/user/keys")
        val sshKeys =
            SimpleHttp
                .doGet(sshKeysUrl, session)
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Keycloak GitHub Identity Provider")
                .asJson()

        if (sshKeys.size() == 0) {
            logger.warnf("User %s has no SSH keys configured", context.username)
            throw IdentityBrokerException("User must have at least one SSH key configured")
        }

        context.setUserAttribute("github_ssh_keys_count", sshKeys.size().toString())
        logger.debugf(
            "SSH keys verification passed for user: %s (%d keys)",
            context.username,
            sshKeys.size(),
        )
    }

    private fun verify2FAStatus(
        accessToken: String,
        context: BrokeredIdentityContext,
    ) {
        val userUrl = config.getApiUrl("/user")
        val userInfo =
            SimpleHttp
                .doGet(userUrl, session)
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Keycloak GitHub Identity Provider")
                .asJson()

        val twoFactorAuthentication = userInfo.get("two_factor_authentication")?.asBoolean() ?: false

        if (!twoFactorAuthentication) {
            logger.warnf("User %s does not have 2FA enabled", context.username)
            throw IdentityBrokerException("Two-factor authentication is required")
        }

        context.setUserAttribute("github_2fa_enabled", "true")
        logger.debugf("2FA verification passed for user: %s", context.username)
    }

    private fun syncTeamMemberships(
        accessToken: String,
        context: BrokeredIdentityContext,
    ) {
        try {
            val teamsUrl = config.getApiUrl("/user/teams")
            val teams =
                SimpleHttp
                    .doGet(teamsUrl, session)
                    .header("Authorization", "Bearer $accessToken")
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Keycloak GitHub Identity Provider")
                    .asJson()

            val teamMemberships =
                teams.map {
                    mapOf(
                        "organization" to it.get("organization").get("login").asText(),
                        "team" to it.get("name").asText(),
                        "permission" to it.get("permission").asText(),
                    )
                }

            context.setUserAttribute(
                "github_team_memberships",
                teamMemberships.joinToString(",") { "${it["organization"]}/${it["team"]}:${it["permission"]}" },
            )

            logger.debugf(
                "Team memberships synced for user: %s (%d teams)",
                context.username,
                teamMemberships.size,
            )
        } catch (e: Exception) {
            logger.warn("Failed to sync team memberships", e)
        }
    }

    private fun syncRepositoryAccess(
        accessToken: String,
        context: BrokeredIdentityContext,
    ) {
        try {
            val reposUrl = config.getApiUrl("/user/repos?type=all&sort=updated&per_page=100")
            val repositories =
                SimpleHttp
                    .doGet(reposUrl, session)
                    .header("Authorization", "Bearer $accessToken")
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Keycloak GitHub Identity Provider")
                    .asJson()

            val repoAccess =
                repositories.map {
                    mapOf(
                        "name" to it.get("full_name").asText(),
                        "permission" to it.get("permissions").toString(),
                        "private" to it.get("private").asBoolean().toString(),
                    )
                }

            context.setUserAttribute("github_repository_count", repositories.size().toString())
            context.setUserAttribute(
                "github_repository_access",
                repoAccess.take(50).joinToString(",") { "${it["name"]}:${it["permission"]}" },
            )

            logger.debugf(
                "Repository access synced for user: %s (%d repos)",
                context.username,
                repositories.size(),
            )
        } catch (e: Exception) {
            logger.warn("Failed to sync repository access", e)
        }
    }

    private fun enhanceDeveloperProfile(
        accessToken: String,
        context: BrokeredIdentityContext,
    ) {
        try {
            // Get user profile with additional stats
            val userUrl = config.getApiUrl("/user")
            val userProfile =
                SimpleHttp
                    .doGet(userUrl, session)
                    .header("Authorization", "Bearer $accessToken")
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Keycloak GitHub Identity Provider")
                    .asJson()

            // Extract developer statistics
            context.setUserAttribute("github_public_repos", userProfile.get("public_repos")?.asText() ?: "0")
            context.setUserAttribute("github_followers", userProfile.get("followers")?.asText() ?: "0")
            context.setUserAttribute("github_following", userProfile.get("following")?.asText() ?: "0")
            context.setUserAttribute("github_created_at", userProfile.get("created_at")?.asText() ?: "")
            context.setUserAttribute("github_updated_at", userProfile.get("updated_at")?.asText() ?: "")

            val company = userProfile.get("company")?.asText()
            if (!company.isNullOrBlank()) {
                context.setUserAttribute("github_company", company)
            }

            val location = userProfile.get("location")?.asText()
            if (!location.isNullOrBlank()) {
                context.setUserAttribute("github_location", location)
            }

            val blog = userProfile.get("blog")?.asText()
            if (!blog.isNullOrBlank()) {
                context.setUserAttribute("github_blog", blog)
            }

            // Get programming languages from recent repositories
            extractProgrammingLanguages(accessToken, context)

            logger.debugf("Enhanced developer profile for user: %s", context.username)
        } catch (e: Exception) {
            logger.warn("Failed to enhance developer profile", e)
        }
    }

    private fun extractProgrammingLanguages(
        accessToken: String,
        context: BrokeredIdentityContext,
    ) {
        try {
            val reposUrl = config.getApiUrl("/user/repos?type=owner&sort=updated&per_page=10")
            val repositories =
                SimpleHttp
                    .doGet(reposUrl, session)
                    .header("Authorization", "Bearer $accessToken")
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Keycloak GitHub Identity Provider")
                    .asJson()

            val languages =
                repositories
                    .mapNotNull { repo ->
                        repo.get("language")?.asText()?.takeIf { it != "null" }
                    }.distinct()

            if (languages.isNotEmpty()) {
                context.setUserAttribute("github_languages", languages.joinToString(","))
            }

            logger.debugf(
                "Programming languages extracted for user: %s (%s)",
                context.username,
                languages.joinToString(", "),
            )
        } catch (e: Exception) {
            logger.warn("Failed to extract programming languages", e)
        }
    }

    // PKCE helper methods
    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(codeVerifier.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    private fun performEnterpriseIntegration(
        accessToken: String,
        context: BrokeredIdentityContext,
    ) {
        try {
            // Sync GitHub Enterprise organization structure
            if (config.isSyncTeams) {
                syncEnterpriseOrganizationStructure(accessToken, context)
            }

            // Map GitHub Teams to Keycloak Groups
            if (config.isSyncTeams) {
                mapTeamsToKeycloakGroups(accessToken, context)
            }

            // Repository access-based authorization
            if (config.isSyncRepositories) {
                configureRepositoryBasedAuthorization(accessToken, context)
            }

            logger.debugf("Enterprise integration completed for user: %s", context.username)
        } catch (e: Exception) {
            logger.warn("Failed to perform enterprise integration", e)
        }
    }

    private fun syncEnterpriseOrganizationStructure(
        accessToken: String,
        context: BrokeredIdentityContext,
    ) {
        try {
            val orgsUrl = config.getApiUrl("/user/orgs")
            val organizations =
                SimpleHttp
                    .doGet(orgsUrl, session)
                    .header("Authorization", "Bearer $accessToken")
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Keycloak GitHub Identity Provider")
                    .asJson()

            val orgStructure =
                organizations.map { org ->
                    val orgLogin = org.get("login").asText()

                    // Get organization details
                    val orgDetailsUrl = config.getApiUrl("/orgs/$orgLogin")
                    val orgDetails =
                        SimpleHttp
                            .doGet(orgDetailsUrl, session)
                            .header("Authorization", "Bearer $accessToken")
                            .header("Accept", "application/vnd.github.v3+json")
                            .header("User-Agent", "Keycloak GitHub Identity Provider")
                            .asJson()

                    mapOf(
                        "login" to orgLogin,
                        "name" to (orgDetails.get("name")?.asText() ?: orgLogin),
                        "description" to (orgDetails.get("description")?.asText() ?: ""),
                        "company" to (orgDetails.get("company")?.asText() ?: ""),
                        "location" to (orgDetails.get("location")?.asText() ?: ""),
                        "public_repos" to orgDetails.get("public_repos").asText(),
                        "private_repos" to orgDetails.get("total_private_repos").asText(),
                    )
                }

            context.setUserAttribute(
                "github_enterprise_organizations",
                orgStructure.joinToString("|") { "${it["login"]}:${it["name"]}" },
            )

            logger.debugf(
                "Enterprise organization structure synced for user: %s (%d orgs)",
                context.username,
                orgStructure.size,
            )
        } catch (e: Exception) {
            logger.warn("Failed to sync enterprise organization structure", e)
        }
    }

    private fun mapTeamsToKeycloakGroups(
        accessToken: String,
        context: BrokeredIdentityContext,
    ) {
        try {
            val teamsUrl = config.getApiUrl("/user/teams")
            val teams =
                SimpleHttp
                    .doGet(teamsUrl, session)
                    .header("Authorization", "Bearer $accessToken")
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Keycloak GitHub Identity Provider")
                    .asJson()

            val keycloakGroups =
                teams.map { team ->
                    val orgName = team.get("organization").get("login").asText()
                    val teamName = team.get("name").asText()
                    val permission = team.get("permission").asText()
                    val privacy = team.get("privacy").asText()

                    // Create Keycloak group path: /github-teams/{organization}/{team}
                    val groupPath = "/github-teams/$orgName/$teamName"

                    mapOf(
                        "path" to groupPath,
                        "name" to teamName,
                        "organization" to orgName,
                        "permission" to permission,
                        "privacy" to privacy,
                        "attributes" to
                            mapOf(
                                "github_team_id" to team.get("id").asText(),
                                "github_team_slug" to team.get("slug").asText(),
                                "github_organization" to orgName,
                                "github_permission" to permission,
                                "github_privacy" to privacy,
                            ),
                    )
                }

            // Store group mappings for Keycloak to process
            context.setUserAttribute(
                "keycloak_group_mappings",
                keycloakGroups.joinToString("|") { it["path"] as String },
            )

            context.setUserAttribute(
                "github_team_group_mappings",
                keycloakGroups.joinToString("|") { "${it["organization"]}/${it["name"]}:${it["path"]}" },
            )

            logger.debugf(
                "Teams mapped to Keycloak groups for user: %s (%d teams)",
                context.username,
                keycloakGroups.size,
            )
        } catch (e: Exception) {
            logger.warn("Failed to map teams to Keycloak groups", e)
        }
    }

    private fun configureRepositoryBasedAuthorization(
        accessToken: String,
        context: BrokeredIdentityContext,
    ) {
        try {
            val reposUrl = config.getApiUrl("/user/repos?type=all&sort=updated&per_page=100")
            val repositories =
                SimpleHttp
                    .doGet(reposUrl, session)
                    .header("Authorization", "Bearer $accessToken")
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Keycloak GitHub Identity Provider")
                    .asJson()

            val repoAuthorizations =
                repositories.map { repo ->
                    val repoName = repo.get("full_name").asText()
                    val permissions = repo.get("permissions")
                    val isPrivate = repo.get("private").asBoolean()
                    val isOwner = repo.get("owner").get("login").asText() == context.username

                    val authLevel =
                        when {
                            isOwner -> "owner"
                            permissions.get("admin").asBoolean() -> "admin"
                            permissions.get("maintain").asBoolean() -> "maintain"
                            permissions.get("push").asBoolean() -> "write"
                            permissions.get("triage").asBoolean() -> "triage"
                            permissions.get("pull").asBoolean() -> "read"
                            else -> "none"
                        }

                    mapOf(
                        "repository" to repoName,
                        "authorization" to authLevel,
                        "private" to isPrivate.toString(),
                        "owner" to isOwner.toString(),
                    )
                }

            // Create authorization rules for different access levels
            val adminRepos = repoAuthorizations.filter { it["authorization"] in listOf("owner", "admin") }
            val writeRepos = repoAuthorizations.filter { it["authorization"] in listOf("maintain", "write") }
            val readRepos = repoAuthorizations.filter { it["authorization"] == "read" }

            context.setUserAttribute(
                "github_admin_repositories",
                adminRepos.joinToString(",") { it["repository"] as String },
            )
            context.setUserAttribute(
                "github_write_repositories",
                writeRepos.joinToString(",") { it["repository"] as String },
            )
            context.setUserAttribute(
                "github_read_repositories",
                readRepos.joinToString(",") { it["repository"] as String },
            )

            // Set authorization scopes based on repository access
            val authorizationScopes = mutableListOf<String>()
            if (adminRepos.isNotEmpty()) authorizationScopes.add("github:admin")
            if (writeRepos.isNotEmpty()) authorizationScopes.add("github:write")
            if (readRepos.isNotEmpty()) authorizationScopes.add("github:read")

            context.setUserAttribute("github_authorization_scopes", authorizationScopes.joinToString(","))

            logger.debugf(
                "Repository-based authorization configured for user: %s (admin: %d, write: %d, read: %d)",
                context.username,
                adminRepos.size,
                writeRepos.size,
                readRepos.size,
            )
        } catch (e: Exception) {
            logger.warn("Failed to configure repository-based authorization", e)
        }
    }

    private fun logUserAuthentication(
        context: BrokeredIdentityContext,
        accessToken: String,
    ) {
        try {
            val auditData =
                mapOf(
                    "timestamp" to System.currentTimeMillis(),
                    "provider" to "github",
                    "user_id" to context.id,
                    "username" to context.username,
                    "email" to context.email,
                    "ip_address" to session.getContext().connection.remoteAddr,
                    "user_agent" to "Keycloak GitHub Identity Provider",
                    "github_enterprise" to config.isGitHubEnterprise,
                    "organizations" to context.getUserAttribute("github_organizations"),
                    "teams" to context.getUserAttribute("github_teams"),
                    "2fa_enabled" to context.getUserAttribute("github_2fa_enabled"),
                    "ssh_keys_count" to context.getUserAttribute("github_ssh_keys_count"),
                    "repository_count" to context.getUserAttribute("github_repository_count"),
                    "authentication_method" to "oauth2",
                    "realm" to session.context.realm.name,
                )

            // Log to Keycloak events
            logger.infof(
                "GitHub authentication successful: user=%s, ip=%s, enterprise=%s",
                context.username,
                session.getContext().connection.remoteAddr,
                config.isGitHubEnterprise,
            )

            // Store audit data for external systems
            context.setUserAttribute(
                "github_audit_log",
                auditData.entries.joinToString("|") { "${it.key}=${it.value}" },
            )

            logger.debugf("Audit log created for user authentication: %s", context.username)
        } catch (e: Exception) {
            logger.warn("Failed to log user authentication", e)
        }
    }
}
