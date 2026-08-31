package com.seewik.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class CitizenProfileController {
    private final CitizenIdentityVerifier identityVerifier;
    private final CitizenProfileService profiles;

    public CitizenProfileController(
            CitizenIdentityVerifier identityVerifier,
            CitizenProfileService profiles) {
        this.identityVerifier = identityVerifier;
        this.profiles = profiles;
    }

    @PostMapping(value = "/sync", produces = "application/json")
    public ResponseEntity<?> sync(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return withLinkedCitizen(authorization, true);
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<?> get(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return withLinkedCitizen(authorization, false);
    }

    private ResponseEntity<?> withLinkedCitizen(String authorization, boolean sync) {
        try {
            var citizen = CitizenIdentityVerifier.requireGoogleLinked(identityVerifier.verifyBearer(authorization));
            return ResponseEntity.ok(sync ? profiles.sync(citizen.uid()) : profiles.get(citizen.uid()));
        } catch (CitizenIdentityVerifier.LinkedIdentityRequiredException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(error("GOOGLE_LINK_REQUIRED", exception.getMessage()));
        } catch (CitizenIdentityVerifier.AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("AUTHENTICATION_REQUIRED", exception.getMessage()));
        } catch (CitizenProfileService.ProfileException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(error(exception.code(), exception.getMessage()));
        }
    }

    private static Map<String, String> error(String code, String message) {
        return Map.of("status", "PROFILE_ERROR", "errorCode", code, "message", message);
    }
}
