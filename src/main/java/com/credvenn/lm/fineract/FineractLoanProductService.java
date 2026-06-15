package com.credvenn.lm.fineract;

import com.credvenn.lm.common.api.PagedResponse;
import com.credvenn.lm.common.api.PaginationSupport;
import com.credvenn.lm.security.CurrentActorService;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FineractLoanProductService {

    private static final Map<String, java.util.function.Function<FineractDtos.LoanProductResponse, ? extends Comparable<?>>> LOAN_PRODUCT_SORTS = new LinkedHashMap<>();

    static {
        LOAN_PRODUCT_SORTS.put("name", FineractDtos.LoanProductResponse::name);
        LOAN_PRODUCT_SORTS.put("shortName", FineractDtos.LoanProductResponse::shortName);
        LOAN_PRODUCT_SORTS.put("minPrincipal", FineractDtos.LoanProductResponse::minPrincipal);
        LOAN_PRODUCT_SORTS.put("maxPrincipal", FineractDtos.LoanProductResponse::maxPrincipal);
        LOAN_PRODUCT_SORTS.put("minNumberOfRepayments", FineractDtos.LoanProductResponse::minNumberOfRepayments);
        LOAN_PRODUCT_SORTS.put("maxNumberOfRepayments", FineractDtos.LoanProductResponse::maxNumberOfRepayments);
        LOAN_PRODUCT_SORTS.put("numberOfRepayments", FineractDtos.LoanProductResponse::numberOfRepayments);
        LOAN_PRODUCT_SORTS.put("interestRatePerPeriod", FineractDtos.LoanProductResponse::interestRatePerPeriod);
        LOAN_PRODUCT_SORTS.put("currencyCode", FineractDtos.LoanProductResponse::currencyCode);
    }

    private final CurrentActorService currentActorService;
    private final TenantService tenantService;
    private final FineractGateway fineractGateway;

    public FineractLoanProductService(
            CurrentActorService currentActorService,
            TenantService tenantService,
            FineractGateway fineractGateway) {
        this.currentActorService = currentActorService;
        this.tenantService = tenantService;
        this.fineractGateway = fineractGateway;
    }

    @Transactional(readOnly = true)
    public List<FineractDtos.LoanProductResponse> listCurrentTenantLoanProducts() {
        Tenant tenant = currentTenant();
        return fineractGateway.fetchActiveLoanProducts(tenant).stream().map(FineractDtos.LoanProductResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<FineractDtos.LoanProductResponse> listCurrentTenantLoanProducts(
            Integer page,
            Integer size,
            String sortBy,
            String sortDir) {
        Tenant tenant = currentTenant();
        List<FineractDtos.LoanProductResponse> items = fineractGateway.fetchActiveLoanProducts(tenant).stream()
                .map(FineractDtos.LoanProductResponse::from)
                .toList();
        return PaginationSupport.paginateList(items, page, size, sortBy, sortDir, LOAN_PRODUCT_SORTS, "name");
    }

    private Tenant currentTenant() {
        String tenantId = currentActorService.requireCurrentUser().tenantId();
        return tenantService.getRequiredTenant(tenantId);
    }
}
