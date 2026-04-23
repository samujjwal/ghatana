/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.pluggability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link AgentPackage}, {@link AgentPackageSource}, and the fluent builder.
 *
 * @doc.type class
 * @doc.purpose Tests for P8-T2: AgentPackage + AgentPackageBuilder
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AgentPackage (P8-T2)")
class AgentPackageTest {

    private static final String PACKAGE_ID = "pkg-123";
    private static final String IMPL_CLASS  = "com.example.MyAgent";
    private static final Instant NOW        = Instant.parse("2026-01-01T00:00:00Z");

    private static AgentCapabilityManifest sampleManifest() { // GH-90000
        return AgentCapabilityManifest.standalone("agent-001", "1.0.0", "tenant-001"); // GH-90000
    }

    @Nested
    @DisplayName("record validation")
    class RecordValidation {

        @Test
        @DisplayName("blank packageId is rejected")
        void blankPackageIdRejected() { // GH-90000
            assertThatThrownBy(() -> new AgentPackage( // GH-90000
                    "", sampleManifest(), IMPL_CLASS, AgentPackageSource.DYNAMIC, // GH-90000
                    AgentPackage.ReleaseState.STABLE, null, NOW, Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("packageId");
        }

        @Test
        @DisplayName("blank implementationClass is rejected")
        void blankImplClassRejected() { // GH-90000
            assertThatThrownBy(() -> new AgentPackage( // GH-90000
                    PACKAGE_ID, sampleManifest(), "", AgentPackageSource.DYNAMIC, // GH-90000
                    AgentPackage.ReleaseState.STABLE, null, NOW, Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("implementationClass");
        }

        @Test
        @DisplayName("metadata is defensively immutable")
        void metadataIsImmutable() { // GH-90000
            AgentPackage pkg = new AgentPackage( // GH-90000
                    PACKAGE_ID, sampleManifest(), IMPL_CLASS, AgentPackageSource.LOCAL_FILE, // GH-90000
                    AgentPackage.ReleaseState.STABLE, null, NOW, Map.of("k", "v")); // GH-90000
            assertThatThrownBy(() -> pkg.metadata().put("x", "y")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("convenience helpers")
    class Helpers {

        @Test
        @DisplayName("agentId() delegates to manifest")
        void agentIdDelegatesToManifest() { // GH-90000
            AgentPackage pkg = new AgentPackage( // GH-90000
                    PACKAGE_ID, sampleManifest(), IMPL_CLASS, // GH-90000
                    AgentPackageSource.BUILT_IN, AgentPackage.ReleaseState.STABLE, null, NOW, Map.of()); // GH-90000
            assertThat(pkg.agentId()).isEqualTo("agent-001");
        }

        @Test
        @DisplayName("agentVersion() delegates to manifest")
        void agentVersionDelegatesToManifest() { // GH-90000
            AgentPackage pkg = new AgentPackage( // GH-90000
                    PACKAGE_ID, sampleManifest(), IMPL_CLASS, // GH-90000
                    AgentPackageSource.BUILT_IN, AgentPackage.ReleaseState.STABLE, null, NOW, Map.of()); // GH-90000
            assertThat(pkg.agentVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("hasChecksum() is false when checksum is null")
        void hasChecksumFalseForNull() { // GH-90000
            AgentPackage pkg = new AgentPackage( // GH-90000
                    PACKAGE_ID, sampleManifest(), IMPL_CLASS, // GH-90000
                    AgentPackageSource.DYNAMIC, AgentPackage.ReleaseState.STABLE, null, NOW, Map.of()); // GH-90000
            assertThat(pkg.hasChecksum()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("hasChecksum() is true when checksum is provided")
        void hasChecksumTrueWhenProvided() { // GH-90000
            AgentPackage pkg = new AgentPackage( // GH-90000
                    PACKAGE_ID, sampleManifest(), IMPL_CLASS, // GH-90000
                    AgentPackageSource.REMOTE_REGISTRY, AgentPackage.ReleaseState.STABLE, "abc123", NOW, Map.of()); // GH-90000
            assertThat(pkg.hasChecksum()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("builder")
    class BuilderTests {

        @Test
        @DisplayName("builder creates equivalent record")
        void builderCreatesEquivalentRecord() { // GH-90000
            AgentCapabilityManifest m = sampleManifest(); // GH-90000
            AgentPackage pkg = AgentPackage.builder() // GH-90000
                    .packageId(PACKAGE_ID) // GH-90000
                    .manifest(m) // GH-90000
                    .implementationClass(IMPL_CLASS) // GH-90000
                    .source(AgentPackageSource.LOCAL_FILE) // GH-90000
                    .checksum("deadbeef")
                    .registeredAt(NOW) // GH-90000
                    .metadata(Map.of("tag", "test")) // GH-90000
                    .build(); // GH-90000

            assertThat(pkg.packageId()).isEqualTo(PACKAGE_ID); // GH-90000
            assertThat(pkg.manifest()).isEqualTo(m); // GH-90000
            assertThat(pkg.implementationClass()).isEqualTo(IMPL_CLASS); // GH-90000
            assertThat(pkg.source()).isEqualTo(AgentPackageSource.LOCAL_FILE); // GH-90000
            assertThat(pkg.checksum()).isEqualTo("deadbeef");
            assertThat(pkg.registeredAt()).isEqualTo(NOW); // GH-90000
            assertThat(pkg.metadata()).containsEntry("tag", "test"); // GH-90000
        }

        @Test
        @DisplayName("builder defaults source to DYNAMIC")
        void builderDefaultsSourceToDynamic() { // GH-90000
            AgentPackage pkg = AgentPackage.builder() // GH-90000
                    .packageId(PACKAGE_ID) // GH-90000
                    .manifest(sampleManifest()) // GH-90000
                    .implementationClass(IMPL_CLASS) // GH-90000
                    .build(); // GH-90000
            assertThat(pkg.source()).isEqualTo(AgentPackageSource.DYNAMIC); // GH-90000
        }

        @Test
        @DisplayName("builder rejects blank packageId")
        void builderRejectsBlankPackageId() { // GH-90000
            assertThatThrownBy(() -> AgentPackage.builder() // GH-90000
                    .packageId("")
                    .manifest(sampleManifest()) // GH-90000
                    .implementationClass(IMPL_CLASS) // GH-90000
                    .build()) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("packageId");
        }
    }
}
