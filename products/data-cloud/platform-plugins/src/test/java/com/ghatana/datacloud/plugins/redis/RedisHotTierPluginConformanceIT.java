package com.ghatana.datacloud.plugins.redis;

import com.ghatana.datacloud.StorageTier;
import com.ghatana.datacloud.event.common.Offset;
import com.ghatana.datacloud.event.common.PartitionId;
import com.ghatana.datacloud.event.model.Event;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.plugin.Plugin;
import com.ghatana.platform.plugin.PluginCapability;
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginInteractionBus;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provider-backed conformance coverage for {@link RedisHotTierPlugin} and {@link RedisHotTierPluginProvider}.
 *
 * <p>Tests the real Redis implementation using Testcontainers (redis:7-alpine):
 * <ul>
 *   <li>Plugin lifecycle: initialize → start → healthCheck returns HEALTHY</li>
 *   <li>Single append returns a non-negative offset</li>
 *   <li>Batch append returns monotonically increasing offsets</li>
 *   <li>ReadById round-trips an appended event</li>
 *   <li>Idempotency key prevents duplicate appends</li>
 *   <li>Provider discovery creates a valid plugin instance</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Redis HOT-tier plugin provider-backed conformance integration test
 * @doc.layer product
 * @doc.pattern Testcontainers, ConformanceTest, IntegrationTest
 */
@Testcontainers(disabledWithoutDocker = true) 
@DisplayName("RedisHotTierPlugin conformance (real Redis)")
class RedisHotTierPluginConformanceIT extends EventloopTestBase {

    private static final int REDIS_PORT = 6379;

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = 
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(REDIS_PORT);

    private RedisHotTierPlugin plugin;

    @BeforeEach
    void setUp() throws Exception { 
        RedisStorageConfig config = RedisStorageConfig.builder()
                .host(REDIS.getHost()) 
                .port(REDIS.getMappedPort(REDIS_PORT)) 
                .maxPoolSize(4)
                .hotTierTtl(Duration.ofMinutes(5))
                .flushBatchSize(100)
                .flushIntervalMs(500L)
                .ringBufferSize(256)
                .build();

        plugin = new RedisHotTierPlugin(config);
        runPromise(() -> plugin.initialize(testPluginContext()));
        runPromise(() -> plugin.start());
    }

    @AfterEach
    void tearDown() { 
        if (plugin != null) {
            runPromise(() -> plugin.stop());
            runPromise(() -> plugin.shutdown());
        }
    }

    @Test
    @DisplayName("plugin lifecycle: state is RUNNING after initialize+start")
    void pluginStateIsRunningAfterStart() { 
        assertThat(plugin.getState()).isEqualTo(PluginState.RUNNING); 
    }

    @Test
    @DisplayName("healthCheck returns HEALTHY after successful initialization")
    void healthCheckReturnsHealthy() { 
        HealthStatus health = runPromise(() -> plugin.healthCheck());

        assertThat(health).isNotNull();
        assertThat(health.isHealthy()).isTrue(); 
    }

    @Test
    @DisplayName("append returns a non-negative offset for a single event")
    void appendReturnsPosOffsetForSingleEvent() { 
        Event event = sampleEvent("tenant-redis-single", "stream-a", "order.created");

        Offset offset = runPromise(() -> plugin.append(event));

        assertThat(offset).isNotNull();
        assertThat(offset.value()).isGreaterThanOrEqualTo(0L); 
    }

    @Test
    @DisplayName("appendBatch returns one offset per event with increasing values")
    void appendBatchReturnsIncreasedOffsets() { 
        String tenantId = "tenant-redis-batch";
        String streamName = "stream-batch";

        List<Event> events = List.of(
                sampleEvent(tenantId, streamName, "batch.a"),
                sampleEvent(tenantId, streamName, "batch.b"),
                sampleEvent(tenantId, streamName, "batch.c")
        );

        List<Offset> offsets = runPromise(() -> plugin.appendBatch(events));

        assertThat(offsets).hasSize(3); 
        for (int i = 1; i < offsets.size(); i++) {
            assertThat(offsets.get(i).value()).isGreaterThan(offsets.get(i - 1).value()); 
        }
    }

    @Test
    @DisplayName("readById returns the event that was appended")
    void readByIdRoundTripsEvent() { 
        Event event = sampleEvent("tenant-redis-read", "stream-read", "read.event");

        runPromise(() -> plugin.append(event));
        Optional<Event> found = runPromise(() -> plugin.readById(event.getTenantId(), event.getId().toString()));

        assertThat(found).isPresent(); 
        assertThat(found.orElseThrow().getId()).isEqualTo(event.getId()); 
        assertThat(found.orElseThrow().getEventTypeName()).isEqualTo("read.event"); 
    }

    @Test
    @DisplayName("duplicate idempotency key returns same offset on second append")
    void idempotencyKeyPreventsDoubleWrite() { 
        String idemKey = "idem-" + UUID.randomUUID();
        String tenantId = "tenant-redis-idem";

        Event first  = sampleEvent(tenantId, "stream-idem", "idem.event", idemKey);
        Event second = sampleEvent(tenantId, "stream-idem", "idem.event", idemKey);

        Offset o1 = runPromise(() -> plugin.append(first));
        Offset o2 = runPromise(() -> plugin.append(second));

        assertThat(o2.value()).isEqualTo(o1.value()); // same offset signals deduplication
    }

    @Test
    @DisplayName("provider discovery creates a valid, non-null plugin via PluginProvider")
    void providerCreatesValidPlugin() { 
        RedisHotTierPluginProvider provider = new RedisHotTierPluginProvider();

        assertThat(provider.isEnabled()).isTrue(); 
        assertThat(provider.getMetadata()).isNotNull();
        assertThat(provider.getMetadata().id()).isNotEmpty();

        Plugin created = provider.createPlugin();
        assertThat(created).isInstanceOf(RedisHotTierPlugin.class); 
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Event sampleEvent(String tenantId, String streamName, String eventType) { 
        return sampleEvent(tenantId, streamName, eventType, null);
    }

    private static Event sampleEvent(String tenantId, String streamName, String eventType,
                                     @Nullable String idempotencyKey) { 
        return Event.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .streamName(streamName)
                .partitionId(0)
                .eventTypeName(eventType)
                .eventTypeVersion("1.0.0")
                .occurrenceTime(Instant.now())
                .detectionTime(Instant.now())
                .idempotencyKey(idempotencyKey)
                .headers(Map.of("test-source", "redis-conformance"))
                .payload(Map.of("value", 42))
                .currentTier(StorageTier.HOT)
                .build();
    }

    private static PluginContext testPluginContext() { 
        return new PluginContext() {
            @Override
            public <T> @Nullable T getConfig(@NotNull Class<T> configType) {
                return null;
            }

            @Override
            public @NotNull String getConfig(@NotNull String key, @NotNull String defaultValue) {
                return defaultValue;
            }

            @Override
            public <T extends Plugin> @NotNull Optional<T> findPlugin(@NotNull String pluginId) {
                return Optional.empty();
            }

            @Override
            public @NotNull List<Plugin> findPluginsByCapability(@NotNull Class<? extends PluginCapability> capability) {
                return List.of();
            }

            @Override
            public @NotNull PluginInteractionBus getInteractionBus() {
                return new PluginInteractionBus() {
                    @Override
                    public <Req, Res> @NotNull Promise<Res> request(@NotNull String targetPluginId, @NotNull Req request,
                                                                     @NotNull Class<Res> responseType, @NotNull Duration timeout) {
                        return Promise.ofException(new UnsupportedOperationException());
                    }

                    @Override
                    public void publish(@NotNull String topic, @NotNull Object event) {}

                    @Override
                    public void subscribe(@NotNull String topic, @NotNull java.util.function.Consumer<Object> listener) {}
                };
            }
        };
    }
}
