package com.ghatana.datacloud.config;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ConfigRegistry}.
 *
 * <p>Focuses on cache management, invalidation, version tracking, and lookup
 * behaviours that do not require a filesystem-backed ConfigLoader.
 *
 * @doc.type test
 * @doc.purpose Validate ConfigRegistry cache invalidation, version tracking, and collection listing
 * @doc.layer product
 */
@DisplayName("ConfigRegistry Tests")
@ExtendWith(MockitoExtension.class) // GH-90000
class ConfigRegistryTest extends EventloopTestBase {

    @Mock
    private ConfigLoader loader;

    @Mock
    private ConfigValidator validator;

    @Mock
    private CollectionConfigCompiler compiler;

    @Mock
    private MetricsCollector metrics;

    private ConfigRegistry registry;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new ConfigRegistry(loader, validator, compiler, metrics); // GH-90000
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should throw NullPointerException for null loader")
        void shouldThrowForNullLoader() { // GH-90000
            assertThatThrownBy(() -> new ConfigRegistry(null, validator, compiler, metrics)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should throw NullPointerException for null validator")
        void shouldThrowForNullValidator() { // GH-90000
            assertThatThrownBy(() -> new ConfigRegistry(loader, null, compiler, metrics)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should throw NullPointerException for null compiler")
        void shouldThrowForNullCompiler() { // GH-90000
            assertThatThrownBy(() -> new ConfigRegistry(loader, validator, null, metrics)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should throw NullPointerException for null metrics")
        void shouldThrowForNullMetrics() { // GH-90000
            assertThatThrownBy(() -> new ConfigRegistry(loader, validator, compiler, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // =========================================================================
    // VERSION TRACKING
    // =========================================================================

    @Nested
    @DisplayName("Version tracking")
    class VersionTracking {

        @Test
        @DisplayName("initial version should be zero")
        void initialVersionShouldBeZero() { // GH-90000
            assertThat(registry.getVersion()).isEqualTo(0L); // GH-90000
        }
    }

    // =========================================================================
    // CACHE — NOT CACHED LOOKUP
    // =========================================================================

    @Nested
    @DisplayName("Cache")
    class Cache {

        @Test
        @DisplayName("getCollectionIfCached should return null before any load")
        void shouldReturnNullBeforeLoad() { // GH-90000
            assertThat(registry.getCollectionIfCached("tenant-1", "products")).isNull(); // GH-90000
        }

        @Test
        @DisplayName("invalidate should not throw even when cache is empty")
        void shouldNotThrowOnInvalidateWhenEmpty() { // GH-90000
            assertThatCode(() -> registry.invalidate("tenant-1", "products")) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("invalidateAll should not throw when cache is empty")
        void shouldNotThrowOnInvalidateAllWhenEmpty() { // GH-90000
            assertThatCode(() -> registry.invalidateAll()) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }
    }

    // =========================================================================
    // COLLECTION NAMES
    // =========================================================================

    @Nested
    @DisplayName("getCollectionNames")
    class GetCollectionNames {

        @Test
        @DisplayName("should return empty list when no collections cached for tenant")
        void shouldReturnEmptyListForUnknownTenant() { // GH-90000
            when(loader.listCollectionsAsync("unknown-tenant")).thenReturn(Promise.of(List.of()));
            List<String> names = runPromise(() -> registry.getCollectionNames("unknown-tenant"));
            assertThat(names).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // RELOAD ALL ASYNC
    // =========================================================================

    @Nested
    @DisplayName("reloadAllAsync")
    class ReloadAllAsync {

        @Test
        @DisplayName("should complete reload when cache is empty (no-op)")
        void shouldCompleteWhenCacheEmpty() { // GH-90000
            assertThatCode(() -> runPromise(() -> registry.reloadAllAsync())) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }
    }

    // =========================================================================
    // PLUGIN NAMES
    // =========================================================================

    @Nested
    @DisplayName("getPluginNames")
    class GetPluginNames {

        @Test
        @DisplayName("should return empty list when no plugins cached for tenant")
        void shouldReturnEmptyPluginListForUnknownTenant() { // GH-90000
            when(loader.listPluginsAsync("unknown-tenant")).thenReturn(Promise.of(List.of()));
            List<String> names = runPromise(() -> registry.getPluginNames("unknown-tenant"));
            assertThat(names).isEmpty(); // GH-90000
        }
    }
}
