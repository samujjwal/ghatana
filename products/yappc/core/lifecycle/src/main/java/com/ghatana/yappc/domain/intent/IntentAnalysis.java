package com.ghatana.yappc.domain.intent;

import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Analysis result of intent specification
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record IntentAnalysis(
    String intentId,
    boolean feasible,
    List<String> risks,
    List<String> gaps,
    List<String> assumptions,
    Map<String, Double> scores,
    String summary
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String intentId;
        private boolean feasible = true;
        private List<String> risks = List.of();
        private List<String> gaps = List.of();
        private List<String> assumptions = List.of();
        private Map<String, Double> scores = Map.of();
        private String summary;
        
        public Builder intentId(String intentId) {
            this.intentId = intentId;
            return this;
        }
        
        public Builder feasible(boolean feasible) {
            this.feasible = feasible;
            return this;
        }
        
        public Builder risks(List<String> risks) {
            this.risks = risks;
            return this;
        }
        
        public Builder gaps(List<String> gaps) {
            this.gaps = gaps;
            return this;
        }
        
        public Builder assumptions(List<String> assumptions) {
            this.assumptions = assumptions;
            return this;
        }
        
        public Builder scores(Map<String, Double> scores) {
            this.scores = scores;
            return this;
        }
        
        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }
        
        public IntentAnalysis build() {
            return new IntentAnalysis(intentId, feasible, risks, gaps, assumptions, scores, summary);
        }
    }
}
