package com.seewik.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

@Service
public class CivicRouterService {
    public static final String PACK_VERSION = "v0.1";
    private static final Set<String> SUPPORTED_PRABHAG_IDS = IntStream.rangeClosed(1, 20)
            .mapToObj(number -> "PRABHAG-%02d".formatted(number))
            .collect(Collectors.toUnmodifiableSet());
    private final CivicPack pack;
    private final Map<String, RouteDefinition> routesByIssueType;

    public CivicRouterService(ObjectMapper objectMapper) throws IOException {
        try (InputStream input = CivicRouterService.class.getResourceAsStream("/civic-pack-v0.1.json")) {
            if (input == null) {
                throw new IOException("Missing Civic Pack resource civic-pack-v0.1.json");
            }
            this.pack = objectMapper.readValue(input, CivicPack.class);
        }
        if (!PACK_VERSION.equals(pack.packVersion())) {
            throw new IOException("Unexpected Civic Pack version: " + pack.packVersion());
        }
        Map<String, RouteDefinition> indexed = new LinkedHashMap<>();
        for (RouteDefinition route : pack.routes()) {
            if (indexed.put(route.issueType(), route) != null) {
                throw new IOException("Duplicate issueType in Civic Pack: " + route.issueType());
            }
        }
        this.routesByIssueType = Map.copyOf(indexed);
    }

    public CivicRouteResponse route(CivicRouteRequest request) {
        String issueType = normalizeIssueType(request == null ? null : request.issueType());
        String prabhagId = request == null ? "" : firstNonBlank(request.prabhagId(), request.wardId());
        RouteDefinition route = routesByIssueType.get(issueType);
        if (route == null || !SUPPORTED_PRABHAG_IDS.contains(prabhagId)) {
            return CivicRouteResponse.unsupported(prabhagId.isBlank() ? null : prabhagId, PACK_VERSION);
        }
        String requestedMethod = normalizeResolutionMethod(request == null ? null : request.resolutionMethod());
        boolean syntheticCandidate = PrabhagResolverService.RESOLUTION_METHOD.equals(requestedMethod);
        if (!requestedMethod.isBlank() && !"SELF_REPORTED".equals(requestedMethod) && !syntheticCandidate) {
            return CivicRouteResponse.unsupported(prabhagId, PACK_VERSION);
        }
        if (syntheticCandidate
                && (!Boolean.TRUE.equals(request.citizenConfirmed())
                        || !PrabhagResolverService.DATASET_VERSION.equals(request.boundaryDatasetVersion()))) {
            return CivicRouteResponse.confirmationRequired(prabhagId, PACK_VERSION);
        }
        String resolutionMethod = syntheticCandidate
                ? "CITIZEN_CONFIRMED_SYNTHETIC_BOUNDARY"
                : "SELF_REPORTED";
        return new CivicRouteResponse(
                "SUPPORTED_ROUTE",
                route.routeId(),
                prabhagId,
                resolutionMethod,
                syntheticCandidate,
                syntheticCandidate ? PrabhagResolverService.DATASET_VERSION : null,
                pack.authority(),
                route.department(),
                pack.officialChannels(),
                "NOT_VERIFIED",
                "NOT_VERIFIED",
                route.officialSource(),
                route.sourceStatus(),
                route.reviewStatus(),
                pack.packVersion());
    }

    private static String normalizeIssueType(String issueType) {
        if (issueType == null) return "";
        return issueType.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String firstNonBlank(String preferred, String compatibilityAlias) {
        String value = preferred == null || preferred.isBlank() ? compatibilityAlias : preferred;
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeResolutionMethod(String resolutionMethod) {
        return resolutionMethod == null ? "" : resolutionMethod.trim().toUpperCase(Locale.ROOT);
    }

    public record CivicRouteRequest(
            String issueType,
            String prabhagId,
            String wardId,
            String resolutionMethod,
            Boolean citizenConfirmed,
            String boundaryDatasetVersion) {}

    public record CivicRouteResponse(
            String status,
            String routeId,
            String prabhagId,
            String resolutionMethod,
            Boolean citizenConfirmationRecorded,
            String boundaryDatasetVersion,
            String authority,
            String department,
            List<OfficialChannel> officialChannels,
            String sla,
            String escalation,
            OfficialSource officialSource,
            String sourceStatus,
            String reviewStatus,
            String packVersion) {
        static CivicRouteResponse unsupported(String prabhagId, String packVersion) {
            return new CivicRouteResponse(
                    "UNSUPPORTED_ROUTE", null, prabhagId, null, false, null, null, null, List.of(),
                    "NOT_VERIFIED", "NOT_VERIFIED", null, null, null, packVersion);
        }

        static CivicRouteResponse confirmationRequired(String prabhagId, String packVersion) {
            return new CivicRouteResponse(
                    "CONFIRMATION_REQUIRED", null, prabhagId, PrabhagResolverService.RESOLUTION_METHOD,
                    false, PrabhagResolverService.DATASET_VERSION, null, null, List.of(),
                    "NOT_VERIFIED", "NOT_VERIFIED", null, null, null, packVersion);
        }
    }

    public record CivicPack(
            String packVersion,
            String authority,
            List<OfficialChannel> officialChannels,
            List<RouteDefinition> routes) {}

    public record RouteDefinition(
            String routeId,
            String issueType,
            String department,
            String scopeNote,
            OfficialSource officialSource,
            String sourceStatus,
            String reviewStatus) {}

    public record OfficialChannel(String type, String value, String label) {}

    public record OfficialSource(String title, String url, String reference) {}
}
