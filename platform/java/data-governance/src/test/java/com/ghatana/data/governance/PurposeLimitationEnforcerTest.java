/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void setUp() { // GH-90000
        enforcer = new DefaultPurposeLimitationEnforcer(); // GH-90000
    }

    @Nested
    @DisplayName("bindPurpose")
    class BindPurposeTests {

        @Test
        @DisplayName("binding stores allowed purposes")
        void storesPurposes() { // GH-90000
            runPromise(() -> enforcer.bindPurpose("t1", "user-emails", Set.of("analytics")));
            Set<String> allowed = runPromise(() -> enforcer.getAllowedPurposes("t1", "user-emails")); // GH-90000
            assertThat(allowed).containsExactly("analytics");
        }

        @Test
        @DisplayName("rebinding overwrites previous purposes")
        void rebindingOverwrites() { // GH-90000
            runPromise(() -> enforcer.bindPurpose("t1", "user-emails", Set.of("analytics")));
            runPromise(() -> enforcer.bindPurpose("t1", "user-emails", Set.of("marketing", "support"))); // GH-90000
            Set<String> allowed = runPromise(() -> enforcer.getAllowedPurposes("t1", "user-emails")); // GH-90000
            assertThat(allowed).containsExactlyInAnyOrder("marketing", "support"); // GH-90000
        }

        @Test
        @DisplayName("binding with empty set fails")
        void emptySetFails() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> enforcer.bindPurpose("t1", "data", Set.of())) // GH-90000
            ).isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("binding with null fails")
        void nullSetFails() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> enforcer.bindPurpose("t1", "data", null)) // GH-90000
            ).isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("enforceForPurpose")
    class EnforceTests {

        @Test
        @DisplayName("allows when purpose is in binding")
        void allowsPermittedPurpose() { // GH-90000
            runPromise(() -> enforcer.bindPurpose("t1", "emails", Set.of("analytics")));
            runPromise(() -> enforcer.enforceForPurpose("t1", "emails", "analytics")); // GH-90000
        }

        @Test
        @DisplayName("throws PurposeViolationException for disallowed purpose")
        void rejectsDisallowedPurpose() { // GH-90000
            runPromise(() -> enforcer.bindPurpose("t1", "emails", Set.of("analytics")));
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> enforcer.enforceForPurpose("t1", "emails", "marketing")) // GH-90000
            ).isInstanceOf(PurposeViolationException.class) // GH-90000
             .satisfies(ex -> { // GH-90000
                 PurposeViolationException e = (PurposeViolationException) ex; // GH-90000
                 assertThat(e.requestedPurpose()).isEqualTo("marketing");
                 assertThat(e.allowedPurposes()).containsExactly("analytics");
             });
        }

        @Test
        @DisplayName("throws PurposeViolationException when no binding exists")
        void rejectsUnboundData() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> enforcer.enforceForPurpose("t1", "unknown-data", "analytics")) // GH-90000
            ).isInstanceOf(PurposeViolationException.class); // GH-90000
        }

        @Test
        @DisplayName("tenants are isolated")
        void tenantsAreIsolated() { // GH-90000
            runPromise(() -> enforcer.bindPurpose("tenantA", "data", Set.of("analytics")));
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> enforcer.enforceForPurpose("tenantB", "data", "analytics")) // GH-90000
            ).isInstanceOf(PurposeViolationException.class); // GH-90000
        }
    }
}
