package com.credvenn.lm.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String SERVICE_TOKEN_TYPE = "service";

    private final AppSecurityProperties properties;
    private final javax.crypto.SecretKey signingKey;
    private final javax.crypto.SecretKey serviceSigningKey;

    public JwtService(AppSecurityProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.serviceSigningKey = Keys.hmacShaKeyFor(properties.serviceSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(com.credvenn.lm.user.AppUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.accessTokenExpirationSeconds());
        List<String> authorities = user.getRoles().stream()
                .filter(com.credvenn.lm.security.Role::isActive)
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .distinct()
                .sorted()
                .toList();
        List<String> roles = user.getRoles().stream()
                .filter(com.credvenn.lm.security.Role::isActive)
                .map(Role::getCode)
                .sorted()
                .toList();

        return Jwts.builder()
                .subject(user.getId())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("tenantId", user.getTenantId())
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .claim("authorities", authorities)
                .signWith(signingKey)
                .compact();
    }

    public Claims parse(String token) {
        return parseToken(token).claims();
    }

    public AuthenticatedUser toAuthenticatedUser(Claims claims) {
        @SuppressWarnings("unchecked")
        List<String> authorities = claims.get("authorities", List.class);
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        Collection<SimpleGrantedAuthority> grantedAuthorities = authorities == null
                ? List.of()
                : authorities.stream().map(SimpleGrantedAuthority::new).toList();
        return new AuthenticatedUser(
                claims.getSubject(),
                claims.get("tenantId", String.class),
                claims.get("username", String.class),
                claims.get("email", String.class),
                roles == null ? List.of() : roles,
                grantedAuthorities);
    }

    public Object toAuthenticatedPrincipal(String token) {
        ParsedToken parsed = parseToken(token);
        if (parsed.serviceToken()) {
            return toAuthenticatedService(parsed.claims());
        }
        return toAuthenticatedUser(parsed.claims());
    }

    public AuthenticatedService toAuthenticatedService(Claims claims) {
        @SuppressWarnings("unchecked")
        List<String> scopes = claims.get("scopes", List.class);
        Collection<SimpleGrantedAuthority> grantedAuthorities = scopes == null
                ? List.of()
                : scopes.stream().map(SimpleGrantedAuthority::new).toList();
        String serviceName = claims.get("serviceName", String.class);
        if (serviceName == null || serviceName.isBlank()) {
            serviceName = claims.getSubject();
        }
        return new AuthenticatedService(
                serviceName,
                claims.get("tenantId", String.class),
                scopes == null ? List.of() : scopes,
                grantedAuthorities);
    }

    public long accessTokenExpiresInSeconds() {
        return properties.accessTokenExpirationSeconds();
    }

    public long refreshTokenExpiresInSeconds() {
        return properties.refreshTokenExpirationSeconds();
    }

    private ParsedToken parseToken(String token) {
        JwtException userFailure = null;
        try {
            Claims claims = parseWithKey(token, signingKey);
            validateIssuer(claims, properties.issuer());
            if (SERVICE_TOKEN_TYPE.equalsIgnoreCase(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
                if (isSameSecret()) {
                    validateIssuer(claims, properties.serviceIssuer());
                    return new ParsedToken(claims, true);
                }
                throw new JwtException("Service token must be signed with the service key");
            }
            return new ParsedToken(claims, false);
        } catch (JwtException ex) {
            userFailure = ex;
        }

        Claims serviceClaims = parseWithKey(token, serviceSigningKey);
        validateIssuer(serviceClaims, properties.serviceIssuer());
        if (!SERVICE_TOKEN_TYPE.equalsIgnoreCase(serviceClaims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw userFailure == null ? new JwtException("Unsupported token type") : userFailure;
        }
        return new ParsedToken(serviceClaims, true);
    }

    private Claims parseWithKey(String token, javax.crypto.SecretKey key) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private void validateIssuer(Claims claims, String expectedIssuer) {
        if (expectedIssuer != null && !expectedIssuer.isBlank() && !expectedIssuer.equals(claims.getIssuer())) {
            throw new JwtException("Token issuer is invalid");
        }
    }

    private boolean isSameSecret() {
        return properties.secret().equals(properties.serviceSecret());
    }

    private record ParsedToken(Claims claims, boolean serviceToken) {
    }
}
