package com.credvenn.lm.loanproduct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.credvenn.lm.fineract.FineractGateway;
import com.credvenn.lm.fineracttemplate.GlAccountTemplateRepository;
import com.credvenn.lm.security.AuthenticatedUser;
import com.credvenn.lm.security.CurrentActorService;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LoanProductCatalogServiceTest {

    @Test
    void updateCurrentTenantProductMergesPatchAndPersistsChanges() {
        TestContext context = new TestContext();
        LoanProductMapping existing = existingMapping();
        when(context.currentActorService.requireCurrentUser()).thenReturn(
                new AuthenticatedUser("user-1", "tenant-1", "tester", "tester@example.com", List.of(), List.of()));
        when(context.loanProductMappingRepository.findByTenantIdAndShortNameIgnoreCase("tenant-1", "PH12"))
                .thenReturn(Optional.of(existing));
        when(context.tenantService.getRequiredTenant("tenant-1")).thenReturn(new Tenant());
        when(context.loanProductMappingRepository.save(any(LoanProductMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoanProductCatalogDtos.UpdateLoanProductRequest request = new LoanProductCatalogDtos.UpdateLoanProductRequest(
                "Updated Phone Loan",
                null,
                "  New description  ",
                null,
                new LoanProductCatalogDtos.PrincipalPatchRequest(null, null, new BigDecimal("18000")),
                new LoanProductCatalogDtos.TermPatchRequest(14, null, null),
                null,
                null,
                null,
                null,
                null,
                false);

        LoanProductCatalogDtos.LoanProductCatalogResponse response =
                context.service.updateCurrentTenantProductByShortName("PH12", request);

        ArgumentCaptor<FineractGateway.UpdateLoanProductRequest> gatewayCaptor =
                ArgumentCaptor.forClass(FineractGateway.UpdateLoanProductRequest.class);
        verify(context.fineractGateway).updateLoanProduct(any(Tenant.class), eq("77"), gatewayCaptor.capture());
        FineractGateway.UpdateLoanProductRequest gatewayRequest = gatewayCaptor.getValue();
        assertEquals("Updated Phone Loan", gatewayRequest.name());
        assertEquals("PH12", gatewayRequest.shortName());
        assertEquals("New description", gatewayRequest.description());
        assertEquals(new BigDecimal("18000.00"), gatewayRequest.maxPrincipal());
        assertEquals(14, gatewayRequest.numberOfRepayments());
        assertNull(gatewayRequest.loanPortfolioAccountId());

        ArgumentCaptor<LoanProductMapping> savedCaptor = ArgumentCaptor.forClass(LoanProductMapping.class);
        verify(context.loanProductMappingRepository).save(savedCaptor.capture());
        LoanProductMapping saved = savedCaptor.getValue();
        assertEquals("Updated Phone Loan", saved.getDisplayName());
        assertEquals("New description", saved.getDescription());
        assertEquals(new BigDecimal("5000.00"), saved.getPrincipalMin());
        assertEquals(new BigDecimal("7500.00"), saved.getPrincipalDefaultAmount());
        assertEquals(new BigDecimal("18000.00"), saved.getPrincipalMax());
        assertEquals(14, saved.getNumberOfRepayments());
        assertEquals(1, saved.getRepaymentEvery());
        assertEquals("WEEKS", saved.getRepaymentFrequency());
        assertFalse(saved.isActive());
        assertEquals("tester", saved.getUpdatedBy());

        assertEquals("Updated Phone Loan", response.displayName());
        assertEquals("New description", response.description());
        assertFalse(response.active());
    }

    private static LoanProductMapping existingMapping() {
        LoanProductMapping mapping = new LoanProductMapping();
        mapping.setTenantId("tenant-1");
        mapping.setProductCode("PHONE_12_WEEKS");
        mapping.setDisplayName("Phone Loan");
        mapping.setShortName("PH12");
        mapping.setDescription("Existing description");
        mapping.setCurrencyCode("KES");
        mapping.setPrincipalMin(new BigDecimal("5000.00"));
        mapping.setPrincipalDefaultAmount(new BigDecimal("7500.00"));
        mapping.setPrincipalMax(new BigDecimal("15000.00"));
        mapping.setNumberOfRepayments(12);
        mapping.setRepaymentEvery(1);
        mapping.setRepaymentFrequency("WEEKS");
        mapping.setInterestRatePerPeriod(new BigDecimal("12.5000"));
        mapping.setInterestType("FLAT");
        mapping.setInterestCalculationPeriodType("SAME_AS_REPAYMENT_PERIOD");
        mapping.setInterestRateFrequency("MONTHS");
        mapping.setAmortizationType("EQUAL_INSTALLMENTS");
        mapping.setTransactionProcessingStrategyCode("ADVANCED-PAYMENT-ALLOCATION-STRATEGY");
        mapping.setAccountingTemplateCode("STANDARD");
        mapping.setFineractProductId(77L);
        mapping.setActive(true);
        mapping.setCreatedBy("creator");
        mapping.setUpdatedBy("creator");
        return mapping;
    }

    private static final class TestContext {
        private final LoanProductMappingRepository loanProductMappingRepository = mock(LoanProductMappingRepository.class);
        private final GlAccountTemplateRepository glAccountTemplateRepository = mock(GlAccountTemplateRepository.class);
        private final CurrentActorService currentActorService = mock(CurrentActorService.class);
        private final TenantService tenantService = mock(TenantService.class);
        private final FineractGateway fineractGateway = mock(FineractGateway.class);
        private final LoanProductCatalogService service = new LoanProductCatalogService(
                loanProductMappingRepository,
                glAccountTemplateRepository,
                currentActorService,
                tenantService,
                fineractGateway);
    }
}
