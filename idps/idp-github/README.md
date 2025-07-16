# GitHub Identity Provider for Keycloak

This module provides a GitHub OAuth2 identity provider for Keycloak, enabling users to authenticate using their GitHub accounts.

## Features

- Standard OAuth2 flow with GitHub
- Access to user's public profile and email
- Seamless integration with Keycloak's identity broker

## Configuration

### GitHub OAuth2 Setup

1. Go to [GitHub Developer Settings](https://github.com/settings/developers)
2. Click "New OAuth App"
3. Fill in the application details:
   - **Application name**: Your app name
   - **Homepage URL**: Your application's homepage
   - **Authorization callback URL**: `https://your-keycloak-domain/auth/realms/your-realm/broker/github/endpoint`
4. Click "Register application"
5. Note down the **Client ID** and **Client Secret**

### Keycloak Configuration

1. In Keycloak Admin Console, go to your realm
2. Navigate to Identity Providers
3. Add Provider → GitHub
4. Configure the following:
   - **Client ID**: Your GitHub OAuth2 client ID
   - **Client Secret**: Your GitHub OAuth2 client secret
   - **Default Scopes**: `user:email` (default)

## OAuth2 Endpoints

- **Authorization URL**: `https://github.com/login/oauth/authorize`
- **Token URL**: `https://github.com/login/oauth/access_token`
- **User Info URL**: `https://api.github.com/user`

## Scopes

The provider uses the following default scopes:
- `user:email`: Access to user's email addresses (required for Keycloak user matching)

Additional scopes can be configured in Keycloak:
- `user`: Access to user's public profile information
- `user:read`: Read-only access to user's profile data
- `public_repo`: Access to public repositories
- `repo`: Access to private repositories (use with caution)

## Building

```bash
./gradlew :idps:idp-github:build
```

## Testing

```bash
./gradlew :idps:idp-github:test
```