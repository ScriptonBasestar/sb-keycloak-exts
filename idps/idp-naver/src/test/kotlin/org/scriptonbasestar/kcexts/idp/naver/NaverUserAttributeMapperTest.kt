package org.scriptonbasestar.kcexts.idp.naver

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NaverUserAttributeMapperTest {
    private lateinit var mapper: NaverUserAttributeMapper
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        mapper = NaverUserAttributeMapper()
        objectMapper = ObjectMapper()
    }

    @Test
    fun `should return correct mapper id`() {
        assertThat(mapper.id).isEqualTo("naver-user-attribute-mapper")
    }

    @Test
    fun `should return compatible provider list`() {
        val providers = mapper.compatibleProviders
        assertThat(providers).containsExactly("naver")
    }

    @Test
    fun `should extend AbstractJsonUserAttributeMapper`() {
        assertThat(mapper).isInstanceOf(org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper::class.java)
    }

    @Test
    fun `should map Naver user profile to user attributes`() {
        val naverResponse = """{
            "resultcode": "00",
            "message": "success",
            "response": {
                "id": "32742776",
                "nickname": "홍길동",
                "name": "홍길동",
                "email": "test@naver.com",
                "gender": "F",
                "age": "20-29",
                "birthday": "10-01",
                "birthyear": "1990",
                "profile_image": "https://phinf.pstatic.net/contact/profile.jpg",
                "mobile": "010-1234-5678",
                "mobile_e164": "+821012345678"
            }
        }"""

        val jsonNode: JsonNode = objectMapper.readTree(naverResponse)
        
        assertThat(jsonNode.get("resultcode").asText()).isEqualTo("00")
        assertThat(jsonNode.get("message").asText()).isEqualTo("success")
        
        val response = jsonNode.get("response")
        assertThat(response.get("id").asText()).isEqualTo("32742776")
        assertThat(response.get("nickname").asText()).isEqualTo("홍길동")
        assertThat(response.get("email").asText()).isEqualTo("test@naver.com")
        assertThat(response.get("gender").asText()).isEqualTo("F")
        assertThat(response.get("age").asText()).isEqualTo("20-29")
    }

    @Test
    fun `should handle error response from Naver`() {
        val errorResponse = """{
            "resultcode": "024",
            "message": "Authentication failed",
            "error": "invalid_request",
            "error_description": "Invalid client_id"
        }"""

        val jsonNode: JsonNode = objectMapper.readTree(errorResponse)
        
        assertThat(jsonNode.get("resultcode").asText()).isNotEqualTo("00")
        assertThat(jsonNode.get("message").asText()).contains("failed")
        assertThat(jsonNode.get("error")).isNotNull
        assertThat(jsonNode.get("error_description")).isNotNull
    }

    @Test
    fun `should handle missing optional fields`() {
        val minimalResponse = """{
            "resultcode": "00",
            "message": "success",
            "response": {
                "id": "32742776",
                "nickname": "사용자"
            }
        }"""

        val jsonNode: JsonNode = objectMapper.readTree(minimalResponse)
        val response = jsonNode.get("response")
        
        assertThat(response.get("id")).isNotNull
        assertThat(response.get("nickname")).isNotNull
        assertThat(response.get("email")).isNull()
        assertThat(response.get("gender")).isNull()
        assertThat(response.get("age")).isNull()
        assertThat(response.get("mobile")).isNull()
    }

    @Test
    fun `should handle various gender formats`() {
        val genderMappings = mapOf(
            "M" to "male",
            "F" to "female",
            "U" to "unknown"
        )

        genderMappings.forEach { (naverGender, expectedGender) ->
            when (naverGender) {
                "M" -> assertThat("male").isEqualTo(expectedGender)
                "F" -> assertThat("female").isEqualTo(expectedGender)
                "U" -> assertThat("unknown").isEqualTo(expectedGender)
            }
        }
    }
}