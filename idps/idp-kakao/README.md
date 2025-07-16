# Kakao Identity Provider for Keycloak

This module provides Kakao OAuth2 integration for Keycloak, allowing users to authenticate using their Kakao accounts.

## Features

- OAuth2 authentication with Kakao
- User profile mapping from Kakao to Keycloak
- Support for email, nickname, and profile image attributes
- Compatible with Keycloak 26.3.1+

## Installation

1. Build the JAR file:
   ```bash
   ./gradlew :idps:idp-kakao:shadowJar
   ```

2. Copy the generated JAR to your Keycloak providers directory:
   ```bash
   cp idps/idp-kakao/build/libs/idp-kakao-*-all.jar $KEYCLOAK_HOME/providers/
   ```

3. Restart Keycloak to load the provider.

## Kakao Application Setup

1. Visit [Kakao Developers Console](https://developers.kakao.com/)
2. Create a new application or select an existing one
3. In the application settings:
   - Go to "플랫폼" (Platform) section
   - Add "Web" platform
   - Set your domain (e.g., `https://your-keycloak-domain.com`)
4. In "카카오 로그인" (Kakao Login) settings:
   - Enable Kakao Login
   - Set Redirect URI: `https://your-keycloak-domain.com/realms/{realm}/broker/kakao/endpoint`
   - Enable required consent items:
     - profile_nickname (닉네임)
     - profile_image (프로필 사진)
     - account_email (이메일)
5. Note down your REST API Key (this will be your Client ID)

## Keycloak Configuration

1. Login to Keycloak Admin Console
2. Select your realm
3. Navigate to "Identity Providers"
4. Click "Add provider" and select "Kakao" from the dropdown
5. Configure the provider:
   - **Client ID**: Your Kakao REST API Key
   - **Client Secret**: Leave empty (Kakao doesn't require client secret for web apps)
   - **Default Scopes**: `profile_image openid profile_nickname` (automatically set)
   - Other settings can be left as default or customized as needed
6. Save the configuration

## User Attribute Mapping

The provider automatically imports the following attributes from Kakao:

- **Username**: Set to email if available, otherwise Kakao ID
- **Email**: User's email (if permission granted)
- **First Name**: User's nickname from Kakao

### Custom Attribute Mapping

To map additional Kakao attributes to Keycloak user attributes:

1. In the Identity Provider settings, go to "Mappers" tab
2. Click "Create"
3. Select "User Attribute" as Mapper Type
4. Configure:
   - **Name**: Choose a descriptive name
   - **Kakao Attribute**: Path to the attribute in Kakao's response (e.g., `kakao_account.gender`)
   - **User Attribute Name**: Keycloak user attribute name

### Available Kakao Attributes

Common attributes available from Kakao (requires appropriate permissions):

- `id` - Kakao user ID
- `connected_at` - Account connection timestamp
- `properties.nickname` - User's nickname
- `properties.profile_image` - Profile image URL
- `properties.thumbnail_image` - Thumbnail image URL
- `kakao_account.email` - Email address
- `kakao_account.age_range` - Age range (e.g., "20~29")
- `kakao_account.birthday` - Birthday (MMDD format)
- `kakao_account.gender` - Gender ("male" or "female")

## Troubleshooting

### Common Issues

1. **"Invalid redirect URI" error**
   - Ensure the redirect URI in Kakao app settings exactly matches Keycloak's format
   - Check that your Keycloak base URL is correctly configured

2. **Missing user information**
   - Verify that required permissions are enabled in Kakao app settings
   - Check that users have agreed to share the requested information

3. **Authentication fails**
   - Check Keycloak logs for detailed error messages
   - Verify your REST API Key is correctly entered as Client ID
   - Ensure the Kakao app is not in development mode if testing with other users

### Debug Logging

Enable debug logging for the provider by adding to Keycloak's logging configuration:
```
logger.kakao-idp.name=org.scriptonbasestar.kcexts.idp.kakao
logger.kakao-idp.level=DEBUG
```

## References

- [Kakao Login REST API Documentation](https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api)
- [Keycloak Server Developer Guide](https://www.keycloak.org/docs/latest/server_development/)