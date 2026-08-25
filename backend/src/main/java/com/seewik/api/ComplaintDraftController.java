package com.seewik.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/civic")
public class ComplaintDraftController {
    private final ComplaintDraftService complaintDraftService;

    public ComplaintDraftController(ComplaintDraftService complaintDraftService) {
        this.complaintDraftService = complaintDraftService;
    }

    @PostMapping(value = "/draft-complaint", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> draft(@RequestBody(required = false) ComplaintDraftService.ComplaintDraftRequest request) {
        try {
            return ResponseEntity.ok(complaintDraftService.draft(request));
        } catch (ComplaintDraftService.ComplaintDraftInputException exception) {
            HttpStatus status = switch (exception.code()) {
                case "UNSUPPORTED_ROUTE" -> HttpStatus.UNPROCESSABLE_ENTITY;
                case "ROUTE_CONFIRMATION_REQUIRED" -> HttpStatus.CONFLICT;
                default -> HttpStatus.BAD_REQUEST;
            };
            return ResponseEntity.status(status).body(error(exception.code(), exception.getMessage()));
        } catch (ComplaintDraftService.ComplaintDraftExecutionException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error(
                    exception.code(),
                    "A complaint draft could not be created. Your confirmed route remains available."));
        }
    }

    private static Map<String, String> error(String code, String message) {
        return Map.of("status", "DRAFT_ERROR", "errorCode", code, "message", message);
    }
}
