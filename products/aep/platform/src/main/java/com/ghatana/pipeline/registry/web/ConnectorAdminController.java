package com.ghatana.pipeline.registry.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghatana.platform.http.server.response.ErrorResponse;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.pipeline.registry.model.ConnectorInstance;
import com.ghatana.pipeline.registry.service.ConnectorAdminService;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * HTTP controller for connector instance administration.
 *
 * <p>Purpose: Provides REST endpoints for managing connector instances
 * including listing, creating, updating, and deleting connectors.
 * Supports filtering by type, direction, and status.</p>
 *
 * @doc.type class
 * @doc.purpose REST endpoints for connector instance management
 * @doc.layer product
 * @doc.pattern Controller
 * @since 2.0.0
 */
public class ConnectorAdminController {

    private final ConnectorAdminService connectorAdminService;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    public ConnectorAdminController(ConnectorAdminService connectorAdminService) {
        this.connectorAdminService = connectorAdminService;
    }

    public Promise<HttpResponse> listConnectors(
            HttpRequest request,
            TenantId tenantId,
            String userId) {
        String type = request.getQueryParameter("type");
        String direction = request.getQueryParameter("direction");
        String status = request.getQueryParameter("status");

        Collection<ConnectorInstance> all = connectorAdminService.listByTenant(tenantId);
        List<ConnectorInstance> filtered = new ArrayList<>();
        for (ConnectorInstance connector : all) {
            if (type != null && !type.isEmpty() && !type.equals(connector.getType())) {
                continue;
            }
            if (direction != null && !direction.isEmpty() && !direction.equals(connector.getDirection())) {
                continue;
            }
            if (status != null && !status.isEmpty() && !status.equals(connector.getStatus())) {
                continue;
            }
            filtered.add(connector);
        }

        ArrayNode arrayNode = objectMapper.createArrayNode();
        for (ConnectorInstance connector : filtered) {
            arrayNode.add(objectMapper.valueToTree(connector));
        }
        ObjectNode response = objectMapper.createObjectNode();
        response.set("connectors", arrayNode);
        return Promise.of(ResponseBuilder.ok().json(response).build());
    }

    public Promise<HttpResponse> getConnector(
            TenantId tenantId,
            String userId,
            String connectorId) {
        Optional<ConnectorInstance> opt = connectorAdminService.get(connectorId);
        if (opt.isEmpty()) {
            return Promise.of(ResponseBuilder.notFound()
                    .json(ErrorResponse.of(404, "NOT_FOUND", "Connector not found: " + connectorId))
                    .build());
        }
        ConnectorInstance connector = opt.get();
        return Promise.of(ResponseBuilder.ok()
                .json(objectMapper.valueToTree(connector))
                .build());
    }

    public Promise<HttpResponse> createConnector(
            HttpRequest request,
            TenantId tenantId,
            String userId) {
        try {
            String body = request.getBody().asString(StandardCharsets.UTF_8);
            JsonNode json = objectMapper.readTree(body);

            if (!json.hasNonNull("name") || !json.hasNonNull("templateId") || !json.hasNonNull("config")) {
                return Promise.of(ResponseBuilder.badRequest()
                        .json(ErrorResponse.of(400, "VALIDATION_ERROR", "name, templateId and config are required"))
                        .build());
            }

            String id = json.hasNonNull("id") ? json.get("id").asText() : java.util.UUID.randomUUID().toString();
            String name = json.get("name").asText();
            String templateId = json.get("templateId").asText();
            String type = json.has("type") ? json.get("type").asText() : "";
            String direction = json.has("direction") ? json.get("direction").asText() : "source";
            String status = json.has("status") ? json.get("status").asText() : "configuring";
            String schemaId = json.has("schemaId") && !json.get("schemaId").isNull() ? json.get("schemaId").asText()
                    : null;

            Map<String, Object> config = json.has("config") && json.get("config").isObject()
                    ? objectMapper.convertValue(json.get("config"), Map.class)
                    : java.util.Collections.emptyMap();

            List<String> eventTypeIds = new java.util.ArrayList<>();
            if (json.has("eventTypeIds") && json.get("eventTypeIds").isArray()) {
                for (JsonNode n : json.get("eventTypeIds")) {
                    eventTypeIds.add(n.asText());
                }
            }

            Map<String, Object> metadata = json.has("metadata") && json.get("metadata").isObject()
                    ? objectMapper.convertValue(json.get("metadata"), Map.class)
                    : java.util.Collections.emptyMap();

            Instant now = Instant.now();

            ConnectorInstance connector = ConnectorInstance.builder()
                    .id(id)
                    .tenantId(tenantId)
                    .name(name)
                    .templateId(templateId)
                    .type(type)
                    .direction(direction)
                    .status(status)
                    .config(config)
                    .schemaId(schemaId)
                    .eventTypeIds(eventTypeIds)
                    .createdAt(now)
                    .updatedAt(now)
                    .createdBy(userId)
                    .metadata(metadata)
                    .build();

            ConnectorInstance created = connectorAdminService.create(connector);
            return Promise.of(ResponseBuilder.created().json(objectMapper.valueToTree(created)).build());
        } catch (Exception e) {
            return Promise.of(ResponseBuilder.internalServerError()
                    .json(ErrorResponse.of(500, "INTERNAL_ERROR", e.getMessage()))
                    .build());
        }
    }

    public Promise<HttpResponse> updateConnector(
            HttpRequest request,
            TenantId tenantId,
            String userId,
            String connectorId) {
        try {
            String body = request.getBody().asString(StandardCharsets.UTF_8);
            JsonNode json = objectMapper.readTree(body);

            Optional<ConnectorInstance> opt = connectorAdminService.update(connectorId, existing -> {
                if (json.has("name")) {
                    existing.setName(json.get("name").asText());
                }
                if (json.has("status")) {
                    existing.setStatus(json.get("status").asText());
                }
                if (json.has("schemaId")) {
                    if (json.get("schemaId").isNull()) {
                        existing.setSchemaId(null);
                    } else {
                        existing.setSchemaId(json.get("schemaId").asText());
                    }
                }
                if (json.has("config") && json.get("config").isObject()) {
                    Map<String, Object> config = objectMapper.convertValue(json.get("config"), Map.class);
                    existing.setConfig(config);
                }
                if (json.has("eventTypeIds") && json.get("eventTypeIds").isArray()) {
                    List<String> eventTypeIds = new java.util.ArrayList<>();
                    for (JsonNode n : json.get("eventTypeIds")) {
                        eventTypeIds.add(n.asText());
                    }
                    existing.setEventTypeIds(eventTypeIds);
                }
                if (json.has("metadata") && json.get("metadata").isObject()) {
                    Map<String, Object> metadata = objectMapper.convertValue(json.get("metadata"), Map.class);
                    existing.setMetadata(metadata);
                }
                existing.setUpdatedAt(Instant.now());
            });
            
            return Promise.of(opt
                    .map(updated -> ResponseBuilder.ok().json(objectMapper.valueToTree(updated)).build())
                    .orElseGet(() -> ResponseBuilder.notFound()
                            .json(ErrorResponse.of(404, "NOT_FOUND", "Connector not found: " + connectorId))
                            .build()));
        } catch (Exception e) {
            return Promise.of(ResponseBuilder.internalServerError()
                    .json(ErrorResponse.of(500, "INTERNAL_ERROR", e.getMessage()))
                    .build());
        }
    }

    public Promise<HttpResponse> deleteConnector(
            TenantId tenantId,
            String userId,
            String connectorId) {
        boolean removed = connectorAdminService.delete(connectorId);
        if (!removed) {
            return Promise.of(ResponseBuilder.notFound()
                    .json(ErrorResponse.of(404, "NOT_FOUND", "Connector not found: " + connectorId))
                    .build());
        }
        return Promise.of(ResponseBuilder.noContent().build());
    }
}
