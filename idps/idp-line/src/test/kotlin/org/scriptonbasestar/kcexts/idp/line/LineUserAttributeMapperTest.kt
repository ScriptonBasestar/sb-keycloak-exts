package org.scriptonbasestar.kcexts.idp.line

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LineUserAttributeMapperTest {
    private lateinit var mapper: LineUserAttributeMapper
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        mapper = LineUserAttributeMapper()
        objectMapper = ObjectMapper()
    }

    @Test
    fun `should return correct mapper id`() {
        assertThat(mapper.id).isEqualTo("line-user-attribute-mapper")
    }

    @Test
    fun `should return compatible provider list`() {
        val providers = mapper.compatibleProviders
        assertThat(providers).containsExactly("line")
    }

    @Test
    fun `should extend AbstractJsonUserAttributeMapper`() {
        assertThat(mapper).isInstanceOf(org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper::class.java)
    }

    @Test
    fun `should have single compatible provider`() {
        val providers = mapper.compatibleProviders
        assertThat(providers).hasSize(1)
        assertThat(providers[0]).isEqualTo(LineConstant.providerId)
    }

    @Test
    fun `should map LINE user profile to user attributes`() {
        val lineResponse = """{
            "userId": "U4af4980629...",
            "displayName": "Brown",
            "pictureUrl": "https://profile.line-scdn.net/profile.jpg",
            "statusMessage": "Hello, LINE!"
        }"""

        val jsonNode: JsonNode = objectMapper.readTree(lineResponse)
        
        assertThat(jsonNode.get("userId").asText()).isEqualTo("U4af4980629...")
        assertThat(jsonNode.get("displayName").asText()).isEqualTo("Brown")
        assertThat(jsonNode.get("pictureUrl").asText()).isEqualTo("https://profile.line-scdn.net/profile.jpg")
        assertThat(jsonNode.get("statusMessage").asText()).isEqualTo("Hello, LINE!")
    }

    @Test
    fun `should handle OpenID Connect profile response`() {
        val lineOidcResponse = """{
            "sub": "U4af4980629...",
            "name": "Brown Sally",
            "picture": "https://profile.line-scdn.net/profile.jpg",
            "email": "brown@example.com",
            "email_verified": true
        }"""

        val jsonNode: JsonNode = objectMapper.readTree(lineOidcResponse)
        
        assertThat(jsonNode.get("sub").asText()).isEqualTo("U4af4980629...")
        assertThat(jsonNode.get("name").asText()).isEqualTo("Brown Sally")
        assertThat(jsonNode.get("email").asText()).isEqualTo("brown@example.com")
        assertThat(jsonNode.get("email_verified").asBoolean()).isTrue
    }

    @Test
    fun `should handle missing optional fields`() {
        val minimalResponse = """{
            "userId": "U4af4980629...",
            "displayName": "User"
        }"""

        val jsonNode: JsonNode = objectMapper.readTree(minimalResponse)
        
        assertThat(jsonNode.get("userId")).isNotNull
        assertThat(jsonNode.get("displayName")).isNotNull
        assertThat(jsonNode.get("pictureUrl")).isNull()
        assertThat(jsonNode.get("statusMessage")).isNull()
        assertThat(jsonNode.get("email")).isNull()
    }
}