package com.ember.backend.config

import com.ember.backend.logging.RequestLoggingFilter
import com.ember.backend.security.FirebaseAuthenticationFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig(
    private val firebaseAuthenticationFilter: FirebaseAuthenticationFilter,
    private val requestLoggingFilter: RequestLoggingFilter,
) {

    // RequestLoggingFilter is added explicitly to the security chain below (after Firebase auth
    // resolves the caller) instead of letting Spring Boot auto-register it a second time.
    @Bean
    fun requestLoggingFilterRegistration(): FilterRegistrationBean<RequestLoggingFilter> =
        FilterRegistrationBean(requestLoggingFilter).apply { isEnabled = false }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // The reward callback is called by Google's servers, which can't log in; it
                    // proves itself with a signature instead (see AdRewardVerifier).
                    .requestMatchers("/auth/**", "/actuator/health", "/ads/reward-callback").permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .httpBasic { it.disable() }
            .formLogin(AbstractHttpConfigurer<*, HttpSecurity>::disable)
            .addFilterBefore(firebaseAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(requestLoggingFilter, FirebaseAuthenticationFilter::class.java)

        return http.build()
    }
}
