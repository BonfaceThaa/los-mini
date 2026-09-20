package com.credvenn.lm.logbook;

import static com.credvenn.lm.logbook.LogbookDtos.*;
import com.credvenn.lm.application.*;
import com.credvenn.lm.common.exception.*;
import com.credvenn.lm.document.ApplicationDocumentRepository;
import com.credvenn.lm.origination.*;
import com.credvenn.lm.origination.OriginationProfileDtos.*;
import com.credvenn.lm.security.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class LogbookService {
    private final CurrentActorService actors;
    private final LoanRequestApplicationRepository applications;
    private final OriginationProfileRepository profiles;
    private final ApplicationDocumentRepository documents;
    private final LogbookVehicleRepository vehicles;
    private final LogbookValuationRepository valuations;
    private final LogbookVerificationRepository verifications;
    private final LogbookAuditRepository audits;
    private final ObjectMapper json;
    private Clock clock = Clock.system(ZoneId.of("Africa/Nairobi"));

    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('LOGBOOK_VIEW')")
    public Summary get(String applicationId) {
        var actor = actors.requireCurrentUser();
        var application = application(actor.tenantId(), applicationId, false);
        return summary(application);
    }

    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('LOGBOOK_VIEW')")
    public List<LogbookAudit> auditHistory(String applicationId) {
        var actor = actors.requireCurrentUser();
        application(actor.tenantId(), applicationId, false);
        return audits.findAllByTenantIdAndApplicationIdOrderByCreatedAtDesc(actor.tenantId(), applicationId);
    }

    @Transactional @PreAuthorize("hasAuthority('LOGBOOK_VEHICLE_MANAGE')")
    public Summary saveVehicle(String applicationId, VehicleRequest request) {
        var actor = actors.requireCurrentUser();
        var application = application(actor.tenantId(), applicationId, true);
        if (request.manufactureYear() > today().getYear()) throw new BadRequestException("Manufacture year cannot be in the future");
        var vehicle = vehicles.findByTenantIdAndApplicationId(actor.tenantId(), applicationId).orElse(null);
        String before = vehicle == null ? null : encode(vehicle);
        if (vehicle == null) {
            if (request.expectedVersion() != null) throw new ConflictException("Vehicle does not exist; omit expectedVersion for initial capture");
            vehicle = new LogbookVehicle(); initialize(vehicle, application, actor);
        } else {
            expected(vehicle, request.expectedVersion());
            vehicle.setEvidenceRevision(vehicle.getEvidenceRevision() + 1);
        }
        vehicle.setRegistrationNumber(normalizeIdentifier(request.registrationNumber()));
        vehicle.setChassisNumber(normalizeIdentifier(request.chassisNumber()));
        vehicle.setEngineNumber(normalizeIdentifier(request.engineNumber()));
        vehicle.setMake(request.make().trim()); vehicle.setModel(request.model().trim());
        vehicle.setManufactureYear(request.manufactureYear()); vehicle.setRegisteredOwner(request.registeredOwner().trim());
        vehicle.setLogbookNumber(request.logbookNumber().trim());
        touch(vehicle, actor); vehicles.saveAndFlush(vehicle);
        audit(application, actor, "VEHICLE_SAVED", vehicle.getId(), before, vehicle);
        return summary(application);
    }

    @Transactional @PreAuthorize("hasAuthority('LOGBOOK_VALUATION_SUBMIT')")
    public Summary submitValuation(String applicationId, ValuationRequest request) {
        var actor = actors.requireCurrentUser(); var application = application(actor.tenantId(), applicationId, true);
        var vehicle = vehicle(application, request.expectedVersion());
        if (request.marketValue().signum() <= 0 || request.forcedSaleValue().signum() <= 0
                || request.forcedSaleValue().compareTo(request.marketValue()) > 0)
            throw new BadRequestException("Valuation values must be positive and forced-sale value cannot exceed market value");
        if (request.valuedOn().isAfter(today())) throw new BadRequestException("Valuation date cannot be in the future");
        evidence(application, request.reportDocumentId());
        var valuation = new LogbookValuation(); initialize(valuation, application, actor);
        valuation.setVehicleId(vehicle.getId()); valuation.setVehicleRevision(vehicle.getEvidenceRevision());
        valuation.setRecordedVersion(vehicle.getVersion() + 1);
        valuation.setMarketValue(request.marketValue()); valuation.setForcedSaleValue(request.forcedSaleValue());
        valuation.setValuedOn(request.valuedOn()); valuation.setValuerOrganization(request.valuerOrganization().trim());
        valuation.setReportDocumentId(request.reportDocumentId()); valuation.setStatus(ValuationStatus.PENDING);
        valuations.save(valuation); touch(vehicle, actor); vehicles.flush();
        audit(application, actor, "VALUATION_SUBMITTED", valuation.getId(), null, valuation);
        return summary(application);
    }

    @Transactional @PreAuthorize("hasAuthority('LOGBOOK_VALUATION_REVIEW')")
    public Summary reviewValuation(String applicationId, String valuationId, ReviewRequest request) {
        var actor = actors.requireCurrentUser(); var application = application(actor.tenantId(), applicationId, true);
        var vehicle = vehicle(application, request.expectedVersion());
        var valuation = valuations.findByTenantIdAndApplicationIdAndId(actor.tenantId(), applicationId, valuationId)
                .orElseThrow(() -> new NotFoundException("Valuation not found"));
        var current = valuations.findAllByTenantIdAndApplicationIdOrderByRecordedVersionDesc(actor.tenantId(), applicationId);
        if (current.isEmpty() || !current.getFirst().getId().equals(valuationId)
                || valuation.getVehicleRevision() != vehicle.getEvidenceRevision())
            throw new ConflictException("Only the latest valuation for the current vehicle revision can be reviewed");
        if (actor.userId().equals(valuation.getCreatedBy())) throw new ForbiddenOperationException("The valuer cannot review their own valuation");
        boolean decision = valuation.getStatus() == ValuationStatus.PENDING
                && (request.decision() == ValuationStatus.APPROVED || request.decision() == ValuationStatus.REJECTED);
        boolean revocation = valuation.getStatus() == ValuationStatus.APPROVED && request.decision() == ValuationStatus.REVOKED;
        if (!decision && !revocation) throw new ConflictException("Invalid valuation decision transition");
        if (request.decision() == ValuationStatus.APPROVED && !fresh(valuation, configuration(application)))
            throw new BadRequestException("Expired valuation cannot be approved; submit a new report");
        evidence(application, valuation.getReportDocumentId());
        var before = encode(valuation);
        valuation.setStatus(request.decision()); valuation.setReviewedBy(actor.userId());
        valuation.setReviewedAt(clock.instant()); valuation.setReviewReason(request.reason().trim());
        valuations.save(valuation); touch(vehicle, actor); vehicles.flush();
        audit(application, actor, "VALUATION_" + request.decision(), valuation.getId(), before, valuation);
        return summary(application);
    }

    @Transactional @PreAuthorize("hasAuthority('LOGBOOK_VERIFICATION_MANAGE')")
    public Summary verify(String applicationId, VerificationKind kind, VerificationRequest request) {
        var actor = actors.requireCurrentUser(); var application = application(actor.tenantId(), applicationId, true);
        var vehicle = vehicle(application, request.expectedVersion());
        if (kind == VerificationKind.INSURANCE) {
            if (request.validFrom() == null || request.validUntil() == null || request.validUntil().isBefore(request.validFrom()))
                throw new BadRequestException("Insurance requires a valid inclusive coverage period");
        } else if (request.validFrom() != null || request.validUntil() != null) {
            throw new BadRequestException("Coverage dates apply only to insurance");
        }
        evidence(application, request.documentId());
        var verification = new LogbookVerification(); initialize(verification, application, actor);
        verification.setVehicleId(vehicle.getId()); verification.setVehicleRevision(vehicle.getEvidenceRevision());
        verification.setRecordedVersion(vehicle.getVersion() + 1); verification.setKind(kind); verification.setStatus(request.status());
        verification.setReferenceNumber(request.referenceNumber().trim()); verification.setDocumentId(request.documentId());
        verification.setValidFrom(request.validFrom()); verification.setValidUntil(request.validUntil()); verification.setNotes(request.notes().trim());
        verifications.save(verification); touch(vehicle, actor); vehicles.flush();
        audit(application, actor, kind + "_" + request.status(), verification.getId(), null, verification);
        return summary(application);
    }

    private LoanRequestApplication application(String tenantId, String id, boolean write) {
        if (tenantId == null || tenantId.isBlank()) throw new ForbiddenOperationException("A tenant user is required");
        var application = (write ? applications.findForLogbookUpdateByTenantIdAndId(tenantId, id)
                : applications.findByIdAndTenantId(id, tenantId))
                .orElseThrow(() -> new NotFoundException("Loan request application not found"));
        var profile = profile(application);
        try {
            Map<Stage,List<Requirement>> requirements = json.readValue(profile.getRequirementsJson(), new TypeReference<>() {});
            if (requirements.values().stream().filter(Objects::nonNull).flatMap(List::stream)
                    .noneMatch(r -> r == Requirement.VALUATION_APPROVED))
                throw new BadRequestException("Application profile does not support vehicle valuation capabilities");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Invalid stored profile requirements", e); }
        if (write && (application.isInternalApproved() || application.getFineractLoanId() != null
                || application.getStatus() == ApplicationStatus.REJECTED || application.getStatus() == ApplicationStatus.LOAN_CLOSED))
            throw new ConflictException("Logbook evidence is locked after internal approval, loan creation or application closure");
        return application;
    }
    private OriginationProfile profile(LoanRequestApplication application) {
        if (application.getOriginationProfileId() == null) throw new BadRequestException("Application origination profile must be reconciled first");
        return profiles.findByTenantIdAndId(application.getTenantId(), application.getOriginationProfileId())
                .orElseThrow(() -> new NotFoundException("Application profile not found"));
    }
    private Configuration configuration(LoanRequestApplication application) {
        try {
            var config = json.readValue(profile(application).getConfigurationJson(), Configuration.class);
            if (config == null || config.valuationBasis() == null || config.maxLtvRatio() == null
                    || config.maxLtvRatio().signum() <= 0 || config.maxLtvRatio().compareTo(BigDecimal.ONE) > 0
                    || config.valuationValidityDays() == null || config.valuationValidityDays() < 1)
                throw new BadRequestException("Profile valuation configuration is incomplete");
            return config;
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            throw new BadRequestException("Profile valuation configuration is invalid");
        }
    }
    private LogbookVehicle vehicle(LoanRequestApplication app, Long version) {
        var vehicle = vehicles.findByTenantIdAndApplicationId(app.getTenantId(), app.getId())
                .orElseThrow(() -> new BadRequestException("Capture the vehicle first"));
        expected(vehicle, version); return vehicle;
    }
    private void expected(LogbookVehicle vehicle, Long version) {
        if (version == null || version != vehicle.getVersion()) throw new ConflictException("Vehicle changed; reload and use its current version");
    }
    private void evidence(LoanRequestApplication app, String documentId) {
        var document = documents.findByIdAndTenantId(documentId, app.getTenantId())
                .orElseThrow(() -> new NotFoundException("Evidence document not found"));
        if (!app.getId().equals(document.getApplicationId())) throw new BadRequestException("Evidence document must belong to this application");
    }
    private void initialize(LogbookRecord record, LoanRequestApplication app, AuthenticatedUser actor) {
        record.setId(UUID.randomUUID().toString()); record.setTenantId(app.getTenantId()); record.setApplicationId(app.getId());
        record.setCreatedBy(actor.userId()); record.setCreatedAt(clock.instant());
    }
    private void touch(LogbookVehicle vehicle, AuthenticatedUser actor) {
        // Monotonic even for fixed clocks: every child change must advance the aggregate optimistic version.
        var now = clock.instant();
        vehicle.setUpdatedAt(vehicle.getUpdatedAt() != null && !now.isAfter(vehicle.getUpdatedAt()) ? vehicle.getUpdatedAt().plusMillis(1) : now);
        vehicle.setUpdatedBy(actor.userId());
    }
    private void audit(LoanRequestApplication app, AuthenticatedUser actor, String action, String recordId, String before, Object after) {
        var audit = new LogbookAudit(); initialize(audit, app, actor); audit.setAction(action); audit.setRecordId(recordId);
        audit.setBeforeJson(before); audit.setAfterJson(encode(after)); audits.save(audit);
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Cannot serialize logbook audit", e); }
    }
    private Summary summary(LoanRequestApplication app) {
        var vehicle = vehicles.findByTenantIdAndApplicationId(app.getTenantId(), app.getId()).orElse(null);
        var values = valuations.findAllByTenantIdAndApplicationIdOrderByRecordedVersionDesc(app.getTenantId(), app.getId());
        var checks = verifications.findAllByTenantIdAndApplicationIdOrderByRecordedVersionDesc(app.getTenantId(), app.getId());
        var config = configuration(app);
        boolean ownership = checked(checks, vehicle, VerificationKind.OWNERSHIP);
        boolean insurance = checked(checks, vehicle, VerificationKind.INSURANCE);
        boolean security = checked(checks, vehicle, VerificationKind.SECURITY_REGISTRATION);
        var valuation = values.isEmpty() ? null : values.getFirst();
        boolean approved = vehicle != null && valuation != null && valuation.getVehicleRevision() == vehicle.getEvidenceRevision()
                && valuation.getStatus() == ValuationStatus.APPROVED && fresh(valuation, config);
        BigDecimal limit = approved ? (config.valuationBasis() == ValuationBasis.MARKET_VALUE ? valuation.getMarketValue() : valuation.getForcedSaleValue())
                .multiply(config.maxLtvRatio()).setScale(2, RoundingMode.DOWN) : null;
        boolean within = limit != null && app.getRequestedAmount() != null && app.getRequestedAmount().signum() > 0 && app.getRequestedAmount().compareTo(limit) <= 0;
        var missing = new ArrayList<String>();
        if (vehicle == null) missing.add("VEHICLE_CAPTURED");
        if (!ownership) missing.add("VEHICLE_OWNERSHIP_VERIFIED");
        if (!approved) missing.add("VALUATION_APPROVED");
        if (!insurance) missing.add("INSURANCE_VALID");
        if (!security) missing.add("SECURITY_REGISTRATION_CONFIRMED");
        if (!within) missing.add("REQUESTED_AMOUNT_WITHIN_LTV");
        return new Summary(vehicle, values, checks, new Readiness(vehicle != null, ownership, approved, insurance, security, "KES", limit, within, missing));
    }
    private boolean fresh(LogbookValuation valuation, Configuration config) {
        var today = today();
        return !valuation.getValuedOn().isAfter(today) && today.isBefore(valuation.getValuedOn().plusDays(config.valuationValidityDays()));
    }
    private boolean checked(List<LogbookVerification> checks, LogbookVehicle vehicle, VerificationKind kind) {
        if (vehicle == null) return false;
        var latest = checks.stream().filter(c -> c.getKind() == kind).findFirst().orElse(null);
        if (latest == null || latest.getVehicleRevision() != vehicle.getEvidenceRevision() || latest.getStatus() != VerificationStatus.VERIFIED) return false;
        return kind != VerificationKind.INSURANCE || (latest.getValidFrom() != null && latest.getValidUntil() != null
                && !today().isBefore(latest.getValidFrom()) && !today().isAfter(latest.getValidUntil()));
    }
    private LocalDate today() { return LocalDate.now(clock); }
    private String normalizeIdentifier(String value) { return value.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT); }
}
