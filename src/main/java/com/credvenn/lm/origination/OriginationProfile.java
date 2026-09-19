package com.credvenn.lm.origination;

import com.credvenn.lm.common.domain.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "origination_profiles")
@Getter @Setter
public class OriginationProfile extends AuditableEntity {
    @Id @Column(length = 36) private String id;
    @Version @Column(nullable = false) private long version;
    @Column(name = "tenant_id", nullable = false, length = 36) private String tenantId;
    @Column(nullable = false, length = 100) private String code;
    @Column(name = "display_name", nullable = false) private String displayName;
    @Column(length = 1000) private String description;
    @Column(nullable = false) private boolean active;
    @Column(name = "requirements_json", nullable = false, columnDefinition = "JSON") private String requirementsJson;
    @Column(name = "configuration_json", columnDefinition = "JSON") private String configurationJson;
    @Column(name = "created_by", nullable = false) private String createdBy;
    @Column(name = "updated_by", nullable = false) private String updatedBy;
    @PrePersist void assignId() { if (id == null) id = UUID.randomUUID().toString(); }
}
