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

package com.ghatana.yappc.core.docs.adr.context;

import com.ghatana.yappc.core.docs.adr.model.ADRModels.*;

import java.util.*;

/**
 * Extracts decision context from project information.
 * Provides environmental and project-specific context for decision analysis.
 *
 * @doc.type class
 * @doc.purpose Extracts decision context from project information.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class DecisionContextExtractor {

    /**
     * Extracts context information from the ADR request.
     * 
     * @param request ADR request containing project information
     * @return Decision context with project details
     */
    public DecisionContext extractContext(ADRRequest request) {
        Map<String, Object> projectInfo = request.projectInfo();

        String projectType = (String) projectInfo.getOrDefault("projectType", "Unknown");

        @SuppressWarnings("unchecked")
        List<String> technologies =
                (List<String>)
                        projectInfo.getOrDefault(
                                "technologies",
                                List.of("Java", "Spring Boot")); // Default assumptions

        String architecturalStyle =
                (String) projectInfo.getOrDefault("architecturalStyle", "Layered");

        @SuppressWarnings("unchecked")
        Map<String, Object> constraints =
                (Map<String, Object>)
                        projectInfo.getOrDefault(
                                "constraints",
                                Map.of("budget", "limited", "timeline", "6 months"));

        List<String> existingDecisions = List.of(); // Would be populated from existing ADRs

        return new DecisionContext(
                projectType, technologies, architecturalStyle, constraints, existingDecisions);
    }
}
