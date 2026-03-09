package com.ghatana.products.yappc.design.figma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Figma API response models.
 *
 * <p><b>Purpose</b><br>
 * Data structures for Figma REST API responses.
 * Used for parsing design token data from Figma files.
 *
 * @doc.type class
 * @doc.purpose Figma API response models
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 */
public final class FigmaModels {
    
    // Private constructor - utility class
    private FigmaModels() {}
    
    /**
     * Figma file response
     *
     * @doc.type record
     * @doc.purpose Figma file metadata and nodes
     * @doc.layer product
     * @doc.pattern DTO
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FigmaFileResponse {
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("lastModified")
        private String lastModified;
        
        @JsonProperty("version")
        private String version;
        
        @JsonProperty("document")
        private FigmaNode document;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getLastModified() { return lastModified; }
        public void setLastModified(String lastModified) { this.lastModified = lastModified; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public FigmaNode getDocument() { return document; }
        public void setDocument(FigmaNode document) { this.document = document; }
    }
    
    /**
     * Figma node (page, frame, component, etc.)
     *
     * @doc.type record
     * @doc.purpose Figma design tree node
     * @doc.layer product
     * @doc.pattern DTO
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FigmaNode {
        @JsonProperty("id")
        private String id;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("children")
        private List<FigmaNode> children;
        
        @JsonProperty("fills")
        private List<FigmaPaint> fills;
        
        @JsonProperty("strokes")
        private List<FigmaPaint> strokes;
        
        @JsonProperty("effects")
        private List<FigmaEffect> effects;
        
        @JsonProperty("cornerRadius")
        private Double cornerRadius;
        
        @JsonProperty("fontSize")
        private Double fontSize;
        
        @JsonProperty("fontFamily")
        private String fontFamily;
        
        @JsonProperty("fontWeight")
        private Integer fontWeight;
        
        @JsonProperty("lineHeightPx")
        private Double lineHeightPx;
        
        @JsonProperty("letterSpacing")
        private Double letterSpacing;
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public List<FigmaNode> getChildren() { return children; }
        public void setChildren(List<FigmaNode> children) { this.children = children; }
        
        public List<FigmaPaint> getFills() { return fills; }
        public void setFills(List<FigmaPaint> fills) { this.fills = fills; }
        
        public List<FigmaPaint> getStrokes() { return strokes; }
        public void setStrokes(List<FigmaPaint> strokes) { this.strokes = strokes; }
        
        public List<FigmaEffect> getEffects() { return effects; }
        public void setEffects(List<FigmaEffect> effects) { this.effects = effects; }
        
        public Double getCornerRadius() { return cornerRadius; }
        public void setCornerRadius(Double cornerRadius) { this.cornerRadius = cornerRadius; }
        
        public Double getFontSize() { return fontSize; }
        public void setFontSize(Double fontSize) { this.fontSize = fontSize; }
        
        public String getFontFamily() { return fontFamily; }
        public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }
        
        public Integer getFontWeight() { return fontWeight; }
        public void setFontWeight(Integer fontWeight) { this.fontWeight = fontWeight; }
        
        public Double getLineHeightPx() { return lineHeightPx; }
        public void setLineHeightPx(Double lineHeightPx) { this.lineHeightPx = lineHeightPx; }
        
        public Double getLetterSpacing() { return letterSpacing; }
        public void setLetterSpacing(Double letterSpacing) { this.letterSpacing = letterSpacing; }
    }
    
    /**
     * Figma paint (fill or stroke)
     *
     * @doc.type record
     * @doc.purpose Figma paint style
     * @doc.layer product
     * @doc.pattern DTO
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FigmaPaint {
        @JsonProperty("type")
        private String type; // SOLID, GRADIENT_LINEAR, etc.
        
        @JsonProperty("color")
        private FigmaColor color;
        
        @JsonProperty("opacity")
        private Double opacity;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public FigmaColor getColor() { return color; }
        public void setColor(FigmaColor color) { this.color = color; }
        
        public Double getOpacity() { return opacity; }
        public void setOpacity(Double opacity) { this.opacity = opacity; }
    }
    
    /**
     * Figma color (RGBA)
     *
     * @doc.type record
     * @doc.purpose Figma color value
     * @doc.layer product
     * @doc.pattern DTO
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FigmaColor {
        @JsonProperty("r")
        private Double r; // 0.0 to 1.0
        
        @JsonProperty("g")
        private Double g; // 0.0 to 1.0
        
        @JsonProperty("b")
        private Double b; // 0.0 to 1.0
        
        @JsonProperty("a")
        private Double a; // 0.0 to 1.0
        
        public Double getR() { return r; }
        public void setR(Double r) { this.r = r; }
        
        public Double getG() { return g; }
        public void setG(Double g) { this.g = g; }
        
        public Double getB() { return b; }
        public void setB(Double b) { this.b = b; }
        
        public Double getA() { return a; }
        public void setA(Double a) { this.a = a; }
        
        /**
         * Convert to hex color
         *
         * @return Hex color string (e.g., "#ff5733")
         */
        public String toHex() {
            int red = (int) (r * 255);
            int green = (int) (g * 255);
            int blue = (int) (b * 255);
            return String.format("#%02x%02x%02x", red, green, blue);
        }
        
        /**
         * Convert to RGBA CSS string
         *
         * @return RGBA string (e.g., "rgba(255, 87, 51, 1.0)")
         */
        public String toRgba() {
            int red = (int) (r * 255);
            int green = (int) (g * 255);
            int blue = (int) (b * 255);
            return String.format("rgba(%d, %d, %d, %.2f)", red, green, blue, a);
        }
    }
    
    /**
     * Figma effect (shadow, blur, etc.)
     *
     * @doc.type record
     * @doc.purpose Figma visual effect
     * @doc.layer product
     * @doc.pattern DTO
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FigmaEffect {
        @JsonProperty("type")
        private String type; // DROP_SHADOW, INNER_SHADOW, etc.
        
        @JsonProperty("color")
        private FigmaColor color;
        
        @JsonProperty("offset")
        private Map<String, Double> offset; // {x, y}
        
        @JsonProperty("radius")
        private Double radius;
        
        @JsonProperty("visible")
        private Boolean visible;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public FigmaColor getColor() { return color; }
        public void setColor(FigmaColor color) { this.color = color; }
        
        public Map<String, Double> getOffset() { return offset; }
        public void setOffset(Map<String, Double> offset) { this.offset = offset; }
        
        public Double getRadius() { return radius; }
        public void setRadius(Double radius) { this.radius = radius; }
        
        public Boolean getVisible() { return visible; }
        public void setVisible(Boolean visible) { this.visible = visible; }
    }
}
