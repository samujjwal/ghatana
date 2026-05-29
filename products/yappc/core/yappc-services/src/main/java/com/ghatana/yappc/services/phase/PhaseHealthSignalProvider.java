package com.ghatana.yappc.services.phase;

import com.ghatana.core.runtime.PreviewRuntimeService;
import com.ghatana.yappc.api.PhasePacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds health signals for phase packets from preview runtime service.
 *
 * @doc.type class
 * @doc.purpose Builds health signals for phase packets from preview runtime service
 * @doc.layer service
 * @doc.pattern Service
 */
public final class PhaseHealthSignalProvider {

    private static final Logger log = LoggerFactory.getLogger(PhaseHealthSignalProvider.class);

    private final PreviewRuntimeService previewRuntimeService;

    public PhaseHealthSignalProvider(@NotNull PreviewRuntimeService previewRuntimeService) {
        this.previewRuntimeService = Objects.requireNonNull(previewRuntimeService, "previewRuntimeService");
    }

    PhasePacket.HealthSignals build(String phase, String projectId, Map<String, Object> projectState) {
        try {
            Optional<String> previewId = stringState(projectState, "previewId");
            Optional<String> generationId = stringState(projectState, "generationId");
            Optional<String> runtimeId = stringState(projectState, "runtimeId");
            PhasePacket.PreviewSecurity previewSecurity = buildPreviewSecurity(projectState);

            if (isProductionRuntime() && (previewId.isEmpty() || generationId.isEmpty() || runtimeId.isEmpty())) {
                return buildProductionMissingIdentifierHealth(projectState, previewSecurity, previewId, generationId, runtimeId);
            }

            String effectivePreviewId = previewId.orElse(projectId + "-" + phase.toLowerCase(java.util.Locale.ROOT));
            String effectiveGenerationId = generationId.orElse(effectivePreviewId + "-gen");
            String effectiveRuntimeId = runtimeId.orElse(effectivePreviewId + "-runtime");

            PreviewRuntimeService.PreviewHealthStatus previewHealth = previewRuntimeService.getHealth(effectivePreviewId);
            PreviewRuntimeService.GenerationHealthStatus generationHealth = previewRuntimeService.getGenerationHealth(effectiveGenerationId);
            PreviewRuntimeService.RuntimeHealthStatus runtimeHealth = previewRuntimeService.getRuntimeHealth(effectiveRuntimeId);
            List<String> previewIssues = new ArrayList<>(previewHealth.issues());
            previewIssues.addAll(previewSecurity.issues());

            return new PhasePacket.HealthSignals(
                    new PhasePacket.PreviewHealth(
                            previewHealth.healthy() && previewSecurity.safe(),
                            previewSecurity.safe() ? previewHealth.status() : "unsafe",
                            List.copyOf(previewIssues),
                            previewSecurity),
                    new PhasePacket.GenerationHealth(
                            generationHealth.healthy(),
                            generationHealth.status(),
                            generationHealth.generationId(),
                            generationHealth.issues()),
                    new PhasePacket.RuntimeHealth(
                            runtimeHealth.healthy(),
                            runtimeHealth.status(),
                            runtimeHealth.runtimeId(),
                            runtimeHealth.issues()),
                    buildAgentGovernanceHealth(projectState));
        } catch (Exception exception) {
            log.error("Error building health signals: phase={}, projectId={}", phase, projectId, exception);
            return new PhasePacket.HealthSignals(
                    new PhasePacket.PreviewHealth(false, "error", List.of("Health check failed")),
                    new PhasePacket.GenerationHealth(false, "error", null, List.of("Health check failed")),
                    new PhasePacket.RuntimeHealth(false, "error", null, List.of("Health check failed")),
                    new PhasePacket.AgentGovernanceHealth(
                            false,
                            "error",
                            "unknown",
                            "none",
                            List.of(),
                            List.of("Health check failed")));
        }
    }

    private PhasePacket.HealthSignals buildProductionMissingIdentifierHealth(
            Map<String, Object> projectState,
            PhasePacket.PreviewSecurity previewSecurity,
            Optional<String> previewId,
            Optional<String> generationId,
            Optional<String> runtimeId
    ) {
        List<String> missingIdentifiers = new ArrayList<>();
        if (previewId.isEmpty()) {
            missingIdentifiers.add("Missing previewId in production project state");
        }
        if (generationId.isEmpty()) {
            missingIdentifiers.add("Missing generationId in production project state");
        }
        if (runtimeId.isEmpty()) {
            missingIdentifiers.add("Missing runtimeId in production project state");
        }
        List<String> previewIssues = new ArrayList<>(missingIdentifiers);
        previewIssues.addAll(previewSecurity.issues());
        return new PhasePacket.HealthSignals(
                new PhasePacket.PreviewHealth(false, "degraded", List.copyOf(previewIssues), previewSecurity),
                new PhasePacket.GenerationHealth(false, "degraded", generationId.orElse(null), List.copyOf(missingIdentifiers)),
                new PhasePacket.RuntimeHealth(false, "degraded", runtimeId.orElse(null), List.copyOf(missingIdentifiers)),
                buildAgentGovernanceHealth(projectState));
    }

    @SuppressWarnings("unchecked")
    private PhasePacket.AgentGovernanceHealth buildAgentGovernanceHealth(Map<String, Object> projectState) {
        Object rawGovernance = projectState.get("agentGovernance");
        if (!(rawGovernance instanceof Map<?, ?> governance)) {
            return PhasePacket.AgentGovernanceHealth.unknown();
        }

        String status = stringValue(governance.get("status")).orElse("unknown");
        String governanceState = stringValue(governance.get("governanceState")).orElse(status);
        String learningLevel = stringValue(governance.get("learningLevel")).orElse("none");
        List<String> evidenceIds = stringList(governance.get("evidenceIds"));
        if (evidenceIds.isEmpty()) {
            evidenceIds = stringList(governance.get("learningEvidenceIds"));
        }
        List<String> issues = stringList(governance.get("issues"));
        boolean isHealthy = Boolean.TRUE.equals(governance.get("healthy"))
                || ("healthy".equalsIgnoreCase(status) && issues.isEmpty());
        return new PhasePacket.AgentGovernanceHealth(
                isHealthy,
                status,
                governanceState,
                learningLevel,
                evidenceIds,
                issues);
    }

    @SuppressWarnings("unchecked")
    private PhasePacket.PreviewSecurity buildPreviewSecurity(Map<String, Object> projectState) {
        Object rawSecurity = projectState.get("previewSecurity");
        if (!(rawSecurity instanceof Map<?, ?> raw)) {
            return PhasePacket.PreviewSecurity.safeDefault();
        }

        Object rawTrustLevel = raw.get("trustLevel");
        String trustLevel = rawTrustLevel instanceof String value && !value.isBlank()
                ? value.toLowerCase(java.util.Locale.ROOT)
                : "trusted";
        String expiresAt = raw.get("expiresAt") instanceof String value && !value.isBlank() ? value : null;
        boolean expired = Boolean.TRUE.equals(raw.get("expired")) || isExpired(expiresAt);
        List<PhasePacket.TokenScope> tokenScopes = new ArrayList<>();
        Object scopesObject = raw.get("tokenScopes");
        if (scopesObject == null) {
            scopesObject = raw.get("tokenScope");
        }
        if (scopesObject instanceof List<?> scopes) {
            for (Object scopeObject : scopes) {
                if (scopeObject instanceof Map<?, ?> scope) {
                    Object rawId = scope.get("id");
                    String id = rawId instanceof String value && !value.isBlank() ? value : "preview-scope";
                    Object rawName = scope.get("name");
                    String name = rawName instanceof String value && !value.isBlank() ? value : id;
                    boolean required = Boolean.TRUE.equals(scope.get("required"));
                    boolean granted = Boolean.TRUE.equals(scope.get("granted"));
                    tokenScopes.add(new PhasePacket.TokenScope(id, name, required, granted));
                }
            }
        }

        List<String> issues = new ArrayList<>();
        Object rawIssues = raw.get("issues");
        if (rawIssues instanceof List<?> issueList) {
            issueList.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(issue -> !issue.isBlank())
                    .forEach(issues::add);
        }
        Object rawMismatches = raw.get("scopeMismatches");
        if (rawMismatches instanceof List<?> mismatches) {
            mismatches.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(issue -> !issue.isBlank())
                    .forEach(issue -> issues.add("Scope mismatch: " + issue));
        }

        long missingRequiredScopes = tokenScopes.stream()
                .filter(scope -> scope.required() && !scope.granted())
                .count();
        if (missingRequiredScopes > 0) {
            issues.add(missingRequiredScopes + " required preview token scope(s) are not granted");
        }
        if (expired) {
            issues.add("Preview token is expired");
        }
        if ("untrusted".equals(trustLevel)) {
            issues.add("Preview trust level is untrusted");
        }

        boolean explicitSafe = raw.get("safe") instanceof Boolean value ? value : true;
        boolean safe = explicitSafe && !"untrusted".equals(trustLevel) && !expired && missingRequiredScopes == 0 && issues.isEmpty();
        return new PhasePacket.PreviewSecurity(
                trustLevel,
                List.copyOf(tokenScopes),
                expiresAt,
                expired,
                safe,
                List.copyOf(issues));
    }

    private boolean isExpired(@Nullable String expiresAt) {
        if (expiresAt == null || expiresAt.isBlank()) {
            return false;
        }
        try {
            return Instant.parse(expiresAt).isBefore(Instant.now());
        } catch (Exception ignored) {
            return true;
        }
    }

    private Optional<String> stringState(Map<String, Object> projectState, String key) {
        return stringValue(projectState.get(key));
    }

    private Optional<String> stringValue(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return Optional.of(text);
        }
        return Optional.empty();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(text -> !text.isBlank())
                .toList();
    }

    private static boolean isProductionRuntime() {
        return isProductionValue(System.getProperty("ghatana.runtime.profile"))
                || isProductionValue(System.getProperty("yappc.runtime.profile"))
                || isProductionValue(System.getenv("GHATANA_RUNTIME_PROFILE"))
                || isProductionValue(System.getenv("GHATANA_ENV"))
                || isProductionValue(System.getenv("SPRING_PROFILES_ACTIVE"));
    }

    private static boolean isProductionValue(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String part : value.split("[,;\\s]+")) {
            String normalized = part.trim().toLowerCase(java.util.Locale.ROOT);
            if ("production".equals(normalized) || "prod".equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}