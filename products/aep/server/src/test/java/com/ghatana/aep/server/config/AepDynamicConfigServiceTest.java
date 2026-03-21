/*
 * Copyright (c) 2026 Ghatana Inc.
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
    void setUp() {
        EnvConfig base = EnvConfig.fromMap(Map.of(
                EnvConfig.KAFKA_BOOTSTRAP_SERVERS, "kafka1:9092",
                EnvConfig.REDIS_PORT, "6379"
        ));
        service = new AepDynamicConfigService(base, new SimpleMeterRegistry());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Constructor Validation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should reject null baseConfig")
    void rejectsNullBaseConfig() {
        assertThatThrownBy(() -> new AepDynamicConfigService(null, new SimpleMeterRegistry()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should reject null MeterRegistry")
    void rejectsNullRegistry() {
        assertThatThrownBy(() -> new AepDynamicConfigService(
                EnvConfig.fromMap(Map.of()), null))
                .isInstanceOf(NullPointerException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Read — Resolution Order
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Resolution Order")
    class ResolutionOrder {

        @Test
        @DisplayName("should return base-config value when no overlay is set")
        void returnsBaseValue() {
            assertThat(service.get(EnvConfig.KAFKA_BOOTSTRAP_SERVERS, "default"))
                    .isEqualTo("kafka1:9092");
        }

        @Test
        @DisplayName("should return default when key absent in all sources")
        void returnsDefaultWhenAbsent() {
            assertThat(service.get("NONEXISTENT_KEY", "my-default"))
                    .isEqualTo("my-default");
        }

        @Test
        @DisplayName("overlay should override base-config value")
        void overlayTakesPrecedenceOverBase() {
            service.set(EnvConfig.KAFKA_BOOTSTRAP_SERVERS, "broker2:9092");
            assertThat(service.get(EnvConfig.KAFKA_BOOTSTRAP_SERVERS, "default"))
                    .isEqualTo("broker2:9092");
        }

        @Test
        @DisplayName("overlay should override default when base is also missing")
        void overlayTakesPrecedenceOverDefault() {
            service.set("MY_KEY", "my-value");
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
        void returnsBaseInt() {
            assertThat(service.getInt(EnvConfig.REDIS_PORT, 0)).isEqualTo(6379);
        }

        @Test
        @DisplayName("should return integer from overlay")
        void returnsOverlayInt() {
            service.set(EnvConfig.REDIS_PORT, "9999");
            assertThat(service.getInt(EnvConfig.REDIS_PORT, 0)).isEqualTo(9999);
        }

        @Test
        @DisplayName("should return default when key is absent in all sources")
        void returnsDefaultIntWhenAbsent() {
            assertThat(service.getInt("NONEXISTENT_INT_KEY", 42)).isEqualTo(42);
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
        void rejectsNullKey() {
            assertThatThrownBy(() -> service.set(null, "value"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject blank key")
        void rejectsBlankKey() {
            assertThatThrownBy(() -> service.set("   ", "value"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject blank value")
        void rejectsBlankValue() {
            assertThatThrownBy(() -> service.set("SOME_KEY", "   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject non-integer value for known integer key")
        void rejectsNonIntValueForIntKey() {
            assertThatThrownBy(() -> service.set(EnvConfig.REDIS_PORT, "bad"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expects an integer");
        }

        @Test
        @DisplayName("should accept integer string for known integer key")
        void acceptsIntValueForIntKey() {
            assertThatCode(() -> service.set(EnvConfig.REDIS_PORT, "1234"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("setAll should validate all entries before applying any")
        void setAllValidatesBeforeApply() {
            Map<String, String> overrides = Map.of(
                    "GOOD_KEY", "good-value",
                    EnvConfig.REDIS_PORT, "not-int"  // invalid
            );
            assertThatThrownBy(() -> service.setAll(overrides))
                    .isInstanceOf(IllegalArgumentException.class);
            // GOOD_KEY must NOT have been written (fail-fast)
            assertThat(service.get("GOOD_KEY", "missing")).isEqualTo("missing");
        }

        @Test
        @DisplayName("setAll should apply all valid entries")
        void setAllAppliesAllValid() {
            service.setAll(Map.of(
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
        void revertsToBaaseAfterClear() {
            service.set(EnvConfig.KAFKA_BOOTSTRAP_SERVERS, "override-broker");
            service.clear(EnvConfig.KAFKA_BOOTSTRAP_SERVERS);
            assertThat(service.get(EnvConfig.KAFKA_BOOTSTRAP_SERVERS, "default"))
                    .isEqualTo("kafka1:9092");
        }

        @Test
        @DisplayName("clear returns removed value")
        void returnsRemovedValue() {
            service.set("MY_KEY", "my-value");
            String removed = service.clear("MY_KEY");
            assertThat(removed).isEqualTo("my-value");
        }

        @Test
        @DisplayName("clear returns null when key was never set")
        void returnsNullWhenNotSet() {
            assertThat(service.clear("NONEXISTENT_KEY")).isNull();
        }

        @Test
        @DisplayName("clearAll should remove all overlays")
        void clearAllRemovesAllOverlays() {
            service.set("KEY_A", "value-a");
            service.set("KEY_B", "value-b");
            service.clearAll();
            assertThat(service.overlaySnapshot()).isEmpty();
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
        void notifiesOnSet() {
            List<String> captured = new ArrayList<>();
            service.addChangeListener((key, oldVal, newVal) ->
                    captured.add(key + ":" + oldVal + "->" + newVal));

            service.set(EnvConfig.KAFKA_BOOTSTRAP_SERVERS, "new-broker");

            assertThat(captured).hasSize(1);
            assertThat(captured.get(0)).contains("KAFKA_BOOTSTRAP_SERVERS");
            assertThat(captured.get(0)).contains("new-broker");
        }

        @Test
        @DisplayName("should notify listener with old value from base config on first override")
        void notifiesWithBaaseOldValue() {
            List<AepDynamicConfigService.ConfigChange> changes = new ArrayList<>();
            service.addChangeListener((key, oldVal, newVal) ->
                    changes.add(new AepDynamicConfigService.ConfigChange(key, oldVal, newVal, null)));

            service.set(EnvConfig.KAFKA_BOOTSTRAP_SERVERS, "new-broker");

            assertThat(changes.get(0).oldValue()).isEqualTo("kafka1:9092");
        }

        @Test
        @DisplayName("should notify listener on clear")
        void notifiesOnClear() {
            service.set("MY_KEY", "my-value");
            List<String> captured = new ArrayList<>();
            service.addChangeListener((key, oldVal, newVal) -> captured.add(key));

            service.clear("MY_KEY");
            assertThat(captured).containsExactly("MY_KEY");
        }

        @Test
        @DisplayName("should support multiple listeners")
        void multipleListeners() {
            List<String> eventsA = new ArrayList<>();
            List<String> eventsB = new ArrayList<>();
            service.addChangeListener((k, o, n) -> eventsA.add(k));
            service.addChangeListener((k, o, n) -> eventsB.add(k));

            service.set("KEY", "value");

            assertThat(eventsA).containsExactly("KEY");
            assertThat(eventsB).containsExactly("KEY");
        }

        @Test
        @DisplayName("should allow removing a listener")
        void removeListener() {
            List<String> events = new ArrayList<>();
            AepDynamicConfigService.ChangeListener listener = (k, o, n) -> events.add(k);

            service.addChangeListener(listener);
            service.set("KEY_A", "v1");

            service.removeChangeListener(listener);
            service.set("KEY_B", "v2");

            // Only KEY_A was captured; KEY_B was set after removal
            assertThat(events).containsExactly("KEY_A");
        }

        @Test
        @DisplayName("should not propagate exceptions from a misbehaving listener")
        void listenerExceptionDoesNotAbortOthers() {
            List<String> capturedBySecond = new ArrayList<>();
            service.addChangeListener((k, o, n) -> { throw new RuntimeException("bad listener"); });
            service.addChangeListener((k, o, n) -> capturedBySecond.add(k));

            // Must not throw
            assertThatCode(() -> service.set("KEY", "value")).doesNotThrowAnyException();
            // Second listener still ran
            assertThat(capturedBySecond).containsExactly("KEY");
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
        void recordsHistory() {
            service.set("KEY_A", "v1");
            service.set("KEY_B", "v2");

            List<AepDynamicConfigService.ConfigChange> history = service.changeHistory();
            assertThat(history).hasSize(2);
            // Newest first
            assertThat(history.get(0).key()).isEqualTo("KEY_B");
            assertThat(history.get(1).key()).isEqualTo("KEY_A");
        }

        @Test
        @DisplayName("should include changedAt timestamp")
        void includesTimestamp() {
            service.set("KEY", "value");
            assertThat(service.changeHistory().get(0).changedAt()).isNotNull();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  isSet / overlaySnapshot
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isSet should return true for key present in base config")
    void isSetBaseConfig() {
        assertThat(service.isSet(EnvConfig.KAFKA_BOOTSTRAP_SERVERS)).isTrue();
    }

    @Test
    @DisplayName("isSet should return true for key present only in overlay")
    void isSetOverlay() {
        service.set("DYNAMIC_KEY", "value");
        assertThat(service.isSet("DYNAMIC_KEY")).isTrue();
    }

    @Test
    @DisplayName("isSet should return false for completely unknown key")
    void isSetUnknownKey() {
        assertThat(service.isSet("TOTALLY_UNKNOWN")).isFalse();
    }

    @Test
    @DisplayName("overlaySnapshot should reflect current overlay state")
    void overlaySnapshot() {
        service.set("A", "1");
        service.set("B", "2");
        Map<String, String> snapshot = service.overlaySnapshot();
        assertThat(snapshot).containsEntry("A", "1").containsEntry("B", "2");
    }
}
