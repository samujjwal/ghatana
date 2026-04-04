package com.ghatana.datacloud.config;

import com.ghatana.datacloud.config.model.CompiledStorageProfileConfig;
import com.ghatana.datacloud.config.model.CompiledStorageProfileConfig.StorageTier;
import com.ghatana.datacloud.config.model.RawStorageProfileConfig;
import com.ghatana.datacloud.config.model.RawStorageProfileConfig.RawProfileBackend;
import com.ghatana.datacloud.config.model.RawStorageProfileConfig.RawStorageProfile;
import com.ghatana.platform.core.exception.ConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link StorageProfileCompiler}.
 *
 * <p>Validates compilation of raw storage profile YAML configuration into
 * validated, immutable runtime objects including tier, backend, size parsing,
 * and default resolution.
 *
 * @doc.type test
 * @doc.purpose Validate storage profile compilation, tier/backend mapping, and default resolution
 * @doc.layer product
 */
@DisplayName("StorageProfileCompiler Tests")
class StorageProfileCompilerTest {

    private StorageProfileCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new StorageProfileCompiler();
    }

    private RawStorageProfile rawProfile(String name, String tier, String plugin) {
        return new RawStorageProfile(name, name, tier,
                new RawProfileBackend(plugin, Map.of()),
                null, Map.of(), null);
    }

    // =========================================================================
    // COMPILE ALL
    // =========================================================================

    @Nested
    @DisplayName("compileAll")
    class CompileAll {

        @Test
        @DisplayName("should return empty list when rawConfig is null")
        void shouldReturnEmptyForNullConfig() {
            List<CompiledStorageProfileConfig> result = compiler.compileAll(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when profiles list is null")
        void shouldReturnEmptyForNullProfiles() {
            RawStorageProfileConfig config = new RawStorageProfileConfig("v1", "StorageProfile", null, null);
            List<CompiledStorageProfileConfig> result = compiler.compileAll(config);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should compile all profiles in list")
        void shouldCompileAllProfiles() {
            RawStorageProfileConfig config = new RawStorageProfileConfig("v1", "StorageProfile",
                    null,
                    List.of(
                            rawProfile("hot-profile", "HOT", "redis"),
                            rawProfile("warm-profile", "WARM", "postgres")
                    ));

            List<CompiledStorageProfileConfig> result = compiler.compileAll(config);
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("compiled list should be unmodifiable")
        void compiledListShouldBeUnmodifiable() {
            RawStorageProfileConfig config = new RawStorageProfileConfig("v1", "StorageProfile",
                    null,
                    List.of(rawProfile("p1", "HOT", "redis")));

            List<CompiledStorageProfileConfig> result = compiler.compileAll(config);
            assertThatThrownBy(() -> result.add(null)).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // =========================================================================
    // COMPILE SINGLE
    // =========================================================================

    @Nested
    @DisplayName("compile")
    class CompileSingle {

        @Test
        @DisplayName("should compile a basic profile with name and tier")
        void shouldCompileBasicProfile() {
            CompiledStorageProfileConfig result = compiler.compile(rawProfile("my-profile", "HOT", "redis"));

            assertThat(result.name()).isEqualTo("my-profile");
            assertThat(result.tier()).isEqualTo(StorageTier.HOT);
        }

        @Test
        @DisplayName("should throw NullPointerException for null raw profile")
        void shouldThrowForNullRawProfile() {
            assertThatThrownBy(() -> compiler.compile(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NullPointerException for profile without name")
        void shouldThrowForNullProfileName() {
            RawStorageProfile noName = new RawStorageProfile(null, null, "HOT",
                    new RawProfileBackend("redis", Map.of()), null, Map.of(), null);
            assertThatThrownBy(() -> compiler.compile(noName))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should use name as displayName when displayName is not set")
        void shouldDefaultDisplayNameToName() {
            RawStorageProfile profile = new RawStorageProfile("my-profile", null, "WARM",
                    new RawProfileBackend("postgres", Map.of()), null, Map.of(), null);

            CompiledStorageProfileConfig result = compiler.compile(profile);
            assertThat(result.displayName()).isEqualTo("my-profile");
        }

        @Test
        @DisplayName("should use given displayName when set")
        void shouldUseGivenDisplayName() {
            RawStorageProfile profile = new RawStorageProfile("my-profile", "My Profile", "WARM",
                    new RawProfileBackend("postgres", Map.of()), null, Map.of(), null);

            CompiledStorageProfileConfig result = compiler.compile(profile);
            assertThat(result.displayName()).isEqualTo("My Profile");
        }
    }

    // =========================================================================
    // TIER COMPILATION
    // =========================================================================

    @Nested
    @DisplayName("compileTier")
    class CompileTier {

        @ParameterizedTest
        @DisplayName("should compile valid tier strings")
        @CsvSource({
            "HOT, HOT",
            "hot, HOT",
            "WARM, WARM",
            "warm, WARM",
            "COLD, COLD",
            "cold, COLD"
        })
        void shouldCompileValidTiers(String raw, String expected) {
            StorageTier tier = compiler.compileTier(raw);
            assertThat(tier.name()).isEqualTo(expected);
        }

        @Test
        @DisplayName("should default to WARM when tier is null")
        void shouldDefaultToWarmForNull() {
            StorageTier tier = compiler.compileTier(null);
            assertThat(tier).isEqualTo(StorageTier.WARM);
        }

        @Test
        @DisplayName("should default to WARM when tier is blank")
        void shouldDefaultToWarmForBlank() {
            StorageTier tier = compiler.compileTier("  ");
            assertThat(tier).isEqualTo(StorageTier.WARM);
        }

        @ParameterizedTest
        @DisplayName("should throw ConfigurationException for invalid tier values")
        @ValueSource(strings = {"SUPERFAST", "NONE", "invalid"})
        void shouldThrowForInvalidTier(String invalidTier) {
            assertThatThrownBy(() -> compiler.compileTier(invalidTier))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("Invalid storage tier");
        }
    }

    // =========================================================================
    // BACKEND COMPILATION
    // =========================================================================

    @Nested
    @DisplayName("compileBackend")
    class CompileBackend {

        @Test
        @DisplayName("should throw ConfigurationException when backend is null")
        void shouldThrowForNullBackend() {
            RawStorageProfile profile = new RawStorageProfile("p", "p", "HOT", null, null, Map.of(), null);
            assertThatThrownBy(() -> compiler.compile(profile))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("backend plugin");
        }

        @Test
        @DisplayName("should compile backend with plugin name")
        void shouldCompileBackendWithPlugin() {
            RawStorageProfile profile = new RawStorageProfile("p", "p", "HOT",
                    new RawProfileBackend("redis-plugin", Map.of("host", "localhost")),
                    null, Map.of(), null);

            CompiledStorageProfileConfig result = compiler.compile(profile);
            assertThat(result.backend()).isNotNull();
            assertThat(result.backend().pluginName()).isEqualTo("redis-plugin");
        }
    }
}
