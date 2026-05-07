/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.taxonomy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * P3-20: Capability taxonomy scanner that infers agent capabilities from configuration.
 *
 * <p>Analyzes registered agent configurations to automatically categorize them by capability.
 * This enables discovery and organization of agents by functional area without manual tagging.
 *
 * <h3>Capability Categories</h3>
 * <ul>
 *   <li>DATA_PROCESSING: Agents that transform, filter, or aggregate data</li>
 *   <li>ML_INFERENCE: Agents that perform machine learning predictions</li>
 *   <li>NATURAL_LANGUAGE: Agents that process text, speech, or language</li>
 *   <li>VISION: Agents that process images or video</li>
 *   <li>INTEGRATION: Agents that connect to external systems/APIs</li>
 *   <li>WORKFLOW: Agents that orchestrate or coordinate other agents</li>
 *   <li>SECURITY: Agents that perform security checks or compliance</li>
 *   <li>ANALYTICS: Agents that generate insights or reports</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Infer agent capability taxonomy from configuration
 * @doc.layer product
 * @doc.pattern Scanner, Classifier
 */
public final class CapabilityTaxonomyScanner {

    private static final Logger log = LoggerFactory.getLogger(CapabilityTaxonomyScanner.class);

    // Capability detection patterns
    private static final Map<String, List<Pattern>> CAPABILITY_PATTERNS = Map.of(
        "DATA_PROCESSING", List.of(
            Pattern.compile("(transform|filter|aggregate|map|reduce|join)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(kafka|kinesis|stream|batch)", Pattern.CASE_INSENSITIVE)
        ),
        "ML_INFERENCE", List.of(
            Pattern.compile("(predict|classify|regress|inference|model)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(tensorflow|pytorch|sklearn|xgboost)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(neural|deep.*learning|machine.*learning)", Pattern.CASE_INSENSITIVE)
        ),
        "NATURAL_LANGUAGE", List.of(
            Pattern.compile("(nlp|text|sentiment|language|translate|summarize)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(openai|anthropic|claude|gpt|llm)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(embedding|tokenize|bert|transformer)", Pattern.CASE_INSENSITIVE)
        ),
        "VISION", List.of(
            Pattern.compile("(image|video|vision|object.*detect|segment)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(opencv|yolo|resnet|efficientnet)", Pattern.CASE_INSENSITIVE)
        ),
        "INTEGRATION", List.of(
            Pattern.compile("(api|webhook|http|rest|graphql)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(database|sql|postgres|mysql|mongo)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(connector|adapter|bridge)", Pattern.CASE_INSENSITIVE)
        ),
        "WORKFLOW", List.of(
            Pattern.compile("(orchestrat|workflow|pipeline|dag|step)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(coordinate|schedule|trigger|event)", Pattern.CASE_INSENSITIVE)
        ),
        "SECURITY", List.of(
            Pattern.compile("(security|auth|encrypt|decrypt|validate)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(compliance|audit|pii|gdpr)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(scan|check|verify|sanitize)", Pattern.CASE_INSENSITIVE)
        ),
        "ANALYTICS", List.of(
            Pattern.compile("(analytic|metric|report|dashboard|insight)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(aggregate|summarize|statistic)", Pattern.CASE_INSENSITIVE)
        )
    );

    /**
     * Result of capability taxonomy analysis.
     */
    public static class TaxonomyResult {
        private final String agentId;
        private final String agentName;
        private final String agentType;
        private final Set<String> inferredCapabilities;
        private final double confidence;
        private final Map<String, Object> evidence;

        public TaxonomyResult(String agentId, String agentName, String agentType,
                             Set<String> inferredCapabilities, double confidence,
                             Map<String, Object> evidence) {
            this.agentId = agentId;
            this.agentName = agentName;
            this.agentType = agentType;
            this.inferredCapabilities = Set.copyOf(inferredCapabilities);
            this.confidence = confidence;
            this.evidence = Map.copyOf(evidence);
        }

        public String agentId() {
            return agentId;
        }

        public String agentName() {
            return agentName;
        }

        public String agentType() {
            return agentType;
        }

        public Set<String> inferredCapabilities() {
            return inferredCapabilities;
        }

        public double confidence() {
            return confidence;
        }

        public Map<String, Object> evidence() {
            return evidence;
        }
    }

    /**
     * Scans a single agent configuration to infer capabilities.
     *
     * @param agentId   agent identifier
     * @param agentName agent name
     * @param agentType agent type
     * @param config    agent configuration map
     * @return taxonomy analysis result
     */
    public TaxonomyResult scanAgent(String agentId, String agentName, String agentType,
                                     Map<String, Object> config) {
        Set<String> capabilities = new HashSet<>();
        Map<String, Object> evidence = new HashMap<>();
        int totalMatches = 0;

        // Scan configuration values for capability patterns
        String configText = configToString(config);

        for (Map.Entry<String, List<Pattern>> entry : CAPABILITY_PATTERNS.entrySet()) {
            String capability = entry.getKey();
            List<Pattern> patterns = entry.getValue();
            int matches = 0;
            List<String> matchedPatterns = new ArrayList<>();

            for (Pattern pattern : patterns) {
                if (pattern.matcher(configText).find()) {
                    matches++;
                    matchedPatterns.add(pattern.pattern());
                }
            }

            if (matches > 0) {
                capabilities.add(capability);
                evidence.put(capability, Map.of(
                    "matches", matches,
                    "patterns", matchedPatterns
                ));
                totalMatches += matches;
            }
        }

        // Infer from agent type if no capabilities found
        if (capabilities.isEmpty()) {
            capabilities.addAll(inferFromAgentType(agentType));
        }

        // Calculate confidence based on match count
        double confidence = calculateConfidence(totalMatches, configText.length());

        log.debug("[TaxonomyScanner] Scanned agent={} name={} type={} capabilities={}",
            agentId, agentName, agentType, capabilities);

        return new TaxonomyResult(agentId, agentName, agentType, capabilities, confidence, evidence);
    }

    /**
     * Scans multiple agents and returns taxonomy results.
     *
     * @param agents list of agent data maps
     * @return list of taxonomy results
     */
    public List<TaxonomyResult> scanAgents(List<Map<String, Object>> agents) {
        List<TaxonomyResult> results = new ArrayList<>();

        for (Map<String, Object> agent : agents) {
            String agentId = (String) agent.get("id");
            String agentName = (String) agent.get("name");
            String agentType = (String) agent.get("type");
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) agent.getOrDefault("config", Map.of());

            if (agentId != null) {
                results.add(scanAgent(agentId, agentName, agentType, config));
            }
        }

        log.info("[TaxonomyScanner] Scanned {} agents, inferred capabilities for {}",
            agents.size(), results.size());

        return results;
    }

    /**
     * Returns a summary of capabilities across all scanned agents.
     *
     * @param results taxonomy results from scanAgents
     * @return capability summary map
     */
    public Map<String, Object> summarizeCapabilities(List<TaxonomyResult> results) {
        Map<String, Integer> capabilityCounts = new HashMap<>();
        Map<String, List<String>> agentsByCapability = new HashMap<>();

        for (TaxonomyResult result : results) {
            for (String capability : result.inferredCapabilities()) {
                capabilityCounts.merge(capability, 1, Integer::sum);
                agentsByCapability
                    .computeIfAbsent(capability, k -> new ArrayList<>())
                    .add(result.agentId());
            }
        }

        return Map.of(
            "totalAgents", results.size(),
            "capabilityCounts", capabilityCounts,
            "agentsByCapability", agentsByCapability
        );
    }

    private Set<String> inferFromAgentType(String agentType) {
        Set<String> capabilities = new HashSet<>();

        if (agentType == null) {
            return capabilities;
        }

        String type = agentType.toLowerCase();

        if (type.contains("reactive") || type.contains("stream")) {
            capabilities.add("DATA_PROCESSING");
        }
        if (type.contains("deliberative") || type.contains("planning")) {
            capabilities.add("WORKFLOW");
        }
        if (type.contains("learning") || type.contains("ml")) {
            capabilities.add("ML_INFERENCE");
        }

        return capabilities;
    }

    private String configToString(Map<String, Object> config) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append(" ");
        }
        return sb.toString();
    }

    private double calculateConfidence(int matches, int configLength) {
        if (matches == 0) {
            return 0.3; // Low confidence for type-only inference
        }
        if (configLength == 0) {
            return 0.5; // Medium confidence for pattern matches on minimal config
        }
        // Higher confidence for more pattern matches relative to config size
        return Math.min(1.0, 0.5 + (matches * 0.1));
    }
}
