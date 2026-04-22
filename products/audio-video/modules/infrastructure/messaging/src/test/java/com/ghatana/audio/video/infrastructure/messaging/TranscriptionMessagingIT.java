package com.ghatana.audio.video.infrastructure.messaging;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.messaging.strategy.rabbitmq.RabbitMQConfig;
import com.ghatana.platform.messaging.strategy.rabbitmq.RabbitMQConsumerStrategy;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Integration tests for transcription job producer→consumer round-trip via RabbitMQ.
 *
 * <p>Covers:
 * <ul>
 *   <li>Happy-path: message produced → consumed, payload verified</li>
 *   <li>Retry: consumer nack → requeue → re-delivered</li>
 *   <li>DLQ: messages exceeding x-death-count are dead-lettered</li>
 *   <li>Idempotency: duplicate jobId is processed exactly once by a deduplication guard</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose RabbitMQ integration tests for transcription messaging flow (AV-P0-04) // GH-90000
 * @doc.layer test
 * @doc.pattern IntegrationTest
 */
@Testcontainers
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("Transcription Messaging Integration Tests (AV-P0-04) [GH-90000]")
class TranscriptionMessagingIT extends EventloopTestBase {

    private static final int MAX_DELIVERY_ATTEMPTS = 2;

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer( // GH-90000
            DockerImageName.parse("rabbitmq:3.13-management-alpine [GH-90000]"));

    @Mock
    private MetricsCollector metricsCollector;

    private TranscriptionJobProducer producer;
    private TranscriptionJobConsumer consumer;
    private String queueName;
    private String dlqName;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        lenient().doNothing().when(metricsCollector).incrementCounter(anyString(), any(String[].class)); // GH-90000
        lenient().doNothing().when(metricsCollector).recordTimer(anyString(), anyLong(), any(String[].class)); // GH-90000

        String queueSuffix = UUID.randomUUID().toString(); // GH-90000
        queueName = "av.jobs." + queueSuffix;
        dlqName = queueName + ".dlq";
        String deadLetterExchange = "dlx." + queueSuffix;

        // Declare queues with DLQ wiring via direct AMQP connection
        ConnectionFactory factory = new ConnectionFactory(); // GH-90000
        factory.setHost(RABBIT.getHost()); // GH-90000
        factory.setPort(RABBIT.getAmqpPort()); // GH-90000
        factory.setUsername("guest [GH-90000]");
        factory.setPassword("guest [GH-90000]");
        factory.setVirtualHost("/ [GH-90000]");
        try (Connection conn = factory.newConnection(); // GH-90000
             com.rabbitmq.client.Channel ch = conn.createChannel()) { // GH-90000
            ch.exchangeDeclare(deadLetterExchange, "direct", true); // GH-90000
            ch.queueDeclare(dlqName, true, false, false, java.util.Map.of()); // GH-90000
            ch.queueBind(dlqName, deadLetterExchange, queueName); // GH-90000
            ch.queueDeclare(queueName, true, false, false, java.util.Map.of( // GH-90000
                    "x-dead-letter-exchange", deadLetterExchange,
                    "x-dead-letter-routing-key", queueName
            ));
        }

        RabbitMQConfig config = RabbitMQConfig.builder() // GH-90000
                .host(RABBIT.getHost()) // GH-90000
                .port(RABBIT.getAmqpPort()) // GH-90000
                .username("guest [GH-90000]")
                .password("guest [GH-90000]")
                .virtualHost("/ [GH-90000]")
                .queueName(queueName) // GH-90000
                .maxDeliveryAttempts(MAX_DELIVERY_ATTEMPTS) // GH-90000
                .build(); // GH-90000

        producer = new TranscriptionJobProducer(queueName, // GH-90000
                new com.ghatana.platform.messaging.strategy.rabbitmq.RabbitMQProducerStrategy(config), // GH-90000
                metricsCollector);

        consumer = new TranscriptionJobConsumer(queueName, // GH-90000
                new RabbitMQConsumerStrategy(config), // GH-90000
                metricsCollector);
    }

    @AfterEach
    void tearDown() { // GH-90000
        try {
            if (producer != null) runPromise(() -> producer.stop()); // GH-90000
        } catch (Exception ignored) { // GH-90000
        }
        try {
            if (consumer != null) runPromise(() -> consumer.stop()); // GH-90000
        } catch (Exception ignored) { // GH-90000
        }
    }

    @Test
    @DisplayName("Should deliver message from producer to consumer (happy path) [GH-90000]")
    void shouldDeliverMessageRoundTrip() throws InterruptedException { // GH-90000
        List<TranscriptionJobProducer.TranscriptionJobMessage> received =
                Collections.synchronizedList(new ArrayList<>()); // GH-90000
        CountDownLatch latch = new CountDownLatch(1); // GH-90000

        consumer.setJobProcessor(job -> { // GH-90000
            received.add(job); // GH-90000
            latch.countDown(); // GH-90000
            return Promise.complete(); // GH-90000
        });

        runPromise(() -> producer.start()); // GH-90000
        runPromise(() -> consumer.start()); // GH-90000

        TranscriptionJobProducer.TranscriptionJobMessage job =
                new TranscriptionJobProducer.TranscriptionJobMessage( // GH-90000
                        UUID.randomUUID(), "tenant-1", UUID.randomUUID(), "en", "m1", Instant.now()); // GH-90000

        String messageId = runPromise(() -> producer.submitJob(job)); // GH-90000
        assertThat(messageId).isNotNull(); // GH-90000

        boolean delivered = latch.await(10, TimeUnit.SECONDS); // GH-90000
        assertThat(delivered).isTrue(); // GH-90000
        assertThat(received).hasSize(1); // GH-90000
        assertThat(received.get(0).jobId()).isEqualTo(job.jobId()); // GH-90000
        assertThat(received.get(0).tenantId()).isEqualTo("tenant-1 [GH-90000]");
    }

    @Test
    @DisplayName("Should re-deliver on consumer nack (retry path) [GH-90000]")
    void shouldRetryOnConsumerFailure() throws InterruptedException { // GH-90000
        AtomicInteger deliveryCount = new AtomicInteger(0); // GH-90000
        CountDownLatch latch = new CountDownLatch(2); // expect 2 deliveries // GH-90000

        consumer.setJobProcessor(job -> { // GH-90000
            int count = deliveryCount.incrementAndGet(); // GH-90000
            latch.countDown(); // GH-90000
            if (count == 1) { // GH-90000
                // Simulate failure on first delivery — triggers nack+requeue
                return Promise.ofException(new RuntimeException("simulated processing failure [GH-90000]"));
            }
            return Promise.complete(); // GH-90000
        });

        runPromise(() -> producer.start()); // GH-90000
        runPromise(() -> consumer.start()); // GH-90000

        TranscriptionJobProducer.TranscriptionJobMessage job =
                new TranscriptionJobProducer.TranscriptionJobMessage( // GH-90000
                        UUID.randomUUID(), "tenant-retry", UUID.randomUUID(), "en", "m1", Instant.now()); // GH-90000

        runPromise(() -> producer.submitJob(job)); // GH-90000

        boolean delivered = latch.await(15, TimeUnit.SECONDS); // GH-90000
        assertThat(delivered).isTrue(); // GH-90000
        assertThat(deliveryCount.get()).isGreaterThanOrEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("Should route poison messages to DLQ after max delivery count [GH-90000]")
    void shouldDeadLetterPoisonMessage() throws Exception { // GH-90000
        AtomicInteger deliveryCount = new AtomicInteger(0); // GH-90000

        // Consumer always fails → strategy retries up to MAX_DELIVERY_ATTEMPTS, then dead-letters
        consumer.setJobProcessor(job -> { // GH-90000
            deliveryCount.incrementAndGet(); // GH-90000
            return Promise.ofException(new RuntimeException("always fails — DLQ test [GH-90000]"));
        });

        // Subscribe to DLQ via direct AMQP channel
        ConnectionFactory factory = new ConnectionFactory(); // GH-90000
        factory.setHost(RABBIT.getHost()); // GH-90000
        factory.setPort(RABBIT.getAmqpPort()); // GH-90000
        factory.setUsername("guest [GH-90000]");
        factory.setPassword("guest [GH-90000]");
        factory.setVirtualHost("/ [GH-90000]");

        List<String> dlqMessages = Collections.synchronizedList(new ArrayList<>()); // GH-90000
        CountDownLatch dlqLatch = new CountDownLatch(1); // GH-90000

        Connection dlqConn = factory.newConnection(); // GH-90000
        com.rabbitmq.client.Channel dlqChannel = dlqConn.createChannel(); // GH-90000
        dlqChannel.basicConsume(dlqName, true, // GH-90000
                (tag, delivery) -> { // GH-90000
                    dlqMessages.add(new String(delivery.getBody(), StandardCharsets.UTF_8)); // GH-90000
                    dlqLatch.countDown(); // GH-90000
                },
                tag -> {});

        runPromise(() -> producer.start()); // GH-90000
        runPromise(() -> consumer.start()); // GH-90000

        TranscriptionJobProducer.TranscriptionJobMessage job =
                new TranscriptionJobProducer.TranscriptionJobMessage( // GH-90000
                        UUID.randomUUID(), "tenant-dlq", UUID.randomUUID(), "en", "m1", Instant.now()); // GH-90000

        runPromise(() -> producer.submitJob(job)); // GH-90000

        boolean receivedInDlq = dlqLatch.await(30, TimeUnit.SECONDS); // GH-90000
        assertThat(receivedInDlq).isTrue(); // GH-90000
        assertThat(dlqMessages).hasSize(1); // GH-90000
        assertThat(deliveryCount.get()).isEqualTo(MAX_DELIVERY_ATTEMPTS); // GH-90000

        dlqChannel.close(); // GH-90000
        dlqConn.close(); // GH-90000
    }

    @Test
    @DisplayName("Duplicate jobId should be processed idempotently via seen-set guard [GH-90000]")
    void shouldProcessDuplicateJobIdOnlyOnce() throws InterruptedException { // GH-90000
        // Idempotency guard: consumer tracks seen job IDs in a concurrent set
        java.util.Set<UUID> seen = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>()); // GH-90000
        AtomicInteger processedCount = new AtomicInteger(0); // GH-90000
        CountDownLatch latch = new CountDownLatch(1); // GH-90000

        consumer.setJobProcessor(job -> { // GH-90000
            if (seen.add(job.jobId())) { // GH-90000
                processedCount.incrementAndGet(); // GH-90000
                latch.countDown(); // GH-90000
            }
            return Promise.complete(); // GH-90000
        });

        runPromise(() -> producer.start()); // GH-90000
        runPromise(() -> consumer.start()); // GH-90000

        UUID jobId = UUID.randomUUID(); // GH-90000
        TranscriptionJobProducer.TranscriptionJobMessage job =
                new TranscriptionJobProducer.TranscriptionJobMessage( // GH-90000
                        jobId, "tenant-dedup", UUID.randomUUID(), "en", "m1", Instant.now()); // GH-90000

        // Submit same job twice
        runPromise(() -> producer.submitJob(job)); // GH-90000
        runPromise(() -> producer.submitJob(job)); // GH-90000

        boolean delivered = latch.await(10, TimeUnit.SECONDS); // GH-90000
        assertThat(delivered).isTrue(); // GH-90000

        // Allow a brief window for potential duplicate delivery
        Thread.sleep(500); // GH-90000
        assertThat(processedCount.get()).isEqualTo(1); // GH-90000
    }
}






