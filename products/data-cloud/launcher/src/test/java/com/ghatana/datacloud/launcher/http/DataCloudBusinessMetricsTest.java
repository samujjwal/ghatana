/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.platform.observability.MetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DataCloudBusinessMetrics}.
 *
 * @doc.type class
 * @doc.purpose Tests for DataCloudBusinessMetrics KPI emission
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudBusinessMetrics")
class DataCloudBusinessMetricsTest {

    @Nested
    @DisplayName("noop()")
    class NoopTests {

        @Test
        void noopReturnsSingletonInstance() {
            DataCloudBusinessMetrics noop1 = DataCloudBusinessMetrics.noop();
            DataCloudBusinessMetrics noop2 = DataCloudBusinessMetrics.noop();
            assertThat(noop1).isSameAs(noop2);
        }

        @Test
        void noopDoesNotThrowOnRecordOperations() {
            DataCloudBusinessMetrics noop = DataCloudBusinessMetrics.noop();
            
            // Should not throw even though metrics collector is null
            noop.recordEntityOperation("create", "users", "tenant-1", "success", 100);
            noop.recordEventAppend("tenant-1", "success", 50, "entity-created");
            noop.recordGovernanceOperation("retention-classify", "tenant-1", "success", 75);
        }
    }

    @Nested
    @DisplayName("recordEntityOperation")
    class EntityOperationTests {

        @Test
        void recordsEntityOperationWithValidMetricsCollector() {
            MeterRegistry registry = new SimpleMeterRegistry();
            MetricsCollector collector = mock(MetricsCollector.class);
            when(collector.getMeterRegistry()).thenReturn(registry);
            
            DataCloudBusinessMetrics metrics = new DataCloudBusinessMetrics(collector);
            metrics.recordEntityOperation("create", "users", "tenant-1", "success", 100);
            
            assertThat(registry.get(DataCloudBusinessMetrics.METRIC_ENTITY_TOTAL).counter().count()).isEqualTo(1.0);
        }

        @Test
        void normalizesNullValuesToUnknown() {
            MeterRegistry registry = new SimpleMeterRegistry();
            MetricsCollector collector = mock(MetricsCollector.class);
            when(collector.getMeterRegistry()).thenReturn(registry);
            
            DataCloudBusinessMetrics metrics = new DataCloudBusinessMetrics(collector);
            metrics.recordEntityOperation(null, null, null, null, 100);
            
            assertThat(registry.get(DataCloudBusinessMetrics.METRIC_ENTITY_TOTAL).counter().count()).isEqualTo(1.0);
        }

        @Test
        void handlesNegativeDuration() {
            MeterRegistry registry = new SimpleMeterRegistry();
            MetricsCollector collector = mock(MetricsCollector.class);
            when(collector.getMeterRegistry()).thenReturn(registry);
            
            DataCloudBusinessMetrics metrics = new DataCloudBusinessMetrics(collector);
            metrics.recordEntityOperation("create", "users", "tenant-1", "success", -10);
            
            // Should use max(durationMs, 0) = 0
            assertThat(registry.get(DataCloudBusinessMetrics.METRIC_ENTITY_TOTAL).counter().count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("recordEventAppend")
    class EventAppendTests {

        @Test
        void recordsEventAppendWithValidMetricsCollector() {
            MeterRegistry registry = new SimpleMeterRegistry();
            MetricsCollector collector = mock(MetricsCollector.class);
            when(collector.getMeterRegistry()).thenReturn(registry);
            
            DataCloudBusinessMetrics metrics = new DataCloudBusinessMetrics(collector);
            metrics.recordEventAppend("tenant-1", "success", 50, "entity-created");
            
            assertThat(registry.get(DataCloudBusinessMetrics.METRIC_EVENT_APPEND_TOTAL).counter().count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("recordGovernanceOperation")
    class GovernanceOperationTests {

        @Test
        void recordsGovernanceOperationWithValidMetricsCollector() {
            MeterRegistry registry = new SimpleMeterRegistry();
            MetricsCollector collector = mock(MetricsCollector.class);
            when(collector.getMeterRegistry()).thenReturn(registry);
            
            DataCloudBusinessMetrics metrics = new DataCloudBusinessMetrics(collector);
            metrics.recordGovernanceOperation("retention-classify", "tenant-1", "success", 75);
            
            assertThat(registry.get(DataCloudBusinessMetrics.METRIC_GOVERNANCE_TOTAL).counter().count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("normalizeTenant")
    class NormalizeTenantTests {

        @Test
        void handlesLongTenantId() {
            String longTenant = "a".repeat(100);
            
            // Test through recordEntityOperation since normalizeTenant is private
            MeterRegistry registry = new SimpleMeterRegistry();
            MetricsCollector collector = mock(MetricsCollector.class);
            when(collector.getMeterRegistry()).thenReturn(registry);
            
            DataCloudBusinessMetrics metrics = new DataCloudBusinessMetrics(collector);
            metrics.recordEntityOperation("create", "users", longTenant, "success", 100);
            
            assertThat(registry.get(DataCloudBusinessMetrics.METRIC_ENTITY_TOTAL).counter().count()).isEqualTo(1.0);
        }
    }
}
