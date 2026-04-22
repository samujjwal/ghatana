/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
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
 * Tests for {@link PluginAuditInterceptor} (plan section 10.2.4). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Tests that BEFORE/AFTER audit records are emitted for plugin method calls
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PluginAuditInterceptor Tests (10.2) [GH-90000]")
class PluginAuditInterceptorTest {

    /** Minimal test plugin contract. */
    interface Generator {
        String generate(String input); // GH-90000
        void initialize(String config); // GH-90000
    }

    /** In-memory sink that collects audit records. */
    private final List<Map<String, Object>> auditRecords = new ArrayList<>(); // GH-90000

    private Generator proxy;

    @BeforeEach
    void setUp() { // GH-90000
        Generator real = new Generator() { // GH-90000
            @Override
            public String generate(String input) { // GH-90000
                return "generated:" + input;
            }

            @Override
            public void initialize(String config) { // GH-90000
                // no-op
            }
        };

        proxy = PluginAuditInterceptor.wrap( // GH-90000
                real, Generator.class, "test-plugin-1", "intent-agent", auditRecords::add);
    }

    @Test
    @DisplayName("10.2.4.1 — call generate() → exactly 2 audit records (BEFORE, AFTER) [GH-90000]")
    void generateProducesTwoAuditRecords() { // GH-90000
        proxy.generate("my-project [GH-90000]");

        assertThat(auditRecords).hasSize(2); // GH-90000

        Map<String, Object> before = auditRecords.get(0); // GH-90000
        assertThat(before.get("phase [GH-90000]")).isEqualTo("BEFORE [GH-90000]");
        assertThat(before.get("action [GH-90000]")).isEqualTo("GENERATE [GH-90000]");
        assertThat(before.get("pluginId [GH-90000]")).isEqualTo("test-plugin-1 [GH-90000]");
        assertThat(before.get("agentId [GH-90000]")).isEqualTo("intent-agent [GH-90000]");
        assertThat(before.get("timestamp [GH-90000]")).isNotNull();

        Map<String, Object> after = auditRecords.get(1); // GH-90000
        assertThat(after.get("phase [GH-90000]")).isEqualTo("AFTER [GH-90000]");
        assertThat(after.get("action [GH-90000]")).isEqualTo("GENERATE [GH-90000]");
        assertThat(after.get("status [GH-90000]")).isEqualTo("OK [GH-90000]");
        assertThat(after.get("durationMs [GH-90000]")).isNotNull();
        assertThat(after.get("outputHash [GH-90000]")).isNotNull();
    }

    @Test
    @DisplayName("10.2.4.2 — call initialize() → INIT action in audit records [GH-90000]")
    void initializeProducesInitAction() { // GH-90000
        proxy.initialize("conf [GH-90000]");

        assertThat(auditRecords).hasSize(2); // GH-90000
        assertThat(auditRecords.get(0).get("action [GH-90000]")).isEqualTo("INIT [GH-90000]");
        assertThat(auditRecords.get(1).get("action [GH-90000]")).isEqualTo("INIT [GH-90000]");
    }

    @Test
    @DisplayName("10.2.4.3 — BEFORE record has inputHash field [GH-90000]")
    void beforeRecordHasInputHash() { // GH-90000
        proxy.generate("project-spec [GH-90000]");

        Map<String, Object> before = auditRecords.get(0); // GH-90000
        assertThat(before).containsKey("inputHash [GH-90000]");
        // BEFORE record should NOT have durationMs or status
        assertThat(before).doesNotContainKey("durationMs [GH-90000]");
        assertThat(before).doesNotContainKey("status [GH-90000]");
    }

    @Test
    @DisplayName("10.2.4.4 — AFTER record has outputHash and durationMs [GH-90000]")
    void afterRecordHasOutputHashAndDuration() { // GH-90000
        proxy.generate("test-input [GH-90000]");

        Map<String, Object> after = auditRecords.get(1); // GH-90000
        assertThat(after).containsKey("outputHash [GH-90000]");
        assertThat(after).containsKey("durationMs [GH-90000]");
        assertThat(after).containsKey("status [GH-90000]");
    }

    @Test
    @DisplayName("10.2.4.5 — exception from plugin causes ERROR phase record [GH-90000]")
    void exceptionCausesErrorRecord() { // GH-90000
        Generator failingPlugin = PluginAuditInterceptor.wrap( // GH-90000
                new Generator() { // GH-90000
                    @Override
                    public String generate(String input) { // GH-90000
                        throw new RuntimeException("plugin-crash [GH-90000]");
                    }

                    @Override
                    public void initialize(String config) {} // GH-90000
                },
                Generator.class, "crashing-plugin", "intent-agent", auditRecords::add);

        try {
            failingPlugin.generate("input [GH-90000]");
        } catch (RuntimeException ignored) { // GH-90000
            // expected
        }

        assertThat(auditRecords).hasSize(2); // GH-90000
        assertThat(auditRecords.get(0).get("phase [GH-90000]")).isEqualTo("BEFORE [GH-90000]");
        assertThat(auditRecords.get(1).get("phase [GH-90000]")).isEqualTo("ERROR [GH-90000]");
        assertThat(auditRecords.get(1).get("status [GH-90000]")).isEqualTo("ERROR [GH-90000]");
    }

    @Test
    @DisplayName("10.2.4.6 — multiple invocations accumulate separate audit pairs [GH-90000]")
    void multipleInvocationsAccumulateAuditPairs() { // GH-90000
        proxy.generate("run-1 [GH-90000]");
        proxy.generate("run-2 [GH-90000]");

        assertThat(auditRecords).hasSize(4); // 2 per call // GH-90000
    }
}
