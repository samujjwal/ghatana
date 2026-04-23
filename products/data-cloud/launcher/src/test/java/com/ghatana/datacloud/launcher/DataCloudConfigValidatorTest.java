/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void allDefaults_passesValidation() { // GH-90000
        assertThatCode(() -> // GH-90000
                DataCloudConfigValidator.builder().build().validate()) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("explicit valid ports — passes validation")
    void explicitValidPorts_passesValidation() { // GH-90000
        assertThatCode(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .httpPortStr("8080")
                        .grpcPortStr("9090")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("DB enabled with all credentials — passes")
    void dbEnabledWithAllCredentials_passes() { // GH-90000
        assertThatCode(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .dbEnabled(true) // GH-90000
                        .dbUrl("jdbc:postgresql://localhost:5432/dc")
                        .dbUser("dc_user")
                        .dbPassword("s3cr3t")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("AI enabled with all credentials — passes")
    void aiEnabledWithAllCredentials_passes() { // GH-90000
        assertThatCode(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .aiEnabled(true) // GH-90000
                        .dbUrl("jdbc:postgresql://localhost:5432/dc")
                        .dbUser("dc_user")
                        .dbPassword("s3cr3t")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("Kafka enabled with bootstrap servers — passes")
    void kafkaEnabledWithBootstrap_passes() { // GH-90000
        assertThatCode(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .kafkaEnabled(true) // GH-90000
                        .kafkaBootstrap("kafka:9092")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    // ==================== Port violations ====================

    @Test
    @DisplayName("HTTP port out of range — fails with port violation")
    void httpPortOutOfRange_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .httpPortStr("99999")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_HTTP_PORT");
    }

    @Test
    @DisplayName("HTTP port not a number — fails")
    void httpPortNotANumber_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .httpPortStr("not-a-port")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_HTTP_PORT");
    }

    @Test
    @DisplayName("HTTP and gRPC port collision — fails with collision message")
    void httpAndGrpcPortCollision_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .httpPortStr("8080")
                        .grpcPortStr("8080")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("must not be the same");
    }

    @Test
    @DisplayName("gRPC port zero — fails")
    void grpcPortZero_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .grpcPortStr("0")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_GRPC_PORT");
    }

    // ==================== Max connections violations ====================

    @Test
    @DisplayName("max connections negative — fails")
    void maxConnectionsNegative_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .maxConnectionsStr("-1")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_MAX_CONNECTIONS");
    }

    @Test
    @DisplayName("max connections not a number — fails")
    void maxConnectionsNotANumber_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .maxConnectionsStr("many")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_MAX_CONNECTIONS");
    }

    // ==================== Instance ID ====================

    @Test
    @DisplayName("blank instance ID — fails")
    void blankInstanceId_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .instanceId("   ")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_INSTANCE_ID");
    }

    // ==================== DB missing credentials ====================

    @Test
    @DisplayName("DB enabled but no URL — fails")
    void dbEnabledNoUrl_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .dbEnabled(true) // GH-90000
                        .dbUser("user")
                        .dbPassword("pass")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_DB_URL");
    }

    @Test
    @DisplayName("DB enabled but no user — fails")
    void dbEnabledNoUser_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .dbEnabled(true) // GH-90000
                        .dbUrl("jdbc:postgresql://localhost/dc")
                        .dbPassword("pass")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_DB_USER");
    }

    @Test
    @DisplayName("AI enabled but no URL — fails")
    void aiEnabledNoUrl_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .aiEnabled(true) // GH-90000
                        .dbUser("user")
                        .dbPassword("pass")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_DB_URL");
    }

    @Test
    @DisplayName("Multiple violations reported together")
    void multipleViolations_allReported() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .httpPortStr("99999")          // violation 1
                        .maxConnectionsStr("-5")       // violation 2
                        .dbEnabled(true)               // violation 3,4,5 (url, user, pass) // GH-90000
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .satisfies(ex -> { // GH-90000
                    String msg = ex.getMessage(); // GH-90000
                    // Verify all violations are in the single exception
                    org.assertj.core.api.Assertions.assertThat(msg) // GH-90000
                            .contains("DATACLOUD_HTTP_PORT")
                            .contains("DATACLOUD_MAX_CONNECTIONS")
                            .contains("DATACLOUD_DB_URL");
                });
    }

    // ==================== Dependent services ====================

    @Test
    @DisplayName("Kafka enabled but no bootstrap — fails")
    void kafkaEnabledNoBootstrap_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .kafkaEnabled(true) // GH-90000
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_KAFKA_BOOTSTRAP");
    }

    @Test
    @DisplayName("ClickHouse enabled but no host — fails")
    void clickhouseEnabledNoHost_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .clickhouseEnabled(true) // GH-90000
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_CLICKHOUSE_HOST");
    }

    @Test
    @DisplayName("OpenSearch enabled but no host — fails")
    void opensearchEnabledNoHost_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .opensearchEnabled(true) // GH-90000
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_OPENSEARCH_HOST");
    }
}
