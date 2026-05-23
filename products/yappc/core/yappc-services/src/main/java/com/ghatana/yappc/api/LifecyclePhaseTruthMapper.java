package com.ghatana.yappc.api;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Maps a durable lifecycle execution into per-phase truth persisted by YAPPC.
 *
 * @doc.type class
 * @doc.purpose Extract canonical phase state, gate context, evidence, runtime truth, feature flags, and entitlements
 * @doc.layer api
 * @doc.pattern Mapper
 */
final class LifecyclePhaseTruthMapper {

    private LifecyclePhaseTruthMapper() {
    }

    static PhaseTruthSnapshot fromExecution(
            @NotNull LifecycleExecutionRepository.LifecycleExecution execution,
            @NotNull String phase) {
        String normalizedPhase = phase.toUpperCase(Locale.ROOT);
        Map<String, Object> artifacts = phaseArtifacts(execution, normalizedPhase);
        Map<String, Object> runtimeHealth = runtimeHealth(execution, normalizedPhase, artifacts);
        List<Map<String, Object>> evidence = evidenceRefs(artifacts, execution.metadata());
        List<String> featureFlags = stringRefs(artifacts, execution.metadata(), "featureFlags", "enabledPhaseFlags", "feature_flags");
        List<String> tenantEntitlements = stringRefs(artifacts, execution.metadata(), "tenantEntitlements", "entitlements", "tenant_entitlements");
        Map<String, Object> gateContext = gateContext(
                execution,
                normalizedPhase,
                artifacts,
                evidence,
                runtimeHealth,
                featureFlags,
                tenantEntitlements);
        return new PhaseTruthSnapshot(
                gateContext,
                artifacts,
                evidence,
                runtimeHealth,
                featureFlags,
                tenantEntitlements);
    }

    private static Map<String, Object> gateContext(
            LifecycleExecutionRepository.LifecycleExecution execution,
            String phase,
            Map<String, Object> artifacts,
            List<Map<String, Object>> evidence,
            Map<String, Object> runtimeHealth,
            List<String> featureFlags,
            List<String> tenantEntitlements) {
        Map<String, Object> context = new LinkedHashMap<>();
        boolean policyAllowed = policyAllowed(artifacts);
        boolean runtimeHealthy = Boolean.TRUE.equals(runtimeHealth.get("healthy"));
        boolean artifactsPresent = !artifacts.isEmpty();

        context.put("phase", phase);
        context.put("executionId", execution.executionId());
        context.put("tenantId", execution.tenantId());
        context.put("workspaceId", execution.workspaceId());
        context.put("projectId", execution.projectId());
        context.put("artifacts.present", artifactsPresent);
        context.put("policy.allowed", policyAllowed);
        context.put("evidence.available", !evidence.isEmpty());
        context.put("runtime.healthy", runtimeHealthy);
        context.put("featureFlags.loaded", !featureFlags.isEmpty());
        context.put("tenantEntitlements.loaded", !tenantEntitlements.isEmpty());
        context.put("durationMs", execution.phaseDurationsMs().getOrDefault(phase, 0L));
        context.put("status", "FAILED".equalsIgnoreCase(execution.status()) ? "BLOCKED" : "COMPLETED");

        List<String> missingInputs = new ArrayList<>();
        if (!artifactsPresent) {
            missingInputs.add("artifacts");
        }
        if (evidence.isEmpty()) {
            missingInputs.add("evidence");
        }
        if (featureFlags.isEmpty()) {
            missingInputs.add("featureFlags");
        }
        if (tenantEntitlements.isEmpty()) {
            missingInputs.add("tenantEntitlements");
        }
        if (!policyAllowed) {
            missingInputs.add("policy");
        }
        if (!runtimeHealthy) {
            missingInputs.add("runtimeHealth");
        }
        context.put("missingInputs", List.copyOf(missingInputs));
        context.put("complete", missingInputs.isEmpty());
        return Map.copyOf(context);
    }

    private static boolean policyAllowed(Map<String, Object> artifacts) {
        Object explicit = firstValue(artifacts, "policyAllowed", "policy_allowed", "allowed", "allClear");
        if (explicit instanceof Boolean value) {
            return value;
        }
        Object valid = firstValue(artifacts, "valid", "success");
        if (valid instanceof Boolean value) {
            return value;
        }
        return !hasNonEmptyValue(artifacts, "violations", "policyViolations", "blockers", "errors");
    }

    private static Map<String, Object> runtimeHealth(
            LifecycleExecutionRepository.LifecycleExecution execution,
            String phase,
            Map<String, Object> artifacts) {
        Map<String, Object> health = new LinkedHashMap<>();
        Object explicitHealthy = firstValue(artifacts, "runtimeHealthy", "healthy", "health");
        boolean healthy = !"FAILED".equalsIgnoreCase(execution.status());
        if (explicitHealthy instanceof Boolean value) {
            healthy = value;
        }
        Object artifactStatus = firstValue(artifacts, "status", "runtimeStatus");
        if (artifactStatus instanceof String status) {
            String normalized = status.toUpperCase(Locale.ROOT);
            if (normalized.contains("FAILED") || normalized.contains("UNHEALTHY") || normalized.contains("BLOCKED")) {
                healthy = false;
            }
        }
        health.put("phase", phase);
        health.put("status", execution.status());
        health.put("healthy", healthy);
        health.put("durationMs", execution.phaseDurationsMs().getOrDefault(phase, 0L));
        health.put("completedAt", execution.completedAt().toString());
        return Map.copyOf(health);
    }

    private static List<Map<String, Object>> evidenceRefs(Map<String, Object> artifacts, Map<String, String> metadata) {
        List<Map<String, Object>> refs = new ArrayList<>();
        addEvidenceRefs(refs, artifacts, "evidenceRefs", "evidenceIds", "evidence", "provenance", "traceId", "trace_id");
        if (metadata != null) {
            addEvidenceRefs(refs, metadata, "evidenceRefs", "evidenceIds", "evidence", "provenance", "traceId", "trace_id");
        }
        return List.copyOf(refs);
    }

    private static void addEvidenceRefs(List<Map<String, Object>> refs, Map<?, ?> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Map<?, ?> map) {
                refs.add(Map.of("sourceKey", key, "payload", stringifyMap(map)));
            } else if (value instanceof Iterable<?> iterable) {
                for (Object entry : iterable) {
                    addEvidenceRef(refs, key, entry);
                }
            } else {
                addEvidenceRef(refs, key, value);
            }
        }
    }

    private static void addEvidenceRef(List<Map<String, Object>> refs, String key, Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return;
        }
        refs.add(Map.of("sourceKey", key, "ref", String.valueOf(value)));
    }

    private static List<String> stringRefs(Map<String, Object> artifacts, Map<String, String> metadata, String... keys) {
        Set<String> values = new LinkedHashSet<>();
        addStringRefs(values, artifacts, keys);
        if (metadata != null) {
            addStringRefs(values, metadata, keys);
        }
        return List.copyOf(values);
    }

    private static void addStringRefs(Set<String> values, Map<?, ?> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Iterable<?> iterable) {
                for (Object entry : iterable) {
                    addStringRef(values, entry);
                }
            } else if (value instanceof String text && text.contains(",")) {
                for (String entry : text.split(",")) {
                    addStringRef(values, entry);
                }
            } else {
                addStringRef(values, value);
            }
        }
    }

    private static void addStringRef(Set<String> values, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (!text.isBlank()) {
            values.add(text);
        }
    }

    private static Object firstValue(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static boolean hasNonEmptyValue(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value instanceof Iterable<?> iterable) {
                if (iterable.iterator().hasNext()) {
                    return true;
                }
            } else if (value instanceof Map<?, ?> map) {
                if (!map.isEmpty()) {
                    return true;
                }
            } else if (value != null && !String.valueOf(value).isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> stringifyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null && value != null) {
                result.put(String.valueOf(key), value);
            }
        });
        return Map.copyOf(result);
    }

    private static Map<String, Object> phaseArtifacts(LifecycleExecutionRepository.LifecycleExecution execution, String phase) {
        return switch (phase) {
            case "INTENT" -> nullToEmpty(execution.intentResult());
            case "SHAPE" -> nullToEmpty(execution.shapeResult());
            case "VALIDATE" -> nullToEmpty(execution.validationResult());
            case "GENERATE" -> nullToEmpty(execution.generationResult());
            case "RUN" -> nullToEmpty(execution.runResult());
            case "OBSERVE" -> nullToEmpty(execution.observationResult());
            case "LEARN" -> nullToEmpty(execution.learningResult());
            case "EVOLVE" -> nullToEmpty(execution.evolutionResult());
            default -> Map.of();
        };
    }

    private static Map<String, Object> nullToEmpty(Map<String, Object> value) {
        return value == null ? Map.of() : Map.copyOf(value);
    }

    record PhaseTruthSnapshot(
            Map<String, Object> gateContext,
            Map<String, Object> artifacts,
            List<Map<String, Object>> evidence,
            Map<String, Object> runtimeHealth,
            List<String> featureFlags,
            List<String> tenantEntitlements) {
    }
}
