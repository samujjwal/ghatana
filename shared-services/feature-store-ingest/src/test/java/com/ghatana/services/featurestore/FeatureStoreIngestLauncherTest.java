package com.ghatana.services.featurestore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verify feature-store-ingest health payload behavior
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("FeatureStoreIngestLauncher Tests")
class FeatureStoreIngestLauncherTest {

    @Test
    @DisplayName("healthPayloadJson should expose service metadata for probes")
    void healthPayloadJsonShouldExposeServiceMetadata() {
        Instant timestamp = Instant.parse("2026-03-26T12:00:00Z");

        String json = FeatureStoreIngestLauncher.healthPayloadJson(timestamp);

        assertThat(json).contains("\"status\":\"UP\"");
        assertThat(json).contains("\"service\":\"feature-store-ingest\"");
        assertThat(json).contains("\"timestamp\":\"2026-03-26T12:00:00Z\"");
        assertThat(json).contains("\"version\":\"1.0.0\"");
    }
}