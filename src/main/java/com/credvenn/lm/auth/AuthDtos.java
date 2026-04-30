package com.credvenn.lm.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public final class AuthDtos {

    private AuthDtos() {
    }

    @Schema(name = "LoginRequest")
    public record LoginRequest(
            @Size(max = 100) String tenantCode,
            @NotBlank @Size(max = 255) String identifier,
            @NotBlank @Size(min = 8, max = 100) String password) {
    }

    @Schema(name = "RefreshTokenRequest")
    public record RefreshTokenRequest(@NotBlank String refreshToken) {
    }

    @Schema(name = "LogoutRequest")
    public record LogoutRequest(@NotBlank String refreshToken) {
    }

    @Schema(name = "CurrentUserResponse")
    public record CurrentUserResponse(
            String userId,
            String tenantId,
            String username,
            String email,
            Set<String> roles,
            Set<String> authorities) {
    }

    @Schema(name = "AuthResponse")
    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds,
            CurrentUserResponse user) {
    }
}
