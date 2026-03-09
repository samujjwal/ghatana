package com.ghatana.requirements.ai.dto;

import java.util.ArrayList;
import java.util.List;

/**

 * @doc.type class

 * @doc.purpose Handles requirement quality result operations

 * @doc.layer core

 * @doc.pattern ValueObject

 */

public class RequirementQualityResult {
    private double score;
    private boolean valid;
    private List<String> issues;
    private List<String> suggestions;

    private RequirementQualityResult() {}

    public double getScore() { return score; }
    public boolean isValid() { return valid; }
    public List<String> getIssues() { return issues; }
    public List<String> getSuggestions() { return suggestions; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final RequirementQualityResult r = new RequirementQualityResult();
        public Builder score(double s) { r.score = s; return this; }
        public Builder isValid(boolean v) { r.valid = v; return this; }
        public Builder issues(List<String> i) { r.issues = i; return this; }
        public Builder suggestions(List<String> s) { r.suggestions = s; return this; }
        public RequirementQualityResult build() { return r; }
    }
}
