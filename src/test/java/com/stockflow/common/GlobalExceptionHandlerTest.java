package com.stockflow.common;

import com.stockflow.common.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("Should map UnauthorizedException to 403 Forbidden for BOLA/ownership violation")
    void shouldMapUnauthorizedExceptionToForbidden() {
        UnauthorizedException ex = new UnauthorizedException("You are not authorized to cancel this order");
        ProblemDetail problem = exceptionHandler.handleUnauthorized(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getTitle()).isEqualTo("Access Denied");
        assertThat(problem.getDetail()).isEqualTo("You are not authorized to cancel this order");
        assertThat(problem.getType().toString()).isEqualTo("https://stockflow.io/errors/forbidden");
    }

    @Test
    @DisplayName("Should map UnauthorizedException to 401 Unauthorized for signature or token failures")
    void shouldMapUnauthorizedExceptionToUnauthorizedForTokenOrSignature() {
        UnauthorizedException ex = new UnauthorizedException("Invalid webhook signature");
        ProblemDetail problem = exceptionHandler.handleUnauthorized(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problem.getTitle()).isEqualTo("Authentication Required");
        assertThat(problem.getDetail()).isEqualTo("Invalid webhook signature");
        assertThat(problem.getType().toString()).isEqualTo("https://stockflow.io/errors/unauthorized");
    }

    @Test
    @DisplayName("Should map ResourceNotFoundException to 404 Not Found")
    void shouldMapResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Order not found: 123");
        ProblemDetail problem = exceptionHandler.handleResourceNotFound(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Resource Not Found");
        assertThat(problem.getDetail()).isEqualTo("Order not found: 123");
    }

    @Test
    @DisplayName("Should map BusinessRuleViolationException to 422 Unprocessable Entity")
    void shouldMapBusinessRuleViolationException() {
        BusinessRuleViolationException ex = new BusinessRuleViolationException("Cart is empty");
        ProblemDetail problem = exceptionHandler.handleBusinessRuleViolation(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(problem.getTitle()).isEqualTo("Business Rule Violation");
        assertThat(problem.getDetail()).isEqualTo("Cart is empty");
    }
}
