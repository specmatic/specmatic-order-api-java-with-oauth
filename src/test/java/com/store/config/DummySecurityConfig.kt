package com.store.config

import com.store.security.DummySecurityFilter
import com.store.handlers.RestSecurityExceptionHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter

@Configuration
@Profile("test")
open class DummySecurityConfig: SecurityConfig() {
    @Bean
    override fun filterChain(
        http: HttpSecurity,
        restSecurityExceptionHandler: RestSecurityExceptionHandler
    ): SecurityFilterChain {
        http.addFilterBefore(DummySecurityFilter(), AbstractPreAuthenticatedProcessingFilter::class.java)
        http.csrf().disable()
        http.authorizeRequests { auth -> auth.anyRequest().permitAll() }
        http.exceptionHandling().authenticationEntryPoint(restSecurityExceptionHandler).accessDeniedHandler(restSecurityExceptionHandler)
        return http.build()
    }
}
