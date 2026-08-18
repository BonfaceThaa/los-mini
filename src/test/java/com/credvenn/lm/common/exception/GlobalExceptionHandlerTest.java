package com.credvenn.lm.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    @Test
    void handleUnexpectedReturnsGenericMessageWithoutLeakingBackendDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/applications/app-1/internal-approval");
        Exception exception = new RuntimeException(
                "I/O error on POST request for \"http://localhost:8080/fineract-provider/api/v1/loans\": null");

        var response = handler.handleUnexpected(exception, request);

        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(
                "An unexpected error occurred while processing the request. Please try again later.",
                response.getBody().message());
        assertEquals("/api/v1/applications/app-1/internal-approval", response.getBody().path());
        assertTrue(response.getBody().validationErrors().isEmpty());
    }
}
