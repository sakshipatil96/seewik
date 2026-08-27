package com.seewik.api;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class ModelCallExecutor {
    public static final Duration DEFAULT_CLASSIFICATION_TIMEOUT = Duration.ofSeconds(15);
    public static final Duration DEFAULT_DRAFTING_TIMEOUT = Duration.ofSeconds(20);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Duration classificationTimeout;
    private final Duration draftingTimeout;

    @Autowired
    public ModelCallExecutor(
            @Value("${seewik.model.classification-timeout-ms:15000}") long classificationTimeoutMs,
            @Value("${seewik.model.drafting-timeout-ms:20000}") long draftingTimeoutMs) {
        this(Duration.ofMillis(classificationTimeoutMs), Duration.ofMillis(draftingTimeoutMs));
    }

    ModelCallExecutor(Duration classificationTimeout, Duration draftingTimeout) {
        this.classificationTimeout = requirePositive(classificationTimeout);
        this.draftingTimeout = requirePositive(draftingTimeout);
    }

    public <T> T classification(Callable<T> call) throws Exception {
        return execute(call, classificationTimeout);
    }

    public <T> T drafting(Callable<T> call) throws Exception {
        return execute(call, draftingTimeout);
    }

    public Duration classificationTimeout() {
        return classificationTimeout;
    }

    public Duration draftingTimeout() {
        return draftingTimeout;
    }

    private <T> T execute(Callable<T> call, Duration timeout) throws Exception {
        Future<T> future = executor.submit(call);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new ModelTimeoutException(exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw exception;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) throw checked;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Model call failed", cause);
        }
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }

    private static Duration requirePositive(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("Model timeout must be positive");
        }
        return value;
    }

    public static final class ModelTimeoutException extends Exception {
        ModelTimeoutException(Throwable cause) {
            super("The model call exceeded its deadline", cause);
        }
    }
}
