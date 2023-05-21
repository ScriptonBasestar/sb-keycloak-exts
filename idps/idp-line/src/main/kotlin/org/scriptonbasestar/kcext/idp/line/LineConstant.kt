package org.scriptonbasestar.kcext.idp.line

object LineConstant {
//    https://developers.line.biz/en/reference/line-login/#userinfo
    val providerId = "line"
    val providerName = "Line"

    val authorizationUrl = "https://access.line.me/oauth2/v2.1/authorize"
    val tokenUrl = "https://api.line.me/oauth2/v2.1/token"
    val userInfoUrl = "https://api.line.me/oauth2/v2.1/userinfo"
//    val userInfoUrl = "https://api.line.me/v2/profile"

    val defaultScope = "profile openid email"
}
