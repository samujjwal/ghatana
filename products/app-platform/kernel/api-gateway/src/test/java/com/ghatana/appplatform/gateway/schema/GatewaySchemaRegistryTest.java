/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.gateway.schema;

import com.ghatana.appplatform.gateway.schema.RequestSchemaValidator.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link GatewaySchemaRegistry}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for gateway per-route schema registry (K11-013)
 * @doc.layer kernel
 * @doc.pattern Test
 */
@DisplayName("GatewaySchemaRegistry — Unit Tests")
class GatewaySchemaRegistryTest {

    private GatewaySchemaRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new GatewaySchemaRegistry();
    }

    private GatewaySchemaRegistry.SchemaEntry acceptAllEntry() {
        return new GatewaySchemaRegistry.SchemaEntry(
                "test-service", "test-schema", "1.0",
                body -> ValidationResult.ok()
        );
    }

    private GatewaySchemaRegistry.SchemaEntry rejectAllEntry() {
        return new GatewaySchemaRegistry.SchemaEntry(
                "test-service", "strict-schema", "1.0",
                body -> body == null || body.isBlank()
                        ? ValidationResult.fail("body is empty")
                        : ValidationResult.ok()
        );
    }

    @Test
    @DisplayName("register and findSchema — exact match returned")
    void registerAndFind_exactMatch() {
        registry.register("POST:/api/v1/trades", acceptAllEntry());

        GatewaySchemaRegistry.SchemaEntry found = registry.findSchema("POST:/api/v1/trades");

        assertThat(found).isNotNull();
        assertThat(found.schemaName()).isEqualTo("test-schema");
    }

    @Test
    @DisplayName("findSchema — returns null when no schema registered for route")
    void findSchema_noMatch_returnsNull() {
        assertThat(registry.findSchema("GET:/unknown")).isNull();
    }

    @Test
    @DisplayName("deregister — removes schema entry")
    void deregister_removesEntry() {
        registry.register("POST:/api/v1/orders", acceptAllEntry());
        registry.deregister("POST:/api/v1/orders");

        assertThat(registry.findSchema("POST:/api/v1/orders")).isNull();
    }

    @Test
    @DisplayName("size — returns count of registered schemas")
    void size_returnsRegisteredCount() {
        assertThat(registry.size()).isZero();
        registry.register("POST:/api/v1/trades", acceptAllEntry());
        registry.register("GET:/api/v1/accounts/:id", acceptAllEntry());
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("wildcard pattern — prefix match resolves schema")
    void wildcardPattern_prefixMatch() {
        registry.register("POST:/api/v1/**", acceptAllEntry());

        GatewaySchemaRegistry.SchemaEntry found = registry.findSchema("POST:/api/v1/trades/123");

        assertThat(found).isNotNull();
    }

    @Test
    @DisplayName("exact match takes priority over wildcard")
    void exactMatchPriority() {
        GatewaySchemaRegistry.SchemaEntry exact  = acceptAllEntry();
        GatewaySchemaRegistry.SchemaEntry wildcard = rejectAllEntry();
        registry.register("POST:/api/v1/trades", exact);
        registry.register("POST:/api/v1/**", wildcard);

        GatewaySchemaRegistry.SchemaEntry found = registry.findSchema("POST:/api/v1/trades");

        assertThat(found.schemaName()).isEqualTo("test-schema"); // exact match
    }

    @Test
    @DisplayName("SchemaEntry.validate — wraps exceptions from validator as fail")
    void schemaEntry_validatorException_returnsFail() {
        GatewaySchemaRegistry.SchemaEntry entry = new GatewaySchemaRegistry.SchemaEntry(
                "svc", "schema", "1.0",
                body -> { throw new RuntimeException("parse error"); }
        );

        ValidationResult result = entry.validate("{bad json}");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("parse error");
    }

    @Test
    @DisplayName("register null routeKey — throws NullPointerException")
    void register_nullRouteKey_throws() {
        assertThatThrownBy(() -> registry.register(null, acceptAllEntry()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("method mismatch — wildcard pattern for GET does not match POST")
    void wildcardMethodMismatch_noMatch() {
        registry.register("GET:/api/v1/**", acceptAllEntry());

        GatewaySchemaRegistry.SchemaEntry found = registry.findSchema("POST:/api/v1/trades");

        assertThat(found).isNull();
    }
}
