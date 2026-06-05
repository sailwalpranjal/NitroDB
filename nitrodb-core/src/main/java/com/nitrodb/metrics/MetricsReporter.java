package com.nitrodb.metrics;

public final class MetricsReporter {

    private final NitroDBMetrics metrics;
    private final MetricsSink sink;
    private volatile boolean running;
    private Thread thread;

    public MetricsReporter(NitroDBMetrics metrics) {
        this(metrics, new LoggingMetricsSink());
    }

    public MetricsReporter(NitroDBMetrics metrics, MetricsSink sink) {
        this.metrics = metrics;
        this.sink = sink;
    }

    public void start() {
        if (!running) {
            running = true;
            thread = Thread.ofVirtual().name("nitrodb-metrics-reporter").start(() -> {
                while (running) {
                    try {
                        sink.publish(metrics.snapshot());
                    } catch (RuntimeException e) {
                        // Observability failures must not kill the reporter thread.
                    }
                    try {
                        Thread.sleep(10_000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            });
        }
    }

    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }
}
