package com.ghatana.platform.messaging.strategy;

import com.ghatana.platform.messaging.config.RetryConfig;
import com.ghatana.platform.messaging.strategy.kafka.KafkaConsumerConfig;
import com.ghatana.platform.messaging.strategy.kafka.KafkaConsumerStrategy;
import com.ghatana.platform.messaging.strategy.kafka.KafkaProducerConfig;
import com.ghatana.platform.messaging.strategy.kafka.KafkaProducerStrategy;
import com.ghatana.platform.messaging.strategy.rabbitmq.RabbitMQConfig;
import com.ghatana.platform.messaging.strategy.rabbitmq.RabbitMQConsumerStrategy;
import com.ghatana.platform.messaging.strategy.sqs.SqsConfig;
import com.ghatana.platform.messaging.strategy.sqs.SqsConsumerStrategy;
import com.ghatana.platform.messaging.strategy.sqs.SqsProducerStrategy;
import com.rabbitmq.client.ConnectionFactory;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
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
import java.nio.charset.StandardCharsets;
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
@Testcontainers(disabledWithoutDocker = true) // GH-90000
@DisplayName("Broker-backed connector strategies")
class BrokerConnectorIntegrationTest extends EventloopTestBase {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
        DockerImageName.parse("apache/kafka-native:3.8.0"))
        .withStartupTimeout(Duration.ofMinutes(2)); // GH-90000

    @Container
    static final GenericContainer<?> RABBITMQ = new GenericContainer<>(
        DockerImageName.parse("rabbitmq:3.13-management-alpine"))
            .withExposedPorts(5672)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static final GenericContainer<?> LOCALSTACK = new GenericContainer<>(
        DockerImageName.parse("localstack/localstack:3.8"))
        .withEnv("SERVICES", "sqs") // GH-90000
        .withExposedPorts(4566) // GH-90000
        .waitingFor(Wait.forHttp("/_localstack/health").forPort(4566).forStatusCode(200))
        .withStartupTimeout(Duration.ofMinutes(2)); // GH-90000

    @Test
    @DisplayName("KafkaProducerStrategy sends records to Kafka")
    void shouldSendKafkaRecords() { // GH-90000
        String topic = "aep-producer-" + UUID.randomUUID(); // GH-90000
        KafkaProducerStrategy strategy = new KafkaProducerStrategy(KafkaProducerConfig.builder() // GH-90000
            .bootstrapServers(KAFKA.getBootstrapServers()) // GH-90000
            .topic(topic) // GH-90000
            .retryConfig(RetryConfig.NO_RETRY) // GH-90000
            .build()); // GH-90000

        runPromise(strategy::start); // GH-90000

        boolean sent = strategy.send(new QueueMessage("msg-1", "producer-payload", Map.of("x-test", "1"))); // GH-90000

        assertThat(sent).isTrue(); // GH-90000

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaConsumerProps("verify-" + topic))) { // GH-90000
            consumer.subscribe(List.of(topic)); // GH-90000
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10)); // GH-90000
            assertThat(records.count()).isGreaterThan(0); // GH-90000
            assertThat(records.iterator().next().value()).isEqualTo("producer-payload");
        }

        runPromise(strategy::stop); // GH-90000
    }

    @Test
    @DisplayName("KafkaConsumerStrategy consumes records from Kafka")
    void shouldConsumeKafkaRecords() throws Exception { // GH-90000
        String topic = "aep-consumer-" + UUID.randomUUID(); // GH-90000
        CountDownLatch received = new CountDownLatch(1); // GH-90000
        AtomicReference<String> bodyRef = new AtomicReference<>(); // GH-90000
        KafkaConsumerStrategy strategy = new KafkaConsumerStrategy(KafkaConsumerConfig.builder() // GH-90000
            .bootstrapServers(KAFKA.getBootstrapServers()) // GH-90000
            .topic(topic) // GH-90000
            .groupId("group-" + UUID.randomUUID()) // GH-90000
            .pollTimeoutMs(200) // GH-90000
            .retryConfig(RetryConfig.NO_RETRY) // GH-90000
            .build(), body -> { // GH-90000
                bodyRef.set(body); // GH-90000
                received.countDown(); // GH-90000
            });

        runPromise(strategy::start); // GH-90000
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(kafkaProducerProps())) { // GH-90000
            producer.send(new ProducerRecord<>(topic, "key-1", "consumer-payload")).get(10, TimeUnit.SECONDS); // GH-90000
            producer.flush(); // GH-90000
        } catch (Exception e) { // GH-90000
            throw new RuntimeException(e); // GH-90000
        }

        assertThat(received.await(10, TimeUnit.SECONDS)).isTrue(); // GH-90000
        assertThat(bodyRef.get()).isEqualTo("consumer-payload");

        runPromise(strategy::stop); // GH-90000
    }

    @Test
    @DisplayName("D-7: RabbitMQConsumerStrategy consumes queued messages")
    void shouldConsumeRabbitMqMessages() throws Exception { // GH-90000
        String queueName = "aep-rabbit-" + UUID.randomUUID();
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> bodyRef = new AtomicReference<>();

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBITMQ.getHost());
        factory.setPort(RABBITMQ.getMappedPort(5672));
        factory.setUsername("guest");
        factory.setPassword("guest");
        factory.setVirtualHost("/");

        RabbitMQConsumerStrategy strategy = new RabbitMQConsumerStrategy(rabbitConfig(queueName), body -> {
            bodyRef.set(body);
            received.countDown();
        });

        try (com.rabbitmq.client.Connection connection = factory.newConnection();
             com.rabbitmq.client.Channel channel = connection.createChannel()) {
            channel.queueDeclare(queueName, true, false, false, null);
        }

        runPromise(strategy::start);

        try (com.rabbitmq.client.Connection connection = factory.newConnection();
             com.rabbitmq.client.Channel channel = connection.createChannel()) {
            channel.basicPublish("", queueName, null, "rabbit-payload".getBytes(StandardCharsets.UTF_8));
        }

        assertThat(received.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(bodyRef.get()).isEqualTo("rabbit-payload");

        runPromise(strategy::stop);
    }

    @Test
    @DisplayName("SqsProducerStrategy sends messages to emulated SQS")
    void shouldSendSqsMessages() { // GH-90000
        String queueUrl = createQueue("aep-producer-queue-" + UUID.randomUUID()); // GH-90000
        SqsProducerStrategy strategy = new SqsProducerStrategy(sqsConfig(queueUrl)); // GH-90000

        runPromise(strategy::start); // GH-90000

        boolean sent = strategy.send(new QueueMessage("id-1", "sqs-producer-payload", Map.of("header", "value"))); // GH-90000

        assertThat(sent).isTrue(); // GH-90000
        assertThat(sqsClient().receiveMessage(ReceiveMessageRequest.builder() // GH-90000
                .queueUrl(queueUrl) // GH-90000
                .maxNumberOfMessages(1) // GH-90000
                .waitTimeSeconds(5) // GH-90000
                .messageAttributeNames("All")
                .build()) // GH-90000
            .messages()) // GH-90000
            .singleElement() // GH-90000
            .satisfies(message -> assertThat(message.body()).isEqualTo("sqs-producer-payload"));

        runPromise(strategy::stop); // GH-90000
    }

    @Test
    @DisplayName("SqsConsumerStrategy consumes messages from emulated SQS")
    void shouldConsumeSqsMessages() throws Exception { // GH-90000
        String queueUrl = createQueue("aep-consumer-queue-" + UUID.randomUUID()); // GH-90000
        CountDownLatch received = new CountDownLatch(1); // GH-90000
        AtomicReference<String> bodyRef = new AtomicReference<>(); // GH-90000
        SqsConsumerStrategy strategy = new SqsConsumerStrategy(sqsConfig(queueUrl), body -> { // GH-90000
            bodyRef.set(body); // GH-90000
            received.countDown(); // GH-90000
        });

        runPromise(strategy::start); // GH-90000
        sqsClient().sendMessage(SendMessageRequest.builder() // GH-90000
            .queueUrl(queueUrl) // GH-90000
            .messageBody("sqs-consumer-payload")
            .build()); // GH-90000

        assertThat(received.await(10, TimeUnit.SECONDS)).isTrue(); // GH-90000
        assertThat(bodyRef.get()).isEqualTo("sqs-consumer-payload");

        runPromise(strategy::stop); // GH-90000
    }

    private static Properties kafkaProducerProps() { // GH-90000
        Properties props = new Properties(); // GH-90000
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()); // GH-90000
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()); // GH-90000
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()); // GH-90000
        props.put(ProducerConfig.ACKS_CONFIG, "all"); // GH-90000
        return props;
    }

    private static Properties kafkaConsumerProps(String groupId) { // GH-90000
        Properties props = new Properties(); // GH-90000
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()); // GH-90000
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId); // GH-90000
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()); // GH-90000
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()); // GH-90000
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // GH-90000
        return props;
    }

    private static SqsConfig sqsConfig(String queueUrl) { // GH-90000
        return SqsConfig.builder() // GH-90000
            .queueUrl(queueUrl) // GH-90000
            .region("us-east-1")
            .accessKey("test")
            .secretKey("test")
            .endpointOverride(localstackEndpoint()) // GH-90000
            .waitTimeSeconds(1) // GH-90000
            .maxMessages(1) // GH-90000
            .retryConfig(RetryConfig.NO_RETRY) // GH-90000
            .build(); // GH-90000
    }

    private static RabbitMQConfig rabbitConfig(String queueName) {
        return RabbitMQConfig.builder()
            .host(RABBITMQ.getHost())
            .port(RABBITMQ.getMappedPort(5672))
            .username("guest")
            .password("guest")
            .queueName(queueName)
            .retryConfig(RetryConfig.NO_RETRY)
            .build();
    }

    private static String createQueue(String queueName) { // GH-90000
        return sqsClient().createQueue(CreateQueueRequest.builder().queueName(queueName).build()).queueUrl(); // GH-90000
    }

    private static SqsClient sqsClient() { // GH-90000
        return SqsClient.builder() // GH-90000
            .endpointOverride(URI.create(localstackEndpoint())) // GH-90000
            .region(Region.of("us-east-1"))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))) // GH-90000
            .build(); // GH-90000
    }

    private static String localstackEndpoint() { // GH-90000
        return "http://" + LOCALSTACK.getHost() + ":" + LOCALSTACK.getMappedPort(4566); // GH-90000
    }
}
