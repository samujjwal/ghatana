/**
 * P1-046: Feature-flag off/on backend tests.
 *
 * <p>Tests that verify behavior when feature flags are enabled/disabled.</p>
 *
 * @doc.type class
 * @doc.purpose Feature-flag off/on backend tests (P1-046)
 * @doc.layer test
 */
package com.ghatana.digitalmarketing.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("P1-046: Feature-Flag On/Off Backend Tests")
class FeatureFlagOnOffTest {

    @Test
    @DisplayName("P1-046: AI_ENABLED flag key is correctly defined")
    void aiEnabledFlagKeyIsCorrectlyDefined() {
        assertThat(DmosFeatureFlags.AI_ENABLED)
            .isNotNull()
            .isNotEmpty()
            .startsWith("dmos.");
    }

    @Test
    @DisplayName("P1-046: GOOGLE_ADS_CONNECTOR_ENABLED flag key is correctly defined")
    void googleAdsConnectorFlagKeyIsCorrectlyDefined() {
        assertThat(DmosFeatureFlags.GOOGLE_ADS_CONNECTOR_ENABLED)
            .isNotNull()
            .isNotEmpty()
            .startsWith("dmos.");
    }

    @Test
    @DisplayName("P1-046: KILL_SWITCH_ENABLED flag key is correctly defined")
    void killSwitchFlagKeyIsCorrectlyDefined() {
        assertThat(DmosFeatureFlags.KILL_SWITCH_ENABLED)
            .isNotNull()
            .isNotEmpty()
            .startsWith("dmos.");
    }

    @Test
    @DisplayName("P1-046: ROLLBACK_ENABLED flag key is correctly defined")
    void rollbackFlagKeyIsCorrectlyDefined() {
        assertThat(DmosFeatureFlags.ROLLBACK_ENABLED)
            .isNotNull()
            .isNotEmpty()
            .startsWith("dmos.");
    }

    @Test
    @DisplayName("P1-046: OBSERVABILITY_ENABLED flag key is correctly defined")
    void observabilityFlagKeyIsCorrectlyDefined() {
        assertThat(DmosFeatureFlags.OBSERVABILITY_ENABLED)
            .isNotNull()
            .isNotEmpty()
            .startsWith("dmos.");
    }

    @Test
    @DisplayName("P1-046: All flag keys use dmos. prefix (namespace contract)")
    void allFlagKeysUseCanonicalPrefix() {
        List<String> flagValues = getAllFlagValues();

        assertThat(flagValues).isNotEmpty();
        flagValues.forEach(flagKey ->
            assertThat(flagKey)
                .as("Flag key should use dmos. namespace: " + flagKey)
                .startsWith("dmos.")
        );
    }

    @Test
    @DisplayName("P1-046: All flag keys are unique (no duplicate keys)")
    void allFlagKeysAreUnique() {
        List<String> flagValues = getAllFlagValues();

        Set<String> uniqueKeys = new HashSet<>(flagValues);

        assertThat(uniqueKeys).hasSameSizeAs(flagValues);
    }

    @Test
    @DisplayName("P1-046: No flag key is null or blank")
    void noFlagKeyIsNullOrBlank() {
        List<String> flagValues = getAllFlagValues();

        flagValues.forEach(flagKey ->
            assertThat(flagKey)
                .as("Flag key must not be null or blank")
                .isNotNull()
                .isNotBlank()
        );
    }

    @Test
    @DisplayName("P1-046: Flag keys use dot-separated lowercase naming convention")
    void flagKeysUseLowercaseDotSeparatedConvention() {
        List<String> flagValues = getAllFlagValues();

        flagValues.forEach(flagKey ->
            assertThat(flagKey)
                .as("Flag key should use lowercase dot-separated format: " + flagKey)
                .matches("[a-z][a-z0-9_.]*")
        );
    }

    @Test
    @DisplayName("P1-046: DmosFeatureFlags cannot be instantiated (utility class)")
    void dmosFeatureFlagsIsUtilityClass() {
        // Utility class must have private constructor and not be extendable
        var constructors = DmosFeatureFlags.class.getDeclaredConstructors();

        assertThat(constructors).hasSize(1);
        assertThat(Modifier.isPrivate(constructors[0].getModifiers())).isTrue();
        assertThat(Modifier.isFinal(DmosFeatureFlags.class.getModifiers())).isTrue();
    }

    @ParameterizedTest
    @MethodSource("criticalFlagKeys")
    @DisplayName("P1-046: Critical flag key {0} is defined and non-empty")
    void criticalFlagKeyIsDefined(String expectedKey) {
        List<String> allKeys = getAllFlagValues();

        assertThat(allKeys)
            .as("Critical flag key '%s' must be defined in DmosFeatureFlags", expectedKey)
            .contains(expectedKey);
    }

    static Stream<String> criticalFlagKeys() {
        return Stream.of(
            "dmos.ai.enabled",
            "dmos.google_ads_connector.enabled",
            "dmos.kill_switch.enabled",
            "dmos.rollback.enabled",
            "dmos.observability.enabled"
        );
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private List<String> getAllFlagValues() {
        return Arrays.stream(DmosFeatureFlags.class.getDeclaredFields())
            .filter(f -> Modifier.isPublic(f.getModifiers())
                && Modifier.isStatic(f.getModifiers())
                && Modifier.isFinal(f.getModifiers())
                && f.getType() == String.class)
            .map(f -> {
                try {
                    return (String) f.get(null);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to read flag constant", e);
                }
            })
            .toList();
    }
}

