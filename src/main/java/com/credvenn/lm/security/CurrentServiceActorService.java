package com.credvenn.lm.security;

import com.credvenn.lm.common.exception.ForbiddenOperationException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentServiceActorService {

    public AuthenticatedService requireCurrentService() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof AuthenticatedService principal)) {
            throw new ForbiddenOperationException("Service authentication is required");
        }
        return principal;
    }
}
