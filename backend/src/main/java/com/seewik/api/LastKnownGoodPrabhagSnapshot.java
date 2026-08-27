package com.seewik.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public final class LastKnownGoodPrabhagSnapshot {
    public static final String RESOURCE = "/prabhag-snapshot-synthetic-v0.1.geojson";
    public static final String DATASET_VERSION = "synthetic-v0.1";
    public static final String CHECKSUM = "059533c8988334e7a268482c83bac9693e74783081c5b3a8cb51061bda4e100a";
    public static final String PROVENANCE = "Packaged last-known-good copy of fixed-seed synthetic-boundaries-v0.1";
    private final List<PolygonBoundary> boundaries;

    public LastKnownGoodPrabhagSnapshot(ObjectMapper json) {
        try (InputStream input = LastKnownGoodPrabhagSnapshot.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IOException("Missing packaged prabhag snapshot");
            byte[] bytes = input.readAllBytes();
            if (!CHECKSUM.equals(sha256(bytes))) throw new IOException("Prabhag snapshot checksum mismatch");
            JsonNode root = json.readTree(bytes);
            if (!"FeatureCollection".equals(root.path("type").asText()) || root.path("features").size() != 20) {
                throw new IOException("Prabhag snapshot must contain exactly 20 polygon features");
            }
            List<PolygonBoundary> loaded = new ArrayList<>();
            for (JsonNode feature : root.path("features")) loaded.add(parse(feature));
            this.boundaries = List.copyOf(loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("The packaged prabhag snapshot is invalid", exception);
        }
    }

    public Optional<PrabhagBoundaryGateway.BoundaryMatch> findCoveringBoundary(double latitude, double longitude) {
        for (PolygonBoundary boundary : boundaries) {
            if (covers(boundary.ring(), longitude, latitude)) return Optional.of(boundary.match());
        }
        return Optional.empty();
    }

    int boundaryCount() {
        return boundaries.size();
    }

    private static PolygonBoundary parse(JsonNode feature) throws IOException {
        JsonNode properties = feature.path("properties");
        JsonNode ringNode = feature.at("/geometry/coordinates/0");
        if (!"Polygon".equals(feature.at("/geometry/type").asText()) || !ringNode.isArray() || ringNode.size() < 4) {
            throw new IOException("Snapshot feature has invalid polygon geometry");
        }
        List<Point> ring = new ArrayList<>();
        for (JsonNode coordinate : ringNode) {
            if (!coordinate.isArray() || coordinate.size() < 2
                    || !coordinate.get(0).isNumber() || !coordinate.get(1).isNumber()) {
                throw new IOException("Snapshot polygon has invalid coordinate");
            }
            ring.add(new Point(coordinate.get(0).asDouble(), coordinate.get(1).asDouble()));
        }
        PrabhagBoundaryGateway.BoundaryMatch match = new PrabhagBoundaryGateway.BoundaryMatch(
                required(properties, "prabhagId"),
                required(properties, "prabhagName"),
                required(properties, "resolutionQuality"),
                properties.path("requiresCitizenConfirmation").asBoolean(false),
                required(properties, "sourceReference"),
                required(properties, "sourceStatus"),
                required(properties, "reviewStatus"),
                required(properties, "datasetVersion"));
        GoogleBigQueryPrabhagGateway.validate(match);
        return new PolygonBoundary(match, List.copyOf(ring));
    }

    private static String required(JsonNode properties, String field) throws IOException {
        String value = properties.path(field).asText("").strip();
        if (value.isEmpty()) throw new IOException("Snapshot property is missing: " + field);
        return value;
    }

    private static boolean covers(List<Point> ring, double x, double y) {
        boolean inside = false;
        for (int current = 0, previous = ring.size() - 1; current < ring.size(); previous = current++) {
            Point a = ring.get(previous);
            Point b = ring.get(current);
            if (onSegment(a, b, x, y)) return true;
            boolean crosses = (a.y() > y) != (b.y() > y)
                    && x < (b.x() - a.x()) * (y - a.y()) / (b.y() - a.y()) + a.x();
            if (crosses) inside = !inside;
        }
        return inside;
    }

    private static boolean onSegment(Point a, Point b, double x, double y) {
        double cross = (x - a.x()) * (b.y() - a.y()) - (y - a.y()) * (b.x() - a.x());
        if (Math.abs(cross) > 1e-10) return false;
        return x >= Math.min(a.x(), b.x()) - 1e-10 && x <= Math.max(a.x(), b.x()) + 1e-10
                && y >= Math.min(a.y(), b.y()) - 1e-10 && y <= Math.max(a.y(), b.y()) + 1e-10;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Point(double x, double y) {}
    private record PolygonBoundary(PrabhagBoundaryGateway.BoundaryMatch match, List<Point> ring) {}
}
