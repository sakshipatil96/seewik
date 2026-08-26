package com.seewik.api;

public interface LifecycleAnalyticsPublisher {
    void publishPending(String outboxId);
}
