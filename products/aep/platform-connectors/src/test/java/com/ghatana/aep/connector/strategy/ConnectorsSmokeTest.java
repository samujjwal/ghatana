package com.ghatana.aep.connector.strategy;

import com.ghatana.aep.connector.strategy.http.HttpIngressConfig;
import com.ghatana.aep.connector.strategy.kafka.KafkaConsumerConfig;
import com.ghatana.aep.connector.strategy.kafka.KafkaProducerConfig;
import com.ghatana.aep.connector.strategy.rabbitmq.RabbitMQConfig;
import com.ghatana.aep.connector.strategy.s3.S3Config;
import com.ghatana.aep.connector.strategy.sqs.SqsConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for AEP Platform Connectors module.
 * Tests config models and QueueMessage — implementation classes
 * use compileOnly dependencies (Kafka, SQS, etc.) and are excluded.
 *
 * @doc.type class
 * @doc.purpose Smoke tests for connector config and model classes
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Platform Connectors — Smoke Tests")
class ConnectorsSmokeTest {

    @Test
    @DisplayName("QueueMessage can be constructed and fields accessed")
    void queueMessageFields() {
        var msg = new QueueMessage("msg-1", "payload body", Map.of("header1", "value1"));
        assertEquals("msg-1", msg.getId());
        assertEquals("payload body", msg.getBody());
        assertEquals("value1", msg.getHeaders().get("header1"));
        assertTrue(msg.getTimestamp() > 0);
    }

    @Test
    @DisplayName("KafkaProducerConfig defaults and setters work")
    void kafkaProducerConfigDefaults() {
        var config = new KafkaProducerConfig();
        config.setBootstrapServers("localhost:9092");
        config.setTopic("test-topic");
        assertEquals("localhost:9092", config.getBootstrapServers());
        assertEquals("test-topic", config.getTopic());
        assertEquals(3, config.getRetries());
        assertEquals(16384, config.getBatchSize());
    }

    @Test
    @DisplayName("KafkaConsumerConfig defaults and setters work")
    void kafkaConsumerConfigDefaults() {
        var config = new KafkaConsumerConfig();
        config.setBootstrapServers("localhost:9092");
        config.setTopic("events");
        config.setGroupId("test-group");
        assertEquals("events", config.getTopic());
        assertEquals("test-group", config.getGroupId());
        assertEquals(1000, config.getPollTimeoutMs());
    }

    @Test
    @DisplayName("SqsConfig defaults and setters work")
    void sqsConfigDefaults() {
        var config = new SqsConfig();
        config.setQueueUrl("https://sqs.us-east-1.amazonaws.com/123/queue");
        config.setRegion("us-east-1");
        assertEquals(10, config.getMaxMessages());
        assertEquals(20, config.getWaitTimeSeconds());
        assertNotNull(config.getQueueUrl());
    }

    @Test
    @DisplayName("RabbitMQConfig defaults and setters work")
    void rabbitMQConfigDefaults() {
        var config = new RabbitMQConfig();
        config.setHost("localhost");
        config.setQueueName("events");
        assertEquals(5672, config.getPort());
        assertEquals("/", config.getVirtualHost());
        assertEquals("localhost", config.getHost());
    }

    @Test
    @DisplayName("S3Config defaults and setters work")
    void s3ConfigDefaults() {
        var config = new S3Config();
        config.setBucketName("my-bucket");
        config.setRegion("us-west-2");
        assertEquals("my-bucket", config.getBucketName());
        assertEquals("", config.getPrefix());
    }

    @Test
    @DisplayName("HttpIngressConfig defaults and setters work")
    void httpIngressConfigDefaults() {
        var config = new HttpIngressConfig();
        config.setEndpoint("https://api.example.com");
        config.setPort(8080);
        config.setPath("/events");
        assertEquals("POST", config.getMethod());
        assertEquals(30000, config.getTimeoutMs());
        assertEquals(8080, config.getPort());
    }
}
