package com.ghatana.datacloud.plugins.kafka;

import com.ghatana.datacloud.event.common.Offset;
import com.ghatana.datacloud.event.common.PartitionId;
import com.ghatana.datacloud.event.model.Event;
import com.ghatana.datacloud.event.spi.StreamingPlugin;
import com.ghatana.platform.plugin.HealthStatus;
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Apache Kafka implementation of StreamingPlugin SPI.
 *
 * <p>
 * <b>Purpose</b><br>
 * High-throughput, distributed event streaming using Apache Kafka. Provides:
 * <ul>
 * <li><b>Consumer Groups</b>: Coordinated partition assignment</li>
 * <li><b>Exactly-Once</b>: Transactional producers with read-committed
 * consumers</li>
 * <li><b>Backpressure</b>: Flow control via poll intervals</li>
 * <li><b>Multi-Tenancy</b>: Tenant-prefixed topics</li>
 * </ul>
 *
 * <p>
 * <b>Topic Naming</b><br>
 * Topics follow the pattern: {@code {prefix}.{tenantId}.{streamName}}
 * <pre>
 * eventcloud.tenant-123.user-events
 * eventcloud.tenant-456.orders
 * </pre>
 *
 * <p>
 * <b>Usage Example</b><br>
 * <pre>{@code
 * KafkaStreamingConfig config = KafkaStreamingConfig.builder()
 *     .bootstrapServers("kafka:9092")
 *     .exactlyOnceEnabled(true)
 *     .build();
 *
 * KafkaStreamingPlugin plugin = new KafkaStreamingPlugin(config);
 * plugin.initialize(context);
 *
 * // Subscribe with consumer group
 * plugin.subscribeWithGroup("tenant-1", "orders", "order-processor", event -> {
 *     processOrder(event);
 * });
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Apache Kafka streaming plugin implementation
 * @doc.layer plugin
 * @doc.pattern Plugin, Observer, Pub/Sub
 */
public class KafkaStreamingPlugin implements StreamingPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaStreamingPlugin.class);

    private static final PluginMetadata METADATA = PluginMetadata.builder()
            .id("kafka-streaming")
            .name("Kafka Streaming Plugin")
            .version("1.0.0")
            .description("Apache Kafka streaming plugin for EventCloud")
            .type(PluginType.STREAMING)
            .capabilities(Set.of("consumer-groups", "exactly-once", "backpressure", "partitioning"))
            .vendor("Ghatana")
            .license("Apache-2.0")
            .build();

    private final KafkaStreamingConfig config;
    private final EventSerializer eventSerializer;
    private final KafkaConsumerGroupManager consumerGroupManager;

    // Eventloop and metrics
    private Eventloop eventloop;
    private MeterRegistry meterRegistry;
    private ExecutorService blockingExecutor;

    // Kafka clients
    private KafkaProducer<String, byte[]> producer;
    private AdminClient adminClient;

    // Active subscriptions
    private final Map<String, KafkaSubscription> subscriptions = new ConcurrentHashMap<>();

    // Consumer thread pool
    private ExecutorService consumerExecutor;

    // Lifecycle state
    private final AtomicReference<PluginState> state = new AtomicReference<>(PluginState.UNLOADED);
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Metrics
    private Counter publishedEvents;
    private Counter consumedEvents;
    private Counter publishErrors;
    private Counter consumeErrors;
    private Timer publishLatency;
    private Timer consumeLatency;

    public KafkaStreamingPlugin() {
        this(KafkaStreamingConfig.defaults());
    }

    /**
     * Creates a new Kafka streaming plugin.
     *
     * @param config Kafka configuration
     */
    public KafkaStreamingPlugin(KafkaStreamingConfig config) {
        this.config = config;
        this.meterRegistry = new SimpleMeterRegistry();
        this.eventSerializer = new EventSerializer();
        this.consumerGroupManager = new KafkaConsumerGroupManager(config, this.meterRegistry);
    }

    /**
     * Creates a new Kafka streaming plugin with injected dependencies.
     *
     * @param config Kafka configuration
     * @param eventloop ActiveJ eventloop
     * @param meterRegistry metrics registry
     */
    public KafkaStreamingPlugin(
            KafkaStreamingConfig config,
            Eventloop eventloop,
            MeterRegistry meterRegistry) {
        this.config = config;
        this.eventloop = eventloop;
        this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
        this.eventSerializer = new EventSerializer();
        this.consumerGroupManager = new KafkaConsumerGroupManager(config, this.meterRegistry);
    }

    // ==================== Plugin Lifecycle ====================
    @Override
    public PluginMetadata metadata() {
        return METADATA;
    }

    @Override
    public PluginState getState() {
        return state.get();
    }

    @Override
    public Promise<Void> initialize(PluginContext context) {
        state.set(PluginState.DISCOVERED);

        // Create blocking executor for Kafka operations
        this.blockingExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "kafka-blocking");
            t.setDaemon(true);
            return t;
        });

        // Create eventloop if not injected
        if (this.eventloop == null) {
            this.eventloop = Eventloop.builder()
                    .withCurrentThread()
                    .build();
        }

        return Promise.ofBlocking(blockingExecutor, () -> {
            LOG.info("Initializing Kafka streaming plugin with bootstrap servers: {}",
                    config.getBootstrapServers());

            config.validate();

            // Initialize metrics
            initMetrics();

            // Create Kafka admin client
            Properties adminProps = new Properties();
            adminProps.put("bootstrap.servers", config.getBootstrapServers());
            config.toProducerProperties().forEach((k, v) -> {
                String key = k.toString();
                if (key.startsWith("security.") || key.startsWith("sasl.")
                        || key.startsWith("ssl.")) {
                    adminProps.put(k, v);
                }
            });
            adminClient = AdminClient.create(adminProps);

            // Create producer
            producer = new KafkaProducer<>(config.toProducerProperties());

            // Initialize transaction for exactly-once
            if (config.isExactlyOnceEnabled()) {
                producer.initTransactions();
                LOG.info("Kafka transactional producer initialized");
            }

            // Create consumer executor
            consumerExecutor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "kafka-consumer");
                t.setDaemon(true);
                return t;
            });

            running.set(true);
            state.set(PluginState.INITIALIZED);
            LOG.info("Kafka streaming plugin initialized successfully");
            return null;
        });
    }

    @Override
    public Promise<Void> start() {
        state.set(PluginState.STARTED);
        LOG.info("Kafka streaming plugin started");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        return Promise.ofBlocking(blockingExecutor, () -> {
            LOG.info("Stopping Kafka streaming plugin");
            state.set(PluginState.STOPPED);
            running.set(false);

            // Cancel all subscriptions
            for (KafkaSubscription subscription : subscriptions.values()) {
                subscription.close();
            }
            subscriptions.clear();

            // Shutdown consumer executor
            if (consumerExecutor != null) {
                consumerExecutor.shutdown();
                try {
                    if (!consumerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                        consumerExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    consumerExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            // Close producer
            if (producer != null) {
                producer.close(Duration.ofSeconds(30));
            }

            // Close admin client
            if (adminClient != null) {
                adminClient.close(Duration.ofSeconds(30));
            }

            LOG.info("Kafka streaming plugin stopped");
            return null;
        });
    }

    @Override
    public Promise<HealthStatus> healthCheck() {
        return Promise.ofBlocking(blockingExecutor, () -> {
            try {
                // Check if we can list topics
                adminClient.listTopics().names().get(5, TimeUnit.SECONDS);
                return HealthStatus.ok("Kafka cluster reachable");
            } catch (Exception e) {
                LOG.warn("Kafka health check failed: {}", e.getMessage());
                return HealthStatus.error("Kafka health check failed", e);
            }
        });
    }

    @Override
    public Promise<Void> shutdown() {
        return stop().then(() -> {
            consumerGroupManager.shutdown();
            if (blockingExecutor != null) {
                blockingExecutor.shutdown();
            }
            state.set(PluginState.UNLOADED);
            LOG.info("Kafka streaming plugin shut down");
            return Promise.complete();
        });
    }

    // ==================== Subscribe Operations ====================
    @Override
    public Promise<Subscription> subscribe(
            String tenantId,
            String streamName,
            PartitionId partitionId,
            Offset startOffset,
            Consumer<Event> consumer) {

        String topic = config.buildTopicName(tenantId, streamName);
        String subscriptionId = generateSubscriptionId(tenantId, streamName, null);

        return ensureTopicExists(topic)
                .then(() -> Promise.ofBlocking(blockingExecutor, () -> {
            LOG.info("Creating subscription for topic: {}, partition: {}, offset: {}",
                    topic, partitionId, startOffset);

            Properties props = config.toConsumerProperties(subscriptionId);
            KafkaConsumer<String, byte[]> kafkaConsumer = new KafkaConsumer<>(props);

            // Assign specific partition(s)
            List<TopicPartition> partitions = new ArrayList<>();
            if (partitionId == null || partitionId.equals(PartitionId.ALL)) {
                // Get all partitions
                kafkaConsumer.partitionsFor(topic).forEach(info
                        -> partitions.add(new TopicPartition(topic, info.partition())));
            } else {
                partitions.add(new TopicPartition(topic, partitionId.value()));
            }
            kafkaConsumer.assign(partitions);

            // Seek to start offset
            seekToOffset(kafkaConsumer, partitions, startOffset);

            KafkaSubscription subscription = new KafkaSubscription(
                    subscriptionId, kafkaConsumer, consumer, topic, tenantId, false);
            subscriptions.put(subscriptionId, subscription);

            // Start consumer loop
            consumerExecutor.submit(subscription::run);

            return (Subscription) subscription;
        }));
    }

    @Override
    public Promise<Subscription> subscribeWithGroup(
            String tenantId,
            String streamName,
            String consumerGroup,
            Consumer<Event> consumer) {

        String topic = config.buildTopicName(tenantId, streamName);
        String groupId = config.buildConsumerGroupId(tenantId, consumerGroup);
        String subscriptionId = generateSubscriptionId(tenantId, streamName, consumerGroup);

        return ensureTopicExists(topic)
                .then(() -> Promise.ofBlocking(blockingExecutor, () -> {
            LOG.info("Creating consumer group subscription for topic: {}, group: {}",
                    topic, groupId);

            Properties props = config.toConsumerProperties(groupId);
            KafkaConsumer<String, byte[]> kafkaConsumer = new KafkaConsumer<>(props);

            // Subscribe (allows dynamic partition assignment)
            kafkaConsumer.subscribe(Collections.singletonList(topic));

            KafkaSubscription subscription = new KafkaSubscription(
                    subscriptionId, kafkaConsumer, consumer, topic, tenantId, true);
            subscriptions.put(subscriptionId, subscription);

            // Register with consumer group manager
            consumerGroupManager.registerConsumer(groupId, subscriptionId);

            // Start consumer loop
            consumerExecutor.submit(subscription::run);

            return (Subscription) subscription;
        }));
    }

    @Override
    public Promise<Subscription> subscribeBatch(
            String tenantId,
            String streamName,
            SubscriptionOptions options,
            Consumer<List<Event>> batchConsumer) {

        String topic = config.buildTopicName(tenantId, streamName);
        String groupId = options.consumerGroup() != null
                ? config.buildConsumerGroupId(tenantId, options.consumerGroup())
                : generateSubscriptionId(tenantId, streamName, null);
        String subscriptionId = generateSubscriptionId(tenantId, streamName, options.consumerGroup());

        return ensureTopicExists(topic)
                .then(() -> Promise.ofBlocking(blockingExecutor, () -> {
            LOG.info("Creating batch subscription for topic: {}, batchSize: {}",
                    topic, options.batchSize());

            Properties props = config.toConsumerProperties(groupId);
            props.put("max.poll.records", options.batchSize());

            KafkaConsumer<String, byte[]> kafkaConsumer = new KafkaConsumer<>(props);

            if (options.consumerGroup() != null) {
                kafkaConsumer.subscribe(Collections.singletonList(topic));
            } else {
                List<TopicPartition> partitions = new ArrayList<>();
                if (options.partitionId() == null || options.partitionId().equals(PartitionId.ALL)) {
                    kafkaConsumer.partitionsFor(topic).forEach(info
                            -> partitions.add(new TopicPartition(topic, info.partition())));
                } else {
                    partitions.add(new TopicPartition(topic, options.partitionId().value()));
                }
                kafkaConsumer.assign(partitions);
                seekToOffset(kafkaConsumer, partitions, options.startOffset());
            }

            KafkaBatchSubscription subscription = new KafkaBatchSubscription(
                    subscriptionId, kafkaConsumer, batchConsumer, topic, tenantId,
                    options.batchSize(), options.batchTimeout(),
                    options.autoCommit(), options.consumerGroup() != null);
            subscriptions.put(subscriptionId, subscription);

            consumerExecutor.submit(subscription::run);

            return (Subscription) subscription;
        }));
    }

    // ==================== Publish Operations ====================
    @Override
    public Promise<Void> publish(Event event) {
        String topic = config.buildTopicName(event.getTenantId(), event.getStreamName());
        byte[] value = eventSerializer.serialize(event);
        String key = event.getCorrelationId() != null ? event.getCorrelationId() : event.getId().toString();

        return Promise.ofBlocking(blockingExecutor, () -> {
            Timer.Sample sample = Timer.start(meterRegistry);

            try {
                ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, value);

                // Add event metadata as headers
                record.headers()
                        .add("event-id", event.getId().toString().getBytes())
                        .add("event-type", event.getEventTypeName().getBytes())
                        .add("tenant-id", event.getTenantId().getBytes());

                if (config.isExactlyOnceEnabled()) {
                    producer.beginTransaction();
                    try {
                        producer.send(record).get();
                        producer.commitTransaction();
                    } catch (Exception e) {
                        producer.abortTransaction();
                        throw e;
                    }
                } else {
                    producer.send(record).get();
                }

                publishedEvents.increment();
                sample.stop(publishLatency);

                LOG.debug("Published event {} to topic {}", event.getId(), topic);
                return null;
            } catch (Exception e) {
                publishErrors.increment();
                LOG.error("Failed to publish event {} to topic {}: {}", event.getId(), topic, e.getMessage());
                throw new RuntimeException("Failed to publish event", e);
            }
        });
    }

    @Override
    public Promise<Void> publishBatch(List<Event> events) {
        if (events.isEmpty()) {
            return Promise.complete();
        }

        return Promise.ofBlocking(blockingExecutor, () -> {
            Timer.Sample sample = Timer.start(meterRegistry);

            try {
                if (config.isExactlyOnceEnabled()) {
                    producer.beginTransaction();
                }

                try {
                    for (Event event : events) {
                        String topic = config.buildTopicName(event.getTenantId(), event.getStreamName());
                        byte[] value = eventSerializer.serialize(event);
                        String key = event.getCorrelationId() != null ? event.getCorrelationId() : event.getId().toString();

                        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, value);
                        record.headers()
                                .add("event-id", event.getId().toString().getBytes())
                                .add("event-type", event.getEventTypeName().getBytes())
                                .add("tenant-id", event.getTenantId().getBytes());

                        producer.send(record);
                    }

                    producer.flush();

                    if (config.isExactlyOnceEnabled()) {
                        producer.commitTransaction();
                    }

                    publishedEvents.increment(events.size());
                    sample.stop(publishLatency);

                    LOG.debug("Published {} events in batch", events.size());
                    return null;
                } catch (Exception e) {
                    if (config.isExactlyOnceEnabled()) {
                        producer.abortTransaction();
                    }
                    throw e;
                }
            } catch (Exception e) {
                publishErrors.increment(events.size());
                LOG.error("Failed to publish batch of {} events: {}", events.size(), e.getMessage());
                throw new RuntimeException("Failed to publish batch", e);
            }
        });
    }

    // ==================== Capabilities ====================
    @Override
    public Capabilities capabilities() {
        return new KafkaCapabilities();
    }

    // ==================== Helper Methods ====================
    private void initMetrics() {
        Tags tags = Tags.of("plugin", "kafka");

        publishedEvents = Counter.builder("eventcloud.kafka.published")
                .tags(tags)
                .description("Number of events published to Kafka")
                .register(meterRegistry);

        consumedEvents = Counter.builder("eventcloud.kafka.consumed")
                .tags(tags)
                .description("Number of events consumed from Kafka")
                .register(meterRegistry);

        publishErrors = Counter.builder("eventcloud.kafka.publish.errors")
                .tags(tags)
                .description("Number of publish errors")
                .register(meterRegistry);

        consumeErrors = Counter.builder("eventcloud.kafka.consume.errors")
                .tags(tags)
                .description("Number of consume errors")
                .register(meterRegistry);

        publishLatency = Timer.builder("eventcloud.kafka.publish.latency")
                .tags(tags)
                .description("Kafka publish latency")
                .register(meterRegistry);

        consumeLatency = Timer.builder("eventcloud.kafka.consume.latency")
                .tags(tags)
                .description("Kafka consume latency")
                .register(meterRegistry);
    }

    private Promise<Void> ensureTopicExists(String topic) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            try {
                if (!adminClient.listTopics().names().get(5, TimeUnit.SECONDS).contains(topic)) {
                    LOG.info("Creating topic: {}", topic);
                    NewTopic newTopic = new NewTopic(topic, config.getDefaultPartitions(),
                            config.getDefaultReplicationFactor());
                    newTopic.configs(Map.of(
                            "retention.ms", String.valueOf(config.getTopicRetention().toMillis())
                    ));
                    adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
                    LOG.info("Topic created: {}", topic);
                }
            } catch (Exception e) {
                LOG.warn("Could not ensure topic exists: {} - {}", topic, e.getMessage());
            }
            return null;
        });
    }

    private void seekToOffset(KafkaConsumer<String, byte[]> consumer,
            List<TopicPartition> partitions,
            Offset offset) {
        if (offset == null || offset.equals(Offset.LATEST)) {
            consumer.seekToEnd(partitions);
        } else if (offset.equals(Offset.EARLIEST)) {
            consumer.seekToBeginning(partitions);
        } else {
            for (TopicPartition partition : partitions) {
                consumer.seek(partition, offset.value());
            }
        }
    }

    private String generateSubscriptionId(String tenantId, String streamName, String consumerGroup) {
        if (consumerGroup != null) {
            return String.format("%s-%s-%s-%d", tenantId, streamName, consumerGroup, System.nanoTime());
        }
        return String.format("%s-%s-%d", tenantId, streamName, System.nanoTime());
    }

    // ==================== Inner Classes ====================
    /**
     * Kafka subscription implementation.
     */
    private class KafkaSubscription implements Subscription, Runnable {

        protected final String id;
        protected final KafkaConsumer<String, byte[]> consumer;
        private final Consumer<Event> eventConsumer;
        protected final String topic;
        protected final String tenantId;
        private final boolean useConsumerGroup;

        protected final AtomicBoolean active = new AtomicBoolean(true);
        protected final AtomicBoolean paused = new AtomicBoolean(false);

        KafkaSubscription(String id, KafkaConsumer<String, byte[]> consumer,
                Consumer<Event> eventConsumer, String topic,
                String tenantId, boolean useConsumerGroup) {
            this.id = id;
            this.consumer = consumer;
            this.eventConsumer = eventConsumer;
            this.topic = topic;
            this.tenantId = tenantId;
            this.useConsumerGroup = useConsumerGroup;
        }

        @Override
        public void run() {
            LOG.info("Starting consumer loop for subscription: {}", id);

            try {
                while (active.get() && running.get()) {
                    if (paused.get()) {
                        sleepQuietly(100);
                        continue;
                    }

                    ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(100));

                    for (ConsumerRecord<String, byte[]> record : records) {
                        try {
                            Event event = eventSerializer.deserialize(record.value());

                            eventloop.execute(() -> {
                                try {
                                    eventConsumer.accept(event);
                                    consumedEvents.increment();
                                } catch (Exception e) {
                                    consumeErrors.increment();
                                    LOG.error("Error processing event: {}", e.getMessage());
                                }
                            });
                        } catch (Exception e) {
                            consumeErrors.increment();
                            LOG.error("Error deserializing event from partition {} offset {}: {}",
                                    record.partition(), record.offset(), e.getMessage());
                        }
                    }
                }
            } catch (WakeupException e) {
                if (active.get()) {
                    LOG.warn("Consumer wakeup for subscription: {}", id);
                }
            } catch (Exception e) {
                LOG.error("Consumer error for subscription {}: {}", id, e.getMessage());
            } finally {
                consumer.close();
                LOG.info("Consumer closed for subscription: {}", id);
            }
        }

        protected void sleepQuietly(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public Promise<Void> pause() {
            paused.set(true);
            consumer.pause(consumer.assignment());
            LOG.info("Subscription paused: {}", id);
            return Promise.complete();
        }

        @Override
        public Promise<Void> resume() {
            consumer.resume(consumer.assignment());
            paused.set(false);
            LOG.info("Subscription resumed: {}", id);
            return Promise.complete();
        }

        @Override
        public boolean isActive() {
            return active.get() && !paused.get();
        }

        @Override
        public boolean isPaused() {
            return paused.get();
        }

        @Override
        public Promise<Map<PartitionId, Offset>> getCurrentOffsets() {
            return Promise.ofBlocking(blockingExecutor, () -> {
                Map<PartitionId, Offset> offsets = new HashMap<>();
                for (TopicPartition tp : consumer.assignment()) {
                    offsets.put(PartitionId.of(tp.partition()), Offset.of(consumer.position(tp)));
                }
                return offsets;
            });
        }

        @Override
        public Promise<Void> commitOffsets(Map<PartitionId, Offset> offsets) {
            return Promise.ofBlocking(blockingExecutor, () -> {
                Map<TopicPartition, OffsetAndMetadata> kafkaOffsets = new HashMap<>();
                for (Map.Entry<PartitionId, Offset> entry : offsets.entrySet()) {
                    TopicPartition tp = new TopicPartition(topic, entry.getKey().value());
                    kafkaOffsets.put(tp, new OffsetAndMetadata(entry.getValue().value()));
                }
                consumer.commitSync(kafkaOffsets);
                LOG.debug("Committed offsets for subscription: {}", id);
                return null;
            });
        }

        @Override
        public Promise<List<PartitionId>> getAssignedPartitions() {
            return Promise.ofBlocking(blockingExecutor, () -> {
                List<PartitionId> partitions = new ArrayList<>();
                for (TopicPartition tp : consumer.assignment()) {
                    partitions.add(PartitionId.of(tp.partition()));
                }
                return partitions;
            });
        }

        @Override
        public void close() {
            active.set(false);
            consumer.wakeup();
            subscriptions.remove(id);
            if (useConsumerGroup) {
                String[] parts = id.split("-");
                if (parts.length >= 3) {
                    consumerGroupManager.unregisterConsumer(
                            config.buildConsumerGroupId(tenantId, parts[2]), id);
                }
            }
            LOG.info("Subscription closed: {}", id);
        }
    }

    /**
     * Batch subscription implementation.
     */
    private class KafkaBatchSubscription extends KafkaSubscription {

        private final Consumer<List<Event>> batchConsumer;
        private final int batchSize;
        private final Duration batchTimeout;
        private final boolean autoCommit;

        KafkaBatchSubscription(String id, KafkaConsumer<String, byte[]> consumer,
                Consumer<List<Event>> batchConsumer, String topic,
                String tenantId, int batchSize, Duration batchTimeout,
                boolean autoCommit, boolean useConsumerGroup) {
            super(id, consumer, null, topic, tenantId, useConsumerGroup);
            this.batchConsumer = batchConsumer;
            this.batchSize = batchSize;
            this.batchTimeout = batchTimeout;
            this.autoCommit = autoCommit;
        }

        @Override
        public void run() {
            LOG.info("Starting batch consumer loop for subscription: {}", id);

            List<Event> batch = new ArrayList<>(batchSize);
            long lastFlush = System.currentTimeMillis();

            try {
                while (active.get() && running.get()) {
                    if (paused.get()) {
                        sleepQuietly(100);
                        continue;
                    }

                    ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(100));

                    for (ConsumerRecord<String, byte[]> record : records) {
                        try {
                            Event event = eventSerializer.deserialize(record.value());
                            batch.add(event);
                        } catch (Exception e) {
                            consumeErrors.increment();
                            LOG.error("Error deserializing event: {}", e.getMessage());
                        }
                    }

                    // Flush batch if size reached or timeout exceeded
                    long now = System.currentTimeMillis();
                    boolean sizeReached = batch.size() >= batchSize;
                    boolean timeoutReached = now - lastFlush >= batchTimeout.toMillis() && !batch.isEmpty();

                    if (sizeReached || timeoutReached) {
                        List<Event> toProcess = new ArrayList<>(batch);
                        batch.clear();
                        lastFlush = now;

                        eventloop.execute(() -> {
                            try {
                                batchConsumer.accept(toProcess);
                                consumedEvents.increment(toProcess.size());
                            } catch (Exception e) {
                                consumeErrors.increment(toProcess.size());
                                LOG.error("Error processing batch: {}", e.getMessage());
                            }
                        });

                        if (autoCommit) {
                            consumer.commitSync();
                        }
                    }
                }
            } catch (WakeupException e) {
                // Expected on close
            } catch (Exception e) {
                LOG.error("Batch consumer error: {}", e.getMessage());
            } finally {
                consumer.close();
                LOG.info("Batch consumer closed for subscription: {}", id);
            }
        }
    }

    /**
     * Kafka streaming capabilities.
     */
    private static class KafkaCapabilities implements Capabilities {

        @Override
        public boolean supportsBackpressure() {
            return true;
        }

        @Override
        public boolean supportsPartitionRebalancing() {
            return true;
        }

        @Override
        public boolean supportsConsumerGroups() {
            return true;
        }

        @Override
        public boolean supportsExactlyOnce() {
            return true;
        }

        @Override
        public int maxSubscribers() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int maxBatchSize() {
            return 10000;
        }
    }
}
