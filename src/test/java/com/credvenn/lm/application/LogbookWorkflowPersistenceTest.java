package com.credvenn.lm.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.document.ApplicationDocument;
import com.credvenn.lm.fineract.*;
import com.credvenn.lm.kyc.*;
import com.credvenn.lm.loanproduct.*;
import com.credvenn.lm.logbook.*;
import com.credvenn.lm.origination.*;
import com.credvenn.lm.security.*;
import com.credvenn.lm.statement.*;
import com.credvenn.lm.subscription.SubscriptionBillingService;
import com.credvenn.lm.tenant.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named="MARIADB_MIGRATION_TEST_URL",matches=".+")
class LogbookWorkflowPersistenceTest {
    @Test void freshMigrationHasNoBackfillOrQueuedLoans() throws Exception {
        OriginationProfileMigrationTest.withDatabase((url,c)->{
            OriginationProfileMigrationTest.migrate(url,"40");
            assertEquals(0,OriginationProfileMigrationTest.scalar(c,"SELECT COUNT(*) FROM logbook_loan_operations"));
            assertEquals(0,OriginationProfileMigrationTest.scalar(c,"SELECT COUNT(*) FROM loan_request_applications"));
        });
    }
    @Test void committedQueueAndRollbackRecoveryDoNotDuplicateRemoteOperations() throws Exception {
        OriginationProfileMigrationTest.withDatabase((url,c)->{
            OriginationProfileMigrationTest.migrate(url,"37");OriginationProfileMigrationTest.legacyRows(c,"a");OriginationProfileMigrationTest.legacyRows(c,"b");
            OriginationProfileMigrationTest.migrate(url,"39");OriginationProfileMigrationTest.migrate(url,"40");
            assertEquals(2,OriginationProfileMigrationTest.scalar(c,"SELECT COUNT(*) FROM loan_request_applications WHERE fineract_loan_id='existing-loan'"));
            var json=new ObjectMapper().findAndRegisterModules();
            try(var statement=c.prepareStatement("UPDATE origination_profiles SET requirements_json=?,configuration_json=?,active=TRUE WHERE tenant_id='a'")){
                statement.setString(1,json.writeValueAsString(OriginationProfileValidator.LOGBOOK));
                statement.setString(2,"{\"valuationBasis\":\"FORCED_SALE_VALUE\",\"maxLtvRatio\":0.6,\"valuationValidityDays\":30}");statement.executeUpdate();
            }
            OriginationProfileMigrationTest.execute(c,"UPDATE tenants SET statement_analysis_mode='DISABLED' WHERE id='a'");
            OriginationProfileMigrationTest.execute(c,"UPDATE loan_request_applications SET fineract_loan_id=NULL,fineract_client_id='3',selected_loan_product_mapping_id='product-a',selected_fineract_product_id='7',status='OFFER_SELECTED',consent_captured=TRUE,requested_amount=480000 WHERE tenant_id='a'");
            OriginationProfileMigrationTest.execute(c,"UPDATE loan_product_mappings SET principal_max=2000000,number_of_repayments=12 WHERE tenant_id='a'");
            var config=new Configuration().addAnnotatedClass(LoanRequestApplication.class).addAnnotatedClass(ApplicationStatusHistory.class)
                .addAnnotatedClass(OriginationProfile.class).addAnnotatedClass(LoanProductMapping.class).addAnnotatedClass(Tenant.class)
                .addAnnotatedClass(LogbookVehicle.class).addAnnotatedClass(LogbookValuation.class).addAnnotatedClass(LogbookVerification.class).addAnnotatedClass(LogbookAudit.class)
                .addAnnotatedClass(LogbookLoanOperation.class).addAnnotatedClass(ApplicationDocument.class)
                .addAnnotatedClass(KycCheck.class).addAnnotatedClass(StatementAnalysis.class).addAnnotatedClass(StatementReview.class)
                .setProperty("hibernate.connection.url",url).setProperty("hibernate.connection.username","root")
                .setProperty("hibernate.connection.password",System.getenv("MARIADB_MIGRATION_TEST_PASSWORD")).setProperty("hibernate.hbm2ddl.auto","validate");
            try(var factory=config.buildSessionFactory()){
                var em=SharedEntityManagerCreator.createSharedEntityManager(factory);var repositories=new JpaRepositoryFactory(em);
                var manager=new JpaTransactionManager(factory);var tx=new TransactionTemplate(manager);
                var apps=repositories.getRepository(LoanRequestApplicationRepository.class);var profiles=repositories.getRepository(OriginationProfileRepository.class);
                var products=repositories.getRepository(LoanProductMappingRepository.class);var kyc=repositories.getRepository(KycCheckRepository.class);
                var operations=repositories.getRepository(LogbookLoanOperationRepository.class);var actors=mock(CurrentActorService.class);
                when(actors.requireCurrentUser()).thenReturn(new AuthenticatedUser("user","a","officer","test@example.test",List.of(),List.of()));
                var tenantService=mock(TenantService.class);when(tenantService.getRequiredTenant("a")).thenAnswer(inv->em.find(Tenant.class,"a"));
                var capabilities=mock(LogbookService.class);var insured=new AtomicBoolean(false);
                when(capabilities.readinessForWorkflow(any())).thenAnswer(inv->new LogbookDtos.Readiness(true,true,true,insured.get(),insured.get(),"KES",new BigDecimal("480000"),true,List.of()));
                var workflow=new LogbookWorkflowService(new LogbookWorkflowPolicy(profiles,new OriginationProfileValidator(),json),capabilities,apps,profiles,products,kyc,
                    repositories.getRepository(StatementReviewRepository.class),repositories.getRepository(StatementAnalysisRepository.class),tenantService,operations,
                    repositories.getRepository(ApplicationStatusHistoryRepository.class),actors,em);
                tx.executeWithoutResult(status->{var check=new KycCheck();check.setTenantId("a");check.setApplicationId("application-a");check.setProvider("TEST");check.setStatus(KycStatus.PASSED);kyc.save(check);});
                tx.executeWithoutResult(status->{var app=apps.findByIdAndTenantId("application-a","a").orElseThrow();workflow.assess("application-a",new LogbookWorkflowService.FinancingRequest(app.getVersion(),new BigDecimal("480000")));});
                tx.executeWithoutResult(status->workflow.approve("a","application-a","officer","Approved"));
                var creation=operations.findByTenantIdAndApplicationIdAndKind("a","application-a",LogbookLoanOperation.Kind.CREATE_LOAN).orElseThrow();
                assertEquals(LogbookLoanOperation.State.QUEUED,creation.getState());
                var gateway=mock(FineractGateway.class);var remoteExists=new AtomicBoolean(false);var remoteActive=new AtomicBoolean(false);
                when(gateway.findSecuredLoan(any(),any())).thenAnswer(inv->remoteExists.get()?Optional.of(new FineractGateway.LoanSummary("100",remoteActive.get(),remoteActive.get()?"loanStatusType.active":"loanStatusType.submitted.and.pending.approval")):Optional.empty());
                when(gateway.createSecuredPendingLoan(any(),any(),any(),anyString())).thenAnswer(inv->{remoteExists.set(true);throw new RuntimeException("timeout after remote commit");});
                var processor=new LogbookLoanProcessor(manager,workflow,apps,operations,tenantService,gateway,mock(SubscriptionBillingService.class),mock(ApplicationEventPublisher.class));
                processor.process("a","application-a",creation.getId());
                assertNull(apps.findByIdAndTenantId("application-a","a").orElseThrow().getFineractLoanId());
                assertEquals(LogbookLoanOperation.State.REVIEW_REQUIRED,operations.findByTenantIdAndApplicationIdAndKind("a","application-a",LogbookLoanOperation.Kind.CREATE_LOAN).orElseThrow().getState());
                tx.executeWithoutResult(status->workflow.retry("application-a",creation.getId()));processor.process("a","application-a",creation.getId());
                assertEquals("100",apps.findByIdAndTenantId("application-a","a").orElseThrow().getFineractLoanId());
                verify(gateway,times(1)).createSecuredPendingLoan(any(),any(),any(),anyString());
                assertThrows(BadRequestException.class,()->tx.executeWithoutResult(status->workflow.disburse("a","application-a","officer")));
                insured.set(true);tx.executeWithoutResult(status->workflow.disburse("a","application-a","officer"));
                var disbursement=operations.findByTenantIdAndApplicationIdAndKind("a","application-a",LogbookLoanOperation.Kind.DISBURSE).orElseThrow();
                doAnswer(inv->{remoteActive.set(true);throw new RuntimeException("timeout after remote disbursement");}).when(gateway).activateLoan(any(),any());
                processor.process("a","application-a",disbursement.getId());
                tx.executeWithoutResult(status->workflow.retry("application-a",disbursement.getId()));processor.process("a","application-a",disbursement.getId());
                assertEquals(ApplicationStatus.FINERACT_LOAN_ACTIVATED,apps.findByIdAndTenantId("application-a","a").orElseThrow().getStatus());
                assertEquals(LogbookLoanOperation.State.SUCCEEDED,operations.findByTenantIdAndApplicationIdAndKind("a","application-a",LogbookLoanOperation.Kind.DISBURSE).orElseThrow().getState());
                verify(gateway,times(1)).activateLoan(any(),any());
                processor.process("a","application-a",disbursement.getId());verify(gateway,times(1)).activateLoan(any(),any());
                assertTrue(operations.findAllByTenantIdAndApplicationIdOrderByCreatedAtAsc("b","application-a").isEmpty());
            }
            assertEquals("23000",assertThrows(SQLException.class,()->OriginationProfileMigrationTest.execute(c,"UPDATE logbook_loan_operations SET tenant_id='b' WHERE tenant_id='a'")).getSQLState());
            assertEquals(2,OriginationProfileMigrationTest.scalar(c,"SELECT COUNT(*) FROM logbook_loan_operations WHERE tenant_id='a' AND state='SUCCEEDED'"));
        });
    }
}
