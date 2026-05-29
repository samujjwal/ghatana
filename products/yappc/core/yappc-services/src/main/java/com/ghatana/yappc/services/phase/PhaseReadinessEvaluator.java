package com.ghatana.yappc.services.phase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ghatana.yappc.api.PhasePacket;
import com.ghatana.yappc.services.lifecycle.TransitionConfigLoader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Calculates phase readiness scores based on configurable weights and thresholds
 * @doc.layer product
 * @doc.pattern Service
 */
final class PhaseReadinessEvaluator {

    private static final Logger log = LoggerFactory.getLogger(PhaseReadinessEvaluator.class);
    private static final String CONFIG_DIR_PROP = "yappc.config.dir";
    private static final String RELATIVE_PATH = "lifecycle/readiness-config.yaml";

    private final TransitionConfigLoader transitionConfigLoader;
    private final ReadinessConfig config;

    PhaseReadinessEvaluator(@NotNull TransitionConfigLoader transitionConfigLoader) {
        this.transitionConfigLoader = Objects.requireNonNull(transitionConfigLoader, "transitionConfigLoader");
        this.config = loadConfig();
        log.info("PhaseReadinessEvaluator: loaded readiness config with threshold {}", config.threshold);
    }

    PhasePacket.PhaseReadiness calculate(
            String phase,
            String projectId,
            List<PhasePacket.PhaseBlocker> blockers,
            List<PhasePacket.RequiredArtifact> requiredArtifacts,
            List<PhasePacket.CompletedArtifact> completedArtifacts,
            List<PhasePacket.PhaseEvidence> evidence,
            List<PhasePacket.GovernanceRecord> governance,
            PhasePacket.HealthSignals healthSignals,
            Map<String, Object> projectState
    ) {
        try {
            List<String> missingPrerequisites = new ArrayList<>();

            for (PhasePacket.PhaseBlocker blocker : blockers) {
                if ("CRITICAL".equals(blocker.severity())) {
                    missingPrerequisites.add(blocker.title());
                }
            }

            boolean completedArtifactsUnavailable = completedArtifacts.stream().anyMatch(this::isCompletedArtifactsUnavailable);
            long completedRequired = requiredArtifacts.stream()
                    .filter(required -> isArtifactComplete(required, completedArtifacts))
                    .count();
            if (!requiredArtifacts.isEmpty()) {
                requiredArtifacts.stream()
                        .filter(required -> !isArtifactComplete(required, completedArtifacts))
                        .map(PhasePacket.RequiredArtifact::title)
                        .forEach(missingPrerequisites::add);
            }
            if (completedArtifactsUnavailable) {
                missingPrerequisites.add("Completed artifacts unavailable");
            }

            boolean policyAllowed = governance.stream().noneMatch(this::isPolicyDenied);
            if (!policyAllowed) {
                missingPrerequisites.add("Policy approval");
            }

            boolean evidenceAvailable = evidence.stream().noneMatch(this::isEvidenceDegraded);
            if (!evidenceAvailable) {
                missingPrerequisites.add("Phase evidence unavailable");
            }

            boolean healthReady = healthSignals.preview().isHealthy()
                    && healthSignals.generation().isHealthy()
                    && healthSignals.runtime().isHealthy();
            if (!healthReady) {
                missingPrerequisites.add("Healthy preview, generation, and runtime signals");
            }

            double artifactScore = requiredArtifacts.isEmpty()
                    ? 1.0
                    : completedArtifactsUnavailable ? 0.0 : (double) completedRequired / (double) requiredArtifacts.size();
            double blockerScore = blockers.isEmpty()
                    ? 1.0
                    : blockers.stream().anyMatch(blocker -> "CRITICAL".equals(blocker.severity())) ? 0.0 : 0.5;
            double evidenceScore = evidence.isEmpty() || !evidenceAvailable ? 0.0 : 1.0;
            double governanceScore = policyAllowed ? 1.0 : 0.0;
            double healthScore = healthReady ? 1.0 : 0.0;
            double completenessScore = roundScore(
                    artifactScore * config.weights.artifact
                            + blockerScore * config.weights.blocker
                            + evidenceScore * config.weights.evidence
                            + governanceScore * config.weights.governance
                            + healthScore * config.weights.health);
            
            // Improved degraded semantics: check for any degraded dependency
            boolean isDegraded = completedArtifactsUnavailable
                    || !evidenceAvailable
                    || !healthReady
                    || !policyAllowed
                    || blockers.stream().anyMatch(b -> "CRITICAL".equals(b.severity()));
            
            boolean canAdvance = missingPrerequisites.isEmpty()
                    && blockers.isEmpty()
                    && evidenceAvailable
                    && completenessScore >= config.threshold
                    && "active".equalsIgnoreCase(String.valueOf(projectState.getOrDefault("status", "active")));
            List<String> distinctMissingPrerequisites = missingPrerequisites.stream().distinct().toList();
            int estimatedReadyInHours = estimateReadyInHours(canAdvance, distinctMissingPrerequisites, completenessScore);

            return new PhasePacket.PhaseReadiness(
                    canAdvance,
                    transitionConfigLoader.getNextPhase(phase),
                    distinctMissingPrerequisites,
                    completenessScore,
                    isDegraded,
                    humanizeReadyInHours(estimatedReadyInHours),
                    estimatedReadyInHours,
                    predictionConfidence(completenessScore, distinctMissingPrerequisites.size(), evidenceAvailable, healthReady));
        } catch (Exception exception) {
            log.error("Error calculating phase readiness: phase={}, projectId={}", phase, projectId, exception);
            return new PhasePacket.PhaseReadiness(
                    false,
                    transitionConfigLoader.getNextPhase(phase),
                    List.of("Error calculating readiness"),
                    0.0,
                    true,
                    "Blocked",
                    24,
                    0.35);
        }
    }

    private int estimateReadyInHours(boolean canAdvance, List<String> missingPrerequisites, double completenessScore) {
        if (canAdvance) {
            return 0;
        }
        int blockerHours = Math.max(1, missingPrerequisites.size()) * 6;
        int readinessPenaltyHours = (int) Math.ceil(Math.max(0.0, config.threshold - completenessScore) * 24.0);
        return Math.max(1, blockerHours + readinessPenaltyHours);
    }

    private String humanizeReadyInHours(int hours) {
        if (hours <= 0) {
            return "Ready now";
        }
        if (hours < 24) {
            return "~" + hours + " hours";
        }
        int days = Math.max(1, (int) Math.round(hours / 24.0));
        return "~" + days + (days == 1 ? " day" : " days");
    }

    private double predictionConfidence(
            double completenessScore,
            int missingPrerequisiteCount,
            boolean evidenceAvailable,
            boolean healthReady
    ) {
        double signalPenalty = (evidenceAvailable ? 0.0 : 0.12) + (healthReady ? 0.0 : 0.10);
        double blockerPenalty = Math.min(0.25, missingPrerequisiteCount * 0.04);
        return roundScore(Math.max(0.35, Math.min(0.95, completenessScore - signalPenalty - blockerPenalty)));
    }

    private boolean isArtifactComplete(
            PhasePacket.RequiredArtifact required,
            List<PhasePacket.CompletedArtifact> completedArtifacts
    ) {
        return required.isComplete()
                || completedArtifacts.stream().anyMatch(completed ->
                        equalsCanonical(completed.artifactId(), required.artifactId())
                                || equalsCanonical(completed.artifactType(), required.artifactType()));
    }

    private boolean equalsCanonical(String actual, String expected) {
        return actual != null && expected != null && actual.equalsIgnoreCase(expected);
    }

    private double roundScore(double score) {
        return Math.max(0.0, Math.min(1.0, Math.round(score * 100.0) / 100.0));
    }

    private boolean isPolicyDenied(PhasePacket.GovernanceRecord record) {
        return "DENIED".equalsIgnoreCase(record.outcome())
                || "POLICY_DENIAL".equalsIgnoreCase(record.type());
    }

    private boolean isEvidenceDegraded(PhasePacket.PhaseEvidence evidence) {
        return "SYSTEM_DEGRADED".equalsIgnoreCase(evidence.type())
                || "EVIDENCE_QUERY_FAILED".equalsIgnoreCase(evidence.id());
    }

    private boolean isCompletedArtifactsUnavailable(PhasePacket.CompletedArtifact artifact) {
        return "SYSTEM_DEGRADED".equalsIgnoreCase(artifact.artifactType())
                || "COMPLETED_ARTIFACT_QUERY_FAILED".equalsIgnoreCase(artifact.artifactId());
    }

    // ─── Config loading ──────────────────────────────────────────────────────

    private ReadinessConfig loadConfig() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        String configDir = System.getProperty(CONFIG_DIR_PROP);
        if (configDir != null && !configDir.isBlank()) {
            Path external = Paths.get(configDir, RELATIVE_PATH);
            if (Files.exists(external)) {
                try (InputStream is = Files.newInputStream(external)) {
                    ReadinessConfig result = parse(mapper, is);
                    log.info("PhaseReadinessEvaluator: loaded config from external path {}", external);
                    return result;
                } catch (IOException e) {
                    log.warn("PhaseReadinessEvaluator: could not read {}, falling back to defaults", external, e);
                }
            }
        }

        InputStream is = PhaseReadinessEvaluator.class.getResourceAsStream("/" + RELATIVE_PATH);
        if (is == null) {
            log.warn("PhaseReadinessEvaluator: {} not found on classpath — using default config", RELATIVE_PATH);
            return ReadinessConfig.defaults();
        }
        try (is) {
            return parse(mapper, is);
        } catch (IOException e) {
            log.warn("PhaseReadinessEvaluator: failed to parse {}, using default config", RELATIVE_PATH, e);
            return ReadinessConfig.defaults();
        }
    }

    private ReadinessConfig parse(ObjectMapper mapper, InputStream is) throws IOException {
        return mapper.readValue(is, ReadinessConfig.class);
    }

    // ─── Config model ────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ReadinessConfig {
        @JsonProperty("threshold")
        double threshold;

        @JsonProperty("weights")
        Weights weights;

        static ReadinessConfig defaults() {
            ReadinessConfig config = new ReadinessConfig();
            config.threshold = 0.90;
            config.weights = new Weights();
            config.weights.artifact = 0.40;
            config.weights.blocker = 0.25;
            config.weights.evidence = 0.15;
            config.weights.governance = 0.10;
            config.weights.health = 0.10;
            return config;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class Weights {
        @JsonProperty("artifact")
        double artifact;

        @JsonProperty("blocker")
        double blocker;

        @JsonProperty("evidence")
        double evidence;

        @JsonProperty("governance")
        double governance;

        @JsonProperty("health")
        double health;
    }
}