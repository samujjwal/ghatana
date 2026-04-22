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
@DisplayName("Memory Governance (WP4) [GH-90000]")
class MemoryNamespaceTest {

    @Nested
    @DisplayName("MemoryNamespace access control [GH-90000]")
    class AccessControl {

        @Test
        @DisplayName("owner should always have read and write access [GH-90000]")
        void ownerShouldAlwaysHaveAccess() { // GH-90000
            MemoryNamespace ns = new MemoryNamespace( // GH-90000
                    "ns-1", "tenant-1", "agent-owner",
                    "private", true, "version-check",
                    true, 90, 30, "Private namespace");

            assertThat(ns.canRead("agent-owner [GH-90000]")).isTrue();
            assertThat(ns.canWrite("agent-owner [GH-90000]")).isTrue();
        }

        @Test
        @DisplayName("private namespace should deny non-owner access [GH-90000]")
        void privateShouldDenyNonOwner() { // GH-90000
            MemoryNamespace ns = new MemoryNamespace( // GH-90000
                    "ns-1", "tenant-1", "agent-owner",
                    "private", true, "last-write-wins",
                    false, 0, 0, null);

            assertThat(ns.canRead("other-agent [GH-90000]")).isFalse();
            assertThat(ns.canWrite("other-agent [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("shared-read namespace should allow read but deny write to non-owner [GH-90000]")
        void sharedReadShouldAllowReadOnly() { // GH-90000
            MemoryNamespace ns = new MemoryNamespace( // GH-90000
                    "ns-1", "tenant-1", "agent-owner",
                    "shared-read", false, "last-write-wins",
                    false, 0, 0, null);

            assertThat(ns.canRead("other-agent [GH-90000]")).isTrue();
            assertThat(ns.canWrite("other-agent [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("shared-write namespace should allow both read and write to non-owner [GH-90000]")
        void sharedWriteShouldAllowBoth() { // GH-90000
            MemoryNamespace ns = new MemoryNamespace( // GH-90000
                    "ns-1", "tenant-1", "agent-owner",
                    "shared-write", true, "version-check",
                    true, 90, 30, null);

            assertThat(ns.canRead("other-agent [GH-90000]")).isTrue();
            assertThat(ns.canWrite("other-agent [GH-90000]")).isTrue();
        }

        @Test
        @DisplayName("public-read namespace should allow read but deny write to non-owner [GH-90000]")
        void publicReadShouldAllowReadOnly() { // GH-90000
            MemoryNamespace ns = new MemoryNamespace( // GH-90000
                    "ns-1", "tenant-1", "agent-owner",
                    "public-read", false, "last-write-wins",
                    false, 0, 0, null);

            assertThat(ns.canRead("other-agent [GH-90000]")).isTrue();
            assertThat(ns.canWrite("other-agent [GH-90000]")).isFalse();
        }
    }

    @Nested
    @DisplayName("MemoryNamespace validation [GH-90000]")
    class Validation {

        @Test
        @DisplayName("should reject null required fields [GH-90000]")
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
        @DisplayName("should reject negative retention days [GH-90000]")
        void shouldRejectNegativeRetention() { // GH-90000
            assertThatThrownBy(() -> new MemoryNamespace( // GH-90000
                    "ns-1", "tenant-1", "owner", "private",
                    false, "last-write-wins", false, -1, 0, null))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("retentionDays [GH-90000]");
        }

        @Test
        @DisplayName("should allow zero retention (permanent) [GH-90000]")
        void shouldAllowZeroRetention() { // GH-90000
            MemoryNamespace ns = new MemoryNamespace( // GH-90000
                    "ns-1", "tenant-1", "owner", "private",
                    true, "last-write-wins", true, 0, 0, null);

            assertThat(ns.retentionDays()).isEqualTo(0); // GH-90000
        }
    }

    @Nested
    @DisplayName("MemoryNamespace properties [GH-90000]")
    class Properties {

        @Test
        @DisplayName("should carry all governance configuration [GH-90000]")
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

            assertThat(ns.id()).isEqualTo("tenant.procurement.shared [GH-90000]");
            assertThat(ns.tenantId()).isEqualTo("tenant-acme [GH-90000]");
            assertThat(ns.ownerId()).isEqualTo("agent.procurement-assistant [GH-90000]");
            assertThat(ns.sharingMode()).isEqualTo("shared-write [GH-90000]");
            assertThat(ns.versioningEnabled()).isTrue(); // GH-90000
            assertThat(ns.conflictResolution()).isEqualTo("version-check [GH-90000]");
            assertThat(ns.provenanceRequired()).isTrue(); // GH-90000
            assertThat(ns.retentionDays()).isEqualTo(90); // GH-90000
            assertThat(ns.verificationIntervalDays()).isEqualTo(30); // GH-90000
            assertThat(ns.description()).isEqualTo("Shared procurement namespace [GH-90000]");
        }
    }
}
