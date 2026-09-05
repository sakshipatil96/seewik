package com.seewik.api;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-e2e")
@RequestMapping("/api/local-e2e/clock")
public class LocalE2EClockController {
    private final AdjustableClock clock;

    LocalE2EClockController(AdjustableClock clock) {
        this.clock = clock;
    }

    @GetMapping(produces = "application/json")
    Map<String, Object> status() {
        return response();
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    ResponseEntity<?> set(@RequestBody(required = false) ClockRequest request) {
        if (request == null || request.offsetDays() < 0 || request.offsetDays() > 30) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "INVALID_CLOCK_OFFSET",
                    "message", "offsetDays must be between 0 and 30"));
        }
        clock.setOffsetDays(request.offsetDays());
        return ResponseEntity.ok(response());
    }

    private Map<String, Object> response() {
        return Map.of(
                "status", "LOCAL_E2E_CLOCK",
                "offsetDays", clock.offsetDays(),
                "serverNow", clock.instant().toString());
    }

    public record ClockRequest(long offsetDays) {}
}
