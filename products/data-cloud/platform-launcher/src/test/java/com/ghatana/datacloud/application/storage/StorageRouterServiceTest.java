package com.ghatana.datacloud.application.storage;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.entity.storage.CollectionStorageProfile;
import com.ghatana.datacloud.entity.storage.CollectionStorageProfileRepository;
import com.ghatana.datacloud.application.storage.StorageRouterService.RoutingTarget;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT) // GH-90000
@ExtendWith(MockitoExtension.class) // GH-90000
class StorageRouterServiceTest extends EventloopTestBase {

    @Mock
    CollectionStorageProfileRepository repository;

    @Mock
    MetricsCollector metrics;

    StorageRouterService router;

    static final String TENANT = "t1";
    static final String COLLECTION = "events";
    static final String QUERY = "SELECT * FROM events";
    static final String PRIMARY_BACKEND = "postgres-primary";
    static final String FALLBACK_BACKEND = "postgres-replica";

    @BeforeEach
    void setUp() { // GH-90000
        doNothing().when(metrics).incrementCounter(anyString(), any(String[].class)); // GH-90000
        router = new StorageRouterService(repository, metrics, Duration.ofSeconds(10)); // GH-90000
    }

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    void resolveBackendFor_returnsTarget_whenProfileExists() { // GH-90000
        CollectionStorageProfile profile = profile(TENANT, PRIMARY_BACKEND, List.of()); // GH-90000
        when(repository.findByTenantAndName(TENANT, COLLECTION)) // GH-90000
                .thenReturn(Promise.of(Optional.of(profile))); // GH-90000

        RoutingTarget target = resolve(router.resolveBackendFor(TENANT, COLLECTION, QUERY)); // GH-90000

        assertThat(target.getPrimaryBackendId()).isEqualTo(PRIMARY_BACKEND); // GH-90000
        assertThat(target.getQuery()).isEqualTo(QUERY); // GH-90000
    }

    @Test
    void resolveBackendFor_includesFallbackBackends() { // GH-90000
        CollectionStorageProfile profile = profile(TENANT, PRIMARY_BACKEND, List.of(FALLBACK_BACKEND)); // GH-90000
        when(repository.findByTenantAndName(TENANT, COLLECTION)) // GH-90000
                .thenReturn(Promise.of(Optional.of(profile))); // GH-90000

        RoutingTarget target = resolve(router.resolveBackendFor(TENANT, COLLECTION, QUERY)); // GH-90000

        assertThat(target.getFallbackBackendIds()).containsExactly(FALLBACK_BACKEND); // GH-90000
    }

    // ── caching ──────────────────────────────────────────────────────────────

    @Test
    void resolveBackendFor_cachesResult_secondCallSkipsRepository() { // GH-90000
        CollectionStorageProfile profile = profile(TENANT, PRIMARY_BACKEND, List.of()); // GH-90000
        when(repository.findByTenantAndName(TENANT, COLLECTION)) // GH-90000
                .thenReturn(Promise.of(Optional.of(profile))); // GH-90000

        resolve(router.resolveBackendFor(TENANT, COLLECTION, QUERY)); // GH-90000
        resolve(router.resolveBackendFor(TENANT, COLLECTION, QUERY)); // GH-90000

        // Repository should only be called once; second call served from cache
        verify(repository, times(1)).findByTenantAndName(TENANT, COLLECTION); // GH-90000
    }

    @Test
    void resolveBackendFor_expiredCache_refetchesFromRepository() throws InterruptedException { // GH-90000
        // Use very short TTL
        router = new StorageRouterService(repository, metrics, Duration.ofMillis(50)); // GH-90000
        CollectionStorageProfile profile = profile(TENANT, PRIMARY_BACKEND, List.of()); // GH-90000
        when(repository.findByTenantAndName(TENANT, COLLECTION)) // GH-90000
                .thenReturn(Promise.of(Optional.of(profile))); // GH-90000

        resolve(router.resolveBackendFor(TENANT, COLLECTION, QUERY)); // GH-90000
        Thread.sleep(100); // wait for cache to expire // GH-90000
        resolve(router.resolveBackendFor(TENANT, COLLECTION, QUERY)); // GH-90000

        verify(repository, times(2)).findByTenantAndName(TENANT, COLLECTION); // GH-90000
    }

    // ── tenant isolation ─────────────────────────────────────────────────────

    @Test
    void resolveBackendFor_tenantMismatch_throwsException() { // GH-90000
        CollectionStorageProfile profile = profile("different-tenant", PRIMARY_BACKEND, List.of()); // GH-90000
        when(repository.findByTenantAndName(TENANT, COLLECTION)) // GH-90000
                .thenReturn(Promise.of(Optional.of(profile))); // GH-90000

        Promise<RoutingTarget> result = router.resolveBackendFor(TENANT, COLLECTION, QUERY); // GH-90000

        assertThatThrownBy(() -> resolve(result)).isInstanceOf(Exception.class); // GH-90000
    }

    @Test
    void resolveBackendFor_differentTenants_separateCacheEntries() { // GH-90000
        CollectionStorageProfile p1 = profile("tenant-A", "backend-A", List.of()); // GH-90000
        CollectionStorageProfile p2 = profile("tenant-B", "backend-B", List.of()); // GH-90000
        when(repository.findByTenantAndName("tenant-A", COLLECTION)) // GH-90000
                .thenReturn(Promise.of(Optional.of(p1))); // GH-90000
        when(repository.findByTenantAndName("tenant-B", COLLECTION)) // GH-90000
                .thenReturn(Promise.of(Optional.of(p2))); // GH-90000

        RoutingTarget t1 = resolve(router.resolveBackendFor("tenant-A", COLLECTION, QUERY)); // GH-90000
        RoutingTarget t2 = resolve(router.resolveBackendFor("tenant-B", COLLECTION, QUERY)); // GH-90000

        assertThat(t1.getPrimaryBackendId()).isEqualTo("backend-A [GH-90000]");
        assertThat(t2.getPrimaryBackendId()).isEqualTo("backend-B [GH-90000]");
    }

    // ── validation ───────────────────────────────────────────────────────────

    @Test
    void resolveBackendFor_nullTenantId_throwsException() { // GH-90000
        Promise<RoutingTarget> p = router.resolveBackendFor(null, COLLECTION, QUERY); // GH-90000
        assertThatThrownBy(() -> resolve(p)).isInstanceOf(Exception.class); // GH-90000
    }

    @Test
    void resolveBackendFor_blankTenantId_throwsException() { // GH-90000
        Promise<RoutingTarget> p = router.resolveBackendFor("  ", COLLECTION, QUERY); // GH-90000
        assertThatThrownBy(() -> resolve(p)).isInstanceOf(Exception.class); // GH-90000
    }

    @Test
    void resolveBackendFor_nullCollectionName_throwsException() { // GH-90000
        Promise<RoutingTarget> p = router.resolveBackendFor(TENANT, null, QUERY); // GH-90000
        assertThatThrownBy(() -> resolve(p)).isInstanceOf(Exception.class); // GH-90000
    }

    @Test
    void resolveBackendFor_nullQuery_throwsException() { // GH-90000
        Promise<RoutingTarget> p = router.resolveBackendFor(TENANT, COLLECTION, null); // GH-90000
        assertThatThrownBy(() -> resolve(p)).isInstanceOf(Exception.class); // GH-90000
    }

    // ── profile not found ────────────────────────────────────────────────────

    @Test
    void resolveBackendFor_noProfile_throwsResourceNotFoundException() { // GH-90000
        when(repository.findByTenantAndName(TENANT, COLLECTION)) // GH-90000
                .thenReturn(Promise.of(Optional.empty())); // GH-90000

        Promise<RoutingTarget> p = router.resolveBackendFor(TENANT, COLLECTION, QUERY); // GH-90000
        assertThatThrownBy(() -> resolve(p)).isInstanceOf(Exception.class); // GH-90000
    }

    // ── getAllBackendsFor ─────────────────────────────────────────────────────

    @Test
    void getAllBackendsFor_returnsPrimaryAndFallbacks() { // GH-90000
        CollectionStorageProfile profile = profile(TENANT, PRIMARY_BACKEND, List.of(FALLBACK_BACKEND)); // GH-90000
        when(repository.findByTenantAndName(TENANT, COLLECTION)) // GH-90000
                .thenReturn(Promise.of(Optional.of(profile))); // GH-90000

        List<String> backends = resolve(router.getAllBackendsFor(TENANT, COLLECTION)); // GH-90000

        assertThat(backends).contains(PRIMARY_BACKEND, FALLBACK_BACKEND); // GH-90000
    }

    @Test
    void getAllBackendsFor_blankTenantId_throwsException() { // GH-90000
        Promise<List<String>> p = router.getAllBackendsFor("", COLLECTION); // GH-90000
        assertThatThrownBy(() -> resolve(p)).isInstanceOf(Exception.class); // GH-90000
    }

    // ── constructor ──────────────────────────────────────────────────────────

    @Test
    void constructor_nullRepository_throwsNPE() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> new StorageRouterService(null, metrics, Duration.ofSeconds(10))); // GH-90000
    }

    @Test
    void constructor_nullMetrics_throwsNPE() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> new StorageRouterService(repository, null, Duration.ofSeconds(10))); // GH-90000
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private CollectionStorageProfile profile(String tenantId, String primaryBackend, List<String> fallbacks) { // GH-90000
        CollectionStorageProfile p = mock(CollectionStorageProfile.class); // GH-90000
        when(p.getTenantId()).thenReturn(tenantId); // GH-90000
        when(p.getPrimaryBackendId()).thenReturn(primaryBackend); // GH-90000
        when(p.getFallbackBackendIds()).thenReturn(fallbacks); // GH-90000
        when(p.hasFailoverSupport()).thenReturn(!fallbacks.isEmpty()); // GH-90000
        return p;
    }

    private <T> T resolve(Promise<T> promise) { // GH-90000
        return runPromise(() -> promise); // GH-90000
    }
}
