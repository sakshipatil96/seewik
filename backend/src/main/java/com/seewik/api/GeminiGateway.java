package com.seewik.api;

import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface GeminiGateway {
    GeneratedContent generateStructured(
            String prompt,
            byte[] image,
            String mimeType,
            JsonNode responseSchema) throws Exception;

    record GeneratedContent(
            String text,
            String modelVersion,
            String responseId,
            Long promptTokenCount,
            Long candidatesTokenCount,
            Long totalTokenCount) {}
}
