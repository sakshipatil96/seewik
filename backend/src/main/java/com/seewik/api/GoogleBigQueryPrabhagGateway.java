package com.seewik.api;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.TableResult;
import java.util.Iterator;
import java.util.Optional;
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
              datasetVersion
            FROM `seewik.seewik_civic.prabhag_boundaries`
            WHERE isActive = TRUE
              AND ST_COVERS(geometry, ST_GEOGPOINT(@longitude, @latitude))
            ORDER BY prabhagId
            LIMIT 1
            """;

    private final BigQuery bigQuery;

    public GoogleBigQueryPrabhagGateway(BigQuery bigQuery) {
        this.bigQuery = bigQuery;
    }

    @Override
    public Optional<BoundaryMatch> findCoveringBoundary(double latitude, double longitude)
            throws InterruptedException {
        QueryJobConfiguration query = QueryJobConfiguration.newBuilder(LOOKUP_SQL)
                .setUseLegacySql(false)
                .setUseQueryCache(false)
                .addNamedParameter("latitude", QueryParameterValue.float64(latitude))
                .addNamedParameter("longitude", QueryParameterValue.float64(longitude))
                .build();
        TableResult result = bigQuery.query(query);
        Iterator<FieldValueList> rows = result.iterateAll().iterator();
        if (!rows.hasNext()) {
            return Optional.empty();
        }
        FieldValueList row = rows.next();
        return Optional.of(new BoundaryMatch(
                row.get("prabhagId").getStringValue(),
                row.get("prabhagName").getStringValue(),
                row.get("resolutionQuality").getStringValue(),
                row.get("requiresCitizenConfirmation").getBooleanValue(),
                row.get("sourceReference").getStringValue(),
                row.get("sourceStatus").getStringValue(),
                row.get("reviewStatus").getStringValue(),
                row.get("datasetVersion").getStringValue()));
    }
}
