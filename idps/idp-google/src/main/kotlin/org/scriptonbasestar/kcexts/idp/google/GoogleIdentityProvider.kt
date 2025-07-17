package org.scriptonbasestar.kcexts.idp.google

import com.fasterxml.jackson.databind.JsonNode
import org.jboss.logging.Logger
import org.keycloak.broker.oidc.AbstractOAuth2IdentityProvider
import org.keycloak.broker.provider.BrokeredIdentityContext
import org.keycloak.broker.provider.IdentityBrokerException
import org.keycloak.broker.provider.util.SimpleHttp
import org.keycloak.broker.social.SocialIdentityProvider
import org.keycloak.models.KeycloakSession
import org.keycloak.util.JsonSerialization
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class GoogleIdentityProvider(
    keycloakSession: KeycloakSession,
    config: GoogleIdentityProviderConfig,
) : AbstractOAuth2IdentityProvider<GoogleIdentityProviderConfig>(
        keycloakSession,
        config,
    ),
    SocialIdentityProvider<GoogleIdentityProviderConfig> {
    companion object {
        private val logger = Logger.getLogger(GoogleIdentityProvider::class.java)
        
        // Security enhancements
        private val jwksCache = ConcurrentHashMap<String, RSAPublicKey>()
        private val jwksCacheExpiry = ConcurrentHashMap<String, Long>()
        private val rateLimitMap = ConcurrentHashMap<String, RateLimitInfo>()
        private val JWKS_CACHE_TTL = TimeUnit.HOURS.toMillis(1) // 1 hour cache
        private val RATE_LIMIT_WINDOW = TimeUnit.MINUTES.toMillis(1) // 1 minute window
        private val MAX_REQUESTS_PER_MINUTE = 60
        
        private data class RateLimitInfo(
            var requestCount: Int = 0,
            var windowStart: Long = System.currentTimeMillis()
        )
    }

    override fun getDefaultScopes(): String =
        when {
            config.isEnableAdminSDK && config.isUseFullProfile -> GoogleConstant.FULL_ENTERPRISE_SCOPE
            config.isEnableAdminSDK -> GoogleConstant.ENTERPRISE_ADMIN_SCOPE
            config.isEnableCalendarDrive && config.isUseFullProfile -> GoogleConstant.COMPREHENSIVE_SCOPE
            config.isEnableCalendarDrive -> GoogleConstant.CALENDAR_DRIVE_SCOPE
            config.isUseFullProfile -> GoogleConstant.FULL_PROFILE_SCOPE
            config.isUsePeopleAPI -> GoogleConstant.EXTENDED_SCOPE
            config.workspaceDomain.isNotBlank() -> GoogleConstant.WORKSPACE_SCOPE
            else -> GoogleConstant.DEFAULT_SCOPE
        }

    override fun getFederatedIdentity(response: String): BrokeredIdentityContext {
        // Rate limiting check (if enabled)
        if (config.isEnableRateLimiting) {
            checkRateLimit()
        }
        
        // Enhanced state parameter validation (if enabled)
        if (config.isEnhancedStateValidation) {
            validateStateParameter()
        }
        
        val accessToken =
            extractTokenFromResponse(response, getAccessTokenResponseParameter())
                ?: throw IdentityBrokerException("No access token available")

        // Extract and verify ID token if present and enabled
        val idToken = extractTokenFromResponse(response, "id_token")
        if (idToken != null && config.isVerifyIdToken) {
            verifyIdToken(idToken)
        }

        val context = doGetFederatedIdentity(accessToken)

        // Verify hosted domain if configured
        val workspaceDomain = config.workspaceDomain
        if (workspaceDomain.isNotBlank()) {
            verifyHostedDomain(context, workspaceDomain)
        }

        // Check 2FA status if enabled
        if (config.isVerify2FA) {
            verify2FAStatus(context, accessToken)
        }

        return context
    }

    override fun doGetFederatedIdentity(accessToken: String): BrokeredIdentityContext =
        try {
            val profileUrl =
                if (config.isUsePeopleAPI) {
                    buildPeopleApiUrl()
                } else {
                    GoogleConstant.USERINFO_URL
                }

            val profile =
                SimpleHttp
                    .doGet(profileUrl, session)
                    .header("Authorization", "Bearer $accessToken")
                    .asJson()

            logger.debugf("Google profile response: %s", profile.toString())

            val userId =
                getJsonProperty(
                    profile,
                    "sub",
                ) ?: getJsonProperty(profile, "id") ?: throw IdentityBrokerException("No user ID found in profile")
            val context = BrokeredIdentityContext(userId, config)

            // Extract user information
            context.username = getJsonProperty(profile, "email")
            context.email = getJsonProperty(profile, "email")
            context.firstName = getJsonProperty(profile, "given_name") ?: extractFirstName(profile)
            context.lastName = getJsonProperty(profile, "family_name") ?: extractLastName(profile)

            // Set name property
            val displayName = getJsonProperty(profile, "name") ?: extractDisplayName(profile)
            if (displayName != null) {
                context.contextData["name"] = displayName
            }

            // Set profile picture with high resolution if available
            val pictureUrl =
                getJsonProperty(profile, "picture")
                    ?: extractProfilePicture(profile)
            if (pictureUrl != null) {
                context.contextData["picture"] = enhancePictureUrl(pictureUrl)
            }

            // Add Google-specific attributes
            context.contextData["provider"] = GoogleConstant.PROVIDER_ID
            context.contextData["verified_email"] = getJsonProperty(profile, "email_verified") ?: "true"

            // Extract locale and timezone if available
            getJsonProperty(profile, "locale")?.let {
                context.contextData["locale"] = it
            }

            // Extract organization info if using People API
            if (config.isUsePeopleAPI) {
                extractOrganizationInfo(profile, context)
                
                // Enhanced profile mapping
                if (config.isMapPhoneNumbers) {
                    extractPhoneNumbers(profile, context)
                }
                
                if (config.isMapAddresses) {
                    extractAddresses(profile, context)
                }
                
                if (config.isMapLocaleSettings) {
                    extractEnhancedLocaleSettings(profile, context)
                }
                
                // Enterprise integration features
                if (config.isEnableAdminSDK) {
                    performEnterpriseIntegration(context, accessToken)
                }
            }

            context
        } catch (e: IOException) {
            logger.error("Failed to fetch Google user profile", e)
            throw IdentityBrokerException("Could not obtain user profile from Google", e)
        }

    private fun verifyHostedDomain(
        context: BrokeredIdentityContext,
        expectedDomain: String,
    ) {
        val email = context.email
        if (email == null || !email.endsWith("@$expectedDomain")) {
            throw IdentityBrokerException(
                "User email domain '${email?.substringAfter("@")}' does not match required domain '$expectedDomain'",
            )
        }
        context.contextData["hd"] = expectedDomain
        logger.debugf("Verified hosted domain: %s for user: %s", expectedDomain, email)
    }

    private fun verify2FAStatus(
        context: BrokeredIdentityContext,
        accessToken: String,
    ) {
        try {
            // Call Google's tokeninfo endpoint to check for 2FA
            val tokenInfo =
                SimpleHttp
                    .doGet("https://www.googleapis.com/oauth2/v1/tokeninfo?access_token=$accessToken", session)
                    .asJson()

            val scope = getJsonProperty(tokenInfo, "scope") ?: ""
            val has2FA = scope.contains("https://www.googleapis.com/auth/userinfo.profile")

            context.contextData["two_factor_enabled"] = has2FA.toString()

            if (!has2FA && config.isRequire2FA) {
                throw IdentityBrokerException("Two-factor authentication is required but not enabled for this account")
            }

            logger.debugf("2FA verification: %s for user: %s", has2FA, context.email)
        } catch (e: Exception) {
            logger.warn("Could not verify 2FA status", e)
            // Don't fail authentication, just log the warning
        }
    }

    private fun extractFirstName(profile: JsonNode): String? {
        // Try People API format first
        val names = profile.get("names")
        if (names?.isArray == true && names.size() > 0) {
            return names[0].get("givenName")?.asText()
        }
        return null
    }

    private fun extractLastName(profile: JsonNode): String? {
        // Try People API format first
        val names = profile.get("names")
        if (names?.isArray == true && names.size() > 0) {
            return names[0].get("familyName")?.asText()
        }
        return null
    }

    private fun extractDisplayName(profile: JsonNode): String? {
        // Try People API format first
        val names = profile.get("names")
        if (names?.isArray == true && names.size() > 0) {
            return names[0].get("displayName")?.asText()
        }
        return null
    }

    private fun extractProfilePicture(profile: JsonNode): String? {
        // Try People API format first
        val photos = profile.get("photos")
        if (photos?.isArray == true && photos.size() > 0) {
            return photos[0].get("url")?.asText()
        }
        return null
    }

    private fun extractOrganizationInfo(
        profile: JsonNode,
        context: BrokeredIdentityContext,
    ) {
        val organizations = profile.get("organizations")
        if (organizations?.isArray == true && organizations.size() > 0) {
            val org = organizations[0]
            
            // Basic organization info
            org.get("name")?.asText()?.let { context.contextData["organization"] = it }
            org.get("title")?.asText()?.let { context.contextData["job_title"] = it }
            org.get("department")?.asText()?.let { context.contextData["department"] = it }
            
            // Enhanced organization mapping if enabled
            if (config.isEnhancedOrganizationMapping) {
                org.get("type")?.asText()?.let { context.contextData["organization_type"] = it }
                org.get("location")?.asText()?.let { context.contextData["work_location"] = it }
                org.get("domain")?.asText()?.let { context.contextData["organization_domain"] = it }
                org.get("startDate")?.asText()?.let { context.contextData["employment_start_date"] = it }
                org.get("endDate")?.asText()?.let { context.contextData["employment_end_date"] = it }
                org.get("current")?.asBoolean()?.let { context.contextData["current_employment"] = it.toString() }
                
                // Extract all organizations if multiple
                if (organizations.size() > 1) {
                    val orgList = mutableListOf<String>()
                    for (orgItem in organizations) {
                        val orgName = orgItem.get("name")?.asText()
                        val orgTitle = orgItem.get("title")?.asText()
                        if (orgName != null) {
                            orgList.add(if (orgTitle != null) "$orgName ($orgTitle)" else orgName)
                        }
                    }
                    if (orgList.isNotEmpty()) {
                        context.contextData["all_organizations"] = orgList.joinToString("; ")
                    }
                }
            }
        }
    }

    private fun enhancePictureUrl(originalUrl: String): String {
        // Enhance Google profile picture URL for higher resolution
        return when {
            originalUrl.contains("?sz=") -> originalUrl.replace(Regex("\\?sz=\\d+"), "?sz=400")
            originalUrl.contains("=s") -> originalUrl.replace(Regex("=s\\d+"), "=s400")
            else -> "$originalUrl?sz=400"
        }
    }

    private fun generatePKCEParams(): PKCEParams {
        val secureRandom = SecureRandom()
        val codeVerifier =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(ByteArray(32).also { secureRandom.nextBytes(it) })

        val bytes = codeVerifier.toByteArray(StandardCharsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        val codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash)

        return PKCEParams(codeVerifier, codeChallenge)
    }

    private data class PKCEParams(
        val codeVerifier: String,
        val codeChallenge: String,
    )

    // Enhanced Profile Mapping Methods
    
    private fun buildPeopleApiUrl(): String {
        val baseFields = mutableListOf(
            "names", "emailAddresses", "photos", "organizations", "locales"
        )
        
        if (config.isMapPhoneNumbers) {
            baseFields.add("phoneNumbers")
        }
        
        if (config.isMapAddresses) {
            baseFields.add("addresses")
        }
        
        if (config.isUseFullProfile) {
            baseFields.addAll(listOf(
                "birthdays", "genders", "urls", "biographies",
                "occupations", "skills", "interests"
            ))
        }
        
        return "${GoogleConstant.PEOPLE_API_URL}?personFields=${baseFields.joinToString(",")}"
    }
    
    private fun extractPhoneNumbers(profile: JsonNode, context: BrokeredIdentityContext) {
        val phoneNumbers = profile.get("phoneNumbers")
        if (phoneNumbers?.isArray == true && phoneNumbers.size() > 0) {
            val phoneList = mutableListOf<String>()
            
            for (phone in phoneNumbers) {
                val value = phone.get("value")?.asText()
                val type = phone.get("type")?.asText()
                
                if (value != null) {
                    phoneList.add(if (type != null) "$type: $value" else value)
                }
            }
            
            if (phoneList.isNotEmpty()) {
                context.contextData["phone_numbers"] = phoneList.joinToString("; ")
                // Set primary phone if available
                if (phoneNumbers.size() > 0) {
                    val primaryPhone = phoneNumbers[0].get("value")?.asText()
                    if (primaryPhone != null) {
                        context.contextData["phone"] = primaryPhone
                    }
                }
            }
        }
    }
    
    private fun extractAddresses(profile: JsonNode, context: BrokeredIdentityContext) {
        val addresses = profile.get("addresses")
        if (addresses?.isArray == true && addresses.size() > 0) {
            val addressList = mutableListOf<String>()
            
            for (address in addresses) {
                val formattedValue = address.get("formattedValue")?.asText()
                val type = address.get("type")?.asText()
                
                if (formattedValue != null) {
                    addressList.add(if (type != null) "$type: $formattedValue" else formattedValue)
                }
            }
            
            if (addressList.isNotEmpty()) {
                context.contextData["addresses"] = addressList.joinToString("; ")
                
                // Extract structured address components for primary address
                if (addresses.size() > 0) {
                    val primaryAddress = addresses[0]
                    primaryAddress.get("streetAddress")?.asText()?.let { 
                        context.contextData["street_address"] = it 
                    }
                    primaryAddress.get("city")?.asText()?.let { 
                        context.contextData["city"] = it 
                    }
                    primaryAddress.get("region")?.asText()?.let { 
                        context.contextData["region"] = it 
                    }
                    primaryAddress.get("postalCode")?.asText()?.let { 
                        context.contextData["postal_code"] = it 
                    }
                    primaryAddress.get("country")?.asText()?.let { 
                        context.contextData["country"] = it 
                    }
                }
            }
        }
    }
    
    private fun extractEnhancedLocaleSettings(profile: JsonNode, context: BrokeredIdentityContext) {
        // Extract locale information from People API
        val locales = profile.get("locales")
        if (locales?.isArray == true && locales.size() > 0) {
            for (locale in locales) {
                val value = locale.get("value")?.asText()
                if (value != null) {
                    context.contextData["preferred_locale"] = value
                    
                    // Parse locale components
                    try {
                        val parts = value.split("-", "_")
                        if (parts.isNotEmpty()) {
                            context.contextData["language"] = parts[0]
                        }
                        if (parts.size > 1) {
                            context.contextData["country_code"] = parts[1]
                        }
                    } catch (e: Exception) {
                        logger.debugf("Failed to parse locale: %s", value)
                    }
                    break // Use first locale
                }
            }
        }
        
        // Set timezone if available (from organization or inferred)
        context.contextData["timezone"] = inferTimezone(context)
    }
    
    private fun inferTimezone(context: BrokeredIdentityContext): String {
        // Try to infer timezone from country code or region
        val countryCode = context.contextData["country_code"] as? String
        val country = context.contextData["country"] as? String
        val region = context.contextData["region"] as? String
        
        return when {
            countryCode == "US" || country?.contains("United States") == true -> {
                when {
                    region?.contains("Pacific") == true || region?.contains("California") == true -> "America/Los_Angeles"
                    region?.contains("Mountain") == true || region?.contains("Denver") == true -> "America/Denver"
                    region?.contains("Central") == true || region?.contains("Chicago") == true -> "America/Chicago"
                    else -> "America/New_York"
                }
            }
            countryCode == "GB" || country?.contains("United Kingdom") == true -> "Europe/London"
            countryCode == "DE" || country?.contains("Germany") == true -> "Europe/Berlin"
            countryCode == "FR" || country?.contains("France") == true -> "Europe/Paris"
            countryCode == "JP" || country?.contains("Japan") == true -> "Asia/Tokyo"
            countryCode == "KR" || country?.contains("Korea") == true -> "Asia/Seoul"
            countryCode == "CN" || country?.contains("China") == true -> "Asia/Shanghai"
            countryCode == "IN" || country?.contains("India") == true -> "Asia/Kolkata"
            countryCode == "AU" || country?.contains("Australia") == true -> "Australia/Sydney"
            countryCode == "CA" || country?.contains("Canada") == true -> "America/Toronto"
            else -> "UTC"
        }
    }

    // Enterprise Integration Methods
    
    private fun performEnterpriseIntegration(context: BrokeredIdentityContext, accessToken: String) {
        try {
            // Sync Google Groups membership
            if (config.isSyncGroups) {
                syncUserGroups(context, accessToken)
            }
            
            // Sync organizational unit information
            if (config.isSyncOrgUnit) {
                syncOrganizationalUnit(context, accessToken)
            }
            
            // Fetch user account status and sync
            if (config.isRealTimeSync) {
                syncAccountStatus(context, accessToken)
            }
            
            // Collect audit logs if enabled
            if (config.isEnableAuditLog) {
                collectAuditLogs(context, accessToken)
            }
            
        } catch (e: Exception) {
            logger.error("Enterprise integration failed", e)
            // Don't fail authentication, just log the error
        }
    }
    
    private fun syncUserGroups(context: BrokeredIdentityContext, accessToken: String) {
        try {
            val userEmail = context.email ?: return
            
            // Get user's group memberships from Admin SDK
            val groupsUrl = "${GoogleConstant.ADMIN_GROUPS_URL}?domain=${config.serviceAccountDomain}&userKey=$userEmail"
            val groupsResponse = SimpleHttp.doGet(groupsUrl, session)
                .header("Authorization", "Bearer $accessToken")
                .asJson()
            
            val groups = groupsResponse.get("groups")
            if (groups?.isArray == true && groups.size() > 0) {
                val groupNames = mutableListOf<String>()
                val groupEmails = mutableListOf<String>()
                
                for (group in groups) {
                    val groupName = group.get("name")?.asText()
                    val groupEmail = group.get("email")?.asText()
                    val groupDescription = group.get("description")?.asText()
                    
                    if (groupName != null) {
                        groupNames.add(groupName)
                    }
                    if (groupEmail != null) {
                        groupEmails.add(groupEmail)
                    }
                    
                    // Add detailed group info
                    if (groupName != null && groupEmail != null) {
                        context.contextData["group_${groupEmail}"] = mapOf(
                            "name" to groupName,
                            "email" to groupEmail,
                            "description" to (groupDescription ?: ""),
                            "type" to (group.get("type")?.asText() ?: ""),
                            "adminCreated" to (group.get("adminCreated")?.asBoolean()?.toString() ?: "false")
                        ).toString()
                    }
                }
                
                if (groupNames.isNotEmpty()) {
                    context.contextData["google_groups"] = groupNames.joinToString(",")
                    context.contextData["google_group_emails"] = groupEmails.joinToString(",")
                    context.contextData["google_groups_count"] = groups.size().toString()
                }
            }
            
            logger.debugf("Synced %d groups for user: %s", groups?.size() ?: 0, userEmail)
            
        } catch (e: Exception) {
            logger.warn("Failed to sync user groups", e)
        }
    }
    
    private fun syncOrganizationalUnit(context: BrokeredIdentityContext, accessToken: String) {
        try {
            val userEmail = context.email ?: return
            
            // Get user details including organizational unit
            val userUrl = "${GoogleConstant.ADMIN_USERS_URL}/$userEmail?projection=full"
            val userResponse = SimpleHttp.doGet(userUrl, session)
                .header("Authorization", "Bearer $accessToken")
                .asJson()
            
            // Extract organizational unit path
            val orgUnitPath = userResponse.get("orgUnitPath")?.asText()
            if (orgUnitPath != null) {
                context.contextData["org_unit_path"] = orgUnitPath
                
                // Get detailed org unit information
                val orgUnitUrl = "${GoogleConstant.ADMIN_ORGUNIT_URL}?orgUnitPath=${orgUnitPath.replace("/", "%2F")}"
                val orgUnitResponse = SimpleHttp.doGet(orgUnitUrl, session)
                    .header("Authorization", "Bearer $accessToken")
                    .asJson()
                
                val orgUnit = orgUnitResponse.get("organizationUnits")?.get(0)
                if (orgUnit != null) {
                    orgUnit.get("name")?.asText()?.let { context.contextData["org_unit_name"] = it }
                    orgUnit.get("description")?.asText()?.let { context.contextData["org_unit_description"] = it }
                    orgUnit.get("parentOrgUnitPath")?.asText()?.let { context.contextData["parent_org_unit_path"] = it }
                }
            }
            
            // Extract additional user properties
            userResponse.get("suspended")?.asBoolean()?.let { 
                context.contextData["account_suspended"] = it.toString() 
            }
            userResponse.get("isAdmin")?.asBoolean()?.let { 
                context.contextData["is_admin"] = it.toString() 
            }
            userResponse.get("isDelegatedAdmin")?.asBoolean()?.let { 
                context.contextData["is_delegated_admin"] = it.toString() 
            }
            userResponse.get("lastLoginTime")?.asText()?.let { 
                context.contextData["last_login_time"] = it 
            }
            userResponse.get("creationTime")?.asText()?.let { 
                context.contextData["account_creation_time"] = it 
            }
            
            logger.debugf("Synced organizational unit for user: %s, org unit: %s", userEmail, orgUnitPath)
            
        } catch (e: Exception) {
            logger.warn("Failed to sync organizational unit", e)
        }
    }
    
    private fun syncAccountStatus(context: BrokeredIdentityContext, accessToken: String) {
        try {
            val userEmail = context.email ?: return
            
            // Get real-time account status
            val userUrl = "${GoogleConstant.ADMIN_USERS_URL}/$userEmail?projection=basic"
            val userResponse = SimpleHttp.doGet(userUrl, session)
                .header("Authorization", "Bearer $accessToken")
                .asJson()
            
            val suspended = userResponse.get("suspended")?.asBoolean() ?: false
            val suspensionReason = userResponse.get("suspensionReason")?.asText()
            val changePasswordAtNextLogin = userResponse.get("changePasswordAtNextLogin")?.asBoolean() ?: false
            
            // Update context with real-time status
            context.contextData["real_time_suspended"] = suspended.toString()
            context.contextData["account_active"] = (!suspended).toString()
            
            if (suspensionReason != null) {
                context.contextData["suspension_reason"] = suspensionReason
            }
            
            context.contextData["password_change_required"] = changePasswordAtNextLogin.toString()
            context.contextData["sync_timestamp"] = System.currentTimeMillis().toString()
            
            // Throw exception if account is suspended and suspension should block access
            if (suspended) {
                logger.warnf("User account is suspended: %s, reason: %s", userEmail, suspensionReason)
                throw IdentityBrokerException("Google Workspace account is suspended: ${suspensionReason ?: "Unknown reason"}")
            }
            
            logger.debugf("Account status synced for user: %s, active: %s", userEmail, !suspended)
            
        } catch (e: Exception) {
            if (e is IdentityBrokerException) {
                throw e // Re-throw suspension exceptions
            }
            logger.warn("Failed to sync account status", e)
        }
    }
    
    private fun collectAuditLogs(context: BrokeredIdentityContext, accessToken: String) {
        try {
            val userEmail = context.email ?: return
            
            // Collect recent login activities
            val auditUrl = GoogleConstant.AUDIT_ACTIVITIES_URL
                .replace("{userKey}", userEmail)
                .replace("{applicationName}", "login") + "?maxResults=10"
            
            val auditResponse = SimpleHttp.doGet(auditUrl, session)
                .header("Authorization", "Bearer $accessToken")
                .asJson()
            
            val activities = auditResponse.get("items")
            if (activities?.isArray == true && activities.size() > 0) {
                val recentActivities = mutableListOf<String>()
                
                for (activity in activities) {
                    val eventTime = activity.get("id")?.get("time")?.asText()
                    val eventName = activity.get("events")?.get(0)?.get("name")?.asText()
                    val ipAddress = activity.get("ipAddress")?.asText()
                    
                    if (eventTime != null && eventName != null) {
                        recentActivities.add("$eventTime: $eventName" + if (ipAddress != null) " from $ipAddress" else "")
                    }
                }
                
                if (recentActivities.isNotEmpty()) {
                    context.contextData["recent_audit_activities"] = recentActivities.joinToString("; ")
                    context.contextData["audit_activities_count"] = activities.size().toString()
                }
                
                // Extract first and last login info
                if (activities.size() > 0) {
                    val latestActivity = activities[0]
                    latestActivity.get("id")?.get("time")?.asText()?.let {
                        context.contextData["latest_activity_time"] = it
                    }
                    latestActivity.get("ipAddress")?.asText()?.let {
                        context.contextData["latest_activity_ip"] = it
                    }
                }
            }
            
            logger.debugf("Collected %d audit log entries for user: %s", activities?.size() ?: 0, userEmail)
            
        } catch (e: Exception) {
            logger.warn("Failed to collect audit logs", e)
        }
    }

    // Security Enhancement Methods
    
    private fun checkRateLimit() {
        val clientIp = getClientIpAddress()
        val currentTime = System.currentTimeMillis()
        
        val rateLimitInfo = rateLimitMap.computeIfAbsent(clientIp) { RateLimitInfo() }
        
        synchronized(rateLimitInfo) {
            // Reset counter if window has passed
            if (currentTime - rateLimitInfo.windowStart > RATE_LIMIT_WINDOW) {
                rateLimitInfo.requestCount = 0
                rateLimitInfo.windowStart = currentTime
            }
            
            rateLimitInfo.requestCount++
            
            if (rateLimitInfo.requestCount > MAX_REQUESTS_PER_MINUTE) {
                logger.warnf("Rate limit exceeded for IP: %s", clientIp)
                throw IdentityBrokerException("Rate limit exceeded. Please try again later.")
            }
        }
    }
    
    private fun getClientIpAddress(): String {
        // Try to get real client IP from headers
        val headers = listOf("X-Forwarded-For", "X-Real-IP", "X-Forwarded-Proto")
        for (header in headers) {
            val value = session.getContext().getRequestHeaders().getHeaderString(header)
            if (!value.isNullOrBlank()) {
                return value.split(",")[0].trim()
            }
        }
        return session.getContext().getConnection().remoteAddr ?: "unknown"
    }
    
    private fun validateStateParameter() {
        // Enhanced state parameter validation
        try {
            val stateParam = session.getContext().getUri().getQueryParameters()["state"]?.firstOrNull()
            if (stateParam.isNullOrBlank()) {
                logger.debugf("Missing state parameter - skipping validation")
                return
            }
            
            val storedState = session.getAttribute("oauth_state") as? String
            if (storedState.isNullOrBlank()) {
                logger.debugf("No stored state found - skipping validation")
                return
            }
            
            // Verify state parameter matches stored value
            if (stateParam != storedState) {
                logger.warnf("State parameter mismatch. Expected: %s, Got: %s", storedState, stateParam)
                throw IdentityBrokerException("Invalid state parameter - possible CSRF attack")
            }
            
            // Clean up stored state
            session.removeAttribute("oauth_state")
            logger.debugf("State parameter validation successful")
            
        } catch (e: Exception) {
            logger.warn("State parameter validation failed", e)
            // Don't fail the entire authentication flow for state validation issues
        }
    }
    
    private fun verifyIdToken(idToken: String) {
        try {
            val parts = idToken.split(".")
            if (parts.size != 3) {
                throw IdentityBrokerException("Invalid ID token format")
            }
            
            // Decode header and payload
            val headerJson = String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8)
            val payloadJson = String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
            
            val header = JsonSerialization.readValue(headerJson, JsonNode::class.java)
            val payload = JsonSerialization.readValue(payloadJson, JsonNode::class.java)
            
            // Verify signature
            val keyId = header.get("kid")?.asText()
            if (keyId.isNullOrBlank()) {
                throw IdentityBrokerException("Missing key ID in ID token header")
            }
            
            val publicKey = getPublicKey(keyId)
            if (!verifySignature(parts[0] + "." + parts[1], parts[2], publicKey)) {
                throw IdentityBrokerException("ID token signature verification failed")
            }
            
            // Verify claims
            verifyTokenClaims(payload)
            
            logger.debugf("ID token verified successfully")
            
        } catch (e: Exception) {
            logger.error("ID token verification failed", e)
            throw IdentityBrokerException("ID token verification failed", e)
        }
    }
    
    private fun getPublicKey(keyId: String): RSAPublicKey {
        val currentTime = System.currentTimeMillis()
        
        // Check cache first
        val cachedKey = jwksCache[keyId]
        val cacheExpiry = jwksCacheExpiry[keyId]
        
        if (cachedKey != null && cacheExpiry != null && currentTime < cacheExpiry) {
            return cachedKey
        }
        
        // Fetch from JWKS endpoint
        try {
            val jwksResponse = SimpleHttp.doGet(GoogleConstant.JWKS_URL, session).asJson()
            val keys = jwksResponse.get("keys")
            
            if (keys?.isArray == true) {
                for (key in keys) {
                    val kid = key.get("kid")?.asText()
                    if (kid == keyId) {
                        val nValue = key.get("n")?.asText()
                        val eValue = key.get("e")?.asText()
                        
                        if (nValue != null && eValue != null) {
                            val publicKey = createRSAPublicKey(nValue, eValue)
                            
                            // Cache the key
                            jwksCache[keyId] = publicKey
                            jwksCacheExpiry[keyId] = currentTime + config.jwksCacheTtl
                            
                            return publicKey
                        }
                    }
                }
            }
            
            throw IdentityBrokerException("Public key not found for key ID: $keyId")
            
        } catch (e: Exception) {
            logger.error("Failed to fetch JWKS", e)
            throw IdentityBrokerException("Failed to fetch Google public keys", e)
        }
    }
    
    private fun createRSAPublicKey(nValue: String, eValue: String): RSAPublicKey {
        val nBytes = Base64.getUrlDecoder().decode(nValue)
        val eBytes = Base64.getUrlDecoder().decode(eValue)
        
        val modulus = java.math.BigInteger(1, nBytes)
        val exponent = java.math.BigInteger(1, eBytes)
        
        val keySpec = java.security.spec.RSAPublicKeySpec(modulus, exponent)
        val keyFactory = java.security.KeyFactory.getInstance("RSA")
        
        return keyFactory.generatePublic(keySpec) as RSAPublicKey
    }
    
    private fun verifySignature(data: String, signature: String, publicKey: RSAPublicKey): Boolean {
        return try {
            val signatureBytes = Base64.getUrlDecoder().decode(signature)
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(publicKey)
            verifier.update(data.toByteArray(StandardCharsets.UTF_8))
            verifier.verify(signatureBytes)
        } catch (e: Exception) {
            logger.error("Signature verification failed", e)
            false
        }
    }
    
    private fun verifyTokenClaims(payload: JsonNode) {
        // Verify issuer
        val issuer = payload.get("iss")?.asText()
        if (issuer != "https://accounts.google.com") {
            throw IdentityBrokerException("Invalid issuer: $issuer")
        }
        
        // Verify audience (client ID)
        val audience = payload.get("aud")?.asText()
        if (audience != config.clientId) {
            throw IdentityBrokerException("Invalid audience: $audience")
        }
        
        // Verify expiration
        val exp = payload.get("exp")?.asLong()
        if (exp == null || Instant.now().epochSecond >= exp) {
            throw IdentityBrokerException("Token has expired")
        }
        
        // Verify not before
        val nbf = payload.get("nbf")?.asLong()
        if (nbf != null && Instant.now().epochSecond < nbf) {
            throw IdentityBrokerException("Token not yet valid")
        }
        
        // Verify issued at (allow some clock skew)
        val iat = payload.get("iat")?.asLong()
        if (iat != null && Instant.now().epochSecond - iat > 3600) { // 1 hour max age
            throw IdentityBrokerException("Token issued too long ago")
        }
    }
}
