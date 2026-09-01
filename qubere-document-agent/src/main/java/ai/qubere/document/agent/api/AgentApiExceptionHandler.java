package ai.qubere.document.agent.api;

import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AgentApiExceptionHandler {

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



