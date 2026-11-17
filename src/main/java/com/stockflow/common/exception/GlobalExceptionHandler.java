package com.stockflow.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * ============================================================================
 * [API SPECIFICATION & STANDARDS: RFC 7807 PROBLEM DETAILS]
 * ============================================================================
 * Why not return ad-hoc JSON errors like {"error": "bad request"}?
 * Naive custom error formats force frontend and third-party API clients to
 * write custom error parsers for every micro-service or endpoint.
 *
 * In accordance with IETF RFC 7807 ("Problem Details for HTTP APIs"), StockFlow
 * converts all application domain exceptions into standard machine-readable
 * ProblemDetail payloads containing:
 *  - type: A URI reference identifying the specific problem type.
 *  - title: A short human-readable summary.
 *  - status: The HTTP status code.
 *  - detail: Human-readable explanation specific to this occurrence.
 *  - timestamp & correlationId: Enriched for distributed tracing and log analysis.
 *
 * @author StockFlow Platform Engineering
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://stockflow.io/errors/not-found"));
        enrichProblem(problem);
        return problem;
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        log.warn("Conflict detected: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflict");
        problem.setType(URI.create("https://stockflow.io/errors/conflict"));
        enrichProblem(problem);
        return problem;
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ProblemDetail handleBusinessRuleViolation(BusinessRuleViolationException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Business Rule Violation");
        problem.setType(URI.create("https://stockflow.io/errors/business-rule-violation"));
        enrichProblem(problem);
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed for one or more fields");
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("https://stockflow.io/errors/validation-failed"));
        problem.setProperty("invalid_fields", errors);
        enrichProblem(problem);
        return problem;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        log.warn("Authentication failed: invalid credentials provided");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        problem.setTitle("Authentication Failed");
        problem.setType(URI.create("https://stockflow.io/errors/authentication-failed"));
        enrichProblem(problem);
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied: insufficient permissions");
        problem.setTitle("Forbidden");
        problem.setType(URI.create("https://stockflow.io/errors/forbidden"));
        enrichProblem(problem);
        return problem;
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedException ex) {
        log.warn("Unauthorized access attempt: {}", ex.getMessage());
        boolean isAuthError = ex.getMessage() != null &&
                (ex.getMessage().toLowerCase().contains("signature") ||
                 ex.getMessage().toLowerCase().contains("token") ||
                 ex.getMessage().toLowerCase().contains("deactivated"));
        HttpStatus status = isAuthError ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(isAuthError ? "Authentication Required" : "Access Denied");
        problem.setType(URI.create("https://stockflow.io/errors/" + (isAuthError ? "unauthorized" : "forbidden")));
        enrichProblem(problem);
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("Data integrity violation: ", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Database constraint violation or duplicate entry");
        problem.setTitle("Data Integrity Violation");
        problem.setType(URI.create("https://stockflow.io/errors/data-integrity"));
        enrichProblem(problem);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unhandled server exception: ", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please contact support.");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://stockflow.io/errors/internal-error"));
        enrichProblem(problem);
        return problem;
    }

    private void enrichProblem(ProblemDetail problem) {
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            problem.setProperty("correlation_id", correlationId);
        }
        problem.setProperty("timestamp", Instant.now().toString());
    }
}
