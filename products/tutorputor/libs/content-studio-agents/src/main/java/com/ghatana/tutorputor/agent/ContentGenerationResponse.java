package com.ghatana.tutorputor.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Response from the content generation agent.
 *
 * @doc.type record
 * @doc.purpose Content generation response DTO
 * @doc.layer product
 * @doc.pattern Value Object
 *
 * @param content the generated content
 * @param domain the subject domain
 * @param gradeLevel the target grade level
 * @param contentType the type of content generated
 * @param qualityScore quality score from 0.0 to 1.0
 * @param curriculumAligned whether content aligns with curriculum standards
 * @param alignedTopics curriculum topics this content aligns with
 * @param validationIssues any validation issues found
 * @param generationTimeMs time taken to generate in milliseconds
 * @param tokenCount number of tokens used
 * @param metadata additional metadata
 */
public record ContentGenerationResponse(
    @NotNull String content,
    @NotNull String domain,
    @NotNull String gradeLevel,
    @NotNull ContentGenerationRequest.ContentType contentType,
    double qualityScore,
    boolean curriculumAligned,
    @Nullable List<String> alignedTopics,
    @Nullable List<String> validationIssues,
    long generationTimeMs,
    int tokenCount,
    @Nullable Map<String, Object> metadata
) {
    /**
     * Creates a builder for ContentGenerationResponse.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a copy with validation issues added.
     *
     * @param issues the validation issues
     * @return a new response with issues
     */
    public ContentGenerationResponse withValidationIssues(List<String> issues) {
        return new ContentGenerationResponse(
            content, domain, gradeLevel, contentType, qualityScore,
            curriculumAligned, alignedTopics, issues,
            generationTimeMs, tokenCount, metadata
        );
    }

    /**
     * Returns a copy with curriculum alignment information.
     *
     * @param aligned whether aligned
     * @param topics aligned topics
     * @return a new response with alignment info
     */
    public ContentGenerationResponse withCurriculumAlignment(boolean aligned, List<String> topics) {
        return new ContentGenerationResponse(
            content, domain, gradeLevel, contentType, qualityScore,
            aligned, topics, validationIssues,
            generationTimeMs, tokenCount, metadata
        );
    }

    /**
     * Builder for ContentGenerationResponse.
     */
    public static class Builder {
        private String content;
        private String domain;
        private String gradeLevel;
        private ContentGenerationRequest.ContentType contentType;
        private double qualityScore = 0.5;
        private boolean curriculumAligned = false;
        private List<String> alignedTopics;
        private List<String> validationIssues;
        private long generationTimeMs = 0;
        private int tokenCount = 0;
        private Map<String, Object> metadata;

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public Builder gradeLevel(String gradeLevel) {
            this.gradeLevel = gradeLevel;
            return this;
        }

        public Builder contentType(ContentGenerationRequest.ContentType contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder qualityScore(double qualityScore) {
            this.qualityScore = qualityScore;
            return this;
        }

        public Builder curriculumAligned(boolean curriculumAligned) {
            this.curriculumAligned = curriculumAligned;
            return this;
        }

        public Builder alignedTopics(List<String> alignedTopics) {
            this.alignedTopics = alignedTopics;
            return this;
        }

        public Builder validationIssues(List<String> validationIssues) {
            this.validationIssues = validationIssues;
            return this;
        }

        public Builder generationTimeMs(long generationTimeMs) {
            this.generationTimeMs = generationTimeMs;
            return this;
        }

        public Builder tokenCount(int tokenCount) {
            this.tokenCount = tokenCount;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ContentGenerationResponse build() {
            return new ContentGenerationResponse(
                content, domain, gradeLevel, contentType, qualityScore,
                curriculumAligned, alignedTopics, validationIssues,
                generationTimeMs, tokenCount, metadata
            );
        }
    }
}
