/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.data.governance;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DefaultPurposeLimitationEnforcer}.
 */
@DisplayName("DefaultPurposeLimitationEnforcer")
class PurposeLimitationEnforcerTest extends EventloopTestBase {

    private DefaultPurposeLimitationEnforcer enforcer;

    @BeforeEach
    void setUp() {
        enforcer = new DefaultPurposeLimitationEnforcer();
    }

    @Nested
    @DisplayName("bindPurpose")
    class BindPurposeTests {

        @Test
        @DisplayName("binding stores allowed purposes")
        void storesPurposes() {
            runPromise(() -> enforcer.bindPurpose("t1", "user-emails", Set.of("analytics")));
            Set<String> allowed = runPromise(() -> enforcer.getAllowedPurposes("t1", "user-emails"));
            assertThat(allowed).containsExactly("analytics");
        }

        @Test
        @DisplayName("rebinding overwrites previous purposes")
        void rebindingOverwrites() {
            runPromise(() -> enforcer.bindPurpose("t1", "user-emails", Set.of("analytics")));
            runPromise(() -> enforcer.bindPurpose("t1", "user-emails", Set.of("marketing", "support")));
            Set<String> allowed = runPromise(() -> enforcer.getAllowedPurposes("t1", "user-emails"));
            assertThat(allowed).containsExactlyInAnyOrder("marketing", "support");
        }

        @Test
        @DisplayName("binding with empty set fails")
        void emptySetFails() {
            assertThatThrownBy(() ->
                runPromise(() -> enforcer.bindPurpose("t1", "data", Set.of()))
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("binding with null fails")
        void nullSetFails() {
            assertThatThrownBy(() ->
                runPromise(() -> enforcer.bindPurpose("t1", "data", null))
            ).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("enforceForPurpose")
    class EnforceTests {

        @Test
        @DisplayName("allows when purpose is in binding")
        void allowsPermittedPurpose() {
            runPromise(() -> enforcer.bindPurpose("t1", "emails", Set.of("analytics")));
            runPromise(() -> enforcer.enforceForPurpose("t1", "emails", "analytics"));
        }

        @Test
        @DisplayName("throws PurposeViolationException for disallowed purpose")
        void rejectsDisallowedPurpose() {
            runPromise(() -> enforcer.bindPurpose("t1", "emails", Set.of("analytics")));
            assertThatThrownBy(() ->
                runPromise(() -> enforcer.enforceForPurpose("t1", "emails", "marketing"))
            ).isInstanceOf(PurposeViolationException.class)
             .satisfies(ex -> {
                 PurposeViolationException e = (PurposeViolationException) ex;
                 assertThat(e.requestedPurpose()).isEqualTo("marketing");
                 assertThat(e.allowedPurposes()).containsExactly("analytics");
             });
        }

        @Test
        @DisplayName("throws PurposeViolationException when no binding exists")
        void rejectsUnboundData() {
            assertThatThrownBy(() ->
                runPromise(() -> enforcer.enforceForPurpose("t1", "unknown-data", "analytics"))
            ).isInstanceOf(PurposeViolationException.class);
        }

        @Test
        @DisplayName("tenants are isolated")
        void tenantsAreIsolated() {
            runPromise(() -> enforcer.bindPurpose("tenantA", "data", Set.of("analytics")));
            assertThatThrownBy(() ->
                runPromise(() -> enforcer.enforceForPurpose("tenantB", "data", "analytics"))
            ).isInstanceOf(PurposeViolationException.class);
        }
    }
}
