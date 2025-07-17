package org.scriptonbasestar.kcexts.idp.github

import org.keycloak.broker.oidc.OAuth2IdentityProviderConfig
import org.keycloak.models.IdentityProviderModel

class GitHubIdentityProviderConfig : OAuth2IdentityProviderConfig {
    constructor() : super() {
        this.alias = GitHubConstant.PROVIDER_ID
        this.authorizationUrl = GitHubConstant.AUTH_URL
        this.tokenUrl = GitHubConstant.TOKEN_URL
        this.userInfoUrl = GitHubConstant.USER_API_URL
        this.defaultScope = GitHubConstant.DEFAULT_SCOPE
    }

    constructor(model: IdentityProviderModel) : super(model) {
        this.alias = GitHubConstant.PROVIDER_ID
        this.authorizationUrl = GitHubConstant.AUTH_URL
        this.tokenUrl = GitHubConstant.TOKEN_URL
        this.userInfoUrl = GitHubConstant.USER_API_URL
        this.defaultScope = GitHubConstant.DEFAULT_SCOPE
    }

    // GitHub Enterprise Server Configuration
    val githubEnterpriseUrl: String
        get() = getConfig().getOrDefault(GitHubConstant.GITHUB_ENTERPRISE_URL_KEY, "")

    val isGitHubEnterprise: Boolean
        get() = githubEnterpriseUrl.isNotBlank()

    // OAuth2 Security Configuration
    val isEnablePKCE: Boolean
        get() = getConfig().getOrDefault(GitHubConstant.ENABLE_PKCE_KEY, "true").toBoolean()

    // Organization & Team Configuration
    val isVerifyOrgMembership: Boolean
        get() = getConfig().getOrDefault(GitHubConstant.VERIFY_ORG_MEMBERSHIP_KEY, "false").toBoolean()

    val requiredOrganization: String
        get() = getConfig().getOrDefault(GitHubConstant.REQUIRED_ORGANIZATION_KEY, "")

    val requiredTeam: String
        get() = getConfig().getOrDefault(GitHubConstant.REQUIRED_TEAM_KEY, "")

    // Security Verification Configuration
    val isVerifySSHKeys: Boolean
        get() = getConfig().getOrDefault(GitHubConstant.VERIFY_SSH_KEYS_KEY, "false").toBoolean()

    val isVerify2FA: Boolean
        get() = getConfig().getOrDefault(GitHubConstant.VERIFY_2FA_KEY, "false").toBoolean()

    // Synchronization Configuration
    val isSyncTeams: Boolean
        get() = getConfig().getOrDefault(GitHubConstant.SYNC_TEAMS_KEY, "false").toBoolean()

    val isSyncRepositories: Boolean
        get() = getConfig().getOrDefault(GitHubConstant.SYNC_REPOSITORIES_KEY, "false").toBoolean()

    // Setter Methods
    fun setGitHubEnterpriseUrl(url: String) {
        getConfig()[GitHubConstant.GITHUB_ENTERPRISE_URL_KEY] = url
        // Update URLs for Enterprise Server
        if (url.isNotBlank()) {
            val baseUrl = url.trimEnd('/')
            this.authorizationUrl = "$baseUrl/login/oauth/authorize"
            this.tokenUrl = "$baseUrl/login/oauth/access_token"
            this.userInfoUrl = "$baseUrl${GitHubConstant.ENTERPRISE_PATH_PREFIX}/user"
        } else {
            // Reset to GitHub.com URLs
            this.authorizationUrl = GitHubConstant.AUTH_URL
            this.tokenUrl = GitHubConstant.TOKEN_URL
            this.userInfoUrl = GitHubConstant.USER_API_URL
        }
    }

    fun setEnablePKCE(enable: Boolean) {
        getConfig()[GitHubConstant.ENABLE_PKCE_KEY] = enable.toString()
    }

    fun setVerifyOrgMembership(enable: Boolean) {
        getConfig()[GitHubConstant.VERIFY_ORG_MEMBERSHIP_KEY] = enable.toString()
    }

    fun setRequiredOrganization(organization: String) {
        getConfig()[GitHubConstant.REQUIRED_ORGANIZATION_KEY] = organization
    }

    fun setRequiredTeam(team: String) {
        getConfig()[GitHubConstant.REQUIRED_TEAM_KEY] = team
    }

    fun setVerifySSHKeys(enable: Boolean) {
        getConfig()[GitHubConstant.VERIFY_SSH_KEYS_KEY] = enable.toString()
    }

    fun setVerify2FA(enable: Boolean) {
        getConfig()[GitHubConstant.VERIFY_2FA_KEY] = enable.toString()
    }

    fun setSyncTeams(enable: Boolean) {
        getConfig()[GitHubConstant.SYNC_TEAMS_KEY] = enable.toString()
    }

    fun setSyncRepositories(enable: Boolean) {
        getConfig()[GitHubConstant.SYNC_REPOSITORIES_KEY] = enable.toString()
    }

    // Dynamic API URL Generation for Enterprise Support
    fun getApiUrl(endpoint: String): String {
        return if (isGitHubEnterprise) {
            "${githubEnterpriseUrl.trimEnd('/')}${GitHubConstant.ENTERPRISE_PATH_PREFIX}$endpoint"
        } else {
            "https://api.github.com$endpoint"
        }
    }
}
