package com.credvenn.lm.scoring;

import com.credvenn.lm.common.domain.AuditableEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "scoring_scorecard_versions")
@Getter @Setter
public class ScorecardVersion extends AuditableEntity {
    @Id @Column(length = 36) private String id;
    @Column(name = "tenant_id", nullable = false, length = 36) private String tenantId;
    @Column(name = "origination_profile_id", nullable = false, length = 36) private String originationProfileId;
    @Column(name = "version_number", nullable = false) private int versionNumber;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "draft_json", nullable = false, columnDefinition = "LONGTEXT") private String draftJson;
    @Column(name = "snapshot_json", columnDefinition = "LONGTEXT") private String snapshotJson;
    @Column(name = "created_by", nullable = false) private String createdBy;
    @Column(name = "updated_by", nullable = false) private String updatedBy;
    @Column(name = "published_at") private Instant publishedAt;
    @PrePersist void assignId() { if (id == null) id = UUID.randomUUID().toString(); }
}
