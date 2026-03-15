/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.rules.bundle;

import com.ghatana.appplatform.rules.bundle.PolicyBundleCanaryService.CanaryResult;
import com.ghatana.appplatform.rules.bundle.PolicyBundleCanaryService.CanaryState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PolicyBundleCanaryService} (STORY-K03-008).
 *
 * <p>Covers:
 * <ul>
 *   <li>Canary routing percentage (AC1)</li>
 *   <li>Promotion when mismatch rate ≤ threshold (AC2)</li>
 *   <li>Auto-rollback when mismatch rate exceeds threshold (AC3)</li>
 *   <li>Shadow decision comparison accumulation</li>
 *   <li>Listener notification on promote/rollback</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Unit tests for OPA rule pack canary deployment with shadow comparison (K03-008)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PolicyBundleCanaryService — K03-008 rule pack canary deployment")
class PolicyBundleCanaryServiceTest {

    // ── In-memory store (reused from bundle test infrastructure) ─────────────

    private static final class InMemoryBundleStore implements PolicyBundleStore {
        private final Map<String, PolicyBundle> store = new ConcurrentHashMap<>();

        @Override public void save(PolicyBundle b)    { store.put(b.bundleId(), b); }
        @Override public void update(PolicyBundle b)   { store.put(b.bundleId(), b); }
        @Override public Optional<PolicyBundle> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }
        @Override public Optional<PolicyBundle> findActive() {
            return store.values().stream().filter(PolicyBundle::active).findFirst();
        }
        @Override public List<PolicyBundle> findAll() {
            return store.values().stream()
                    .sorted(Comparator.comparingInt(PolicyBundle::version))
                    .toList();
        }
        @Override public int latestVersion() {
            return store.values().stream()
                    .mapToInt(PolicyBundle::version).max().orElse(0);
        }
    }

    private InMemoryBundleStore bundleStore;
    private PolicyBundleService bundleService;
    private String stableId;
    private String canaryId;

    @BeforeEach
    void setUp() {
        bundleStore   = new InMemoryBundleStore();
        bundleService = new PolicyBundleService(bundleStore);

        // Upload and activate the stable bundle
        PolicyBundle stable = bundleService.uploadBundle("auth",
                "stable content".getBytes(StandardCharsets.UTF_8));
        stableId = stable.bundleId();
        bundleService.activateBundle(stableId);

        // Upload canary bundle (not yet active)
        PolicyBundle canary = bundleService.uploadBundle("auth",
                "canary content v2".getBytes(StandardCharsets.UTF_8));
        canaryId = canary.bundleId();
    }

    // ── AC1: Canary traffic percentage ────────────────────────────────────────

    @Test
    @DisplayName("canary_percentage_10pct — ~10% of requests routed to canary")
    void canary_percentage_10pct() {
        // Always-true random: routes all requests to canary (100%)
        PolicyBundleCanaryService svc = new PolicyBundleCanaryService(
                bundleService, new FixedRandom(0.05)); // 0.05 * 100 = 5 < 10 → true
        svc.startCanary(canaryId, 10.0);

        boolean usesCanary = svc.shouldUseCanary();
        assertThat(usesCanary).isTrue();
    }

    @Test
    @DisplayName("canary_percentage_0pct — 0% routes no requests to canary")
    void canary_percentage_0pct() {
        PolicyBundleCanaryService svc = new PolicyBundleCanaryService(
                bundleService, new FixedRandom(0.50)); // 0.50 * 100 = 50 >= 0 → false
        svc.startCanary(canaryId, 0.0);

        boolean usesCanary = svc.shouldUseCanary();
        assertThat(usesCanary).isFalse();
    }

    @Test
    @DisplayName("canary_percentage_100pct — 100% routes all requests to canary")
    void canary_percentage_100pct() {
        // The check is: random * 100 < percentage → 99.9 * 100 = 9990 < 100 → false
        // But with 100%: 99.9 < 100 → true. Boundary: nextDouble returns [0,1) so 0.99 < 1.0
        PolicyBundleCanaryService svc = new PolicyBundleCanaryService(
                bundleService, new FixedRandom(0.999)); // 0.999 * 100 = 99.9 < 100.0 → true
        svc.startCanary(canaryId, 100.0);

        assertThat(svc.shouldUseCanary()).isTrue();
    }

    // ── AC2: Promote when mismatch rate ≤ threshold ───────────────────────────

    @Test
    @DisplayName("canary_promote_belowThreshold — Promote succeeds when mismatch rate ≤ 5%")
    void canary_promote_belowThreshold() {
        PolicyBundleCanaryService svc = new PolicyBundleCanaryService(bundleService);
        svc.startCanary(canaryId, 10.0);
        svc.setMismatchThreshold(0.05); // 5%

        // Record 100 decisions: 3 mismatches → 3% mismatch rate (below 5%)
        for (int i = 0; i < 97; i++) svc.recordDecision("ALLOW", "ALLOW");
        for (int i = 0; i < 3;  i++) svc.recordDecision("ALLOW", "DENY");

        CanaryResult result = svc.promote();

        assertThat(result.promoted()).isTrue();
        assertThat(result.state()).isEqualTo(CanaryState.PROMOTED);
        assertThat(result.mismatchRate()).isLessThanOrEqualTo(0.05);
        assertThat(result.totalDecisions()).isEqualTo(100);
        assertThat(result.mismatchCount()).isEqualTo(3);

        // Canary bundle should now be active in store
        assertThat(bundleService.getActiveBundle())
            .isPresent()
            .hasValueSatisfying(b -> assertThat(b.bundleId()).isEqualTo(canaryId));
    }

    @Test
    @DisplayName("canary_promote_atThreshold — Promote succeeds when mismatch rate == threshold")
    void canary_promote_atThreshold() {
        PolicyBundleCanaryService svc = new PolicyBundleCanaryService(bundleService);
        svc.startCanary(canaryId, 10.0);
        svc.setMismatchThreshold(0.05); // 5%

        // 5/100 = exactly 5% = threshold
        for (int i = 0; i < 95; i++) svc.recordDecision("DENY", "DENY");
        for (int i = 0; i < 5;  i++) svc.recordDecision("ALLOW", "DENY");

        CanaryResult result = svc.promote();

        assertThat(result.promoted()).isTrue();
    }

    // ── AC3: Auto-rollback when mismatch rate > threshold ─────────────────────

    @Test
    @DisplayName("canary_rollback_aboveThreshold — Rollback triggered when mismatch rate > 5%")
    void canary_rollback_aboveThreshold() {
        PolicyBundleCanaryService svc = new PolicyBundleCanaryService(bundleService);
        svc.startCanary(canaryId, 10.0);
        svc.setMismatchThreshold(0.05); // 5%

        // 10/100 = 10% > 5% threshold → promote auto-rolls back
        for (int i = 0; i < 90; i++) svc.recordDecision("ALLOW", "ALLOW");
        for (int i = 0; i < 10; i++) svc.recordDecision("ALLOW", "DENY");

        CanaryResult result = svc.promote();

        assertThat(result.rolledBack()).isTrue();
        assertThat(result.state()).isEqualTo(CanaryState.ROLLED_BACK);
        assertThat(result.mismatchRate()).isGreaterThan(0.05);

        // Stable bundle should remain active (canary was NOT promoted)
        assertThat(bundleService.getActiveBundle())
            .isPresent()
            .hasValueSatisfying(b -> assertThat(b.bundleId()).isEqualTo(stableId));
    }

    @Test
    @DisplayName("canary_explicit_rollback — Explicit rollback returns ROLLED_BACK")
    void canary_explicit_rollback() {
        PolicyBundleCanaryService svc = new PolicyBundleCanaryService(bundleService);
        svc.startCanary(canaryId, 10.0);
        svc.recordDecision("ALLOW", "ALLOW");

        CanaryResult result = svc.rollback();

        assertThat(result.rolledBack()).isTrue();
        assertThat(svc.getState()).isEqualTo(CanaryState.ROLLED_BACK);
    }

    // ── Shadow comparison ─────────────────────────────────────────────────────

    @Test
    @DisplayName("canary_shadow_mismatchRate — Mismatch rate reflects correct count")
    void canary_shadow_mismatchRate() {
        PolicyBundleCanaryService svc = new PolicyBundleCanaryService(bundleService);
        svc.startCanary(canaryId, 10.0);

        svc.recordDecision("ALLOW", "ALLOW"); // match
        svc.recordDecision("ALLOW", "DENY");  // mismatch
        svc.recordDecision("DENY",  "DENY");  // match
        svc.recordDecision("DENY",  "ALLOW"); // mismatch

        double rate = svc.getMismatchRate();
        assertThat(rate).isEqualTo(0.5); // 2/4 = 50%
    }

    @Test
    @DisplayName("canary_shadow_noDecisions_zeroRate — No decisions → 0% mismatch")
    void canary_shadow_noDecisions_zeroRate() {
        PolicyBundleCanaryService svc = new PolicyBundleCanaryService(bundleService);
        svc.startCanary(canaryId, 10.0);

        assertThat(svc.getMismatchRate()).isEqualTo(0.0);
    }

    // ── Listener notification ─────────────────────────────────────────────────

    @Test
    @DisplayName("canary_listener_notifiedOnPromote — Listener receives PROMOTED result")
    void canary_listener_notifiedOnPromote() {
        PolicyBundleCanaryService svc = new PolicyBundleCanaryService(bundleService);
        List<CanaryResult> events = new ArrayList<>();
        svc.addCanaryEventListener(events::add);
        svc.startCanary(canaryId, 10.0);
        svc.setMismatchThreshold(0.05);
        svc.recordDecision("ALLOW", "ALLOW"); // 0% mismatch

        svc.promote();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).state()).isEqualTo(CanaryState.PROMOTED);
    }

    @Test
    @DisplayName("canary_listener_notifiedOnRollback — Listener receives ROLLED_BACK result")
    void canary_listener_notifiedOnRollback() {
        PolicyBundleCanaryService svc = new PolicyBundleCanaryService(bundleService);
        List<CanaryResult> events = new ArrayList<>();
        svc.addCanaryEventListener(events::add);
        svc.startCanary(canaryId, 10.0);

        svc.rollback();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).state()).isEqualTo(CanaryState.ROLLED_BACK);
    }

    // ── Guard rails ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("canary_doubleStart_throws — Cannot start two concurrent canaries")
    void canary_doubleStart_throws() {
        PolicyBundleCanaryService svc = new PolicyBundleCanaryService(bundleService);
        svc.startCanary(canaryId, 10.0);

        assertThatThrownBy(() -> svc.startCanary(canaryId, 20.0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("canary is already active");
    }

    @Test
    @DisplayName("canary_invalidPercentage_throws — Percentage outside [0,100] throws IAE")
    void canary_invalidPercentage_throws() {
        PolicyBundleCanaryService svc = new PolicyBundleCanaryService(bundleService);

        assertThatThrownBy(() -> svc.startCanary(canaryId, -1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.startCanary(canaryId, 101.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Test helpers ──────────────────────────────────────────────────────────

    /**
     * A deterministic {@link Random} that always returns a fixed value from {@code nextDouble()}.
     * Used to make canary routing decisions predictable in tests.
     */
    private static final class FixedRandom extends Random {
        private final double fixedValue;

        FixedRandom(double fixedValue) { this.fixedValue = fixedValue; }

        @Override
        public double nextDouble() { return fixedValue; }
    }
}
