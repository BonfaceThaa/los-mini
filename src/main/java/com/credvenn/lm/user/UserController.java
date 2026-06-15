package com.credvenn.lm.user;

import com.credvenn.lm.common.api.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Tenant Users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW')")
    @Operation(summary = "List users for the authenticated tenant")
    public ResponseEntity<PagedResponse<UserDtos.UserResponse>> listUsers(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "username") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(userService.listCurrentTenantUsers(page, size, sortBy, sortDir));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    @Operation(summary = "Get a tenant user by id")
    public ResponseEntity<UserDtos.UserResponse> getUser(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getCurrentTenantUser(userId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Create a user within the authenticated tenant")
    public ResponseEntity<UserDtos.UserResponse> createUser(@Valid @RequestBody UserDtos.CreateUserRequest request) {
        return ResponseEntity.ok(userService.createCurrentTenantUser(request));
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Update a tenant user's profile")
    public ResponseEntity<UserDtos.UserResponse> updateUser(
            @PathVariable String userId,
            @Valid @RequestBody UserDtos.UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateCurrentTenantUser(userId, request));
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('USER_ASSIGN_ROLES')")
    @Operation(summary = "Assign tenant roles to a user")
    public ResponseEntity<UserDtos.UserResponse> assignRoles(
            @PathVariable String userId,
            @Valid @RequestBody UserDtos.AssignRolesRequest request) {
        return ResponseEntity.ok(userService.assignRolesCurrentTenant(userId, request));
    }

    @PatchMapping("/{userId}/password")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Reset a tenant user's password")
    public ResponseEntity<UserDtos.UserResponse> updatePassword(
            @PathVariable String userId,
            @Valid @RequestBody UserDtos.UpdatePasswordRequest request) {
        return ResponseEntity.ok(userService.updatePasswordCurrentTenant(userId, request));
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('USER_DEACTIVATE')")
    @Operation(summary = "Activate or deactivate a tenant user")
    public ResponseEntity<UserDtos.UserResponse> updateStatus(
            @PathVariable String userId,
            @Valid @RequestBody UserDtos.UpdateUserStatusRequest request) {
        return ResponseEntity.ok(userService.updateStatusCurrentTenant(userId, request));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('USER_DEACTIVATE')")
    @Operation(summary = "Soft delete a tenant user by deactivating the account")
    public ResponseEntity<Void> deactivateUser(@PathVariable String userId) {
        userService.deactivateCurrentTenantUser(userId);
        return ResponseEntity.noContent().build();
    }
}
