package com.ghatana.datacloud;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DataCloud event envelope validation")
class DataCloudEventEnvelopeValidationTest extends EventloopTestBase {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("non-local profiles reject source-less events")
    void nonLocalRejectsSourceLessEvents() {
        DataCloud.DataCloudConfig config = DataCloud.DataCloudConfig.builder()
            .profile(DataCloud.DataCloudConfig.DataCloudProfile.SOVEREIGN)
            .customConfig(Map.of("sovereign.dataDir", tempDir.resolve("strict").toString()))
            .build();

        DataCloudClient client = DataCloud.create(config);
        try {
            DataCloudClient.Event event = DataCloudClient.Event.of("entity.saved", Map.of("id", "1"));

            assertThatThrownBy(() -> runPromise(() -> client.appendEvent("tenant-1", event)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("event.source is required");
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("local profile allows legacy source-less events")
    void localAllowsSourceLessEvents() {
        DataCloudClient client = DataCloud.forTesting();
        try {
            DataCloudClient.Event event = DataCloudClient.Event.of("entity.saved", Map.of("id", "1"));
            runPromise(() -> client.appendEvent("tenant-1", event));
        } finally {
            client.close();
        }
    }
}
