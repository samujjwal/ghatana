package com.ghatana.digitalmarketing.application.feature;

import com.ghatana.digitalmarketing.application.DmosFeatureFlags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1-046: Tests for DmosFeatureFlags constant integrity.
 *
 * @doc.type class
 * @doc.purpose Tests for feature flag constant integrity
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("P1-046: Feature Flag Constant Integrity Tests")
class FeatureFlagServiceTest {

    @Test
    @DisplayName("P1-046: DmosFeatureFlags has a private constructor")
    void shouldHavePrivateConstructor() throws Exception {
        Constructor<DmosFeatureFlags> ctor = DmosFeatureFlags.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(ctor.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("P1-046: All flag keys use canonical dmos. prefix")
    void allFlagKeysShouldUseCanonicalPrefix() {
        List<Field> constants = getPublicStaticStringFields();
        assertThat(constants).isNotEmpty();
        for (Field field : constants) {
            try {
                String value = (String) field.get(null);
                assertThat(value)
                    .as("Flag constant '%s' must start with 'dmos.'", field.getName())
                    .startsWith("dmos.");
            } catch (IllegalAccessException e) {
                throw new AssertionError("Cannot read constant " + field.getName(), e);
            }
        }
    }

    @Test
    @DisplayName("P1-046: Kill-switch flag key is defined")
    void shouldDefineKillSwitchFlag() {
        assertThat(DmosFeatureFlags.KILL_SWITCH_ENABLED).isEqualTo("dmos.kill_switch.enabled");
    }

    @Test
    @DisplayName("P1-046: Rollback workflow flag key is defined")
    void shouldDefineRollbackWorkflowFlag() {
        assertThat(DmosFeatureFlags.ROLLBACK_WORKFLOW_ENABLED).isEqualTo("dmos.rollback_workflow.enabled");
    }

    @Test
    @DisplayName("P1-046: Rollback enabled flag key is defined")
    void shouldDefineRollbackEnabledFlag() {
        assertThat(DmosFeatureFlags.ROLLBACK_ENABLED).isEqualTo("dmos.rollback.enabled");
    }

    @Test
    @DisplayName("P1-046: AI enabled flag key is defined")
    void shouldDefineAiEnabledFlag() {
        assertThat(DmosFeatureFlags.AI_ENABLED).isEqualTo("dmos.ai.enabled");
    }

    @Test
    @DisplayName("P1-046: Google Ads connector flag key is defined")
    void shouldDefineGoogleAdsConnectorFlag() {
        assertThat(DmosFeatureFlags.GOOGLE_ADS_CONNECTOR_ENABLED).isEqualTo("dmos.google_ads_connector.enabled");
    }

    @Test
    @DisplayName("P1-046: All flag constants are non-null and non-empty")
    void allFlagConstantsShouldBeNonNullAndNonEmpty() {
        for (Field field : getPublicStaticStringFields()) {
            try {
                String value = (String) field.get(null);
                assertThat(value)
                    .as("Flag constant '%s' must not be null or empty", field.getName())
                    .isNotNull()
                    .isNotEmpty();
            } catch (IllegalAccessException e) {
                throw new AssertionError("Cannot read constant " + field.getName(), e);
            }
        }
    }

    @Test
    @DisplayName("P1-046: No duplicate flag key values")
    void shouldHaveNoDuplicateFlagKeys() {
        List<String> values = getPublicStaticStringFields().stream()
            .map(field -> {
                try {
                    return (String) field.get(null);
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            })
            .toList();
        assertThat(values).doesNotHaveDuplicates();
    }

    private List<Field> getPublicStaticStringFields() {
        return Arrays.stream(DmosFeatureFlags.class.getDeclaredFields())
            .filter(f -> Modifier.isStatic(f.getModifiers())
                && Modifier.isFinal(f.getModifiers())
                && Modifier.isPublic(f.getModifiers())
                && f.getType() == String.class)
            .toList();
    }
}
