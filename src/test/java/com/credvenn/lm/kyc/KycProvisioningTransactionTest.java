package com.credvenn.lm.kyc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantKycMode;
import com.credvenn.lm.tenant.TenantService;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.*;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real Spring transaction proxies, JDBC commits and production event listeners.
 * Provider calls and domain persistence are replaced with controlled test fixtures.
 */
class KycProvisioningTransactionTest {
    enum ApprovalPath { AUTOMATIC, MANUAL, DISABLED }

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private KycCheck check;
    private LoanRequestApplication application;
    private ApplicationService applications;
    private ClientProvisioningService provisioning;
    private KycProvider provider;
    private final AtomicInteger attempts = new AtomicInteger();
    private int failures;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        jdbc.execute("create table decisions (tenant_id varchar(36), application_id varchar(36), status varchar(50))");
        jdbc.execute("create table finalizations (tenant_id varchar(36), application_id varchar(36))");
        check = new KycCheck();
        org.springframework.test.util.ReflectionTestUtils.setField(check, "id", "kyc-1");
        check.setTenantId("tenant-1");
        check.setApplicationId("app-1");
        check.setStatus(KycStatus.IN_PROGRESS);
        application = new LoanRequestApplication();
        applications = context.getBean(ApplicationService.class);
        provisioning = context.getBean(ClientProvisioningService.class);
        provider = context.getBean(KycProvider.class);
        var repository = context.getBean(KycCheckRepository.class);
        when(repository.findFirstByApplicationIdOrderByCreatedAtDesc("app-1")).thenReturn(Optional.of(check));
        when(repository.save(any(KycCheck.class))).thenAnswer(invocation -> {
            KycCheck saved = invocation.getArgument(0);
            jdbc.update("delete from decisions where tenant_id = ? and application_id = ?", "tenant-1", "app-1");
            jdbc.update("insert into decisions values (?, ?, ?)", "tenant-1", "app-1", saved.getStatus().name());
            return saved;
        });
        when(applications.getRequired("tenant-1", "app-1")).thenReturn(application);
        var tenant = new Tenant();
        tenant.setKycMode(TenantKycMode.AUTO);
        when(context.getBean(TenantService.class).getRequiredTenant("tenant-1")).thenReturn(tenant);
        when(context.getBean(KycProviderRegistry.class).currentProvider()).thenReturn(provider);
        when(provider.providerCode()).thenReturn("SMILE_ID");
        when(provider.assess(application)).thenReturn(
                new KycProvider.KycDecision(KycStatus.PASSED, "reference", "Exact match", null));
        doAnswer(invocation -> {
            jdbc.update("insert into finalizations values (?, ?)", "tenant-1", "app-1");
            if (attempts.incrementAndGet() <= failures) {
                throw new CannotAcquireLockException("simulated lock conflict");
            }
            return null;
        }).when(applications).markKycPassed(eq("tenant-1"), eq("app-1"), eq("officer"), anyString());
        doAnswer(invocation -> {
            // Read using an independent connection: provisioning must see committed data.
            try (var connection = context.getBean(DataSource.class).getConnection();
                 var statement = connection.prepareStatement(
                         "select count(*) from finalizations where tenant_id = ? and application_id = ?")) {
                statement.setString(1, "tenant-1");
                statement.setString(2, "app-1");
                try (var result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(1, result.getInt(1));
                }
            }
            return null;
        }).when(provisioning).process(eq("tenant-1"), eq("app-1"), eq("officer"), nullable(String.class));
    }

    @AfterEach
    void close() {
        context.close();
    }

    @ParameterizedTest
    @EnumSource(ApprovalPath.class)
    void approvalCommitsBeforeProvisioningForEveryCaller(ApprovalPath path) {
        approve(path);
        assertEquals(1, count("finalizations"));
        verify(provisioning).process("tenant-1", "app-1", "officer", check.getId());
        verify(provider, times(path == ApprovalPath.AUTOMATIC ? 1 : 0)).assess(application);
    }

    @ParameterizedTest
    @EnumSource(ApprovalPath.class)
    void lockRetriesRollbackAndProvisionOnlyAfterSuccessfulCommit(ApprovalPath path) {
        failures = 3;
        approve(path);
        assertEquals(4, attempts.get());
        assertEquals(1, count("finalizations"), "Failed attempts must roll back their writes");
        assertEquals(1, count("decisions"));
        verify(provisioning).process("tenant-1", "app-1", "officer", check.getId());
        verify(provider, times(path == ApprovalPath.AUTOMATIC ? 1 : 0)).assess(application);
    }

    @Test
    void exhaustedRetriesKeepApprovedDecisionWithoutProvisioning() {
        failures = 4;
        approve(ApprovalPath.AUTOMATIC);
        assertEquals(4, attempts.get());
        assertEquals(0, count("finalizations"));
        assertEquals("PASSED", jdbc.queryForObject(
                "select status from decisions where tenant_id = ? and application_id = ?",
                String.class, "tenant-1", "app-1"));
        verifyNoInteractions(provisioning);
        verify(provider).assess(application);
    }

    @ParameterizedTest
    @EnumSource(ApprovalPath.class)
    void rolledBackDecisionDoesNotFinalizeOrProvision(ApprovalPath path) {
        transaction.executeWithoutResult(status -> {
            approve(path);
            verifyNoInteractions(provisioning);
            status.setRollbackOnly();
        });
        assertEquals(0, count("decisions"));
        assertEquals(0, attempts.get());
        verifyNoInteractions(provisioning);
    }

    @Test
    void explicitClientRetryWaitsForItsOwnCommit() {
        check.setStatus(KycStatus.PASSED);
        doAnswer(invocation -> {
            jdbc.update("insert into finalizations values (?, ?)", "tenant-1", "app-1");
            return null;
        }).when(applications).markClientCreationInProgress("tenant-1", "app-1", "officer");
        transaction.executeWithoutResult(status -> {
            context.getBean(KycService.class).retryClientCreation("tenant-1", "app-1", "officer");
            verifyNoInteractions(provisioning);
        });
        verify(provisioning).process("tenant-1", "app-1", "officer", check.getId());
    }

    @Test
    void rolledBackExplicitClientRetryDoesNotProvision() {
        check.setStatus(KycStatus.PASSED);
        transaction.executeWithoutResult(status -> {
            context.getBean(KycService.class).retryClientCreation("tenant-1", "app-1", "officer");
            status.setRollbackOnly();
        });
        verifyNoInteractions(provisioning);
    }

    private void approve(ApprovalPath path) {
        switch (path) {
            case AUTOMATIC -> context.getBean(KycProcessingService.class).process("tenant-1", "app-1", "officer");
            case MANUAL -> context.getBean(KycService.class).manualReview("tenant-1", "app-1", "officer",
                    new KycDtos.ManualKycReviewRequest(true, "Approved by reviewer"));
            case DISABLED -> {
                context.getBean(TenantService.class).getRequiredTenant("tenant-1").setKycMode(TenantKycMode.DISABLED);
                context.getBean(KycService.class).handleApplicationCreated("tenant-1", "app-1", "officer");
            }
        }
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table
                + " where tenant_id = ? and application_id = ?", Integer.class, "tenant-1", "app-1");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableResilientMethods
    @Import({KycDecisionService.class, KycApprovalService.class, KycApprovalRetryService.class,
            KycApprovedEventListener.class, ClientProvisioningRequestedListener.class,
            KycService.class, KycProcessingService.class})
    static class Config {
        @Bean(destroyMethod = "shutdown")
        EmbeddedDatabase dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        }
        @Bean
        PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean KycCheckRepository repository() { return mock(KycCheckRepository.class); }
        @Bean ApplicationService applications() { return mock(ApplicationService.class); }
        @Bean TenantService tenants() { return mock(TenantService.class); }
        @Bean KycProviderRegistry registry() { return mock(KycProviderRegistry.class); }
        @Bean KycProvider provider() { return mock(KycProvider.class); }
        @Bean ClientProvisioningService provisioning() { return mock(ClientProvisioningService.class); }
    }
}
