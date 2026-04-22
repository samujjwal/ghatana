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
@DisplayName("DataCloudConfigValidator — startup config validation [GH-90000]")
class DataCloudConfigValidatorTest {

    // ==================== Valid configurations ====================

    @Test
    @DisplayName("all-defaults (no env vars set) — passes validation [GH-90000]")
    void allDefaults_passesValidation() { // GH-90000
        assertThatCode(() -> // GH-90000
                DataCloudConfigValidator.builder().build().validate()) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("explicit valid ports — passes validation [GH-90000]")
    void explicitValidPorts_passesValidation() { // GH-90000
        assertThatCode(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .httpPortStr("8080 [GH-90000]")
                        .grpcPortStr("9090 [GH-90000]")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("DB enabled with all credentials — passes [GH-90000]")
    void dbEnabledWithAllCredentials_passes() { // GH-90000
        assertThatCode(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .dbEnabled(true) // GH-90000
                        .dbUrl("jdbc:postgresql://localhost:5432/dc [GH-90000]")
                        .dbUser("dc_user [GH-90000]")
                        .dbPassword("s3cr3t [GH-90000]")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("AI enabled with all credentials — passes [GH-90000]")
    void aiEnabledWithAllCredentials_passes() { // GH-90000
        assertThatCode(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .aiEnabled(true) // GH-90000
                        .dbUrl("jdbc:postgresql://localhost:5432/dc [GH-90000]")
                        .dbUser("dc_user [GH-90000]")
                        .dbPassword("s3cr3t [GH-90000]")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("Kafka enabled with bootstrap servers — passes [GH-90000]")
    void kafkaEnabledWithBootstrap_passes() { // GH-90000
        assertThatCode(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .kafkaEnabled(true) // GH-90000
                        .kafkaBootstrap("kafka:9092 [GH-90000]")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    // ==================== Port violations ====================

    @Test
    @DisplayName("HTTP port out of range — fails with port violation [GH-90000]")
    void httpPortOutOfRange_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .httpPortStr("99999 [GH-90000]")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_HTTP_PORT [GH-90000]");
    }

    @Test
    @DisplayName("HTTP port not a number — fails [GH-90000]")
    void httpPortNotANumber_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .httpPortStr("not-a-port [GH-90000]")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_HTTP_PORT [GH-90000]");
    }

    @Test
    @DisplayName("HTTP and gRPC port collision — fails with collision message [GH-90000]")
    void httpAndGrpcPortCollision_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .httpPortStr("8080 [GH-90000]")
                        .grpcPortStr("8080 [GH-90000]")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("must not be the same [GH-90000]");
    }

    @Test
    @DisplayName("gRPC port zero — fails [GH-90000]")
    void grpcPortZero_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .grpcPortStr("0 [GH-90000]")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_GRPC_PORT [GH-90000]");
    }

    // ==================== Max connections violations ====================

    @Test
    @DisplayName("max connections negative — fails [GH-90000]")
    void maxConnectionsNegative_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .maxConnectionsStr("-1 [GH-90000]")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_MAX_CONNECTIONS [GH-90000]");
    }

    @Test
    @DisplayName("max connections not a number — fails [GH-90000]")
    void maxConnectionsNotANumber_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .maxConnectionsStr("many [GH-90000]")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_MAX_CONNECTIONS [GH-90000]");
    }

    // ==================== Instance ID ====================

    @Test
    @DisplayName("blank instance ID — fails [GH-90000]")
    void blankInstanceId_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .instanceId("    [GH-90000]")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_INSTANCE_ID [GH-90000]");
    }

    // ==================== DB missing credentials ====================

    @Test
    @DisplayName("DB enabled but no URL — fails [GH-90000]")
    void dbEnabledNoUrl_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .dbEnabled(true) // GH-90000
                        .dbUser("user [GH-90000]")
                        .dbPassword("pass [GH-90000]")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_DB_URL [GH-90000]");
    }

    @Test
    @DisplayName("DB enabled but no user — fails [GH-90000]")
    void dbEnabledNoUser_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .dbEnabled(true) // GH-90000
                        .dbUrl("jdbc:postgresql://localhost/dc [GH-90000]")
                        .dbPassword("pass [GH-90000]")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_DB_USER [GH-90000]");
    }

    @Test
    @DisplayName("AI enabled but no URL — fails [GH-90000]")
    void aiEnabledNoUrl_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .aiEnabled(true) // GH-90000
                        .dbUser("user [GH-90000]")
                        .dbPassword("pass [GH-90000]")
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_DB_URL [GH-90000]");
    }

    @Test
    @DisplayName("Multiple violations reported together [GH-90000]")
    void multipleViolations_allReported() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .httpPortStr("99999 [GH-90000]")          // violation 1
                        .maxConnectionsStr("-5 [GH-90000]")       // violation 2
                        .dbEnabled(true)               // violation 3,4,5 (url, user, pass) // GH-90000
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .satisfies(ex -> { // GH-90000
                    String msg = ex.getMessage(); // GH-90000
                    // Verify all violations are in the single exception
                    org.assertj.core.api.Assertions.assertThat(msg) // GH-90000
                            .contains("DATACLOUD_HTTP_PORT [GH-90000]")
                            .contains("DATACLOUD_MAX_CONNECTIONS [GH-90000]")
                            .contains("DATACLOUD_DB_URL [GH-90000]");
                });
    }

    // ==================== Dependent services ====================

    @Test
    @DisplayName("Kafka enabled but no bootstrap — fails [GH-90000]")
    void kafkaEnabledNoBootstrap_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .kafkaEnabled(true) // GH-90000
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_KAFKA_BOOTSTRAP [GH-90000]");
    }

    @Test
    @DisplayName("ClickHouse enabled but no host — fails [GH-90000]")
    void clickhouseEnabledNoHost_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .clickhouseEnabled(true) // GH-90000
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_CLICKHOUSE_HOST [GH-90000]");
    }

    @Test
    @DisplayName("OpenSearch enabled but no host — fails [GH-90000]")
    void opensearchEnabledNoHost_fails() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                DataCloudConfigValidator.builder() // GH-90000
                        .opensearchEnabled(true) // GH-90000
                        .build() // GH-90000
                        .validate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_OPENSEARCH_HOST [GH-90000]");
    }
}
