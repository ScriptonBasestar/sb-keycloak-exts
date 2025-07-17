package org.scriptonbasestar.kcexts.idp.github

import org.keycloak.broker.oidc.OAuth2IdentityProviderConfig
import org.keycloak.models.IdentityProviderModel

class GitHubIdentityProviderConfig : OAuth2IdentityProviderConfig {
    constructor() : super() {
        this.alias = GitHubConstant.providerId
        this.authorizationUrl = GitHubConstant.authUrl
        this.tokenUrl = GitHubConstant.tokenUrl
        this.userInfoUrl = GitHubConstant.profileUrl
        this.defaultScope = GitHubConstant.defaultScope
    }

    constructor(model: IdentityProviderModel) : super(model) {
        this.alias = GitHubConstant.providerId
        this.authorizationUrl = GitHubConstant.authUrl
        this.tokenUrl = GitHubConstant.tokenUrl
        this.userInfoUrl = GitHubConstant.profileUrl
        this.defaultScope = GitHubConstant.defaultScope
    }
}
