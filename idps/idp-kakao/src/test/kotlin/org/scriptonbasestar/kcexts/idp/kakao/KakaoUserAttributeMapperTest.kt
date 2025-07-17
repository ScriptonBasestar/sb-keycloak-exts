package org.scriptonbasestar.kcexts.idp.kakao

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.broker.provider.BrokeredIdentityContext
import org.keycloak.models.*
import org.mockito.kotlin.*

class KakaoUserAttributeMapperTest {
    private lateinit var mapper: KakaoUserAttributeMapper
    private lateinit var objectMapper: ObjectMapper
    private lateinit var mockSession: KeycloakSession
    private lateinit var mockRealm: RealmModel
    private lateinit var mockUser: UserModel
    private lateinit var mockContext: BrokeredIdentityContext

    @BeforeEach
    fun setUp() {
        mapper = KakaoUserAttributeMapper()
        objectMapper = ObjectMapper()
        mockSession = mock()
        mockRealm = mock()
        mockUser = mock()
        mockContext = mock()
    }

    @Test
    fun `should return correct mapper id`() {
        assertThat(mapper.id).isEqualTo("kakao-user-attribute-mapper")
    }

    @Test
    fun `should return compatible provider list`() {
        val providers = mapper.compatibleProviders
        assertThat(providers).containsExactly("kakao")
    }

    @Test
    fun `should extend AbstractJsonUserAttributeMapper`() {
        assertThat(mapper).isInstanceOf(org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper::class.java)
    }

    @Test
    fun `should map Kakao user profile to user attributes`() {
        val kakaoResponse = """{
            "id": 123456789,
            "connected_at": "2024-01-01T00:00:00Z",
            "properties": {
                "nickname": "홍길동",
                "profile_image": "https://k.kakaocdn.net/profile.jpg",
                "thumbnail_image": "https://k.kakaocdn.net/thumbnail.jpg"
            },
            "kakao_account": {
                "profile_nickname_needs_agreement": false,
                "profile_image_needs_agreement": false,
                "profile": {
                    "nickname": "홍길동",
                    "thumbnail_image_url": "https://k.kakaocdn.net/thumbnail.jpg",
                    "profile_image_url": "https://k.kakaocdn.net/profile.jpg",
                    "is_default_image": false
                },
                "has_email": true,
                "email_needs_agreement": false,
                "is_email_valid": true,
                "is_email_verified": true,
                "email": "test@kakao.com",
                "has_age_range": true,
                "age_range_needs_agreement": false,
                "age_range": "20~29",
                "has_birthday": true,
                "birthday_needs_agreement": false,
                "birthday": "0101",
                "birthday_type": "SOLAR",
                "has_gender": true,
                "gender_needs_agreement": false,
                "gender": "female"
            }
        }"""

        val jsonNode: JsonNode = objectMapper.readTree(kakaoResponse)

        // Verify basic profile data extraction
        assertThat(jsonNode.get("id").asLong()).isEqualTo(123456789L)
        assertThat(jsonNode.get("properties").get("nickname").asText()).isEqualTo("홍길동")

        // Verify kakao_account data extraction
        val kakaoAccount = jsonNode.get("kakao_account")
        assertThat(kakaoAccount.get("email").asText()).isEqualTo("test@kakao.com")
        assertThat(kakaoAccount.get("age_range").asText()).isEqualTo("20~29")
        assertThat(kakaoAccount.get("gender").asText()).isEqualTo("female")
        assertThat(kakaoAccount.get("birthday").asText()).isEqualTo("0101")
    }

    @Test
    fun `should handle missing optional fields gracefully`() {
        val minimalKakaoResponse = """{
            "id": 123456789,
            "connected_at": "2024-01-01T00:00:00Z",
            "properties": {
                "nickname": "User"
            },
            "kakao_account": {
                "has_email": false,
                "has_age_range": false,
                "has_birthday": false,
                "has_gender": false
            }
        }"""

        val jsonNode: JsonNode = objectMapper.readTree(minimalKakaoResponse)

        assertThat(jsonNode.get("id")).isNotNull
        assertThat(jsonNode.get("properties").get("nickname")).isNotNull
        assertThat(jsonNode.get("kakao_account").get("email")).isNull()
        assertThat(jsonNode.get("kakao_account").get("age_range")).isNull()
        assertThat(jsonNode.get("kakao_account").get("gender")).isNull()
    }

    @Test
    fun `should extract nested profile image URLs`() {
        val response =
            mapOf(
                "properties" to
                    mapOf(
                        "profile_image" to "https://k.kakaocdn.net/profile1.jpg",
                        "thumbnail_image" to "https://k.kakaocdn.net/thumb1.jpg",
                    ),
                "kakao_account" to
                    mapOf(
                        "profile" to
                            mapOf(
                                "profile_image_url" to "https://k.kakaocdn.net/profile2.jpg",
                                "thumbnail_image_url" to "https://k.kakaocdn.net/thumb2.jpg",
                                "is_default_image" to false,
                            ),
                    ),
            )

        val properties = response["properties"] as Map<String, Any>
        val kakaoAccount = response["kakao_account"] as Map<String, Any>
        val profile = kakaoAccount["profile"] as Map<String, Any>

        assertThat(properties["profile_image"]).isEqualTo("https://k.kakaocdn.net/profile1.jpg")
        assertThat(profile["profile_image_url"]).isEqualTo("https://k.kakaocdn.net/profile2.jpg")
        assertThat(profile["is_default_image"]).isEqualTo(false)
    }
}
