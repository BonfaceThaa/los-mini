package com.credvenn.lm.auth;

import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.ForbiddenOperationException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.security.AuthenticatedUser;
import com.credvenn.lm.security.CurrentActorService;
import com.credvenn.lm.security.JwtService;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantService;
import com.credvenn.lm.user.AppUser;
import com.credvenn.lm.user.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final TenantService tenantService;
    private final CurrentActorService currentActorService;

    public AuthService(
            AppUserRepository userRepository,
            RefreshTokenSessionRepository refreshTokenSessionRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            TenantService tenantService,
            CurrentActorService currentActorService) {
        this.userRepository = userRepository;
        this.refreshTokenSessionRepository = refreshTokenSessionRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.tenantService = tenantService;
        this.currentActorService = currentActorService;
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request, HttpServletRequest httpRequest) {
        AppUser user;
        if (request.tenantCode() != null && !request.tenantCode().isBlank()) {
            Tenant tenant = tenantService.requireActiveTenantByCode(request.tenantCode().trim());
            user = userRepository.findTenantUserForLogin(tenant.getId(), request.identifier().trim())
                    .orElseThrow(() -> new ForbiddenOperationException("Invalid credentials"));
        } else {
            user = userRepository.findPlatformUserForLogin(request.identifier().trim())
                    .orElseThrow(() -> new ForbiddenOperationException("Invalid credentials"));
        }

        if (!user.isActive()) {
            throw new ForbiddenOperationException("User account is inactive");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ForbiddenOperationException("Invalid credentials");
        }

        user.setLastLoginAt(Instant.now());
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = newRefreshTokenValue();
        RefreshTokenSession session = new RefreshTokenSession();
        session.setUserId(user.getId());
        session.setTenantId(user.getTenantId());
        session.setTokenHash(hashToken(refreshToken));
        session.setExpiresAt(Instant.now().plusSeconds(jwtService.refreshTokenExpiresInSeconds()));
        session.setIpAddress(httpRequest.getRemoteAddr());
        session.setUserAgent(httpRequest.getHeader("User-Agent"));
        refreshTokenSessionRepository.save(session);
        return buildAuthResponse(user, accessToken, refreshToken);
    }

    @Transactional
    public AuthDtos.AuthResponse refresh(AuthDtos.RefreshTokenRequest request, HttpServletRequest httpRequest) {
        RefreshTokenSession currentSession = getValidSession(request.refreshToken());
        AppUser user = loadUser(currentSession.getUserId(), currentSession.getTenantId());
        currentSession.setRevokedAt(Instant.now());
        currentSession.setLastUsedAt(Instant.now());
        String newRefreshToken = newRefreshTokenValue();
        RefreshTokenSession replacement = new RefreshTokenSession();
        replacement.setUserId(user.getId());
        replacement.setTenantId(user.getTenantId());
        replacement.setTokenHash(hashToken(newRefreshToken));
        replacement.setExpiresAt(Instant.now().plusSeconds(jwtService.refreshTokenExpiresInSeconds()));
        replacement.setIpAddress(httpRequest.getRemoteAddr());
        replacement.setUserAgent(httpRequest.getHeader("User-Agent"));
        refreshTokenSessionRepository.save(replacement);
        currentSession.setReplacedBySessionId(replacement.getId());
        return buildAuthResponse(user, jwtService.generateAccessToken(user), newRefreshToken);
    }

    @Transactional
    public void logout(AuthDtos.LogoutRequest request) {
        refreshTokenSessionRepository.findByTokenHash(hashToken(request.refreshToken()))
                .ifPresent(session -> session.setRevokedAt(Instant.now()));
    }

    @Transactional(readOnly = true)
    public AuthDtos.CurrentUserResponse me() {
        AuthenticatedUser actor = currentActorService.requireCurrentUser();
        return new AuthDtos.CurrentUserResponse(
                actor.userId(),
                actor.tenantId(),
                actor.username(),
                actor.email(),
                new LinkedHashSet<>(actor.roleCodes()),
                actor.authorities().stream().map(authority -> authority.getAuthority()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    private RefreshTokenSession getValidSession(String refreshToken) {
        RefreshTokenSession session = refreshTokenSessionRepository.findByTokenHash(hashToken(refreshToken))
                .orElseThrow(() -> new ForbiddenOperationException("Refresh token is invalid"));
        if (session.getRevokedAt() != null) {
            throw new ForbiddenOperationException("Refresh token has been revoked");
        }
        if (session.getExpiresAt().isBefore(Instant.now())) {
            throw new ForbiddenOperationException("Refresh token has expired");
        }
        return session;
    }

    private AppUser loadUser(String userId, String tenantId) {
        if (tenantId == null) {
            return userRepository.findByIdAndTenantIdIsNull(userId)
                    .orElseThrow(() -> new NotFoundException("User not found"));
        }
        return userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private AuthDtos.AuthResponse buildAuthResponse(AppUser user, String accessToken, String refreshToken) {
        Set<String> roles = user.getRoles().stream().map(role -> role.getCode()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> authorities = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getCode())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new AuthDtos.AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.accessTokenExpiresInSeconds(),
                new AuthDtos.CurrentUserResponse(user.getId(), user.getTenantId(), user.getUsername(), user.getEmail(), roles, authorities));
    }

    private String newRefreshTokenValue() {
        return UUID.randomUUID() + "." + UUID.randomUUID();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new BadRequestException("Unable to hash token");
        }
    }
}
