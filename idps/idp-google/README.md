# Google Identity Provider for Keycloak

This module provides a Google OAuth2 identity provider for Keycloak, enabling users to authenticate using their Google accounts.

## Features

- Standard OAuth2 flow with Google
- OpenID Connect support
- Email and profile information access
- Seamless integration with Keycloak's identity broker

## Configuration

### Google OAuth2 Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the Google+ API
4. Create OAuth2 credentials:
   - Go to APIs & Services → Credentials
   - Click "Create Credentials" → "OAuth 2.0 Client ID"
   - Set application type to "Web application"
   - Add your Keycloak redirect URI: `https://your-keycloak-domain/auth/realms/your-realm/broker/google/endpoint`

### Keycloak Configuration

1. In Keycloak Admin Console, go to your realm
2. Navigate to Identity Providers
3. Add Provider → Google
4. Configure the following:
   - **Client ID**: Your Google OAuth2 client ID
   - **Client Secret**: Your Google OAuth2 client secret
   - **Default Scopes**: `openid email profile` (default)

## OAuth2 Endpoints

- **Authorization URL**: `https://accounts.google.com/o/oauth2/v2/auth`
- **Token URL**: `https://oauth2.googleapis.com/token`
- **User Info URL**: `https://www.googleapis.com/oauth2/v2/userinfo`

## Scopes

The provider uses the following default scopes:
- `openid`: OpenID Connect authentication
- `email`: Access to user's email address
- `profile`: Access to user's basic profile information

## Building

```bash
./gradlew :idps:idp-google:build
```

## Testing

```bash
./gradlew :idps:idp-google:test
```