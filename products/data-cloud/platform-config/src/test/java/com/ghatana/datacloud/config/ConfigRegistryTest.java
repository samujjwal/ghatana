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
@ExtendWith(MockitoExtension.class) 
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
    void setUp() { 
        registry = new ConfigRegistry(loader, validator, compiler, metrics); 
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should throw NullPointerException for null loader")
        void shouldThrowForNullLoader() { 
            assertThatThrownBy(() -> new ConfigRegistry(null, validator, compiler, metrics)) 
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        @DisplayName("should throw NullPointerException for null validator")
        void shouldThrowForNullValidator() { 
            assertThatThrownBy(() -> new ConfigRegistry(loader, null, compiler, metrics)) 
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        @DisplayName("should throw NullPointerException for null compiler")
        void shouldThrowForNullCompiler() { 
            assertThatThrownBy(() -> new ConfigRegistry(loader, validator, null, metrics)) 
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        @DisplayName("should throw NullPointerException for null metrics")
        void shouldThrowForNullMetrics() { 
            assertThatThrownBy(() -> new ConfigRegistry(loader, validator, compiler, null)) 
                    .isInstanceOf(NullPointerException.class); 
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
        void initialVersionShouldBeZero() { 
            assertThat(registry.getVersion()).isEqualTo(0L); 
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
        void shouldReturnNullBeforeLoad() { 
            assertThat(registry.getCollectionIfCached("tenant-1", "products")).isNull(); 
        }

        @Test
        @DisplayName("invalidate should not throw even when cache is empty")
        void shouldNotThrowOnInvalidateWhenEmpty() { 
            assertThatCode(() -> registry.invalidate("tenant-1", "products")) 
                    .doesNotThrowAnyException(); 
        }

        @Test
        @DisplayName("invalidateAll should not throw when cache is empty")
        void shouldNotThrowOnInvalidateAllWhenEmpty() { 
            assertThatCode(() -> registry.invalidateAll()) 
                    .doesNotThrowAnyException(); 
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
        void shouldReturnEmptyListForUnknownTenant() { 
            when(loader.listCollectionsAsync("unknown-tenant")).thenReturn(Promise.of(List.of()));
            List<String> names = runPromise(() -> registry.getCollectionNames("unknown-tenant"));
            assertThat(names).isEmpty(); 
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
        void shouldCompleteWhenCacheEmpty() { 
            assertThatCode(() -> runPromise(() -> registry.reloadAllAsync())) 
                    .doesNotThrowAnyException(); 
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
        void shouldReturnEmptyPluginListForUnknownTenant() { 
            when(loader.listPluginsAsync("unknown-tenant")).thenReturn(Promise.of(List.of()));
            List<String> names = runPromise(() -> registry.getPluginNames("unknown-tenant"));
            assertThat(names).isEmpty(); 
        }
    }
}
