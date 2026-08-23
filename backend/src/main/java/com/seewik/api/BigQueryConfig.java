package com.seewik.api;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BigQueryConfig {
    @Bean
    BigQuery bigQuery(@Value("${GOOGLE_CLOUD_PROJECT:seewik}") String projectId) {
        return BigQueryOptions.newBuilder().setProjectId(projectId).build().getService();
    }
}
