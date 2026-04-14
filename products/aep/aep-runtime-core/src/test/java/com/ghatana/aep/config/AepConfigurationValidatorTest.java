/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AepConfigurationValidator}.
 *
 * <p>Each nested class covers one validation section independently, using
 * {@link EnvConfig#fromMap(Map)} so no real system env is required.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AepConfigurationValidator semantic validation rules
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepConfigurationValidator")
class AepConfigurationValidatorTest {

    // =========================================================================
    //  Helper factories
    // =========================================================================

    /** Returns a base-level valid configuration map (all required variables set). */
    private static Map<String, String> validBase() {
        Map<String, String> m = new HashMap<>();
        m.put("AEP_DB_URL",       "jdbc:postgresql://localhost:5432/aep");
        m.put("AEP_DB_USERNAME",   "aep");
        m.put("AEP_DB_PASSWORD",   "correctPassword123");  // meets length requirement
        m.put("AEP_DB_POOL_SIZE",  "10");
        m.put("AEP_DC_BASE_URL",   "http://localhost:8085");
        m.put("APP_ENV",           "development");         // lenient password checks
        m.put("EVENT_CLOUD_TRANSPORT", "eventlog");
        return m;
    }

    private static AepConfigurationValidator validatorFor(Map<String, String> env) {
        return new AepConfigurationValidator(EnvConfig.fromMap(env), env);
    }

    // =========================================================================
    //  1. Happy path — everything valid
    // =========================================================================

    @Nested
    @DisplayName("Valid configuration")
    class ValidConfig {

        @Test
        @DisplayName("full minimal valid config passes with no errors")
        void validConfigNoErrors() {
            AepConfigurationValidator.ValidationResult result = validatorFor(validBase()).validate();
            assertThat(result.isValid()).isTrue();
            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("throwIfInvalid does not throw when valid")
        void throwIfInvalidNoThrowWhenValid() {
            AepConfigurationValidator.ValidationResult result = validatorFor(validBase()).validate();
            // Should not throw
            result.throwIfInvalid();
        }
    }

    // =========================================================================
    //  2. Database validation
    // =========================================================================

    @Nested
    @DisplayName("Database configuration")
    class DatabaseValidation {

        @Test
        @DisplayName("invalid DB URL prefix produces error")
        void invalidDbUrlSchemeProducesError() {
            Map<String, String> env = validBase();
            env.put("AEP_DB_URL", "mysql://localhost:3306/aep");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("AEP_DB_URL"));
        }

        @Test
        @DisplayName("blank DB username produces error")
        void blankDbUsernameProducesError() {
            Map<String, String> env = validBase();
            env.put("AEP_DB_USERNAME", "  ");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("AEP_DB_USERNAME"));
        }

        @Test
        @DisplayName("missing DB password produces error")
        void missingDbPasswordProducesError() {
            Map<String, String> env = validBase();
            env.remove("AEP_DB_PASSWORD");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("AEP_DB_PASSWORD"));
        }

        @Test
        @DisplayName("pool size 0 produces error")
        void poolSizeZeroProducesError() {
            Map<String, String> env = validBase();
            env.put("AEP_DB_POOL_SIZE", "0");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("AEP_DB_POOL_SIZE"));
        }

        @Test
        @DisplayName("pool size > 200 produces error")
        void poolSizeTooLargeProducesError() {
            Map<String, String> env = validBase();
            env.put("AEP_DB_POOL_SIZE", "201");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("AEP_DB_POOL_SIZE"));
        }

        @Test
        @DisplayName("pool size of 3 in development produces a warning but no error")
        void poolSizeSmallProducesWarning() {
            Map<String, String> env = validBase();
            env.put("AEP_DB_POOL_SIZE", "3");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.isValid()).isTrue();
            assertThat(result.warnings()).anyMatch(w -> w.contains("AEP_DB_POOL_SIZE"));
        }
    }

    // =========================================================================
    //  3. Event-cloud transport validation
    // =========================================================================

    @Nested
    @DisplayName("Event-cloud transport")
    class TransportValidation {

        @Test
        @DisplayName("invalid transport value produces error")
        void invalidTransportProducesError() {
            Map<String, String> env = validBase();
            env.put("EVENT_CLOUD_TRANSPORT", "kafka");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("EVENT_CLOUD_TRANSPORT"));
        }

        @Test
        @DisplayName("grpc transport with invalid endpoint produces error")
        void grpcTransportInvalidEndpointProducesError() {
            Map<String, String> env = validBase();
            env.put("EVENT_CLOUD_TRANSPORT", "grpc");
            env.put("AEP_GRPC_ENDPOINT", "not-a-valid-host-port");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("AEP_GRPC_ENDPOINT"));
        }

        @Test
        @DisplayName("grpc transport with valid host:port passes")
        void grpcTransportValidEndpointPasses() {
            Map<String, String> env = validBase();
            env.put("EVENT_CLOUD_TRANSPORT", "grpc");
            env.put("AEP_GRPC_ENDPOINT", "event-cloud.internal:50051");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.errors()).noneMatch(e -> e.contains("AEP_GRPC_ENDPOINT"));
        }

        @Test
        @DisplayName("http transport with invalid URL produces error")
        void httpTransportInvalidUrlProducesError() {
            Map<String, String> env = validBase();
            env.put("EVENT_CLOUD_TRANSPORT", "http");
            env.put("HTTP_INGRESS_ENDPOINT", "not-a-url");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("HTTP_INGRESS_ENDPOINT"));
        }
    }

    // =========================================================================
    //  4. Kafka validation
    // =========================================================================

    @Nested
    @DisplayName("Kafka configuration")
    class KafkaValidation {

        @Test
        @DisplayName("valid kafka broker list produces no error")
        void validBrokersNoError() {
            Map<String, String> env = validBase();
            env.put("KAFKA_BOOTSTRAP_SERVERS", "broker1:9092,broker2:9092");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.errors()).noneMatch(e -> e.contains("KAFKA_BOOTSTRAP_SERVERS"));
        }

        @Test
        @DisplayName("invalid broker address produces error")
        void invalidBrokerAddressProducesError() {
            Map<String, String> env = validBase();
            env.put("KAFKA_BOOTSTRAP_SERVERS", "broker-without-port");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("KAFKA_BOOTSTRAP_SERVERS"));
        }

        @Test
        @DisplayName("brokers set without consumer group produces warning")
        void brokersWithoutGroupProducesWarning() {
            Map<String, String> env = validBase();
            env.put("KAFKA_BOOTSTRAP_SERVERS", "broker1:9092");
            // No KAFKA_CONSUMER_GROUP
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.warnings()).anyMatch(w -> w.contains("KAFKA_CONSUMER_GROUP"));
        }
    }

    // =========================================================================
    //  5. SQS / S3 validation
    // =========================================================================

    @Nested
    @DisplayName("AWS S3/SQS configuration")
    class AwsValidation {

        @Test
        @DisplayName("invalid SQS region produces error")
        void invalidSqsRegionProducesError() {
            Map<String, String> env = validBase();
            env.put("SQS_QUEUE_NAME", "my-queue");
            env.put("SQS_REGION", "invalid-region-x");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("SQS_REGION"));
        }

        @Test
        @DisplayName("valid SQS config passes")
        void validSqsConfigPasses() {
            Map<String, String> env = validBase();
            env.put("SQS_QUEUE_NAME", "my-queue");
            env.put("SQS_REGION", "us-east-1");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.errors()).noneMatch(e -> e.contains("SQS"));
        }

        @Test
        @DisplayName("invalid S3 bucket name produces error")
        void invalidS3BucketProducesError() {
            Map<String, String> env = validBase();
            env.put("S3_BUCKET", "INVALID_UPPER_CASE");
            env.put("S3_REGION", "us-east-1");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("S3_BUCKET"));
        }

        @Test
        @DisplayName("valid S3 config passes")
        void validS3ConfigPasses() {
            Map<String, String> env = validBase();
            env.put("S3_BUCKET", "my-aep-bucket");
            env.put("S3_REGION", "eu-central-1");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.errors()).noneMatch(e -> e.contains("S3"));
        }
    }

    // =========================================================================
    //  6. APP_ENV validation
    // =========================================================================

    @Nested
    @DisplayName("APP_ENV validation")
    class AppEnvValidation {

        @Test
        @DisplayName("unknown APP_ENV value produces error")
        void unknownAppEnvProducesError() {
            Map<String, String> env = validBase();
            env.put("APP_ENV", "qa");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("APP_ENV"));
        }

        @Test
        @DisplayName("production APP_ENV with localhost DC URL produces a security warning")
        void productionLocalDcUrlProducesWarning() {
            Map<String, String> env = validBase();
            env.put("APP_ENV", "production");
            env.put("AEP_DC_BASE_URL", "http://localhost:8085");
            env.put("AEP_DB_PASSWORD", "StrongProductionPassword!42");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.warnings()).anyMatch(w -> w.contains("localhost"));
        }
    }

    // =========================================================================
    //  7. consolidation interval bounds
    // =========================================================================

    @Nested
    @DisplayName("Consolidation interval")
    class ConsolidationIntervalValidation {

        @Test
        @DisplayName("consolidation interval of 0 produces error")
        void consolidationIntervalZeroProducesError() {
            Map<String, String> env = validBase();
            env.put("AEP_CONSOLIDATION_INTERVAL_HOURS", "0");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("AEP_CONSOLIDATION_INTERVAL_HOURS"));
        }

        @Test
        @DisplayName("consolidation interval of 6 passes")
        void consolidationInterval6Passes() {
            Map<String, String> env = validBase();
            env.put("AEP_CONSOLIDATION_INTERVAL_HOURS", "6");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThat(result.errors()).noneMatch(e -> e.contains("AEP_CONSOLIDATION_INTERVAL_HOURS"));
        }
    }

    // =========================================================================
    //  8. Static helper method tests
    // =========================================================================

    @Nested
    @DisplayName("Static helper methods")
    class HelperMethods {

        @Test
        @DisplayName("isValidHttpUrl: valid HTTP URL returns true")
        void isValidHttpUrlValidHttp() {
            assertThat(AepConfigurationValidator.isValidHttpUrl("http://example.com/path")).isTrue();
        }

        @Test
        @DisplayName("isValidHttpUrl: valid HTTPS URL returns true")
        void isValidHttpUrlValidHttps() {
            assertThat(AepConfigurationValidator.isValidHttpUrl("https://vault.internal:8200")).isTrue();
        }

        @Test
        @DisplayName("isValidHttpUrl: non-URL string returns false")
        void isValidHttpUrlNotAUrl() {
            assertThat(AepConfigurationValidator.isValidHttpUrl("not-a-url")).isFalse();
        }

        @Test
        @DisplayName("isValidHttpUrl: null returns false")
        void isValidHttpUrlNull() {
            assertThat(AepConfigurationValidator.isValidHttpUrl(null)).isFalse();
        }

        @Test
        @DisplayName("isValidHostPort: valid host:port returns true")
        void isValidHostPortValidEntry() {
            assertThat(AepConfigurationValidator.isValidHostPort("broker1:9092")).isTrue();
        }

        @Test
        @DisplayName("isValidHostPort: missing port returns false")
        void isValidHostPortMissingPort() {
            assertThat(AepConfigurationValidator.isValidHostPort("broker1-only")).isFalse();
        }

        @Test
        @DisplayName("isValidHostPort: port 0 returns false")
        void isValidHostPortPortZero() {
            assertThat(AepConfigurationValidator.isValidHostPort("host:0")).isFalse();
        }

        @Test
        @DisplayName("isValidHostPort: port 65535 returns true")
        void isValidHostPortMaxPort() {
            assertThat(AepConfigurationValidator.isValidHostPort("host:65535")).isTrue();
        }

        @Test
        @DisplayName("isValidHostPort: port 65536 returns false")
        void isValidHostPortOverMaxPort() {
            assertThat(AepConfigurationValidator.isValidHostPort("host:65536")).isFalse();
        }

        @Test
        @DisplayName("isValidS3BucketName: valid bucket returns true")
        void isValidS3BucketNameValid() {
            assertThat(AepConfigurationValidator.isValidS3BucketName("my-valid-bucket")).isTrue();
        }

        @Test
        @DisplayName("isValidS3BucketName: uppercase bucket returns false")
        void isValidS3BucketNameUppercase() {
            assertThat(AepConfigurationValidator.isValidS3BucketName("MyBucket")).isFalse();
        }

        @Test
        @DisplayName("isValidS3BucketName: bucket shorter than 3 chars returns false")
        void isValidS3BucketNameTooShort() {
            assertThat(AepConfigurationValidator.isValidS3BucketName("ab")).isFalse();
        }

        @Test
        @DisplayName("isValidS3BucketName: bucket with consecutive hyphens returns false")
        void isValidS3BucketNameConsecutiveHyphens() {
            assertThat(AepConfigurationValidator.isValidS3BucketName("my--bucket")).isFalse();
        }
    }

    // =========================================================================
    //  9. ValidationResult record behaviour
    // =========================================================================

    @Nested
    @DisplayName("ValidationResult")
    class ValidationResultTests {

        @Test
        @DisplayName("throwIfInvalid throws when there are errors")
        void throwIfInvalidThrowsOnErrors() {
            Map<String, String> env = validBase();
            env.put("AEP_DB_URL", "bad-url");
            AepConfigurationValidator.ValidationResult result = validatorFor(env).validate();
            assertThatThrownBy(result::throwIfInvalid)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AEP configuration is invalid");
        }

        @Test
        @DisplayName("toString includes error and warning counts")
        void toStringIncludesCounts() {
            AepConfigurationValidator.ValidationResult result = validatorFor(validBase()).validate();
            assertThat(result.toString()).contains("valid=true")
                    .contains("errors=0");
        }
    }
}
