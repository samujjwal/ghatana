/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.audio.video.common.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link MediaProcessingMetrics}.
 *
 * @doc.type class
 * @doc.purpose Regression tests for media processing metrics contract
 * @doc.layer product
 * @doc.pattern TestCase
 */
@DisplayName("MediaProcessingMetrics [GH-90000]")
class MediaProcessingMetricsTest {

    private MediaProcessingMetrics metrics;

    @BeforeEach
    void setUp() { // GH-90000
        metrics = MediaProcessingMetrics.create(); // GH-90000
    }

    @Test
    @DisplayName("recordStarted increments started counter for operation [GH-90000]")
    void recordStarted_incrementsCounter() { // GH-90000
        metrics.recordStarted("vision.detect [GH-90000]");
        metrics.recordStarted("vision.detect [GH-90000]");

        assertThat(metrics.startedCount("vision.detect [GH-90000]")).isEqualTo(2);
    }

    @Test
    @DisplayName("recordSucceeded increments succeeded counter and accumulates latency [GH-90000]")
    void recordSucceeded_incrementsSucceededAndLatency() { // GH-90000
        metrics.recordSucceeded("vision.detect", 25L); // GH-90000
        metrics.recordSucceeded("vision.detect", 35L); // GH-90000

        assertThat(metrics.succeededCount("vision.detect [GH-90000]")).isEqualTo(2);
        assertThat(metrics.latencyMsTotal("vision.detect [GH-90000]")).isEqualTo(60L);
    }

    @Test
    @DisplayName("recordFailed increments failed counter without affecting succeeded [GH-90000]")
    void recordFailed_incrementsFailedOnly() { // GH-90000
        metrics.recordFailed("multimodal.analyse [GH-90000]");

        assertThat(metrics.failedCount("multimodal.analyse [GH-90000]")).isEqualTo(1);
        assertThat(metrics.succeededCount("multimodal.analyse [GH-90000]")).isEqualTo(0);
    }

    @Test
    @DisplayName("operations are tracked independently [GH-90000]")
    void operationsAreIsolated() { // GH-90000
        metrics.recordStarted("vision.detect [GH-90000]");
        metrics.recordStarted("vision.analyze [GH-90000]");
        metrics.recordSucceeded("vision.detect", 10L); // GH-90000

        assertThat(metrics.startedCount("vision.detect [GH-90000]")).isEqualTo(1);
        assertThat(metrics.startedCount("vision.analyze [GH-90000]")).isEqualTo(1);
        assertThat(metrics.succeededCount("vision.detect [GH-90000]")).isEqualTo(1);
        assertThat(metrics.succeededCount("vision.analyze [GH-90000]")).isEqualTo(0);
    }

    @Test
    @DisplayName("noop instance never increments any counter [GH-90000]")
    void noop_neverRecordsAnything() { // GH-90000
        MediaProcessingMetrics noop = MediaProcessingMetrics.noop(); // GH-90000

        noop.recordStarted("vision.detect [GH-90000]");
        noop.recordSucceeded("vision.detect", 100L); // GH-90000
        noop.recordFailed("vision.detect [GH-90000]");

        assertThat(noop.startedCount("vision.detect [GH-90000]")).isEqualTo(0);
        assertThat(noop.succeededCount("vision.detect [GH-90000]")).isEqualTo(0);
        assertThat(noop.failedCount("vision.detect [GH-90000]")).isEqualTo(0);
        assertThat(noop.latencyMsTotal("vision.detect [GH-90000]")).isEqualTo(0);
    }

    @Test
    @DisplayName("recordStarted with null operation does not throw [GH-90000]")
    void recordStarted_nullOperation_doesNotThrow() { // GH-90000
        assertThatCode(() -> metrics.recordStarted(null)).doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("recordSucceeded with null operation does not throw [GH-90000]")
    void recordSucceeded_nullOperation_doesNotThrow() { // GH-90000
        assertThatCode(() -> metrics.recordSucceeded(null, 50L)).doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("recordFailed with null operation does not throw [GH-90000]")
    void recordFailed_nullOperation_doesNotThrow() { // GH-90000
        assertThatCode(() -> metrics.recordFailed(null)).doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("scrape returns prometheus-format text for all recorded operations [GH-90000]")
    void scrape_containsAllOperations() { // GH-90000
        metrics.recordStarted("vision.detect [GH-90000]");
        metrics.recordSucceeded("vision.detect", 20L); // GH-90000
        metrics.recordFailed("multimodal.analyse [GH-90000]");

        String output = metrics.scrape(); // GH-90000

        assertThat(output).contains("media_processing_started_total [GH-90000]");
        assertThat(output).contains("vision.detect [GH-90000]");
        assertThat(output).contains("media_processing_failed_total [GH-90000]");
        assertThat(output).contains("multimodal.analyse [GH-90000]");
    }

    @Test
    @DisplayName("unrecorded operation returns zero for all accessors [GH-90000]")
    void unknownOperation_returnsZero() { // GH-90000
        assertThat(metrics.startedCount("unknown.op [GH-90000]")).isEqualTo(0);
        assertThat(metrics.succeededCount("unknown.op [GH-90000]")).isEqualTo(0);
        assertThat(metrics.failedCount("unknown.op [GH-90000]")).isEqualTo(0);
        assertThat(metrics.latencyMsTotal("unknown.op [GH-90000]")).isEqualTo(0);
    }
}
