package com.credvenn.lm.logbook;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="logbook_audits") @Getter @Setter
public class LogbookAudit extends LogbookRecord {
    @Column(nullable=false, length=50) private String action;
    @Column(name="record_id", nullable=false, length=36) private String recordId;
    @Column(name="before_json", columnDefinition="JSON") private String beforeJson;
    @Column(name="after_json", nullable=false, columnDefinition="JSON") private String afterJson;
}
