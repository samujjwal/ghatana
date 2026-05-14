package com.ghatana.yappc.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ServiceObservability}.
 */
@DisplayName("ServiceObservability")
class ServiceObservabilityTest {

    @Test
    @DisplayName("flowTags returns all nine canonical tag keys")
    void flowTagsContainsAllNineKeys() {
        Map<String, String> tags = ServiceObservability.flowTags(
                "tenant-1", "ws-1", "proj-1",
                "BUILD", "generate", "SUCCESS",
                false, null, "corr-xyz");

        assertThat(tags).containsOnlyKeys(
                "tenantId", "workspaceId", "projectId",
                "phase", "operation", "outcome",
                "degraded", "errorClass", "correlationId");
    }

    @Test
    @DisplayName("flowTags populates known values correctly")
    void flowTagsPopulatesKnownValues() {
        Map<String, String> tags = ServiceObservability.flowTags(
                "t1", "ws1", "p1", "DESIGN", "build", "SUCCESS", false, null, "c1");

        assertThat(tags.get("tenantId")).isEqualTo("t1");
        assertThat(tags.get("workspaceId")).isEqualTo("ws1");
        assertThat(tags.get("projectId")).isEqualTo("p1");
        assertThat(tags.get("phase")).isEqualTo("DESIGN");
        assertThat(tags.get("operation")).isEqualTo("build");
        assertThat(tags.get("outcome")).isEqualTo("SUCCESS");
        assertThat(tags.get("degraded")).isEqualTo("false");
        assertThat(tags.get("errorClass")).isEqualTo("none");
        assertThat(tags.get("correlationId")).isEqualTo("c1");
    }

    @Test
    @DisplayName("flowTags normalises null values to sentinels")
    void flowTagsNormalisesNulls() {
        Map<String, String> tags = ServiceObservability.flowTags(
                null, null, null, null, null, null, true, null, null);

        assertThat(tags.get("tenantId")).isEqualTo("unknown");
        assertThat(tags.get("workspaceId")).isEqualTo("unknown");
        assertThat(tags.get("projectId")).isEqualTo("unknown");
        assertThat(tags.get("phase")).isEqualTo("unknown");
        assertThat(tags.get("operation")).isEqualTo("unknown");
        assertThat(tags.get("outcome")).isEqualTo("unknown");
        assertThat(tags.get("degraded")).isEqualTo("true");
        assertThat(tags.get("errorClass")).isEqualTo("none");
        assertThat(tags.get("correlationId")).isEqualTo("none");
    }

    @Test
    @DisplayName("flowTags result is unmodifiable")
    void flowTagsResultIsUnmodifiable() {
        Map<String, String> tags = ServiceObservability.flowTags(
                "t", "w", "p", "ph", "op", "OK", false, null, null);

        assertThatThrownBy(() -> tags.put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("flowTags with degraded=true sets tag to 'true'")
    void flowTagsDegradedTrue() {
        Map<String, String> tags = ServiceObservability.flowTags(
                "t", "w", "p", "ph", "op", "ERROR", true, "IOException", "c1");

        assertThat(tags.get("degraded")).isEqualTo("true");
        assertThat(tags.get("errorClass")).isEqualTo("IOException");
    }
}
