package com.ghatana.phr.fhir.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghatana.phr.plugin.FhirInteropKernelPlugin.FhirResource;
import com.ghatana.phr.plugin.FhirInteropKernelPlugin.SearchResult;

/**
 * @doc.type class
 * @doc.purpose Generates FHIR-compliant Bundle and OperationOutcome payloads for the PHR runtime surface
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class FhirBundleSupport {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private FhirBundleSupport() {}

    public static String toSearchBundle(SearchResult result) {
        try {
            ObjectNode root = JSON_MAPPER.createObjectNode();
            root.put("resourceType", "Bundle");
            root.put("type", "searchset");
            root.put("total", result.getTotalCount());
            ArrayNode entries = root.putArray("entry");

            for (FhirResource resource : result.getResources()) {
                ObjectNode entry = entries.addObject();
                entry.put("fullUrl", resource.getResourceType() + "/" + resource.getId());
                entry.putObject("search").put("mode", "match");
                JsonNode resourceNode = JSON_MAPPER.readTree(resource.getJson());
                entry.set("resource", resourceNode);
            }

            return JSON_MAPPER.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to build FHIR Bundle", exception);
        }
    }

    public static String toCollectionBundle(Iterable<String> resourcePayloads) {
        try {
            ObjectNode root = JSON_MAPPER.createObjectNode();
            root.put("resourceType", "Bundle");
            root.put("type", "collection");
            ArrayNode entries = root.putArray("entry");

            for (String payload : resourcePayloads) {
                JsonNode resourceNode = JSON_MAPPER.readTree(payload);
                ObjectNode entry = entries.addObject();
                String resourceType = textValue(resourceNode.path("resourceType"), "Resource");
                String resourceId = textValue(resourceNode.path("id"), "generated");
                entry.put("fullUrl", resourceType + "/" + resourceId);
                entry.set("resource", resourceNode);
            }

            root.put("total", entries.size());
            return JSON_MAPPER.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to build collection bundle", exception);
        }
    }

    public static String operationOutcome(String severity, String code, String diagnostics) {
        try {
            ObjectNode root = JSON_MAPPER.createObjectNode();
            root.put("resourceType", "OperationOutcome");
            ArrayNode issues = root.putArray("issue");
            ObjectNode issue = issues.addObject();
            issue.put("severity", severity);
            issue.put("code", code);
            issue.put("diagnostics", diagnostics);
            return JSON_MAPPER.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to build OperationOutcome", exception);
        }
    }

    private static String textValue(JsonNode node, String defaultValue) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? defaultValue : value;
    }
}