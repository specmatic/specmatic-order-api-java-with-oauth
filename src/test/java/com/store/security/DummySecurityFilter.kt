package com.store.security

import com.store.model.User
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.web.filter.OncePerRequestFilter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class DummySecurityFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Bearer token missing")
            return
        }
        SecurityContextHolder.getContext().authentication = PreAuthenticatedAuthenticationToken(
            User("Dummy User"),
            null,
            emptyList()
        ).apply { isAuthenticated = true }
        filterChain.doFilter(request, response)
    }
}