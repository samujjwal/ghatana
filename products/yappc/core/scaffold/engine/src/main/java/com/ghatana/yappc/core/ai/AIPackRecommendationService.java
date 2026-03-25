/*
 * Copyright (c) 2025 Ghatana Platform Contributors
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

package com.ghatana.yappc.core.ai;

import com.ghatana.yappc.core.ai.model.AIPromptRequest;
import com.ghatana.yappc.core.ai.model.PackRecommendation;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Day 16: AI Pack Recommendation Service using LangChain4J.
 *
 * <p>Connects to configured model provider, scoring packs using metadata from Weeks 1-3. Implements
 * governance requirements per Doc1 §6 (AI policies) with audit trail.
 *
 * @doc.type class
 * @doc.purpose Day 16: AI Pack Recommendation Service using LangChain4J.
 * @doc.layer platform
 * @doc.pattern Service
 */
public class AIPackRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(AIPackRecommendationService.class);

    // Mock AI provider configuration - would connect to actual LangChain4J in production
    private static final String DEFAULT_MODEL_PROVIDER = "openai";
    private static final boolean AI_FEATURES_ENABLED = true; // Opt-in toggle per Doc1 §6

    /**
     * Get AI-powered pack recommendations based on user requirements.
     *
     * @param request The AI prompt request with user preferences
     * @return List of ranked pack recommendations with explanations
     */
    public List<PackRecommendation> getRecommendations(AIPromptRequest request) {
        // Doc1 §6 Governance: Check AI opt-in policy
        if (!AI_FEATURES_ENABLED) {
            throw new IllegalStateException("AI features are disabled. Enable with opt-in policy.");
        }

        // Log usage in audit trail (Doc1 §6 requirement)
        auditAIUsage(request);

        // Simulate LangChain4J service integration
        // In production, this would call actual AI model provider
        return generateMockRecommendations(request);
    }

    private void auditAIUsage(AIPromptRequest request) {
        // Doc1 §6: Log usage in audit trail
        log.info("[AUDIT] AI recommendation request: model={}, type={}, lang={}", request.getAiModel(), request.getProjectType(), request.getPrimaryLanguage());
    }

    private List<PackRecommendation> generateMockRecommendations(AIPromptRequest request) {
        // Mock implementation - in production would integrate with LangChain4J
        // This simulates AI scoring of available packs based on metadata from Weeks 1-3

        PackRecommendation[] allRecommendations = {
            // Java service packs (Week 2, Day 7)
            new PackRecommendation(
                    "java-service-activej-gradle",
                    "api-service",
                    "java",
                    "activej",
                    0.95,
                    "ActiveJ provides high-performance async services perfect for microservices"
                            + " architecture",
                    new String[] {"microservices", "high-performance", "async"}),

            // TypeScript React packs (Week 2, Day 8)
            new PackRecommendation(
                    "ts-react-vite",
                    "web-app",
                    "typescript",
                    "react",
                    0.92,
                    "Vite provides fast development experience with React and TypeScript",
                    new String[] {"frontend", "fast-build", "modern"}),
            new PackRecommendation(
                    "ts-react-nextjs",
                    "web-app",
                    "typescript",
                    "nextjs",
                    0.89,
                    "Next.js offers full-stack React with server-side rendering and API routes",
                    new String[] {"fullstack", "ssr", "production-ready"}),

            // Base pack (Week 2, Day 7)
            new PackRecommendation(
                    "base",
                    "library",
                    "generic",
                    "none",
                    0.75,
                    "Base pack provides essential configuration files and project structure",
                    new String[] {"foundation", "cross-language", "essential"})
        };

        // Filter and score based on request criteria
        return Arrays.stream(allRecommendations)
                .filter(rec -> matchesCriteria(rec, request))
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(request.getMaxResults())
                .collect(Collectors.toList());
    }

    private boolean matchesCriteria(PackRecommendation recommendation, AIPromptRequest request) {
        // Score based on matching criteria
        boolean languageMatch =
                request.getPrimaryLanguage() == null
                        || recommendation.getLanguage().equals(request.getPrimaryLanguage())
                        || recommendation.getLanguage().equals("generic");

        boolean typeMatch =
                request.getProjectType() == null
                        || recommendation.getProjectType().equals(request.getProjectType());

        boolean frameworkMatch =
                request.getPreferredFramework() == null
                        || recommendation.getFramework().equals(request.getPreferredFramework());

        return languageMatch && (typeMatch || frameworkMatch);
    }
}
