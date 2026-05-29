package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.util.List;

/**
 * Maps gate validator blockers into canonical phase packet blockers.
 *
 * @doc.type class
 * @doc.purpose Maps gate validator blockers into canonical phase packet blockers
 * @doc.layer service
 * @doc.pattern Mapper
 */
public final class PhaseBlockerMapper {

    List<PhasePacket.PhaseBlocker> map(List<String> blockers) {
        if (blockers == null || blockers.isEmpty()) {
            return List.of();
        }

        return blockers.stream()
                .map(this::mapBlocker)
                .toList();
    }

    private PhasePacket.PhaseBlocker mapBlocker(String blocker) {
        String normalized = blocker == null ? "unknown" : blocker;
        String type = resolveType(normalized);
        String severity = resolveSeverity(normalized);
        String title = normalized
                .replace("entry-criterion: ", "")
                .replace("prior-exit-criterion: ", "")
                .replace("missing-artifact: ", "")
                .replace("policy-denied: ", "")
                .replace("dependency-degraded: ", "");

        return new PhasePacket.PhaseBlocker(
                normalized,
                type,
                title,
                normalized,
                severity,
                normalized,
                !"CRITERION".equals(type) || !"CRITICAL".equals(severity)
        );
    }

    private String resolveType(String blocker) {
        if (blocker.startsWith("missing-artifact")) {
            return "ARTIFACT";
        }
        if (blocker.startsWith("policy-denied")) {
            return "POLICY";
        }
        if (blocker.startsWith("dependency-degraded")) {
            return "DEPENDENCY";
        }
        return "CRITERION";
    }

    private String resolveSeverity(String blocker) {
        if (blocker.startsWith("missing-artifact") || blocker.startsWith("policy-denied") || blocker.startsWith("dependency-degraded")) {
            return "CRITICAL";
        }
        return "WARNING";
    }
}
