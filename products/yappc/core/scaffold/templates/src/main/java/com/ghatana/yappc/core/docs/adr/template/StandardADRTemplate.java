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

import java.util.Map;

/**
 * Standard ADR template with comprehensive sections.
 * Includes context, decision, rationale, alternatives, consequences, and metadata.
 *
 * @doc.type class
 * @doc.purpose Standard ADR template with comprehensive sections.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class StandardADRTemplate implements ADRTemplate {
    
    @Override
    public ADRTemplateType templateType() {
        return ADRTemplateType.STANDARD;
    }

    @Override
    public String generate(Map<String, Object> data) {
        ADRRequest request = (ADRRequest) data.get("request");
        AIAnalysisResult analysis = (AIAnalysisResult) data.get("analysis");
        String date = (String) data.get("date");

        return String.format(
                """
            # %s

            **Status:** %s
            **Date:** %s
            **Decision Type:** %s
            **Impact:** %s
            **Tags:** %s

            ## Context

            %s

            ## Decision

            %s

            ## Rationale

            %s

            ## Alternatives Considered

            %s

            ## Consequences

            ### Positive
            %s

            ### Negative
            %s

            ## Implementation Notes

            - Timeline: To be determined
            - Responsible parties: %s
            - Success metrics: To be defined

            ## Related Decisions

            %s

            ---
            *This ADR was generated with AI assistance by YAPPC on %s*
            """,
                request.title(),
                request.status(),
                date,
                analysis.decisionType(),
                analysis.impact(),
                String.join(", ", analysis.suggestedTags()),
                request.context().isEmpty() ? "*Context to be added*" : request.context(),
                request.decision().isEmpty() ? "*Decision to be added*" : request.decision(),
                request.rationale().isEmpty() ? "*Rationale to be added*" : request.rationale(),
                request.alternatives().isEmpty()
                        ? "- *No alternatives documented*"
                        : request.alternatives().stream()
                                .map(alt -> "- " + alt)
                                .reduce("", (a, b) -> a + "\n" + b),
                request.consequences().isEmpty()
                        ? "- *To be determined*"
                        : request.consequences().stream()
                                .filter(c -> !c.toLowerCase().contains("negative"))
                                .map(c -> "- " + c)
                                .reduce("", (a, b) -> a + "\n" + b),
                request.consequences().isEmpty()
                        ? "- *To be determined*"
                        : request.consequences().stream()
                                .filter(c -> c.toLowerCase().contains("negative"))
                                .map(c -> "- " + c)
                                .reduce("", (a, b) -> a + "\n" + b),
                request.stakeholders().isEmpty()
                        ? "*To be identified*"
                        : String.join(", ", request.stakeholders()),
                analysis.relatedDecisions().isEmpty()
                        ? "- *No related decisions identified*"
                        : analysis.relatedDecisions().stream()
                                .map(rel -> "- " + rel)
                                .reduce("", (a, b) -> a + "\n" + b),
                data.get("timestamp"));
    }
}
