package com.seewik.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportLifecycleController {
    private final CitizenIdentityVerifier identityVerifier;
    private final ReportLifecycleService lifecycleService;

    public ReportLifecycleController(
            CitizenIdentityVerifier identityVerifier,
            ReportLifecycleService lifecycleService) {
        this.identityVerifier = identityVerifier;
        this.lifecycleService = lifecycleService;
    }

    @PostMapping(
            value = "/api/reports/{reportId}/transitions",
            consumes = "application/json",
            produces = "application/json")
    public ResponseEntity<?> transition(
            @PathVariable String reportId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) ReportLifecycleService.TransitionRequest request) {
        try {
            CitizenIdentityVerifier.AuthenticatedCitizen citizen = identityVerifier.verifyBearer(authorization);
            ReportLifecycleService.TransitionResponse response =
                    lifecycleService.transition(citizen.uid(), reportId, request);
            return "POSSIBLE_DUPLICATE".equals(response.status())
                    ? ResponseEntity.status(HttpStatus.CONFLICT).body(response)
                    : ResponseEntity.ok(response);
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("AUTHENTICATION_REQUIRED", exception.getMessage()));
        } catch (ReportLifecycleService.LifecycleException exception) {
            return ResponseEntity.status(statusFor(exception.code()))
                    .body(error(exception.code(), exception.getMessage()));
        }
    }

    private static HttpStatus statusFor(String code) {
        return switch (code) {
            case "REPORT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "REPORT_FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "INVALID_TRANSITION", "IDEMPOTENCY_KEY_REUSED" -> HttpStatus.CONFLICT;
            case "OVERDUE_NOT_ELIGIBLE", "OVERDUE_NOT_REACHED" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "LIFECYCLE_STORE_FAILED", "LIFECYCLE_STORE_INTERRUPTED" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    private static Map<String, String> error(String code, String message) {
        return Map.of("status", "LIFECYCLE_ERROR", "errorCode", code, "message", message);
    }
}
