package com.credvenn.lm.user;

import com.credvenn.lm.common.api.PagedResponse;
import com.credvenn.lm.common.api.PaginationSupport;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.ConflictException;
import com.credvenn.lm.common.exception.ForbiddenOperationException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.security.AuthenticatedUser;
import com.credvenn.lm.security.CurrentActorService;
import com.credvenn.lm.security.Role;
import com.credvenn.lm.security.RoleService;
import com.credvenn.lm.subscription.SubscriptionGuardService;
import com.credvenn.lm.tenant.TenantRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Map<String, String> USER_SORTS = new LinkedHashMap<>();

    static {
        USER_SORTS.put("username", "username");
        USER_SORTS.put("email", "email");
        USER_SORTS.put("active", "active");
        USER_SORTS.put("createdAt", "createdAt");
        USER_SORTS.put("updatedAt", "updatedAt");
        USER_SORTS.put("lastLoginAt", "lastLoginAt");
    }

    private final AppUserRepository userRepository;
    private final RoleService roleService;
    private final CurrentActorService currentActorService;
    private final PasswordEncoder passwordEncoder;
    private final TenantRepository tenantRepository;
    private final SubscriptionGuardService subscriptionGuardService;

    public UserService(
            AppUserRepository userRepository,
            RoleService roleService,
            CurrentActorService currentActorService,
            PasswordEncoder passwordEncoder,
            TenantRepository tenantRepository,
            SubscriptionGuardService subscriptionGuardService) {
        this.userRepository = userRepository;
        this.roleService = roleService;
        this.currentActorService = currentActorService;
        this.passwordEncoder = passwordEncoder;
        this.tenantRepository = tenantRepository;
        this.subscriptionGuardService = subscriptionGuardService;
    }

    @Transactional(readOnly = true)
    public List<UserDtos.UserResponse> listCurrentTenantUsers() {
        AuthenticatedUser actor = currentActorService.requireCurrentUser();
        ensureTenantBound(actor);
        return listUsersByTenant(actor.tenantId());
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserDtos.UserResponse> listCurrentTenantUsers(
            Integer page,
            Integer size,
            String sortBy,
            String sortDir) {
        AuthenticatedUser actor = currentActorService.requireCurrentUser();
        ensureTenantBound(actor);
        return listUsersByTenant(actor.tenantId(), page, size, sortBy, sortDir);
    }

    @Transactional(readOnly = true)
    public List<UserDtos.UserResponse> listUsersByTenant(String tenantId) {
        assertTenantExists(tenantId);
        return userRepository.findAllByTenantIdOrderByUsernameAsc(tenantId).stream().map(UserService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserDtos.UserResponse> listUsersByTenant(
            String tenantId,
            Integer page,
            Integer size,
            String sortBy,
            String sortDir) {
        assertTenantExists(tenantId);
        Pageable pageable = PaginationSupport.pageable(page, size, sortBy, sortDir, USER_SORTS, "username");
        String normalizedSortBy = PaginationSupport.normalizeSortBy(sortBy, USER_SORTS, "username");
        String normalizedSortDir = PaginationSupport.normalizeDirectionValue(sortDir);
        var resultPage = userRepository.findAllByTenantId(tenantId, pageable).map(UserService::toResponse);
        return PagedResponse.fromPage(resultPage, normalizedSortBy, normalizedSortDir);
    }

    @Transactional(readOnly = true)
    public UserDtos.UserResponse getCurrentTenantUser(String userId) {
        AuthenticatedUser actor = currentActorService.requireCurrentUser();
        ensureTenantBound(actor);
        return toResponse(getRequiredTenantUser(actor.tenantId(), userId));
    }

    @Transactional(readOnly = true)
    public UserDtos.UserResponse getTenantUser(String tenantId, String userId) {
        return toResponse(getRequiredTenantUser(tenantId, userId));
    }

    @Transactional
    public UserDtos.UserResponse createCurrentTenantUser(UserDtos.CreateUserRequest request) {
        AuthenticatedUser actor = currentActorService.requireCurrentUser();
        ensureTenantBound(actor);
        return createUser(actor.tenantId(), request);
    }

    @Transactional
    public UserDtos.UserResponse createUser(String tenantId, UserDtos.CreateUserRequest request) {
        assertTenantExists(tenantId);
        subscriptionGuardService.assertCanCreateUser(tenantId);
        ensureUniqueUser(tenantId, request.username(), request.email(), null);
        AppUser user = new AppUser();
        user.setTenantId(tenantId);
        user.setUsername(request.username().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.getRoles().addAll(roleService.resolveTenantRoles(tenantId, request.roleIds()));
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserDtos.UserResponse updateCurrentTenantUser(String userId, UserDtos.UpdateUserRequest request) {
        AuthenticatedUser actor = currentActorService.requireCurrentUser();
        ensureTenantBound(actor);
        return updateUser(actor.tenantId(), userId, request);
    }

    @Transactional
    public UserDtos.UserResponse updateUser(String tenantId, String userId, UserDtos.UpdateUserRequest request) {
        AppUser user = getRequiredTenantUser(tenantId, userId);
        ensureUniqueUser(tenantId, request.username(), request.email(), userId);
        user.setUsername(request.username().trim());
        user.setEmail(request.email().trim().toLowerCase());
        return toResponse(user);
    }

    @Transactional
    public UserDtos.UserResponse assignRolesCurrentTenant(String userId, UserDtos.AssignRolesRequest request) {
        AuthenticatedUser actor = currentActorService.requireCurrentUser();
        ensureTenantBound(actor);
        return assignRoles(actor.tenantId(), userId, request);
    }

    @Transactional
    public UserDtos.UserResponse assignRoles(String tenantId, String userId, UserDtos.AssignRolesRequest request) {
        AppUser user = getRequiredTenantUser(tenantId, userId);
        List<Role> roles = roleService.resolveTenantRoles(tenantId, request.roleIds());
        user.getRoles().clear();
        user.getRoles().addAll(roles);
        return toResponse(user);
    }

    @Transactional
    public UserDtos.UserResponse updatePasswordCurrentTenant(String userId, UserDtos.UpdatePasswordRequest request) {
        AuthenticatedUser actor = currentActorService.requireCurrentUser();
        ensureTenantBound(actor);
        return updatePassword(actor.tenantId(), userId, request);
    }

    @Transactional
    public UserDtos.UserResponse updatePassword(String tenantId, String userId, UserDtos.UpdatePasswordRequest request) {
        AppUser user = getRequiredTenantUser(tenantId, userId);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return toResponse(user);
    }

    @Transactional
    public UserDtos.UserResponse updateStatusCurrentTenant(String userId, UserDtos.UpdateUserStatusRequest request) {
        AuthenticatedUser actor = currentActorService.requireCurrentUser();
        ensureTenantBound(actor);
        return updateStatus(actor.tenantId(), userId, request);
    }

    @Transactional
    public UserDtos.UserResponse updateStatus(String tenantId, String userId, UserDtos.UpdateUserStatusRequest request) {
        AppUser user = getRequiredTenantUser(tenantId, userId);
        user.setActive(request.active());
        return toResponse(user);
    }

    @Transactional
    public void deactivateCurrentTenantUser(String userId) {
        updateStatusCurrentTenant(userId, new UserDtos.UpdateUserStatusRequest(false));
    }

    @Transactional
    public void deactivateTenantUser(String tenantId, String userId) {
        updateStatus(tenantId, userId, new UserDtos.UpdateUserStatusRequest(false));
    }

    @Transactional(readOnly = true)
    public AppUser getRequiredTenantUser(String tenantId, String userId) {
        assertTenantExists(tenantId);
        return userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private void ensureUniqueUser(String tenantId, String username, String email, String existingUserId) {
        userRepository.findAllByTenantIdOrderByUsernameAsc(tenantId).stream()
                .filter(user -> existingUserId == null || !user.getId().equals(existingUserId))
                .forEach(user -> {
                    if (user.getUsername().equalsIgnoreCase(username.trim())) {
                        throw new ConflictException("Username already exists in this tenant");
                    }
                    if (user.getEmail().equalsIgnoreCase(email.trim())) {
                        throw new ConflictException("Email already exists in this tenant");
                    }
                });
    }

    private void ensureTenantBound(AuthenticatedUser actor) {
        if (actor.tenantId() == null) {
            throw new ForbiddenOperationException("Tenant context is required for this operation");
        }
    }

    private void assertTenantExists(String tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new NotFoundException("Tenant not found");
        }
    }

    static UserDtos.UserResponse toResponse(AppUser user) {
        return new UserDtos.UserResponse(
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                user.getEmail(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt(),
                user.getRoles().stream()
                        .map(role -> new UserDtos.UserRoleSummary(role.getId(), role.getCode(), role.getName(), role.isActive()))
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }
}
