package com.seewik.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/civic")
public class ComplaintDraftController {
    private final ComplaintDraftService complaintDraftService;
    private final CitizenIdentityVerifier identityVerifier;
    private final PaidEndpointRateLimiter rateLimiter;
    private final OperationalMetrics metrics;

    public ComplaintDraftController(
            ComplaintDraftService complaintDraftService,
            CitizenIdentityVerifier identityVerifier,
            PaidEndpointRateLimiter rateLimiter,
            OperationalMetrics metrics) {
        this.complaintDraftService = complaintDraftService;
        this.identityVerifier = identityVerifier;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
    }

    @PostMapping(value = "/draft-complaint", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> draft(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) ComplaintDraftService.ComplaintDraftRequest request) {
        long startedAt = System.nanoTime();
        try {
            CitizenIdentityVerifier.AuthenticatedCitizen citizen = identityVerifier.verifyBearer(authorization);
            metrics.increment("request.drafting.authenticated");
            rateLimiter.check(citizen.uid(), PaidEndpointRateLimiter.DRAFTING);
            return ResponseEntity.ok(complaintDraftService.draft(request));
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            metrics.increment("request.drafting.authentication_rejected");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("AUTHENTICATION_REQUIRED", exception.getMessage()));
        } catch (PaidEndpointRateLimiter.RateLimitedException exception) {
            metrics.increment("rate_limit." + exception.scope() + ".rejected");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.retryAfterSeconds()))
                    .body(error("RATE_LIMITED", "Too many automatic drafting requests. Write the complaint manually or retry later."));
        } catch (PaidEndpointRateLimiter.LimiterUnavailableException exception) {
            metrics.increment("rate_limit.protection_unavailable");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(
                    "BILLING_PROTECTION_UNAVAILABLE",
                    "Automatic drafting is temporarily unavailable. Your route remains available; write or copy your own complaint."));
        } catch (ComplaintDraftService.ComplaintDraftInputException exception) {
            HttpStatus status = switch (exception.code()) {
                case "UNSUPPORTED_ROUTE" -> HttpStatus.UNPROCESSABLE_ENTITY;
                case "ROUTE_CONFIRMATION_REQUIRED" -> HttpStatus.CONFLICT;
                default -> HttpStatus.BAD_REQUEST;
            };
            return ResponseEntity.status(status).body(error(exception.code(), exception.getMessage()));
        } catch (ComplaintDraftService.ComplaintDraftExecutionException exception) {
            HttpStatus status = "MODEL_TIMEOUT".equals(exception.code())
                    ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
            String message = "MODEL_TIMEOUT".equals(exception.code())
                    ? "Automatic drafting took too long. Your confirmed route remains available; write or copy your own complaint."
                    : "A complaint draft could not be created. Your confirmed route remains available.";
            return ResponseEntity.status(status).body(error(exception.code(), message));
        } finally {
            metrics.recordLatency("endpoint.drafting", elapsedMillis(startedAt));
        }
    }

    @PostMapping(value = "/draft-observation", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> observeDraftAttempt(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) DraftAttemptObservation observation) {
        try {
            identityVerifier.verifyBearer(authorization);
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("AUTHENTICATION_REQUIRED", exception.getMessage()));
        }
        if (observation == null || observation.attemptNumber() == null || observation.durationMs() == null
                || observation.outcome() == null || observation.attemptNumber() < 1 || observation.attemptNumber() > 2
                || observation.durationMs() < 0 || observation.durationMs() > 300_000) {
            return ResponseEntity.badRequest().body(error("INVALID_OBSERVATION", "Draft observation fields are invalid."));
        }
        String outcome = switch (observation.outcome()) {
            case "SUCCESS", "TIMEOUT", "HTTP_ERROR", "INVALID_RESPONSE", "NETWORK_ERROR" -> observation.outcome();
            default -> null;
        };
        if (outcome == null) {
            return ResponseEntity.badRequest().body(error("INVALID_OBSERVATION", "Draft observation outcome is invalid."));
        }
        String metric = "client.drafting.attempt_" + observation.attemptNumber();
        metrics.increment(metric + "." + outcome.toLowerCase());
        metrics.recordLatency(metric, observation.durationMs());
        return ResponseEntity.noContent().build();
    }

    public record DraftAttemptObservation(Integer attemptNumber, Long durationMs, String outcome) {}

    private static Map<String, String> error(String code, String message) {
        return Map.of("status", "DRAFT_ERROR", "errorCode", code, "message", message);
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
