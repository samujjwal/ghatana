package com.ghatana.pipeline.registry.web;

import com.ghatana.pipeline.registry.model.SchemaDefinition;
import com.ghatana.pipeline.registry.service.EventDesignService;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EventDesignController}.
 *
 * @doc.type test
 * @doc.purpose Verify HTTP responses for event schema and connector binding endpoints
 * @doc.layer product
 * @doc.pattern ControllerTest
 */
@DisplayName("EventDesignController tests")
@ExtendWith(MockitoExtension.class)
class EventDesignControllerTest extends EventloopTestBase {

    @Mock
    private EventDesignService eventDesignService;

    private EventDesignController controller;
    private static final TenantId TENANT = TenantId.of("tenant-1");

    @BeforeEach
    void setUp() {
        controller = new EventDesignController(eventDesignService);
    }

    @Test
    @DisplayName("createSchema: valid body → 201 Created")
    void createSchemaValidReturns201() {
        SchemaDefinition created = SchemaDefinition.builder()
                .id("schema-1")
                .tenantId(TENANT)
                .eventTypeId("order-event")
                .format("json-schema")
                .build();
        when(eventDesignService.createSchema(any(SchemaDefinition.class))).thenReturn(created);

        String json = "{\"format\":\"json-schema\",\"definition\":\"{}\"}";
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/event-design/schemas")
                .withBody(ByteBuf.wrapForReading(json.getBytes(StandardCharsets.UTF_8)))
                .build();

        HttpResponse response = runPromise(() -> controller.createSchema(request, TENANT, "user-1", "order-event"));

        assertThat(response.getCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("deleteSchema: existing id → 204 No Content")
    void deleteSchemaExistsReturns204() {
        when(eventDesignService.deleteSchema("schema-1")).thenReturn(true);

        HttpResponse response = runPromise(() -> controller.deleteSchema(TENANT, "user-1", "order-event", "schema-1"));

        assertThat(response.getCode()).isEqualTo(204);
    }

    @Test
    @DisplayName("deleteSchema: not found → 404 Not Found")
    void deleteSchemaNotFoundReturns404() {
        when(eventDesignService.deleteSchema("missing")).thenReturn(false);

        HttpResponse response = runPromise(() -> controller.deleteSchema(TENANT, "user-1", "order-event", "missing"));

        assertThat(response.getCode()).isEqualTo(404);
    }
}
