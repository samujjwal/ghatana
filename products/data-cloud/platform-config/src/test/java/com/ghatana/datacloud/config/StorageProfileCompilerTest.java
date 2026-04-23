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
    void setUp() { // GH-90000
        compiler = new StorageProfileCompiler(); // GH-90000
    }

    private RawStorageProfile rawProfile(String name, String tier, String plugin) { // GH-90000
        return new RawStorageProfile(name, name, tier, // GH-90000
                new RawProfileBackend(plugin, Map.of()), // GH-90000
                null, Map.of(), null); // GH-90000
    }

    // =========================================================================
    // COMPILE ALL
    // =========================================================================

    @Nested
    @DisplayName("compileAll")
    class CompileAll {

        @Test
        @DisplayName("should return empty list when rawConfig is null")
        void shouldReturnEmptyForNullConfig() { // GH-90000
            List<CompiledStorageProfileConfig> result = compiler.compileAll(null); // GH-90000
            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should return empty list when profiles list is null")
        void shouldReturnEmptyForNullProfiles() { // GH-90000
            RawStorageProfileConfig config = new RawStorageProfileConfig("v1", "StorageProfile", null, null); // GH-90000
            List<CompiledStorageProfileConfig> result = compiler.compileAll(config); // GH-90000
            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should compile all profiles in list")
        void shouldCompileAllProfiles() { // GH-90000
            RawStorageProfileConfig config = new RawStorageProfileConfig("v1", "StorageProfile", // GH-90000
                    null,
                    List.of( // GH-90000
                            rawProfile("hot-profile", "HOT", "redis"), // GH-90000
                            rawProfile("warm-profile", "WARM", "postgres") // GH-90000
                    ));

            List<CompiledStorageProfileConfig> result = compiler.compileAll(config); // GH-90000
            assertThat(result).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("compiled list should be unmodifiable")
        void compiledListShouldBeUnmodifiable() { // GH-90000
            RawStorageProfileConfig config = new RawStorageProfileConfig("v1", "StorageProfile", // GH-90000
                    null,
                    List.of(rawProfile("p1", "HOT", "redis"))); // GH-90000

            List<CompiledStorageProfileConfig> result = compiler.compileAll(config); // GH-90000
            assertThatThrownBy(() -> result.add(null)).isInstanceOf(UnsupportedOperationException.class); // GH-90000
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
        void shouldCompileBasicProfile() { // GH-90000
            CompiledStorageProfileConfig result = compiler.compile(rawProfile("my-profile", "HOT", "redis")); // GH-90000

            assertThat(result.name()).isEqualTo("my-profile");
            assertThat(result.tier()).isEqualTo(StorageTier.HOT); // GH-90000
        }

        @Test
        @DisplayName("should throw NullPointerException for null raw profile")
        void shouldThrowForNullRawProfile() { // GH-90000
            assertThatThrownBy(() -> compiler.compile(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should throw NullPointerException for profile without name")
        void shouldThrowForNullProfileName() { // GH-90000
            RawStorageProfile noName = new RawStorageProfile(null, null, "HOT", // GH-90000
                    new RawProfileBackend("redis", Map.of()), null, Map.of(), null); // GH-90000
            assertThatThrownBy(() -> compiler.compile(noName)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should use name as displayName when displayName is not set")
        void shouldDefaultDisplayNameToName() { // GH-90000
            RawStorageProfile profile = new RawStorageProfile("my-profile", null, "WARM", // GH-90000
                    new RawProfileBackend("postgres", Map.of()), null, Map.of(), null); // GH-90000

            CompiledStorageProfileConfig result = compiler.compile(profile); // GH-90000
            assertThat(result.displayName()).isEqualTo("my-profile");
        }

        @Test
        @DisplayName("should use given displayName when set")
        void shouldUseGivenDisplayName() { // GH-90000
            RawStorageProfile profile = new RawStorageProfile("my-profile", "My Profile", "WARM", // GH-90000
                    new RawProfileBackend("postgres", Map.of()), null, Map.of(), null); // GH-90000

            CompiledStorageProfileConfig result = compiler.compile(profile); // GH-90000
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
        @CsvSource({ // GH-90000
            "HOT, HOT",
            "hot, HOT",
            "WARM, WARM",
            "warm, WARM",
            "COLD, COLD",
            "cold, COLD"
        })
        void shouldCompileValidTiers(String raw, String expected) { // GH-90000
            StorageTier tier = compiler.compileTier(raw); // GH-90000
            assertThat(tier.name()).isEqualTo(expected); // GH-90000
        }

        @Test
        @DisplayName("should default to WARM when tier is null")
        void shouldDefaultToWarmForNull() { // GH-90000
            StorageTier tier = compiler.compileTier(null); // GH-90000
            assertThat(tier).isEqualTo(StorageTier.WARM); // GH-90000
        }

        @Test
        @DisplayName("should default to WARM when tier is blank")
        void shouldDefaultToWarmForBlank() { // GH-90000
            StorageTier tier = compiler.compileTier("  ");
            assertThat(tier).isEqualTo(StorageTier.WARM); // GH-90000
        }

        @ParameterizedTest
        @DisplayName("should throw ConfigurationException for invalid tier values")
        @ValueSource(strings = {"SUPERFAST", "NONE", "invalid"}) // GH-90000
        void shouldThrowForInvalidTier(String invalidTier) { // GH-90000
            assertThatThrownBy(() -> compiler.compileTier(invalidTier)) // GH-90000
                    .isInstanceOf(ConfigurationException.class) // GH-90000
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
        void shouldThrowForNullBackend() { // GH-90000
            RawStorageProfile profile = new RawStorageProfile("p", "p", "HOT", null, null, Map.of(), null); // GH-90000
            assertThatThrownBy(() -> compiler.compile(profile)) // GH-90000
                    .isInstanceOf(ConfigurationException.class) // GH-90000
                    .hasMessageContaining("backend plugin");
        }

        @Test
        @DisplayName("should compile backend with plugin name")
        void shouldCompileBackendWithPlugin() { // GH-90000
            RawStorageProfile profile = new RawStorageProfile("p", "p", "HOT", // GH-90000
                    new RawProfileBackend("redis-plugin", Map.of("host", "localhost")), // GH-90000
                    null, Map.of(), null); // GH-90000

            CompiledStorageProfileConfig result = compiler.compile(profile); // GH-90000
            assertThat(result.backend()).isNotNull(); // GH-90000
            assertThat(result.backend().pluginName()).isEqualTo("redis-plugin");
        }
    }
}
