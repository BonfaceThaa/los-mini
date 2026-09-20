package com.credvenn.lm.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.credvenn.lm.common.exception.ConflictException;
import com.credvenn.lm.fineract.FineractDtos;
import com.credvenn.lm.loanproduct.*;
import com.credvenn.lm.origination.*;
import com.credvenn.lm.security.*;
import com.credvenn.lm.tenant.*;
import java.util.List;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

@EnabledIfEnvironmentVariable(named = "MARIADB_MIGRATION_TEST_URL", matches = ".+")
class ProductProfilePersistenceTest {
    @Test void scopedCatalogAndHistoricalSelectionReferencesRemainTenantSafe() throws Exception {
        OriginationProfileMigrationTest.withDatabase((url, connection) -> {
            OriginationProfileMigrationTest.migrate(url, "37");
            OriginationProfileMigrationTest.legacyRows(connection, "a");
            OriginationProfileMigrationTest.legacyRows(connection, "b");
            OriginationProfileMigrationTest.execute(connection, "UPDATE loan_request_applications SET selected_fineract_product_id='7' WHERE tenant_id='a'");
            OriginationProfileMigrationTest.migrate(url, "38");
            OriginationProfileMigrationTest.execute(connection, """
                    INSERT INTO origination_profiles
                      (id,tenant_id,code,display_name,requirements_json,created_by,updated_by,created_at,updated_at)
                    VALUES ('logbook-a','a','LOGBOOK','Logbook','{}','test','test',NOW(),NOW())
                    """);
            var config = new Configuration().addAnnotatedClass(LoanProductMapping.class).addAnnotatedClass(LoanRequestApplication.class)
                    .addAnnotatedClass(OriginationProfile.class).addAnnotatedClass(Tenant.class)
                    .setProperty("hibernate.connection.url", url).setProperty("hibernate.connection.username", "root")
                    .setProperty("hibernate.connection.password", System.getenv("MARIADB_MIGRATION_TEST_PASSWORD"))
                    .setProperty("hibernate.hbm2ddl.auto", "validate");
            try (var factory = config.buildSessionFactory()) {
                String phoneProfile; String logbookProductId;
                try (var session = factory.openSession()) {
                    var tx = session.beginTransaction(); var repos = new JpaRepositoryFactory(session);
                    var products = repos.getRepository(LoanProductMappingRepository.class);
                    var phone = products.findByTenantIdAndProductCodeIgnoreCase("a", "PHONE").orElseThrow();
                    phoneProfile = phone.getOriginationProfileId();
                    var logbook = copy(phone, "LOGBOOK_CASH", 8L, "logbook-a");
                    products.save(logbook); logbookProductId = logbook.getId();
                    products.save(copy(phone, "UNCLASSIFIED", 9L, null));
                    var inactive = copy(phone, "INACTIVE_PHONE", 10L, phoneProfile); inactive.setActive(false); products.save(inactive);
                    tx.commit();
                }
                try (var session = factory.openSession()) {
                    var tx = session.beginTransaction(); var repos = new JpaRepositoryFactory(session);
                    var products = repos.getRepository(LoanProductMappingRepository.class);
                    var applications = repos.getRepository(LoanRequestApplicationRepository.class);
                    var scoped = products.findAllByTenantIdAndOriginationProfileIdAndActiveTrueOrderByDisplayNameAsc("a", phoneProfile);
                    assertEquals(List.of("PHONE"), scoped.stream().map(LoanProductMapping::getProductCode).toList());
                    assertEquals("PHONE", FineractDtos.LoanProductResponse.from(scoped.getFirst()).productCode());
                    assertTrue(products.findAllByTenantIdAndOriginationProfileIdAndActiveTrueOrderByDisplayNameAsc("b", phoneProfile).isEmpty());
                    assertEquals(1, products.findAllByTenantIdAndOriginationProfileIdAndActiveTrue("a", phoneProfile, PageRequest.of(0, 10)).getTotalElements());
                    assertEquals(2, products.findAllByTenantIdAndOriginationProfileId("a", phoneProfile, PageRequest.of(0, 10)).getTotalElements());
                    var application = applications.findByIdAndTenantId("application-a", "a").orElseThrow();
                    assertEquals("product-a", application.getSelectedLoanProductMappingId());
                    application.setSelectedLoanProductMappingId(null); session.flush();
                    assertEquals(1, applications.findProductReferencesForUpdate("a", "product-a", "7").size());
                    assertTrue(applications.findProductReferencesForUpdate("b", "product-a", "7").isEmpty());
                    var actors = mock(CurrentActorService.class);
                    when(actors.requireCurrentUser()).thenReturn(new AuthenticatedUser("u", "a", "admin", "a@example.test", List.of(), List.of()));
                    var service = new ProductOriginationService(repos.getRepository(OriginationProfileRepository.class),
                            new OriginationProfileValidator(), repos.getRepository(TenantRepository.class), products, applications, actors, session);
                    assertThrows(ConflictException.class, () -> service.associate("PHONE", "LOGBOOK"));
                    assertThrows(ConflictException.class, () -> service.assertCanDelete(scoped.getFirst()));
                    assertEquals(phoneProfile, service.associate("LOGBOOK_CASH", "PHONE_FINANCE").originationProfileId());
                    application.setSelectedLoanProductMappingId("product-a");
                    tx.commit();
                }
                try (var session = factory.openSession()) {
                    var repos = new JpaRepositoryFactory(session);
                    assertEquals(phoneProfile, repos.getRepository(LoanProductMappingRepository.class)
                            .findByTenantIdAndProductCodeIgnoreCase("a", "LOGBOOK_CASH").orElseThrow().getOriginationProfileId());
                    assertEquals("product-a", repos.getRepository(LoanRequestApplicationRepository.class)
                            .findByIdAndTenantId("application-a", "a").orElseThrow().getSelectedLoanProductMappingId());
                }
            }
        });
    }
    private static LoanProductMapping copy(LoanProductMapping source, String code, long remoteId, String profileId) {
        var target = new LoanProductMapping(); BeanUtils.copyProperties(source, target, "id", "createdAt", "updatedAt");
        target.setProductCode(code); target.setShortName(code); target.setFineractProductId(remoteId); target.setOriginationProfileId(profileId);
        return target;
    }
}
