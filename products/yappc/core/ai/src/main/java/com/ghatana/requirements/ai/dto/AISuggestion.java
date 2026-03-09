package com.ghatana.requirements.ai.dto;

/**

 * @doc.type class

 * @doc.purpose Handles ai suggestion operations

 * @doc.layer core

 * @doc.pattern ValueObject

 */

public class AISuggestion {
    private String type;
    private String suggestion;
    private String rationale;
    private double confidence;

    private AISuggestion() {}

    public String getType() { return type; }
    public String getSuggestion() { return suggestion; }
    public String getRationale() { return rationale; }
    public double getConfidence() { return confidence; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final AISuggestion s = new AISuggestion();
        public Builder type(String t) { s.type = t; return this; }
        public Builder suggestion(String sg) { s.suggestion = sg; return this; }
        public Builder rationale(String r) { s.rationale = r; return this; }
        public Builder confidence(double c) { s.confidence = c; return this; }
        public AISuggestion build() { return s; }
    }
}
