package org.scriptonbasestar.kctxts.idp.kakao

object KakaoConstant {
    // https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api
    val providerId = "kakao"
    val providerName = "Kakao"

    val authUrl = "https://kauth.kakao.com/oauth/authorize"
    val tokenUrl = "https://kauth.kakao.com/oauth/token"
    val profileUrl = "https://kapi.kakao.com/v2/user/me"

    val defaultScope = "profile_image openid profile_nickname"
}
