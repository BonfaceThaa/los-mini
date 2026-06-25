package com.credvenn.lm.tenant;

import com.credvenn.lm.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "fineract_tenant_id", nullable = false, length = 100)
    private String fineractTenantId;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_mode", nullable = false, length = 20)
    private TenantKycMode kycMode = TenantKycMode.AUTO;

    @Enumerated(EnumType.STRING)
    @Column(name = "statement_analysis_mode", nullable = false, length = 20)
    private TenantStatementAnalysisMode statementAnalysisMode = TenantStatementAnalysisMode.AUTO;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFineractTenantId() {
        return fineractTenantId;
    }

    public void setFineractTenantId(String fineractTenantId) {
        this.fineractTenantId = fineractTenantId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public TenantKycMode getKycMode() {
        return kycMode;
    }

    public void setKycMode(TenantKycMode kycMode) {
        this.kycMode = kycMode;
    }

    public TenantStatementAnalysisMode getStatementAnalysisMode() {
        return statementAnalysisMode;
    }

    public void setStatementAnalysisMode(TenantStatementAnalysisMode statementAnalysisMode) {
        this.statementAnalysisMode = statementAnalysisMode;
    }
}
