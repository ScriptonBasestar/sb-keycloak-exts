# Naver Identity Provider for Keycloak

This module provides Naver OAuth2 integration for Keycloak, allowing users to authenticate using their Naver accounts.

## Features

- OAuth2 authentication with Naver
- User profile mapping from Naver to Keycloak
- Support for email, name, nickname, profile image, age, gender, and birthday attributes
- External token exchange support
- Compatible with Keycloak 26.3.1+

## Installation

1. Build the JAR file:
   ```bash
   ./gradlew :idps:idp-naver:shadowJar
   ```

2. Copy the generated JAR to your Keycloak providers directory:
   ```bash
   cp idps/idp-naver/build/libs/idp-naver-*-all.jar $KEYCLOAK_HOME/providers/
   ```

3. Restart Keycloak to load the provider.

## Naver Application Setup

1. Visit [Naver Developers Console](https://developers.naver.com/apps/#/register)
2. Create a new application:
   - **애플리케이션 이름** (Application Name): Your application name
   - **사용 API** (APIs): Select "네이버 로그인" (Naver Login)
3. In application settings:
   - Go to "API 설정" (API Settings) tab
   - Under "네이버 로그인" (Naver Login):
     - **서비스 URL** (Service URL): Your application's base URL
     - **Callback URL**: Add `https://your-keycloak-domain.com/realms/{realm}/broker/naver/endpoint`
4. Configure permissions:
   - Select required information to retrieve:
     - 회원이름 (Name) - Recommended
     - 이메일주소 (Email) - Recommended
     - 별명 (Nickname) - Optional
     - 프로필사진 (Profile Image) - Optional
     - 생일 (Birthday) - Optional
     - 연령대 (Age Range) - Optional
     - 성별 (Gender) - Optional
5. In "개요" (Overview) tab:
   - Note down your **Client ID**
   - Note down your **Client Secret**

## Keycloak Configuration

1. Login to Keycloak Admin Console
2. Select your realm
3. Navigate to "Identity Providers"
4. Click "Add provider" and select "Naver" from the dropdown
5. Configure the provider:
   - **Client ID**: Your Naver application Client ID
   - **Client Secret**: Your Naver application Client Secret
   - **Default Scopes**: `profile email` (automatically set)
   - **Supports External Exchange**: Enabled by default
   - Other settings can be left as default or customized as needed
6. Save the configuration

## User Attribute Mapping

The provider automatically imports the following attributes from Naver:

- **Username**: Set to email
- **Email**: User's email address
- **First Name**: User's name from Naver
- **ID**: Naver user ID (stored in broker context)

### Custom Attribute Mapping

To map additional Naver attributes to Keycloak user attributes:

1. In the Identity Provider settings, go to "Mappers" tab
2. Click "Create"
3. Select "User Attribute" as Mapper Type
4. Configure:
   - **Name**: Choose a descriptive name
   - **Naver Attribute**: Path to the attribute in Naver's response
   - **User Attribute Name**: Keycloak user attribute name

### Available Naver Attributes

The Naver API returns user information in a `response` object with these fields:

- `response.id` - Naver user ID
- `response.email` - Email address
- `response.name` - User's real name
- `response.nickname` - User's nickname
- `response.profile_image` - Profile image URL
- `response.age` - Age range (e.g., "20-29")
- `response.gender` - Gender ("M" or "F")
- `response.birthday` - Birthday (MM-DD format)
- `response.birthyear` - Birth year

Note: Available attributes depend on the permissions configured in your Naver application and user consent.

## External Token Exchange

This provider supports external token exchange, allowing you to:
- Exchange external access tokens for Keycloak tokens
- Validate tokens through Naver's profile endpoint
- Use Naver tokens obtained outside of Keycloak

To use external exchange:
```
POST /realms/{realm}/broker/naver/token
Authorization: Bearer {keycloak-access-token}
Content-Type: application/x-www-form-urlencoded

subject_token={naver-access-token}&subject_token_type=urn:ietf:params:oauth:token-type:access_token
```

## Troubleshooting

### Common Issues

1. **"Authentication failed" error**
   - Verify Client ID and Client Secret are correctly entered
   - Check that your application is not in development mode if testing with other users
   - Ensure the Callback URL matches exactly (including protocol and path)

2. **Missing user information**
   - Check that required permissions are enabled in Naver application settings
   - Verify that users have agreed to share the requested information
   - Some information may require additional Naver verification

3. **"Invalid redirect_uri" error**
   - The Callback URL in Naver must exactly match Keycloak's broker endpoint
   - Check for trailing slashes or URL encoding issues
   - Ensure the protocol is HTTPS in production

4. **Token exchange fails**
   - Verify the Naver access token is valid and not expired
   - Check that the token has the necessary scopes
   - Ensure the Keycloak user has the appropriate permissions for token exchange

### Debug Logging

Enable debug logging for the provider by adding to Keycloak's logging configuration:
```
logger.naver-idp.name=org.scriptonbasestar.kcexts.idp.naver
logger.naver-idp.level=DEBUG
```

## API Endpoints

This provider uses the following Naver API endpoints:
- Authorization: `https://nid.naver.com/oauth2.0/authorize`
- Token: `https://nid.naver.com/oauth2.0/token`
- Profile: `https://openapi.naver.com/v1/nid/me`

## Security Considerations

1. Always use HTTPS for your Keycloak instance
2. Keep your Client Secret secure and never expose it in client-side code
3. Regularly monitor your Naver application for suspicious activity
4. Consider implementing IP whitelist in Naver application settings for additional security
5. Be cautious with birthday and age information due to privacy regulations

## Development vs Production

- In development, your Naver application may have limitations on the number of users
- For production use, you may need to submit your application for Naver's review
- Some permissions may require additional verification from Naver

## References

- [Naver Login API Documentation](https://developers.naver.com/docs/login/api/)
- [Naver Developers Console](https://developers.naver.com/main/)
- [Keycloak Server Developer Guide](https://www.keycloak.org/docs/latest/server_development/)