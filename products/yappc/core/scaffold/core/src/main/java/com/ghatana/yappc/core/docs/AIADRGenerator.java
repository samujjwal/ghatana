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
package com.ghatana.yappc.core.docs;

import com.ghatana.yappc.core.docs.adr.analyzer.AIDecisionAnalyzer;
import com.ghatana.yappc.core.docs.adr.context.DecisionContextExtractor;
import com.ghatana.yappc.core.docs.adr.model.ADRModels.*;
import com.ghatana.yappc.core.docs.adr.template.ADRTemplate;
import com.ghatana.yappc.core.docs.adr.template.ADRTemplateEngine;

import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI-powered Architecture Decision Record (ADR) generator with templates and
 * intelligent assistance.
 *
 * <p>
 * Week 10 Day 48: ADR templates with decision rationale (AI assists drafting).
 *
 * @doc.type class
 * @doc.purpose AI-powered Architecture Decision Record (ADR) generator with
 * templates and intelligent
 * @doc.layer platform
 * @doc.pattern Generator
 */
public class AIADRGenerator {

    private static final Logger log = LoggerFactory.getLogger(AIADRGenerator.class);

    private final AIDecisionAnalyzer analyzer;
    private final ADRTemplateEngine templateEngine;
    private final DecisionContextExtractor contextExtractor;

    public AIADRGenerator() {
        this.analyzer = new AIDecisionAnalyzer();
        this.templateEngine = new ADRTemplateEngine();
        this.contextExtractor = new DecisionContextExtractor();
    }

    /**
     * Generates an Architecture Decision Record with AI assistance.
     */
    public ADRGenerationResult generateADR(ADRRequest request) {
        log.info("🤖 AI ADR Generator: Analyzing decision context...");

        // Extract context from project and decision
        DecisionContext context = contextExtractor.extractContext(request);

        // Analyze decision with AI
        AIAnalysisResult analysis = analyzer.analyzeDecision(request, context);

        // Select appropriate template
        ADRTemplate template = templateEngine.selectTemplate(analysis);

        // Generate ADR content
        String adrContent = templateEngine.generateADRContent(template, request, analysis, context);

        // Generate metadata
        Map<String, Object> metadata = generateMetadata(request, analysis, context);

        return new ADRGenerationResult(
                adrContent,
                template.templateType(),
                analysis.confidence(),
                analysis.recommendations(),
                metadata,
                Instant.now());
    }

    /**
     * Suggests decision alternatives and trade-offs using AI analysis.
     */
    public DecisionAlternatives suggestAlternatives(String decisionTitle, String currentContext) {
        return analyzer.suggestAlternatives(decisionTitle, currentContext);
    }

    /**
     * Validates an existing ADR for completeness and quality.
     */
    public ADRValidationResult validateADR(String adrContent) {
        return analyzer.validateADR(adrContent);
    }

    /**
     * Generates follow-up questions to improve decision rationale.
     */
    public List<String> generateFollowUpQuestions(ADRRequest request) {
        DecisionContext context = contextExtractor.extractContext(request);
        return analyzer.generateFollowUpQuestions(request, context);
    }

    private Map<String, Object> generateMetadata(
            ADRRequest request, AIAnalysisResult analysis, DecisionContext context) {
        Map<String, Object> metadata = new HashMap<>();

        metadata.put("decisionType", analysis.decisionType());
        metadata.put("complexity", analysis.complexity());
        metadata.put("impact", analysis.impact());
        metadata.put("confidence", analysis.confidence());
        metadata.put("stakeholders", request.stakeholders());
        metadata.put("tags", analysis.suggestedTags());
        metadata.put("relatedDecisions", analysis.relatedDecisions());
        metadata.put("projectContext", context.projectType());
        metadata.put("generatedAt", Instant.now());

        return metadata;
    }

    // All model classes, enums, templates, analyzer, and context extractor
    // have been extracted to dedicated packages:
    // - com.ghatana.yappc.core.docs.adr.model (ADREnums, ADRModels)
    // - com.ghatana.yappc.core.docs.adr.template (templates and engine)
    // - com.ghatana.yappc.core.docs.adr.analyzer (AIDecisionAnalyzer)
    // - com.ghatana.yappc.core.docs.adr.context (DecisionContextExtractor)
}
