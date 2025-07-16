package org.scriptonbasestar.kcexts.idp.github

object GitHubConstant {
    const val providerId = "github"
    const val providerName = "GitHub"
    const val authUrl = "https://github.com/login/oauth/authorize"
    const val tokenUrl = "https://github.com/login/oauth/access_token"
    const val profileUrl = "https://api.github.com/user"
    const val defaultScope = "user:email"
}