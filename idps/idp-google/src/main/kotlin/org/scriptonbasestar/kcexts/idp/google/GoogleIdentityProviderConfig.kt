package org.scriptonbasestar.kcexts.idp.google

import org.keycloak.broker.oidc.OAuth2IdentityProviderConfig
import org.keycloak.models.IdentityProviderModel

class GoogleIdentityProviderConfig : OAuth2IdentityProviderConfig {
    constructor() : super() {
        this.alias = GoogleConstant.PROVIDER_ID
        this.authorizationUrl = GoogleConstant.AUTH_URL
        this.tokenUrl = GoogleConstant.TOKEN_URL
        this.userInfoUrl = GoogleConstant.USERINFO_URL
        this.defaultScope = GoogleConstant.DEFAULT_SCOPE
    }

    constructor(model: IdentityProviderModel) : super(model) {
        this.alias = GoogleConstant.PROVIDER_ID
        this.authorizationUrl = GoogleConstant.AUTH_URL
        this.tokenUrl = GoogleConstant.TOKEN_URL
        this.userInfoUrl = GoogleConstant.USERINFO_URL
        this.defaultScope = GoogleConstant.DEFAULT_SCOPE
    }

    val workspaceDomain: String
        get() = getConfig().getOrDefault(GoogleConstant.WORKSPACE_DOMAIN_KEY, "")

    val isEnablePKCE: Boolean
        get() = getConfig().getOrDefault(GoogleConstant.ENABLE_PKCE_KEY, "false").toBoolean()

    val isUsePeopleAPI: Boolean
        get() = getConfig().getOrDefault(GoogleConstant.USE_PEOPLE_API_KEY, "false").toBoolean()

    val isVerify2FA: Boolean
        get() = getConfig().getOrDefault(GoogleConstant.VERIFY_2FA_KEY, "false").toBoolean()

    val isRequire2FA: Boolean
        get() = getConfig().getOrDefault(GoogleConstant.REQUIRE_2FA_KEY, "false").toBoolean()

    fun setWorkspaceDomain(domain: String) {
        getConfig()[GoogleConstant.WORKSPACE_DOMAIN_KEY] = domain
    }

    fun setEnablePKCE(enable: Boolean) {
        getConfig()[GoogleConstant.ENABLE_PKCE_KEY] = enable.toString()
    }

    fun setUsePeopleAPI(enable: Boolean) {
        getConfig()[GoogleConstant.USE_PEOPLE_API_KEY] = enable.toString()
    }

    fun setVerify2FA(enable: Boolean) {
        getConfig()[GoogleConstant.VERIFY_2FA_KEY] = enable.toString()
    }

    fun setRequire2FA(require: Boolean) {
        getConfig()[GoogleConstant.REQUIRE_2FA_KEY] = require.toString()
    }

    // Security Configuration Properties
    val isEnableRateLimiting: Boolean
        get() = getConfig().getOrDefault(GoogleConstant.ENABLE_RATE_LIMITING_KEY, "true").toBoolean()

    val isVerifyIdToken: Boolean
        get() = getConfig().getOrDefault(GoogleConstant.VERIFY_ID_TOKEN_KEY, "true").toBoolean()

    val isEnhancedStateValidation: Boolean
        get() = getConfig().getOrDefault(GoogleConstant.ENHANCED_STATE_VALIDATION_KEY, "true").toBoolean()

    val jwksCacheTtl: Long
        get() = getConfig().getOrDefault(GoogleConstant.JWKS_CACHE_TTL_KEY, "3600000").toLong() // 1 hour default

    fun setEnableRateLimiting(enable: Boolean) {
        getConfig()[GoogleConstant.ENABLE_RATE_LIMITING_KEY] = enable.toString()
    }

    fun setVerifyIdToken(enable: Boolean) {
        getConfig()[GoogleConstant.VERIFY_ID_TOKEN_KEY] = enable.toString()
    }

    fun setEnhancedStateValidation(enable: Boolean) {
        getConfig()[GoogleConstant.ENHANCED_STATE_VALIDATION_KEY] = enable.toString()
    }

    fun setJwksCacheTtl(ttl: Long) {
        getConfig()[GoogleConstant.JWKS_CACHE_TTL_KEY] = ttl.toString()
    }

    // Profile Mapping Configuration Properties
    val isUseFullProfile: Boolean
        get() = getConfig().getOrDefault(GoogleConstant.USE_FULL_PROFILE_KEY, "false").toBoolean()

    val isEnableCalendarDrive: Boolean
        get() = getConfig().getOrDefault(GoogleConstant.ENABLE_CALENDAR_DRIVE_KEY, "false").toBoolean()

    val isMapPhoneNumbers: Boolean
        get() = getConfig().getOrDefault(GoogleConstant.MAP_PHONE_NUMBERS_KEY, "false").toBoolean()

    val isMapAddresses: Boolean
        get() = getConfig().getOrDefault(GoogleConstant.MAP_ADDRESSES_KEY, "false").toBoolean()

    val isMapLocaleSettings: Boolean
        get() = getConfig().getOrDefault(GoogleConstant.MAP_LOCALE_SETTINGS_KEY, "true").toBoolean()

    val isEnhancedOrganizationMapping: Boolean
        get() = getConfig().getOrDefault(GoogleConstant.ENHANCED_ORGANIZATION_MAPPING_KEY, "true").toBoolean()

    fun setUseFullProfile(enable: Boolean) {
        getConfig()[GoogleConstant.USE_FULL_PROFILE_KEY] = enable.toString()
    }

    fun setEnableCalendarDrive(enable: Boolean) {
        getConfig()[GoogleConstant.ENABLE_CALENDAR_DRIVE_KEY] = enable.toString()
    }

    fun setMapPhoneNumbers(enable: Boolean) {
        getConfig()[GoogleConstant.MAP_PHONE_NUMBERS_KEY] = enable.toString()
    }

    fun setMapAddresses(enable: Boolean) {
        getConfig()[GoogleConstant.MAP_ADDRESSES_KEY] = enable.toString()
    }

    fun setMapLocaleSettings(enable: Boolean) {
        getConfig()[GoogleConstant.MAP_LOCALE_SETTINGS_KEY] = enable.toString()
    }

    fun setEnhancedOrganizationMapping(enable: Boolean) {
        getConfig()[GoogleConstant.ENHANCED_ORGANIZATION_MAPPING_KEY] = enable.toString()
    }

    // Enterprise Integration Configuration Properties
    val isEnableAdminSDK: Boolean
        get() = getConfig().getOrDefault(GoogleConstant.ENABLE_ADMIN_SDK_KEY, "false").toBoolean()

    val isSyncGroups: Boolean
        get() = getConfig().getOrDefault(GoogleConstant.SYNC_GROUPS_KEY, "false").toBoolean()

    val isSyncOrgUnit: Boolean
        get() = getConfig().getOrDefault(GoogleConstant.SYNC_ORGUNIT_KEY, "false").toBoolean()

    val isEnableAuditLog: Boolean
        get() = getConfig().getOrDefault(GoogleConstant.ENABLE_AUDIT_LOG_KEY, "false").toBoolean()

    val isRealTimeSync: Boolean
        get() = getConfig().getOrDefault(GoogleConstant.REAL_TIME_SYNC_KEY, "false").toBoolean()

    val adminUserEmail: String
        get() = getConfig().getOrDefault(GoogleConstant.ADMIN_USER_EMAIL_KEY, "")

    val serviceAccountDomain: String
        get() = getConfig().getOrDefault(GoogleConstant.SERVICE_ACCOUNT_DOMAIN_KEY, "")

    fun setEnableAdminSDK(enable: Boolean) {
        getConfig()[GoogleConstant.ENABLE_ADMIN_SDK_KEY] = enable.toString()
    }

    fun setSyncGroups(enable: Boolean) {
        getConfig()[GoogleConstant.SYNC_GROUPS_KEY] = enable.toString()
    }

    fun setSyncOrgUnit(enable: Boolean) {
        getConfig()[GoogleConstant.SYNC_ORGUNIT_KEY] = enable.toString()
    }

    fun setEnableAuditLog(enable: Boolean) {
        getConfig()[GoogleConstant.ENABLE_AUDIT_LOG_KEY] = enable.toString()
    }

    fun setRealTimeSync(enable: Boolean) {
        getConfig()[GoogleConstant.REAL_TIME_SYNC_KEY] = enable.toString()
    }

    fun setAdminUserEmail(email: String) {
        getConfig()[GoogleConstant.ADMIN_USER_EMAIL_KEY] = email
    }

    fun setServiceAccountDomain(domain: String) {
        getConfig()[GoogleConstant.SERVICE_ACCOUNT_DOMAIN_KEY] = domain
    }
}
