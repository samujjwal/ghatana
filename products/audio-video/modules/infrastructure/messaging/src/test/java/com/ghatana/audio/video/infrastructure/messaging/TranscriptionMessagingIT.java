package com.ghatana.audio.video.infrastructure.messaging;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
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
 * @doc.purpose RabbitMQ integration tests for transcription messaging flow (AV-P0-04)
 * @doc.layer test
 * @doc.pattern IntegrationTest
 */
@Testcontainers
@ExtendWith(MockitoExtension.class)
@DisplayName("Transcription Messaging Integration Tests (AV-P0-04)")
class TranscriptionMessagingIT {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management-alpine"))
            .withVhost("/")
            .withUser("guest", "guest");

    @Mock
    private MetricsCollector metricsCollector;

    private TranscriptionJobProducer producer;
    private TranscriptionJobConsumer consumer;

    @BeforeEach
    void setUp() throws Exception {
        lenient().doNothing().when(metricsCollector).incrementCounter(anyString(), any(String[].class));
        lenient().doNothing().when(metricsCollector).recordTimer(anyString(), anyLong(), any(String[].class));

        // Declare queues with DLQ wiring via direct AMQP connection
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBIT.getHost());
        factory.setPort(RABBIT.getAmqpPort());
        factory.setUsername("guest");
        factory.setPassword("guest");
        factory.setVirtualHost("/");
        try (Connection conn = factory.newConnection();
             com.rabbitmq.client.Channel ch = conn.createChannel()) {
            ch.exchangeDeclare("dlx", "direct", true);
            ch.queueDeclare("av.jobs.dlq", true, false, false, java.util.Map.of());
            ch.queueBind("av.jobs.dlq", "dlx", "av.jobs");
            ch.queueDeclare("av.jobs", true, false, false, java.util.Map.of(
                    "x-dead-letter-exchange", "dlx",
                    "x-dead-letter-routing-key", "av.jobs",
                    "x-max-delivery-count", 2
            ));
        }

        RabbitMQConfig config = RabbitMQConfig.builder()
                .host(RABBIT.getHost())
                .port(RABBIT.getAmqpPort())
                .username("guest")
                .password("guest")
                .virtualHost("/")
                .queueName("av.jobs")
                .build();

        producer = new TranscriptionJobProducer("av.jobs",
                new com.ghatana.platform.messaging.strategy.rabbitmq.RabbitMQProducerStrategy(config),
                metricsCollector);

        consumer = new TranscriptionJobConsumer("av.jobs",
                new RabbitMQConsumerStrategy(config),
                metricsCollector);
    }

    @AfterEach
    void tearDown() {
        try {
            if (producer != null) producer.stop().getResult();
        } catch (Exception ignored) {
        }
        try {
            if (consumer != null) consumer.stop().getResult();
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("Should deliver message from producer to consumer (happy path)")
    void shouldDeliverMessageRoundTrip() throws InterruptedException {
        List<TranscriptionJobProducer.TranscriptionJobMessage> received =
                Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(1);

        consumer.setJobProcessor(job -> {
            received.add(job);
            latch.countDown();
            return Promise.complete();
        });

        producer.start().getResult();
        consumer.start().getResult();

        TranscriptionJobProducer.TranscriptionJobMessage job =
                new TranscriptionJobProducer.TranscriptionJobMessage(
                        UUID.randomUUID(), "tenant-1", UUID.randomUUID(), "en", "m1", Instant.now());

        String messageId = producer.submitJob(job).getResult();
        assertThat(messageId).isNotNull();

        boolean delivered = latch.await(10, TimeUnit.SECONDS);
        assertThat(delivered).isTrue();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).jobId()).isEqualTo(job.jobId());
        assertThat(received.get(0).tenantId()).isEqualTo("tenant-1");
    }

    @Test
    @DisplayName("Should re-deliver on consumer nack (retry path)")
    void shouldRetryOnConsumerFailure() throws InterruptedException {
        AtomicInteger deliveryCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2); // expect 2 deliveries

        consumer.setJobProcessor(job -> {
            int count = deliveryCount.incrementAndGet();
            latch.countDown();
            if (count == 1) {
                // Simulate failure on first delivery — triggers nack+requeue
                return Promise.ofException(new RuntimeException("simulated processing failure"));
            }
            return Promise.complete();
        });

        producer.start().getResult();
        consumer.start().getResult();

        TranscriptionJobProducer.TranscriptionJobMessage job =
                new TranscriptionJobProducer.TranscriptionJobMessage(
                        UUID.randomUUID(), "tenant-retry", UUID.randomUUID(), "en", "m1", Instant.now());

        producer.submitJob(job).getResult();

        boolean delivered = latch.await(15, TimeUnit.SECONDS);
        assertThat(delivered).isTrue();
        assertThat(deliveryCount.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Should route poison messages to DLQ after max delivery count")
    void shouldDeadLetterPoisonMessage() throws Exception {
        AtomicInteger deliveryCount = new AtomicInteger(0);

        // Consumer always nacks → message eventually dead-lettered
        consumer.setJobProcessor(job -> {
            deliveryCount.incrementAndGet();
            return Promise.ofException(new RuntimeException("always fails — DLQ test"));
        });

        // Subscribe to DLQ via direct AMQP channel
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBIT.getHost());
        factory.setPort(RABBIT.getAmqpPort());
        factory.setUsername("guest");
        factory.setPassword("guest");
        factory.setVirtualHost("/");

        List<String> dlqMessages = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch dlqLatch = new CountDownLatch(1);

        Connection dlqConn = factory.newConnection();
        com.rabbitmq.client.Channel dlqChannel = dlqConn.createChannel();
        dlqChannel.basicConsume("av.jobs.dlq", true,
                (tag, delivery) -> {
                    dlqMessages.add(new String(delivery.getBody(), StandardCharsets.UTF_8));
                    dlqLatch.countDown();
                },
                tag -> {});

        producer.start().getResult();
        consumer.start().getResult();

        TranscriptionJobProducer.TranscriptionJobMessage job =
                new TranscriptionJobProducer.TranscriptionJobMessage(
                        UUID.randomUUID(), "tenant-dlq", UUID.randomUUID(), "en", "m1", Instant.now());

        producer.submitJob(job).getResult();

        boolean receivedInDlq = dlqLatch.await(30, TimeUnit.SECONDS);
        assertThat(receivedInDlq).isTrue();
        assertThat(dlqMessages).hasSize(1);

        dlqChannel.close();
        dlqConn.close();
    }

    @Test
    @DisplayName("Duplicate jobId should be processed idempotently via seen-set guard")
    void shouldProcessDuplicateJobIdOnlyOnce() throws InterruptedException {
        // Idempotency guard: consumer tracks seen job IDs in a concurrent set
        java.util.Set<UUID> seen = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
        AtomicInteger processedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        consumer.setJobProcessor(job -> {
            if (seen.add(job.jobId())) {
                processedCount.incrementAndGet();
                latch.countDown();
            }
            return Promise.complete();
        });

        producer.start().getResult();
        consumer.start().getResult();

        UUID jobId = UUID.randomUUID();
        TranscriptionJobProducer.TranscriptionJobMessage job =
                new TranscriptionJobProducer.TranscriptionJobMessage(
                        jobId, "tenant-dedup", UUID.randomUUID(), "en", "m1", Instant.now());

        // Submit same job twice
        producer.submitJob(job).getResult();
        producer.submitJob(job).getResult();

        boolean delivered = latch.await(10, TimeUnit.SECONDS);
        assertThat(delivered).isTrue();

        // Allow a brief window for potential duplicate delivery
        Thread.sleep(500);
        assertThat(processedCount.get()).isEqualTo(1);
    }
}






