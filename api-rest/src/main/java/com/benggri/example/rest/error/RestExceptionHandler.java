package com.benggri.example.rest.error;

import com.benggri.example.core.common.DuplicateException;
import com.benggri.example.core.common.ForbiddenException;
import com.benggri.example.core.common.NotFoundException;
import com.benggri.example.core.security.InvalidTokenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every failure to an {@code application/problem+json} body with a fixed, generic detail.
 * Exception messages and rejected values are never echoed: they can carry emails, passwords or
 * internals. Standard MVC errors (404, 405, 415, malformed JSON) come from the parent class.
 */
@Slf4j
@RestControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound() {
        return problem(HttpStatus.NOT_FOUND, "Resource not found");
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail forbidden() {
        return problem(HttpStatus.FORBIDDEN, "Access is denied");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDenied() {
        return problem(HttpStatus.FORBIDDEN, "Access is denied");
    }

    @ExceptionHandler(DuplicateException.class)
    ProblemDetail duplicate() {
        return problem(HttpStatus.CONFLICT, "Resource already exists");
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail badCredentials() {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    @ExceptionHandler(InvalidTokenException.class)
    ProblemDetail invalidToken() {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid token");
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail authentication() {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication is required");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolation(ConstraintViolationException ex) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> error(lastSegment(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return validation(errors);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex, HttpServletRequest request) {
        // full detail stays server-side; the client only gets a generic body
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> error(f.getField(), f.getDefaultMessage()))
                .toList();
        return respond(validation(errors));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return respond(problem(HttpStatus.BAD_REQUEST, "Validation failed"));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return respond(problem(HttpStatus.BAD_REQUEST, "Invalid request parameter"));
    }

    private static ResponseEntity<Object> respond(ProblemDetail body) {
        return ResponseEntity.status(body.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private static ProblemDetail validation(List<Map<String, String>> errors) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed");
        detail.setProperty("errors", errors);
        return detail;
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }

    private static Map<String, String> error(String field, String message) {
        return Map.of("field", field, "message", String.valueOf(message));
    }

    private static String lastSegment(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }
}
