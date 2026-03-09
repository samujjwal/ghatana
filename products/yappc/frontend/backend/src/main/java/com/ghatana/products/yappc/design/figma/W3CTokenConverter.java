package com.ghatana.products.yappc.design.figma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Converts Figma design tokens to W3C Design Token Format.
 *
 * <p><b>Purpose</b><br>
 * Transforms Figma Variables (design tokens) to W3C Design Token Format
 * (Community Group specification). Enables interoperability with design
 * systems tools (Style Dictionary, Token Studio, etc.).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * W3CTokenConverter converter = new W3CTokenConverter(objectMapper);
 *
 * // Convert Figma variables JSON to W3C format
 * String figmaJson = figmaClient.getVariables("file-id").await();
 * String w3cJson = converter.convert(figmaJson);
 *
 * // Parse and validate
 * JsonNode w3cTokens = converter.parse(w3cJson);
 * }</pre>
 *
 * <p><b>W3C Design Token Format</b><br>
 * <pre>{@code
 * {
 *   "color": {
 *     "primary": {
 *       "$type": "color",
 *       "$value": "#0066cc"
 *     }
 *   },
 *   "spacing": {
 *     "small": {
 *       "$type": "dimension",
 *       "$value": "8px"
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p><b>Supported Token Types</b><br>
 * - color (hex, rgb, rgba)
 * - dimension (px, rem, em)
 * - fontFamily
 * - fontWeight
 * - duration
 * - cubicBezier
 * - number
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. Stateless conversion logic.
 *
 * @doc.type class
 * @doc.purpose Convert Figma tokens to W3C format
 * @doc.layer product
 * @doc.pattern Converter/Transformer
 */
public class W3CTokenConverter {
    private static final Logger logger = LoggerFactory.getLogger(W3CTokenConverter.class);
    
    private final ObjectMapper objectMapper;
    
    /**
     * Creates W3C token converter
     *
     * @param objectMapper Jackson ObjectMapper for JSON
     */
    public W3CTokenConverter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper required");
    }
    
    /**
     * Convert Figma variables JSON to W3C format
     *
     * <p>Transforms Figma's variable structure to W3C Design Token Format.
     * Preserves token hierarchy and metadata.
     *
     * @param figmaVariablesJson Figma variables JSON response
     * @return W3C format JSON string
     * @throws TokenConversionException if conversion fails
     */
    public String convert(String figmaVariablesJson) {
        try {
            JsonNode figmaRoot = objectMapper.readTree(figmaVariablesJson);
            
            // Figma variables structure: { meta: {...}, variables: {...} }
            JsonNode variables = figmaRoot.path("variables");
            if (variables.isMissingNode()) {
                throw new TokenConversionException("No 'variables' field in Figma response");
            }
            
            ObjectNode w3cRoot = objectMapper.createObjectNode();
            
            // Convert each variable to W3C token
            variables.fields().forEachRemaining(entry -> {
                String variableId = entry.getKey();
                JsonNode variable = entry.getValue();
                
                convertVariable(variable, w3cRoot);
            });
            
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(w3cRoot);
            
        } catch (IOException e) {
            throw new TokenConversionException("Failed to convert Figma tokens", e);
        }
    }
    
    /**
     * Parse W3C token JSON
     *
     * @param w3cJson W3C format JSON string
     * @return Parsed JSON tree
     * @throws TokenConversionException if parsing fails
     */
    public JsonNode parse(String w3cJson) {
        try {
            return objectMapper.readTree(w3cJson);
        } catch (IOException e) {
            throw new TokenConversionException("Failed to parse W3C tokens", e);
        }
    }
    
    // ========================================================================
    // Conversion Logic
    // ========================================================================
    
    /**
     * Convert single Figma variable to W3C token
     *
     * @param variable Figma variable node
     * @param w3cRoot W3C root object to add token to
     */
    private void convertVariable(JsonNode variable, ObjectNode w3cRoot) {
        String name = variable.path("name").asText();
        String resolvedType = variable.path("resolvedType").asText();
        JsonNode valuesByMode = variable.path("valuesByMode");
        
        if (name.isEmpty() || valuesByMode.isMissingNode()) {
            logger.warn("[W3CTokenConverter] Skipping variable: missing name or values");
            return;
        }
        
        // Get first mode value (Figma supports multiple modes, we use default)
        JsonNode firstMode = valuesByMode.elements().next();
        if (firstMode == null) {
            return;
        }
        
        // Create token path (e.g., "color.primary" → ["color", "primary"])
        String[] pathParts = name.split("/");
        ObjectNode current = w3cRoot;
        
        // Navigate/create nested structure
        for (int i = 0; i < pathParts.length - 1; i++) {
            String part = sanitizeName(pathParts[i]);
            if (!current.has(part)) {
                current.set(part, objectMapper.createObjectNode());
            }
            current = (ObjectNode) current.get(part);
        }
        
        // Create token at leaf
        String tokenName = sanitizeName(pathParts[pathParts.length - 1]);
        ObjectNode token = objectMapper.createObjectNode();
        
        // Set token type
        String w3cType = mapFigmaTypeToW3C(resolvedType);
        token.put("$type", w3cType);
        
        // Set token value
        String w3cValue = convertValue(firstMode, resolvedType);
        token.put("$value", w3cValue);
        
        // Add description if present
        String description = variable.path("description").asText();
        if (!description.isEmpty()) {
            token.put("$description", description);
        }
        
        current.set(tokenName, token);
        
        logger.debug("[W3CTokenConverter] Converted: {} → {}", name, tokenName);
    }
    
    /**
     * Map Figma variable type to W3C token type
     *
     * @param figmaType Figma resolved type (COLOR, FLOAT, STRING, etc.)
     * @return W3C token type
     */
    private String mapFigmaTypeToW3C(String figmaType) {
        return switch (figmaType.toUpperCase()) {
            case "COLOR" -> "color";
            case "FLOAT" -> "number";
            case "STRING" -> "string";
            case "BOOLEAN" -> "boolean";
            default -> {
                logger.warn("[W3CTokenConverter] Unknown Figma type: {}", figmaType);
                yield "string";
            }
        };
    }
    
    /**
     * Convert Figma value to W3C value format
     *
     * @param valueNode Figma value node
     * @param resolvedType Figma resolved type
     * @return W3C formatted value string
     */
    private String convertValue(JsonNode valueNode, String resolvedType) {
        return switch (resolvedType.toUpperCase()) {
            case "COLOR" -> convertColor(valueNode);
            case "FLOAT" -> String.valueOf(valueNode.asDouble());
            case "STRING" -> valueNode.asText();
            case "BOOLEAN" -> String.valueOf(valueNode.asBoolean());
            default -> valueNode.toString();
        };
    }
    
    /**
     * Convert Figma color to hex string
     *
     * @param colorNode Figma color node {r, g, b, a}
     * @return Hex color string (e.g., "#ff5733")
     */
    private String convertColor(JsonNode colorNode) {
        if (colorNode.isTextual()) {
            return colorNode.asText(); // Already a string color
        }
        
        double r = colorNode.path("r").asDouble(0);
        double g = colorNode.path("g").asDouble(0);
        double b = colorNode.path("b").asDouble(0);
        double a = colorNode.path("a").asDouble(1);
        
        int red = (int) (r * 255);
        int green = (int) (g * 255);
        int blue = (int) (b * 255);
        
        if (a < 1.0) {
            // RGBA format
            return String.format("rgba(%d, %d, %d, %.2f)", red, green, blue, a);
        } else {
            // Hex format
            return String.format("#%02x%02x%02x", red, green, blue);
        }
    }
    
    /**
     * Sanitize token name (remove special chars, convert to camelCase)
     *
     * @param name Raw token name
     * @return Sanitized name
     */
    private String sanitizeName(String name) {
        // Remove special characters, convert spaces to camelCase
        return name.trim()
                .replaceAll("[^a-zA-Z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .toLowerCase();
    }
    
    // ========================================================================
    // Exception
    // ========================================================================
    
    /**
     * Exception thrown when token conversion fails
     *
     * @doc.type exception
     * @doc.purpose Token conversion error
     * @doc.layer product
     * @doc.pattern Exception
     */
    public static class TokenConversionException extends RuntimeException {
        public TokenConversionException(String message) {
            super(message);
        }
        
        public TokenConversionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
