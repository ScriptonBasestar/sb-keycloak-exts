package org.scriptonbasestar.kcexts.idp.google

object GoogleConstant {
    const val PROVIDER_ID = "google"
    const val PROVIDER_NAME = "Google"

    // OAuth2 Endpoints (Latest v2/v3 APIs)
    const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
    const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    const val USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"
    const val PEOPLE_API_URL = "https://people.googleapis.com/v1/people/me"
    const val JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs"
    
    // Google Admin SDK Endpoints
    const val ADMIN_DIRECTORY_API_BASE = "https://admin.googleapis.com/admin/directory/v1"
    const val ADMIN_USERS_URL = "$ADMIN_DIRECTORY_API_BASE/users"
    const val ADMIN_GROUPS_URL = "$ADMIN_DIRECTORY_API_BASE/groups"
    const val ADMIN_MEMBERS_URL = "$ADMIN_DIRECTORY_API_BASE/groups/{groupKey}/members"
    const val ADMIN_ORGUNIT_URL = "$ADMIN_DIRECTORY_API_BASE/customer/my_customer/orgunits"
    
    // Google Audit API Endpoints
    const val ADMIN_REPORTS_API_BASE = "https://admin.googleapis.com/admin/reports/v1"
    const val AUDIT_ACTIVITIES_URL = "$ADMIN_REPORTS_API_BASE/activity/users/{userKey}/applications/{applicationName}"

    // OAuth2 Scopes
    const val DEFAULT_SCOPE = "openid email profile"
    const val EXTENDED_SCOPE = "openid email profile https://www.googleapis.com/auth/userinfo.profile"
    const val WORKSPACE_SCOPE = "openid email profile https://www.googleapis.com/auth/admin.directory.user.readonly"
    const val FULL_PROFILE_SCOPE = "openid email profile https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/user.phonenumbers.read https://www.googleapis.com/auth/user.addresses.read"
    const val CALENDAR_DRIVE_SCOPE = "openid email profile https://www.googleapis.com/auth/calendar.readonly https://www.googleapis.com/auth/drive.metadata.readonly"
    const val COMPREHENSIVE_SCOPE = "openid email profile https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/user.phonenumbers.read https://www.googleapis.com/auth/user.addresses.read https://www.googleapis.com/auth/calendar.readonly https://www.googleapis.com/auth/drive.metadata.readonly"
    
    // Enterprise Admin Scopes
    const val ADMIN_DIRECTORY_USER_SCOPE = "https://www.googleapis.com/auth/admin.directory.user.readonly"
    const val ADMIN_DIRECTORY_GROUP_SCOPE = "https://www.googleapis.com/auth/admin.directory.group.readonly"
    const val ADMIN_DIRECTORY_ORGUNIT_SCOPE = "https://www.googleapis.com/auth/admin.directory.orgunit.readonly"
    const val ADMIN_REPORTS_AUDIT_SCOPE = "https://www.googleapis.com/auth/admin.reports.audit.readonly"
    
    // Combined Enterprise Scopes
    const val ENTERPRISE_ADMIN_SCOPE = "openid email profile $ADMIN_DIRECTORY_USER_SCOPE $ADMIN_DIRECTORY_GROUP_SCOPE $ADMIN_DIRECTORY_ORGUNIT_SCOPE $ADMIN_REPORTS_AUDIT_SCOPE"
    const val FULL_ENTERPRISE_SCOPE = "$COMPREHENSIVE_SCOPE $ADMIN_DIRECTORY_USER_SCOPE $ADMIN_DIRECTORY_GROUP_SCOPE $ADMIN_DIRECTORY_ORGUNIT_SCOPE $ADMIN_REPORTS_AUDIT_SCOPE"

    // Configuration Keys
    const val HD_PARAMETER = "hd"
    const val WORKSPACE_DOMAIN_KEY = "workspaceDomain"
    const val ENABLE_PKCE_KEY = "enablePKCE"
    const val USE_PEOPLE_API_KEY = "usePeopleAPI"
    const val VERIFY_2FA_KEY = "verify2FA"
    const val REQUIRE_2FA_KEY = "require2FA"
    
    // Security Configuration Keys
    const val ENABLE_RATE_LIMITING_KEY = "enableRateLimiting"
    const val VERIFY_ID_TOKEN_KEY = "verifyIdToken"
    const val ENHANCED_STATE_VALIDATION_KEY = "enhancedStateValidation"
    const val JWKS_CACHE_TTL_KEY = "jwksCacheTtl"
    
    // Profile Mapping Configuration Keys
    const val USE_FULL_PROFILE_KEY = "useFullProfile"
    const val ENABLE_CALENDAR_DRIVE_KEY = "enableCalendarDrive"
    const val MAP_PHONE_NUMBERS_KEY = "mapPhoneNumbers"
    const val MAP_ADDRESSES_KEY = "mapAddresses"
    const val MAP_LOCALE_SETTINGS_KEY = "mapLocaleSettings"
    const val ENHANCED_ORGANIZATION_MAPPING_KEY = "enhancedOrganizationMapping"
    
    // Enterprise Integration Configuration Keys
    const val ENABLE_ADMIN_SDK_KEY = "enableAdminSDK"
    const val SYNC_GROUPS_KEY = "syncGroups"
    const val SYNC_ORGUNIT_KEY = "syncOrgUnit"
    const val ENABLE_AUDIT_LOG_KEY = "enableAuditLog"
    const val REAL_TIME_SYNC_KEY = "realTimeSync"
    const val ADMIN_USER_EMAIL_KEY = "adminUserEmail"
    const val SERVICE_ACCOUNT_DOMAIN_KEY = "serviceAccountDomain"

    // Legacy compatibility
    @Deprecated("Use PROVIDER_ID instead", ReplaceWith("PROVIDER_ID"))
    const val providerId = PROVIDER_ID

    @Deprecated("Use PROVIDER_NAME instead", ReplaceWith("PROVIDER_NAME"))
    const val providerName = PROVIDER_NAME

    @Deprecated("Use AUTH_URL instead", ReplaceWith("AUTH_URL"))
    const val authUrl = AUTH_URL

    @Deprecated("Use TOKEN_URL instead", ReplaceWith("TOKEN_URL"))
    const val tokenUrl = TOKEN_URL

    @Deprecated("Use USERINFO_URL instead", ReplaceWith("USERINFO_URL"))
    const val profileUrl = USERINFO_URL

    @Deprecated("Use DEFAULT_SCOPE instead", ReplaceWith("DEFAULT_SCOPE"))
    const val defaultScope = DEFAULT_SCOPE
}
