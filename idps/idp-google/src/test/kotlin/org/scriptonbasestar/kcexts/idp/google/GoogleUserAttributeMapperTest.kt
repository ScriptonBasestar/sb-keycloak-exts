package org.scriptonbasestar.kcexts.idp.google

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GoogleUserAttributeMapperTest {
    private val mapper = GoogleUserAttributeMapper()

    @Test
    fun `getId should return correct mapper ID`() {
        assertThat(mapper.id).isEqualTo("google-user-attribute-mapper")
    }

    @Test
    fun `getCompatibleProviders should return Google provider ID`() {
        val compatibleProviders = mapper.compatibleProviders

        assertThat(compatibleProviders).containsExactly(GoogleConstant.providerId)
    }

    @Test
    fun `mapper should extend AbstractJsonUserAttributeMapper`() {
        assertThat(mapper).isInstanceOf(org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper::class.java)
    }
}