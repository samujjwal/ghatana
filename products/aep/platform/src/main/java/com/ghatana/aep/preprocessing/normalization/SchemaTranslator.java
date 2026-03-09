/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.preprocessing.normalization;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Translates source-specific schemas to canonical schema.
 * 
 * <p><b>Purpose</b><br>
 * Maps attribute names, event types, and field formats from various sources
 * to canonical naming conventions. Ensures consistent schema across all events.
 * 
 * <p><b>Translation Rules</b><br>
 * <ul>
 *   <li>Event types: PascalCase (UserLoginEvent, DataChangeEvent)</li>
 *   <li>Attributes: camelCase (userId, orderTotal, ipAddress)</li>
 *   <li>Timestamps: ISO-8601 Instant format</li>
 *   <li>IDs: UUID format where possible</li>
 * </ul>
 * 
 * <p><b>Source Mappings</b><br>
 * HTTP: http.method → httpMethod, http.path → httpPath
 * DB: table_name → tableName, row_id → rowId
 * File: file.path → filePath, file.size → fileSize
 * 
 * @doc.type class
 * @doc.purpose Schema translation and mapping
 * @doc.layer product
 * @doc.pattern Service
 */
public class SchemaTranslator {
    
    private static final Pattern SNAKE_CASE = Pattern.compile("_([a-z])");
    private static final Pattern KEBAB_CASE = Pattern.compile("-([a-z])");
    private static final Pattern DOT_NOTATION = Pattern.compile("\\.([a-z])");

    private final Map<String, String> eventTypeMap = new HashMap<>();
    private final Map<String, String> attributeMap = new HashMap<>();

    public SchemaTranslator() {
        initializeDefaultMappings();
    }

    /**
     * Normalizes event type to canonical format.
     * 
     * @param sourceEventType Original event type
     * @return Normalized event type in PascalCase
     */
    public String normalizeEventType(String sourceEventType) {
        // Check explicit mapping first
        if (eventTypeMap.containsKey(sourceEventType)) {
            return eventTypeMap.get(sourceEventType);
        }

        // Convert to PascalCase
        return toPascalCase(sourceEventType);
    }

    /**
     * Normalizes attribute name to canonical format.
     * 
     * @param sourceAttribute Original attribute name
     * @return Normalized attribute in camelCase
     */
    public String normalizeAttributeName(String sourceAttribute) {
        // Check explicit mapping first
        if (attributeMap.containsKey(sourceAttribute)) {
            return attributeMap.get(sourceAttribute);
        }

        // Convert to camelCase
        return toCamelCase(sourceAttribute);
    }

    /**
     * Translates all attributes in a map to canonical format.
     */
    public Map<String, Object> translateAttributes(Map<String, Object> sourceAttributes) {
        if (sourceAttributes == null || sourceAttributes.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> translated = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : sourceAttributes.entrySet()) {
            String normalizedKey = normalizeAttributeName(entry.getKey());
            translated.put(normalizedKey, entry.getValue());
        }
        
        return translated;
    }

    /**
     * Adds custom event type mapping.
     */
    public void addEventTypeMapping(String sourceType, String canonicalType) {
        eventTypeMap.put(sourceType, canonicalType);
    }

    /**
     * Adds custom attribute mapping.
     */
    public void addAttributeMapping(String sourceAttribute, String canonicalAttribute) {
        attributeMap.put(sourceAttribute, canonicalAttribute);
    }

    /**
     * Converts string to PascalCase.
     */
    private String toPascalCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Handle already PascalCase
        if (Character.isUpperCase(input.charAt(0)) && !input.contains("_") && !input.contains("-")) {
            return input;
        }

        String camelCase = toCamelCase(input);
        return Character.toUpperCase(camelCase.charAt(0)) + camelCase.substring(1);
    }

    /**
     * Converts string to camelCase.
     */
    private String toCamelCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Handle already camelCase
        if (!input.contains("_") && !input.contains("-") && !input.contains(".")) {
            return Character.toLowerCase(input.charAt(0)) + input.substring(1);
        }

        // Convert snake_case
        String result = SNAKE_CASE.matcher(input).replaceAll(match -> 
                match.group(1).toUpperCase());

        // Convert kebab-case
        result = KEBAB_CASE.matcher(result).replaceAll(match -> 
                match.group(1).toUpperCase());

        // Convert dot.notation
        result = DOT_NOTATION.matcher(result).replaceAll(match -> 
                match.group(1).toUpperCase());

        // Ensure first character is lowercase
        return Character.toLowerCase(result.charAt(0)) + result.substring(1);
    }

    /**
     * Initializes default schema mappings.
     */
    private void initializeDefaultMappings() {
        // Event type mappings
        eventTypeMap.put("http_request", "HttpActivityEvent");
        eventTypeMap.put("database_update", "DataChangeEvent");
        eventTypeMap.put("file_created", "FileSystemEvent");
        eventTypeMap.put("sensor_reading", "TelemetryEvent");
        eventTypeMap.put("error", "ErrorEvent");
        eventTypeMap.put("exception", "ErrorEvent");

        // Common attribute mappings
        attributeMap.put("user_id", "userId");
        attributeMap.put("order_id", "orderId");
        attributeMap.put("ip_address", "ipAddress");
        attributeMap.put("http.method", "httpMethod");
        attributeMap.put("http.path", "httpPath");
        attributeMap.put("http.status", "httpStatus");
        attributeMap.put("db.table", "tableName");
        attributeMap.put("file.path", "filePath");
        attributeMap.put("file.size", "fileSize");
    }
}
