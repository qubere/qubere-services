package ai.qubere.document.agent.api;

import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class AgentApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentApiExceptionHandler.class);

    @ExceptionHandler(AgentExecutionException.class)
    ResponseEntity<ApiErrorResponse> handleAgentExecutionException(AgentExecutionException exception, HttpServletRequest request) {
        HttpStatus status = mapStatus(exception.errorCode());
        return ResponseEntity
                .status(status)
                .body(ApiErrorResponse.of(status.value(), exception.errorCode().name(), exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiErrorResponse> handleBadRequest(IllegalArgumentException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity
                .status(status)
                .body(ApiErrorResponse.of(status.value(), AgentErrorCode.VALIDATION_FAILED.name(), exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiErrorResponse> handleNoSuchElement(NoSuchElementException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return ResponseEntity
                .status(status)
                .body(ApiErrorResponse.of(status.value(), AgentErrorCode.NOT_FOUND.name(), exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiErrorResponse> handleConflict(IllegalStateException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.CONFLICT;
        return ResponseEntity
                .status(status)
                .body(ApiErrorResponse.of(status.value(), "CONFLICT", exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidationFailure(MethodArgumentNotValidException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity
                .status(status)
                .body(ApiErrorResponse.of(
                        status.value(),
                        AgentErrorCode.VALIDATION_FAILED.name(),
                        "Request validation failed",
                        request.getRequestURI(),
                        fieldErrors
                ));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity
                .status(status)
                .body(ApiErrorResponse.of(status.value(), AgentErrorCode.VALIDATION_FAILED.name(), "Malformed request", request.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.METHOD_NOT_ALLOWED;
        return ResponseEntity
                .status(status)
                .body(ApiErrorResponse.of(status.value(), "METHOD_NOT_ALLOWED", exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.UNSUPPORTED_MEDIA_TYPE;
        return ResponseEntity
                .status(status)
                .body(ApiErrorResponse.of(status.value(), "UNSUPPORTED_MEDIA_TYPE", exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ApiErrorResponse> handleNotFound(Exception exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return ResponseEntity
                .status(status)
                .body(ApiErrorResponse.of(status.value(), AgentErrorCode.NOT_FOUND.name(), exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        log.error("Unhandled exception while processing {} {}", request.getMethod(), request.getRequestURI(), exception);
        return ResponseEntity
                .status(status)
                .body(ApiErrorResponse.of(status.value(), AgentErrorCode.EXECUTION_FAILED.name(), exception.getMessage(), request.getRequestURI()));
    }

    private HttpStatus mapStatus(AgentErrorCode errorCode) {
        return switch (errorCode) {
            case NOT_FOUND, AGENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case AGENT_DISABLED, AUTHORIZATION_DENIED, GUARDRAIL_BLOCKED -> HttpStatus.FORBIDDEN;
            case GOVERNANCE_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case AI_PROVIDER_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case VALIDATION_FAILED, TOOL_NOT_FOUND -> HttpStatus.BAD_REQUEST;
            case TOOL_NOT_ALLOWED, TOOL_APPROVAL_REQUIRED -> HttpStatus.FORBIDDEN;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case EXECUTION_FAILED, AI_PROVIDER_FAILURE, TOOL_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
