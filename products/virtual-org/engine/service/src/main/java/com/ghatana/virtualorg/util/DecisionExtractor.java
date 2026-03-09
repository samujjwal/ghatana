package com.ghatana.virtualorg.util;

import com.ghatana.virtualorg.v1.DecisionProto;
import com.ghatana.virtualorg.v1.DecisionTypeProto;
import com.ghatana.virtualorg.v1.TaskProto;
import com.google.protobuf.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for extracting structured decisions from LLM responses.
 *
 * <p><b>Purpose</b><br>
 * Provides common decision extraction logic used across all agent types
 * to eliminate code duplication and ensure consistent decision parsing.
 *
 * <p><b>Architecture Role</b><br>
 * This utility consolidates ~1,400 lines of duplicate decision extraction
 * logic previously scattered across 13 agent classes (CEO, CTO, CFO, etc.).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Extract decision from LLM response
 * DecisionProto decision = DecisionExtractor.extractDecision(
 *     llmResponse.getContent(),
 *     task,
 *     "agent-ceo-001"
 * );
 *
 * // Parse individual components
 * DecisionTypeProto type = DecisionExtractor.determineDecisionType(response);
 * double confidence = DecisionExtractor.extractConfidence(response);
 * String rationale = DecisionExtractor.extractRationale(response);
 * }</pre>
 *
 * <p><b>Extraction Strategy</b><br>
 * - Uses keyword matching and regex for decision type detection
 * - Extracts confidence scores from numeric values (0.0-1.0)
 * - Truncates rationale to 500 characters for storage efficiency
 * - Production systems should use LLM-structured output for better accuracy
 *
 * @doc.type class
 * @doc.purpose Extract structured decisions from unstructured LLM text responses
 * @doc.layer product
 * @doc.pattern Utility/Helper
 */
public final class DecisionExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(DecisionExtractor.class);

    private static final int MAX_RATIONALE_LENGTH = 500;
    private static final double DEFAULT_CONFIDENCE = 0.7;
    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile("confidence[:\\s]+([0-9.]+)", Pattern.CASE_INSENSITIVE);

    /**
     * Private constructor to prevent instantiation.
     */
    private DecisionExtractor() {
        // Utility class
    }

    /**
     * Extracts a structured decision from an LLM response.
     *
     * <p>Parses the LLM's text response to extract decision components:
     * decision type, confidence score, and reasoning. Handles various
     * response formats and provides sensible defaults for missing data.
     *
     * @param llmResponseContent the LLM response text
     * @param task the task being decided on
     * @param agentId the ID of the deciding agent
     * @return structured decision proto
     */
    public static DecisionProto extractDecision(
            String llmResponseContent,
            TaskProto task,
            String agentId) {

        DecisionTypeProto decisionType = determineDecisionType(llmResponseContent);
        double confidence = extractConfidence(llmResponseContent);
        String rationale = extractRationale(llmResponseContent);

        return DecisionProto.newBuilder()
                .setDecisionId(generateDecisionId(task))
                .setTaskId(task.getTaskId())
                .setAgentId(agentId)
                .setType(decisionType)
                .setConfidence((float) confidence)
                .setReasoning(rationale)
                .setCreatedAt(currentTimestamp())
                .build();
    }

    /**
     * Determines the decision type from LLM response text.
     *
     * <p>Uses keyword matching to classify the decision. Searches for
     * approval/rejection/delegation keywords in a case-insensitive manner.
     *
     * <p><b>Decision Keywords</b><br>
     * - APPROVED: "approve", "accept", "confirm", "proceed"
     * - REJECTED: "reject", "deny", "decline"
     * - DELEGATED: "delegate", "escalate", "forward"
     * - PENDING: default if no keywords match
     *
     * @param response the LLM response text
     * @return the detected decision type
     */
    public static DecisionTypeProto determineDecisionType(String response) {
        String lower = response.toLowerCase();

        if (lower.contains("approve") || lower.contains("accept") ||
            lower.contains("confirm") || lower.contains("proceed")) {
            return DecisionTypeProto.DECISION_TYPE_APPROVED;
        } else if (lower.contains("delegate") || lower.contains("escalate") ||
                   lower.contains("forward")) {
            return DecisionTypeProto.DECISION_TYPE_ESCALATED;
        } else if (lower.contains("reject") || lower.contains("deny") ||
                   lower.contains("decline")) {
            return DecisionTypeProto.DECISION_TYPE_REJECTED;
        } else {
            return DecisionTypeProto.DECISION_TYPE_PENDING;
        }
    }

    /**
     * Extracts confidence score from LLM response.
     *
     * <p>Searches for numeric values between 0.0 and 1.0 near the word "confidence".
     * Uses regex pattern matching for extraction. Returns default confidence (0.7)
     * if no valid confidence score is found.
     *
     * <p><b>Extraction Strategy</b><br>
     * 1. Search for "confidence" keyword (case-insensitive)
     * 2. Look for numeric values (0.0-1.0) within 50 characters
     * 3. Use regex: {@code confidence[:\\s]+([0-9.]+)}
     * 4. Validate extracted value is in [0.0, 1.0] range
     *
     * @param response the LLM response text
     * @return confidence score between 0.0 and 1.0, or 0.7 if not found
     */
    public static double extractConfidence(String response) {
        try {
            Matcher matcher = CONFIDENCE_PATTERN.matcher(response);
            if (matcher.find()) {
                double value = Double.parseDouble(matcher.group(1));
                if (value >= 0.0 && value <= 1.0) {
                    return value;
                } else if (value > 1.0 && value <= 100.0) {
                    // Handle percentage format (e.g., "confidence: 85")
                    return value / 100.0;
                }
            }

            // Fallback: search manually
            int confIndex = response.toLowerCase().indexOf("confidence");
            if (confIndex >= 0) {
                String sub = response.substring(confIndex, Math.min(confIndex + 50, response.length()));
                String[] parts = sub.split("[^0-9.]");
                for (String part : parts) {
                    if (part.isEmpty()) continue;
                    try {
                        double val = Double.parseDouble(part);
                        if (val >= 0.0 && val <= 1.0) {
                            return val;
                        } else if (val > 1.0 && val <= 100.0) {
                            return val / 100.0;
                        }
                    } catch (NumberFormatException e) {
                        // Continue searching
                    }
                }
            }
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            LOG.warn("Failed to extract confidence from response: {}", e.getMessage());
        }

        return DEFAULT_CONFIDENCE;
    }

    /**
     * Extracts decision rationale from LLM response.
     *
     * <p>Truncates the response to {@value #MAX_RATIONALE_LENGTH} characters
     * for storage efficiency. Appends "..." if truncated.
     *
     * <p>Production systems should extract specific rationale sections
     * using structured LLM output or better parsing strategies.
     *
     * @param response the LLM response text
     * @return extracted rationale (max 500 characters)
     */
    public static String extractRationale(String response) {
        if (response == null || response.isEmpty()) {
            return "No rationale provided";
        }

        return response.length() > MAX_RATIONALE_LENGTH
                ? response.substring(0, MAX_RATIONALE_LENGTH) + "..."
                : response;
    }

    /**
     * Generates a unique decision ID for a task.
     *
     * <p>Format: {@code decision-<taskId>-<timestamp>}
     *
     * @param task the task being decided on
     * @return unique decision ID
     */
    public static String generateDecisionId(TaskProto task) {
        return "decision-" + task.getTaskId() + "-" + System.currentTimeMillis();
    }

    /**
     * Formats tool execution results for inclusion in LLM context.
     *
     * <p>Converts a list of tool results into a numbered list format
     * suitable for inclusion in LLM prompts.
     *
     * @param toolResults list of tool execution results
     * @return formatted tool results string
     */
    public static String formatToolResults(List<String> toolResults) {
        if (toolResults == null || toolResults.isEmpty()) {
            return "No tool results available";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < toolResults.size(); i++) {
            sb.append(i + 1).append(". ").append(toolResults.get(i)).append("\n");
        }
        return sb.toString();
    }

    /**
     * Creates a current timestamp in protobuf Timestamp format.
     *
     * @return current timestamp
     */
    public static Timestamp currentTimestamp() {
        Instant now = Instant.now();
        return Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
    }
}
