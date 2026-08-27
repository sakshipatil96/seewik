package com.seewik.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class OperationalMetrics {
    private static final Logger LOGGER = LoggerFactory.getLogger(OperationalMetrics.class);
    private static final int MAX_LATENCY_SAMPLES = 2_048;
    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Latencies> latencies = new ConcurrentHashMap<>();
    private final ObjectMapper json;
    private final String revision;

    @Autowired
    public OperationalMetrics(ObjectMapper json) {
        this(json, System.getenv().getOrDefault("K_REVISION", "local"));
    }

    OperationalMetrics(ObjectMapper json, String revision) {
        this.json = json;
        this.revision = revision == null || revision.isBlank() ? "local" : revision;
    }

    public void increment(String metric) {
        counters.computeIfAbsent(metric, ignored -> new LongAdder()).increment();
    }

    public void recordLatency(String metric, long milliseconds) {
        latencies.computeIfAbsent(metric, ignored -> new Latencies())
                .add(Math.max(0L, milliseconds));
    }

    Map<String, Object> snapshot() {
        Map<String, Long> counterSnapshot = new LinkedHashMap<>();
        counters.keySet().stream().sorted().forEach(name -> counterSnapshot.put(name, counters.get(name).sum()));
        Map<String, Object> latencySnapshot = new LinkedHashMap<>();
        latencies.keySet().stream().sorted().forEach(name -> latencySnapshot.put(name, latencies.get(name).summary()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("event", "operational_metrics_snapshot");
        result.put("revision", revision);
        result.put("recordedAt", Instant.now().toString());
        result.put("counters", counterSnapshot);
        result.put("latencyMs", latencySnapshot);
        return result;
    }

    @Scheduled(fixedRateString = "${seewik.metrics.log-interval-ms:60000}")
    void logSnapshot() {
        try {
            LOGGER.info(json.writeValueAsString(snapshot()));
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Operational metric snapshot serialization failed");
        }
    }

    private static final class Latencies {
        private final ArrayDeque<Long> values = new ArrayDeque<>();

        synchronized void add(long value) {
            if (values.size() == MAX_LATENCY_SAMPLES) values.removeFirst();
            values.addLast(value);
        }

        synchronized Map<String, Long> summary() {
            if (values.isEmpty()) return Map.of("count", 0L);
            List<Long> sorted = new ArrayList<>(values);
            Collections.sort(sorted);
            return Map.of(
                    "count", (long) sorted.size(),
                    "min", sorted.getFirst(),
                    "p50", percentile(sorted, 0.50),
                    "p95", percentile(sorted, 0.95),
                    "max", sorted.getLast());
        }

        private static long percentile(List<Long> sorted, double percentile) {
            int index = (int) Math.ceil(percentile * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
        }
    }
}
