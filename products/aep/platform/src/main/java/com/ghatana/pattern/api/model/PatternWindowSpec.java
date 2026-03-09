package com.ghatana.pattern.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghatana.aep.domain.pattern.WindowType;

import java.time.Duration;
import java.util.Map;

/**
 * Defines windowing specifications for pattern detection with late event handling.
 * 
 * <p>This is distinct from {@code com.ghatana.core.event.query.WindowSpec} which defines
 * windowing for event queries (tumbling, sliding, session windows).
 * PatternWindowSpec adds pattern-specific features like early/late results and additional parameters.
 * 
 * @doc.pattern Value Object Pattern (immutable window config), Builder Pattern (construction)
 * @doc.compiler-phase Window Specification (input to validation and compilation)
 * @doc.threading Thread-safe after construction (immutable value object)
 * @doc.performance O(1) for field access
 * @doc.memory O(1) for fixed fields + O(p) for parameters map where p=parameter count
 * @doc.immutability Immutable after build(); use builder for modifications
 * @doc.serialization JSON serializable via Jackson annotations
 * @doc.apiNote Configure window type, size, and late event handling via builder
 * @doc.limitation No dynamic window adjustments; recompile pattern for changes
 * 
 * <h2>Window Types</h2>
 * <table border="1" cellpadding="5">
 *   <tr>
 *     <th>Type</th>
 *     <th>Required Fields</th>
 *     <th>Use Case</th>
 *   </tr>
 *   <tr>
 *     <td>TUMBLING</td>
 *     <td>size</td>
 *     <td>Non-overlapping fixed-size windows (e.g., 5-minute intervals)</td>
 *   </tr>
 *   <tr>
 *     <td>SLIDING</td>
 *     <td>size, slide</td>
 *     <td>Overlapping windows (e.g., 5-min window every 1 min)</td>
 *   </tr>
 *   <tr>
 *     <td>SESSION</td>
 *     <td>sessionTimeout</td>
 *     <td>Dynamic windows based on activity gaps (e.g., user sessions)</td>
 *   </tr>
 *   <tr>
 *     <td>GLOBAL</td>
 *     <td>-</td>
 *     <td>Single unbounded window (entire event history)</td>
 *   </tr>
 * </table>
 * 
 * <p><b>Late Event Handling</b>:
 * <ul>
 *   <li><b>allowedLateness</b>: How long after window close to accept events</li>
 *   <li><b>earlyResults</b>: Emit partial results before window closes</li>
 *   <li><b>lateResults</b>: Update results when late events arrive (default: true)</li>
 * </ul>
 * 
 * <p><b>Example</b>:
 * <pre>
 * PatternWindowSpec.builder()
 *   .type(WindowType.SLIDING)
 *   .size(Duration.ofMinutes(5))
 *   .slide(Duration.ofMinutes(1))
 *   .allowedLateness(Duration.ofSeconds(30))
 *   .lateResults(true)
 *   .build()
 * </pre>
 */
public class PatternWindowSpec {
    
    @JsonProperty("type")
    private WindowType type;
    
    @JsonProperty("size")
    private Duration size;
    
    @JsonProperty("slide")
    private Duration slide;
    
    @JsonProperty("sessionTimeout")
    private Duration sessionTimeout;
    
    @JsonProperty("allowedLateness")
    private Duration allowedLateness;
    
    @JsonProperty("earlyResults")
    private boolean earlyResults;
    
    @JsonProperty("lateResults")
    private boolean lateResults;
    
    @JsonProperty("parameters")
    private Map<String, Object> parameters;
    
    // Default constructor for JSON deserialization
    public PatternWindowSpec() {}
    
    // Builder pattern constructor
    public PatternWindowSpec(Builder builder) {
        this.type = builder.type;
        this.size = builder.size;
        this.slide = builder.slide;
        this.sessionTimeout = builder.sessionTimeout;
        this.allowedLateness = builder.allowedLateness;
        this.earlyResults = builder.earlyResults;
        this.lateResults = builder.lateResults;
        this.parameters = builder.parameters;
    }
    
    // Getters
    public WindowType getType() { return type; }
    public Duration getSize() { return size; }
    public Duration getSlide() { return slide; }
    public Duration getSessionTimeout() { return sessionTimeout; }
    public Duration getAllowedLateness() { return allowedLateness; }
    public boolean isEarlyResults() { return earlyResults; }
    public boolean isLateResults() { return lateResults; }
    public Map<String, Object> getParameters() { return parameters; }
    
    // Setters
    public void setType(WindowType type) { this.type = type; }
    public void setSize(Duration size) { this.size = size; }
    public void setSlide(Duration slide) { this.slide = slide; }
    public void setSessionTimeout(Duration sessionTimeout) { this.sessionTimeout = sessionTimeout; }
    public void setAllowedLateness(Duration allowedLateness) { this.allowedLateness = allowedLateness; }
    public void setEarlyResults(boolean earlyResults) { this.earlyResults = earlyResults; }
    public void setLateResults(boolean lateResults) { this.lateResults = lateResults; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private WindowType type;
        private Duration size;
        private Duration slide;
        private Duration sessionTimeout;
        private Duration allowedLateness;
        private boolean earlyResults = false;
        private boolean lateResults = true;
        private Map<String, Object> parameters;
        
        public Builder type(WindowType type) { this.type = type; return this; }
        public Builder size(Duration size) { this.size = size; return this; }
        public Builder slide(Duration slide) { this.slide = slide; return this; }
        public Builder sessionTimeout(Duration sessionTimeout) { this.sessionTimeout = sessionTimeout; return this; }
        public Builder allowedLateness(Duration allowedLateness) { this.allowedLateness = allowedLateness; return this; }
        public Builder earlyResults(boolean earlyResults) { this.earlyResults = earlyResults; return this; }
        public Builder lateResults(boolean lateResults) { this.lateResults = lateResults; return this; }
        public Builder parameters(Map<String, Object> parameters) { this.parameters = parameters; return this; }
        
        public PatternWindowSpec build() {
            return new PatternWindowSpec(this);
        }
    }
    
    @Override
    public String toString() {
        return "PatternWindowSpec{" +
                "type=" + type +
                ", size=" + size +
                ", slide=" + slide +
                ", sessionTimeout=" + sessionTimeout +
                ", allowedLateness=" + allowedLateness +
                '}';
    }
}
