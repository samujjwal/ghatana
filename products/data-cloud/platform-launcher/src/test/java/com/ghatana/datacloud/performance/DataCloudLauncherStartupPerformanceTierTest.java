package com.ghatana.datacloud.performance;

import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Cold-start and warm-start performance tier for Data Cloud launcher entry points.
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Data Cloud Launcher Startup Performance Tier [GH-90000]")
class DataCloudLauncherStartupPerformanceTierTest extends EventloopTestBase {

    @Test
    @DisplayName("cold start plus first write stays within startup baseline")
    void shouldMeetColdStartBaseline() {
        Instant started = Instant.now();

        try (DataCloudClient client = DataCloud.forTesting()) {
            runPromise(() -> client.save("tenant-perf", "startup", Map.of("id", "cold-1", "value", "ok")));
        }

        long elapsedMillis = Duration.between(started, Instant.now()).toMillis();
        assertThat(elapsedMillis).isLessThan(2_000);
    }

    @Test
    @DisplayName("warm start loop stays within baseline for repeated boots")
    void shouldMeetWarmStartBaseline() {
        int iterations = 20;

        try (DataCloudClient warmupClient = DataCloud.forTesting()) {
            // Warm JVM/service loader and close immediately.
        }

        Instant started = Instant.now();
        for (int i = 0; i < iterations; i++) {
            final int index = i;
            try (DataCloudClient client = DataCloud.forTesting()) {
                String id = "warm-" + index;
                runPromise(() -> client.save("tenant-perf", "startup", Map.of("id", id, "value", index)));
            }
        }

        long elapsedMillis = Duration.between(started, Instant.now()).toMillis();
        long averageMillis = elapsedMillis / iterations;

        assertThat(averageMillis).isLessThan(250);
    }
}


