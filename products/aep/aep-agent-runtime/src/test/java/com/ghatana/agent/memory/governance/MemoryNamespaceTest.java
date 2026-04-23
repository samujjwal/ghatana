/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.memory.governance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for WP4: Memory governance — MemoryNamespace access control,
 * versioning, and provenance requirements.
 */
@DisplayName("Memory Governance (WP4)")
class MemoryNamespaceTest {

    @Nested
    @DisplayName("MemoryNamespace access control")
    class AccessControl {

        @Test
        @DisplayName("owner should always have read and write access")
        void ownerShouldAlwaysHaveAccess() { // GH-90000
            MemoryNamespace ns = new MemoryNamespace( // GH-90000
                    "ns-1", "tenant-1", "agent-owner",
                    "private", true, "version-check",
                    true, 90, 30, "Private namespace");

            assertThat(ns.canRead("agent-owner")).isTrue();
            assertThat(ns.canWrite("agent-owner")).isTrue();
        }

        @Test
        @DisplayName("private namespace should deny non-owner access")
        void privateShouldDenyNonOwner() { // GH-90000
            MemoryNamespace ns = new MemoryNamespace( // GH-90000
                    "ns-1", "tenant-1", "agent-owner",
                    "private", true, "last-write-wins",
                    false, 0, 0, null);

            assertThat(ns.canRead("other-agent")).isFalse();
            assertThat(ns.canWrite("other-agent")).isFalse();
        }

        @Test
        @DisplayName("shared-read namespace should allow read but deny write to non-owner")
        void sharedReadShouldAllowReadOnly() { // GH-90000
            MemoryNamespace ns = new MemoryNamespace( // GH-90000
                    "ns-1", "tenant-1", "agent-owner",
                    "shared-read", false, "last-write-wins",
                    false, 0, 0, null);

            assertThat(ns.canRead("other-agent")).isTrue();
            assertThat(ns.canWrite("other-agent")).isFalse();
        }

        @Test
        @DisplayName("shared-write namespace should allow both read and write to non-owner")
        void sharedWriteShouldAllowBoth() { // GH-90000
            MemoryNamespace ns = new MemoryNamespace( // GH-90000
                    "ns-1", "tenant-1", "agent-owner",
                    "shared-write", true, "version-check",
                    true, 90, 30, null);

            assertThat(ns.canRead("other-agent")).isTrue();
            assertThat(ns.canWrite("other-agent")).isTrue();
        }

        @Test
        @DisplayName("public-read namespace should allow read but deny write to non-owner")
        void publicReadShouldAllowReadOnly() { // GH-90000
            MemoryNamespace ns = new MemoryNamespace( // GH-90000
                    "ns-1", "tenant-1", "agent-owner",
                    "public-read", false, "last-write-wins",
                    false, 0, 0, null);

            assertThat(ns.canRead("other-agent")).isTrue();
            assertThat(ns.canWrite("other-agent")).isFalse();
        }
    }

    @Nested
    @DisplayName("MemoryNamespace validation")
    class Validation {

        @Test
        @DisplayName("should reject null required fields")
        void shouldRejectNullFields() { // GH-90000
            assertThatThrownBy(() -> new MemoryNamespace( // GH-90000
                    null, "tenant-1", "owner", "private",
                    false, "last-write-wins", false, 0, 0, null))
                    .isInstanceOf(NullPointerException.class); // GH-90000

            assertThatThrownBy(() -> new MemoryNamespace( // GH-90000
                    "ns-1", null, "owner", "private",
                    false, "last-write-wins", false, 0, 0, null))
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should reject negative retention days")
        void shouldRejectNegativeRetention() { // GH-90000
            assertThatThrownBy(() -> new MemoryNamespace( // GH-90000
                    "ns-1", "tenant-1", "owner", "private",
                    false, "last-write-wins", false, -1, 0, null))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("retentionDays");
        }

        @Test
        @DisplayName("should allow zero retention (permanent)")
        void shouldAllowZeroRetention() { // GH-90000
            MemoryNamespace ns = new MemoryNamespace( // GH-90000
                    "ns-1", "tenant-1", "owner", "private",
                    true, "last-write-wins", true, 0, 0, null);

            assertThat(ns.retentionDays()).isEqualTo(0); // GH-90000
        }
    }

    @Nested
    @DisplayName("MemoryNamespace properties")
    class Properties {

        @Test
        @DisplayName("should carry all governance configuration")
        void shouldCarryAllConfig() { // GH-90000
            MemoryNamespace ns = new MemoryNamespace( // GH-90000
                    "tenant.procurement.shared",
                    "tenant-acme",
                    "agent.procurement-assistant",
                    "shared-write",
                    true,
                    "version-check",
                    true,
                    90,
                    30,
                    "Shared procurement namespace");

            assertThat(ns.id()).isEqualTo("tenant.procurement.shared");
            assertThat(ns.tenantId()).isEqualTo("tenant-acme");
            assertThat(ns.ownerId()).isEqualTo("agent.procurement-assistant");
            assertThat(ns.sharingMode()).isEqualTo("shared-write");
            assertThat(ns.versioningEnabled()).isTrue(); // GH-90000
            assertThat(ns.conflictResolution()).isEqualTo("version-check");
            assertThat(ns.provenanceRequired()).isTrue(); // GH-90000
            assertThat(ns.retentionDays()).isEqualTo(90); // GH-90000
            assertThat(ns.verificationIntervalDays()).isEqualTo(30); // GH-90000
            assertThat(ns.description()).isEqualTo("Shared procurement namespace");
        }
    }
}
