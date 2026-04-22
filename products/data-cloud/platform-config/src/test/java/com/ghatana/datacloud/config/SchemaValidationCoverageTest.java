package com.ghatana.datacloud.config;

import com.ghatana.datacloud.config.model.RawCollectionConfig;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Integration coverage for schema validation at YAML loading boundary
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Schema Validation Coverage Tests")
class SchemaValidationCoverageTest extends EventloopTestBase {

    @Test
    @DisplayName("DC-1: schema validation rejects unknown field type from loaded YAML")
    void shouldRejectUnknownSchemaTypeFromYamlBoundary() throws Exception {
        Path configRoot = Files.createTempDirectory("dc-config-test");
        Path tenantDir = configRoot.resolve("collections").resolve("tenant-alpha");
        Files.createDirectories(tenantDir);

        Path yamlFile = tenantDir.resolve("orders.yaml");
        Files.writeString(yamlFile, """
            apiVersion: v1
            kind: Collection
            metadata:
              name: orders
              namespace: tenant-alpha
            spec:
              recordType: ENTITY
              schema:
                version: 1.0
                fields:
                  - name: id
                    type: uuid
                  - name: payload
                    type: made-up-type
            """);

        ConfigLoader loader = new ConfigLoader(configRoot, Executors.newSingleThreadExecutor());
        RawCollectionConfig raw = runPromise(() -> loader.loadCollectionAsync("tenant-alpha", "orders"));

        ConfigValidator.ValidationResult result = new ConfigValidator().validate(raw);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("unknown type"));
    }

    @Test
    @DisplayName("DC-1: schema validation accepts valid index references from loaded YAML")
    void shouldAcceptValidSchemaAndIndexReferences() throws Exception {
        Path configRoot = Files.createTempDirectory("dc-config-valid-test");
        Path tenantDir = configRoot.resolve("collections").resolve("tenant-alpha");
        Files.createDirectories(tenantDir);

        Path yamlFile = tenantDir.resolve("customers.yaml");
        Files.writeString(yamlFile, """
            apiVersion: v1
            kind: Collection
            metadata:
              name: customers
              namespace: tenant-alpha
            spec:
              recordType: ENTITY
              schema:
                version: 1.0
                fields:
                  - name: id
                    type: uuid
                  - name: email
                    type: string
              indexes:
                - name: idx_email
                  fields: [email]
            """);

        ConfigLoader loader = new ConfigLoader(configRoot, Executors.newSingleThreadExecutor());
        RawCollectionConfig raw = runPromise(() -> loader.loadCollectionAsync("tenant-alpha", "customers"));

        ConfigValidator.ValidationResult result = new ConfigValidator().validate(raw);

        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).contains("spec.storage is not specified, using defaults");
        assertThat(result.isValid()).isTrue();
    }
}


