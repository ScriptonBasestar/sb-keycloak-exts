package org.scriptonbasestar.kcexts.idp.github

object GitHubConstant {
    const val PROVIDER_ID = "github"
    const val PROVIDER_NAME = "GitHub"
    
    // OAuth2 Endpoints
    const val AUTH_URL = "https://github.com/login/oauth/authorize"
    const val TOKEN_URL = "https://github.com/login/oauth/access_token"
    const val USER_API_URL = "https://api.github.com/user"
    const val USER_EMAILS_URL = "https://api.github.com/user/emails"
    const val USER_ORGS_URL = "https://api.github.com/user/orgs"
    const val USER_TEAMS_URL = "https://api.github.com/user/teams"
    const val USER_REPOS_URL = "https://api.github.com/user/repos"
    const val USER_SSH_KEYS_URL = "https://api.github.com/user/keys"
    
    // GitHub Enterprise Server Support
    const val ENTERPRISE_PATH_PREFIX = "/api/v3"
    
    // OAuth2 Scopes
    const val DEFAULT_SCOPE = "user:email"
    const val EXTENDED_SCOPE = "user:email read:user"
    const val ORG_SCOPE = "user:email read:user read:org"
    const val TEAM_SCOPE = "user:email read:user read:org read:team"
    const val REPO_SCOPE = "user:email read:user read:org repo"
    const val ADMIN_SCOPE = "user:email read:user read:org admin:org"
    
    // Configuration Keys
    const val GITHUB_ENTERPRISE_URL_KEY = "githubEnterpriseUrl"
    const val ENABLE_PKCE_KEY = "enablePKCE"
    const val VERIFY_ORG_MEMBERSHIP_KEY = "verifyOrgMembership"
    const val REQUIRED_ORGANIZATION_KEY = "requiredOrganization"
    const val REQUIRED_TEAM_KEY = "requiredTeam"
    const val VERIFY_SSH_KEYS_KEY = "verifySSHKeys"
    const val VERIFY_2FA_KEY = "verify2FA"
    const val SYNC_TEAMS_KEY = "syncTeams"
    const val SYNC_REPOSITORIES_KEY = "syncRepositories"
    
    // Legacy compatibility
    @Deprecated("Use PROVIDER_ID instead", ReplaceWith("PROVIDER_ID"))
    const val providerId = PROVIDER_ID
    
    @Deprecated("Use PROVIDER_NAME instead", ReplaceWith("PROVIDER_NAME"))
    const val providerName = PROVIDER_NAME
    
    @Deprecated("Use AUTH_URL instead", ReplaceWith("AUTH_URL"))
    const val authUrl = AUTH_URL
    
    @Deprecated("Use TOKEN_URL instead", ReplaceWith("TOKEN_URL"))
    const val tokenUrl = TOKEN_URL
    
    @Deprecated("Use USER_API_URL instead", ReplaceWith("USER_API_URL"))
    const val profileUrl = USER_API_URL
    
    @Deprecated("Use DEFAULT_SCOPE instead", ReplaceWith("DEFAULT_SCOPE"))
    const val defaultScope = DEFAULT_SCOPE
}
