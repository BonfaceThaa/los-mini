package com.credvenn.lm.security;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public enum PermissionCatalog {
    TENANT_CREATE("TENANT_CREATE", "Create tenant businesses during onboarding"),
    TENANT_VIEW_ALL("TENANT_VIEW_ALL", "View all tenants on the SaaS platform"),
    TENANT_MANAGE_ALL("TENANT_MANAGE_ALL", "Manage users and roles across all tenants"),
    USER_VIEW("USER_VIEW", "View users within a tenant"),
    USER_CREATE("USER_CREATE", "Create users within a tenant"),
    USER_UPDATE("USER_UPDATE", "Update users within a tenant"),
    USER_DEACTIVATE("USER_DEACTIVATE", "Deactivate users within a tenant"),
    USER_ASSIGN_ROLES("USER_ASSIGN_ROLES", "Assign roles to users"),
    ROLE_VIEW("ROLE_VIEW", "View roles within a tenant"),
    ROLE_CREATE("ROLE_CREATE", "Create roles within a tenant"),
    ROLE_UPDATE("ROLE_UPDATE", "Update roles within a tenant"),
    ROLE_DEACTIVATE("ROLE_DEACTIVATE", "Deactivate roles within a tenant"),
    ROLE_ASSIGN_PERMISSIONS("ROLE_ASSIGN_PERMISSIONS", "Assign permissions to roles"),
    CLIENT_VIEW("CLIENT_VIEW", "View client records"),
    CLIENT_CREATE("CLIENT_CREATE", "Create client records"),
    LOAN_VIEW("LOAN_VIEW", "View loan records"),
    LOAN_CREATE("LOAN_CREATE", "Create loan records"),
    KYC_APPROVE("KYC_APPROVE", "Approve KYC"),
    KYC_REVOKE("KYC_REVOKE", "Revoke KYC"),
    TASK_CREATE("TASK_CREATE", "Create tasks"),
    TASK_COMPLETE("TASK_COMPLETE", "Complete tasks"),
    DEVICE_ASSIGN("DEVICE_ASSIGN", "Assign devices");

    private final String code;
    private final String description;

    PermissionCatalog(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    public static Set<String> allCodes() {
        return EnumSet.allOf(PermissionCatalog.class).stream().map(PermissionCatalog::code).collect(Collectors.toSet());
    }
}
