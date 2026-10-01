package com.credvenn.lm.logbook;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="logbook_loan_operations") @Getter @Setter
public class LogbookLoanOperation extends LogbookRecord {
    public enum Kind { CREATE_LOAN, DISBURSE }
    public enum State { QUEUED, RUNNING, SUCCEEDED, BLOCKED, REVIEW_REQUIRED }
    @Version private long version;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Kind kind;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private State state;
    @Column(name="requested_by",nullable=false) private String requestedBy;
    @Column(nullable=false) private int attempts;
    @Column(name="started_at") private Instant startedAt;
    @Column(name="completed_at") private Instant completedAt;
    @Column(name="last_error",length=1000) private String lastError;
}
