package com.store.security

import com.store.model.User
import org.springframework.http.HttpMethod
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.web.filter.OncePerRequestFilter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.OffsetDateTime
import java.util.Base64

class DummySecurityFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.requestURI == "/health") {
            filterChain.doFilter(request, response)
            return
        }

        when (request.method) {
            HttpMethod.POST.name(), HttpMethod.PATCH.name() -> {
                val authResult = authorizeOAuth2(request)
                if (authResult == AuthResult.UNAUTHORIZED) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed")
                    return
                }
                if (authResult == AuthResult.FORBIDDEN) {
                    sendForbidden(response)
                    return
                }
            }
            HttpMethod.GET.name() -> authenticateBasic(request)
                .takeIf { it } ?: run {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed")
                    return
                }
            HttpMethod.DELETE.name() -> authenticateApiKey(request)
                .takeIf { it } ?: run {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed")
                    return
                }
            else -> {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed")
                return
            }
        }

        SecurityContextHolder.getContext().authentication = PreAuthenticatedAuthenticationToken(
            User("Authenticated User"),
            null,
            emptyList()
        ).apply { isAuthenticated = true }

        filterChain.doFilter(request, response)
    }

    private fun authorizeOAuth2(request: HttpServletRequest): AuthResult {
        val authHeader = request.getHeader("Authorization")
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return AuthResult.UNAUTHORIZED
        }

        val token = authHeader.removePrefix("Bearer ").trim()
        if (!token.startsWith("mock-token-")) {
            return AuthResult.UNAUTHORIZED
        }

        val hasOrderCreateScope = token.contains("scope-order:create")
        val hasProductCreateScope = token.contains("scope-product:create")
        val path = request.requestURI
        return when {
            path.startsWith("/products") && hasProductCreateScope -> AuthResult.AUTHORIZED
            path.startsWith("/orders") && hasOrderCreateScope -> AuthResult.AUTHORIZED
            path.startsWith("/products") || path.startsWith("/orders") -> AuthResult.FORBIDDEN
            else -> AuthResult.AUTHORIZED
        }
    }

    private fun authenticateBasic(request: HttpServletRequest): Boolean {
        val authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            try {
                val base64Credentials = authHeader.substring("Basic ".length)
                val credentials = String(Base64.getDecoder().decode(base64Credentials))
                // Just verify format username:password exists, don't validate actual credentials in test mode
                return credentials.contains(":")
            } catch (e: Exception) {
                return false
            }
        }
        return false
    }

    private fun authenticateApiKey(request: HttpServletRequest): Boolean {
        val apiKey = request.getHeader("X-API-Key")
        return apiKey != null && apiKey.isNotEmpty()
    }

    private fun sendForbidden(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = "application/json"
        response.writer.write("""{"timestamp":"${OffsetDateTime.now()}","status":403,"error":"Forbidden","message":"Forbidden"}""")
    }

    private enum class AuthResult {
        AUTHORIZED,
        FORBIDDEN,
        UNAUTHORIZED
    }
}
