package com.store.security

import com.store.model.User
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.web.filter.OncePerRequestFilter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class JwtAuthenticationFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication is JwtAuthenticationToken) {
            val authorities = authentication.authorities.toList()
            logger.info("Request: ${request.method} ${request.requestURI} received with JWT authorities: ${authorities.joinToString(", ")}")
            SecurityContextHolder.getContext().authentication =
                PreAuthenticatedAuthenticationToken(User(authentication.name), null, authorities)
        }

        filterChain.doFilter(request, response)
    }
}
