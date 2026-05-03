package com.ghatana.pipeline.registry.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CapabilitiesService}.
 *
 * <p>Validates that the capability discovery service returns well-formed capability
 * manifests for schema formats, connectors, encodings, and transforms. These manifests
 * drive dynamic UI configuration and integration gating in AEP deployments.
 *
 * @doc.type test
 * @doc.purpose Validates capability manifest structure for all four capability dimensions
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("CapabilitiesService")
class CapabilitiesServiceTest {

    private CapabilitiesService service;

    @BeforeEach
    void setUp() {
        service = new CapabilitiesService();
    }

    // =========================================================================
    // Schema Formats
    // =========================================================================

    @Nested
    @DisplayName("getSchemaFormats")
    class SchemaFormats {

        @Test
        @DisplayName("returns a non-empty list of schema formats")
        void returnsNonEmptyList() {
            Map<String, Object> result = service.getSchemaFormats();

            assertThat(result).containsKey("schemaFormats");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> formats = (List<Map<String, Object>>) result.get("schemaFormats");
            assertThat(formats).isNotEmpty();
        }

        @Test
        @DisplayName("each schema format has id, display, and enabled fields")
        void eachFormatHasRequiredFields() {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> formats =
                    (List<Map<String, Object>>) service.getSchemaFormats().get("schemaFormats");

            assertThat(formats).allSatisfy(format -> {
                assertThat(format).containsKeys("id", "display", "enabled");
                assertThat(format.get("id")).asString().isNotBlank();
                assertThat(format.get("display")).asString().isNotBlank();
                assertThat(format.get("enabled")).isInstanceOf(Boolean.class);
            });
        }

        @Test
        @DisplayName("includes JSON Schema as an enabled format")
        void includesJsonSchemaAsEnabled() {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> formats =
                    (List<Map<String, Object>>) service.getSchemaFormats().get("schemaFormats");

            boolean hasJsonSchema = formats.stream()
                    .anyMatch(f -> "JSON_SCHEMA".equals(f.get("id")) && Boolean.TRUE.equals(f.get("enabled")));
            assertThat(hasJsonSchema)
                    .as("JSON_SCHEMA must be an enabled schema format")
                    .isTrue();
        }

        @Test
        @DisplayName("includes Avro as an enabled format")
        void includesAvroAsEnabled() {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> formats =
                    (List<Map<String, Object>>) service.getSchemaFormats().get("schemaFormats");

            boolean hasAvro = formats.stream()
                    .anyMatch(f -> "AVRO".equals(f.get("id")) && Boolean.TRUE.equals(f.get("enabled")));
            assertThat(hasAvro)
                    .as("AVRO must be an enabled schema format")
                    .isTrue();
        }
    }

    // =========================================================================
    // Connectors
    // =========================================================================

    @Nested
    @DisplayName("getConnectors")
    class Connectors {

        @Test
        @DisplayName("returns a non-empty list of connectors")
        void returnsNonEmptyList() {
            Map<String, Object> result = service.getConnectors();

            assertThat(result).containsKey("connectors");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> connectors = (List<Map<String, Object>>) result.get("connectors");
            assertThat(connectors).isNotEmpty();
        }

        @Test
        @DisplayName("each connector has type, direction, and enabled fields")
        void eachConnectorHasRequiredFields() {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> connectors =
                    (List<Map<String, Object>>) service.getConnectors().get("connectors");

            assertThat(connectors).allSatisfy(connector -> {
                assertThat(connector).containsKeys("type", "direction", "enabled");
                assertThat(connector.get("type")).asString().isNotBlank();
                assertThat(connector.get("direction")).asString().isIn("INGRESS", "EGRESS", "BOTH");
                assertThat(connector.get("enabled")).isInstanceOf(Boolean.class);
            });
        }

        @Test
        @DisplayName("includes HTTP_INGRESS and HTTP_EGRESS as enabled connectors")
        void includesHttpConnectors() {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> connectors =
                    (List<Map<String, Object>>) service.getConnectors().get("connectors");

            boolean hasHttpIngress = connectors.stream()
                    .anyMatch(c -> "HTTP_INGRESS".equals(c.get("type")) && Boolean.TRUE.equals(c.get("enabled")));
            boolean hasHttpEgress = connectors.stream()
                    .anyMatch(c -> "HTTP_EGRESS".equals(c.get("type")) && Boolean.TRUE.equals(c.get("enabled")));

            assertThat(hasHttpIngress).as("HTTP_INGRESS connector must be enabled").isTrue();
            assertThat(hasHttpEgress).as("HTTP_EGRESS connector must be enabled").isTrue();
        }
    }

    // =========================================================================
    // Encodings
    // =========================================================================

    @Nested
    @DisplayName("getEncodings")
    class Encodings {

        @Test
        @DisplayName("returns a non-empty list of encodings")
        void returnsNonEmptyList() {
            Map<String, Object> result = service.getEncodings();

            assertThat(result).containsKey("encodings");
            @SuppressWarnings("unchecked")
            List<String> encodings = (List<String>) result.get("encodings");
            assertThat(encodings).isNotEmpty();
        }

        @Test
        @DisplayName("includes JSON as a supported encoding")
        void includesJson() {
            @SuppressWarnings("unchecked")
            List<String> encodings = (List<String>) service.getEncodings().get("encodings");

            assertThat(encodings).contains("JSON");
        }

        @Test
        @DisplayName("all encodings are non-blank strings")
        void allEncodingsAreNonBlank() {
            @SuppressWarnings("unchecked")
            List<String> encodings = (List<String>) service.getEncodings().get("encodings");

            assertThat(encodings).allSatisfy(enc -> assertThat(enc).isNotBlank());
        }
    }

    // =========================================================================
    // Transforms
    // =========================================================================

    @Nested
    @DisplayName("getTransforms")
    class Transforms {

        @Test
        @DisplayName("returns a non-empty list of transforms")
        void returnsNonEmptyList() {
            Map<String, Object> result = service.getTransforms();

            assertThat(result).containsKey("transforms");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> transforms = (List<Map<String, Object>>) result.get("transforms");
            assertThat(transforms).isNotEmpty();
        }

        @Test
        @DisplayName("each transform has id and description fields")
        void eachTransformHasRequiredFields() {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> transforms =
                    (List<Map<String, Object>>) service.getTransforms().get("transforms");

            assertThat(transforms).allSatisfy(transform -> {
                assertThat(transform).containsKeys("id", "description");
                assertThat(transform.get("id")).asString().isNotBlank();
                assertThat(transform.get("description")).asString().isNotBlank();
            });
        }

        @Test
        @DisplayName("includes uuid() as a built-in transform")
        void includesUuidTransform() {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> transforms =
                    (List<Map<String, Object>>) service.getTransforms().get("transforms");

            boolean hasUuid = transforms.stream()
                    .anyMatch(t -> "uuid()".equals(t.get("id")));
            assertThat(hasUuid)
                    .as("uuid() transform must be present in the capability manifest")
                    .isTrue();
        }

        @Test
        @DisplayName("includes now() as a built-in transform")
        void includesNowTransform() {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> transforms =
                    (List<Map<String, Object>>) service.getTransforms().get("transforms");

            boolean hasNow = transforms.stream()
                    .anyMatch(t -> "now()".equals(t.get("id")));
            assertThat(hasNow)
                    .as("now() transform must be present in the capability manifest")
                    .isTrue();
        }
    }

    // =========================================================================
    // Idempotency (capability results are stable across calls)
    // =========================================================================

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("schema formats are stable across consecutive calls")
        void schemaFormatsAreStable() {
            Map<String, Object> first = service.getSchemaFormats();
            Map<String, Object> second = service.getSchemaFormats();

            assertThat(first).isEqualTo(second);
        }

        @Test
        @DisplayName("connectors are stable across consecutive calls")
        void connectorsAreStable() {
            Map<String, Object> first = service.getConnectors();
            Map<String, Object> second = service.getConnectors();

            assertThat(first).isEqualTo(second);
        }
    }
}
