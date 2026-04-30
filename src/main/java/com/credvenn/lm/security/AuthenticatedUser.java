package com.credvenn.lm.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;

public record AuthenticatedUser(
        String userId,
        String tenantId,
        String username,
        String email,
        List<String> roleCodes,
        Collection<? extends GrantedAuthority> authorities) {
}
