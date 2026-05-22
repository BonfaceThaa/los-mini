package com.credvenn.lm.security;

import com.credvenn.lm.common.logging.LoggingContext;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            String token = extractBearerToken(authorization);
            if (token != null) {
                Object principal = jwtService.toAuthenticatedPrincipal(token);
                UsernamePasswordAuthenticationToken authentication;
                String tenantId = null;
                String userId = null;
                String username = null;
                if (principal instanceof AuthenticatedUser user) {
                    authentication = new UsernamePasswordAuthenticationToken(user, null, user.authorities());
                    tenantId = user.tenantId();
                    userId = user.userId();
                    username = user.username();
                } else if (principal instanceof AuthenticatedService service) {
                    authentication = new UsernamePasswordAuthenticationToken(service, null, service.authorities());
                    tenantId = service.tenantId();
                    username = service.serviceName();
                } else {
                    throw new JwtException("Unsupported principal type");
                }
                SecurityContextHolder.getContext().setAuthentication(authentication);
                TenantContext.setTenantId(tenantId);
                if (tenantId != null && !tenantId.isBlank()) {
                    MDC.put(LoggingContext.TENANT_ID, tenantId);
                }
                if (userId != null && !userId.isBlank()) {
                    MDC.put(LoggingContext.USER_ID, userId);
                }
                if (username != null && !username.isBlank()) {
                    MDC.put(LoggingContext.USERNAME, username);
                }
            }
            filterChain.doFilter(request, response);
        } catch (JwtException ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Invalid or expired access token\"}");
        } finally {
            MDC.remove(LoggingContext.TENANT_ID);
            MDC.remove(LoggingContext.USER_ID);
            MDC.remove(LoggingContext.USERNAME);
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    private String extractBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        String trimmed = authorization.trim();
        if (!trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = trimmed.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
