package com.credvenn.lm.origination;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "origination_profile_audit") @Getter @Setter
public class OriginationProfileAudit {
    @Id @Column(length = 36) private String id = UUID.randomUUID().toString();
    @Column(name = "tenant_id", nullable = false, length = 36) private String tenantId;
    @Column(name = "profile_id", nullable = false, length = 36) private String profileId;
    @Column(nullable = false, length = 30) private String action;
    @Column(name = "changed_by", nullable = false) private String changedBy;
    @Column(name = "before_json", columnDefinition = "JSON") private String beforeJson;
    @Column(name = "after_json", columnDefinition = "JSON") private String afterJson;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
}
