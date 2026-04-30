package com.credvenn.lm.platform;

import com.credvenn.lm.user.UserDtos;
import com.credvenn.lm.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/tenants/{tenantId}/users")
@Tag(name = "Platform Tenant Users")
@SecurityRequirement(name = "bearerAuth")
public class InternalTenantUserController {

    private final UserService userService;

    public InternalTenantUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "List users for any tenant as a platform administrator")
    public ResponseEntity<List<UserDtos.UserResponse>> listUsers(@PathVariable String tenantId) {
        return ResponseEntity.ok(userService.listUsersByTenant(tenantId));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Get a tenant user by id as a platform administrator")
    public ResponseEntity<UserDtos.UserResponse> getUser(@PathVariable String tenantId, @PathVariable String userId) {
        return ResponseEntity.ok(userService.getTenantUser(tenantId, userId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Create a user for any tenant as a platform administrator")
    public ResponseEntity<UserDtos.UserResponse> createUser(
            @PathVariable String tenantId,
            @Valid @RequestBody UserDtos.CreateUserRequest request) {
        return ResponseEntity.ok(userService.createUser(tenantId, request));
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Update a tenant user as a platform administrator")
    public ResponseEntity<UserDtos.UserResponse> updateUser(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @Valid @RequestBody UserDtos.UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(tenantId, userId, request));
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Assign roles to a tenant user as a platform administrator")
    public ResponseEntity<UserDtos.UserResponse> assignRoles(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @Valid @RequestBody UserDtos.AssignRolesRequest request) {
        return ResponseEntity.ok(userService.assignRoles(tenantId, userId, request));
    }

    @PatchMapping("/{userId}/password")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Reset a tenant user's password as a platform administrator")
    public ResponseEntity<UserDtos.UserResponse> updatePassword(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @Valid @RequestBody UserDtos.UpdatePasswordRequest request) {
        return ResponseEntity.ok(userService.updatePassword(tenantId, userId, request));
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Activate or deactivate a tenant user as a platform administrator")
    public ResponseEntity<UserDtos.UserResponse> updateStatus(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @Valid @RequestBody UserDtos.UpdateUserStatusRequest request) {
        return ResponseEntity.ok(userService.updateStatus(tenantId, userId, request));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Soft delete a tenant user by deactivating the account")
    public ResponseEntity<Void> deactivateUser(@PathVariable String tenantId, @PathVariable String userId) {
        userService.deactivateTenantUser(tenantId, userId);
        return ResponseEntity.noContent().build();
    }
}
