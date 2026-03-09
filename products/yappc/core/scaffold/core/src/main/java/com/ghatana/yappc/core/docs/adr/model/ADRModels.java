/*
 * YAPPC - Yet Another Project/Package Creator
 * Copyright (c) 2025 Ghatana
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.core.docs.adr.model;

import com.ghatana.yappc.core.docs.adr.model.ADREnums.*;

import java.time.Instant;
import java.util.*;

/**
 * Domain models for Architecture Decision Records (ADR).
 * Contains all record types used for ADR generation, analysis, and validation.
 *
 * @doc.type class
 * @doc.purpose Domain models for Architecture Decision Records (ADR).
 * @doc.layer platform
 * @doc.pattern Component
 */
public final class ADRModels {

    private ADRModels() {
        // Utility class
    }

    /**
 * Request for generating an Architecture Decision Record. */
    public record ADRRequest(
            String title,
            String context,
            String decision,
            String rationale,
            List<String> alternatives,
            List<String> consequences,
            List<String> stakeholders,
            DecisionStatus status,
            Map<String, Object> projectInfo) {
        
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String title;
            private String context = "";
            private String decision = "";
            private String rationale = "";
            private List<String> alternatives = new ArrayList<>();
            private List<String> consequences = new ArrayList<>();
            private List<String> stakeholders = new ArrayList<>();
            private DecisionStatus status = DecisionStatus.PROPOSED;
            private Map<String, Object> projectInfo = new HashMap<>();

            public Builder title(String title) {
                this.title = title;
                return this;
            }

            public Builder context(String context) {
                this.context = context;
                return this;
            }

            public Builder decision(String decision) {
                this.decision = decision;
                return this;
            }

            public Builder rationale(String rationale) {
                this.rationale = rationale;
                return this;
            }

            public Builder alternatives(List<String> alternatives) {
                this.alternatives = alternatives;
                return this;
            }

            public Builder consequences(List<String> consequences) {
                this.consequences = consequences;
                return this;
            }

            public Builder stakeholders(List<String> stakeholders) {
                this.stakeholders = stakeholders;
                return this;
            }

            public Builder status(DecisionStatus status) {
                this.status = status;
                return this;
            }

            public Builder projectInfo(Map<String, Object> projectInfo) {
                this.projectInfo = projectInfo;
                return this;
            }

            public ADRRequest build() {
                return new ADRRequest(
                        title,
                        context,
                        decision,
                        rationale,
                        alternatives,
                        consequences,
                        stakeholders,
                        status,
                        projectInfo);
            }
        }
    }

    /**
 * Result of ADR generation including content and metadata. */
    public record ADRGenerationResult(
            String content,
            ADRTemplateType templateType,
            double confidence,
            List<String> recommendations,
            Map<String, Object> metadata,
            Instant generatedAt) {}

    /**
 * Context information about the project and environment. */
    public record DecisionContext(
            String projectType,
            List<String> technologies,
            String architecturalStyle,
            Map<String, Object> constraints,
            List<String> existingDecisions) {}

    /**
 * Result of AI-powered decision analysis. */
    public record AIAnalysisResult(
            DecisionType decisionType,
            ComplexityLevel complexity,
            ImpactLevel impact,
            double confidence,
            List<String> recommendations,
            List<String> suggestedTags,
            List<String> relatedDecisions,
            Map<String, Object> analysisDetails) {}

    /**
 * Alternative options for a decision with comparison data. */
    public record DecisionAlternatives(
            String originalDecision,
            List<Alternative> alternatives,
            ComparisonMatrix comparisonMatrix,
            List<String> recommendations) {}

    /**
 * A single alternative option for a decision. */
    public record Alternative(
            String name,
            String description,
            List<String> pros,
            List<String> cons,
            double feasibilityScore,
            double impactScore) {}

    /**
 * Matrix for comparing alternatives across criteria. */
    public record ComparisonMatrix(
            List<String> criteria, 
            Map<String, Map<String, Double>> scores) {}

    /**
 * Result of ADR validation including quality score and issues. */
    public record ADRValidationResult(
            boolean isValid,
            double qualityScore,
            List<ValidationIssue> issues,
            List<String> suggestions) {}

    /**
 * A single validation issue found in an ADR. */
    public record ValidationIssue(
            IssueSeverity severity, 
            String section, 
            String message, 
            String suggestion) {}
}
