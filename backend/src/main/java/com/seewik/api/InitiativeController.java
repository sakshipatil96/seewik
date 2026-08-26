package com.seewik.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/initiatives")
public class InitiativeController {
    private final CitizenIdentityVerifier identityVerifier;
    private final InitiativeService initiativeService;

    public InitiativeController(
            CitizenIdentityVerifier identityVerifier,
            InitiativeService initiativeService) {
        this.identityVerifier = identityVerifier;
        this.initiativeService = initiativeService;
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) InitiativeService.CreateRequest request) {
        try {
            var citizen = identityVerifier.verifyBearer(authorization);
            return ResponseEntity.status(HttpStatus.CREATED).body(initiativeService.create(citizen.uid(), request));
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            return unauthorized(exception);
        } catch (InitiativeService.InitiativeException exception) {
            return failure(exception);
        }
    }

    @PostMapping(value = "/nearby", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> nearby(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) InitiativeService.DiscoveryRequest request) {
        try {
            identityVerifier.verifyBearer(authorization);
            return ResponseEntity.ok(initiativeService.discover(request));
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            return unauthorized(exception);
        } catch (InitiativeService.InitiativeException exception) {
            return failure(exception);
        }
    }

    @PostMapping(value = "/{initiativeId}/join", produces = "application/json")
    public ResponseEntity<?> join(
            @PathVariable String initiativeId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            var citizen = identityVerifier.verifyBearer(authorization);
            return ResponseEntity.ok(initiativeService.join(citizen.uid(), initiativeId));
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            return unauthorized(exception);
        } catch (InitiativeService.InitiativeException exception) {
            return failure(exception);
        }
    }

    private static ResponseEntity<Map<String, String>> unauthorized(
            CitizenIdentityVerifier.AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(error("AUTHENTICATION_REQUIRED", exception.getMessage()));
    }

    private static ResponseEntity<Map<String, String>> failure(InitiativeService.InitiativeException exception) {
        HttpStatus status = switch (exception.code()) {
            case "INITIATIVE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INITIATIVE_NOT_JOINABLE" -> HttpStatus.CONFLICT;
            case "INITIATIVE_STORE_FAILED", "INITIATIVE_STORE_INTERRUPTED" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(error(exception.code(), exception.getMessage()));
    }

    private static Map<String, String> error(String code, String message) {
        return Map.of("status", "INITIATIVE_ERROR", "errorCode", code, "message", message);
    }
}
