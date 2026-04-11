package com.ghatana.platform.messaging.strategy;

import com.ghatana.platform.messaging.config.RetryConfig;
import com.ghatana.platform.messaging.strategy.kafka.KafkaConsumerConfig;
import com.ghatana.platform.messaging.strategy.kafka.KafkaConsumerStrategy;
import com.ghatana.platform.messaging.strategy.kafka.KafkaProducerConfig;
import com.ghatana.platform.messaging.strategy.kafka.KafkaProducerStrategy;
import com.ghatana.platform.messaging.strategy.sqs.SqsConfig;
import com.ghatana.platform.messaging.strategy.sqs.SqsConsumerStrategy;
import com.ghatana.platform.messaging.strategy.sqs.SqsProducerStrategy;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Integration coverage for broker-backed AEP connector strategies using Testcontainers
 * @doc.layer product
 * @doc.pattern Test, Integration
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Broker-backed connector strategies")
class BrokerConnectorIntegrationTest extends EventloopTestBase {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
        DockerImageName.parse("apache/kafka-native:3.8.0"))
        .withStartupTimeout(Duration.ofMinutes(2));

    // RABBITMQ container disabled due to startup issues
    // The RabbitMQ test method is already disabled with @Disabled annotation
    // @Container
    // static final GenericContainer<?> RABBITMQ = new GenericContainer<>(
    //     DockerImageName.parse("rabbitmq:3.13-management-alpine"))
    //         .withExposedPorts(5672)
    //         .waitingFor(Wait.forListeningPort())
    //         .withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static final GenericContainer<?> LOCALSTACK = new GenericContainer<>(
        DockerImageName.parse("localstack/localstack:3.8"))
        .withEnv("SERVICES", "sqs")
        .withExposedPorts(4566)
        .waitingFor(Wait.forHttp("/_localstack/health").forPort(4566).forStatusCode(200))
        .withStartupTimeout(Duration.ofMinutes(2));

    @Test
    @DisplayName("KafkaProducerStrategy sends records to Kafka")
    void shouldSendKafkaRecords() {
        String topic = "aep-producer-" + UUID.randomUUID();
        KafkaProducerStrategy strategy = new KafkaProducerStrategy(KafkaProducerConfig.builder()
            .bootstrapServers(KAFKA.getBootstrapServers())
            .topic(topic)
            .retryConfig(RetryConfig.NO_RETRY)
            .build());

        runPromise(strategy::start);

        boolean sent = strategy.send(new QueueMessage("msg-1", "producer-payload", Map.of("x-test", "1")));

        assertThat(sent).isTrue();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaConsumerProps("verify-" + topic))) {
            consumer.subscribe(List.of(topic));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records.count()).isGreaterThan(0);
            assertThat(records.iterator().next().value()).isEqualTo("producer-payload");
        }

        runPromise(strategy::stop);
    }

    @Test
    @DisplayName("KafkaConsumerStrategy consumes records from Kafka")
    void shouldConsumeKafkaRecords() throws Exception {
        String topic = "aep-consumer-" + UUID.randomUUID();
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> bodyRef = new AtomicReference<>();
        KafkaConsumerStrategy strategy = new KafkaConsumerStrategy(KafkaConsumerConfig.builder()
            .bootstrapServers(KAFKA.getBootstrapServers())
            .topic(topic)
            .groupId("group-" + UUID.randomUUID())
            .pollTimeoutMs(200)
            .retryConfig(RetryConfig.NO_RETRY)
            .build(), body -> {
                bodyRef.set(body);
                received.countDown();
            });

        runPromise(strategy::start);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(kafkaProducerProps())) {
            producer.send(new ProducerRecord<>(topic, "key-1", "consumer-payload")).get(10, TimeUnit.SECONDS);
            producer.flush();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertThat(received.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(bodyRef.get()).isEqualTo("consumer-payload");

        runPromise(strategy::stop);
    }

    @Test
    @Disabled("RabbitMQ container connection issues - disabled until infrastructure is stable")
    @DisplayName("RabbitMQConsumerStrategy consumes queued messages")
    void shouldConsumeRabbitMqMessages() throws Exception {
        // Test body commented out due to disabled RABBITMQ container
        // String queueName = "aep-rabbit-" + UUID.randomUUID();
        // CountDownLatch received = new CountDownLatch(1);
        // AtomicReference<String> bodyRef = new AtomicReference<>();
        // RabbitMQConsumerStrategy strategy = new RabbitMQConsumerStrategy(RabbitMQConfig.builder()
        //     .host(RABBITMQ.getHost())
        //     .port(RABBITMQ.getMappedPort(5672))
        //     .username("guest")
        //     .password("guest")
        //     .queueName(queueName)
        //     .retryConfig(RetryConfig.NO_RETRY)
        //     .build(), body -> {
        //         bodyRef.set(body);
        //         received.countDown();
        //     });

        // runPromise(strategy::start);

        // ConnectionFactory factory = new ConnectionFactory();
        // factory.setHost(RABBITMQ.getHost());
        // factory.setPort(RABBITMQ.getMappedPort(5672));
        // factory.setUsername("guest");
        // factory.setPassword("guest");

        // try (com.rabbitmq.client.Connection connection = factory.newConnection();
        //      com.rabbitmq.client.Channel channel = connection.createChannel()) {
        //     channel.queueDeclare(queueName, true, false, false, null);
        //     channel.basicPublish("", queueName, null, "rabbit-payload".getBytes(StandardCharsets.UTF_8));
        // }

        // assertThat(received.await(10, TimeUnit.SECONDS)).isTrue();
        // assertThat(bodyRef.get()).isEqualTo("rabbit-payload");

        // runPromise(strategy::stop);
    }

    @Test
    @DisplayName("SqsProducerStrategy sends messages to emulated SQS")
    void shouldSendSqsMessages() {
        String queueUrl = createQueue("aep-producer-queue-" + UUID.randomUUID());
        SqsProducerStrategy strategy = new SqsProducerStrategy(sqsConfig(queueUrl));

        runPromise(strategy::start);

        boolean sent = strategy.send(new QueueMessage("id-1", "sqs-producer-payload", Map.of("header", "value")));

        assertThat(sent).isTrue();
        assertThat(sqsClient().receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(5)
                .messageAttributeNames("All")
                .build())
            .messages())
            .singleElement()
            .satisfies(message -> assertThat(message.body()).isEqualTo("sqs-producer-payload"));

        runPromise(strategy::stop);
    }

    @Test
    @DisplayName("SqsConsumerStrategy consumes messages from emulated SQS")
    void shouldConsumeSqsMessages() throws Exception {
        String queueUrl = createQueue("aep-consumer-queue-" + UUID.randomUUID());
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> bodyRef = new AtomicReference<>();
        SqsConsumerStrategy strategy = new SqsConsumerStrategy(sqsConfig(queueUrl), body -> {
            bodyRef.set(body);
            received.countDown();
        });

        runPromise(strategy::start);
        sqsClient().sendMessage(SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody("sqs-consumer-payload")
            .build());

        assertThat(received.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(bodyRef.get()).isEqualTo("sqs-consumer-payload");

        runPromise(strategy::stop);
    }

    private static Properties kafkaProducerProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return props;
    }

    private static Properties kafkaConsumerProps(String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }

    private static SqsConfig sqsConfig(String queueUrl) {
        return SqsConfig.builder()
            .queueUrl(queueUrl)
            .region("us-east-1")
            .accessKey("test")
            .secretKey("test")
            .endpointOverride(localstackEndpoint())
            .waitTimeSeconds(1)
            .maxMessages(1)
            .retryConfig(RetryConfig.NO_RETRY)
            .build();
    }

    private static String createQueue(String queueName) {
        return sqsClient().createQueue(CreateQueueRequest.builder().queueName(queueName).build()).queueUrl();
    }

    private static SqsClient sqsClient() {
        return SqsClient.builder()
            .endpointOverride(URI.create(localstackEndpoint()))
            .region(Region.of("us-east-1"))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
            .build();
    }

    private static String localstackEndpoint() {
        return "http://" + LOCALSTACK.getHost() + ":" + LOCALSTACK.getMappedPort(4566);
    }
}
