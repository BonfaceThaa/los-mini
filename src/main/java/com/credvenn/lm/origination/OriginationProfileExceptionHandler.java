package com.credvenn.lm.origination;

import com.credvenn.lm.common.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@Order(0)
@RestControllerAdvice(assignableTypes = {OriginationProfileController.class, OriginationDefaultController.class})
public class OriginationProfileExceptionHandler {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> malformed(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid profile payload or unknown stage, requirement or valuation basis", request);
    }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> stale(OptimisticLockingFailureException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "Profile changed; reload and retry", request);
    }
    private ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI(), Map.of()));
    }
}
