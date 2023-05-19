package org.scriptonbasestar.kctxts.idp.line

object LineConstant {
//    https://developers.line.biz/en/reference/line-login/#userinfo
    val providerId = "line"
    val providerName = "Line"

    val authUrl = "https://access.line.me/oauth2/v2.1/authorize"
    val tokenUrl = "https://api.line.me/oauth2/v2.1/token"
    val profileUrl = "https://api.line.me/v2/profile"

    val defaultScope = "profile openid email"
}