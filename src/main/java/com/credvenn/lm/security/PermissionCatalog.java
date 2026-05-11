package com.credvenn.lm.security;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public enum PermissionCatalog {
    TENANT_CREATE("TENANT_CREATE", "Create tenant businesses during onboarding"),
    TENANT_VIEW_ALL("TENANT_VIEW_ALL", "View all tenants on the SaaS platform"),
    TENANT_MANAGE_ALL("TENANT_MANAGE_ALL", "Manage users and roles across all tenants"),
    SUBSCRIPTION_PLAN_MANAGE("SUBSCRIPTION_PLAN_MANAGE", "Create and manage platform subscription plans"),
    TENANT_SUBSCRIPTION_MANAGE("TENANT_SUBSCRIPTION_MANAGE", "Assign and manage tenant subscriptions"),
    TENANT_SUBSCRIPTION_VIEW("TENANT_SUBSCRIPTION_VIEW", "View tenant subscriptions and update operational notes"),
    KYC_VIEW("KYC_VIEW", "View KYC checks"),
    KYC_RUN("KYC_RUN", "Run KYC checks"),
    KYC_MANUAL_REVIEW("KYC_MANUAL_REVIEW", "Manually review KYC decisions"),
    CREDIT_CHECK_VIEW("CREDIT_CHECK_VIEW", "View credit and statement analysis results"),
    CREDIT_CHECK_RUN("CREDIT_CHECK_RUN", "Run statement and credit analysis"),
    CREDIT_MANUAL_APPROVE("CREDIT_MANUAL_APPROVE", "Perform manual credit approval"),
    DOCUMENT_VIEW("DOCUMENT_VIEW", "View loan application documents"),
    DOCUMENT_UPLOAD("DOCUMENT_UPLOAD", "Upload loan application documents"),
    DOCUMENT_DELETE("DOCUMENT_DELETE", "Delete loan application documents"),
    STATEMENT_INBOX_VIEW("STATEMENT_INBOX_VIEW", "View inbound statement inbox items"),
    STATEMENT_INBOX_RESOLVE("STATEMENT_INBOX_RESOLVE", "Resolve inbound statement inbox items"),
    STATEMENT_INBOUND_INGEST("STATEMENT_INBOUND_INGEST", "Ingest inbound statement attachments for background routing"),
    INVENTORY_VIEW("INVENTORY_VIEW", "View inventory devices"),
    INVENTORY_MANAGE("INVENTORY_MANAGE", "Manage inventory devices"),
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
