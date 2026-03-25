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

package com.ghatana.yappc.core.docs.adr.template;

import com.ghatana.yappc.core.docs.adr.model.ADREnums.*;
import com.ghatana.yappc.core.docs.adr.model.ADRModels.*;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Template engine for ADR generation.
 * Selects appropriate template based on decision characteristics and generates content.
 *
 * @doc.type class
 * @doc.purpose Template engine for ADR generation.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class ADRTemplateEngine {

    private final Map<ADRTemplateType, ADRTemplate> templates;

    public ADRTemplateEngine() {
        this.templates = initializeTemplates();
    }

    /**
     * Selects the most appropriate template based on analysis results.
     * 
     * @param analysis AI analysis results for the decision
     * @return Selected ADR template
     */
    public ADRTemplate selectTemplate(AIAnalysisResult analysis) {
        // Select template based on complexity and type
        if (analysis.complexity() == ComplexityLevel.LOW) {
            return templates.get(ADRTemplateType.BRIEF);
        } else if (analysis.complexity() == ComplexityLevel.VERY_HIGH) {
            return templates.get(ADRTemplateType.DETAILED);
        } else if (analysis.decisionType() == DecisionType.ARCHITECTURAL) {
            return templates.get(ADRTemplateType.Y_STATEMENT);
        } else {
            return templates.get(ADRTemplateType.STANDARD);
        }
    }

    /**
     * Generates ADR content using the specified template.
     * 
     * @param template ADR template to use
     * @param request ADR request with decision details
     * @param analysis AI analysis results
     * @param context Decision context
     * @return Generated ADR content
     */
    public String generateADRContent(
            ADRTemplate template,
            ADRRequest request,
            AIAnalysisResult analysis,
            DecisionContext context) {

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("request", request);
        templateData.put("analysis", analysis);
        templateData.put("context", context);
        templateData.put(
                "date", DateTimeFormatter.ISO_LOCAL_DATE.format(java.time.LocalDate.now()));
        templateData.put("timestamp", Instant.now());

        return template.generate(templateData);
    }

    private Map<ADRTemplateType, ADRTemplate> initializeTemplates() {
        Map<ADRTemplateType, ADRTemplate> templates = new HashMap<>();

        templates.put(ADRTemplateType.STANDARD, new StandardADRTemplate());
        templates.put(ADRTemplateType.BRIEF, new BriefADRTemplate());
        templates.put(ADRTemplateType.DETAILED, new DetailedADRTemplate());
        templates.put(ADRTemplateType.Y_STATEMENT, new YStatementADRTemplate());
        templates.put(ADRTemplateType.MADR, new MADRADRTemplate());

        return templates;
    }
}
