/*
 * Copyright (c) 2026 Ghatana Inc. 
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
        void ownerShouldAlwaysHaveAccess() { 
            MemoryNamespace ns = new MemoryNamespace( 
                    "ns-1", "tenant-1", "agent-owner",
                    "private", true, "version-check",
                    true, 90, 30, "Private namespace");

            assertThat(ns.canRead("agent-owner")).isTrue();
            assertThat(ns.canWrite("agent-owner")).isTrue();
        }

        @Test
        @DisplayName("private namespace should deny non-owner access")
        void privateShouldDenyNonOwner() { 
            MemoryNamespace ns = new MemoryNamespace( 
                    "ns-1", "tenant-1", "agent-owner",
                    "private", true, "last-write-wins",
                    false, 0, 0, null);

            assertThat(ns.canRead("other-agent")).isFalse();
            assertThat(ns.canWrite("other-agent")).isFalse();
        }

        @Test
        @DisplayName("shared-read namespace should allow read but deny write to non-owner")
        void sharedReadShouldAllowReadOnly() { 
            MemoryNamespace ns = new MemoryNamespace( 
                    "ns-1", "tenant-1", "agent-owner",
                    "shared-read", false, "last-write-wins",
                    false, 0, 0, null);

            assertThat(ns.canRead("other-agent")).isTrue();
            assertThat(ns.canWrite("other-agent")).isFalse();
        }

        @Test
        @DisplayName("shared-write namespace should allow both read and write to non-owner")
        void sharedWriteShouldAllowBoth() { 
            MemoryNamespace ns = new MemoryNamespace( 
                    "ns-1", "tenant-1", "agent-owner",
                    "shared-write", true, "version-check",
                    true, 90, 30, null);

            assertThat(ns.canRead("other-agent")).isTrue();
            assertThat(ns.canWrite("other-agent")).isTrue();
        }

        @Test
        @DisplayName("public-read namespace should allow read but deny write to non-owner")
        void publicReadShouldAllowReadOnly() { 
            MemoryNamespace ns = new MemoryNamespace( 
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
        void shouldRejectNullFields() { 
            assertThatThrownBy(() -> new MemoryNamespace( 
                    null, "tenant-1", "owner", "private",
                    false, "last-write-wins", false, 0, 0, null))
                    .isInstanceOf(NullPointerException.class); 

            assertThatThrownBy(() -> new MemoryNamespace( 
                    "ns-1", null, "owner", "private",
                    false, "last-write-wins", false, 0, 0, null))
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        @DisplayName("should reject negative retention days")
        void shouldRejectNegativeRetention() { 
            assertThatThrownBy(() -> new MemoryNamespace( 
                    "ns-1", "tenant-1", "owner", "private",
                    false, "last-write-wins", false, -1, 0, null))
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("retentionDays");
        }

        @Test
        @DisplayName("should allow zero retention (permanent)")
        void shouldAllowZeroRetention() { 
            MemoryNamespace ns = new MemoryNamespace( 
                    "ns-1", "tenant-1", "owner", "private",
                    true, "last-write-wins", true, 0, 0, null);

            assertThat(ns.retentionDays()).isEqualTo(0); 
        }
    }

    @Nested
    @DisplayName("MemoryNamespace properties")
    class Properties {

        @Test
        @DisplayName("should carry all governance configuration")
        void shouldCarryAllConfig() { 
            MemoryNamespace ns = new MemoryNamespace( 
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
            assertThat(ns.versioningEnabled()).isTrue(); 
            assertThat(ns.conflictResolution()).isEqualTo("version-check");
            assertThat(ns.provenanceRequired()).isTrue(); 
            assertThat(ns.retentionDays()).isEqualTo(90); 
            assertThat(ns.verificationIntervalDays()).isEqualTo(30); 
            assertThat(ns.description()).isEqualTo("Shared procurement namespace");
        }
    }
}
