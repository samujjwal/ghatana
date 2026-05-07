package com.ghatana.tutorputor.contentgeneration.generators;

import com.ghatana.tutorputor.contentgeneration.domain.ContentGenerationRequest;
import com.ghatana.tutorputor.contentgeneration.domain.Domain;
import com.ghatana.tutorputor.contentgeneration.domain.LearningClaim;
import com.ghatana.tutorputor.contentgeneration.domain.SimulationManifest;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Produces domain-aware simulation manifests for learning claims.
 *
 * <p>Parameter bounds and expected outputs are derived from the domain and grade level
 * so that simulations reflect the actual numerical and conceptual range expected of
 * learners at that level. Each simulation also carries a deterministic seed derived from
 * the claim ID to ensure reproducible runs, while avoiding the hardcoded constant 42
 * that was previously present.
 *
 * @doc.type class
 * @doc.purpose Generate domain-specific simulation manifests for claims
 * @doc.layer product
 * @doc.pattern Generator
 */
public class SimulationGenerator {

    public Promise<List<SimulationManifest>> generateSimulations(
            List<LearningClaim> claims, ContentGenerationRequest request) {

        return Promise.of(claims.stream()
                .map(claim -> buildManifest(claim, request))
                .toList());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static SimulationManifest buildManifest(LearningClaim claim, ContentGenerationRequest request) {
        Domain domain = request.getDomain();
        int gradeLevel = request.getGradeLevel();
        Map<String, Object> bounds = parameterBoundsFor(domain, gradeLevel);
        Map<String, Object> expectedOutputs = expectedOutputsFor(domain, gradeLevel);
        // Reproducible seed derived from the claim ID — avoids hardcoded 42.
        int seed = Math.abs(claim.getId().hashCode()) % 100_000;

        return SimulationManifest.builder()
                .id(UUID.randomUUID().toString())
                .title("Simulation: " + claim.getText())
                .description("Interactive exploration of \"" + claim.getText()
                        + "\" for grade-" + gradeLevel + " " + domain.name() + " learners")
                .domain(domain.name())
                .configuration(Map.of(
                        "claimId", claim.getId(),
                        "seed", seed,
                        "parameterBounds", bounds,
                        "expectedOutputs", expectedOutputs
                ))
                .build();
    }

    /** Returns domain-and-grade-specific parameter bounds. */
    private static Map<String, Object> parameterBoundsFor(Domain domain, int gradeLevel) {
        return switch (domain) {
            case MATH -> Map.of(
                    "x", List.of(-10 * gradeLevel, 10 * gradeLevel),
                    "y", List.of(0, 100),
                    "iterations", List.of(1, 50));
            case SCIENCE, ENGINEERING -> Map.of(
                    "force_N", List.of(0.0, 1000.0 * gradeLevel),
                    "mass_kg", List.of(0.1, 200.0),
                    "acceleration_ms2", List.of(0.0, 50.0));
            case COMPUTER_SCIENCE, TECH -> Map.of(
                    "input_size", List.of(1, 1024),
                    "operations", List.of(1, 10_000),
                    "memory_bytes", List.of(256, 65_536));
            case MEDICINE, HEALTH -> Map.of(
                    "dosage_mg", List.of(0.0, 500.0),
                    "duration_days", List.of(1, 30),
                    "patient_weight_kg", List.of(40.0, 120.0));
            case BUSINESS, MANAGEMENT, ECONOMICS -> Map.of(
                    "capital_usd", List.of(1_000, 1_000_000),
                    "growth_rate_pct", List.of(-20.0, 50.0),
                    "time_periods", List.of(1, 10 * gradeLevel));
            default -> Map.of(
                    "input", List.of(0, 100 * gradeLevel),
                    "output", List.of(0, 100));
        };
    }

    /** Returns domain-specific expected simulation outputs for automated evaluation. */
    private static Map<String, Object> expectedOutputsFor(Domain domain, int gradeLevel) {
        return switch (domain) {
            case MATH -> Map.of(
                    "convergence", true,
                    "baseline_result", 0,
                    "tolerance", 0.001);
            case SCIENCE, ENGINEERING -> Map.of(
                    "equilibrium_reached", true,
                    "energy_conserved", true,
                    "tolerance_pct", 5.0);
            case COMPUTER_SCIENCE, TECH -> Map.of(
                    "algorithm_correct", true,
                    "complexity_class", "O(n log n)",
                    "within_memory_limit", true);
            case MEDICINE, HEALTH -> Map.of(
                    "therapeutic_range_met", true,
                    "adverse_events", 0,
                    "efficacy_pct", 80.0);
            case BUSINESS, MANAGEMENT, ECONOMICS -> Map.of(
                    "profitable", true,
                    "roi_pct", 10.0,
                    "break_even_period", gradeLevel);
            default -> Map.of("baseline", 0);
        };
    }
}
