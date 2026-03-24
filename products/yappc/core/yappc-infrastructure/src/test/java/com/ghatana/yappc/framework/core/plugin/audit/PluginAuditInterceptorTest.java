/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Framework Module
 */
package com.ghatana.yappc.framework.core.plugin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PluginAuditInterceptor} (plan section 10.2.4).
 *
 * @doc.type class
 * @doc.purpose Tests that BEFORE/AFTER audit records are emitted for plugin method calls
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PluginAuditInterceptor Tests (10.2)")
class PluginAuditInterceptorTest {

    /** Minimal test plugin contract. */
    interface Generator {
        String generate(String input);
        void initialize(String config);
    }

    /** In-memory sink that collects audit records. */
    private final List<Map<String, Object>> auditRecords = new ArrayList<>();

    private Generator proxy;

    @BeforeEach
    void setUp() {
        Generator real = new Generator() {
            @Override
            public String generate(String input) {
                return "generated:" + input;
            }

            @Override
            public void initialize(String config) {
                // no-op
            }
        };

        proxy = PluginAuditInterceptor.wrap(
                real, Generator.class, "test-plugin-1", "intent-agent", auditRecords::add);
    }

    @Test
    @DisplayName("10.2.4.1 — call generate() → exactly 2 audit records (BEFORE, AFTER)")
    void generateProducesTwoAuditRecords() {
        proxy.generate("my-project");

        assertThat(auditRecords).hasSize(2);

        Map<String, Object> before = auditRecords.get(0);
        assertThat(before.get("phase")).isEqualTo("BEFORE");
        assertThat(before.get("action")).isEqualTo("GENERATE");
        assertThat(before.get("pluginId")).isEqualTo("test-plugin-1");
        assertThat(before.get("agentId")).isEqualTo("intent-agent");
        assertThat(before.get("timestamp")).isNotNull();

        Map<String, Object> after = auditRecords.get(1);
        assertThat(after.get("phase")).isEqualTo("AFTER");
        assertThat(after.get("action")).isEqualTo("GENERATE");
        assertThat(after.get("status")).isEqualTo("OK");
        assertThat(after.get("durationMs")).isNotNull();
        assertThat(after.get("outputHash")).isNotNull();
    }

    @Test
    @DisplayName("10.2.4.2 — call initialize() → INIT action in audit records")
    void initializeProducesInitAction() {
        proxy.initialize("conf");

        assertThat(auditRecords).hasSize(2);
        assertThat(auditRecords.get(0).get("action")).isEqualTo("INIT");
        assertThat(auditRecords.get(1).get("action")).isEqualTo("INIT");
    }

    @Test
    @DisplayName("10.2.4.3 — BEFORE record has inputHash field")
    void beforeRecordHasInputHash() {
        proxy.generate("project-spec");

        Map<String, Object> before = auditRecords.get(0);
        assertThat(before).containsKey("inputHash");
        // BEFORE record should NOT have durationMs or status
        assertThat(before).doesNotContainKey("durationMs");
        assertThat(before).doesNotContainKey("status");
    }

    @Test
    @DisplayName("10.2.4.4 — AFTER record has outputHash and durationMs")
    void afterRecordHasOutputHashAndDuration() {
        proxy.generate("test-input");

        Map<String, Object> after = auditRecords.get(1);
        assertThat(after).containsKey("outputHash");
        assertThat(after).containsKey("durationMs");
        assertThat(after).containsKey("status");
    }

    @Test
    @DisplayName("10.2.4.5 — exception from plugin causes ERROR phase record")
    void exceptionCausesErrorRecord() {
        Generator failingPlugin = PluginAuditInterceptor.wrap(
                new Generator() {
                    @Override
                    public String generate(String input) {
                        throw new RuntimeException("plugin-crash");
                    }

                    @Override
                    public void initialize(String config) {}
                },
                Generator.class, "crashing-plugin", "intent-agent", auditRecords::add);

        try {
            failingPlugin.generate("input");
        } catch (RuntimeException ignored) {
            // expected
        }

        assertThat(auditRecords).hasSize(2);
        assertThat(auditRecords.get(0).get("phase")).isEqualTo("BEFORE");
        assertThat(auditRecords.get(1).get("phase")).isEqualTo("ERROR");
        assertThat(auditRecords.get(1).get("status")).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("10.2.4.6 — multiple invocations accumulate separate audit pairs")
    void multipleInvocationsAccumulateAuditPairs() {
        proxy.generate("run-1");
        proxy.generate("run-2");

        assertThat(auditRecords).hasSize(4); // 2 per call
    }
}
