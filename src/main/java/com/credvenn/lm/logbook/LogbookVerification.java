package com.credvenn.lm.logbook;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity @Table(name="logbook_verifications") @Getter @Setter
public class LogbookVerification extends LogbookRecord {
    @Column(name="vehicle_id", nullable=false, length=36) private String vehicleId;
    @Column(name="vehicle_revision", nullable=false) private long vehicleRevision;
    @Column(name="recorded_version", nullable=false) private long recordedVersion;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private LogbookDtos.VerificationKind kind;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private LogbookDtos.VerificationStatus status;
    @Column(name="reference_number", nullable=false) private String referenceNumber;
    @Column(name="document_id", nullable=false, length=36) private String documentId;
    @Column(name="valid_from") private LocalDate validFrom;
    @Column(name="valid_until") private LocalDate validUntil;
    @Column(nullable=false, length=1000) private String notes;
}
