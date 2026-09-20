package com.credvenn.lm.logbook;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="logbook_vehicles") @Getter @Setter
public class LogbookVehicle extends LogbookRecord {
    @Version private long version;
    @Column(name="evidence_revision", nullable=false) private long evidenceRevision;
    @Column(name="registration_number", nullable=false, length=20) private String registrationNumber;
    @Column(name="chassis_number", nullable=false, length=50) private String chassisNumber;
    @Column(name="engine_number", nullable=false, length=50) private String engineNumber;
    @Column(nullable=false, length=100) private String make;
    @Column(nullable=false, length=100) private String model;
    @Column(name="manufacture_year", nullable=false) private int manufactureYear;
    @Column(name="registered_owner", nullable=false) private String registeredOwner;
    @Column(name="logbook_number", nullable=false, length=100) private String logbookNumber;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Column(name="updated_by", nullable=false, length=36) private String updatedBy;
}
