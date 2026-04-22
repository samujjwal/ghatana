package com.ghatana.kernel.module;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link LazyKernelModule}.
 *
 * @doc.type class
 * @doc.purpose Validates deferred initialization semantics of LazyKernelModule
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("LazyKernelModule Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class LazyKernelModuleTest extends EventloopTestBase {

    @Mock
    private KernelContext context;

    private TrackingModule delegate;
    private LazyKernelModule lazy;

    @BeforeEach
    void setUp() { // GH-90000
        delegate = new TrackingModule("lazy-test", "1.0.0"); // GH-90000
        lazy = LazyKernelModule.wrap(delegate); // GH-90000
    }

    @Test
    @DisplayName("Identity is available before initialization [GH-90000]")
    void identityAvailableBeforeInit() { // GH-90000
        assertThat(lazy.getModuleId()).isEqualTo("lazy-test [GH-90000]");
        assertThat(lazy.getVersion()).isEqualTo("1.0.0 [GH-90000]");
        assertThat(lazy.getCapabilities()).isEmpty(); // GH-90000
        assertThat(lazy.getDependencies()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Delegate is NOT initialized after initialize() is called [GH-90000]")
    void delegateNotInitializedOnInitializeCall() { // GH-90000
        lazy.initialize(context); // GH-90000

        assertThat(lazy.isInitialized()).isFalse(); // GH-90000
        assertThat(delegate.initCount()).isZero(); // GH-90000
    }

    @Test
    @DisplayName("Delegate IS initialized just before first start() [GH-90000]")
    void delegateInitializedOnFirstStart() { // GH-90000
        lazy.initialize(context); // GH-90000

        runPromise(lazy::start); // GH-90000

        assertThat(lazy.isInitialized()).isTrue(); // GH-90000
        assertThat(delegate.initCount()).isEqualTo(1); // GH-90000
        assertThat(delegate.startCount()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Delegate is initialized exactly once across multiple start calls [GH-90000]")
    void initializationHappensOnlyOnce() { // GH-90000
        lazy.initialize(context); // GH-90000

        runPromise(lazy::start); // GH-90000
        runPromise(lazy::stop); // GH-90000
        runPromise(lazy::start); // GH-90000

        assertThat(delegate.initCount()).isEqualTo(1); // GH-90000
        assertThat(delegate.startCount()).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("stop() is a no-op when module was never started [GH-90000]")
    void stopBeforeStartIsNoOp() { // GH-90000
        lazy.initialize(context); // GH-90000

        // no start(), just stop // GH-90000
        runPromise(lazy::stop); // GH-90000

        assertThat(lazy.isInitialized()).isFalse(); // GH-90000
        assertThat(delegate.stopCount()).isZero(); // GH-90000
    }

    @Test
    @DisplayName("Health status is UNHEALTHY before initialization [GH-90000]")
    void healthStatusUnhealthyBeforeInit() { // GH-90000
        HealthStatus status = lazy.getHealthStatus(); // GH-90000
        assertThat(status.isHealthy()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Health status delegates to wrapped module after initialization [GH-90000]")
    void healthStatusDelegatesAfterInit() { // GH-90000
        lazy.initialize(context); // GH-90000
        runPromise(lazy::start); // GH-90000

        assertThat(lazy.getHealthStatus().isHealthy()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("start() without prior initialize() throws IllegalStateException [GH-90000]")
    void startWithoutInitializeThrows() { // GH-90000
        // No initialize() call // GH-90000
        assertThatThrownBy(() -> runPromise(lazy::start)) // GH-90000
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("KernelContext [GH-90000]");
    }

    @Test
    @DisplayName("wrap() rejects null delegate [GH-90000]")
    void wrapRejectsNull() { // GH-90000
        assertThatThrownBy(() -> LazyKernelModule.wrap(null)) // GH-90000
            .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("getDelegate() returns the wrapped module [GH-90000]")
    void getDelegateReturnsWrapped() { // GH-90000
        assertThat(lazy.getDelegate()).isSameAs(delegate); // GH-90000
    }

    // ==================== Test fixture ====================

    static class TrackingModule implements KernelModule {
        private final String id;
        private final String version;
        private final AtomicInteger initCount = new AtomicInteger(0); // GH-90000
        private final AtomicInteger startCount = new AtomicInteger(0); // GH-90000
        private final AtomicInteger stopCount = new AtomicInteger(0); // GH-90000

        TrackingModule(String id, String version) { // GH-90000
            this.id = id;
            this.version = version;
        }

        @Override public String getModuleId() { return id; } // GH-90000
        @Override public String getVersion() { return version; } // GH-90000
        @Override public Set<KernelCapability> getCapabilities() { return Set.of(); } // GH-90000
        @Override public Set<KernelDependency> getDependencies() { return Set.of(); } // GH-90000

        @Override
        public void initialize(KernelContext context) { // GH-90000
            initCount.incrementAndGet(); // GH-90000
        }

        @Override
        public Promise<Void> start() { // GH-90000
            startCount.incrementAndGet(); // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<Void> stop() { // GH-90000
            stopCount.incrementAndGet(); // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public HealthStatus getHealthStatus() { // GH-90000
            return HealthStatus.healthy(); // GH-90000
        }

        int initCount() { return initCount.get(); } // GH-90000
        int startCount() { return startCount.get(); } // GH-90000
        int stopCount() { return stopCount.get(); } // GH-90000
    }
}
