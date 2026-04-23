/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.config;

import com.ghatana.aep.config.EnvConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AepDynamicConfigService}.
 *
 * @doc.type class
 * @doc.purpose Tests for dynamic config overlay, validation, and listener notification
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepDynamicConfigService")
class AepDynamicConfigServiceTest {

    AepDynamicConfigService service;

    @BeforeEach
    void setUp() { // GH-90000
        EnvConfig base = EnvConfig.fromMap(Map.of( // GH-90000
                EnvConfig.KAFKA_BOOTSTRAP_SERVERS, "kafka1:9092",
                EnvConfig.REDIS_PORT, "6379"
        ));
        service = new AepDynamicConfigService(base, new SimpleMeterRegistry()); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Constructor Validation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should reject null baseConfig")
    void rejectsNullBaseConfig() { // GH-90000
        assertThatThrownBy(() -> new AepDynamicConfigService(null, new SimpleMeterRegistry())) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("should reject null MeterRegistry")
    void rejectsNullRegistry() { // GH-90000
        assertThatThrownBy(() -> new AepDynamicConfigService( // GH-90000
                EnvConfig.fromMap(Map.of()), null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Read — Resolution Order
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Resolution Order")
    class ResolutionOrder {

        @Test
        @DisplayName("should return base-config value when no overlay is set")
        void returnsBaseValue() { // GH-90000
            assertThat(service.get(EnvConfig.KAFKA_BOOTSTRAP_SERVERS, "default")) // GH-90000
                    .isEqualTo("kafka1:9092");
        }

        @Test
        @DisplayName("should return default when key absent in all sources")
        void returnsDefaultWhenAbsent() { // GH-90000
            assertThat(service.get("NONEXISTENT_KEY", "my-default")) // GH-90000
                    .isEqualTo("my-default");
        }

        @Test
        @DisplayName("overlay should override base-config value")
        void overlayTakesPrecedenceOverBase() { // GH-90000
            service.set(EnvConfig.KAFKA_BOOTSTRAP_SERVERS, "broker2:9092"); // GH-90000
            assertThat(service.get(EnvConfig.KAFKA_BOOTSTRAP_SERVERS, "default")) // GH-90000
                    .isEqualTo("broker2:9092");
        }

        @Test
        @DisplayName("overlay should override default when base is also missing")
        void overlayTakesPrecedenceOverDefault() { // GH-90000
            service.set("MY_KEY", "my-value"); // GH-90000
            assertThat(service.get("MY_KEY", "default")).isEqualTo("my-value");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  getInt
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getInt")
    class GetInt {

        @Test
        @DisplayName("should return integer from base config")
        void returnsBaseInt() { // GH-90000
            assertThat(service.getInt(EnvConfig.REDIS_PORT, 0)).isEqualTo(6379); // GH-90000
        }

        @Test
        @DisplayName("should return integer from overlay")
        void returnsOverlayInt() { // GH-90000
            service.set(EnvConfig.REDIS_PORT, "9999"); // GH-90000
            assertThat(service.getInt(EnvConfig.REDIS_PORT, 0)).isEqualTo(9999); // GH-90000
        }

        @Test
        @DisplayName("should return default when key is absent in all sources")
        void returnsDefaultIntWhenAbsent() { // GH-90000
            assertThat(service.getInt("NONEXISTENT_INT_KEY", 42)).isEqualTo(42); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  set / setAll Validation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("set Validation")
    class SetValidation {

        @Test
        @DisplayName("should reject null key")
        void rejectsNullKey() { // GH-90000
            assertThatThrownBy(() -> service.set(null, "value")) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should reject blank key")
        void rejectsBlankKey() { // GH-90000
            assertThatThrownBy(() -> service.set("   ", "value")) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("should reject blank value")
        void rejectsBlankValue() { // GH-90000
            assertThatThrownBy(() -> service.set("SOME_KEY", "   ")) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("should reject non-integer value for known integer key")
        void rejectsNonIntValueForIntKey() { // GH-90000
            assertThatThrownBy(() -> service.set(EnvConfig.REDIS_PORT, "bad")) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("expects an integer");
        }

        @Test
        @DisplayName("should accept integer string for known integer key")
        void acceptsIntValueForIntKey() { // GH-90000
            assertThatCode(() -> service.set(EnvConfig.REDIS_PORT, "1234")) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("should reject Kafka bootstrap servers without host-port pairs")
        void rejectsInvalidKafkaBootstrapServers() { // GH-90000
            assertThatThrownBy(() -> service.set(EnvConfig.KAFKA_BOOTSTRAP_SERVERS, "broker-without-port")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("invalid broker address");
        }

        @Test
        @DisplayName("should reject Redis port outside valid range")
        void rejectsOutOfRangeRedisPort() { // GH-90000
            assertThatThrownBy(() -> service.set(EnvConfig.REDIS_PORT, "70000")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("must be between 1 and 65535");
        }

        @Test
        @DisplayName("should reject consolidation interval lower than one hour")
        void rejectsInvalidConsolidationInterval() { // GH-90000
            assertThatThrownBy(() -> service.set(EnvConfig.AEP_CONSOLIDATION_INTERVAL_HOURS, "0")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("must be between 1 and");
        }

        @Test
        @DisplayName("setAll should validate all entries before applying any")
        void setAllValidatesBeforeApply() { // GH-90000
            Map<String, String> overrides = Map.of( // GH-90000
                    "GOOD_KEY", "good-value",
                    EnvConfig.REDIS_PORT, "not-int"  // invalid
            );
            assertThatThrownBy(() -> service.setAll(overrides)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
            // GOOD_KEY must NOT have been written (fail-fast) // GH-90000
            assertThat(service.get("GOOD_KEY", "missing")).isEqualTo("missing");
        }

        @Test
        @DisplayName("setAll should apply all valid entries")
        void setAllAppliesAllValid() { // GH-90000
            service.setAll(Map.of( // GH-90000
                    "FEATURE_A", "enabled",
                    "FEATURE_B", "disabled"
            ));
            assertThat(service.get("FEATURE_A", "missing")).isEqualTo("enabled");
            assertThat(service.get("FEATURE_B", "missing")).isEqualTo("disabled");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  clear / clearAll
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("clear")
    class ClearTests {

        @Test
        @DisplayName("clear should revert to base-config value")
        void revertsToBaaseAfterClear() { // GH-90000
            service.set(EnvConfig.KAFKA_BOOTSTRAP_SERVERS, "override-broker:9092"); // GH-90000
            service.clear(EnvConfig.KAFKA_BOOTSTRAP_SERVERS); // GH-90000
            assertThat(service.get(EnvConfig.KAFKA_BOOTSTRAP_SERVERS, "default")) // GH-90000
                    .isEqualTo("kafka1:9092");
        }

        @Test
        @DisplayName("clear returns removed value")
        void returnsRemovedValue() { // GH-90000
            service.set("MY_KEY", "my-value"); // GH-90000
            String removed = service.clear("MY_KEY");
            assertThat(removed).isEqualTo("my-value");
        }

        @Test
        @DisplayName("clear returns null when key was never set")
        void returnsNullWhenNotSet() { // GH-90000
            assertThat(service.clear("NONEXISTENT_KEY")).isNull();
        }

        @Test
        @DisplayName("clearAll should remove all overlays")
        void clearAllRemovesAllOverlays() { // GH-90000
            service.set("KEY_A", "value-a"); // GH-90000
            service.set("KEY_B", "value-b"); // GH-90000
            service.clearAll(); // GH-90000
            assertThat(service.overlaySnapshot()).isEmpty(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Change Listeners
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Change Listeners")
    class ChangeListeners {

        @Test
        @DisplayName("should notify listener on set")
        void notifiesOnSet() { // GH-90000
            List<String> captured = new ArrayList<>(); // GH-90000
            service.addChangeListener((key, oldVal, newVal) -> // GH-90000
                    captured.add(key + ":" + oldVal + "->" + newVal)); // GH-90000

            service.set(EnvConfig.KAFKA_BOOTSTRAP_SERVERS, "new-broker:9092"); // GH-90000

            assertThat(captured).hasSize(1); // GH-90000
            assertThat(captured.get(0)).contains("KAFKA_BOOTSTRAP_SERVERS");
            assertThat(captured.get(0)).contains("new-broker:9092");
        }

        @Test
        @DisplayName("should notify listener with old value from base config on first override")
        void notifiesWithBaaseOldValue() { // GH-90000
            List<AepDynamicConfigService.ConfigChange> changes = new ArrayList<>(); // GH-90000
            service.addChangeListener((key, oldVal, newVal) -> // GH-90000
                    changes.add(new AepDynamicConfigService.ConfigChange(key, oldVal, newVal, null))); // GH-90000

            service.set(EnvConfig.KAFKA_BOOTSTRAP_SERVERS, "new-broker:9092"); // GH-90000

            assertThat(changes.get(0).oldValue()).isEqualTo("kafka1:9092");
        }

        @Test
        @DisplayName("should notify listener on clear")
        void notifiesOnClear() { // GH-90000
            service.set("MY_KEY", "my-value"); // GH-90000
            List<String> captured = new ArrayList<>(); // GH-90000
            service.addChangeListener((key, oldVal, newVal) -> captured.add(key)); // GH-90000

            service.clear("MY_KEY");
            assertThat(captured).containsExactly("MY_KEY");
        }

        @Test
        @DisplayName("should support multiple listeners")
        void multipleListeners() { // GH-90000
            List<String> eventsA = new ArrayList<>(); // GH-90000
            List<String> eventsB = new ArrayList<>(); // GH-90000
            service.addChangeListener((k, o, n) -> eventsA.add(k)); // GH-90000
            service.addChangeListener((k, o, n) -> eventsB.add(k)); // GH-90000

            service.set("KEY", "value"); // GH-90000

            assertThat(eventsA).containsExactly("KEY");
            assertThat(eventsB).containsExactly("KEY");
        }

        @Test
        @DisplayName("should allow removing a listener")
        void removeListener() { // GH-90000
            List<String> events = new ArrayList<>(); // GH-90000
            AepDynamicConfigService.ChangeListener listener = (k, o, n) -> events.add(k); // GH-90000

            service.addChangeListener(listener); // GH-90000
            service.set("KEY_A", "v1"); // GH-90000

            service.removeChangeListener(listener); // GH-90000
            service.set("KEY_B", "v2"); // GH-90000

            // Only KEY_A was captured; KEY_B was set after removal
            assertThat(events).containsExactly("KEY_A");
        }

        @Test
        @DisplayName("should roll back override when a listener rejects the change")
        void listenerExceptionRollsBackChange() { // GH-90000
            service.addChangeListener((k, o, n) -> { throw new RuntimeException("bad listener"); });

            assertThatThrownBy(() -> service.set("KEY", "value")) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("Failed to apply config change");
            assertThat(service.overlaySnapshot()).doesNotContainKey("KEY");
        }

        @Test
        @DisplayName("setAll should roll back all overrides when a listener rejects the batch")
        void setAllRollsBackOnListenerFailure() { // GH-90000
            service.addChangeListener((k, o, n) -> { // GH-90000
                if ("FEATURE_B".equals(k)) { // GH-90000
                    throw new RuntimeException("reject batch");
                }
            });

            assertThatThrownBy(() -> service.setAll(Map.of( // GH-90000
                    "FEATURE_A", "enabled",
                    "FEATURE_B", "disabled")))
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("FEATURE_B");

            assertThat(service.overlaySnapshot()).isEmpty(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Change History
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Change History")
    class ChangeHistoryTests {

        @Test
        @DisplayName("should record changes in reverse chronological order")
        void recordsHistory() { // GH-90000
            service.set("KEY_A", "v1"); // GH-90000
            service.set("KEY_B", "v2"); // GH-90000

            List<AepDynamicConfigService.ConfigChange> history = service.changeHistory(); // GH-90000
            assertThat(history).hasSize(2); // GH-90000
            // Newest first
            assertThat(history.get(0).key()).isEqualTo("KEY_B");
            assertThat(history.get(1).key()).isEqualTo("KEY_A");
        }

        @Test
        @DisplayName("should include changedAt timestamp")
        void includesTimestamp() { // GH-90000
            service.set("KEY", "value"); // GH-90000
            assertThat(service.changeHistory().get(0).changedAt()).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Audit History")
    class AuditHistoryTests {

        @Test
        @DisplayName("records rejected writes in audit history")
        void recordsRejectedWrites() { // GH-90000
            assertThatThrownBy(() -> service.set(EnvConfig.KAFKA_BOOTSTRAP_SERVERS, "broker-without-port")) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000

            List<AepDynamicConfigService.ConfigAuditEntry> auditHistory = service.auditHistory(); // GH-90000
            assertThat(auditHistory).hasSize(1); // GH-90000
            assertThat(auditHistory.get(0).status()).isEqualTo(AepDynamicConfigService.AuditStatus.REJECTED); // GH-90000
            assertThat(auditHistory.get(0).key()).isEqualTo(EnvConfig.KAFKA_BOOTSTRAP_SERVERS); // GH-90000
        }

        @Test
        @DisplayName("records rolled back writes in audit history")
        void recordsRolledBackWrites() { // GH-90000
            service.addChangeListener((k, o, n) -> { throw new RuntimeException("listener failed"); });

            assertThatThrownBy(() -> service.set("FEATURE_FLAG", "enabled")) // GH-90000
                    .isInstanceOf(IllegalStateException.class); // GH-90000

            List<AepDynamicConfigService.ConfigAuditEntry> auditHistory = service.auditHistory(); // GH-90000
            assertThat(auditHistory).hasSize(1); // GH-90000
            assertThat(auditHistory.get(0).status()).isEqualTo(AepDynamicConfigService.AuditStatus.ROLLED_BACK); // GH-90000
            assertThat(auditHistory.get(0).key()).isEqualTo("FEATURE_FLAG");
            assertThat(auditHistory.get(0).detail()).contains("Failed to apply config change");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  isSet / overlaySnapshot
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isSet should return true for key present in base config")
    void isSetBaseConfig() { // GH-90000
        assertThat(service.isSet(EnvConfig.KAFKA_BOOTSTRAP_SERVERS)).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("isSet should return true for key present only in overlay")
    void isSetOverlay() { // GH-90000
        service.set("DYNAMIC_KEY", "value"); // GH-90000
        assertThat(service.isSet("DYNAMIC_KEY")).isTrue();
    }

    @Test
    @DisplayName("isSet should return false for completely unknown key")
    void isSetUnknownKey() { // GH-90000
        assertThat(service.isSet("TOTALLY_UNKNOWN")).isFalse();
    }

    @Test
    @DisplayName("overlaySnapshot should reflect current overlay state")
    void overlaySnapshot() { // GH-90000
        service.set("A", "1"); // GH-90000
        service.set("B", "2"); // GH-90000
        Map<String, String> snapshot = service.overlaySnapshot(); // GH-90000
        assertThat(snapshot).containsEntry("A", "1").containsEntry("B", "2"); // GH-90000
    }
}
