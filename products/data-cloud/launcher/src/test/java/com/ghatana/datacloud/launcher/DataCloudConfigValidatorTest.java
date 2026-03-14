/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DataCloudConfigValidator}.
 *
 * @doc.type class
 * @doc.purpose Verify fail-fast startup config validation rules
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudConfigValidator — startup config validation")
class DataCloudConfigValidatorTest {

    // ==================== Valid configurations ====================

    @Test
    @DisplayName("all-defaults (no env vars set) — passes validation")
    void allDefaults_passesValidation() {
        assertThatCode(() ->
                DataCloudConfigValidator.builder().build().validate())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("explicit valid ports — passes validation")
    void explicitValidPorts_passesValidation() {
        assertThatCode(() ->
                DataCloudConfigValidator.builder()
                        .httpPortStr("8080")
                        .grpcPortStr("9090")
                        .build()
                        .validate())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DB enabled with all credentials — passes")
    void dbEnabledWithAllCredentials_passes() {
        assertThatCode(() ->
                DataCloudConfigValidator.builder()
                        .dbEnabled(true)
                        .dbUrl("jdbc:postgresql://localhost:5432/dc")
                        .dbUser("dc_user")
                        .dbPassword("s3cr3t")
                        .build()
                        .validate())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Kafka enabled with bootstrap servers — passes")
    void kafkaEnabledWithBootstrap_passes() {
        assertThatCode(() ->
                DataCloudConfigValidator.builder()
                        .kafkaEnabled(true)
                        .kafkaBootstrap("kafka:9092")
                        .build()
                        .validate())
                .doesNotThrowAnyException();
    }

    // ==================== Port violations ====================

    @Test
    @DisplayName("HTTP port out of range — fails with port violation")
    void httpPortOutOfRange_fails() {
        assertThatThrownBy(() ->
                DataCloudConfigValidator.builder()
                        .httpPortStr("99999")
                        .build()
                        .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATACLOUD_HTTP_PORT");
    }

    @Test
    @DisplayName("HTTP port not a number — fails")
    void httpPortNotANumber_fails() {
        assertThatThrownBy(() ->
                DataCloudConfigValidator.builder()
                        .httpPortStr("not-a-port")
                        .build()
                        .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATACLOUD_HTTP_PORT");
    }

    @Test
    @DisplayName("HTTP and gRPC port collision — fails with collision message")
    void httpAndGrpcPortCollision_fails() {
        assertThatThrownBy(() ->
                DataCloudConfigValidator.builder()
                        .httpPortStr("8080")
                        .grpcPortStr("8080")
                        .build()
                        .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be the same");
    }

    @Test
    @DisplayName("gRPC port zero — fails")
    void grpcPortZero_fails() {
        assertThatThrownBy(() ->
                DataCloudConfigValidator.builder()
                        .grpcPortStr("0")
                        .build()
                        .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATACLOUD_GRPC_PORT");
    }

    // ==================== Max connections violations ====================

    @Test
    @DisplayName("max connections negative — fails")
    void maxConnectionsNegative_fails() {
        assertThatThrownBy(() ->
                DataCloudConfigValidator.builder()
                        .maxConnectionsStr("-1")
                        .build()
                        .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATACLOUD_MAX_CONNECTIONS");
    }

    @Test
    @DisplayName("max connections not a number — fails")
    void maxConnectionsNotANumber_fails() {
        assertThatThrownBy(() ->
                DataCloudConfigValidator.builder()
                        .maxConnectionsStr("many")
                        .build()
                        .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATACLOUD_MAX_CONNECTIONS");
    }

    // ==================== Instance ID ====================

    @Test
    @DisplayName("blank instance ID — fails")
    void blankInstanceId_fails() {
        assertThatThrownBy(() ->
                DataCloudConfigValidator.builder()
                        .instanceId("   ")
                        .build()
                        .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATACLOUD_INSTANCE_ID");
    }

    // ==================== DB missing credentials ====================

    @Test
    @DisplayName("DB enabled but no URL — fails")
    void dbEnabledNoUrl_fails() {
        assertThatThrownBy(() ->
                DataCloudConfigValidator.builder()
                        .dbEnabled(true)
                        .dbUser("user")
                        .dbPassword("pass")
                        .build()
                        .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATACLOUD_DB_URL");
    }

    @Test
    @DisplayName("DB enabled but no user — fails")
    void dbEnabledNoUser_fails() {
        assertThatThrownBy(() ->
                DataCloudConfigValidator.builder()
                        .dbEnabled(true)
                        .dbUrl("jdbc:postgresql://localhost/dc")
                        .dbPassword("pass")
                        .build()
                        .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATACLOUD_DB_USER");
    }

    @Test
    @DisplayName("Multiple violations reported together")
    void multipleViolations_allReported() {
        assertThatThrownBy(() ->
                DataCloudConfigValidator.builder()
                        .httpPortStr("99999")          // violation 1
                        .maxConnectionsStr("-5")       // violation 2
                        .dbEnabled(true)               // violation 3,4,5 (url, user, pass)
                        .build()
                        .validate())
                .isInstanceOf(IllegalStateException.class)
                .satisfies(ex -> {
                    String msg = ex.getMessage();
                    // Verify all violations are in the single exception
                    org.assertj.core.api.Assertions.assertThat(msg)
                            .contains("DATACLOUD_HTTP_PORT")
                            .contains("DATACLOUD_MAX_CONNECTIONS")
                            .contains("DATACLOUD_DB_URL");
                });
    }

    // ==================== Dependent services ====================

    @Test
    @DisplayName("Kafka enabled but no bootstrap — fails")
    void kafkaEnabledNoBootstrap_fails() {
        assertThatThrownBy(() ->
                DataCloudConfigValidator.builder()
                        .kafkaEnabled(true)
                        .build()
                        .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATACLOUD_KAFKA_BOOTSTRAP");
    }

    @Test
    @DisplayName("ClickHouse enabled but no host — fails")
    void clickhouseEnabledNoHost_fails() {
        assertThatThrownBy(() ->
                DataCloudConfigValidator.builder()
                        .clickhouseEnabled(true)
                        .build()
                        .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATACLOUD_CLICKHOUSE_HOST");
    }

    @Test
    @DisplayName("OpenSearch enabled but no host — fails")
    void opensearchEnabledNoHost_fails() {
        assertThatThrownBy(() ->
                DataCloudConfigValidator.builder()
                        .opensearchEnabled(true)
                        .build()
                        .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATACLOUD_OPENSEARCH_HOST");
    }
}
