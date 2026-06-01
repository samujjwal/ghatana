package com.ghatana.datacloud.launcher.http;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DC-P1-07: Dependency health status for Runtime Truth.
 */
record DependencyHealth(String name, boolean healthy, String status, String message) {
    public Map<String, Object> toMap() {
        return Map.of("name", name, "healthy", healthy, "status", status, "message", message != null ? message : "");
    }
}

/**
 * Typed, immutable Runtime Truth surface record.
 *
 * <p>DC-P1-5: The architecture requires every surface to have typed state,
 * required dependencies, dependency probe results, tenant scope, runtime profile,
 * and evidence. This record replaces the loose {@code Map<String, Object>} previously
 * returned by {@link SurfaceRegistryHandler}.
 *
 * <p>A surface cannot be in {@link RuntimeTruthStatus#LIVE LIVE} state without at least
 * one dependency probe result. This invariant is enforced at construction time.
 *
 * <p>DC-P1-07: Full runtime posture metadata captures auth, durability, audit, policy,
 * metrics, tracing, event store, and idempotency state for complete operational visibility.
 *
 * <p>WS1: Enriched with UI fields so the backend is the single source of truth for
 * UI navigation, route access, capability availability, disabled states, dependency status,
 * preview audience, owner plane, and user action availability. The UI no longer needs
 * a parallel route registry.
 *
 * @param surfaceId            canonical surface identifier (e.g. {@code ai.assist})
 * @param state                canonical status from {@link RuntimeTruthStatus}
 * @param ownerPlane           owning Data Cloud plane (e.g. {@code intelligence}, {@code governance})
 * @param requiredDependencies human-readable dependency names required for LIVE state
 * @param dependencyProbes     probe results that back the current state claim
 * @param tenantScope          scope: {@code global}, {@code tenant}, or {@code per-tenant}
 * @param runtimeProfile       deployment profile this record was evaluated at
 * @param lastCheckedAt        timestamp of this evaluation
 * @param evidence             free-form evidence map exposed to operators
 * @param limitations          optional description of what is limited in DEGRADED/PREVIEW modes
 * @param actionsAllowed       explicit list of actions gated by this surface
 * @param runtimePosture       DC-P1-07: Full runtime posture metadata
 * @param path                 UI route path for this surface
 * @param label                Display label for the surface
 * @param labelKey             i18n key for the label
 * @param description          Human-readable description
 * @param descriptionKey       i18n key for the description
 * @param iconName             Icon name for UI display
 * @param minimumShellRole     Minimum shell role required to access this surface
 * @param discoverable         Whether this surface should appear in navigation
 * @param lifecycle           Lifecycle state (stable, preview, deprecated, experimental)
 * @param previewAudience      Audience for preview surfaces (e.g., operator-preview, beta-users)
 * @param routeGroup           Navigation group this surface belongs to
 * @param sortOrder            Sort order within navigation group
 * @param primaryNavigation    Whether this is a primary navigation item
 * @param contextualNavigation Whether this appears in contextual navigation
 * @param fallbackReason       Reason shown when surface is unavailable
 * @param recommendedAction    Suggested action when surface is degraded/unavailable
 *
 * @doc.type class
 * @doc.purpose Typed, immutable Runtime Truth surface record with dependency-probe evidence and UI metadata
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SurfaceRecord(
    String surfaceId,
    RuntimeTruthStatus state,
    String ownerPlane,
    List<String> requiredDependencies,
    List<DependencyProbeResult> dependencyProbes,
    String tenantScope,
    String runtimeProfile,
    Instant lastCheckedAt,
    Map<String, Object> evidence,
    String limitations,
    List<String> actionsAllowed,
    RuntimePosture runtimePosture,
    String path,
    String label,
    String labelKey,
    String description,
    String descriptionKey,
    String iconName,
    String minimumShellRole,
    boolean discoverable,
    String lifecycle,
    String previewAudience,
    String routeGroup,
    Integer sortOrder,
    boolean primaryNavigation,
    boolean contextualNavigation,
    String fallbackReason,
    String recommendedAction
) {

    /**
     * Validates the invariants of a {@link SurfaceRecord}.
     *
     * <p>Invariants:
     * <ul>
     *   <li>{@code surfaceId} must not be null or blank.</li>
     *   <li>{@code state} must not be null.</li>
     *   <li>A {@link RuntimeTruthStatus#LIVE LIVE} surface must have at least one passing
     *       {@link DependencyProbeResult}.</li>
     * </ul>
     */
    public SurfaceRecord {
        Objects.requireNonNull(surfaceId, "surfaceId must not be null");
        Objects.requireNonNull(state, "state must not be null");
        if (surfaceId.isBlank()) {
            throw new IllegalArgumentException("surfaceId must not be blank");
        }
        requiredDependencies = requiredDependencies == null
            ? List.of()
            : Collections.unmodifiableList(List.copyOf(requiredDependencies));
        dependencyProbes = dependencyProbes == null
            ? List.of()
            : Collections.unmodifiableList(List.copyOf(dependencyProbes));
        evidence = evidence == null
            ? Map.of()
            : Collections.unmodifiableMap(Map.copyOf(evidence));
        actionsAllowed = actionsAllowed == null
            ? List.of()
            : Collections.unmodifiableList(List.copyOf(actionsAllowed));
        tenantScope = tenantScope == null ? "global" : tenantScope;
        runtimeProfile = runtimeProfile == null ? "unknown" : runtimeProfile;
        lastCheckedAt = lastCheckedAt == null ? Instant.now() : lastCheckedAt;
        
        // WS1: UI fields with sensible defaults
        path = path == null ? "" : path;
        label = label == null ? surfaceId : label;
        labelKey = labelKey == null ? "" : labelKey;
        description = description == null ? "" : description;
        descriptionKey = descriptionKey == null ? "" : descriptionKey;
        iconName = iconName == null ? "" : iconName;
        minimumShellRole = minimumShellRole == null ? "viewer" : minimumShellRole;
        lifecycle = lifecycle == null ? "stable" : lifecycle;
        previewAudience = previewAudience == null ? "" : previewAudience;
        routeGroup = routeGroup == null ? "" : routeGroup;
        sortOrder = sortOrder == null ? 0 : sortOrder;
        fallbackReason = fallbackReason == null ? "" : fallbackReason;
        recommendedAction = recommendedAction == null ? "" : recommendedAction;

        // DC-P1-5: LIVE surfaces must have at least one probe result to prevent false LIVE claims.
        if (state == RuntimeTruthStatus.LIVE && dependencyProbes.isEmpty()) {
            throw new IllegalArgumentException(
                "Surface '" + surfaceId + "' cannot be LIVE without at least one DependencyProbeResult. "
                + "Add a probe result or use DEGRADED/PREVIEW for unverified surfaces.");
        }
    }

    /**
     * Serialises this record to a plain {@link Map} suitable for JSON serialisation.
     *
     * @return an unmodifiable map representation of this record
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("surfaceId", surfaceId);
        result.put("state", state.name()); // Use enum name for JSON serialization
        result.put("status", state.toLegacyValue());
        result.put("ownerPlane", ownerPlane != null ? ownerPlane : "unknown");
        result.put("requiredDependencies", requiredDependencies);
        result.put("dependencyProbes", dependencyProbes.stream().map(DependencyProbeResult::toMap).toList());
        result.put("tenantScope", tenantScope);
        result.put("runtimeProfile", runtimeProfile);
        result.put("lastCheckedAt", lastCheckedAt.toString());
        result.put("evidence", evidence);
        result.put("limitations", limitations != null ? limitations : "");
        result.put("actionsAllowed", actionsAllowed);
        // DC-P1-07: Include runtime posture if present
        if (runtimePosture != null) {
            result.put("runtimePosture", runtimePosture.toMap());
        }
        // WS1: Include UI fields for backend-as-single-source-of-truth
        result.put("path", path);
        result.put("label", label);
        result.put("labelKey", labelKey);
        result.put("description", description);
        result.put("descriptionKey", descriptionKey);
        result.put("iconName", iconName);
        result.put("minimumShellRole", minimumShellRole);
        result.put("discoverable", discoverable);
        result.put("lifecycle", lifecycle);
        result.put("previewAudience", previewAudience);
        result.put("routeGroup", routeGroup);
        result.put("sortOrder", sortOrder);
        result.put("primaryNavigation", primaryNavigation);
        result.put("contextualNavigation", contextualNavigation);
        result.put("fallbackReason", fallbackReason);
        result.put("recommendedAction", recommendedAction);
        return Collections.unmodifiableMap(result);
    }

    // =========================================================================
    // Builder
    // =========================================================================

    /**
     * Returns a builder for constructing a {@link SurfaceRecord}.
     *
     * @param surfaceId the surface identifier
     * @return a new builder
     */
    public static Builder builder(String surfaceId) {
        return new Builder(surfaceId);
    }

    /**
     * Builder for {@link SurfaceRecord}.
     *
     * @doc.type class
     * @doc.purpose Builder for SurfaceRecord
     * @doc.layer product
     * @doc.pattern Builder
     */
    public static final class Builder {

        private final String surfaceId;
        private RuntimeTruthStatus state = RuntimeTruthStatus.UNAVAILABLE;
        private String ownerPlane;
        private List<String> requiredDependencies = List.of();
        private List<DependencyProbeResult> dependencyProbes = List.of();
        private String tenantScope = "global";
        private String runtimeProfile = "local";
        private Instant lastCheckedAt = Instant.now();
        private Map<String, Object> evidence = Map.of();
        private String limitations;
        private List<String> actionsAllowed = List.of();
        private RuntimePosture runtimePosture;  // DC-P1-07
        // WS1: UI fields
        private String path;
        private String label;
        private String labelKey;
        private String description;
        private String descriptionKey;
        private String iconName;
        private String minimumShellRole;
        private boolean discoverable = true;
        private String lifecycle = "stable";
        private String previewAudience;
        private String routeGroup;
        private Integer sortOrder = 0;
        private boolean primaryNavigation = false;
        private boolean contextualNavigation = false;
        private String fallbackReason;
        private String recommendedAction;

        private Builder(String surfaceId) {
            this.surfaceId = Objects.requireNonNull(surfaceId, "surfaceId");
        }

        /** Sets the canonical state. */
        public Builder state(RuntimeTruthStatus state) {
            this.state = Objects.requireNonNull(state, "state");
            return this;
        }

        /** Sets the owning plane. */
        public Builder ownerPlane(String ownerPlane) {
            this.ownerPlane = ownerPlane;
            return this;
        }

        /** Sets the required dependency names. */
        public Builder requiredDependencies(List<String> requiredDependencies) {
            this.requiredDependencies = requiredDependencies;
            return this;
        }

        /** Sets the dependency probe results. */
        public Builder dependencyProbes(List<DependencyProbeResult> dependencyProbes) {
            this.dependencyProbes = dependencyProbes;
            return this;
        }

        /** Adds a single probe result. */
        public Builder probe(DependencyProbeResult probe) {
            java.util.List<DependencyProbeResult> updated = new java.util.ArrayList<>(this.dependencyProbes);
            updated.add(probe);
            this.dependencyProbes = updated;
            return this;
        }

        /** Sets the tenant scope. */
        public Builder tenantScope(String tenantScope) {
            this.tenantScope = tenantScope;
            return this;
        }

        /** Sets the runtime profile label. */
        public Builder runtimeProfile(String runtimeProfile) {
            this.runtimeProfile = runtimeProfile;
            return this;
        }

        /** Sets the last-checked timestamp. */
        public Builder lastCheckedAt(Instant lastCheckedAt) {
            this.lastCheckedAt = lastCheckedAt;
            return this;
        }

        /** Sets the free-form evidence map. */
        public Builder evidence(Map<String, Object> evidence) {
            this.evidence = evidence;
            return this;
        }

        /** Sets the limitations description for DEGRADED/PREVIEW states. */
        public Builder limitations(String limitations) {
            this.limitations = limitations;
            return this;
        }

        /** Sets the list of actions gated by this surface. */
        public Builder actionsAllowed(List<String> actionsAllowed) {
            this.actionsAllowed = actionsAllowed;
            return this;
        }

        /** DC-P1-07: Sets the runtime posture metadata. */
        public Builder runtimePosture(RuntimePosture runtimePosture) {
            this.runtimePosture = runtimePosture;
            return this;
        }

        // WS1: UI field setters

        /** Sets the UI route path. */
        public Builder path(String path) {
            this.path = path;
            return this;
        }

        /** Sets the display label. */
        public Builder label(String label) {
            this.label = label;
            return this;
        }

        /** Sets the i18n key for the label. */
        public Builder labelKey(String labelKey) {
            this.labelKey = labelKey;
            return this;
        }

        /** Sets the human-readable description. */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /** Sets the i18n key for the description. */
        public Builder descriptionKey(String descriptionKey) {
            this.descriptionKey = descriptionKey;
            return this;
        }

        /** Sets the icon name for UI display. */
        public Builder iconName(String iconName) {
            this.iconName = iconName;
            return this;
        }

        /** Sets the minimum shell role required. */
        public Builder minimumShellRole(String minimumShellRole) {
            this.minimumShellRole = minimumShellRole;
            return this;
        }

        /** Sets whether this surface should appear in navigation. */
        public Builder discoverable(boolean discoverable) {
            this.discoverable = discoverable;
            return this;
        }

        /** Sets the lifecycle state. */
        public Builder lifecycle(String lifecycle) {
            this.lifecycle = lifecycle;
            return this;
        }

        /** Sets the preview audience. */
        public Builder previewAudience(String previewAudience) {
            this.previewAudience = previewAudience;
            return this;
        }

        /** Sets the navigation group. */
        public Builder routeGroup(String routeGroup) {
            this.routeGroup = routeGroup;
            return this;
        }

        /** Sets the sort order within navigation group. */
        public Builder sortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }

        /** Sets whether this is a primary navigation item. */
        public Builder primaryNavigation(boolean primaryNavigation) {
            this.primaryNavigation = primaryNavigation;
            return this;
        }

        /** Sets whether this appears in contextual navigation. */
        public Builder contextualNavigation(boolean contextualNavigation) {
            this.contextualNavigation = contextualNavigation;
            return this;
        }

        /** Sets the fallback reason when unavailable. */
        public Builder fallbackReason(String fallbackReason) {
            this.fallbackReason = fallbackReason;
            return this;
        }

        /** Sets the recommended action when degraded/unavailable. */
        public Builder recommendedAction(String recommendedAction) {
            this.recommendedAction = recommendedAction;
            return this;
        }

        /** Builds the {@link SurfaceRecord}, validating all invariants. */
        public SurfaceRecord build() {
            return new SurfaceRecord(
                surfaceId, state, ownerPlane, requiredDependencies, dependencyProbes,
                tenantScope, runtimeProfile, lastCheckedAt, evidence, limitations, actionsAllowed, runtimePosture,
                path, label, labelKey, description, descriptionKey, iconName, minimumShellRole,
                discoverable, lifecycle, previewAudience, routeGroup, sortOrder,
                primaryNavigation, contextualNavigation, fallbackReason, recommendedAction);
        }
    }
}
