package com.ghatana.tutorputor.explorer.model;

import java.util.List;

public class QualityReport {
    private final boolean passed;
    private final double overallScore;
    private final List<String> issues;
    
    public QualityReport(boolean passed, double overallScore, List<String> issues) {
        this.passed = passed; this.overallScore = overallScore; this.issues = issues;
    }
    
    public boolean isPassed() { return passed; }
    public double getOverallScore() { return overallScore; }
    public List<String> getIssues() { return issues; }
    
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private boolean passed;
        private double overallScore;
        private List<String> issues;
        public Builder passed(boolean passed) { this.passed = passed; return this; }
        public Builder overallScore(double overallScore) { this.overallScore = overallScore; return this; }
        public Builder issues(List<String> issues) { this.issues = issues; return this; }
        public QualityReport build() { return new QualityReport(passed, overallScore, issues); }
    }
}
