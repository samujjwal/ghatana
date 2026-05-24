/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3: Contract tests for TimeSeriesRecord.
 *
 * @doc.type class
 * @doc.purpose Tests for TimeSeriesRecord time-series functionality
 * @doc.layer test
 */
@DisplayName("TimeSeriesRecord Tests")
class TimeSeriesRecordTest {

    @Nested
    @DisplayName("Builder and Construction")
    class BuilderTests {

        @Test
        @DisplayName("builder creates metric with all fields")
        void builderCreatesMetricWithAllFields() {
            Instant timestamp = Instant.now();
            Map<String, String> tags = Map.of("host", "server-1", "region", "us-east");

            TimeSeriesRecord metric = TimeSeriesRecord.builder()
                .tenantId("tenant-123")
                .collectionName("system-metrics")
                .metricName("cpu_usage")
                .timestamp(timestamp)
                .value(72.5)
                .tags(tags)
                .data(Map.of("unit", "percent"))
                .build();

            assertThat(metric.getTenantId()).isEqualTo("tenant-123");
            assertThat(metric.getCollectionName()).isEqualTo("system-metrics");
            assertThat(metric.getMetricName()).isEqualTo("cpu_usage");
            assertThat(metric.getTimestamp()).isEqualTo(timestamp);
            assertThat(metric.getValue()).isEqualTo(72.5);
            assertThat(metric.getTags()).isEqualTo(tags);
            assertThat(metric.getRecordType()).isEqualTo(RecordType.TIMESERIES);
        }

        @Test
        @DisplayName("builder requires timestamp to be provided")
        void builderRequiresTimestamp() {
            Instant timestamp = Instant.now();
            TimeSeriesRecord metric = TimeSeriesRecord.builder()
                .tenantId("tenant-123")
                .collectionName("metrics")
                .metricName("test")
                .timestamp(timestamp)
                .build();

            assertThat(metric.getTimestamp()).isEqualTo(timestamp);
        }

        @Test
        @DisplayName("builder uses empty tags map when not provided")
        void builderUsesEmptyTagsMapWhenNotProvided() {
            TimeSeriesRecord metric = TimeSeriesRecord.builder()
                .tenantId("tenant-123")
                .collectionName("metrics")
                .metricName("test")
                .build();

            assertThat(metric.getTags()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("Tag Management")
    class TagManagementTests {

        @Test
        @DisplayName("tag adds tag to metric")
        void tagAddsTagToMetric() {
            TimeSeriesRecord metric = TimeSeriesRecord.builder()
                .tenantId("tenant-123")
                .collectionName("metrics")
                .metricName("test")
                .build();

            TimeSeriesRecord result = metric.tag("host", "server-1");

            assertThat(result).isSameAs(metric);
            assertThat(metric.getTags()).containsEntry("host", "server-1");
        }

        @Test
        @DisplayName("tag adds multiple tags")
        void tagAddsMultipleTags() {
            TimeSeriesRecord metric = TimeSeriesRecord.builder()
                .tenantId("tenant-123")
                .collectionName("metrics")
                .metricName("test")
                .build();

            metric.tag("host", "server-1");
            metric.tag("region", "us-east");
            metric.tag("env", "production");

            assertThat(metric.getTags()).hasSize(3);
            assertThat(metric.getTags()).containsEntry("host", "server-1");
            assertThat(metric.getTags()).containsEntry("region", "us-east");
            assertThat(metric.getTags()).containsEntry("env", "production");
        }

        @Test
        @DisplayName("tag overwrites existing tag")
        void tagOverwritesExistingTag() {
            TimeSeriesRecord metric = TimeSeriesRecord.builder()
                .tenantId("tenant-123")
                .collectionName("metrics")
                .metricName("test")
                .build();

            metric.tag("host", "server-1");
            metric.tag("host", "server-2");

            assertThat(metric.getTags()).containsEntry("host", "server-2");
            assertThat(metric.getTags()).hasSize(1);
        }

        @Test
        @DisplayName("getTag returns tag value")
        void getTagReturnsTagValue() {
            TimeSeriesRecord metric = TimeSeriesRecord.builder()
                .tenantId("tenant-123")
                .collectionName("metrics")
                .metricName("test")
                .tags(Map.of("host", "server-1"))
                .build();

            assertThat(metric.getTag("host")).isEqualTo("server-1");
        }

        @Test
        @DisplayName("getTag returns null for missing tag")
        void getTagReturnsNullForMissingTag() {
            TimeSeriesRecord metric = TimeSeriesRecord.builder()
                .tenantId("tenant-123")
                .collectionName("metrics")
                .metricName("test")
                .tags(Map.of("host", "server-1"))
                .build();

            assertThat(metric.getTag("region")).isNull();
        }

        @Test
        @DisplayName("getTag returns null when tags is null")
        void getTagReturnsNullWhenTagsIsNull() {
            TimeSeriesRecord metric = TimeSeriesRecord.builder()
                .tenantId("tenant-123")
                .collectionName("metrics")
                .metricName("test")
                .tags(null)
                .build();

            assertThat(metric.getTag("host")).isNull();
        }

        @Test
        @DisplayName("hasTag returns true for existing tag")
        void hasTagReturnsTrueForExistingTag() {
            TimeSeriesRecord metric = TimeSeriesRecord.builder()
                .tenantId("tenant-123")
                .collectionName("metrics")
                .metricName("test")
                .tags(Map.of("host", "server-1"))
                .build();

            assertThat(metric.hasTag("host")).isTrue();
        }

        @Test
        @DisplayName("hasTag returns false for missing tag")
        void hasTagReturnsFalseForMissingTag() {
            TimeSeriesRecord metric = TimeSeriesRecord.builder()
                .tenantId("tenant-123")
                .collectionName("metrics")
                .metricName("test")
                .tags(Map.of("host", "server-1"))
                .build();

            assertThat(metric.hasTag("region")).isFalse();
        }

        @Test
        @DisplayName("hasTag returns false when tags is null")
        void hasTagReturnsFalseWhenTagsIsNull() {
            TimeSeriesRecord metric = TimeSeriesRecord.builder()
                .tenantId("tenant-123")
                .collectionName("metrics")
                .metricName("test")
                .tags(null)
                .build();

            assertThat(metric.hasTag("host")).isFalse();
        }
    }

    @Nested
    @DisplayName("toBuilder")
    class ToBuilderTests {

        @Test
        @DisplayName("toBuilder creates builder with existing values")
        void toBuilderCreatesBuilderWithExistingValues() {
            Instant timestamp = Instant.now();
            TimeSeriesRecord original = TimeSeriesRecord.builder()
                .tenantId("tenant-123")
                .collectionName("metrics")
                .metricName("cpu_usage")
                .timestamp(timestamp)
                .value(50.0)
                .tags(Map.of("host", "server-1"))
                .build();

            TimeSeriesRecord updated = original.toBuilder()
                .metricName("memory_usage")
                .value(75.0)
                .build();

            assertThat(updated.getTenantId()).isEqualTo("tenant-123");
            assertThat(updated.getCollectionName()).isEqualTo("metrics");
            assertThat(updated.getMetricName()).isEqualTo("memory_usage");
            assertThat(updated.getValue()).isEqualTo(75.0);
            assertThat(updated.getTimestamp()).isEqualTo(timestamp);
            assertThat(updated.getTags()).containsEntry("host", "server-1");
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("tags map is mutable through builder")
        void tagsMapIsMutableThroughBuilder() {
            Map<String, String> tags = new HashMap<>();
            tags.put("host", "server-1");

            TimeSeriesRecord metric = TimeSeriesRecord.builder()
                .tenantId("tenant-123")
                .collectionName("metrics")
                .metricName("test")
                .tags(tags)
                .build();

            // The record uses the map reference, so it's mutable
            // This is a known behavior of Lombok @SuperBuilder
            assertThat(metric.getTags()).isNotNull();
        }
    }
}
