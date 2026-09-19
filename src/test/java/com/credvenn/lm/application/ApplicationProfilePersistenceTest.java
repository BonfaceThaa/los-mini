package com.credvenn.lm.application;

import static org.junit.jupiter.api.Assertions.*;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.origination.*;
import com.credvenn.lm.tenant.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

@EnabledIfEnvironmentVariable(named = "MARIADB_MIGRATION_TEST_URL", matches = ".+")
class ApplicationProfilePersistenceTest {
    @Test void persistsResolvedProfileAndReadsCurrentStateDespiteOlderSnapshot() throws Exception {
        OriginationProfileMigrationTest.withDatabase((url, connection) -> {
            OriginationProfileMigrationTest.migrate(url, "37");
            OriginationProfileMigrationTest.legacyRows(connection, "a");
            OriginationProfileMigrationTest.migrate(url, "38");
            var configuration = new Configuration()
                    .addAnnotatedClass(LoanRequestApplication.class)
                    .addAnnotatedClass(OriginationProfile.class)
                    .addAnnotatedClass(Tenant.class)
                    .setProperty("hibernate.connection.url", url)
                    .setProperty("hibernate.connection.username", "root")
                    .setProperty("hibernate.connection.password", System.getenv("MARIADB_MIGRATION_TEST_PASSWORD"))
                    .setProperty("hibernate.hbm2ddl.auto", "validate");
            try (var factory = configuration.buildSessionFactory()) {
                String applicationId;
                String profileId;
                try (var session = factory.openSession()) {
                    var tx = session.beginTransaction();
                    var repositories = new JpaRepositoryFactory(session);
                    var resolver = new OriginationProfileResolver(repositories.getRepository(TenantRepository.class),
                            repositories.getRepository(OriginationProfileRepository.class), new OriginationProfileValidator(), new ObjectMapper());
                    profileId = resolver.resolveForApplication("a", null);
                    assertEquals(profileId, resolver.resolveForApplication("a", "phone_finance"));
                    var application = new LoanRequestApplication();
                    application.setTenantId("a"); application.setOriginationProfileId(profileId);
                    application.setApplicantFirstName("Mary"); application.setApplicantLastName("Test");
                    application.setPhoneNumber("0700000000"); application.setNationalId("12345678");
                    application.setApplicantIdType(ApplicantIdType.NATIONAL_ID);
                    application.setRequestedAmount(new BigDecimal("20000")); application.setStatus(ApplicationStatus.PENDING_KYC);
                    repositories.getRepository(LoanRequestApplicationRepository.class).save(application);
                    applicationId = application.getId(); tx.commit();
                }
                try (var session = factory.openSession()) {
                    var repository = new JpaRepositoryFactory(session).getRepository(LoanRequestApplicationRepository.class);
                    assertEquals(profileId, repository.findByIdAndTenantId(applicationId, "a").orElseThrow().getOriginationProfileId());
                    assertTrue(repository.findByIdAndTenantId(applicationId, "other-tenant").isEmpty());
                }
                try (var session = factory.openSession()) {
                    var tx = session.beginTransaction();
                    // Establish a repeatable-read snapshot without putting the profile into Hibernate's entity cache.
                    session.doWork(jdbc -> {
                        try (var statement = jdbc.createStatement();
                             var rows = statement.executeQuery("SELECT active FROM origination_profiles WHERE tenant_id='a'")) {
                            assertTrue(rows.next()); assertTrue(rows.getBoolean(1));
                        }
                    });
                    // Simulates a completed admin transaction before creation acquires the tenant lock.
                    OriginationProfileMigrationTest.execute(connection, "UPDATE origination_profiles SET active=FALSE WHERE tenant_id='a'");
                    var repositories = new JpaRepositoryFactory(session);
                    var resolver = new OriginationProfileResolver(repositories.getRepository(TenantRepository.class),
                            repositories.getRepository(OriginationProfileRepository.class), new OriginationProfileValidator(), new ObjectMapper());
                    assertEquals("Origination profile is inactive", assertThrows(BadRequestException.class,
                            () -> resolver.resolveForApplication("a", null)).getMessage());
                    tx.rollback();
                }
            }
        });
    }
}
