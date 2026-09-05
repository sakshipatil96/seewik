package com.seewik.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports/{reportId}/follow-ups")
public class ReportFollowUpController {
    private final CitizenIdentityVerifier identityVerifier;
    private final ReportFollowUpService service;

    public ReportFollowUpController(CitizenIdentityVerifier identityVerifier, ReportFollowUpService service) {
        this.identityVerifier = identityVerifier;
        this.service = service;
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<?> get(
            @PathVariable String reportId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return handle(reportId, authorization, null, false);
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> record(
            @PathVariable String reportId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) ReportFollowUpService.FollowUpRequest request) {
        return handle(reportId, authorization, request, true);
    }

    private ResponseEntity<?> handle(
            String reportId,
            String authorization,
            ReportFollowUpService.FollowUpRequest request,
            boolean write) {
        try {
            var citizen = CitizenIdentityVerifier.requireGoogleLinked(identityVerifier.verifyBearer(authorization));
            return ResponseEntity.ok(write
                    ? service.record(citizen.uid(), reportId, request)
                    : service.get(citizen.uid(), reportId));
        } catch (CitizenIdentityVerifier.LinkedIdentityRequiredException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("GOOGLE_LINK_REQUIRED", exception.getMessage()));
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("AUTHENTICATION_REQUIRED", exception.getMessage()));
        } catch (ReportFollowUpService.FollowUpException exception) {
            return ResponseEntity.status(statusFor(exception.code())).body(error(exception.code(), exception.getMessage()));
        }
    }

    private static HttpStatus statusFor(String code) {
        return switch (code) {
            case "REPORT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "REPORT_FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "IDEMPOTENCY_KEY_REUSED" -> HttpStatus.CONFLICT;
            case "FOLLOW_UP_NOT_DUE", "ESCALATION_NOT_AVAILABLE" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "FOLLOW_UP_STORE_FAILED" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    private static Map<String, String> error(String code, String message) {
        return Map.of("status", "FOLLOW_UP_ERROR", "errorCode", code, "message", message);
    }
}
