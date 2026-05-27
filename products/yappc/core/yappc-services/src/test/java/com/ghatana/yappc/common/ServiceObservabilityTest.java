package com.ghatana.yappc.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
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

    @Test
    @DisplayName("redactSensitiveFields removes prompt, input, output, payload, and generated content values")
    void redactSensitiveFieldsRemovesSensitiveValues() {
        Map<String, Object> redacted = ServiceObservability.redactSensitiveFields(Map.of(
                "prompt", "create a private app for a named customer",
                "input", "raw user transcript",
                "output", "model output",
                "payload", Map.of("generatedContent", "source code", "safeCount", 2),
                "items", List.of(Map.of("apiKey", "sk-secret", "label", "kept")),
                "inputTokens", 42,
                "model", "gpt-test"));

        assertThat(redacted).containsEntry("prompt", ServiceObservability.REDACTED_VALUE);
        assertThat(redacted).containsEntry("input", ServiceObservability.REDACTED_VALUE);
        assertThat(redacted).containsEntry("output", ServiceObservability.REDACTED_VALUE);
        assertThat(redacted).containsEntry("payload", ServiceObservability.REDACTED_VALUE);
        assertThat(redacted).containsEntry("inputTokens", 42);
        assertThat(redacted).containsEntry("model", "gpt-test");
        assertThat(redacted.toString())
                .doesNotContain("private app", "raw user transcript", "model output", "source code", "sk-secret");
    }
}
