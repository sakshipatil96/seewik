package com.seewik.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    public RecognitionController(
            CitizenIdentityVerifier identityVerifier,
            RecognitionService recognition) {
        this.identityVerifier = identityVerifier;
        this.recognition = recognition;
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
            case "RECOGNITION_TARGET_UNAVAILABLE" -> HttpStatus.NOT_FOUND;
            case "RECOGNITION_STORE_FAILED" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(error(exception.code(), exception.getMessage()));
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
