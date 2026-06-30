package com.store.security

import com.store.model.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken

class JwtAuthenticationFilterTest {
    private val filter = JwtAuthenticationFilter()

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `maps realm roles to spring authorities`() {
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "user1")
            .claim("realm_access", mapOf("roles" to listOf("users", "admins")))
            .build()

        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt, emptyList())

        filter.doFilter(
            MockHttpServletRequest("POST", "/orders"),
            MockHttpServletResponse(),
            MockFilterChain()
        )

        val authentication = SecurityContextHolder.getContext().authentication
        assertThat(authentication).isInstanceOf(PreAuthenticatedAuthenticationToken::class.java)

        val preAuth = authentication as PreAuthenticatedAuthenticationToken
        assertThat(preAuth.principal).isEqualTo(User("user1"))
        assertThat(preAuth.authorities.map { it.authority }).containsExactlyInAnyOrder("ROLE_users", "ROLE_admins")
    }
}
