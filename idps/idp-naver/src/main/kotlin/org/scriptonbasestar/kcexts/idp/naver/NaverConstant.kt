package org.scriptonbasestar.kcexts.idp.naver

object NaverConstant {
    val providerId = "naver"
    val providerName = "Naver"

    val authUrl = "https://nid.naver.com/oauth2.0/authorize"
    val tokenUrl = "https://nid.naver.com/oauth2.0/token"
    val profileUrl = "https://openapi.naver.com/v1/nid/me"

    val defaultScope = "profile email"
}