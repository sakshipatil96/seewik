package com.seewik.api;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.TableResult;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GoogleBigQueryPrabhagGateway implements PrabhagBoundaryGateway {
    static final String LOOKUP_SQL = """
            SELECT
              prabhagId,
              prabhagName,
              resolutionQuality,
              requiresCitizenConfirmation,
              sourceReference,
              sourceStatus,
              reviewStatus,
              datasetVersion,
              COUNT(*) OVER() AS coveringMatchCount
            FROM `seewik.seewik_civic.prabhag_boundaries`
            WHERE isActive = TRUE
              AND ST_COVERS(geometry, ST_GEOGPOINT(@longitude, @latitude))
            ORDER BY prabhagId
            LIMIT 1
            """;

    private final BigQuery bigQuery;
    private final long timeoutMs;
    private final OperationalMetrics metrics;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private static final Set<String> PRABHAG_IDS = IntStream.rangeClosed(1, 20)
            .mapToObj(number -> "PRABHAG-%02d".formatted(number)).collect(Collectors.toUnmodifiableSet());

    public GoogleBigQueryPrabhagGateway(
            BigQuery bigQuery,
            @Value("${seewik.bigquery.timeout-ms:1500}") long timeoutMs,
            OperationalMetrics metrics) {
        if (timeoutMs < 1L) throw new IllegalArgumentException("BigQuery timeout must be positive");
        this.bigQuery = bigQuery;
        this.timeoutMs = timeoutMs;
        this.metrics = metrics;
    }

    @Override
    public Optional<BoundaryMatch> findCoveringBoundary(double latitude, double longitude)
            throws InterruptedException {
        QueryJobConfiguration query = QueryJobConfiguration.newBuilder(LOOKUP_SQL)
                .setUseLegacySql(false)
                .setUseQueryCache(false)
                .setJobTimeoutMs(timeoutMs)
                .addNamedParameter("latitude", QueryParameterValue.float64(latitude))
                .addNamedParameter("longitude", QueryParameterValue.float64(longitude))
                .build();
        Future<TableResult> future = executor.submit(() -> bigQuery.query(query));
        TableResult result;
        try {
            result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new BoundaryTimeoutException(Duration.ofMillis(timeoutMs), exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof InterruptedException interrupted) throw interrupted;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new BoundaryUnavailableException(cause);
        }
        if (result == null) throw new InvalidBoundaryResponseException("BigQuery returned no result object");
        Iterator<FieldValueList> rows = result.iterateAll().iterator();
        if (!rows.hasNext()) {
            return Optional.empty();
        }
        FieldValueList row = rows.next();
        try {
            BoundaryMatch match = new BoundaryMatch(
                    row.get("prabhagId").getStringValue(),
                    row.get("prabhagName").getStringValue(),
                    row.get("resolutionQuality").getStringValue(),
                    row.get("requiresCitizenConfirmation").getBooleanValue(),
                    row.get("sourceReference").getStringValue(),
                    row.get("sourceStatus").getStringValue(),
                    row.get("reviewStatus").getStringValue(),
                    row.get("datasetVersion").getStringValue());
            validate(match);
            if (row.get("coveringMatchCount").getLongValue() > 1L) {
                metrics.increment("prabhag.bigquery_multi_match");
            }
            return Optional.of(match);
        } catch (RuntimeException exception) {
            if (exception instanceof InvalidBoundaryResponseException invalid) throw invalid;
            throw new InvalidBoundaryResponseException("BigQuery returned an incomplete boundary row", exception);
        }
    }

    static void validate(BoundaryMatch match) {
        if (match == null
                || !PRABHAG_IDS.contains(match.prabhagId())
                || match.prabhagName() == null || match.prabhagName().isBlank()
                || !PrabhagResolverService.RESOLUTION_QUALITY.equals(match.resolutionQuality())
                || !match.requiresCitizenConfirmation()
                || match.sourceReference() == null || match.sourceReference().isBlank()
                || !PrabhagResolverService.SOURCE_STATUS.equals(match.sourceStatus())
                || !PrabhagResolverService.REVIEW_STATUS.equals(match.reviewStatus())
                || !PrabhagResolverService.DATASET_VERSION.equals(match.datasetVersion())) {
            throw new InvalidBoundaryResponseException("BigQuery returned an invalid boundary row");
        }
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }

    public static final class BoundaryTimeoutException extends RuntimeException {
        BoundaryTimeoutException(Duration timeout, Throwable cause) {
            super("BigQuery boundary lookup exceeded " + timeout.toMillis() + " ms", cause);
        }
    }

    public static final class InvalidBoundaryResponseException extends RuntimeException {
        InvalidBoundaryResponseException(String message) { super(message); }
        InvalidBoundaryResponseException(String message, Throwable cause) { super(message, cause); }
    }

    public static final class BoundaryUnavailableException extends RuntimeException {
        BoundaryUnavailableException(Throwable cause) { super("BigQuery boundary lookup failed", cause); }
    }
}
