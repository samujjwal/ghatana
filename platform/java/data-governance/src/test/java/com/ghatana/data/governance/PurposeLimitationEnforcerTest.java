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
@DisplayName("DefaultPurposeLimitationEnforcer [GH-90000]")
class PurposeLimitationEnforcerTest extends EventloopTestBase {

    private DefaultPurposeLimitationEnforcer enforcer;

    @BeforeEach
    void setUp() { // GH-90000
        enforcer = new DefaultPurposeLimitationEnforcer(); // GH-90000
    }

    @Nested
    @DisplayName("bindPurpose [GH-90000]")
    class BindPurposeTests {

        @Test
        @DisplayName("binding stores allowed purposes [GH-90000]")
        void storesPurposes() { // GH-90000
            runPromise(() -> enforcer.bindPurpose("t1", "user-emails", Set.of("analytics [GH-90000]")));
            Set<String> allowed = runPromise(() -> enforcer.getAllowedPurposes("t1", "user-emails")); // GH-90000
            assertThat(allowed).containsExactly("analytics [GH-90000]");
        }

        @Test
        @DisplayName("rebinding overwrites previous purposes [GH-90000]")
        void rebindingOverwrites() { // GH-90000
            runPromise(() -> enforcer.bindPurpose("t1", "user-emails", Set.of("analytics [GH-90000]")));
            runPromise(() -> enforcer.bindPurpose("t1", "user-emails", Set.of("marketing", "support"))); // GH-90000
            Set<String> allowed = runPromise(() -> enforcer.getAllowedPurposes("t1", "user-emails")); // GH-90000
            assertThat(allowed).containsExactlyInAnyOrder("marketing", "support"); // GH-90000
        }

        @Test
        @DisplayName("binding with empty set fails [GH-90000]")
        void emptySetFails() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> enforcer.bindPurpose("t1", "data", Set.of())) // GH-90000
            ).isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("binding with null fails [GH-90000]")
        void nullSetFails() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> enforcer.bindPurpose("t1", "data", null)) // GH-90000
            ).isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("enforceForPurpose [GH-90000]")
    class EnforceTests {

        @Test
        @DisplayName("allows when purpose is in binding [GH-90000]")
        void allowsPermittedPurpose() { // GH-90000
            runPromise(() -> enforcer.bindPurpose("t1", "emails", Set.of("analytics [GH-90000]")));
            runPromise(() -> enforcer.enforceForPurpose("t1", "emails", "analytics")); // GH-90000
        }

        @Test
        @DisplayName("throws PurposeViolationException for disallowed purpose [GH-90000]")
        void rejectsDisallowedPurpose() { // GH-90000
            runPromise(() -> enforcer.bindPurpose("t1", "emails", Set.of("analytics [GH-90000]")));
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> enforcer.enforceForPurpose("t1", "emails", "marketing")) // GH-90000
            ).isInstanceOf(PurposeViolationException.class) // GH-90000
             .satisfies(ex -> { // GH-90000
                 PurposeViolationException e = (PurposeViolationException) ex; // GH-90000
                 assertThat(e.requestedPurpose()).isEqualTo("marketing [GH-90000]");
                 assertThat(e.allowedPurposes()).containsExactly("analytics [GH-90000]");
             });
        }

        @Test
        @DisplayName("throws PurposeViolationException when no binding exists [GH-90000]")
        void rejectsUnboundData() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> enforcer.enforceForPurpose("t1", "unknown-data", "analytics")) // GH-90000
            ).isInstanceOf(PurposeViolationException.class); // GH-90000
        }

        @Test
        @DisplayName("tenants are isolated [GH-90000]")
        void tenantsAreIsolated() { // GH-90000
            runPromise(() -> enforcer.bindPurpose("tenantA", "data", Set.of("analytics [GH-90000]")));
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> enforcer.enforceForPurpose("tenantB", "data", "analytics")) // GH-90000
            ).isInstanceOf(PurposeViolationException.class); // GH-90000
        }
    }
}
