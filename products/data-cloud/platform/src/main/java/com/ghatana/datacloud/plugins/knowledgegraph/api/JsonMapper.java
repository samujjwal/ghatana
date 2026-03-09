package com.ghatana.datacloud.plugins.knowledgegraph.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphEdge;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphNode;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphQuery;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * JSON mapper for Knowledge Graph API.
 * 
 * <p>Handles serialization and deserialization of graph models using Jackson.
 * Configured with proper Java 8 time support and formatting.
 * 
 * @doc.type class
 * @doc.purpose JSON serialization/deserialization
 * @doc.layer api
 * @doc.pattern Utility
 */
@Slf4j
public class JsonMapper {
    
    private final ObjectMapper objectMapper;
    
    public JsonMapper() {
        this.objectMapper = JsonUtils.getPrettyMapper();
        log.info("JsonMapper initialized");
    }
    
    /**
     * Serializes an object to JSON string.
     */
    public String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Error serializing object to JSON", e);
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
    
    /**
     * Deserializes JSON string to GraphNode.
     */
    public GraphNode parseNode(String json) {
        try {
            return objectMapper.readValue(json, GraphNode.class);
        } catch (JsonProcessingException e) {
            log.error("Error parsing GraphNode from JSON", e);
            throw new IllegalArgumentException("Invalid GraphNode JSON: " + e.getMessage(), e);
        }
    }
    
    /**
     * Deserializes JSON string to list of GraphNodes.
     */
    public List<GraphNode> parseNodes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<GraphNode>>() {});
        } catch (JsonProcessingException e) {
            log.error("Error parsing GraphNode list from JSON", e);
            throw new IllegalArgumentException("Invalid GraphNode list JSON: " + e.getMessage(), e);
        }
    }
    
    /**
     * Deserializes JSON string to GraphEdge.
     */
    public GraphEdge parseEdge(String json) {
        try {
            return objectMapper.readValue(json, GraphEdge.class);
        } catch (JsonProcessingException e) {
            log.error("Error parsing GraphEdge from JSON", e);
            throw new IllegalArgumentException("Invalid GraphEdge JSON: " + e.getMessage(), e);
        }
    }
    
    /**
     * Deserializes JSON string to list of GraphEdges.
     */
    public List<GraphEdge> parseEdges(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<GraphEdge>>() {});
        } catch (JsonProcessingException e) {
            log.error("Error parsing GraphEdge list from JSON", e);
            throw new IllegalArgumentException("Invalid GraphEdge list JSON: " + e.getMessage(), e);
        }
    }
    
    /**
     * Deserializes JSON string to GraphQuery.
     */
    public GraphQuery parseQuery(String json) {
        try {
            return objectMapper.readValue(json, GraphQuery.class);
        } catch (JsonProcessingException e) {
            log.error("Error parsing GraphQuery from JSON", e);
            throw new IllegalArgumentException("Invalid GraphQuery JSON: " + e.getMessage(), e);
        }
    }
    
    /**
     * Deserializes JSON string to Map.
     */
    public Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("Error parsing Map from JSON", e);
            throw new IllegalArgumentException("Invalid JSON map: " + e.getMessage(), e);
        }
    }
    
    /**
     * Deserializes JSON string to path request parameters.
     */
    public PathRequest parsePathRequest(String json) {
        try {
            return objectMapper.readValue(json, PathRequest.class);
        } catch (JsonProcessingException e) {
            log.error("Error parsing PathRequest from JSON", e);
            throw new IllegalArgumentException("Invalid PathRequest JSON: " + e.getMessage(), e);
        }
    }
    
    /**
     * Path request DTO.
     */
    public record PathRequest(
        String sourceNodeId,
        String targetNodeId,
        String tenantId,
        Integer maxLength
    ) {}
}
