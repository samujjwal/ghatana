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
@DisplayName("LazyKernelModule Tests")
@ExtendWith(MockitoExtension.class) 
class LazyKernelModuleTest extends EventloopTestBase {

    @Mock
    private KernelContext context;

    private TrackingModule delegate;
    private LazyKernelModule lazy;

    @BeforeEach
    void setUp() { 
        delegate = new TrackingModule("lazy-test", "1.0.0"); 
        lazy = LazyKernelModule.wrap(delegate); 
    }

    @Test
    @DisplayName("Identity is available before initialization")
    void identityAvailableBeforeInit() { 
        assertThat(lazy.getModuleId()).isEqualTo("lazy-test");
        assertThat(lazy.getVersion()).isEqualTo("1.0.0");
        assertThat(lazy.getCapabilities()).isEmpty(); 
        assertThat(lazy.getDependencies()).isEmpty(); 
    }

    @Test
    @DisplayName("Delegate is NOT initialized after initialize() is called")
    void delegateNotInitializedOnInitializeCall() { 
        lazy.initialize(context); 

        assertThat(lazy.isInitialized()).isFalse(); 
        assertThat(delegate.initCount()).isZero(); 
    }

    @Test
    @DisplayName("Delegate IS initialized just before first start()")
    void delegateInitializedOnFirstStart() { 
        lazy.initialize(context); 

        runPromise(lazy::start); 

        assertThat(lazy.isInitialized()).isTrue(); 
        assertThat(delegate.initCount()).isEqualTo(1); 
        assertThat(delegate.startCount()).isEqualTo(1); 
    }

    @Test
    @DisplayName("Delegate is initialized exactly once across multiple start calls")
    void initializationHappensOnlyOnce() { 
        lazy.initialize(context); 

        runPromise(lazy::start); 
        runPromise(lazy::stop); 
        runPromise(lazy::start); 

        assertThat(delegate.initCount()).isEqualTo(1); 
        assertThat(delegate.startCount()).isEqualTo(2); 
    }

    @Test
    @DisplayName("stop() is a no-op when module was never started")
    void stopBeforeStartIsNoOp() { 
        lazy.initialize(context); 

        // no start(), just stop 
        runPromise(lazy::stop); 

        assertThat(lazy.isInitialized()).isFalse(); 
        assertThat(delegate.stopCount()).isZero(); 
    }

    @Test
    @DisplayName("Health status is UNHEALTHY before initialization")
    void healthStatusUnhealthyBeforeInit() { 
        HealthStatus status = lazy.getHealthStatus(); 
        assertThat(status.isHealthy()).isFalse(); 
    }

    @Test
    @DisplayName("Health status delegates to wrapped module after initialization")
    void healthStatusDelegatesAfterInit() { 
        lazy.initialize(context); 
        runPromise(lazy::start); 

        assertThat(lazy.getHealthStatus().isHealthy()).isTrue(); 
    }

    @Test
    @DisplayName("start() without prior initialize() throws IllegalStateException")
    void startWithoutInitializeThrows() { 
        // No initialize() call 
        assertThatThrownBy(() -> runPromise(lazy::start)) 
            .isInstanceOf(IllegalStateException.class) 
            .hasMessageContaining("KernelContext");
    }

    @Test
    @DisplayName("wrap() rejects null delegate")
    void wrapRejectsNull() { 
        assertThatThrownBy(() -> LazyKernelModule.wrap(null)) 
            .isInstanceOf(NullPointerException.class); 
    }

    @Test
    @DisplayName("getDelegate() returns the wrapped module")
    void getDelegateReturnsWrapped() { 
        assertThat(lazy.getDelegate()).isSameAs(delegate); 
    }

    // ==================== Test fixture ====================

    static class TrackingModule implements KernelModule {
        private final String id;
        private final String version;
        private final AtomicInteger initCount = new AtomicInteger(0); 
        private final AtomicInteger startCount = new AtomicInteger(0); 
        private final AtomicInteger stopCount = new AtomicInteger(0); 

        TrackingModule(String id, String version) { 
            this.id = id;
            this.version = version;
        }

        @Override public String getModuleId() { return id; } 
        @Override public String getVersion() { return version; } 
        @Override public Set<KernelCapability> getCapabilities() { return Set.of(); } 
        @Override public Set<KernelDependency> getDependencies() { return Set.of(); } 

        @Override
        public void initialize(KernelContext context) { 
            initCount.incrementAndGet(); 
        }

        @Override
        public Promise<Void> start() { 
            startCount.incrementAndGet(); 
            return Promise.complete(); 
        }

        @Override
        public Promise<Void> stop() { 
            stopCount.incrementAndGet(); 
            return Promise.complete(); 
        }

        @Override
        public HealthStatus getHealthStatus() { 
            return HealthStatus.healthy(); 
        }

        int initCount() { return initCount.get(); } 
        int startCount() { return startCount.get(); } 
        int stopCount() { return stopCount.get(); } 
    }
}
