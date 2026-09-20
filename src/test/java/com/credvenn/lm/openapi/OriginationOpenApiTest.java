package com.credvenn.lm.openapi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import com.credvenn.lm.application.*;
import com.credvenn.lm.applicationvariable.*;
import com.credvenn.lm.fineract.FineractLoanProductController;
import com.credvenn.lm.loanproduct.*;
import com.credvenn.lm.logbook.*;
import com.credvenn.lm.origination.*;
import com.credvenn.lm.security.*;
import com.credvenn.lm.statement.StatementAnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Generates real springdoc JSON with actual controllers and isolated mocked business dependencies. */
@SpringBootTest(classes = OriginationOpenApiTest.Config.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.address=127.0.0.1", "springdoc.api-docs.enabled=true", "springdoc.api-docs.path=/v3/api-docs"})
class OriginationOpenApiTest {
    @Value("${local.server.port}") int port;

    @Test void generatedDocumentDescribesProductProfilesSelectorsAndLogbookExample() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/v3/api-docs")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        var json = new ObjectMapper().findAndRegisterModules();
        var document = json.readTree(response.body());
        Files.writeString(Path.of("target", "openapi-origination.json"), response.body());
        var paths = document.path("paths");
        var association = paths.path("/api/v1/loan-products/{productCode}/origination-profile").path("put");
        assertEquals("Associate a loan product with an origination profile", association.path("summary").asText());
        assertTrue(association.path("security").toString().contains("bearerAuth"));
        assertTrue(association.path("responses").has("409"));
        assertTrue(association.path("responses").has("404"));
        var catalog = paths.path("/api/v1/loan-products");
        assertTrue(catalog.path("get").path("parameters").toString().contains("originationProfileCode"));
        assertTrue(catalog.path("post").path("responses").has("400"));
        assertTrue(catalog.path("post").path("responses").has("404"));
        assertTrue(catalog.path("post").path("responses").has("409"));
        var example = catalog.path("post").path("requestBody").path("content").path("application/json")
                .path("examples").path("Logbook product").path("value");
        if (example.isTextual()) example = json.readTree(example.asText());
        assertEquals("LOGBOOK", example.path("originationProfileCode").asText());
        assertFalse(example.path("active").asBoolean(true));
        var request = json.treeToValue(example, LoanProductCatalogDtos.CreateLoanProductRequest.class);
        try (var validatorFactory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertTrue(validatorFactory.getValidator().validate(request).isEmpty());
        }
        assertTrue(paths.has("/api/v1/applications/{applicationId}/logbook"));
        assertTrue(paths.has("/api/v1/applications/{applicationId}/logbook/vehicle"));
        assertTrue(paths.has("/api/v1/applications/{applicationId}/logbook/valuations/{valuationId}/review"));
        assertTrue(paths.has("/api/v1/applications/{applicationId}/logbook/verifications/{kind}"));
        var schemas = document.path("components").path("schemas");
        assertTrue(schemas.path("ValuationRequest").path("properties").has("forcedSaleValue"));
        assertTrue(schemas.path("Readiness").path("properties").has("maximumSecuredAmount"));
        assertTrue(schemas.path("CreateLoanProductRequest").path("properties").has("originationProfileCode"));
        var selector = schemas.path("SelectOfferRequest");
        assertEquals(2, selector.path("oneOf").size());
        assertTrue(selector.path("properties").path("fineractProductId").path("deprecated").asBoolean());
        assertTrue(schemas.path("SelectOfferByProductCode").path("required").toString().contains("productCode"));
        assertTrue(schemas.path("SelectOfferByLegacyId").path("required").toString().contains("fineractProductId"));
        assertTrue(paths.path("/api/v1/applications/{applicationId}/offers/select").path("post").path("responses").has("400"));
        assertTrue(paths.has("/api/v1/application-variable-definitions/all"));
        assertTrue(schemas.path("LoanRequestApplicationResponse").path("properties").has("selectedLoanProductMappingId"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
    })
    @Import({OpenApiConfig.class, ProductOriginationController.class, FineractLoanProductController.class,
            ApplicationController.class, ApplicationVariableController.class, OriginationProfileController.class,
            OriginationDefaultController.class, LogbookController.class})
    static class Config {
        @Bean LogbookService logbook() { return mock(LogbookService.class); }
        @Bean LoanProductCatalogService catalog() { return mock(LoanProductCatalogService.class); }
        @Bean ProductOriginationService productProfiles() { return mock(ProductOriginationService.class); }
        @Bean ApplicationService applications() { return mock(ApplicationService.class); }
        @Bean ApplicationStatementOtpService otps() { return mock(ApplicationStatementOtpService.class); }
        @Bean StatementAnalysisService statements() { return mock(StatementAnalysisService.class); }
        @Bean CurrentActorService actors() { return mock(CurrentActorService.class); }
        @Bean ApplicationVariableService variables() { return mock(ApplicationVariableService.class); }
        @Bean OriginationProfileService profiles() { return mock(OriginationProfileService.class); }
        // Test-only access to generated metadata; the production security configuration is unchanged.
        @Bean SecurityFilterChain docsSecurity(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(a -> a.anyRequest().permitAll()).csrf(c -> c.disable()).build();
        }
    }
}
