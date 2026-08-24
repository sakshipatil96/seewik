package com.seewik.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ClassificationControllerAdviceTest {
    @Test
    void oversizedMultipartFailureHasControlledResponse() {
        var response = new ClassificationControllerAdvice().imageTooLarge();
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertEquals("CLASSIFICATION_ERROR", response.getBody().get("status"));
        assertEquals("IMAGE_TOO_LARGE", response.getBody().get("errorCode"));
    }
}
