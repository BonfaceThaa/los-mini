package com.credvenn.lm.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AppCorsProperties appCorsProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, AppCorsProperties appCorsProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.appCorsProperties = appCorsProperties;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers(
                                "/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/public/payments/mpesa/callback",
                                "/api/v1/public/payments/mpesa/deposits/callback",
                                "/api/v1/public/tenants/*/payments/mpesa/deposits/callback",
                                "/api/v1/public/integrations/cladfy/webhook",
                                "/api/v1/public/tenants/*/payments/mpesa/stk/callback")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(defaultIfEmpty(appCorsProperties.allowedOrigins(), List.of("*")));
        configuration.setAllowedMethods(defaultIfEmpty(appCorsProperties.allowedMethods(), List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")));
        configuration.setAllowedHeaders(defaultIfEmpty(appCorsProperties.allowedHeaders(), List.of("*")));
        configuration.setExposedHeaders(defaultIfEmpty(appCorsProperties.exposedHeaders(), List.of("Authorization", "Location")));
        configuration.setAllowCredentials(Boolean.TRUE.equals(appCorsProperties.allowCredentials()));
        configuration.setMaxAge(appCorsProperties.maxAgeSeconds() == null ? 3600L : appCorsProperties.maxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> defaultIfEmpty(List<String> values, List<String> defaults) {
        return values == null || values.isEmpty() ? defaults : values;
    }
}
