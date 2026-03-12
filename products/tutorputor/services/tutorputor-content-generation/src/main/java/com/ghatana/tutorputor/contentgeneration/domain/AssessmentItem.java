package com.ghatana.tutorputor.explorer.model;

import java.util.List;

public class AssessmentItem {
    private final String id;
    private final String question;
    private final List<String> options;
    private final int correctAnswerIndex;
    
    public AssessmentItem(String id, String question, List<String> options, int correctAnswerIndex) {
        this.id = id; this.question = question; this.options = options; this.correctAnswerIndex = correctAnswerIndex;
    }
    
    public String getId() { return id; }
    public String getQuestion() { return question; }
    public List<String> getOptions() { return options; }
    public int getCorrectAnswerIndex() { return correctAnswerIndex; }
    
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private String id, question;
        private List<String> options;
        private int correctAnswerIndex;
        public Builder id(String id) { this.id = id; return this; }
        public Builder question(String question) { this.question = question; return this; }
        public Builder options(List<String> options) { this.options = options; return this; }
        public Builder correctAnswerIndex(int correctAnswerIndex) { this.correctAnswerIndex = correctAnswerIndex; return this; }
        public AssessmentItem build() { return new AssessmentItem(id, question, options, correctAnswerIndex); }
    }
}
