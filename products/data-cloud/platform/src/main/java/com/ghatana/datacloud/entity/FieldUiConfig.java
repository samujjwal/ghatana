package com.ghatana.datacloud.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.With;

import java.util.HashMap;
import java.util.Map;

/**
 * Type-safe UI configuration for field rendering.
 *
 * <p>Replaces {@code Map<String, Object>} for UI configuration,
 * providing compile-time type safety and better IDE support.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * FieldUiConfig uiConfig = FieldUiConfig.builder()
 *     .visible(true)
 *     .order(1)
 *     .span(6)
 *     .placeholder("Enter value")
 *     .build();
 * }</pre>
 *
 * @see MetaField
 * @doc.type record
 * @doc.purpose Type-safe UI configuration for field rendering
 * @doc.layer domain
 * @doc.pattern Value Object
 */
@Builder
@With
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldUiConfig(
        // Visibility settings
        Boolean visible,
        Boolean hidden,
        Boolean readOnly,
        Boolean disabled,
        
        // Layout settings
        Integer order,
        Integer span,      // Grid span (1-12 for Bootstrap-style grids)
        String width,      // CSS width value
        
        // Display settings
        String placeholder,
        String helpText,
        String tooltip,
        String icon,
        
        // Input settings
        String inputType,  // text, textarea, select, checkbox, etc.
        Integer rows,      // For textarea
        Boolean multiline,
        
        // Formatting
        String format,     // Date format, number format, etc.
        String prefix,     // Currency symbol, etc.
        String suffix,     // Units, etc.
        
        // Grouping
        String section,    // Section/tab name
        String group,      // Field group name
        
        // Conditional display
        String showWhen,   // Expression for conditional visibility
        String hideWhen    // Expression for conditional hiding
) {
    
    /**
     * Creates an empty UI config (defaults).
     *
     * @return empty UI config
     */
    public static FieldUiConfig empty() {
        return FieldUiConfig.builder().build();
    }
    
    /**
     * Creates a visible UI config with order.
     *
     * @param order display order
     * @return UI config with visibility and order
     */
    public static FieldUiConfig visible(int order) {
        return FieldUiConfig.builder().visible(true).order(order).build();
    }
    
    /**
     * Creates a hidden field UI config.
     *
     * @return hidden UI config
     */
    public static FieldUiConfig hiddenField() {
        return FieldUiConfig.builder().hidden(true).visible(false).build();
    }
    
    /**
     * Creates a read-only field UI config.
     *
     * @param order display order
     * @return read-only UI config
     */
    public static FieldUiConfig readOnly(int order) {
        return FieldUiConfig.builder().visible(true).readOnly(true).order(order).build();
    }
    
    /**
     * Creates a full-width field UI config.
     *
     * @param order display order
     * @return full-width UI config
     */
    public static FieldUiConfig fullWidth(int order) {
        return FieldUiConfig.builder().visible(true).order(order).span(12).build();
    }
    
    /**
     * Creates a FieldUiConfig from a legacy Map.
     *
     * @param map the UI config map
     * @return type-safe FieldUiConfig
     */
    public static FieldUiConfig fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return empty();
        }
        
        return FieldUiConfig.builder()
                .visible(getAsBoolean(map.get("visible")))
                .hidden(getAsBoolean(map.get("hidden")))
                .readOnly(getAsBoolean(map.get("readOnly")))
                .disabled(getAsBoolean(map.get("disabled")))
                .order(getAsInteger(map.get("order")))
                .span(getAsInteger(map.get("span")))
                .width((String) map.get("width"))
                .placeholder((String) map.get("placeholder"))
                .helpText((String) map.get("helpText"))
                .tooltip((String) map.get("tooltip"))
                .icon((String) map.get("icon"))
                .inputType((String) map.get("inputType"))
                .rows(getAsInteger(map.get("rows")))
                .multiline(getAsBoolean(map.get("multiline")))
                .format((String) map.get("format"))
                .prefix((String) map.get("prefix"))
                .suffix((String) map.get("suffix"))
                .section((String) map.get("section"))
                .group((String) map.get("group"))
                .showWhen((String) map.get("showWhen"))
                .hideWhen((String) map.get("hideWhen"))
                .build();
    }
    
    /**
     * Converts this UI config to a Map for JSONB storage.
     *
     * @return map representation
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (visible != null) map.put("visible", visible);
        if (hidden != null) map.put("hidden", hidden);
        if (readOnly != null) map.put("readOnly", readOnly);
        if (disabled != null) map.put("disabled", disabled);
        if (order != null) map.put("order", order);
        if (span != null) map.put("span", span);
        if (width != null) map.put("width", width);
        if (placeholder != null) map.put("placeholder", placeholder);
        if (helpText != null) map.put("helpText", helpText);
        if (tooltip != null) map.put("tooltip", tooltip);
        if (icon != null) map.put("icon", icon);
        if (inputType != null) map.put("inputType", inputType);
        if (rows != null) map.put("rows", rows);
        if (multiline != null) map.put("multiline", multiline);
        if (format != null) map.put("format", format);
        if (prefix != null) map.put("prefix", prefix);
        if (suffix != null) map.put("suffix", suffix);
        if (section != null) map.put("section", section);
        if (group != null) map.put("group", group);
        if (showWhen != null) map.put("showWhen", showWhen);
        if (hideWhen != null) map.put("hideWhen", hideWhen);
        return map;
    }
    
    private static Boolean getAsBoolean(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return null;
    }
    
    private static Integer getAsInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
