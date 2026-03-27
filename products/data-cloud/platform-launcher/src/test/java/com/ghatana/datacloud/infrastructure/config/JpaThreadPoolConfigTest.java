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

@DisplayName("JpaThreadPoolConfig")
class JpaThreadPoolConfigTest {

    @Test
    @DisplayName("builder uses virtual-thread defaults")
    void builderUsesDefaults() {
        JpaThreadPoolConfig config = JpaThreadPoolConfig.builder().build();

        assertThat(config.getType()).isEqualTo(JpaThreadPoolConfig.ThreadPoolType.VIRTUAL);
        assertThat(config.getThreadNamePrefix()).isEqualTo("jpa-worker");
        assertThat(config.getQueueSize()).isEqualTo(1000);
        assertThat(config.getCorePoolSize()).isEqualTo(10);
        assertThat(config.getMaxPoolSize()).isEqualTo(100);
        assertThat(config.getKeepAliveSeconds()).isEqualTo(60L);
    }

    @Test
    @DisplayName("builder applies explicit values")
    void builderAppliesExplicitValues() {
        JpaThreadPoolConfig config = JpaThreadPoolConfig.builder()
            .type(JpaThreadPoolConfig.ThreadPoolType.PLATFORM)
            .threadNamePrefix("db-platform")
            .queueSize(32)
            .corePoolSize(4)
            .maxPoolSize(8)
            .keepAliveSeconds(15L)
            .build();

        assertThat(config.getType()).isEqualTo(JpaThreadPoolConfig.ThreadPoolType.PLATFORM);
        assertThat(config.getThreadNamePrefix()).isEqualTo("db-platform");
        assertThat(config.getQueueSize()).isEqualTo(32);
        assertThat(config.getCorePoolSize()).isEqualTo(4);
        assertThat(config.getMaxPoolSize()).isEqualTo(8);
        assertThat(config.getKeepAliveSeconds()).isEqualTo(15L);
    }

    @Test
    @DisplayName("toBuilder preserves original values")
    void toBuilderPreservesOriginalValues() {
        JpaThreadPoolConfig original = JpaThreadPoolConfig.builder()
            .type(JpaThreadPoolConfig.ThreadPoolType.PLATFORM)
            .threadNamePrefix("original")
            .queueSize(128)
            .corePoolSize(6)
            .maxPoolSize(12)
            .keepAliveSeconds(45L)
            .build();

        JpaThreadPoolConfig copy = original.toBuilder().build();

        assertThat(copy.getType()).isEqualTo(original.getType());
        assertThat(copy.getThreadNamePrefix()).isEqualTo(original.getThreadNamePrefix());
        assertThat(copy.getQueueSize()).isEqualTo(original.getQueueSize());
        assertThat(copy.getCorePoolSize()).isEqualTo(original.getCorePoolSize());
        assertThat(copy.getMaxPoolSize()).isEqualTo(original.getMaxPoolSize());
        assertThat(copy.getKeepAliveSeconds()).isEqualTo(original.getKeepAliveSeconds());
        assertThat(copy.toString()).contains("threadNamePrefix='original'");
    }

    @Test
    @DisplayName("thread pool type must not be null")
    void typeMustNotBeNull() {
        assertThatNullPointerException()
            .isThrownBy(() -> JpaThreadPoolConfig.builder().type(null));
    }

    @Test
    @DisplayName("thread name prefix must not be null")
    void prefixMustNotBeNull() {
        assertThatNullPointerException()
            .isThrownBy(() -> JpaThreadPoolConfig.builder().threadNamePrefix(null));
    }

    @Test
    @DisplayName("queue size must be positive")
    void queueSizeMustBePositive() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> JpaThreadPoolConfig.builder().queueSize(0));
    }

    @Test
    @DisplayName("core pool size must be positive")
    void corePoolSizeMustBePositive() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> JpaThreadPoolConfig.builder().corePoolSize(0));
    }

    @Test
    @DisplayName("max pool size must be positive")
    void maxPoolSizeMustBePositive() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> JpaThreadPoolConfig.builder().maxPoolSize(0));
    }

    @Test
    @DisplayName("keep alive seconds must be non negative")
    void keepAliveMustBeNonNegative() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> JpaThreadPoolConfig.builder().keepAliveSeconds(-1));
    }

    // ------------------------------------------------------------------
    // createExecutorService / createInstrumentedExecutorService
    // ------------------------------------------------------------------

    @Test
    @DisplayName("createExecutorService returns non-null executor for VIRTUAL type")
    void createExecutorServiceVirtual() {
        JpaThreadPoolConfig config = JpaThreadPoolConfig.builder()
            .type(JpaThreadPoolConfig.ThreadPoolType.VIRTUAL)
            .queueSize(16)
            .build();

        ExecutorService exec = config.createExecutorService();
        assertThat(exec).isNotNull();
        exec.shutdown();
    }

    @Test
    @DisplayName("createExecutorService returns non-null executor for PLATFORM type")
    void createExecutorServicePlatform() {
        JpaThreadPoolConfig config = JpaThreadPoolConfig.builder()
            .type(JpaThreadPoolConfig.ThreadPoolType.PLATFORM)
            .corePoolSize(2)
            .maxPoolSize(4)
            .queueSize(8)
            .build();

        ExecutorService exec = config.createExecutorService();
        assertThat(exec).isNotNull();
        exec.shutdown();
    }

    @Test
    @DisplayName("createInstrumentedExecutorService registers metrics with registry")
    void createInstrumentedExecutorServiceRegistersMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        JpaThreadPoolConfig config = JpaThreadPoolConfig.builder()
            .type(JpaThreadPoolConfig.ThreadPoolType.PLATFORM)
            .corePoolSize(1)
            .maxPoolSize(2)
            .queueSize(4)
            .build();

        ExecutorService exec = config.createInstrumentedExecutorService(registry, "test.jpa.pool");
        assertThat(exec).isNotNull();

        // Micrometer ExecutorServiceMetrics registers at least executor.pool.size
        assertThat(registry.getMeters())
            .anyMatch(m -> m.getId().getName().startsWith("test.jpa.pool"));

        exec.shutdown();
    }

    @Test
    @DisplayName("createInstrumentedExecutorService rejects null registry")
    void createInstrumentedExecutorServiceNullRegistry() {
        JpaThreadPoolConfig config = JpaThreadPoolConfig.builder().build();

        assertThatNullPointerException()
            .isThrownBy(() -> config.createInstrumentedExecutorService(null, "any.prefix"));
    }

    @Test
    @DisplayName("createInstrumentedExecutorService rejects null metricsPrefix")
    void createInstrumentedExecutorServiceNullPrefix() {
        JpaThreadPoolConfig config = JpaThreadPoolConfig.builder().build();
        MeterRegistry registry = new SimpleMeterRegistry();

        assertThatNullPointerException()
            .isThrownBy(() -> config.createInstrumentedExecutorService(registry, null));
    }

    @Test
    @DisplayName("executor throws RejectedExecutionException when queue is full")
    void executorRejectsWhenQueueFull() throws Exception {
        JpaThreadPoolConfig config = JpaThreadPoolConfig.builder()
            .type(JpaThreadPoolConfig.ThreadPoolType.PLATFORM)
            .corePoolSize(1)
            .maxPoolSize(1)
            .queueSize(1)
            .build();

        ExecutorService exec = config.createExecutorService();
        // Fill the pool + queue
        exec.submit(() -> {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        });
        exec.submit(() -> {});  // fills queue

        // Next submission must be rejected
        assertThatThrownBy(() -> exec.submit(() -> {}))
            .isInstanceOf(RejectedExecutionException.class)
            .hasMessageContaining("queue is full");

        exec.shutdownNow();
    }
}