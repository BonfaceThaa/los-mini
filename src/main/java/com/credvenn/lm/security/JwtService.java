package com.credvenn.lm.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final AppSecurityProperties properties;
    private final Key signingKey;

    public JwtService(AppSecurityProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
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
        return Jwts.parser().verifyWith((javax.crypto.SecretKey) signingKey).build().parseSignedClaims(token).getPayload();
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

    public long accessTokenExpiresInSeconds() {
        return properties.accessTokenExpirationSeconds();
    }

    public long refreshTokenExpiresInSeconds() {
        return properties.refreshTokenExpirationSeconds();
    }
}
