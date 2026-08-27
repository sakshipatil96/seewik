package com.seewik.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/civic")
public class ClassificationController {
    private final CivicClassificationService classificationService;
    private final CitizenIdentityVerifier identityVerifier;
    private final PaidEndpointRateLimiter rateLimiter;
    private final OperationalMetrics metrics;

    public ClassificationController(
            CivicClassificationService classificationService,
            CitizenIdentityVerifier identityVerifier,
            PaidEndpointRateLimiter rateLimiter,
            OperationalMetrics metrics) {
        this.classificationService = classificationService;
        this.identityVerifier = identityVerifier;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
    }

    @PostMapping(value = "/classify", consumes = "multipart/form-data", produces = "application/json")
    public ResponseEntity<?> classify(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "text", required = false) String text) {
        long startedAt = System.nanoTime();
        try {
            CitizenIdentityVerifier.AuthenticatedCitizen citizen = identityVerifier.verifyBearer(authorization);
            metrics.increment("request.classification.authenticated");
            rateLimiter.check(citizen.uid(), PaidEndpointRateLimiter.CLASSIFICATION);
            byte[] bytes = image == null || image.isEmpty() ? null : image.getBytes();
            String mimeType = bytes == null ? null : image.getContentType();
            return ResponseEntity.ok(classificationService.classify(bytes, mimeType, text));
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            metrics.increment("request.classification.authentication_rejected");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("AUTHENTICATION_REQUIRED", exception.getMessage()));
        } catch (PaidEndpointRateLimiter.RateLimitedException exception) {
            metrics.increment("rate_limit." + exception.scope() + ".rejected");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.retryAfterSeconds()))
                    .body(error("RATE_LIMITED", "Too many automatic classification requests. Choose a category manually or retry later."));
        } catch (PaidEndpointRateLimiter.LimiterUnavailableException exception) {
            metrics.increment("rate_limit.protection_unavailable");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(
                    "BILLING_PROTECTION_UNAVAILABLE",
                    "Automatic classification is temporarily unavailable. Choose a category manually."));
        } catch (CivicClassificationService.ClassificationInputException exception) {
            return ResponseEntity.badRequest().body(error(exception.code(), exception.getMessage()));
        } catch (CivicClassificationService.ClassificationExecutionException exception) {
            HttpStatus status = "MODEL_TIMEOUT".equals(exception.code())
                    ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
            String message = "MODEL_TIMEOUT".equals(exception.code())
                    ? "Automatic classification took too long. Choose a category manually or retry later."
                    : "Classification could not be completed. Please choose a category manually.";
            return ResponseEntity.status(status).body(error(exception.code(), message));
        } catch (java.io.IOException exception) {
            return ResponseEntity.badRequest().body(error("IMAGE_READ_FAILED", "The uploaded image could not be read"));
        } finally {
            metrics.recordLatency("endpoint.classification", elapsedMillis(startedAt));
        }
    }

    private static Map<String, String> error(String code, String message) {
        return Map.of("status", "CLASSIFICATION_ERROR", "errorCode", code, "message", message);
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
