package com.seewik.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
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
            var citizen = CitizenIdentityVerifier.requireGoogleLinked(identityVerifier.verifyBearer(authorization));
            return ResponseEntity.status(HttpStatus.CREATED).body(initiativeService.create(citizen.uid(), request));
        } catch (CitizenIdentityVerifier.LinkedIdentityRequiredException exception) {
            return googleLinkRequired(exception);
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
            var citizen = identityVerifier.verifyBearer(authorization);
            InitiativeService.DiscoveryRequest ownedRequest = request == null
                    ? new InitiativeService.DiscoveryRequest(citizen.uid(), null, null, null)
                    : request.withOwnerUid(citizen.uid());
            return ResponseEntity.ok(initiativeService.discover(ownedRequest));
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            return unauthorized(exception);
        } catch (InitiativeService.InitiativeException exception) {
            return failure(exception);
        }
    }

    @GetMapping(value = "/mine", produces = "application/json")
    public ResponseEntity<?> mine(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            var citizen = identityVerifier.verifyBearer(authorization);
            return ResponseEntity.ok(initiativeService.mine(citizen.uid()));
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            return unauthorized(exception);
        } catch (InitiativeService.InitiativeException exception) {
            return failure(exception);
        }
    }

    @PostMapping(value = "/{initiativeId}/cancel", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> cancel(
            @PathVariable String initiativeId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) InitiativeService.CancelRequest request) {
        try {
            var citizen = CitizenIdentityVerifier.requireGoogleLinked(identityVerifier.verifyBearer(authorization));
            return ResponseEntity.ok(initiativeService.cancel(citizen.uid(), initiativeId, request));
        } catch (CitizenIdentityVerifier.LinkedIdentityRequiredException exception) {
            return googleLinkRequired(exception);
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            return unauthorized(exception);
        } catch (InitiativeService.InitiativeException exception) {
            return failure(exception);
        }
    }

    @PostMapping(value = "/{initiativeId}/complete", produces = "application/json")
    public ResponseEntity<?> complete(
            @PathVariable String initiativeId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            var citizen = CitizenIdentityVerifier.requireGoogleLinked(identityVerifier.verifyBearer(authorization));
            return ResponseEntity.ok(initiativeService.complete(citizen.uid(), initiativeId));
        } catch (CitizenIdentityVerifier.LinkedIdentityRequiredException exception) {
            return googleLinkRequired(exception);
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
            var citizen = CitizenIdentityVerifier.requireGoogleLinked(identityVerifier.verifyBearer(authorization));
            return ResponseEntity.ok(initiativeService.join(citizen.uid(), initiativeId));
        } catch (CitizenIdentityVerifier.LinkedIdentityRequiredException exception) {
            return googleLinkRequired(exception);
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            return unauthorized(exception);
        } catch (InitiativeService.InitiativeException exception) {
            return failure(exception);
        }
    }

    @GetMapping(value = "/{initiativeId}/attendance/code", produces = "application/json")
    public ResponseEntity<?> attendanceCode(
            @PathVariable String initiativeId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            var citizen = CitizenIdentityVerifier.requireGoogleLinked(identityVerifier.verifyBearer(authorization));
            return ResponseEntity.ok(initiativeService.attendanceCode(citizen.uid(), initiativeId));
        } catch (CitizenIdentityVerifier.LinkedIdentityRequiredException exception) {
            return googleLinkRequired(exception);
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            return unauthorized(exception);
        } catch (InitiativeService.InitiativeException exception) {
            return failure(exception);
        }
    }

    @PostMapping(value = "/{initiativeId}/attendance/self", produces = "application/json")
    public ResponseEntity<?> selfAttendance(
            @PathVariable String initiativeId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            var citizen = CitizenIdentityVerifier.requireGoogleLinked(identityVerifier.verifyBearer(authorization));
            return ResponseEntity.ok(initiativeService.selfAttend(citizen.uid(), initiativeId));
        } catch (CitizenIdentityVerifier.LinkedIdentityRequiredException exception) {
            return googleLinkRequired(exception);
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            return unauthorized(exception);
        } catch (InitiativeService.InitiativeException exception) {
            return failure(exception);
        }
    }

    @PostMapping(
            value = "/{initiativeId}/attendance/code",
            consumes = "application/json",
            produces = "application/json")
    public ResponseEntity<?> codeAttendance(
            @PathVariable String initiativeId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) InitiativeService.AttendanceCodeRequest request) {
        try {
            var citizen = CitizenIdentityVerifier.requireGoogleLinked(identityVerifier.verifyBearer(authorization));
            return ResponseEntity.ok(initiativeService.codeAttend(citizen.uid(), initiativeId, request));
        } catch (CitizenIdentityVerifier.LinkedIdentityRequiredException exception) {
            return googleLinkRequired(exception);
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

    private static ResponseEntity<Map<String, String>> googleLinkRequired(
            CitizenIdentityVerifier.LinkedIdentityRequiredException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("GOOGLE_LINK_REQUIRED", exception.getMessage()));
    }

    private static ResponseEntity<Map<String, String>> failure(InitiativeService.InitiativeException exception) {
        HttpStatus status = switch (exception.code()) {
            case "INITIATIVE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INITIATIVE_NOT_JOINABLE", "INITIATIVE_INVALID_TRANSITION", "INITIATIVE_NOT_STARTED",
                    "INITIATIVE_ATTENDANCE_EXISTS", "ATTENDANCE_ALREADY_RECORDED",
                    "ATTENDANCE_REQUIRES_COMPLETION", "ATTENDANCE_CODE_WINDOW_CLOSED",
                    "SELF_ATTENDANCE_WINDOW_CLOSED", "ATTENDANCE_UNAVAILABLE" -> HttpStatus.CONFLICT;
            case "INITIATIVE_FORBIDDEN", "ATTENDANCE_NOT_PARTICIPANT" -> HttpStatus.FORBIDDEN;
            case "ATTENDANCE_RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "INITIATIVE_STORE_FAILED", "INITIATIVE_STORE_INTERRUPTED",
                    "ATTENDANCE_CONFIGURATION_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(error(exception.code(), exception.getMessage()));
    }

    private static Map<String, String> error(String code, String message) {
        return Map.of("status", "INITIATIVE_ERROR", "errorCode", code, "message", message);
    }
}
