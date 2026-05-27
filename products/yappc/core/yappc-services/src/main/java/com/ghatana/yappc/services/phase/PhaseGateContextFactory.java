package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;
import com.ghatana.yappc.services.lifecycle.gate.PhaseGateValidator;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class PhaseGateContextFactory {

    PhaseGateValidator.PhaseGateContext build(
            String phase,
            String projectId,
            String workspaceId,
            Map<String, Object> projectState,
            List<PhasePacket.RequiredArtifact> requiredArtifacts,
            List<PhasePacket.CompletedArtifact> completedArtifacts,
            List<PhasePacket.PhaseEvidence> evidence,
            List<PhasePacket.GovernanceRecord> governance,
            PhasePacket.HealthSignals healthSignals,
            Set<String> enabledFlags
    ) {
        Map<String, Boolean> context = new HashMap<>();
        Set<String> requiredArtifactIds = requiredArtifacts.stream()
                .map(PhasePacket.RequiredArtifact::artifactId)
                .collect(Collectors.toSet());
        Set<String> completedArtifactIds = completedArtifacts.stream()
                .map(PhasePacket.CompletedArtifact::artifactId)
                .collect(Collectors.toSet());

        context.put("project.workspace-scoped", workspaceId != null && !workspaceId.isBlank());
        context.put("project.tenant-scoped", projectState.get("tenantId") != null);
        context.put("project.state-loaded", !projectState.isEmpty());
        context.put("evidence.available", !evidence.isEmpty());
        context.put(
                "policyAllowed",
                governance.stream().noneMatch(record -> "DENIED".equalsIgnoreCase(record.outcome())));
        context.put("previewHealthy", healthSignals.preview().isHealthy());
        context.put("generationHealthy", healthSignals.generation().isHealthy());
        context.put("runtimeHealthy", healthSignals.runtime().isHealthy());
        context.put("phase.advance-enabled", enabledFlags.contains("phase.advance"));

        for (PhasePacket.RequiredArtifact artifact : requiredArtifacts) {
            boolean completed = completedArtifactIds.contains(artifact.artifactId());
            context.put(artifact.artifactId(), completed);
            context.put("artifact:" + artifact.artifactId(), completed);
        }

        addBooleanConditionValues(context, projectState.get("conditions"));
        addBooleanConditionValues(context, projectState.get("gateConditions"));
        addBooleanConditionValues(context, projectState.get("criteriaStatus"));
        addCollectionConditions(context, projectState.get("satisfiedCriteria"), true);
        addCollectionConditions(context, projectState.get("unsatisfiedCriteria"), false);
        return new PhaseGateValidator.PhaseGateContext(
                requiredArtifactIds,
                completedArtifactIds,
                !evidence.isEmpty(),
                governance.stream().noneMatch(record -> "DENIED".equalsIgnoreCase(record.outcome())),
                healthSignals.preview().isHealthy(),
                healthSignals.generation().isHealthy(),
                healthSignals.runtime().isHealthy(),
                enabledFlags,
                context);
    }

    private static void addBooleanConditionValues(Map<String, Boolean> target, Object rawConditions) {
        if (!(rawConditions instanceof Map<?, ?> source)) {
            return;
        }
        source.forEach((key, value) -> {
            if (key != null && value instanceof Boolean satisfied) {
                target.put(String.valueOf(key), satisfied);
            }
        });
    }

    private static void addCollectionConditions(Map<String, Boolean> target, Object rawCriteria, boolean satisfied) {
        if (!(rawCriteria instanceof Collection<?> criteria)) {
            return;
        }
        criteria.stream()
                .filter(value -> value != null && !String.valueOf(value).isBlank())
                .forEach(value -> target.put(String.valueOf(value), satisfied));
    }
}