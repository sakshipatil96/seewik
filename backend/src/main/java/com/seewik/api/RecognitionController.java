package com.seewik.api;

import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recognition")
public class RecognitionController {
    private final CitizenIdentityVerifier identityVerifier;
    private final RecognitionService recognition;
    private final PaidEndpointRateLimiter rateLimiter;

    public RecognitionController(
            CitizenIdentityVerifier identityVerifier,
            RecognitionService recognition,
            PaidEndpointRateLimiter rateLimiter) {
        this.identityVerifier = identityVerifier;
        this.recognition = recognition;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping(value = "/current", produces = "application/json")
    public ResponseEntity<?> current() {
        try {
            return ResponseEntity.ok(recognition.publicPanel());
        } catch (RecognitionService.RecognitionException exception) {
            return recognitionFailure(exception);
        }
    }

    @GetMapping(value = "/me/points", produces = "application/json")
    public ResponseEntity<?> privatePoints(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            var citizen = identityVerifier.verifyBearer(authorization);
            return ResponseEntity.ok(recognition.privatePoints(citizen.uid()));
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            return unauthorized(exception);
        } catch (CitizenProfileService.ProfileException exception) {
            return profileFailure(exception);
        } catch (RecognitionService.RecognitionException exception) {
            return recognitionFailure(exception);
        }
    }

    @GetMapping(value = "/me/rewards", produces = "application/json")
    public ResponseEntity<?> rewards(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            var citizen = identityVerifier.verifyBearer(authorization);
            return ResponseEntity.ok(recognition.rewardOverview(citizen.uid()));
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            return unauthorized(exception);
        } catch (RecognitionService.RecognitionException exception) {
            return recognitionFailure(exception);
        }
    }

    @PostMapping(value = "/me/rewards/claims", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> claimReward(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) RecognitionService.RewardClaimRequest request) {
        try {
            var citizen = CitizenIdentityVerifier.requireGoogleLinked(identityVerifier.verifyBearer(authorization));
            rateLimiter.check(citizen.uid(), PaidEndpointRateLimiter.REWARD_CLAIMS);
            RecognitionService.RewardClaimResponse response = recognition.claimReward(citizen.uid(), request);
            return ResponseEntity.status("REWARD_CLAIM_CREATED".equals(response.status())
                    ? HttpStatus.CREATED : HttpStatus.OK).body(response);
        } catch (CitizenIdentityVerifier.LinkedIdentityRequiredException exception) {
            return googleLinkRequired(exception);
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            return unauthorized(exception);
        } catch (PaidEndpointRateLimiter.RateLimitedException exception) {
            return rateLimited(exception);
        } catch (PaidEndpointRateLimiter.LimiterUnavailableException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(error("REWARD_PROTECTION_UNAVAILABLE", "Reward claims are temporarily unavailable. Try again later."));
        } catch (RecognitionService.RecognitionException exception) {
            return recognitionFailure(exception);
        }
    }

    @PostMapping(value = "/me/rewards/claims/{claimId}/simulate-use", produces = "application/json")
    public ResponseEntity<?> simulateRewardUse(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String claimId) {
        try {
            var citizen = CitizenIdentityVerifier.requireGoogleLinked(identityVerifier.verifyBearer(authorization));
            rateLimiter.check(citizen.uid(), PaidEndpointRateLimiter.REWARD_CLAIMS);
            return ResponseEntity.ok(recognition.useRewardClaim(citizen.uid(), claimId));
        } catch (CitizenIdentityVerifier.LinkedIdentityRequiredException exception) {
            return googleLinkRequired(exception);
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            return unauthorized(exception);
        } catch (PaidEndpointRateLimiter.RateLimitedException exception) {
            return rateLimited(exception);
        } catch (PaidEndpointRateLimiter.LimiterUnavailableException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(error("REWARD_PROTECTION_UNAVAILABLE", "Reward use is temporarily unavailable. Try again later."));
        } catch (RecognitionService.RecognitionException exception) {
            return recognitionFailure(exception);
        }
    }

    @GetMapping(value = "/me/settings", produces = "application/json")
    public ResponseEntity<?> settings(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            var citizen = CitizenIdentityVerifier.requireGoogleLinked(identityVerifier.verifyBearer(authorization));
            return ResponseEntity.ok(recognition.settings(citizen.uid()));
        } catch (CitizenIdentityVerifier.LinkedIdentityRequiredException exception) {
            return googleLinkRequired(exception);
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            return unauthorized(exception);
        } catch (CitizenProfileService.ProfileException exception) {
            return profileFailure(exception);
        } catch (RecognitionService.RecognitionException exception) {
            return recognitionFailure(exception);
        }
    }

    @PutMapping(value = "/me/settings", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> updateSettings(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) RecognitionService.RecognitionSettingsRequest request) {
        try {
            var citizen = CitizenIdentityVerifier.requireGoogleLinked(identityVerifier.verifyBearer(authorization));
            return ResponseEntity.ok(recognition.updateSettings(citizen.uid(), request));
        } catch (CitizenIdentityVerifier.LinkedIdentityRequiredException exception) {
            return googleLinkRequired(exception);
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            return unauthorized(exception);
        } catch (CitizenProfileService.ProfileException exception) {
            return profileFailure(exception);
        } catch (RecognitionService.RecognitionException exception) {
            return recognitionFailure(exception);
        }
    }

    @PostMapping(value = "/reports", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> report(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) RecognitionService.AbuseReportRequest request) {
        try {
            var citizen = CitizenIdentityVerifier.requireGoogleLinked(identityVerifier.verifyBearer(authorization));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(recognition.reportDisplayedName(citizen.uid(), request));
        } catch (CitizenIdentityVerifier.LinkedIdentityRequiredException exception) {
            return googleLinkRequired(exception);
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            return unauthorized(exception);
        } catch (RecognitionService.RecognitionException exception) {
            return recognitionFailure(exception);
        }
    }

    private static ResponseEntity<Map<String, String>> unauthorized(
            CitizenIdentityVerifier.AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(error("AUTHENTICATION_REQUIRED", exception.getMessage()));
    }

    private static ResponseEntity<Map<String, String>> googleLinkRequired(
            CitizenIdentityVerifier.LinkedIdentityRequiredException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("GOOGLE_LINK_REQUIRED", exception.getMessage()));
    }

    private static ResponseEntity<Map<String, String>> recognitionFailure(
            RecognitionService.RecognitionException exception) {
        HttpStatus status = switch (exception.code()) {
            case "RECOGNITION_TARGET_UNAVAILABLE", "REWARD_CLAIM_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "REWARD_CLAIM_FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "REWARD_CLAIM_EXPIRED" -> HttpStatus.GONE;
            case "RECOGNITION_STORE_FAILED" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(error(exception.code(), exception.getMessage()));
    }

    private static ResponseEntity<Map<String, String>> rateLimited(
            PaidEndpointRateLimiter.RateLimitedException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.retryAfterSeconds()))
                .body(error("RATE_LIMITED", "Too many reward requests. Try again shortly."));
    }

    private static ResponseEntity<Map<String, String>> profileFailure(
            CitizenProfileService.ProfileException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(error(exception.code(), exception.getMessage()));
    }

    private static Map<String, String> error(String code, String message) {
        return Map.of("status", "RECOGNITION_ERROR", "errorCode", code, "message", message);
    }
}
