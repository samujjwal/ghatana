package com.ghatana.yappc.ai.quality;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts a {@link ConfidenceScore} from an LLM response string.
 *
 * <p>The scorer expects the LLM to embed a JSON fragment anywhere in its output:
 * <pre>{@code
 *   {"confidence": 0.85, "content": "Here is the generated code..."}
 * }</pre>
 *
 * <p>If the JSON fragment is absent or malformed, {@link ConfidenceScore#absent()} is returned so
 * callers always receive a non-null score — never an exception.
 *
 * <p>Out-of-range values (outside [0.0, 1.0]) are clamped to the nearest boundary.
 *
 * @doc.type class
 * @doc.purpose Parses confidence field from LLM JSON output; returns absent score on failure
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class ConfidenceScorer {

    private static final Logger logger = LoggerFactory.getLogger(ConfidenceScorer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CONFIDENCE_KEY = "confidence";

    private ConfidenceScorer() {}

    /**
     * Parses a confidence score from the LLM response content.
     *
     * <p>The scorer tries to find a JSON object containing a {@code "confidence"} key.
     * It scans for the first {@code {…}} block in the content and attempts to parse it.
     *
     * @param content LLM response (may include prose before/after the JSON fragment)
     * @return parsed score, or {@link ConfidenceScore#absent()} if not found/invalid
     */
    public static ConfidenceScore parse(String content) {
        if (content == null || content.isBlank()) {
            return ConfidenceScore.absent();
        }

        String jsonBlock = extractFirstJsonBlock(content);
        if (jsonBlock == null) {
            logger.debug("No JSON block found in content; returning absent confidence score");
            return ConfidenceScore.absent();
        }

        try {
            JsonNode root = MAPPER.readTree(jsonBlock);
            JsonNode confidenceNode = root.get(CONFIDENCE_KEY);
            if (confidenceNode == null || !confidenceNode.isNumber()) {
                logger.debug("JSON block has no numeric 'confidence' field; returning absent");
                return ConfidenceScore.absent();
            }

            double rawValue = confidenceNode.asDouble();
            double clamped = Math.max(0.0, Math.min(1.0, rawValue));
            if (rawValue != clamped) {
                logger.warn("Confidence value {} out of [0.0, 1.0]; clamped to {}", rawValue, clamped);
            }

            return ConfidenceScore.of(clamped, String.valueOf(rawValue));

        } catch (Exception ex) {
            logger.debug("Failed to parse confidence JSON: {}", ex.getMessage());
            return ConfidenceScore.absent();
        }
    }

    /**
     * Extracts the text of the first balanced {@code {…}} block found in {@code content}.
     *
     * @return the JSON substring, or {@code null} if none found
     */
    static String extractFirstJsonBlock(String content) {
        int start = content.indexOf('{');
        if (start == -1) return null;

        int depth = 0;
        for (int i = start; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return content.substring(start, i + 1);
                }
            }
        }
        return null; // unbalanced
    }
}
