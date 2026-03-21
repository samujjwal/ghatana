/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.aep;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tenant-isolation regression tests for {@link AepServiceClient}.
 *
 * <p>Verifies that methods requiring a tenant ID throw {@link IllegalStateException} when neither
 * the payload nor the thread-local {@code TenantContext} provides one.
 *
 * @doc.type class
 * @doc.purpose Tenant isolation regression tests
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("AepServiceClient — Tenant Isolation Regression")
class AepServiceClientTenantTest {

  private AepServiceClient client;

  @BeforeEach
  void setUp() {
    AepConfig config =
        AepConfig.builder()
            .mode(AepConfig.Mode.SERVICE)
            .serviceUrl("http://localhost:9999")
            .serviceTimeoutMs(5000)
            .build();
    client = new AepServiceClient(config);
  }

  @Nested
  @DisplayName("publishEvent — Missing Tenant")
  class PublishEvent {

    @Test
    @DisplayName("should throw IllegalStateException when payload has no tenantId")
    void shouldRejectMissingTenantInPayload() {
      // GIVEN — payload without tenantId
      String payload = "{\"type\": \"test.event\", \"data\": \"hello\"}";

      // WHEN/THEN
      assertThatThrownBy(() -> client.publishEvent("test.event", payload))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Tenant ID is required");
    }
  }

  @Nested
  @DisplayName("queryEvents — Missing Tenant")
  class QueryEvents {

    @Test
    @DisplayName("should throw IllegalStateException when query has no tenantId")
    void shouldRejectMissingTenantInQuery() {
      // GIVEN — query without tenantId
      String query = "{\"status\": \"active\"}";

      // WHEN/THEN
      assertThatThrownBy(() -> client.queryEvents(query))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Tenant ID is required");
    }
  }

  @Nested
  @DisplayName("executeAction — Missing Tenant")
  class ExecuteAction {

    @Test
    @DisplayName("should throw IllegalStateException when context has no tenantId")
    void shouldRejectMissingTenantInContext() {
      // GIVEN — context without tenantId
      String context = "{\"events\": []}";

      // WHEN/THEN
      assertThatThrownBy(() -> client.executeAction("detect-patterns", context))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Tenant ID is required");
    }
  }
}
