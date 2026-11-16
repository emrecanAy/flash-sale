package com.emrecanay.flashsale.shared;

import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Every error leaves the API as an RFC 9457 problem document. */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomainException(DomainException ex) {
        HttpStatus status = switch (ex.kind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setProperty("code", ex.code());
        return problem;
    }

    /** A database constraint caught something the application-level checks raced past. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleConstraintViolation(DataIntegrityViolationException ex) {
        log.warn("Request rejected by a database constraint: {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The request conflicts with the current state of the resource.");
        problem.setProperty("code", "conflict");
        return problem;
    }

    /** The connection pool is exhausted: shed the request quickly and tell the client when to come back. */
    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class})
    ResponseEntity<ProblemDetail> handleDatabaseSaturation(Exception ex) {
        log.warn("Shedding request, database unavailable or saturated: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The service is busy. Retry shortly.");
        problem.setProperty("code", "service_busy");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        ex.getFieldErrors().forEach(error -> addError(errors, error.getField(), error));
        return handleExceptionInternal(ex, validationProblem(ex.getBody(), errors), headers, status, request);
    }

    /** Raised instead of the exception above when a handler method also constrains headers or parameters. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors body) {
                body.getFieldErrors().forEach(error -> addError(errors, error.getField(), error));
            }
            else {
                String parameter = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(error -> addError(errors, parameter, error));
            }
        }
        return handleExceptionInternal(ex, validationProblem(ex.getBody(), errors), headers, status, request);
    }

    private static void addError(Map<String, String> errors, String name, MessageSourceResolvable error) {
        errors.putIfAbsent(name, String.valueOf(error.getDefaultMessage()));
    }

    private static ProblemDetail validationProblem(ProblemDetail problem, Map<String, String> errors) {
        problem.setProperty("code", "validation_failed");
        problem.setProperty("errors", errors);
        return problem;
    }
}
