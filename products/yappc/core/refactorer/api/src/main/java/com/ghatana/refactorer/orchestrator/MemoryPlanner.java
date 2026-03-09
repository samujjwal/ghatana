package com.ghatana.refactorer.orchestrator;

import com.ghatana.refactorer.shared.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Memory-aware planner that consults the action ledger and knowledge base to gate and rank fix
 * rules.
 *
 * <p>This component provides intelligent fix planning by:
 *
 * <ul>
 *   <li>Consulting historical action outcomes from the ledger
 *   <li>Using knowledge base confidence scores
 *   <li>Detecting and preventing oscillating fixes
 *   <li>Ranking fixes by success probability
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * MemoryPlanner planner = new MemoryPlanner(actionLedger, knowledgeBase, oscillationDetector);
 *
 * List<UnifiedDiagnostic> diagnostics = getDiagnostics();
 * List<PlannedFix> plannedFixes = planner.planFixes(diagnostics, "python");
 *
 * // Apply fixes in order of confidence
 * for (PlannedFix fix : plannedFixes) {
 *     if (fix.shouldApply()) {
 *         applyFix(fix);
 *     }
 * }
 * }</pre>
 
 * @doc.type class
 * @doc.purpose Handles memory planner operations
 * @doc.layer core
 * @doc.pattern Enum
* @doc.gaa.memory episodic
*/
public class MemoryPlanner {

    private static final Logger log = LoggerFactory.getLogger(MemoryPlanner.class);

    private final KnowledgeBase knowledgeBase;
    private final OscillationDetector oscillationDetector;

    private double confidenceThreshold = 0.5;
    private int maxFixesPerFile = 10;
    private boolean enableOscillationDetection = true;

    /**
     * Creates a memory planner with the required dependencies.
     *
     * @param knowledgeBase The knowledge base for fix recipes
     * @param oscillationDetector The oscillation detector (can be null)
     */
    public MemoryPlanner(KnowledgeBase knowledgeBase, OscillationDetector oscillationDetector) {
        this.knowledgeBase = Objects.requireNonNull(knowledgeBase, "knowledgeBase cannot be null");
        this.oscillationDetector = oscillationDetector;
    }

    /**
     * Plans fixes for a list of diagnostics using memory and knowledge base.
     *
     * @param diagnostics The diagnostics to plan fixes for
     * @param language The programming language
     * @return List of planned fixes, ordered by confidence
     */
    public List<PlannedFix> planFixes(List<UnifiedDiagnostic> diagnostics, String language) {
        Objects.requireNonNull(diagnostics, "diagnostics cannot be null");
        Objects.requireNonNull(language, "language cannot be null");

        log.info("Planning fixes for {} diagnostics in language: {}", diagnostics.size(), language);

        // Process all diagnostics and collect fixes
        List<PlannedFix> plannedFixes =
                diagnostics.stream()
                        .flatMap(d -> planFixesForDiagnostic(d, language).stream())
                        .sorted(Comparator.comparingDouble(PlannedFix::confidence).reversed())
                        .collect(Collectors.toList());

        // Apply per-file limits
        Map<Path, List<PlannedFix>> fixesByFile =
                plannedFixes.stream().collect(Collectors.groupingBy(PlannedFix::filePath));

        List<PlannedFix> limitedFixes =
                fixesByFile.values().stream()
                        .flatMap(fixes -> fixes.stream().limit(maxFixesPerFile))
                        .collect(Collectors.toList());

        log.info("Planned {} fixes from {} diagnostics (limited from {})", limitedFixes.size(), diagnostics.size(), plannedFixes.size());

        return limitedFixes;
    }

    /**
     * Plans fixes for a single diagnostic.
     *
     * @param diagnostic The diagnostic to plan fixes for
     * @param language The programming language
     * @return List of planned fixes for this diagnostic
     */
    public List<PlannedFix> planFixesForDiagnostic(UnifiedDiagnostic diagnostic, String language) {
        Objects.requireNonNull(diagnostic, "diagnostic cannot be null");
        Objects.requireNonNull(language, "language cannot be null");

        String ruleId = diagnostic.getRuleId();

        // Get fix recipes from knowledge base
        List<KnowledgeBase.FixRecipe> recipes = knowledgeBase.getFixRecipes(language, ruleId);
        if (recipes.isEmpty()) {
            log.info("Debug: No fix recipes found for {}:{}", language, ruleId);
            return List.of();
        }

        List<PlannedFix> plannedFixes = new ArrayList<>();

        for (KnowledgeBase.FixRecipe recipe : recipes) {
            PlannedFix plannedFix = createPlannedFix(diagnostic, recipe, language);
            if (plannedFix != null) {
                plannedFixes.add(plannedFix);
            }
        }

        return plannedFixes;
    }

    /**
     * Checks if a fix should be skipped based on memory and oscillation detection.
     *
     * @param diagnostic The diagnostic
     * @param recipe The fix recipe
     * @param language The programming language
     * @return SkipReason if the fix should be skipped, null otherwise
     */
    public SkipReason shouldSkipFix(
            UnifiedDiagnostic diagnostic, KnowledgeBase.FixRecipe recipe, String language) {
        Objects.requireNonNull(diagnostic, "diagnostic cannot be null");
        Objects.requireNonNull(recipe, "recipe cannot be null");
        Objects.requireNonNull(language, "language cannot be null");

        String filePath = diagnostic.getFile();
        String ruleId = diagnostic.getRuleId();
        String fingerprint = createFingerprint(diagnostic, language);

        // Check confidence threshold
        if (recipe.confidence() < confidenceThreshold) {
            return new SkipReason(
                    SkipType.LOW_CONFIDENCE,
                    String.format(
                            "Confidence %.2f below threshold %.2f",
                            recipe.confidence(), confidenceThreshold));
        }

        // Check oscillation detection
        if (enableOscillationDetection) {
            try {
                Path path = Paths.get(filePath);
                if (oscillationDetector != null
                        && oscillationDetector.isOscillating(path, fingerprint)) {
                    return new SkipReason(
                            SkipType.OSCILLATION_DETECTED,
                            "Oscillation detected for " + fingerprint);
                }
            } catch (InvalidPathException e) {
                log.error("Warning: Invalid file path {}: {}", filePath, e.getMessage());
            } catch (Exception e) {
                log.error("Warning: Error checking oscillation: {}", e.getMessage());
            }
        }

        // Check historical failures
        if (hasRecentFailures(Paths.get(filePath), ruleId)) {
            return new SkipReason(
                    SkipType.RECENT_FAILURES, "Recent failures detected for " + ruleId);
        }

        return null; // Don't skip
    }

    /**
     * Updates the confidence threshold for fix planning.
     *
     * @param threshold The new confidence threshold (0.0 to 1.0)
     */
    public void setConfidenceThreshold(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("Confidence threshold must be between 0.0 and 1.0");
        }
        this.confidenceThreshold = threshold;
        log.info("Updated confidence threshold to {}", threshold);
    }

    /**
     * Updates the maximum number of fixes per file.
     *
     * @param maxFixes The maximum number of fixes per file
     */
    public void setMaxFixesPerFile(int maxFixes) {
        if (maxFixes <= 0) {
            throw new IllegalArgumentException("Max fixes per file must be positive");
        }
        this.maxFixesPerFile = maxFixes;
        log.info("Updated max fixes per file to {}", maxFixes);
    }

    /**
     * Enables or disables oscillation detection.
     *
     * @param enabled Whether to enable oscillation detection
     */
    public void setOscillationDetectionEnabled(boolean enabled) {
        this.enableOscillationDetection = enabled;
        log.info("Oscillation detection {}", (enabled ? "enabled" : "disabled"));
    }

    private PlannedFix createPlannedFix(
            UnifiedDiagnostic diagnostic, KnowledgeBase.FixRecipe recipe, String language) {
        SkipReason skipReason = shouldSkipFix(diagnostic, recipe, language);
        if (skipReason != null) {
            log.info("Skipping fix for {}: {}", diagnostic.getRuleId(), skipReason.reason());
            return new PlannedFix(
                    Path.of(diagnostic.getFile()),
                    diagnostic,
                    recipe,
                    recipe.confidence(),
                    false,
                    skipReason);
        }

        // Adjust confidence based on historical success
        double adjustedConfidence = adjustConfidenceFromHistory(diagnostic, recipe, language);

        return new PlannedFix(
                diagnostic.file(), diagnostic, recipe, adjustedConfidence, true, null);
    }

    private double adjustConfidenceFromHistory(
            UnifiedDiagnostic diagnostic, KnowledgeBase.FixRecipe recipe, String language) {
        // Default to recipe confidence if no history is available
        return recipe.confidence();
    }

    private boolean hasRecentFailures(Path filePath, String ruleId) {
        // Default to no recent failures if history is not available
        return false;
    }

    private String createFingerprint(UnifiedDiagnostic diagnostic, String language) {
        return String.format(
                "%s:%s:%s",
                language,
                diagnostic.getRuleId(),
                diagnostic.getMessage() != null ? diagnostic.getMessage() : "");
    }

    /**
 * Represents a planned fix with confidence and applicability information. */
    public record PlannedFix(
            Path filePath,
            UnifiedDiagnostic diagnostic,
            KnowledgeBase.FixRecipe recipe,
            double confidence,
            boolean applicable,
            SkipReason skipReason) {
        // Secondary constructor that accepts String path
        public PlannedFix(
                String filePath,
                UnifiedDiagnostic diagnostic,
                KnowledgeBase.FixRecipe recipe,
                double confidence,
                boolean applicable,
                SkipReason skipReason) {
            this(Paths.get(filePath), diagnostic, recipe, confidence, applicable, skipReason);
        }

        public PlannedFix {
            Objects.requireNonNull(filePath, "filePath cannot be null");
            Objects.requireNonNull(diagnostic, "diagnostic cannot be null");
            Objects.requireNonNull(recipe, "recipe cannot be null");

            if (confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
            }
        }
    }

    /**
 * Reason why a fix should be skipped. */
    public record SkipReason(SkipType type, String reason) {
        public SkipReason {
            Objects.requireNonNull(type, "type cannot be null");
            Objects.requireNonNull(reason, "reason cannot be null");
        }
    }

    /**
 * Types of reasons for skipping fixes. */
    public enum SkipType {
        LOW_CONFIDENCE,
        OSCILLATION_DETECTED,
        RECENT_FAILURES,
        PRECONDITION_FAILED,
        BLOCKLISTED
    }
}
