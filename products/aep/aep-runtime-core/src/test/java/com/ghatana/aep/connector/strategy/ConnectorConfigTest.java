package com.ghatana.aep.connector.strategy;

import com.ghatana.aep.connector.strategy.http.HttpIngressConfig;
import com.ghatana.aep.connector.strategy.http.HttpIngressStrategy;
import com.ghatana.aep.connector.strategy.rabbitmq.RabbitMQConfig;
import com.ghatana.aep.connector.strategy.s3.S3Config;
import com.ghatana.aep.connector.strategy.sqs.SqsConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for connector configuration builders.
 *
 * @doc.type test
 * @doc.purpose Verify required-field validation and default values for each connector config
 * @doc.layer product
 * @doc.pattern BuilderValidationTest
 */
@DisplayName("Connector Configuration Builder Tests")
class ConnectorConfigTest {

    // ======================================================================== RabbitMQConfig

    @Nested
    @DisplayName("RabbitMQConfig")
    class RabbitMQConfigTests {

        private RabbitMQConfig minimalValid() {
            return RabbitMQConfig.builder()
                    .username("guest")
                    .password("guest")
                    .queueName("my-queue")
                    .build();
        }

        @Test
        @DisplayName("builds successfully with required fields only")
        void shouldBuildWithRequiredFields() {
            RabbitMQConfig cfg = minimalValid();

            assertThat(cfg.getUsername()).isEqualTo("guest");
            assertThat(cfg.getPassword()).isEqualTo("guest");
            assertThat(cfg.getQueueName()).isEqualTo("my-queue");
        }

        @Test
        @DisplayName("applies default host=localhost and port=5672")
        void shouldApplyDefaultHostAndPort() {
            RabbitMQConfig cfg = minimalValid();

            assertThat(cfg.getHost()).isEqualTo("localhost");
            assertThat(cfg.getPort()).isEqualTo(5672);
        }

        @Test
        @DisplayName("applies default virtualHost=/")
        void shouldApplyDefaultVirtualHost() {
            assertThat(minimalValid().getVirtualHost()).isEqualTo("/");
        }

        @Test
        @DisplayName("applies default prefetchCount=10 and batchSize=10")
        void shouldApplyDefaultPrefetchAndBatchSize() {
            RabbitMQConfig cfg = minimalValid();

            assertThat(cfg.getPrefetchCount()).isEqualTo(10);
            assertThat(cfg.getBatchSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("applies default durable=true, exclusive=false, autoDelete=false")
        void shouldApplyDefaultQueueFlags() {
            RabbitMQConfig cfg = minimalValid();

            assertThat(cfg.isDurable()).isTrue();
            assertThat(cfg.isExclusive()).isFalse();
            assertThat(cfg.isAutoDelete()).isFalse();
        }

        @Test
        @DisplayName("builder overrides host, port, and virtualHost correctly")
        void shouldOverrideConnectionSettings() {
            RabbitMQConfig cfg = RabbitMQConfig.builder()
                    .host("rabbitmq.internal")
                    .port(5671)
                    .virtualHost("production")
                    .username("svc-account")
                    .password("s3cr3t")
                    .queueName("events")
                    .build();

            assertThat(cfg.getHost()).isEqualTo("rabbitmq.internal");
            assertThat(cfg.getPort()).isEqualTo(5671);
            assertThat(cfg.getVirtualHost()).isEqualTo("production");
        }

        @Test
        @DisplayName("requires username — throws NullPointerException when absent")
        void shouldThrowWhenUsernameMissing() {
            assertThatNullPointerException()
                    .isThrownBy(() -> RabbitMQConfig.builder()
                            .password("guest")
                            .queueName("q")
                            .build())
                    .withMessageContaining("username");
        }

        @Test
        @DisplayName("requires password — throws NullPointerException when absent")
        void shouldThrowWhenPasswordMissing() {
            assertThatNullPointerException()
                    .isThrownBy(() -> RabbitMQConfig.builder()
                            .username("guest")
                            .queueName("q")
                            .build())
                    .withMessageContaining("password");
        }

        @Test
        @DisplayName("requires queueName — throws NullPointerException when absent")
        void shouldThrowWhenQueueNameMissing() {
            assertThatNullPointerException()
                    .isThrownBy(() -> RabbitMQConfig.builder()
                            .username("guest")
                            .password("guest")
                            .build())
                    .withMessageContaining("queueName");
        }

        @Test
        @DisplayName("customises exchangeName and routingKey")
        void shouldAcceptExchangeAndRoutingKey() {
            RabbitMQConfig cfg = RabbitMQConfig.builder()
                    .username("u")
                    .password("p")
                    .queueName("q")
                    .exchangeName("events-exchange")
                    .routingKey("event.created")
                    .build();

            assertThat(cfg.getExchangeName()).isEqualTo("events-exchange");
            assertThat(cfg.getRoutingKey()).isEqualTo("event.created");
        }
    }

    // ======================================================================== S3Config

    @Nested
    @DisplayName("S3Config")
    class S3ConfigTests {

        private S3Config minimalValid() {
            return S3Config.builder()
                    .region("us-east-1")
                    .bucketName("my-bucket")
                    .build();
        }

        @Test
        @DisplayName("builds successfully with required fields only")
        void shouldBuildWithRequiredFields() {
            S3Config cfg = minimalValid();

            assertThat(cfg.getRegion()).isEqualTo("us-east-1");
            assertThat(cfg.getBucketName()).isEqualTo("my-bucket");
        }

        @Test
        @DisplayName("applies default prefix=empty string")
        void shouldApplyDefaultEmptyPrefix() {
            assertThat(minimalValid().getPrefix()).isEqualTo("");
        }

        @Test
        @DisplayName("applies default maxKeysPerRequest=1000")
        void shouldApplyDefaultMaxKeys() {
            assertThat(minimalValid().getMaxKeysPerRequest()).isEqualTo(1000);
        }

        @Test
        @DisplayName("applies default pollInterval=30s")
        void shouldApplyDefaultPollInterval() {
            assertThat(minimalValid().getPollInterval()).isEqualTo(Duration.ofSeconds(30));
        }

        @Test
        @DisplayName("deleteAfterRead defaults to false")
        void shouldDefaultDeleteAfterReadToFalse() {
            assertThat(minimalValid().isDeleteAfterRead()).isFalse();
        }

        @Test
        @DisplayName("builder overrides prefix, maxKeys and pollInterval")
        void shouldOverrideOptionalFields() {
            S3Config cfg = S3Config.builder()
                    .region("eu-west-1")
                    .bucketName("archive")
                    .prefix("2024/")
                    .maxKeysPerRequest(500)
                    .pollInterval(Duration.ofMinutes(1))
                    .deleteAfterRead(true)
                    .build();

            assertThat(cfg.getPrefix()).isEqualTo("2024/");
            assertThat(cfg.getMaxKeysPerRequest()).isEqualTo(500);
            assertThat(cfg.getPollInterval()).isEqualTo(Duration.ofMinutes(1));
            assertThat(cfg.isDeleteAfterRead()).isTrue();
        }

        @Test
        @DisplayName("requires region — throws NullPointerException when absent")
        void shouldThrowWhenRegionMissing() {
            assertThatNullPointerException()
                    .isThrownBy(() -> S3Config.builder().bucketName("b").build())
                    .withMessageContaining("region");
        }

        @Test
        @DisplayName("requires bucketName — throws NullPointerException when absent")
        void shouldThrowWhenBucketNameMissing() {
            assertThatNullPointerException()
                    .isThrownBy(() -> S3Config.builder().region("us-east-1").build())
                    .withMessageContaining("bucketName");
        }
    }

    // ======================================================================== SqsConfig

    @Nested
    @DisplayName("SqsConfig")
    class SqsConfigTests {

        private SqsConfig minimalValid() {
            return SqsConfig.builder()
                    .region("us-east-1")
                    .queueName("my-queue")
                    .build();
        }

        @Test
        @DisplayName("builds successfully with required fields only")
        void shouldBuildWithRequiredFields() {
            SqsConfig cfg = minimalValid();

            assertThat(cfg.getRegion()).isEqualTo("us-east-1");
            assertThat(cfg.getQueueName()).isEqualTo("my-queue");
        }

        @Test
        @DisplayName("applies default batchSize=10 (SQS max)")
        void shouldApplyDefaultBatchSize() {
            assertThat(minimalValid().getBatchSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("applies default waitTimeSeconds=1 (long polling)")
        void shouldApplyDefaultWaitTime() {
            assertThat(minimalValid().getWaitTimeSeconds()).isEqualTo(1);
        }

        @Test
        @DisplayName("applies default visibilityTimeout=30s")
        void shouldApplyDefaultVisibilityTimeout() {
            assertThat(minimalValid().getVisibilityTimeout()).isEqualTo(30);
        }

        @Test
        @DisplayName("batchSize is capped at 10 (SQS limit)")
        void shouldCapBatchSizeAtSqsMaximum() {
            SqsConfig cfg = SqsConfig.builder()
                    .region("us-east-1")
                    .queueName("q")
                    .batchSize(100) // exceeds SQS max of 10
                    .build();

            assertThat(cfg.getBatchSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("queueUrl is optional and may be null")
        void queueUrlIsOptional() {
            assertThat(minimalValid().getQueueUrl()).isNull();
        }

        @Test
        @DisplayName("explicit queueUrl is preserved")
        void shouldPreserveExplicitQueueUrl() {
            SqsConfig cfg = SqsConfig.builder()
                    .region("us-east-1")
                    .queueName("q")
                    .queueUrl("https://sqs.us-east-1.amazonaws.com/123456/q")
                    .build();

            assertThat(cfg.getQueueUrl())
                    .isEqualTo("https://sqs.us-east-1.amazonaws.com/123456/q");
        }

        @Test
        @DisplayName("requires region — throws NullPointerException when absent")
        void shouldThrowWhenRegionMissing() {
            assertThatNullPointerException()
                    .isThrownBy(() -> SqsConfig.builder().queueName("q").build())
                    .withMessageContaining("region");
        }

        @Test
        @DisplayName("requires queueName — throws NullPointerException when absent")
        void shouldThrowWhenQueueNameMissing() {
            assertThatNullPointerException()
                    .isThrownBy(() -> SqsConfig.builder().region("us-east-1").build())
                    .withMessageContaining("queueName");
        }
    }

    // ======================================================================== HttpIngressConfig

    @Nested
    @DisplayName("HttpIngressConfig")
    class HttpIngressConfigTests {

        private HttpIngressConfig minimalValid() {
            return HttpIngressConfig.builder()
                    .endpoint("https://api.example.com/events")
                    .build();
        }

        @Test
        @DisplayName("builds successfully with required endpoint only")
        void shouldBuildWithEndpointOnly() {
            HttpIngressConfig cfg = minimalValid();

            assertThat(cfg.getEndpoint()).isEqualTo("https://api.example.com/events");
        }

        @Test
        @DisplayName("applies default authType=NONE")
        void shouldApplyDefaultAuthTypeNone() {
            assertThat(minimalValid().getAuthType()).isEqualTo(HttpIngressStrategy.AuthType.NONE);
        }

        @Test
        @DisplayName("applies default timeout=30s")
        void shouldApplyDefaultTimeout() {
            assertThat(minimalValid().getTimeout()).isEqualTo(Duration.ofSeconds(30));
        }

        @Test
        @DisplayName("applies default maxRetries=3")
        void shouldApplyDefaultMaxRetries() {
            assertThat(minimalValid().getMaxRetries()).isEqualTo(3);
        }

        @Test
        @DisplayName("applies default retryBackoff=1s")
        void shouldApplyDefaultRetryBackoff() {
            assertThat(minimalValid().getRetryBackoff()).isEqualTo(Duration.ofSeconds(1));
        }

        @Test
        @DisplayName("applies default contentType=application/json")
        void shouldApplyDefaultContentType() {
            assertThat(minimalValid().getContentType()).isEqualTo("application/json");
        }

        @Test
        @DisplayName("validateSsl defaults to true")
        void shouldDefaultValidateSslToTrue() {
            assertThat(minimalValid().isValidateSsl()).isTrue();
        }

        @Test
        @DisplayName("bearerAuth sets BEARER authType and adds Authorization header")
        void shouldSetBearerAuthCorrectly() {
            HttpIngressConfig cfg = HttpIngressConfig.builder()
                    .endpoint("https://api.example.com")
                    .bearerAuth("my-token-value")
                    .build();

            assertThat(cfg.getAuthType()).isEqualTo(HttpIngressStrategy.AuthType.BEARER);
            assertThat(cfg.getAuthHeaders())
                    .containsEntry("Authorization", "Bearer my-token-value");
        }

        @Test
        @DisplayName("basicAuth sets BASIC authType with Base64-encoded Authorization header")
        void shouldSetBasicAuthWithBase64Encoding() {
            HttpIngressConfig cfg = HttpIngressConfig.builder()
                    .endpoint("https://api.example.com")
                    .basicAuth("user", "pass")
                    .build();

            assertThat(cfg.getAuthType()).isEqualTo(HttpIngressStrategy.AuthType.BASIC);
            String authHeader = cfg.getAuthHeaders().get("Authorization");
            assertThat(authHeader).startsWith("Basic ");
            // Decode and verify
            String decoded = new String(java.util.Base64.getDecoder().decode(
                    authHeader.substring("Basic ".length())));
            assertThat(decoded).isEqualTo("user:pass");
        }

        @Test
        @DisplayName("apiKeyAuth sets API_KEY authType with custom header")
        void shouldSetApiKeyAuthCorrectly() {
            HttpIngressConfig cfg = HttpIngressConfig.builder()
                    .endpoint("https://api.example.com")
                    .apiKeyAuth("X-Api-Key", "secret-key-value")
                    .build();

            assertThat(cfg.getAuthType()).isEqualTo(HttpIngressStrategy.AuthType.API_KEY);
            assertThat(cfg.getAuthHeaders())
                    .containsEntry("X-Api-Key", "secret-key-value");
        }

        @Test
        @DisplayName("addHeader appends custom header without changing authType")
        void shouldAddCustomHeader() {
            HttpIngressConfig cfg = HttpIngressConfig.builder()
                    .endpoint("https://api.example.com")
                    .addHeader("X-Tenant-Id", "tenant-42")
                    .build();

            assertThat(cfg.getAuthHeaders()).containsEntry("X-Tenant-Id", "tenant-42");
            assertThat(cfg.getAuthType()).isEqualTo(HttpIngressStrategy.AuthType.NONE);
        }

        @Test
        @DisplayName("getAuthHeaders returns a defensive copy")
        void shouldReturnDefensiveCopyOfAuthHeaders() {
            HttpIngressConfig cfg = minimalValid();

            cfg.getAuthHeaders().put("X-Injected", "evil");

            assertThat(cfg.getAuthHeaders()).doesNotContainKey("X-Injected");
        }

        @Test
        @DisplayName("requires endpoint — throws NullPointerException when absent")
        void shouldThrowWhenEndpointMissing() {
            assertThatNullPointerException()
                    .isThrownBy(() -> HttpIngressConfig.builder().build())
                    .withMessageContaining("endpoint");
        }
    }
}
