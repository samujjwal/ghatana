/*
 * Copyright (c) 2026 Ghatana Inc. 
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

    private static AgentCapabilityManifest sampleManifest() { 
        return AgentCapabilityManifest.standalone("agent-001", "1.0.0", "tenant-001"); 
    }

    @Nested
    @DisplayName("record validation")
    class RecordValidation {

        @Test
        @DisplayName("blank packageId is rejected")
        void blankPackageIdRejected() { 
            assertThatThrownBy(() -> new AgentPackage( 
                    "", sampleManifest(), IMPL_CLASS, AgentPackageSource.DYNAMIC, 
                    AgentPackage.ReleaseState.STABLE, null, NOW, Map.of())) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("packageId");
        }

        @Test
        @DisplayName("blank implementationClass is rejected")
        void blankImplClassRejected() { 
            assertThatThrownBy(() -> new AgentPackage( 
                    PACKAGE_ID, sampleManifest(), "", AgentPackageSource.DYNAMIC, 
                    AgentPackage.ReleaseState.STABLE, null, NOW, Map.of())) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("implementationClass");
        }

        @Test
        @DisplayName("metadata is defensively immutable")
        void metadataIsImmutable() { 
            AgentPackage pkg = new AgentPackage( 
                    PACKAGE_ID, sampleManifest(), IMPL_CLASS, AgentPackageSource.LOCAL_FILE, 
                    AgentPackage.ReleaseState.STABLE, null, NOW, Map.of("k", "v")); 
            assertThatThrownBy(() -> pkg.metadata().put("x", "y")) 
                    .isInstanceOf(UnsupportedOperationException.class); 
        }
    }

    @Nested
    @DisplayName("convenience helpers")
    class Helpers {

        @Test
        @DisplayName("agentId() delegates to manifest")
        void agentIdDelegatesToManifest() { 
            AgentPackage pkg = new AgentPackage( 
                    PACKAGE_ID, sampleManifest(), IMPL_CLASS, 
                    AgentPackageSource.BUILT_IN, AgentPackage.ReleaseState.STABLE, null, NOW, Map.of()); 
            assertThat(pkg.agentId()).isEqualTo("agent-001");
        }

        @Test
        @DisplayName("agentVersion() delegates to manifest")
        void agentVersionDelegatesToManifest() { 
            AgentPackage pkg = new AgentPackage( 
                    PACKAGE_ID, sampleManifest(), IMPL_CLASS, 
                    AgentPackageSource.BUILT_IN, AgentPackage.ReleaseState.STABLE, null, NOW, Map.of()); 
            assertThat(pkg.agentVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("hasChecksum() is false when checksum is null")
        void hasChecksumFalseForNull() { 
            AgentPackage pkg = new AgentPackage( 
                    PACKAGE_ID, sampleManifest(), IMPL_CLASS, 
                    AgentPackageSource.DYNAMIC, AgentPackage.ReleaseState.STABLE, null, NOW, Map.of()); 
            assertThat(pkg.hasChecksum()).isFalse(); 
        }

        @Test
        @DisplayName("hasChecksum() is true when checksum is provided")
        void hasChecksumTrueWhenProvided() { 
            AgentPackage pkg = new AgentPackage( 
                    PACKAGE_ID, sampleManifest(), IMPL_CLASS, 
                    AgentPackageSource.REMOTE_REGISTRY, AgentPackage.ReleaseState.STABLE, "abc123", NOW, Map.of()); 
            assertThat(pkg.hasChecksum()).isTrue(); 
        }
    }

    @Nested
    @DisplayName("builder")
    class BuilderTests {

        @Test
        @DisplayName("builder creates equivalent record")
        void builderCreatesEquivalentRecord() { 
            AgentCapabilityManifest m = sampleManifest(); 
            AgentPackage pkg = AgentPackage.builder() 
                    .packageId(PACKAGE_ID) 
                    .manifest(m) 
                    .implementationClass(IMPL_CLASS) 
                    .source(AgentPackageSource.LOCAL_FILE) 
                    .checksum("deadbeef")
                    .registeredAt(NOW) 
                    .metadata(Map.of("tag", "test")) 
                    .build(); 

            assertThat(pkg.packageId()).isEqualTo(PACKAGE_ID); 
            assertThat(pkg.manifest()).isEqualTo(m); 
            assertThat(pkg.implementationClass()).isEqualTo(IMPL_CLASS); 
            assertThat(pkg.source()).isEqualTo(AgentPackageSource.LOCAL_FILE); 
            assertThat(pkg.checksum()).isEqualTo("deadbeef");
            assertThat(pkg.registeredAt()).isEqualTo(NOW); 
            assertThat(pkg.metadata()).containsEntry("tag", "test"); 
        }

        @Test
        @DisplayName("builder defaults source to DYNAMIC")
        void builderDefaultsSourceToDynamic() { 
            AgentPackage pkg = AgentPackage.builder() 
                    .packageId(PACKAGE_ID) 
                    .manifest(sampleManifest()) 
                    .implementationClass(IMPL_CLASS) 
                    .build(); 
            assertThat(pkg.source()).isEqualTo(AgentPackageSource.DYNAMIC); 
        }

        @Test
        @DisplayName("builder rejects blank packageId")
        void builderRejectsBlankPackageId() { 
            assertThatThrownBy(() -> AgentPackage.builder() 
                    .packageId("")
                    .manifest(sampleManifest()) 
                    .implementationClass(IMPL_CLASS) 
                    .build()) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("packageId");
        }
    }
}
