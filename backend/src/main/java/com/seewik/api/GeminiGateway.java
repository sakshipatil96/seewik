package com.seewik.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;

@FunctionalInterface
public interface GeminiGateway {
    GeneratedContent generateStructured(
            String prompt,
            byte[] image,
            String mimeType,
            JsonNode responseSchema) throws Exception;

    default GeneratedContent generateStructured(
            String prompt,
            byte[] image,
            String mimeType,
            JsonNode responseSchema,
            int maxOutputTokens) throws Exception {
        return generateStructured(prompt, image, mimeType, responseSchema);
    }

    default GeneratedContent generateStructured(
            String prompt,
            byte[] image,
            String mimeType,
            JsonNode responseSchema,
            int maxOutputTokens,
            Duration timeout) throws Exception {
        return generateStructured(prompt, image, mimeType, responseSchema, maxOutputTokens);
    }

    final class ModelTransportTimeoutException extends Exception {
        ModelTransportTimeoutException(Throwable cause) {
            super("The model HTTP request exceeded its deadline", cause);
        }
    }

    record GeneratedContent(
            String text,
            String modelVersion,
            String responseId,
            Long promptTokenCount,
            Long candidatesTokenCount,
            Long totalTokenCount,
            String finishReason) {
        GeneratedContent(
                String text,
                String modelVersion,
                String responseId,
                Long promptTokenCount,
                Long candidatesTokenCount,
                Long totalTokenCount) {
            this(text, modelVersion, responseId, promptTokenCount, candidatesTokenCount, totalTokenCount, null);
        }
    }
}
