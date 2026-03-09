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

import com.ghatana.yappc.core.docs.adr.model.ADREnums.ADRTemplateType;
import com.ghatana.yappc.core.docs.adr.model.ADRModels.*;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Detailed ADR template with comprehensive documentation.
 * Includes executive summary, risk assessment, implementation plan, and appendices.
 *
 * @doc.type class
 * @doc.purpose Detailed ADR template with comprehensive documentation.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class DetailedADRTemplate implements ADRTemplate {
    
    @Override
    public ADRTemplateType templateType() {
        return ADRTemplateType.DETAILED;
    }

    @Override
    public String generate(Map<String, Object> data) {
        ADRRequest request = (ADRRequest) data.get("request");
        AIAnalysisResult analysis = (AIAnalysisResult) data.get("analysis");
        DecisionContext context = (DecisionContext) data.get("context");
        String date = (String) data.get("date");

        return String.format(
                """
            # ADR: %s

            | Field | Value |
            |-------|-------|
            | Status | %s |
            | Date | %s |
            | Decision Type | %s |
            | Complexity | %s |
            | Impact Level | %s |
            | Confidence | %.1f%% |
            | Project Type | %s |

            ## Executive Summary

            %s

            ## Business Context

            %s

            ## Technical Context

            **Technologies Involved:** %s
            **Architectural Style:** %s
            **Constraints:** %s

            ## Problem Statement

            %s

            ## Decision

            %s

            ## Detailed Rationale

            %s

            ## Alternatives Analysis

            %s

            ## Risk Assessment

            **Risk Level:** %s
            **Key Risks:**
            - Implementation complexity
            - Team skill requirements
            - Technology maturity
            - Maintenance burden

            ## Implementation Plan

            1. **Phase 1:** Planning and preparation
            2. **Phase 2:** Core implementation
            3. **Phase 3:** Testing and validation
            4. **Phase 4:** Deployment and monitoring

            ## Success Criteria

            - [ ] Implementation completed within timeline
            - [ ] Performance requirements met
            - [ ] Team training completed
            - [ ] Documentation updated

            ## Monitoring and Review

            **Review Date:** %s (6 months from decision)
            **Key Metrics:**
            - Performance indicators
            - Adoption rates
            - Issue resolution times

            ## Stakeholder Communication

            **Stakeholders:** %s
            **Communication Plan:** Regular updates through team meetings and status reports

            ## Related Decisions

            %s

            ## Appendices

            ### A. Technical Specifications
            *To be added during implementation*

            ### B. Performance Benchmarks
            *To be measured post-implementation*

            ### C. Migration Guide
            *To be created if applicable*

            ---
            *Detailed ADR generated with AI assistance by YAPPC on %s*
            """,
                request.title(),
                request.status(),
                date,
                analysis.decisionType(),
                analysis.complexity(),
                analysis.impact(),
                analysis.confidence() * 100,
                context.projectType(),
                request.decision().isEmpty()
                        ? "*Executive summary to be added*"
                        : request.decision()
                                        .substring(
                                                0, Math.min(request.decision().length(), 200))
                                + "...",
                request.context().isEmpty()
                        ? "*Business context to be added*"
                        : request.context(),
                String.join(", ", context.technologies()),
                context.architecturalStyle(),
                context.constraints().isEmpty()
                        ? "*No specific constraints identified*"
                        : context.constraints().toString(),
                request.context().isEmpty()
                        ? "*Problem statement to be added*"
                        : request.context(),
                request.decision().isEmpty() ? "*Decision to be added*" : request.decision(),
                request.rationale().isEmpty()
                        ? "*Detailed rationale to be added*"
                        : request.rationale(),
                request.alternatives().isEmpty()
                        ? "*No alternatives documented*"
                        : request.alternatives().stream()
                                .map(alt -> "- **" + alt + ":** Analysis to be added")
                                .reduce("", (a, b) -> a + "\n" + b),
                analysis.analysisDetails().getOrDefault("riskLevel", "MEDIUM"),
                java.time.LocalDate.now()
                        .plusMonths(6)
                        .format(DateTimeFormatter.ISO_LOCAL_DATE),
                request.stakeholders().isEmpty()
                        ? "*To be identified*"
                        : String.join(", ", request.stakeholders()),
                analysis.relatedDecisions().isEmpty()
                        ? "*No related decisions identified*"
                        : analysis.relatedDecisions().stream()
                                .map(rel -> "- " + rel)
                                .reduce("", (a, b) -> a + "\n" + b),
                data.get("timestamp"));
    }
}
