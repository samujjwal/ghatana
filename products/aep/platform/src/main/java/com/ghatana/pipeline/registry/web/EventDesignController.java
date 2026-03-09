package com.ghatana.pipeline.registry.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghatana.platform.http.server.response.ErrorResponse;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.pipeline.registry.model.ConnectorBinding;
import com.ghatana.pipeline.registry.model.SchemaDefinition;
import com.ghatana.pipeline.registry.service.EventDesignService;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * HTTP controller for event type schema and connector binding management.
 *
 * <p>Purpose: Provides control-plane REST endpoints for managing event type
 * schemas, connector bindings, and binding simulations. Supports the full
 * lifecycle of schema and binding CRUD operations.</p>
 *
 * @doc.type class
 * @doc.purpose REST endpoints for event schemas and connector bindings
 * @doc.layer product
 * @doc.pattern Controller
 * @since 2.0.0
 */
public class EventDesignController {

    private final EventDesignService eventDesignService;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    public EventDesignController(EventDesignService eventDesignService) {
        this.eventDesignService = eventDesignService;
    }

    public Promise<HttpResponse> createSchema(
            HttpRequest request,
            TenantId tenantId,
            String userId,
            String eventTypeId) {
        try {
            String body = request.getBody().asString(StandardCharsets.UTF_8);
            JsonNode json = objectMapper.readTree(body);

            if (!json.hasNonNull("format") || (!json.hasNonNull("document") && !json.hasNonNull("definition"))) {
                return Promise.of(ResponseBuilder.badRequest()
                        .json(ErrorResponse.of(400, "VALIDATION_ERROR", "format and document/definition are required"))
                        .build());
            }

            String format = json.get("format").asText();
            String direction = json.has("direction") ? json.get("direction").asText() : "BOTH";
            String document = json.has("document") ? json.get("document").asText() : json.get("definition").asText();
            String description = json.has("description") ? json.get("description").asText() : "";

            SchemaDefinition schema = SchemaDefinition.create(
                    tenantId,
                    eventTypeId,
                    format,
                    document,
                    direction,
                    description,
                    1);

            SchemaDefinition created = eventDesignService.createSchema(schema);
            JsonNode responseJson = objectMapper.valueToTree(created);
            return Promise.of(ResponseBuilder.created().json(responseJson).build());
        } catch (Exception e) {
            return Promise.of(ResponseBuilder.internalServerError()
                    .json(ErrorResponse.of(500, "INTERNAL_ERROR", e.getMessage()))
                    .build());
        }
    }

    public Promise<HttpResponse> updateSchema(
            HttpRequest request,
            TenantId tenantId,
            String userId,
            String eventTypeId,
            String schemaId) {
        try {
            String body = request.getBody().asString(StandardCharsets.UTF_8);
            JsonNode json = objectMapper.readTree(body);

            Optional<SchemaDefinition> opt = eventDesignService.updateSchema(schemaId, existing -> {
                if (json.has("description")) {
                    existing.setDescription(json.get("description").asText());
                }
                if (json.has("document")) {
                    existing.setDocument(json.get("document").asText());
                } else if (json.has("definition")) {
                    existing.setDocument(json.get("definition").asText());
                }
            });
            
            return Promise.of(opt
                    .map(updated -> ResponseBuilder.ok().json(objectMapper.valueToTree(updated)).build())
                    .orElseGet(() -> ResponseBuilder.notFound()
                            .json(ErrorResponse.of(404, "NOT_FOUND", "Schema not found: " + schemaId))
                            .build()));
        } catch (Exception e) {
            return Promise.of(ResponseBuilder.internalServerError()
                    .json(ErrorResponse.of(500, "INTERNAL_ERROR", e.getMessage()))
                    .build());
        }
    }

    public Promise<HttpResponse> deleteSchema(
            TenantId tenantId,
            String userId,
            String eventTypeId,
            String schemaId) {
        boolean removed = eventDesignService.deleteSchema(schemaId);
        if (!removed) {
            return Promise.of(ResponseBuilder.notFound()
                    .json(ErrorResponse.of(404, "NOT_FOUND", "Schema not found: " + schemaId))
                    .build());
        }
        return Promise.of(ResponseBuilder.noContent().build());
    }

    public Promise<HttpResponse> createBinding(
            HttpRequest request,
            TenantId tenantId,
            String userId,
            String eventTypeId,
            String schemaId) {
        try {
            String body = request.getBody().asString(StandardCharsets.UTF_8);
            JsonNode json = objectMapper.readTree(body);

            if (!json.hasNonNull("connectorId") || !json.hasNonNull("encoding")) {
                return Promise.of(ResponseBuilder.badRequest()
                        .json(ErrorResponse.of(400, "VALIDATION_ERROR", "connectorId and encoding are required"))
                        .build());
            }

            String connectorId = json.get("connectorId").asText();
            String direction = json.has("direction") ? json.get("direction").asText() : "INGRESS";
            String encoding = json.get("encoding").asText();

            List<ConnectorBinding.HeaderMapping> headerMappings = parseHeaderMappings(json.get("headerMappings"));
            ConnectorBinding.PayloadMapping payloadMapping = parsePayloadMapping(json.get("payloadMapping"));

            ConnectorBinding binding = ConnectorBinding.create(
                    tenantId,
                    schemaId,
                    connectorId,
                    direction,
                    encoding,
                    headerMappings,
                    payloadMapping,
                    userId);

            ConnectorBinding created = eventDesignService.createBinding(binding);
            JsonNode responseJson = objectMapper.valueToTree(created);
            return Promise.of(ResponseBuilder.created().json(responseJson).build());
        } catch (Exception e) {
            return Promise.of(ResponseBuilder.internalServerError()
                    .json(ErrorResponse.of(500, "INTERNAL_ERROR", e.getMessage()))
                    .build());
        }
    }

    public Promise<HttpResponse> updateBinding(
            HttpRequest request,
            TenantId tenantId,
            String userId,
            String bindingId) {
        try {
            String body = request.getBody().asString(StandardCharsets.UTF_8);
            JsonNode json = objectMapper.readTree(body);

            Optional<ConnectorBinding> opt = eventDesignService.updateBinding(bindingId, existing -> {
                if (json.has("encoding")) {
                    existing.setEncoding(json.get("encoding").asText());
                }
                if (json.has("direction")) {
                    existing.setDirection(json.get("direction").asText());
                }
                if (json.has("headerMappings")) {
                    existing.setHeaderMappings(parseHeaderMappings(json.get("headerMappings")));
                }
                if (json.has("payloadMapping")) {
                    existing.setPayloadMapping(parsePayloadMapping(json.get("payloadMapping")));
                }
                if (json.has("enabled")) {
                    existing.setEnabled(json.get("enabled").asBoolean());
                }
                existing.touchUpdated(userId);
            });
            
            return Promise.of(opt
                    .map(updated -> ResponseBuilder.ok().json(objectMapper.valueToTree(updated)).build())
                    .orElseGet(() -> ResponseBuilder.notFound()
                            .json(ErrorResponse.of(404, "NOT_FOUND", "Binding not found: " + bindingId))
                            .build()));
        } catch (Exception e) {
            return Promise.of(ResponseBuilder.internalServerError()
                    .json(ErrorResponse.of(500, "INTERNAL_ERROR", e.getMessage()))
                    .build());
        }
    }

    public Promise<HttpResponse> deleteBinding(
            TenantId tenantId,
            String userId,
            String bindingId) {
        boolean removed = eventDesignService.deleteBinding(bindingId);
        if (!removed) {
            return Promise.of(ResponseBuilder.notFound()
                    .json(ErrorResponse.of(404, "NOT_FOUND", "Binding not found: " + bindingId))
                    .build());
        }
        return Promise.of(ResponseBuilder.noContent().build());
    }

    public Promise<HttpResponse> getBinding(
            TenantId tenantId,
            String userId,
            String bindingId) {
        Optional<ConnectorBinding> opt = eventDesignService.getBinding(bindingId);
        if (opt.isEmpty()) {
            return Promise.of(ResponseBuilder.notFound()
                    .json(ErrorResponse.of(404, "NOT_FOUND", "Binding not found: " + bindingId))
                    .build());
        }
        ConnectorBinding binding = opt.get();
        return Promise.of(ResponseBuilder.ok()
                .json(objectMapper.valueToTree(binding))
                .build());
    }

    public Promise<HttpResponse> listBindings(
            HttpRequest request,
            TenantId tenantId,
            String userId) {
        String schemaId = request.getQueryParameter("schemaId");
        String connectorId = request.getQueryParameter("connectorId");
        String direction = request.getQueryParameter("direction");

        List<ConnectorBinding> all = new ArrayList<>(eventDesignService.listBindingsByTenant(tenantId));
        List<ConnectorBinding> filtered = new ArrayList<>();
        for (ConnectorBinding binding : all) {
            if (schemaId != null && !schemaId.isEmpty() && !schemaId.equals(binding.getSchemaId())) {
                continue;
            }
            if (connectorId != null && !connectorId.isEmpty() && !connectorId.equals(binding.getConnectorId())) {
                continue;
            }
            if (direction != null && !direction.isEmpty() && !direction.equalsIgnoreCase(binding.getDirection())) {
                continue;
            }
            filtered.add(binding);
        }

        ArrayNode arrayNode = objectMapper.createArrayNode();
        for (ConnectorBinding binding : filtered) {
            arrayNode.add(objectMapper.valueToTree(binding));
        }
        ObjectNode response = objectMapper.createObjectNode();
        response.set("bindings", arrayNode);
        return Promise.of(ResponseBuilder.ok().json(response).build());
    }

    public Promise<HttpResponse> simulateBinding(
            HttpRequest request,
            TenantId tenantId,
            String userId,
            String bindingId) {
        long start = System.currentTimeMillis();

        Optional<ConnectorBinding> opt = eventDesignService.getBinding(bindingId);
        
        if (opt.isEmpty()) {
            return Promise.of(ResponseBuilder.notFound()
                    .json(ErrorResponse.of(404, "NOT_FOUND", "Binding not found: " + bindingId))
                    .build());
        }

        ConnectorBinding binding = opt.get();
        
        return Promise.complete().map($ -> {

            try {
                String body = request.getBody().asString(StandardCharsets.UTF_8);
                JsonNode json = objectMapper.readTree(body);

                ObjectNode response = objectMapper.createObjectNode();
                response.put("success", true);
                ObjectNode result = objectMapper.createObjectNode();

                if (json.has("messageHeaders") && json.has("messageBody")) {
                    ObjectNode headersNode = simulateIngressHeaders(binding, json.get("messageHeaders"),
                            json.get("messageBody"));
                    JsonNode payloadNode = simulateIngressPayload(binding, json.get("messageBody"));
                    result.set("headers", headersNode);
                    result.set("payload", payloadNode);
                } else if (json.has("event")) {
                    ObjectNode headersNode = simulateEgressHeaders(binding, json.get("event"));
                    JsonNode payloadNode = simulateEgressPayload(binding, json.get("event"));
                    result.set("headers", headersNode);
                    result.set("payload", payloadNode);
                } else {
                    response.put("success", false);
                    ArrayNode errors = objectMapper.createArrayNode();
                    ObjectNode err = objectMapper.createObjectNode();
                    err.put("path", "");
                    err.put("message", "Unsupported simulation request body");
                    errors.add(err);
                    response.set("errors", errors);
                    response.put("processingTimeMs", System.currentTimeMillis() - start);
                    return ResponseBuilder.badRequest().json(response).build();
                }

                response.set("result", result);
                response.put("processingTimeMs", System.currentTimeMillis() - start);
                return ResponseBuilder.ok().json(response).build();

            } catch (Exception e) {
                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("success", false);
                ArrayNode errors = objectMapper.createArrayNode();
                ObjectNode err = objectMapper.createObjectNode();
                err.put("path", "");
                err.put("message", e.getMessage());
                errors.add(err);
                errorResponse.set("errors", errors);
                errorResponse.put("processingTimeMs", System.currentTimeMillis() - start);
                return ResponseBuilder.internalServerError().json(errorResponse).build();
            }
        });
    }

    private List<ConnectorBinding.HeaderMapping> parseHeaderMappings(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<ConnectorBinding.HeaderMapping> mappings = new ArrayList<>();
        for (JsonNode n : node) {
            String source = n.has("source") ? n.get("source").asText() : "";
            String target = n.has("target") ? n.get("target").asText() : "";
            String transform = n.has("transform") ? n.get("transform").asText() : null;
            mappings.add(ConnectorBinding.HeaderMapping.builder()
                    .source(source)
                    .target(target)
                    .transform(transform)
                    .build());
        }
        return mappings;
    }

    private ConnectorBinding.PayloadMapping parsePayloadMapping(JsonNode node) {
        if (node == null || node.isNull()) {
            return ConnectorBinding.PayloadMapping.builder()
                    .rootPath("$")
                    .excludeFields(Collections.emptyList())
                    .fieldTransforms(Collections.emptyMap())
                    .build();
        }
        String rootPath = node.has("rootPath") ? node.get("rootPath").asText() : "$";
        List<String> excludeFields = new ArrayList<>();
        if (node.has("excludeFields") && node.get("excludeFields").isArray()) {
            JsonNode excludeNode = node.get("excludeFields");
            for (Iterator<JsonNode> it = excludeNode.elements(); it.hasNext();) {
                JsonNode f = it.next();
                excludeFields.add(f.asText());
            }
        }
        Map<String, String> fieldTransforms = new HashMap<>();
        if (node.has("fieldTransforms") && node.get("fieldTransforms").isObject()) {
            Iterator<String> names = node.get("fieldTransforms").fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                fieldTransforms.put(name, node.get("fieldTransforms").get(name).asText());
            }
        }
        return ConnectorBinding.PayloadMapping.builder()
                .rootPath(rootPath)
                .excludeFields(excludeFields)
                .fieldTransforms(fieldTransforms)
                .build();
    }

    private ObjectNode simulateIngressHeaders(ConnectorBinding binding, JsonNode headersNode, JsonNode messageBodyNode)
            throws Exception {
        ObjectNode resultHeaders = objectMapper.createObjectNode();
        for (ConnectorBinding.HeaderMapping mapping : binding.getHeaderMappings()) {
            String source = mapping.getSource();
            String target = mapping.getTarget();
            if (source == null || target == null) {
                continue;
            }
            if (source.startsWith("header:")) {
                String headerName = source.substring("header:".length());
                String value = headersNode.has(headerName) ? headersNode.get(headerName).asText() : "";
                resultHeaders.put(target, value);
            } else if (source.startsWith("body:")) {
                String path = source.substring("body:".length());
                JsonNode parsedBody = parseJsonSafe(messageBodyNode.asText());
                JsonNode valueNode = getNestedValue(parsedBody, path);
                String value = valueNode != null && !valueNode.isNull() ? valueNode.asText() : "";
                resultHeaders.put(target, value);
            }
        }
        return resultHeaders;
    }

    private JsonNode simulateIngressPayload(ConnectorBinding binding, JsonNode messageBodyNode) throws Exception {
        JsonNode payload = parseJsonSafe(messageBodyNode.asText());

        ConnectorBinding.PayloadMapping mapping = binding.getPayloadMapping();
        String rootPath = mapping != null ? mapping.getRootPath() : "$";
        if (rootPath != null && !"$".equals(rootPath)) {
            String path = rootPath.startsWith("$.") ? rootPath.substring(2) : rootPath;
            JsonNode maybe = getNestedValue(payload, path);
            if (maybe != null && !maybe.isMissingNode() && !maybe.isNull()) {
                payload = maybe;
            }
        }

        if (payload.isObject() && mapping != null) {
            ObjectNode obj = (ObjectNode) payload.deepCopy();
            for (String field : mapping.getExcludeFields()) {
                obj.remove(field);
            }
            return obj;
        }
        return payload;
    }

    private ObjectNode simulateEgressHeaders(ConnectorBinding binding, JsonNode eventNode) {
        ObjectNode resultHeaders = objectMapper.createObjectNode();
        JsonNode headers = eventNode.get("headers");
        if (headers == null || !headers.isObject()) {
            return resultHeaders;
        }
        for (ConnectorBinding.HeaderMapping mapping : binding.getHeaderMappings()) {
            String source = mapping.getSource();
            String target = mapping.getTarget();
            if (source == null || target == null) {
                continue;
            }
            if (target.startsWith("header:")) {
                String headerName = target.substring("header:".length());
                String value = headers.has(source) ? headers.get(source).asText() : "";
                resultHeaders.put(headerName, value);
            }
        }
        return resultHeaders;
    }

    private JsonNode simulateEgressPayload(ConnectorBinding binding, JsonNode eventNode) {
        JsonNode payload = eventNode.get("payload");
        if (payload == null) {
            return objectMapper.createObjectNode();
        }
        ConnectorBinding.PayloadMapping mapping = binding.getPayloadMapping();
        if (payload.isObject() && mapping != null) {
            ObjectNode obj = (ObjectNode) payload.deepCopy();
            for (String field : mapping.getExcludeFields()) {
                obj.remove(field);
            }
            // Very small transform support: uppercase
            for (Map.Entry<String, String> entry : mapping.getFieldTransforms().entrySet()) {
                String field = entry.getKey();
                String transform = entry.getValue();
                if ("uppercase".equalsIgnoreCase(transform) && obj.has(field)) {
                    obj.put(field, obj.get(field).asText().toUpperCase(Locale.ROOT));
                }
            }
            return obj;
        }
        return payload;
    }

    private JsonNode parseJsonSafe(String json) throws Exception {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            // Fallback to empty object for simulation; caller may surface validation error
            return objectMapper.createObjectNode();
        }
    }

    private JsonNode getNestedValue(JsonNode root, String path) {
        if (root == null || path == null || path.isEmpty()) {
            return root;
        }
        String[] parts = path.split("\\.");
        JsonNode current = root;
        for (String part : parts) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}
