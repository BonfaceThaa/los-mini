package com.credvenn.lm.application;

import static org.junit.jupiter.api.Assertions.*;
import com.credvenn.lm.applicationvariable.*;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.origination.*;
import com.credvenn.lm.tenant.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

@EnabledIfEnvironmentVariable(named = "MARIADB_MIGRATION_TEST_URL", matches = ".+")
class ApplicationVariableScopePersistenceTest {
    @Test void filtersByTenantAndScopeValidatesRequiredAnswersAndPreservesHistoricalSnapshots() throws Exception {
        OriginationProfileMigrationTest.withDatabase((url, connection) -> {
            OriginationProfileMigrationTest.migrate(url, "37");
            OriginationProfileMigrationTest.legacyRows(connection, "a");
            OriginationProfileMigrationTest.legacyRows(connection, "b");
            OriginationProfileMigrationTest.migrate(url, "38");
            OriginationProfileMigrationTest.execute(connection, """
                    INSERT INTO origination_profiles
                      (id,tenant_id,code,display_name,requirements_json,created_by,updated_by,created_at,updated_at)
                    VALUES ('logbook-a','a','LOGBOOK','Logbook','{}','test','test',NOW(),NOW())
                    """);
            var config = new Configuration().addAnnotatedClass(ApplicationVariableDefinition.class)
                    .addAnnotatedClass(ApplicationVariable.class).addAnnotatedClass(OriginationProfile.class).addAnnotatedClass(Tenant.class)
                    .setProperty("hibernate.connection.url", url).setProperty("hibernate.connection.username", "root")
                    .setProperty("hibernate.connection.password", System.getenv("MARIADB_MIGRATION_TEST_PASSWORD"))
                    .setProperty("hibernate.hbm2ddl.auto", "validate");
            try (var factory = config.buildSessionFactory()) {
                String sharedId; String phoneId; String logbookId; String inactiveId;
                try (var session = factory.openSession()) {
                    var tx = session.beginTransaction(); var service = service(new JpaRepositoryFactory(session));
                    sharedId = service.create("a", request("SHARED", null)).id();
                    phoneId = service.create("a", request("PHONE_FIELD", "PHONE_FINANCE")).id();
                    logbookId = service.create("a", request("CAR_REG", "LOGBOOK")).id();
                    inactiveId = service.create("a", request("DISABLED", "PHONE_FINANCE")).id();
                    service.setActive("a", inactiveId, false);
                    service.create("b", request("OTHER_TENANT_REQUIRED", null));
                    tx.commit();
                }
                try (var session = factory.openSession()) {
                    var tx = session.beginTransaction(); var repos = new JpaRepositoryFactory(session); var service = service(repos);
                    var definitions = repos.getRepository(ApplicationVariableDefinitionRepository.class);
                    var phoneProfile = repos.getRepository(TenantRepository.class).findById("a").orElseThrow().getDefaultOriginationProfileId();
                    assertEquals(List.of("EMPLOYMENT", "PHONE_FIELD", "SHARED"),
                            service.list("a", true, null).stream().map(ApplicationVariableDtos.DefinitionResponse::code).toList());
                    assertEquals(List.of("CAR_REG", "EMPLOYMENT", "SHARED"), service.listAll("a", true, "LOGBOOK")
                            .stream().map(ApplicationVariableDtos.DefinitionResponse::code).toList());
                    assertEquals(5, service.listAll("a", false, null).size());
                    assertEquals(4, service.list("a", false, "PHONE_FINANCE").size());
                    assertEquals(2, service.listAll("b", true, null).size());
                    var shared = answer(sharedId, "Shared answer"); var phone = answer(phoneId, "Original phone answer");
                    var prepared = service.prepare("a", phoneProfile, List.of(shared, phone));
                    assertEquals(2, prepared.size()); // Required CAR_REG applies only to logbook.
                    assertThrows(BadRequestException.class, () -> service.prepare("a", phoneProfile, List.of(phone)));
                    assertThrows(BadRequestException.class, () -> service.prepare("a", phoneProfile, List.of(shared)));
                    assertThrows(BadRequestException.class, () -> service.prepare("a", phoneProfile, List.of(shared, phone, answer(logbookId, "KAA123A"))));
                    assertThrows(BadRequestException.class, () -> service.prepare("a", phoneProfile, List.of(shared, phone, answer(inactiveId, "hidden"))));
                    assertThrows(BadRequestException.class, () -> service.prepare("a", phoneProfile, List.of(shared, phone, answer("question-b", "foreign"))));
                    assertEquals(2, service.prepare("a", "logbook-a", List.of(shared, answer(logbookId, "KAA123A"))).size());
                    assertThrows(BadRequestException.class, () -> service.prepare("a", "logbook-a", List.of(shared, phone)));
                    service.savePrepared("a", "application-a", prepared);
                    service.update("a", phoneId, request("PHONE_FIELD", "LOGBOOK"));
                    assertEquals("logbook-a", definitions.findByIdAndTenantId(phoneId, "a").orElseThrow().getOriginationProfileId());
                    assertEquals(2, definitions.findByIdAndTenantId(phoneId, "a").orElseThrow().getDefinitionVersion());
                    assertEquals(1, service.prepare("a", phoneProfile, List.of(shared)).size());
                    service.setActive("a", phoneId, false);
                    tx.commit();
                }
                try (var session = factory.openSession()) {
                    var service = service(new JpaRepositoryFactory(session));
                    var answers = service.answers("a", "application-a");
                    assertEquals(2, answers.size());
                    var phone = answers.stream().filter(a -> a.definitionId().equals(phoneId)).findFirst().orElseThrow();
                    assertEquals(1, phone.definitionVersion()); assertEquals("Original phone answer", phone.textValue());
                    assertTrue(service.answers("b", "application-a").isEmpty());
                }
            }
        });
    }
    private static ApplicationVariableService service(JpaRepositoryFactory repositories) {
        var profiles = repositories.getRepository(OriginationProfileRepository.class);
        var tenants = repositories.getRepository(TenantRepository.class);
        var validator = new OriginationProfileValidator(); var json = new ObjectMapper();
        return new ApplicationVariableService(repositories.getRepository(ApplicationVariableDefinitionRepository.class),
                repositories.getRepository(ApplicationVariableRepository.class), json, profiles,
                new OriginationProfileResolver(tenants, profiles, validator, json), validator, tenants);
    }
    private static ApplicationVariableDtos.DefinitionRequest request(String code, String scope) {
        return new ApplicationVariableDtos.DefinitionRequest(code, code, null, ApplicationVariableFieldType.TEXT,
                true, 0, null, null, List.of(), scope);
    }
    private static ApplicationVariableDtos.AnswerRequest answer(String id, String value) {
        return new ApplicationVariableDtos.AnswerRequest(id, value, List.of());
    }
}
