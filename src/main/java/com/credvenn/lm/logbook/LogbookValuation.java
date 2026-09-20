package com.credvenn.lm.logbook;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;

@Entity @Table(name="logbook_valuations") @Getter @Setter
public class LogbookValuation extends LogbookRecord {
    @Column(name="vehicle_id", nullable=false, length=36) private String vehicleId;
    @Column(name="vehicle_revision", nullable=false) private long vehicleRevision;
    @Column(name="recorded_version", nullable=false) private long recordedVersion;
    @Column(name="market_value", nullable=false, precision=19, scale=2) private BigDecimal marketValue;
    @Column(name="forced_sale_value", nullable=false, precision=19, scale=2) private BigDecimal forcedSaleValue;
    @Column(name="valued_on", nullable=false) private LocalDate valuedOn;
    @Column(name="valuer_organization", nullable=false) private String valuerOrganization;
    @Column(name="report_document_id", nullable=false, length=36) private String reportDocumentId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private LogbookDtos.ValuationStatus status;
    @Column(name="reviewed_by", length=36) private String reviewedBy;
    @Column(name="reviewed_at") private Instant reviewedAt;
    @Column(name="review_reason", length=1000) private String reviewReason;
}
