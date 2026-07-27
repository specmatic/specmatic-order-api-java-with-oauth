package com.store.security

import com.store.model.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.authority.SimpleGrantedAuthority
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
    fun `preserves scope authorities while adapting the principal`() {
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "user1")
            .claim("scope", "order:create")
            .build()

        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(
            jwt,
            listOf(SimpleGrantedAuthority("SCOPE_order:create"))
        )

        filter.doFilter(
            MockHttpServletRequest("POST", "/orders"),
            MockHttpServletResponse(),
            MockFilterChain()
        )

        val authentication = SecurityContextHolder.getContext().authentication
        assertThat(authentication).isInstanceOf(PreAuthenticatedAuthenticationToken::class.java)

        val preAuth = authentication as PreAuthenticatedAuthenticationToken
        assertThat(preAuth.principal).isEqualTo(User("user1"))
        assertThat(preAuth.authorities.map { it.authority }).containsExactly("SCOPE_order:create")
    }
}
