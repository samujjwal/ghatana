/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MemoryLink} — directed inter-item relationships.
 *
 * @doc.type class
 * @doc.purpose Unit tests for MemoryLink value object
 * @doc.layer agent-memory
 * @doc.pattern Test
 */
@DisplayName("MemoryLink - directed relationship between memory items [GH-90000]")
class MemoryLinkTest {

    @Nested
    @DisplayName("Builder defaults [GH-90000]")
    class BuilderDefaultsTests {

        @Test
        @DisplayName("Default strength is 1.0 (full relationship strength) [GH-90000]")
        void defaultStrength_isOne() { // GH-90000
            MemoryLink link = MemoryLink.builder() // GH-90000
                    .targetItemId("item-A [GH-90000]")
                    .linkType(LinkType.RELATED) // GH-90000
                    .build(); // GH-90000
            assertThat(link.getStrength()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("Default description is null [GH-90000]")
        void defaultDescription_isNull() { // GH-90000
            MemoryLink link = MemoryLink.builder() // GH-90000
                    .targetItemId("item-A [GH-90000]")
                    .linkType(LinkType.RELATED) // GH-90000
                    .build(); // GH-90000
            assertThat(link.getDescription()).isNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Custom builder values [GH-90000]")
    class CustomBuilderTests {

        @Test
        @DisplayName("TargetItemId is stored correctly [GH-90000]")
        void targetItemId_storedCorrectly() { // GH-90000
            MemoryLink link = MemoryLink.builder() // GH-90000
                    .targetItemId("target-123 [GH-90000]")
                    .linkType(LinkType.SUPPORTS) // GH-90000
                    .build(); // GH-90000
            assertThat(link.getTargetItemId()).isEqualTo("target-123 [GH-90000]");
        }

        @Test
        @DisplayName("Custom strength in [0,1] is stored correctly [GH-90000]")
        void customStrength_storedCorrectly() { // GH-90000
            MemoryLink link = MemoryLink.builder() // GH-90000
                    .targetItemId("item-B [GH-90000]")
                    .linkType(LinkType.DERIVED_FROM) // GH-90000
                    .strength(0.75) // GH-90000
                    .build(); // GH-90000
            assertThat(link.getStrength()).isEqualTo(0.75); // GH-90000
        }

        @Test
        @DisplayName("Description is stored when provided [GH-90000]")
        void description_storedWhenProvided() { // GH-90000
            MemoryLink link = MemoryLink.builder() // GH-90000
                    .targetItemId("item-C [GH-90000]")
                    .linkType(LinkType.CONTRADICTS) // GH-90000
                    .description("Directly contradicts the previous fact [GH-90000]")
                    .build(); // GH-90000
            assertThat(link.getDescription()).isEqualTo("Directly contradicts the previous fact [GH-90000]");
        }
    }

    @Nested
    @DisplayName("LinkType enum values [GH-90000]")
    class LinkTypeEnumTests {

        @Test
        @DisplayName("All five LinkType values are present [GH-90000]")
        void allLinkTypeValues_present() { // GH-90000
            assertThat(LinkType.values()).containsExactlyInAnyOrder( // GH-90000
                    LinkType.SUPPORTS,
                    LinkType.CONTRADICTS,
                    LinkType.DERIVED_FROM,
                    LinkType.SUPERSEDES,
                    LinkType.RELATED);
        }

        @Test
        @DisplayName("SUPERSEDES link type can be set [GH-90000]")
        void supersedesLinkType_settable() { // GH-90000
            MemoryLink link = MemoryLink.builder() // GH-90000
                    .targetItemId("old-item [GH-90000]")
                    .linkType(LinkType.SUPERSEDES) // GH-90000
                    .build(); // GH-90000
            assertThat(link.getLinkType()).isEqualTo(LinkType.SUPERSEDES); // GH-90000
        }
    }
}
