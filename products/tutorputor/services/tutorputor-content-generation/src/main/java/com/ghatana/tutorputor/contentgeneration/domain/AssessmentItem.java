package com.ghatana.tutorputor.contentgeneration.domain;

import java.util.List;

/**
 * Evidence-Centered Assessment Item
 *
 * Represents an assessment item grounded in evidence from the knowledge base.
 * Includes evidence references and confidence scores for ECA implementation.
 *
 * @doc.type class
 * @doc.purpose Provides evidence-centered assessment item functionality.
 * @doc.layer product
 * @doc.pattern Component
 */
public class AssessmentItem {
    private final String id;
    private final String question;
    private final List<String> options;
    private final int correctAnswerIndex;
    private final String evidenceReference;
    private final double confidenceScore;
    private final String bloomLevel;

    public AssessmentItem(String id, String question, List<String> options, int correctAnswerIndex,
                          String evidenceReference, double confidenceScore, String bloomLevel) {
        this.id = id;
        this.question = question;
        this.options = options;
        this.correctAnswerIndex = correctAnswerIndex;
        this.evidenceReference = evidenceReference;
        this.confidenceScore = confidenceScore;
        this.bloomLevel = bloomLevel;
    }

    public String getId() { return id; }
    public String getQuestion() { return question; }
    public List<String> getOptions() { return options; }
    public int getCorrectAnswerIndex() { return correctAnswerIndex; }
    public String getEvidenceReference() { return evidenceReference; }
    public double getConfidenceScore() { return confidenceScore; }
    public String getBloomLevel() { return bloomLevel; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id, question, evidenceReference, bloomLevel;
        private List<String> options;
        private int correctAnswerIndex;
        private double confidenceScore = 0.5;

        public Builder id(String id) { this.id = id; return this; }
        public Builder question(String question) { this.question = question; return this; }
        public Builder options(List<String> options) { this.options = options; return this; }
        public Builder correctAnswerIndex(int correctAnswerIndex) { this.correctAnswerIndex = correctAnswerIndex; return this; }
        public Builder evidenceReference(String evidenceReference) { this.evidenceReference = evidenceReference; return this; }
        public Builder confidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; return this; }
        public Builder bloomLevel(String bloomLevel) { this.bloomLevel = bloomLevel; return this; }
        public AssessmentItem build() { 
            return new AssessmentItem(id, question, options, correctAnswerIndex, evidenceReference, confidenceScore, bloomLevel); 
        }
    }
}
