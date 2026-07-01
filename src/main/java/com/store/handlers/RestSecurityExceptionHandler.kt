package com.store.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class RestSecurityExceptionHandler(
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint, AccessDeniedHandler {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        writeError(
            response = response,
            error = "Unauthorized",
            httpStatus = HttpStatus.UNAUTHORIZED,
            message = authException.message ?: "Authentication required"
        )
    }

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        writeError(
            response = response,
            error = "Forbidden",
            httpStatus = HttpStatus.FORBIDDEN,
            message = accessDeniedException.message ?: "Access denied"
        )
    }

    private fun writeError(
        error: String,
        message: String,
        httpStatus: HttpStatus,
        response: HttpServletResponse,
    ) {
        response.status = httpStatus.value()
        response.contentType = "application/json"
        objectMapper.writeValue(
            response.writer,
            ErrorResponse(
                error = error,
                message = message,
                status = httpStatus.value(),
                timestamp = LocalDateTime.now(),
            )
        )
    }
}
