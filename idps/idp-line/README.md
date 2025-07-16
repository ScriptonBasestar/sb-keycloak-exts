# LINE Identity Provider for Keycloak

This module provides LINE OAuth2 integration for Keycloak, allowing users to authenticate using their LINE accounts.

## Features

- OAuth2 authentication with LINE (OAuth 2.1 compatible)
- User profile mapping from LINE to Keycloak
- Support for displayName, email, and profile picture attributes
- Compatible with Keycloak 26.3.1+

## Installation

1. Build the JAR file:
   ```bash
   ./gradlew :idps:idp-line:shadowJar
   ```

2. Copy the generated JAR to your Keycloak providers directory:
   ```bash
   cp idps/idp-line/build/libs/idp-line-*-all.jar $KEYCLOAK_HOME/providers/
   ```

3. Restart Keycloak to load the provider.

## LINE Channel Setup

1. Visit [LINE Developers Console](https://developers.line.biz/console/)
2. Create a new provider or select an existing one
3. Create a new LINE Login channel
4. In the channel settings:
   - **Channel name**: Your application name
   - **Channel description**: Brief description of your application
5. In "LINE Login settings":
   - Enable "Web app"
   - Set Callback URL: `https://your-keycloak-domain.com/realms/{realm}/broker/line/endpoint`
6. In "Basic settings":
   - Note down your **Channel ID** (this will be your Client ID)
   - Note down your **Channel secret** (this will be your Client Secret)
7. Configure email permission:
   - Go to "LINE Login" > "Permissions"
   - Enable "Email address" permission if you need email access

## Keycloak Configuration

1. Login to Keycloak Admin Console
2. Select your realm
3. Navigate to "Identity Providers"
4. Click "Add provider" and select "LINE" from the dropdown
5. Configure the provider:
   - **Client ID**: Your LINE Channel ID
   - **Client Secret**: Your LINE Channel secret
   - **Default Scopes**: `profile email openid` (automatically set)
   - Other settings can be left as default or customized as needed
6. Save the configuration

## User Attribute Mapping

The provider automatically imports the following attributes from LINE:

- **Username**: Set to email if available, otherwise LINE user ID
- **Email**: User's email (requires email permission and user consent)
- **First Name**: User's display name from LINE
- **Picture**: User's profile picture URL

### Custom Attribute Mapping

To map additional LINE attributes to Keycloak user attributes:

1. In the Identity Provider settings, go to "Mappers" tab
2. Click "Create"
3. Select "User Attribute" as Mapper Type
4. Configure:
   - **Name**: Choose a descriptive name
   - **LINE Attribute**: Path to the attribute in LINE's response
   - **User Attribute Name**: Keycloak user attribute name

### Available LINE Attributes

Attributes available from LINE user profile:

- `userId` - LINE user ID
- `displayName` - User's display name
- `pictureUrl` - Profile picture URL
- `email` - Email address (requires permission)
- `statusMessage` - User's status message

## Permissions and Scopes

### Required Scopes
- `profile` - Access to user's profile information (always required)
- `openid` - Required for OpenID Connect flow

### Optional Scopes
- `email` - Access to user's email address

Note: Email permission requires approval from LINE. Users must also explicitly consent to share their email address.

## Troubleshooting

### Common Issues

1. **"Invalid client" error**
   - Verify Channel ID and Channel secret are correctly entered
   - Ensure the channel is not deleted or suspended

2. **"Invalid redirect_uri" error**
   - Check that the callback URL in LINE channel settings exactly matches Keycloak's format
   - Ensure no trailing slashes or extra spaces in the URL

3. **Missing email address**
   - Verify email permission is enabled in LINE channel settings
   - Check that users have agreed to share their email
   - Note that not all LINE users have email addresses associated with their accounts

4. **Profile picture not loading**
   - LINE profile picture URLs may expire; they should be cached if long-term storage is needed
   - Ensure your Keycloak instance can access external URLs

### Debug Logging

Enable debug logging for the provider by adding to Keycloak's logging configuration:
```
logger.line-idp.name=org.scriptonbasestar.kcexts.idp.line
logger.line-idp.level=DEBUG
```

## API Version

This provider uses LINE Login v2.1 API endpoints:
- Authorization: `https://access.line.me/oauth2/v2.1/authorize`
- Token: `https://api.line.me/oauth2/v2.1/token`
- Profile: `https://api.line.me/v2/profile`

## Security Considerations

1. Always use HTTPS for your Keycloak instance
2. Keep your Channel secret secure and never expose it in client-side code
3. Regularly rotate your Channel secret if possible
4. Monitor your LINE channel for suspicious activity

## References

- [LINE Login v2.1 Documentation](https://developers.line.biz/en/docs/line-login/integrate-line-login/)
- [LINE Developers Console](https://developers.line.biz/console/)
- [Keycloak Server Developer Guide](https://www.keycloak.org/docs/latest/server_development/)