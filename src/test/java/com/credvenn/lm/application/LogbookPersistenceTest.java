package com.credvenn.lm.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.credvenn.lm.common.exception.ConflictException;
import com.credvenn.lm.document.*;
import com.credvenn.lm.logbook.*;
import com.credvenn.lm.logbook.LogbookDtos.*;
import com.credvenn.lm.origination.*;
import com.credvenn.lm.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

@EnabledIfEnvironmentVariable(named="MARIADB_MIGRATION_TEST_URL", matches=".+")
class LogbookPersistenceTest {
    @Test void freshMigrationCreatesEmptyCapabilities() throws Exception {
        OriginationProfileMigrationTest.withDatabase((url, connection) -> {
            OriginationProfileMigrationTest.migrate(url,"39");
            assertEquals(0, OriginationProfileMigrationTest.scalar(connection,"SELECT COUNT(*) FROM logbook_vehicles"));
            assertEquals(0, OriginationProfileMigrationTest.scalar(connection,"SELECT COUNT(*) FROM origination_profiles"));
            OriginationProfileMigrationTest.migrate(url,"39");
        });
    }
    @Test void upgradeAndRealRepositoriesEnforceEvidenceScopeVersionsAndAudit() throws Exception {
        OriginationProfileMigrationTest.withDatabase((url, connection) -> {
            OriginationProfileMigrationTest.migrate(url,"37");
            OriginationProfileMigrationTest.legacyRows(connection,"a"); OriginationProfileMigrationTest.legacyRows(connection,"b");
            OriginationProfileMigrationTest.migrate(url,"38");
            OriginationProfileMigrationTest.migrate(url,"39");
            assertEquals(2, OriginationProfileMigrationTest.scalar(connection,"SELECT COUNT(*) FROM loan_request_applications WHERE fineract_loan_id='existing-loan'"));
            OriginationProfileMigrationTest.execute(connection,"""
                INSERT INTO origination_profiles(id,tenant_id,code,display_name,requirements_json,configuration_json,created_by,updated_by,created_at,updated_at)
                VALUES ('logbook','a','LOGBOOK','Logbook','{"OFFER_SELECTION":["VALUATION_APPROVED"]}',
                  '{"valuationBasis":"FORCED_SALE_VALUE","maxLtvRatio":0.6,"valuationValidityDays":30}','test','test',NOW(),NOW())
                """);
            OriginationProfileMigrationTest.execute(connection,"UPDATE loan_request_applications SET origination_profile_id='logbook',fineract_loan_id=NULL,status='SUBMITTED',requested_amount=480000 WHERE tenant_id='a'");
            for (var tenant : List.of("a","b")) {
                OriginationProfileMigrationTest.execute(connection,"""
                    INSERT INTO application_documents(id,tenant_id,application_id,document_type,original_filename,stored_filename,relative_path,file_size,public_url,created_by,created_at,updated_at)
                    VALUES ('doc-%1$s','%1$s','application-%1$s','VALUATION','report.pdf','report.pdf','test/report.pdf',1,'/test','test',NOW(),NOW())
                    """.formatted(tenant));
            }
            var config=new Configuration().addAnnotatedClass(LogbookVehicle.class).addAnnotatedClass(LogbookValuation.class)
                .addAnnotatedClass(LogbookVerification.class).addAnnotatedClass(LogbookAudit.class)
                .addAnnotatedClass(LoanRequestApplication.class).addAnnotatedClass(OriginationProfile.class).addAnnotatedClass(ApplicationDocument.class)
                .setProperty("hibernate.connection.url",url).setProperty("hibernate.connection.username","root")
                .setProperty("hibernate.connection.password",System.getenv("MARIADB_MIGRATION_TEST_PASSWORD"))
                .setProperty("hibernate.hbm2ddl.auto","validate");
            var actors=mock(CurrentActorService.class);
            when(actors.requireCurrentUser()).thenReturn(actor("valuer"));
            String valuationId;
            try(var factory=config.buildSessionFactory()) {
                try(var session=factory.openSession()) {
                    var tx=session.beginTransaction();var service=service(session,actors);
                    var first=service.saveVehicle("application-a",new VehicleRequest(null,"KDA 123A","CH123","EN123","Toyota","Fielder",2016,"Borrower","LB123"));
                    assertEquals(0,first.vehicle().getVersion());
                    var submitted=service.submitValuation("application-a",new ValuationRequest(0L,new BigDecimal("1000000"),new BigDecimal("800000"),LocalDate.now(),"Valuers","doc-a"));
                    assertEquals(1,submitted.vehicle().getVersion()); valuationId=submitted.valuations().getFirst().getId(); tx.commit();
                }
                try(var session=factory.openSession()) {
                    var tx=session.beginTransaction(); var service=service(session,actors);
                    assertThrows(ConflictException.class,()->service.submitValuation("application-a",new ValuationRequest(0L,BigDecimal.TEN,BigDecimal.ONE,LocalDate.now(),"Valuers","doc-a")));
                    tx.rollback();
                }
                when(actors.requireCurrentUser()).thenReturn(actor("reviewer"));
                try(var session=factory.openSession()) {
                    var tx=session.beginTransaction(); var service=service(session,actors);
                    var approved=service.reviewValuation("application-a",valuationId,new ReviewRequest(1L,ValuationStatus.APPROVED,"Verified report"));
                    assertEquals(2,approved.vehicle().getVersion()); assertTrue(approved.readiness().requestedAmountWithinLimit());
                    assertEquals(new BigDecimal("480000.00"),approved.readiness().maximumSecuredAmount());
                    var checked=service.verify("application-a",VerificationKind.OWNERSHIP,new VerificationRequest(2L,VerificationStatus.VERIFIED,"SEARCH123","doc-a",null,null,"Ownership checked"));
                    assertEquals(3,checked.vehicle().getVersion()); assertTrue(checked.readiness().ownershipVerified());tx.commit();
                }
                try(var session=factory.openSession()) {
                    var tx=session.beginTransaction();var service=service(session,actors);
                    var revised=service.saveVehicle("application-a",new VehicleRequest(3L,"KDA123B","CH456","EN456","Toyota","Fielder",2016,"Borrower","LB456"));
                    assertEquals(4,revised.vehicle().getVersion());assertFalse(revised.readiness().valuationApproved());assertFalse(revised.readiness().ownershipVerified());
                    assertEquals(5,service.auditHistory("application-a").size());
                    var repos=new JpaRepositoryFactory(session);
                    assertTrue(repos.getRepository(LogbookVehicleRepository.class).findByTenantIdAndApplicationId("b","application-a").isEmpty());
                    assertTrue(repos.getRepository(LogbookValuationRepository.class).findAllByTenantIdAndApplicationIdOrderByRecordedVersionDesc("b","application-a").isEmpty());tx.commit();
                }
            }
            assertEquals("23000",assertThrows(SQLException.class,()->OriginationProfileMigrationTest.execute(connection,"UPDATE logbook_valuations SET report_document_id='doc-b' WHERE tenant_id='a'")).getSQLState());
            assertEquals("23000",assertThrows(SQLException.class,()->OriginationProfileMigrationTest.execute(connection,"UPDATE logbook_vehicles SET tenant_id='b' WHERE tenant_id='a'")).getSQLState());
            assertEquals("23000",assertThrows(SQLException.class,()->OriginationProfileMigrationTest.execute(connection,"DELETE FROM application_documents WHERE tenant_id='a' AND id='doc-a'")).getSQLState());
        });
    }
    private static AuthenticatedUser actor(String id){return new AuthenticatedUser(id,"a",id,"test@example.test",List.of(),List.of());}
    private static LogbookService service(org.hibernate.Session session,CurrentActorService actors){
        var repos=new JpaRepositoryFactory(session);
        return new LogbookService(actors,repos.getRepository(LoanRequestApplicationRepository.class),repos.getRepository(OriginationProfileRepository.class),
            repos.getRepository(ApplicationDocumentRepository.class),repos.getRepository(LogbookVehicleRepository.class),repos.getRepository(LogbookValuationRepository.class),
            repos.getRepository(LogbookVerificationRepository.class),repos.getRepository(LogbookAuditRepository.class),new ObjectMapper().findAndRegisterModules());
    }
}
