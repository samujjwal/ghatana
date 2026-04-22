package com.ghatana.datacloud.infrastructure.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JpaThreadPoolConfig [GH-90000]")
class JpaThreadPoolConfigTest {

    @Test
    @DisplayName("builder uses virtual-thread defaults [GH-90000]")
    void builderUsesDefaults() { // GH-90000
        JpaThreadPoolConfig config = JpaThreadPoolConfig.builder().build(); // GH-90000

        assertThat(config.getType()).isEqualTo(JpaThreadPoolConfig.ThreadPoolType.VIRTUAL); // GH-90000
        assertThat(config.getThreadNamePrefix()).isEqualTo("jpa-worker [GH-90000]");
        assertThat(config.getQueueSize()).isEqualTo(1000); // GH-90000
        assertThat(config.getCorePoolSize()).isEqualTo(10); // GH-90000
        assertThat(config.getMaxPoolSize()).isEqualTo(100); // GH-90000
        assertThat(config.getKeepAliveSeconds()).isEqualTo(60L); // GH-90000
    }

    @Test
    @DisplayName("builder applies explicit values [GH-90000]")
    void builderAppliesExplicitValues() { // GH-90000
        JpaThreadPoolConfig config = JpaThreadPoolConfig.builder() // GH-90000
            .type(JpaThreadPoolConfig.ThreadPoolType.PLATFORM) // GH-90000
            .threadNamePrefix("db-platform [GH-90000]")
            .queueSize(32) // GH-90000
            .corePoolSize(4) // GH-90000
            .maxPoolSize(8) // GH-90000
            .keepAliveSeconds(15L) // GH-90000
            .build(); // GH-90000

        assertThat(config.getType()).isEqualTo(JpaThreadPoolConfig.ThreadPoolType.PLATFORM); // GH-90000
        assertThat(config.getThreadNamePrefix()).isEqualTo("db-platform [GH-90000]");
        assertThat(config.getQueueSize()).isEqualTo(32); // GH-90000
        assertThat(config.getCorePoolSize()).isEqualTo(4); // GH-90000
        assertThat(config.getMaxPoolSize()).isEqualTo(8); // GH-90000
        assertThat(config.getKeepAliveSeconds()).isEqualTo(15L); // GH-90000
    }

    @Test
    @DisplayName("toBuilder preserves original values [GH-90000]")
    void toBuilderPreservesOriginalValues() { // GH-90000
        JpaThreadPoolConfig original = JpaThreadPoolConfig.builder() // GH-90000
            .type(JpaThreadPoolConfig.ThreadPoolType.PLATFORM) // GH-90000
            .threadNamePrefix("original [GH-90000]")
            .queueSize(128) // GH-90000
            .corePoolSize(6) // GH-90000
            .maxPoolSize(12) // GH-90000
            .keepAliveSeconds(45L) // GH-90000
            .build(); // GH-90000

        JpaThreadPoolConfig copy = original.toBuilder().build(); // GH-90000

        assertThat(copy.getType()).isEqualTo(original.getType()); // GH-90000
        assertThat(copy.getThreadNamePrefix()).isEqualTo(original.getThreadNamePrefix()); // GH-90000
        assertThat(copy.getQueueSize()).isEqualTo(original.getQueueSize()); // GH-90000
        assertThat(copy.getCorePoolSize()).isEqualTo(original.getCorePoolSize()); // GH-90000
        assertThat(copy.getMaxPoolSize()).isEqualTo(original.getMaxPoolSize()); // GH-90000
        assertThat(copy.getKeepAliveSeconds()).isEqualTo(original.getKeepAliveSeconds()); // GH-90000
        assertThat(copy.toString()).contains("threadNamePrefix='original' [GH-90000]");
    }

    @Test
    @DisplayName("thread pool type must not be null [GH-90000]")
    void typeMustNotBeNull() { // GH-90000
        assertThatNullPointerException() // GH-90000
            .isThrownBy(() -> JpaThreadPoolConfig.builder().type(null)); // GH-90000
    }

    @Test
    @DisplayName("thread name prefix must not be null [GH-90000]")
    void prefixMustNotBeNull() { // GH-90000
        assertThatNullPointerException() // GH-90000
            .isThrownBy(() -> JpaThreadPoolConfig.builder().threadNamePrefix(null)); // GH-90000
    }

    @Test
    @DisplayName("queue size must be positive [GH-90000]")
    void queueSizeMustBePositive() { // GH-90000
        assertThatIllegalArgumentException() // GH-90000
            .isThrownBy(() -> JpaThreadPoolConfig.builder().queueSize(0)); // GH-90000
    }

    @Test
    @DisplayName("core pool size must be positive [GH-90000]")
    void corePoolSizeMustBePositive() { // GH-90000
        assertThatIllegalArgumentException() // GH-90000
            .isThrownBy(() -> JpaThreadPoolConfig.builder().corePoolSize(0)); // GH-90000
    }

    @Test
    @DisplayName("max pool size must be positive [GH-90000]")
    void maxPoolSizeMustBePositive() { // GH-90000
        assertThatIllegalArgumentException() // GH-90000
            .isThrownBy(() -> JpaThreadPoolConfig.builder().maxPoolSize(0)); // GH-90000
    }

    @Test
    @DisplayName("keep alive seconds must be non negative [GH-90000]")
    void keepAliveMustBeNonNegative() { // GH-90000
        assertThatIllegalArgumentException() // GH-90000
            .isThrownBy(() -> JpaThreadPoolConfig.builder().keepAliveSeconds(-1)); // GH-90000
    }

    // ------------------------------------------------------------------
    // createExecutorService / createInstrumentedExecutorService
    // ------------------------------------------------------------------

    @Test
    @DisplayName("createExecutorService returns non-null executor for VIRTUAL type [GH-90000]")
    void createExecutorServiceVirtual() { // GH-90000
        JpaThreadPoolConfig config = JpaThreadPoolConfig.builder() // GH-90000
            .type(JpaThreadPoolConfig.ThreadPoolType.VIRTUAL) // GH-90000
            .queueSize(16) // GH-90000
            .build(); // GH-90000

        ExecutorService exec = config.createExecutorService(); // GH-90000
        assertThat(exec).isNotNull(); // GH-90000
        exec.shutdown(); // GH-90000
    }

    @Test
    @DisplayName("createExecutorService returns non-null executor for PLATFORM type [GH-90000]")
    void createExecutorServicePlatform() { // GH-90000
        JpaThreadPoolConfig config = JpaThreadPoolConfig.builder() // GH-90000
            .type(JpaThreadPoolConfig.ThreadPoolType.PLATFORM) // GH-90000
            .corePoolSize(2) // GH-90000
            .maxPoolSize(4) // GH-90000
            .queueSize(8) // GH-90000
            .build(); // GH-90000

        ExecutorService exec = config.createExecutorService(); // GH-90000
        assertThat(exec).isNotNull(); // GH-90000
        exec.shutdown(); // GH-90000
    }

    @Test
    @DisplayName("createInstrumentedExecutorService registers metrics with registry [GH-90000]")
    void createInstrumentedExecutorServiceRegistersMetrics() { // GH-90000
        MeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
        JpaThreadPoolConfig config = JpaThreadPoolConfig.builder() // GH-90000
            .type(JpaThreadPoolConfig.ThreadPoolType.PLATFORM) // GH-90000
            .corePoolSize(1) // GH-90000
            .maxPoolSize(2) // GH-90000
            .queueSize(4) // GH-90000
            .build(); // GH-90000

        ExecutorService exec = config.createInstrumentedExecutorService(registry, "test.jpa.pool"); // GH-90000
        assertThat(exec).isNotNull(); // GH-90000

        // Micrometer ExecutorServiceMetrics registers at least executor.pool.size
        assertThat(registry.getMeters()) // GH-90000
            .anyMatch(m -> m.getId().getName().startsWith("executor [GH-90000]"));

        exec.shutdown(); // GH-90000
    }

    @Test
    @DisplayName("createInstrumentedExecutorService rejects null registry [GH-90000]")
    void createInstrumentedExecutorServiceNullRegistry() { // GH-90000
        JpaThreadPoolConfig config = JpaThreadPoolConfig.builder().build(); // GH-90000

        assertThatNullPointerException() // GH-90000
            .isThrownBy(() -> config.createInstrumentedExecutorService(null, "any.prefix")); // GH-90000
    }

    @Test
    @DisplayName("createInstrumentedExecutorService rejects null metricsPrefix [GH-90000]")
    void createInstrumentedExecutorServiceNullPrefix() { // GH-90000
        JpaThreadPoolConfig config = JpaThreadPoolConfig.builder().build(); // GH-90000
        MeterRegistry registry = new SimpleMeterRegistry(); // GH-90000

        assertThatNullPointerException() // GH-90000
            .isThrownBy(() -> config.createInstrumentedExecutorService(registry, null)); // GH-90000
    }

    @Test
    @DisplayName("executor throws RejectedExecutionException when queue is full [GH-90000]")
    void executorRejectsWhenQueueFull() throws Exception { // GH-90000
        JpaThreadPoolConfig config = JpaThreadPoolConfig.builder() // GH-90000
            .type(JpaThreadPoolConfig.ThreadPoolType.PLATFORM) // GH-90000
            .corePoolSize(1) // GH-90000
            .maxPoolSize(1) // GH-90000
            .queueSize(1) // GH-90000
            .build(); // GH-90000

        ExecutorService exec = config.createExecutorService(); // GH-90000
        // Fill the pool + queue
        exec.submit(() -> { // GH-90000
            try { Thread.sleep(200); } catch (InterruptedException ignored) {} // GH-90000
        });
        exec.submit(() -> {});  // fills queue // GH-90000

        // Next submission must be rejected
        assertThatThrownBy(() -> exec.submit(() -> {})) // GH-90000
            .isInstanceOf(RejectedExecutionException.class) // GH-90000
            .hasMessageContaining("queue is full [GH-90000]");

        exec.shutdownNow(); // GH-90000
    }
}
