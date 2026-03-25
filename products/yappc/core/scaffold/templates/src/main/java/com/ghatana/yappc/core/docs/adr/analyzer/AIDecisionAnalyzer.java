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

package com.ghatana.yappc.core.docs.adr.analyzer;

import com.ghatana.yappc.core.docs.adr.model.ADREnums.*;
import com.ghatana.yappc.core.docs.adr.model.ADRModels.*;

import java.util.*;

/**
 * AI-powered analyzer for architecture decision records.
 * Provides decision analysis, alternative generation, ADR validation, and follow-up questions.
 *
 * @doc.type class
 * @doc.purpose AI-powered analyzer for architecture decision records.
 * @doc.layer platform
 * @doc.pattern Analyzer
 */
public class AIDecisionAnalyzer {

    public AIAnalysisResult analyzeDecision(ADRRequest request, DecisionContext context) {
        // Analyze decision type
        DecisionType decisionType = classifyDecisionType(request.title(), request.decision());

        // Assess complexity
        ComplexityLevel complexity = assessComplexity(request, context);

        // Determine impact level
        ImpactLevel impact = assessImpact(request, context);

        // Calculate confidence
        double confidence = calculateConfidence(request);

        // Generate recommendations
        List<String> recommendations = generateRecommendations(request, context, decisionType);

        // Suggest tags
        List<String> suggestedTags = generateTags(request, decisionType);

        // Find related decisions
        List<String> relatedDecisions = findRelatedDecisions(request, context);

        return new AIAnalysisResult(
                decisionType,
                complexity,
                impact,
                confidence,
                recommendations,
                suggestedTags,
                relatedDecisions,
                Map.of(
                        "analysisMethod", "AI-powered analysis",
                        "keyFactors", identifyKeyFactors(request, context),
                        "riskLevel", assessRiskLevel(complexity, impact)));
    }

    public DecisionAlternatives suggestAlternatives(String decisionTitle, String context) {
        List<Alternative> alternatives = new ArrayList<>();

        // Generate alternatives based on decision type
        if (decisionTitle.toLowerCase().contains("database")) {
            alternatives.addAll(generateDatabaseAlternatives());
        } else if (decisionTitle.toLowerCase().contains("framework")) {
            alternatives.addAll(generateFrameworkAlternatives());
        } else if (decisionTitle.toLowerCase().contains("architecture")) {
            alternatives.addAll(generateArchitecturalAlternatives());
        } else {
            alternatives.addAll(generateGenericAlternatives(decisionTitle));
        }

        // Create comparison matrix
        ComparisonMatrix matrix = createComparisonMatrix(alternatives);

        // Generate recommendations
        List<String> recommendations = generateAlternativeRecommendations(alternatives, matrix);

        return new DecisionAlternatives(decisionTitle, alternatives, matrix, recommendations);
    }

    public ADRValidationResult validateADR(String adrContent) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        // Check structure
        validateADRStructure(adrContent, issues);

        // Check completeness
        validateCompleteness(adrContent, issues, suggestions);

        // Check quality
        validateQuality(adrContent, issues, suggestions);

        // Calculate quality score
        double qualityScore = calculateQualityScore(adrContent, issues);

        return new ADRValidationResult(
                issues.stream().noneMatch(issue -> issue.severity() == IssueSeverity.CRITICAL),
                qualityScore,
                issues,
                suggestions);
    }

    public List<String> generateFollowUpQuestions(ADRRequest request, DecisionContext context) {
        List<String> questions = new ArrayList<>();

        // Context questions
        if (request.context().length() < 100) {
            questions.add("What specific problem or need led to this decision?");
            questions.add("What are the current pain points with the existing solution?");
        }

        // Alternative questions
        if (request.alternatives().size() < 2) {
            questions.add("What other options were considered?");
            questions.add("Why were alternative approaches rejected?");
        }

        // Consequence questions
        if (request.consequences().isEmpty()) {
            questions.add("What are the positive consequences of this decision?");
            questions.add("What are the potential negative consequences or trade-offs?");
            questions.add(
                    "How will this decision impact performance, scalability, or"
                            + " maintainability?");
        }

        // Stakeholder questions
        if (request.stakeholders().isEmpty()) {
            questions.add("Who are the key stakeholders affected by this decision?");
            questions.add("What input did stakeholders provide during the decision process?");
        }

        // Implementation questions
        questions.add("What are the implementation risks and mitigation strategies?");
        questions.add("How will success be measured?");
        questions.add("When should this decision be reviewed or revisited?");

        return questions;
    }

    private DecisionType classifyDecisionType(String title, String decision) {
        String content = (title + " " + decision).toLowerCase();

        if (content.contains("architecture")
                || content.contains("pattern")
                || content.contains("structure")) {
            return DecisionType.ARCHITECTURAL;
        } else if (content.contains("technology")
                || content.contains("framework")
                || content.contains("library")) {
            return DecisionType.TECHNOLOGICAL;
        } else if (content.contains("process")
                || content.contains("workflow")
                || content.contains("methodology")) {
            return DecisionType.PROCESS;
        } else if (content.contains("security")
                || content.contains("authentication")
                || content.contains("authorization")) {
            return DecisionType.SECURITY;
        } else if (content.contains("performance")
                || content.contains("optimization")
                || content.contains("scaling")) {
            return DecisionType.PERFORMANCE;
        } else {
            return DecisionType.QUALITY;
        }
    }

    private ComplexityLevel assessComplexity(ADRRequest request, DecisionContext context) {
        int complexityScore = 0;

        // Factors that increase complexity
        complexityScore += request.alternatives().size() * 2; // Multiple alternatives
        complexityScore += request.stakeholders().size(); // Multiple stakeholders
        complexityScore += context.technologies().size(); // Multiple technologies involved
        complexityScore += request.consequences().size(); // Multiple consequences

        if (request.decision().length() > 500)
            complexityScore += 2; // Long decision description
        if (request.context().length() > 300) complexityScore += 1; // Complex context

        if (complexityScore <= 3) return ComplexityLevel.LOW;
        else if (complexityScore <= 7) return ComplexityLevel.MEDIUM;
        else if (complexityScore <= 12) return ComplexityLevel.HIGH;
        else return ComplexityLevel.VERY_HIGH;
    }

    private ImpactLevel assessImpact(ADRRequest request, DecisionContext context) {
        String content = (request.title() + " " + request.decision()).toLowerCase();

        if (content.contains("system")
                || content.contains("architecture")
                || content.contains("platform")) {
            return ImpactLevel.SYSTEM;
        } else if (content.contains("organization")
                || content.contains("team")
                || content.contains("process")) {
            return ImpactLevel.ORGANIZATION;
        } else if (content.contains("module")
                || content.contains("component")
                || content.contains("service")) {
            return ImpactLevel.MODULE;
        } else {
            return ImpactLevel.LOCAL;
        }
    }

    private double calculateConfidence(ADRRequest request) {
        double confidence = 0.5; // Base confidence

        // Increase confidence with more information
        if (!request.context().isEmpty()) confidence += 0.1;
        if (!request.rationale().isEmpty()) confidence += 0.2;
        if (!request.alternatives().isEmpty()) confidence += 0.1;
        if (!request.consequences().isEmpty()) confidence += 0.1;

        return Math.min(1.0, confidence);
    }

    private List<String> generateRecommendations(
            ADRRequest request, DecisionContext context, DecisionType decisionType) {
        List<String> recommendations = new ArrayList<>();

        if (request.alternatives().size() < 2) {
            recommendations.add(
                    "Consider documenting at least 2-3 alternative approaches that were"
                            + " evaluated");
        }

        if (request.consequences().isEmpty()) {
            recommendations.add("Add both positive and negative consequences of this decision");
        }

        if (request.stakeholders().isEmpty()) {
            recommendations.add(
                    "Identify and document key stakeholders affected by this decision");
        }

        switch (decisionType) {
            case TECHNOLOGICAL ->
                    recommendations.add(
                            "Consider long-term maintenance and community support for chosen"
                                    + " technology");
            case ARCHITECTURAL ->
                    recommendations.add(
                            "Evaluate impact on system scalability and maintainability");
            case SECURITY ->
                    recommendations.add(
                            "Conduct security risk assessment and define mitigation"
                                    + " strategies");
            case PERFORMANCE ->
                    recommendations.add("Define performance metrics and monitoring approach");
            case PROCESS ->
                    recommendations.add(
                            "Consider team adoption and change management requirements");
            case QUALITY ->
                    recommendations.add("Define quality gates and measurement criteria");
        }

        return recommendations;
    }

    private List<String> generateTags(ADRRequest request, DecisionType decisionType) {
        List<String> tags = new ArrayList<>();

        tags.add(decisionType.name().toLowerCase());

        String content = (request.title() + " " + request.decision()).toLowerCase();

        // Extract technology tags
        if (content.contains("java")) tags.add("java");
        if (content.contains("spring")) tags.add("spring");
        if (content.contains("docker")) tags.add("docker");
        if (content.contains("kubernetes")) tags.add("kubernetes");
        if (content.contains("database")) tags.add("database");
        if (content.contains("api")) tags.add("api");
        if (content.contains("microservices")) tags.add("microservices");
        if (content.contains("cloud")) tags.add("cloud");

        // Add status tag
        tags.add(request.status().name().toLowerCase());

        return tags;
    }

    private List<String> findRelatedDecisions(ADRRequest request, DecisionContext context) {
        // In a real implementation, this would search existing ADRs
        return List.of("ADR-001: Technology Stack Selection", "ADR-003: API Design Standards");
    }

    private List<String> identifyKeyFactors(ADRRequest request, DecisionContext context) {
        List<String> factors = new ArrayList<>();

        factors.add("Technical feasibility");
        factors.add("Team expertise");
        factors.add("Time constraints");
        factors.add("Budget considerations");

        if (context.projectType().contains("enterprise")) {
            factors.add("Enterprise compliance");
            factors.add("Vendor support");
        }

        return factors;
    }

    private String assessRiskLevel(ComplexityLevel complexity, ImpactLevel impact) {
        if (complexity == ComplexityLevel.VERY_HIGH && impact == ImpactLevel.SYSTEM) {
            return "HIGH";
        } else if (complexity == ComplexityLevel.HIGH || impact == ImpactLevel.SYSTEM) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private List<Alternative> generateDatabaseAlternatives() {
        return List.of(
                new Alternative(
                        "PostgreSQL",
                        "Open-source relational database",
                        List.of("ACID compliance", "Rich feature set", "Strong community"),
                        List.of("Complex setup", "Resource intensive"),
                        0.9,
                        0.8),
                new Alternative(
                        "MongoDB",
                        "Document-oriented NoSQL database",
                        List.of("Flexible schema", "Horizontal scaling", "JSON-like documents"),
                        List.of("Eventual consistency", "Memory usage"),
                        0.8,
                        0.7),
                new Alternative(
                        "Redis",
                        "In-memory data structure store",
                        List.of(
                                "High performance",
                                "Caching capabilities",
                                "Simple operations"),
                        List.of("Memory limitations", "Persistence concerns"),
                        0.7,
                        0.6));
    }

    private List<Alternative> generateFrameworkAlternatives() {
        return List.of(
                new Alternative(
                        "Spring Boot",
                        "Production-ready Spring framework",
                        List.of(
                                "Auto-configuration",
                                "Embedded servers",
                                "Production features"),
                        List.of("Learning curve", "Opinionated defaults"),
                        0.9,
                        0.9),
                new Alternative(
                        "Quarkus",
                        "Kubernetes-native Java framework",
                        List.of("Fast startup", "Low memory footprint", "Native compilation"),
                        List.of("Newer ecosystem", "Limited libraries"),
                        0.7,
                        0.8),
                new Alternative(
                        "Micronaut",
                        "Modern JVM framework",
                        List.of("Compile-time DI", "Low memory usage", "Fast startup"),
                        List.of("Smaller community", "Learning curve"),
                        0.6,
                        0.7));
    }

    private List<Alternative> generateArchitecturalAlternatives() {
        return List.of(
                new Alternative(
                        "Microservices",
                        "Service-oriented architecture",
                        List.of("Scalability", "Technology diversity", "Team autonomy"),
                        List.of("Complexity", "Network overhead", "Distributed challenges"),
                        0.7,
                        0.9),
                new Alternative(
                        "Monolith",
                        "Single deployable unit",
                        List.of("Simplicity", "Easy testing", "Single deployment"),
                        List.of("Scaling limitations", "Technology lock-in"),
                        0.9,
                        0.6),
                new Alternative(
                        "Modular Monolith",
                        "Modular structure within monolith",
                        List.of("Clear boundaries", "Easier refactoring", "Simpler deployment"),
                        List.of("Shared database", "Coupling risks"),
                        0.8,
                        0.7));
    }

    private List<Alternative> generateGenericAlternatives(String decisionTitle) {
        return List.of(
                new Alternative(
                        "Current Approach",
                        "Continue with existing solution",
                        List.of("No change risk", "Known limitations", "Team familiarity"),
                        List.of("Technical debt", "Limited scalability"),
                        0.9,
                        0.5),
                new Alternative(
                        "Incremental Improvement",
                        "Gradual enhancement",
                        List.of("Lower risk", "Continuous improvement", "Manageable change"),
                        List.of("Slower progress", "Partial benefits"),
                        0.8,
                        0.6),
                new Alternative(
                        "Complete Replacement",
                        "Full solution replacement",
                        List.of("Fresh start", "Modern approach", "Best practices"),
                        List.of("High risk", "Significant effort", "Learning curve"),
                        0.5,
                        0.9));
    }

    private ComparisonMatrix createComparisonMatrix(List<Alternative> alternatives) {
        List<String> criteria =
                List.of("Feasibility", "Impact", "Risk", "Effort", "Maintainability");
        Map<String, Map<String, Double>> scores = new HashMap<>();

        for (Alternative alt : alternatives) {
            Map<String, Double> altScores = new HashMap<>();
            altScores.put("Feasibility", alt.feasibilityScore());
            altScores.put("Impact", alt.impactScore());
            altScores.put("Risk", 1.0 - alt.feasibilityScore() * 0.5); // Inverse relationship
            altScores.put("Effort", 1.0 - alt.feasibilityScore() * 0.7);
            altScores.put("Maintainability", alt.impactScore() * 0.8);

            scores.put(alt.name(), altScores);
        }

        return new ComparisonMatrix(criteria, scores);
    }

    private List<String> generateAlternativeRecommendations(
            List<Alternative> alternatives, ComparisonMatrix matrix) {
        List<String> recommendations = new ArrayList<>();

        // Find highest scoring alternative
        Alternative best =
                alternatives.stream()
                        .max(
                                Comparator.comparing(
                                        alt -> alt.feasibilityScore() * alt.impactScore()))
                        .orElse(alternatives.get(0));

        recommendations.add(
                "Recommended: " + best.name() + " - Best balance of feasibility and impact");

        // Add specific recommendations
        recommendations.add(
                "Consider conducting a proof of concept for the top 2 alternatives");
        recommendations.add("Evaluate long-term maintenance costs for each option");
        recommendations.add("Assess team skill requirements and training needs");

        return recommendations;
    }

    private void validateADRStructure(String content, List<ValidationIssue> issues) {
        // Check for required sections
        if (!content.contains("# ") && !content.contains("## Title")) {
            issues.add(
                    new ValidationIssue(
                            IssueSeverity.CRITICAL,
                            "Title",
                            "Missing title section",
                            "Add a clear title describing the decision"));
        }

        if (!content.toLowerCase().contains("status")) {
            issues.add(
                    new ValidationIssue(
                            IssueSeverity.ERROR,
                            "Status",
                            "Missing status section",
                            "Add status (Proposed, Accepted, Deprecated, etc.)"));
        }

        if (!content.toLowerCase().contains("context")) {
            issues.add(
                    new ValidationIssue(
                            IssueSeverity.ERROR,
                            "Context",
                            "Missing context section",
                            "Describe the situation that led to this decision"));
        }

        if (!content.toLowerCase().contains("decision")) {
            issues.add(
                    new ValidationIssue(
                            IssueSeverity.CRITICAL,
                            "Decision",
                            "Missing decision section",
                            "Clearly state what was decided"));
        }
    }

    private void validateCompleteness(
            String content, List<ValidationIssue> issues, List<String> suggestions) {
        if (content.length() < 200) {
            issues.add(
                    new ValidationIssue(
                            IssueSeverity.WARNING,
                            "Content",
                            "ADR appears too brief",
                            "Consider adding more detail to context and rationale"));
        }

        if (!content.toLowerCase().contains("rationale")
                && !content.toLowerCase().contains("consequences")) {
            suggestions.add("Add rationale explaining why this decision was made");
        }

        if (!content.toLowerCase().contains("alternative")) {
            suggestions.add("Document alternative options that were considered");
        }
    }

    private void validateQuality(
            String content, List<ValidationIssue> issues, List<String> suggestions) {
        // Check for vague language
        String[] vagueTerms = {"should", "might", "could", "probably", "maybe"};
        for (String term : vagueTerms) {
            if (content.toLowerCase().contains(term)) {
                suggestions.add(
                        "Consider replacing vague terms like '"
                                + term
                                + "' with more definitive language");
                break;
            }
        }

        // Check for active voice
        if (content.toLowerCase().contains("was decided")
                || content.toLowerCase().contains("will be")) {
            suggestions.add("Use active voice for clearer decision statements");
        }
    }

    private double calculateQualityScore(String content, List<ValidationIssue> issues) {
        double baseScore = 1.0;

        for (ValidationIssue issue : issues) {
            switch (issue.severity()) {
                case CRITICAL -> baseScore -= 0.3;
                case ERROR -> baseScore -= 0.2;
                case WARNING -> baseScore -= 0.1;
                case INFO -> baseScore -= 0.05;
            }
        }

        // Bonus for completeness
        if (content.length() > 500) baseScore += 0.1;
        if (content.toLowerCase().contains("alternative")) baseScore += 0.05;
        if (content.toLowerCase().contains("consequence")) baseScore += 0.05;

        return Math.max(0.0, Math.min(1.0, baseScore));
    }
}
