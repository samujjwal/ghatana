package com.ghatana.platform.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PluginCompatibility")
class PluginCompatibilityTest {

    @Test
    @DisplayName("accepts decorated platform version strings")
    void shouldAcceptDecoratedPlatformVersionStrings() {
        PluginCompatibility compatibility = new PluginCompatibility("1.0.0", "2.0.0");

        assertThat(compatibility.isCompatibleWith("1.5.0"))
            .isTrue();
    }

    @Test
    @DisplayName("accepts prefixed and suffixed semantic versions")
    void shouldAcceptPrefixedAndSuffixedSemanticVersions() {
        PluginCompatibility compatibility = new PluginCompatibility("1.2.0", "1.2.9");

        assertThat(compatibility.isCompatibleWith("v1.2.3-beta"))
            .isTrue();
    }

    @Test
    @DisplayName("treats non-numeric versions as incompatible instead of throwing")
    void shouldTreatNonNumericVersionsAsIncompatibleInsteadOfThrowing() {
        PluginCompatibility compatibility = PluginCompatibility.atLeast("1.0.0");

        assertThat(compatibility.isCompatibleWith("unknown"))
            .isFalse();
    }
}

