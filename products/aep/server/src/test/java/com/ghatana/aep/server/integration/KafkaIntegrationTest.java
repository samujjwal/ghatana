/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.integration;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kafka integration smoke test for the AEP Phase-1 Testcontainers suite.
 *
 * @doc.type class
 * @doc.purpose Verify AEP integration test wiring can reach a live Kafka container
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("KafkaIntegrationTest")
class KafkaIntegrationTest {

    @Container
    private static final KafkaContainer KAFKA =
        new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Test
    @DisplayName("can create and list a topic")
    void canCreateAndListTopic() throws Exception {
        try (AdminClient adminClient = AdminClient.create(Map.of(
            "bootstrap.servers", KAFKA.getBootstrapServers()
        ))) {
            adminClient.createTopics(java.util.List.of(new NewTopic("aep.integration.probe", 1, (short) 1)))
                .all()
                .get();

            assertThat(adminClient.listTopics().names().get()).contains("aep.integration.probe");
        }
    }
}
