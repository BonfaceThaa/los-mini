package com.credvenn.lm.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;

public record AuthenticatedService(
        String serviceName,
        String tenantId,
        List<String> scopes,
        Collection<? extends GrantedAuthority> authorities) {
}
