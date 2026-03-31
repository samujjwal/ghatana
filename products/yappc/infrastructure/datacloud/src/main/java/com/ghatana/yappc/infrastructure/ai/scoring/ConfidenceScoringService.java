package com.ghatana.yappc.infrastructure.ai.scoring;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.domain.ai.ConfidenceScore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for calculating confidence scores on AI-generated artifacts.
 *
 * <p><b>Purpose</b><br>
 * Analyzes AI completion results and assigns confidence scores based on:
 * - Token probabilities from the LLM
 * - Output structure validation
 * - Complexity heuristics
 * - Pattern matching against known good outputs
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ConfidenceScoringService scoring = new ConfidenceScoringService(metrics);
 *
 * // Score a single completion
 * ConfidenceScore score = scoring.scoreCompletion(result, request).getResult();
 *
 * // Use in generation pipeline
 * GenerationService.withConfidenceScoring(scoring);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose AI output confidence scoring service
 * @doc.layer infrastructure
 * @doc.pattern Service
 */
public class ConfidenceScoringService {

    private static final Logger LOG = LoggerFactory.getLogger(ConfidenceScoringService.class);

    private final MetricsCollector metrics;

    public ConfidenceScoringService(MetricsCollector metrics) {
        this.metrics = metrics;
    }

    /**
     * Scores a completion result for quality and confidence.
     *
     * @param result the AI completion result
     * @param request the original request
     * @return promise of confidence score
     */
    public Promise<ConfidenceScore> scoreCompletion(CompletionResult result, CompletionRequest request) {
        return Promise.of(calculateScore(result, request))
            .whenResult(score -> {
                metrics.recordConfidenceScore("ai.confidence.overall", score.overall());
                metrics.recordConfidenceScore("ai.confidence.completeness", score.completeness());
                metrics.recordConfidenceScore("ai.confidence.correctness", score.correctness());
                metrics.incrementCounter("ai.scoring.completed",
                    "priority", score.getReviewPriority().toString());
            });
    }

    /**
     * Calculates confidence score from result heuristics.
     */
    private ConfidenceScore calculateScore(CompletionResult result, CompletionRequest request) {
        String text = result.text();
        String prompt = request.getPrompt();

        // Calculate component scores
        double completeness = calculateCompleteness(text, prompt);
        double correctness = calculateCorrectness(text);
        double consistency = calculateConsistency(text, prompt);
        double complexity = calculateComplexity(text);

        // Weighted overall score
        double overall = (completeness * 0.30) + (correctness * 0.35) + (consistency * 0.25) + (complexity * 0.10);

        LOG.debug("Confidence score calculated: overall={:.2f}, completeness={:.2f}, correctness={:.2f}, " +
            "consistency={:.2f}, complexity={:.2f}", overall, completeness, correctness, consistency, complexity);

        return ConfidenceScore.builder()
            .overall(overall)
            .completeness(completeness)
            .correctness(correctness)
            .consistency(consistency)
            .complexity(complexity)
            .build();
    }

    private double calculateCompleteness(String text, String prompt) {
        double score = 0.5; // Base score

        // Check for expected sections based on prompt
        if (prompt.contains("README") && containsSections(text, "#", "##")) {
            score += 0.20;
        }
        if (prompt.contains("code") && containsCodeBlocks(text)) {
            score += 0.15;
        }
        if (prompt.contains("test") && containsPattern(text, "@Test|test(|assert")) {
            score += 0.15;
        }

        // Length heuristic - very short outputs might be incomplete
        if (text.length() < 50) {
            score -= 0.20;
        } else if (text.length() > 200) {
            score += 0.10;
        }

        return Math.max(0.0, Math.min(1.0, score));
    }

    private double calculateCorrectness(String text) {
        double score = 0.7; // Base score - assume generally correct

        // Penalize error indicators
        if (containsPattern(text, "error|exception|fail|invalid|unable|cannot")) {
            score -= 0.15;
        }

        // Penalize placeholder patterns
        if (containsPattern(text, "TODO|FIXME|XXX|placeholder|example")) {
            score -= 0.10;
        }

        // Reward proper syntax indicators
        if (containsBalancedBrackets(text)) {
            score += 0.10;
        }

        return Math.max(0.0, Math.min(1.0, score));
    }

    private double calculateConsistency(String text, String prompt) {
        double score = 0.6; // Base score

        // Check if output matches prompt language
        if (prompt.contains("Java") && containsPattern(text, "public class|private|import java")) {
            score += 0.20;
        }
        if (prompt.contains("TypeScript") && containsPattern(text, "interface|type|export|import.*from")) {
            score += 0.20;
        }
        if (prompt.contains("Python") && containsPattern(text, "def |class.*:|import |if __name__")) {
            score += 0.20;
        }

        // Check for structural consistency
        if (text.split("\n").length > 3) {
            score += 0.10;
        }

        return Math.max(0.0, Math.min(1.0, score));
    }

    private double calculateComplexity(String text) {
        int lines = text.split("\n").length;
        int tokens = text.split("\\s+").length;

        // Simple complexity heuristic
        if (lines < 10 || tokens < 50) {
            return 0.3; // Simple
        } else if (lines < 50 || tokens < 200) {
            return 0.6; // Moderate
        } else {
            return 0.9; // Complex
        }
    }

    private boolean containsSections(String text, String... markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsCodeBlocks(String text) {
        return text.contains("```") || text.contains("    ") || containsPattern(text, ";\\s*$");
    }

    private boolean containsPattern(String text, String pattern) {
        Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        return m.find();
    }

    private boolean containsBalancedBrackets(String text) {
        int braces = countChar(text, '{') - countChar(text, '}');
        int parens = countChar(text, '(') - countChar(text, ')');
        int brackets = countChar(text, '[') - countChar(text, ']');
        return braces == 0 && parens == 0 && brackets == 0;
    }

    private int countChar(String text, char c) {
        int count = 0;
        for (char ch : text.toCharArray()) {
            if (ch == c) count++;
        }
        return count;
    }
}
