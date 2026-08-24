package com.seewik.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/civic")
public class ClassificationController {
    private final CivicClassificationService classificationService;

    public ClassificationController(CivicClassificationService classificationService) {
        this.classificationService = classificationService;
    }

    @PostMapping(value = "/classify", consumes = "multipart/form-data", produces = "application/json")
    public ResponseEntity<?> classify(
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "text", required = false) String text) {
        try {
            byte[] bytes = image == null || image.isEmpty() ? null : image.getBytes();
            String mimeType = bytes == null ? null : image.getContentType();
            return ResponseEntity.ok(classificationService.classify(bytes, mimeType, text));
        } catch (CivicClassificationService.ClassificationInputException exception) {
            return ResponseEntity.badRequest().body(error(exception.code(), exception.getMessage()));
        } catch (CivicClassificationService.ClassificationExecutionException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(error(exception.code(), "Classification could not be completed. Please choose a category manually."));
        } catch (java.io.IOException exception) {
            return ResponseEntity.badRequest().body(error("IMAGE_READ_FAILED", "The uploaded image could not be read"));
        }
    }

    private static Map<String, String> error(String code, String message) {
        return Map.of("status", "CLASSIFICATION_ERROR", "errorCode", code, "message", message);
    }
}
