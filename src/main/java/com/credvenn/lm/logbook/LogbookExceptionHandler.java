package com.credvenn.lm.logbook;

import com.credvenn.lm.common.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.*;

@Order(0) @RestControllerAdvice(assignableTypes=LogbookController.class)
public class LogbookExceptionHandler {
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> malformed(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid logbook payload, date or verification kind", request);
    }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> stale(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "Logbook evidence changed; reload and retry", request);
    }
    private ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI(), Map.of()));
    }
}
