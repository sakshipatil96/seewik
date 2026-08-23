package com.seewik.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CivicRouterService {
    public static final String PACK_VERSION = "v0.1";
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
        String wardId = request == null || request.wardId() == null ? "" : request.wardId().trim();
        RouteDefinition route = routesByIssueType.get(issueType);
        if (route == null || wardId.isBlank()) {
            return CivicRouteResponse.unsupported(wardId.isBlank() ? null : wardId, PACK_VERSION);
        }
        return new CivicRouteResponse(
                "SUPPORTED_ROUTE",
                route.routeId(),
                wardId,
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

    public record CivicRouteRequest(String issueType, String wardId) {}

    public record CivicRouteResponse(
            String status,
            String routeId,
            String wardId,
            String authority,
            String department,
            List<OfficialChannel> officialChannels,
            String sla,
            String escalation,
            OfficialSource officialSource,
            String sourceStatus,
            String reviewStatus,
            String packVersion) {
        static CivicRouteResponse unsupported(String wardId, String packVersion) {
            return new CivicRouteResponse(
                    "UNSUPPORTED_ROUTE", null, wardId, null, null, List.of(),
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
