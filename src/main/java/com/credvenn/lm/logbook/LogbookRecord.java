package com.credvenn.lm.logbook;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass @Getter @Setter
public abstract class LogbookRecord {
    @Id @Column(length=36) private String id;
    @Column(name="tenant_id", nullable=false, length=36) private String tenantId;
    @Column(name="application_id", nullable=false, length=36) private String applicationId;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="created_by", nullable=false, length=36) private String createdBy;
    @PrePersist void initialize() { if (id == null) id = UUID.randomUUID().toString(); if (createdAt == null) createdAt = Instant.now(); }
}
