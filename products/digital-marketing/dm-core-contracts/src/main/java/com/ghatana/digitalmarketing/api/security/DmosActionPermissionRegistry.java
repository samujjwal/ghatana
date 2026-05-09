package com.ghatana.digitalmarketing.api.security;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical DMOS backend action-to-role permission registry.
 *
 * <p>This class is generated from the canonical route manifest.
 * Do not edit manually - regenerate from dmos-route-manifest.yaml.</p>
 *
 * @doc.type class
 * @doc.purpose Canonical backend action-level role authorization for DMOS APIs
 * @doc.layer product
 * @doc.pattern Policy, Registry
 */
public final class DmosActionPermissionRegistry {

    private static final Map<String, Integer> ROLE_ORDER = Map.of(
        "admin", 4,
        "brand-manager", 1,
        "exec-sponsor", 3,
        "marketing-director", 2,
        "viewer", 0,
        "viewer", 0,
        "brand-manager", 1,
        "marketing-director", 2,
        "exec-sponsor", 3,
        "admin", 4
    );

    private static final Map<String, String> ACTION_MINIMUM_ROLES = Map.ofEntries(
        Map.entry("approve", "brand-manager"),
        Map.entry("approve-budget", "exec-sponsor"),
        Map.entry("approve-optimizations", "marketing-director"),
        Map.entry("approve-strategy", "marketing-director"),
        Map.entry("archive-campaign", "brand-manager"),
        Map.entry("complete-campaign", "brand-manager"),
        Map.entry("create-campaign", "brand-manager"),
        Map.entry("duplicate-campaign", "brand-manager"),
        Map.entry("generate-budget", "marketing-director"),
        Map.entry("generate-strategy", "brand-manager"),
        Map.entry("launch-campaign", "brand-manager"),
        Map.entry("manage-agency", "admin"),
        Map.entry("manage-channels", "marketing-director"),
        Map.entry("manage-funnel", "brand-manager"),
        Map.entry("manage-locales", "brand-manager"),
        Map.entry("pause-campaign", "brand-manager"),
        Map.entry("reject", "brand-manager"),
        Map.entry("review-approval", "viewer"),
        Map.entry("rollback-campaign", "brand-manager"),
        Map.entry("submit-budget", "marketing-director"),
        Map.entry("submit-strategy", "brand-manager"),
        Map.entry("view-approval-detail", "viewer"),
        Map.entry("view-attribution", "brand-manager"),
        Map.entry("view-audit-log", "viewer"),
        Map.entry("view-budget", "marketing-director"),
        Map.entry("view-campaign-detail", "brand-manager"),
        Map.entry("view-campaigns", "brand-manager"),
        Map.entry("view-dashboard", "viewer"),
        Map.entry("view-funnel", "brand-manager"),
        Map.entry("view-recommendations", "brand-manager"),
        Map.entry("view-research", "brand-manager"),
        Map.entry("view-roi", "marketing-director"),
        Map.entry("view-strategy", "brand-manager"),
        Map.entry("view-dashboard", "viewer"),
        Map.entry("review-approval", "viewer"),
        Map.entry("approve", "brand-manager"),
        Map.entry("reject", "brand-manager"),
        Map.entry("view-audit-log", "viewer"),
        Map.entry("launch-campaign", "brand-manager"),
        Map.entry("submit-strategy", "brand-manager"),
        Map.entry("approve-strategy", "marketing-director"),
        Map.entry("submit-budget", "marketing-director"),
        Map.entry("approve-budget", "exec-sponsor")
    );

    private DmosActionPermissionRegistry() {
    }

    public static boolean isActionAllowed(Set<String> roles, String action) {
        Objects.requireNonNull(action, "action must not be null");

        String normalizedAction = action.trim().toLowerCase(Locale.ROOT);
        String minimumRole = ACTION_MINIMUM_ROLES.get(normalizedAction);
        if (minimumRole == null) {
            throw new IllegalArgumentException("Unknown DMOS action: " + action);
        }

        int requiredOrder = ROLE_ORDER.getOrDefault(minimumRole, Integer.MAX_VALUE);
        int highestRoleOrder = roles == null
            ? Integer.MIN_VALUE
            : roles.stream()
                .map(DmosActionPermissionRegistry::normalizeRole)
                .filter(ROLE_ORDER::containsKey)
                .mapToInt(ROLE_ORDER::get)
                .max()
                .orElse(Integer.MIN_VALUE);

        return highestRoleOrder >= requiredOrder;
    }

    private static String normalizeRole(String role) {
        if (role == null) {
            return "";
        }

        return role.trim()
            .toLowerCase(Locale.ROOT)
            .replace('_', '-')
            .replace(' ', '-');
    }
}
