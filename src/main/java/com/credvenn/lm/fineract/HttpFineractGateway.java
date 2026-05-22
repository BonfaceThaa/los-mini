package com.credvenn.lm.fineract;

import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.common.logging.LoggingContext;
import com.credvenn.lm.tenant.Tenant;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpFineractGateway implements FineractGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpFineractGateway.class);

    private final RestClient restClient;
    private final FineractProperties properties;

    public HttpFineractGateway(@Qualifier("fineractRestClient") RestClient fineractRestClient, FineractProperties properties) {
        this.restClient = fineractRestClient;
        this.properties = properties;
    }

    @Override
    public String createClient(Tenant tenant, LoanRequestApplication application) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenant.getId(), application.getId())) {
            log.info("Creating Fineract client for tenantFineractId={}", tenant.getFineractTenantId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("officeId", properties.defaultOfficeId());
            payload.put("legalFormId", properties.resolvedLegalFormId());
            payload.put("firstname", application.getApplicantFirstName());
            payload.put("lastname", application.getApplicantLastName());
            payload.put("mobileNo", application.getPhoneNumber());
            payload.put("externalId", application.getId());
            payload.put("active", true);
            payload.put("activationDate", today());
            payload.put("submittedOnDate", today());
            payload.put("dateFormat", properties.dateFormat());
            payload.put("locale", properties.locale());
            Map<?, ?> response = post("/clients", tenant, payload);
            String resourceId = extractResourceId(response);
            log.info("Created Fineract client resourceId={}", resourceId);
            return resourceId;
        }
    }

    @Override
    public List<FineractLoanProduct> fetchActiveLoanProducts(Tenant tenant) {
        log.debug("Fetching active Fineract loan products for tenantFineractId={}", tenant.getFineractTenantId());
        Object response = restClient.get()
                .uri("/loanproducts")
                .headers(headers -> applyHeaders(headers, tenant))
                .retrieve()
                .body(Object.class);
        List<Map<String, Object>> items = new ArrayList<>();
        if (response instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    items.add((Map<String, Object>) map);
                }
            }
        } else if (response instanceof Map<?, ?> map && map.get("pageItems") instanceof List<?> pageItems) {
            for (Object item : pageItems) {
                if (item instanceof Map<?, ?> child) {
                    items.add((Map<String, Object>) child);
                }
            }
        }
        List<FineractLoanProduct> products = items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getOrDefault("active", Boolean.TRUE)))
                .map(this::toLoanProduct)
                .toList();
        log.info("Fetched {} active Fineract loan products for tenantFineractId={}", products.size(), tenant.getFineractTenantId());
        return products;
    }

    @Override
    public List<FineractClient> fetchClients(Tenant tenant) {
        log.info("Fetching Fineract clients for tenantFineractId={}", tenant.getFineractTenantId());
        Object response = restClient.get()
                .uri("/clients")
                .headers(headers -> applyHeaders(headers, tenant))
                .retrieve()
                .body(Object.class);
        List<Map<String, Object>> items = new ArrayList<>();
        if (response instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    items.add((Map<String, Object>) map);
                }
            }
        } else if (response instanceof Map<?, ?> map) {
            Object pageItems = map.get("pageItems");
            if (pageItems instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> child) {
                        items.add((Map<String, Object>) child);
                    }
                }
            }
        }
        List<FineractClient> clients = items.stream().map(this::toClient).toList();
        log.info("Fetched {} Fineract clients for tenantFineractId={}", clients.size(), tenant.getFineractTenantId());
        return clients;
    }

    @Override
    public FineractClient fetchClient(Tenant tenant, String fineractClientId) {
        log.info("Fetching Fineract client fineractClientId={} tenantFineractId={}", fineractClientId, tenant.getFineractTenantId());
        Object response = restClient.get()
                .uri("/clients/{clientId}", fineractClientId)
                .headers(headers -> applyHeaders(headers, tenant))
                .retrieve()
                .body(Object.class);
        if (response instanceof Map<?, ?> map) {
            return toClient((Map<String, Object>) map);
        }
        throw new NotFoundException("Fineract client not found");
    }

    @Override
    public String createLoanProduct(Tenant tenant, CreateLoanProductRequest request) {
        log.info("Creating Fineract loan product name={} currencyCode={} numberOfRepayments={}", request.name(), request.currencyCode(), request.numberOfRepayments());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", request.name());
        payload.put("shortName", request.shortName());
        payload.put("description", request.description());
        payload.put("currencyCode", request.currencyCode());
        payload.put("digitsAfterDecimal", 2);
        payload.put("inMultiplesOf", 1);
        payload.put("principal", request.principal());
        payload.put("minPrincipal", request.minPrincipal());
        payload.put("maxPrincipal", request.maxPrincipal());
        payload.put("numberOfRepayments", request.numberOfRepayments());
        payload.put("minNumberOfRepayments", request.numberOfRepayments());
        payload.put("maxNumberOfRepayments", request.numberOfRepayments());
        payload.put("repaymentEvery", request.repaymentEvery());
        payload.put("repaymentFrequencyType", request.repaymentFrequencyType());
        payload.put("interestRatePerPeriod", request.interestRatePerPeriod());
        payload.put("interestRateFrequencyType", request.interestRateFrequencyType());
        payload.put("amortizationType", request.amortizationType());
        payload.put("interestType", request.interestType());
        payload.put("interestCalculationPeriodType", request.interestCalculationPeriodType());
        payload.put("transactionProcessingStrategyCode",
                request.transactionProcessingStrategyCode() == null || request.transactionProcessingStrategyCode().isBlank()
                        ? properties.transactionProcessingStrategyCode()
                        : request.transactionProcessingStrategyCode());
        payload.put("accountingRule", 2);
        payload.put("loanPortfolioAccountId", request.loanPortfolioAccountId());
        payload.put("fundSourceAccountId", request.fundSourceAccountId());
        payload.put("interestOnLoanAccountId", request.interestOnLoanAccountId());
        payload.put("incomeFromFeeAccountId", request.incomeFromFeeAccountId());
        payload.put("incomeFromPenaltyAccountId", request.incomeFromPenaltyAccountId());
        payload.put("incomeFromRecoveryAccountId", request.incomeFromRecoveryAccountId());
        payload.put("writeOffAccountId", request.writeOffAccountId());
        payload.put("transfersInSuspenseAccountId", request.transfersInSuspenseAccountId());
        payload.put("overpaymentLiabilityAccountId", request.overpaymentLiabilityAccountId());
        payload.put("dateFormat", properties.dateFormat());
        payload.put("locale", properties.locale());
        payload.put("isInterestRecalculationEnabled", false);
        payload.put("daysInMonthType", 1);
        payload.put("daysInYearType", 1);
        Map<?, ?> response = post("/loanproducts", tenant, payload);
        String resourceId = extractResourceId(response);
        log.info("Created Fineract loan product resourceId={}", resourceId);
        return resourceId;
    }

    @Override
    public String createPendingLoan(
            Tenant tenant,
            LoanRequestApplication application,
            FineractLoanProduct product,
            BigDecimal approvedAmount,
            Integer approvedTermMonths) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenant.getId(), application.getId())) {
            int repaymentEvery = product.repaymentEvery() == null ? 1 : product.repaymentEvery();
            int numberOfRepayments = product.numberOfRepayments() == null ? approvedTermMonths : product.numberOfRepayments();
            int loanTermFrequency = deriveLoanTermFrequency(approvedTermMonths, repaymentEvery, numberOfRepayments);
            log.info(
                    "Creating pending Fineract loan for fineractClientId={} fineractProductId={} approvedAmount={} approvedTermMonths={} loanTermFrequency={} repaymentEvery={} numberOfRepayments={}",
                    application.getFineractClientId(),
                    product.id(),
                    approvedAmount,
                    approvedTermMonths,
                    loanTermFrequency,
                    repaymentEvery,
                    numberOfRepayments);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("clientId", Long.parseLong(application.getFineractClientId()));
            payload.put("productId", Long.parseLong(product.id()));
            payload.put("loanType", product.loanType() == null ? "individual" : product.loanType());
            payload.put("interestType", product.interestType());
            payload.put("interestCalculationPeriodType", product.interestCalculationPeriodType());
            payload.put("interestRatePerPeriod", product.interestRatePerPeriod());
            payload.put("amortizationType", product.amortizationType());
            payload.put("principal", approvedAmount);
            payload.put("loanTermFrequency", loanTermFrequency);
            payload.put("loanTermFrequencyType", product.repaymentFrequencyType() == null ? 2 : product.repaymentFrequencyType());
            payload.put("numberOfRepayments", numberOfRepayments);
            payload.put("repaymentEvery", repaymentEvery);
            payload.put("repaymentFrequencyType", product.repaymentFrequencyType() == null ? 2 : product.repaymentFrequencyType());
            payload.put("submittedOnDate", today());
            payload.put("expectedDisbursementDate", today());
            payload.put("dateFormat", properties.dateFormat());
            payload.put("locale", properties.locale());
            payload.put("transactionProcessingStrategyCode", "mifos-standard-strategy");
            Map<?, ?> response = post("/loans", tenant, payload);
            String resourceId = extractResourceId(response);
            log.info("Created pending Fineract loan resourceId={}", resourceId);
            return resourceId;
        }
    }

    @Override
    public void activateLoan(Tenant tenant, LoanRequestApplication application) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenant.getId(), application.getId())) {
            String fineractLoanId = application.getFineractLoanId();
            log.info("Approving and disbursing Fineract loan fineractLoanId={} tenantFineractId={}", fineractLoanId, tenant.getFineractTenantId());

            LoanSummary initial = getLoanSummary(tenant, fineractLoanId);
            if (initial.active()) {
                log.info("Fineract loan is already active fineractLoanId={} statusCode={}", fineractLoanId, initial.statusCode());
                return;
            }

            if (!isApprovedStatus(initial.statusCode())) {
                Map<String, Object> approvePayload = new LinkedHashMap<>();
                approvePayload.put("approvedOnDate", today());
                approvePayload.put("dateFormat", properties.dateFormat());
                approvePayload.put("locale", properties.locale());
                post("/loans/%s?command=approve".formatted(fineractLoanId), tenant, approvePayload);
                log.info("Approved Fineract loan fineractLoanId={}", fineractLoanId);
            } else {
                log.info("Skipping Fineract approve because loan is already approved fineractLoanId={} statusCode={}", fineractLoanId, initial.statusCode());
            }

            LoanSummary afterApprove = getLoanSummary(tenant, fineractLoanId);
            if (!afterApprove.active()) {
                if (application.getApprovedAmount() == null) {
                    throw new BadRequestException("Approved amount is required before Fineract loan disbursement");
                }
                Map<String, Object> disbursePayload = new LinkedHashMap<>();
                disbursePayload.put("actualDisbursementDate", today());
                disbursePayload.put("transactionAmount", application.getApprovedAmount());
                disbursePayload.put("dateFormat", properties.dateFormat());
                disbursePayload.put("locale", properties.locale());
                post("/loans/%s?command=disburse".formatted(fineractLoanId), tenant, disbursePayload);
                log.info("Disbursed Fineract loan fineractLoanId={} transactionAmount={}", fineractLoanId, application.getApprovedAmount());
            }

            LoanSummary finalSummary = getLoanSummary(tenant, fineractLoanId);
            if (!finalSummary.active()) {
                throw new BadRequestException("Fineract loan did not become active after approve/disburse");
            }
            log.info("Fineract loan is active fineractLoanId={} statusCode={}", fineractLoanId, finalSummary.statusCode());
        }
    }

    @Override
    public LoanSummary getLoanSummary(Tenant tenant, String fineractLoanId) {
        Object response = restClient.get()
                .uri("/loans/{loanId}", fineractLoanId)
                .headers(headers -> applyHeaders(headers, tenant))
                .retrieve()
                .body(Object.class);
        if (response instanceof Map<?, ?> map) {
            Object statusValue = map.get("status");
            if (statusValue instanceof Map<?, ?> statusMap) {
                boolean active = Boolean.TRUE.equals(statusMap.get("active"));
                String statusCode = text(statusMap.get("code"));
                return new LoanSummary(fineractLoanId, active, statusCode);
            }
        }
        return new LoanSummary(fineractLoanId, false, null);
    }

    @Override
    public LoanPage fetchLoansPage(Tenant tenant, int offset, int limit) {
        Object response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/loans")
                        .queryParam("offset", offset)
                        .queryParam("limit", limit)
                        .build())
                .headers(headers -> applyHeaders(headers, tenant))
                .retrieve()
                .body(Object.class);
        List<Map<String, Object>> items = extractItems(response, "pageItems", "content", "loans");
        List<LoanPageItem> loans = items.stream()
                .map(this::toLoanPageItem)
                .toList();
        boolean hasNext = loans.size() >= limit;
        return new LoanPage(loans, offset, limit, hasNext);
    }

    @Override
    public LoanCollectionsSnapshot fetchLoanCollectionsSnapshot(Tenant tenant, String fineractLoanId) {
        Object response = restClient.get()
                .uri("/loans/{loanId}?associations=repaymentSchedule", fineractLoanId)
                .headers(headers -> applyHeaders(headers, tenant))
                .retrieve()
                .body(Object.class);
        if (!(response instanceof Map<?, ?> map)) {
            return new LoanCollectionsSnapshot(fineractLoanId, false, false, null, 0, null, List.of());
        }
        Map<?, ?> status = map.get("status") instanceof Map<?, ?> statusMap ? statusMap : Map.of();
        boolean active = Boolean.TRUE.equals(status.get("active"));
        Object repaymentScheduleValue = map.get("repaymentSchedule");
        List<InstallmentSnapshot> installments = new ArrayList<>();
        LocalDate oldestOverdueDate = null;
        LocalDate nextDueDate = null;
        LocalDate today = LocalDate.now();
        if (repaymentScheduleValue instanceof Map<?, ?> repaymentSchedule
                && repaymentSchedule.get("periods") instanceof List<?> periods) {
            int index = 0;
            for (Object item : periods) {
                if (!(item instanceof Map<?, ?> period)) {
                    continue;
                }
                index++;
                LocalDate dueDate = localDate(period.get("dueDate"));
                if (dueDate == null) {
                    continue;
                }
                BigDecimal dueAmount = decimal(firstNonNull(
                        period.get("totalDueForPeriod"),
                        period.get("totalOriginalDueForPeriod"),
                        period.get("totalInstallmentAmountForPeriod")));
                BigDecimal paidAmount = decimal(firstNonNull(
                        period.get("totalPaidForPeriod"),
                        period.get("totalPaidInAdvanceForPeriod"),
                        period.get("totalPaidLateForPeriod")));
                BigDecimal outstandingAmount = decimal(firstNonNull(
                        period.get("totalOutstandingForPeriod"),
                        period.get("totalOutstandingLoanBalance"),
                        period.get("outstandingPrincipalBalance")));
                boolean fullyPaid = Boolean.TRUE.equals(period.get("complete"))
                        || Boolean.TRUE.equals(period.get("fullyPaid"))
                        || (outstandingAmount != null && outstandingAmount.compareTo(BigDecimal.ZERO) <= 0);
                boolean overdue = !fullyPaid && dueDate.isBefore(today);
                if (!fullyPaid && nextDueDate == null && !dueDate.isBefore(today)) {
                    nextDueDate = dueDate;
                }
                if (overdue && (oldestOverdueDate == null || dueDate.isBefore(oldestOverdueDate))) {
                    oldestOverdueDate = dueDate;
                }
                installments.add(new InstallmentSnapshot(
                        index,
                        dueDate,
                        dueAmount,
                        paidAmount,
                        outstandingAmount,
                        overdue,
                        fullyPaid));
            }
        }
        boolean hasOverdue = oldestOverdueDate != null;
        long daysOverdue = hasOverdue ? ChronoUnit.DAYS.between(oldestOverdueDate, today) : 0;
        return new LoanCollectionsSnapshot(
                fineractLoanId,
                active,
                hasOverdue,
                oldestOverdueDate,
                daysOverdue,
                nextDueDate,
                installments);
    }

    @Override
    public List<LoanRepayment> fetchLoanRepayments(Tenant tenant, String fineractLoanId) {
        log.info("Fetching Fineract loan repayments fineractLoanId={} tenantFineractId={}", fineractLoanId, tenant.getFineractTenantId());
        Object response = restClient.get()
                .uri("/loans/{loanId}/transactions", fineractLoanId)
                .headers(headers -> applyHeaders(headers, tenant))
                .retrieve()
                .body(Object.class);
        List<Map<String, Object>> items = extractItems(response, "content", "pageItems", "transactions");
        List<LoanRepayment> repayments = items.stream()
                .filter(this::isRepaymentTransaction)
                .map(this::toLoanRepayment)
                .toList();
        log.info("Fetched {} Fineract loan repayments fineractLoanId={}", repayments.size(), fineractLoanId);
        return repayments;
    }

    @Override
    public String postLoanRepayment(Tenant tenant, String fineractLoanId, LoanRepaymentRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dateFormat", "dd MMMM yyyy");
        payload.put("locale", "en");
        payload.put("transactionDate", request.transactionDate());
        payload.put("transactionAmount", request.transactionAmount());
        payload.put("paymentTypeId", request.paymentTypeId());
        payload.put("note", request.note());
        payload.put("accountNumber", request.accountNumber());
        payload.put("checkNumber", request.checkNumber());
        payload.put("routingCode", request.routingCode());
        payload.put("receiptNumber", request.receiptNumber());
        payload.put("bankNumber", request.bankNumber());
        Map<?, ?> response = post("/loans/%s/transactions?command=repayment".formatted(fineractLoanId), tenant, payload);
        return extractResourceId(response);
    }

    private Map<?, ?> post(String path, Tenant tenant, Map<String, Object> payload) {
        return restClient.post()
                .uri(path)
                .headers(headers -> applyHeaders(headers, tenant))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);
    }

    private List<Map<String, Object>> extractItems(Object response, String... keys) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (response instanceof List<?> list) {
            addMaps(items, list);
            return items;
        }
        if (response instanceof Map<?, ?> map) {
            for (String key : keys) {
                Object value = map.get(key);
                if (value instanceof List<?> list) {
                    addMaps(items, list);
                    if (!items.isEmpty()) {
                        return items;
                    }
                }
            }
        }
        return items;
    }

    private void addMaps(List<Map<String, Object>> items, List<?> list) {
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                items.add((Map<String, Object>) map);
            }
        }
    }

    private void applyHeaders(HttpHeaders headers, Tenant tenant) {
        String credentials = properties.username() + ":" + properties.password();
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        // headers.set("Fineract-Platform-TenantId", tenant.getFineractTenantId());
        headers.set("Fineract-Platform-TenantId", "default");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    }

    private String extractResourceId(Map<?, ?> response) {
        Object resourceId = response == null ? null : response.get("resourceId");
        if (resourceId == null) {
            throw new BadRequestException("Fineract response did not include a resourceId");
        }
        return String.valueOf(resourceId);
    }

    private FineractLoanProduct toLoanProduct(Map<String, Object> item) {
        return new FineractLoanProduct(
                String.valueOf(item.get("id")),
                text(item.get("name")),
                text(item.get("shortName")),
                decimal(item.get("minPrincipal")),
                decimal(item.get("maxPrincipal")),
                integer(item.get("minNumberOfRepayments")),
                integer(item.get("maxNumberOfRepayments")),
                integer(item.get("loanType")),
                integer(item.get("interestType")),
                integer(item.get("interestCalculationPeriodType")),
                decimal(item.get("interestRatePerPeriod")),
                integer(item.get("amortizationType")),
                integer(item.get("interestRateFrequencyType")),
                integer(item.get("repaymentEvery")),
                integer(item.get("repaymentFrequencyType")),
                integer(item.get("numberOfRepayments")),
                currencyCode(item.get("currency")),
                Boolean.TRUE.equals(item.getOrDefault("active", Boolean.TRUE)));
    }

    private FineractClient toClient(Map<String, Object> item) {
        Map<?, ?> status = item.get("status") instanceof Map<?, ?> statusMap ? statusMap : Map.of();
        return new FineractClient(
                text(item.get("id")),
                text(item.get("accountNo")),
                text(item.get("externalId")),
                text(status.get("value")),
                Boolean.TRUE.equals(item.get("active")) || Boolean.TRUE.equals(status.get("active")),
                text(item.get("firstname")),
                text(item.get("middlename")),
                text(item.get("lastname")),
                text(item.get("displayName")),
                text(item.get("mobileNo")),
                text(item.get("officeName")));
    }

    private LoanPageItem toLoanPageItem(Map<String, Object> item) {
        Map<?, ?> status = item.get("status") instanceof Map<?, ?> statusMap ? statusMap : Map.of();
        boolean active = Boolean.TRUE.equals(status.get("active")) || Boolean.TRUE.equals(item.get("active"));
        return new LoanPageItem(
                text(item.get("id")),
                active,
                text(status.get("code")));
    }

    private LoanRepayment toLoanRepayment(Map<String, Object> item) {
        Map<?, ?> type = item.get("type") instanceof Map<?, ?> typeMap ? typeMap : Map.of();
        return new LoanRepayment(
                text(item.get("id")),
                instant(item.get("date")),
                decimal(item.get("amount")),
                decimal(item.get("principalPortion")),
                decimal(item.get("interestPortion")),
                decimal(item.get("feeChargesPortion")),
                decimal(item.get("penaltyChargesPortion")),
                decimal(item.get("overpaymentPortion")),
                decimal(item.get("outstandingLoanBalance")),
                Boolean.TRUE.equals(item.get("manuallyReversed")) || Boolean.TRUE.equals(item.get("reversed")),
                text(type.get("code")),
                text(type.get("value")));
    }

    private boolean isRepaymentTransaction(Map<String, Object> item) {
        Object typeValue = item.get("type");
        if (typeValue instanceof Map<?, ?> map) {
            String code = text(map.get("code"));
            String value = text(map.get("value"));
            return containsIgnoreCase(code, "repayment") || containsIgnoreCase(value, "repayment");
        }
        return false;
    }

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern(properties.dateFormat()));
    }

    private String date(java.time.Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ofPattern(properties.dateFormat()));
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal decimal(Object value) {
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof Map<?, ?> map) {
            Object id = map.get("id");
            if (id == null) {
                return null;
            }
            if (id instanceof Number number) {
                return number.intValue();
            }
            return Integer.parseInt(String.valueOf(id));
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private String currencyCode(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object code = map.get("code");
            return code == null ? null : String.valueOf(code);
        }
        return null;
    }

    private Instant instant(Object value) {
        if (value instanceof List<?> parts && parts.size() >= 3) {
            int year = integer(parts.get(0));
            int month = integer(parts.get(1));
            int day = integer(parts.get(2));
            return LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC);
        }
        if (value instanceof String text && !text.isBlank()) {
            return LocalDate.parse(text.trim()).atStartOfDay().toInstant(ZoneOffset.UTC);
        }
        return null;
    }

    private LocalDate localDate(Object value) {
        if (value instanceof List<?> parts && parts.size() >= 3) {
            int year = integer(parts.get(0));
            int month = integer(parts.get(1));
            int day = integer(parts.get(2));
            return LocalDate.of(year, month, day);
        }
        if (value instanceof String text && !text.isBlank()) {
            return LocalDate.parse(text.trim());
        }
        return null;
    }

    private boolean containsIgnoreCase(String value, String fragment) {
        return value != null && value.toLowerCase().contains(fragment.toLowerCase());
    }

    private boolean isApprovedStatus(String statusCode) {
        return containsIgnoreCase(statusCode, "approved") || containsIgnoreCase(statusCode, "active");
    }

    private int deriveLoanTermFrequency(Integer approvedTermMonths, int repaymentEvery, int numberOfRepayments) {
        int fallback = approvedTermMonths == null ? numberOfRepayments : approvedTermMonths;
        long suggested = (long) repaymentEvery * numberOfRepayments;
        return (int) Math.max(fallback, suggested);
    }

    private BigDecimal annualInterestRate(BigDecimal interestRatePerPeriod, Integer interestRateFrequencyType) {
        if (interestRatePerPeriod == null) {
            return BigDecimal.ZERO;
        }
        int multiplier = switch (interestRateFrequencyType == null ? 2 : interestRateFrequencyType) {
            case 0, 1 -> 365;
            case 2 -> 12;
            case 3 -> 52;
            case 4 -> 1;
            default -> 12;
        };
        return interestRatePerPeriod.multiply(BigDecimal.valueOf(multiplier));
    }
}
