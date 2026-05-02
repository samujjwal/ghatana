/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("MemoryLink - directed relationship between memory items")
class MemoryLinkTest {

    @Nested
    @DisplayName("Builder defaults")
    class BuilderDefaultsTests {

        @Test
        @DisplayName("Default strength is 1.0 (full relationship strength)")
        void defaultStrength_isOne() { 
            MemoryLink link = MemoryLink.builder() 
                    .targetItemId("item-A")
                    .linkType(LinkType.RELATED) 
                    .build(); 
            assertThat(link.getStrength()).isEqualTo(1.0); 
        }

        @Test
        @DisplayName("Default description is null")
        void defaultDescription_isNull() { 
            MemoryLink link = MemoryLink.builder() 
                    .targetItemId("item-A")
                    .linkType(LinkType.RELATED) 
                    .build(); 
            assertThat(link.getDescription()).isNull(); 
        }
    }

    @Nested
    @DisplayName("Custom builder values")
    class CustomBuilderTests {

        @Test
        @DisplayName("TargetItemId is stored correctly")
        void targetItemId_storedCorrectly() { 
            MemoryLink link = MemoryLink.builder() 
                    .targetItemId("target-123")
                    .linkType(LinkType.SUPPORTS) 
                    .build(); 
            assertThat(link.getTargetItemId()).isEqualTo("target-123");
        }

        @Test
        @DisplayName("Custom strength in [0,1] is stored correctly")
        void customStrength_storedCorrectly() { 
            MemoryLink link = MemoryLink.builder() 
                    .targetItemId("item-B")
                    .linkType(LinkType.DERIVED_FROM) 
                    .strength(0.75) 
                    .build(); 
            assertThat(link.getStrength()).isEqualTo(0.75); 
        }

        @Test
        @DisplayName("Description is stored when provided")
        void description_storedWhenProvided() { 
            MemoryLink link = MemoryLink.builder() 
                    .targetItemId("item-C")
                    .linkType(LinkType.CONTRADICTS) 
                    .description("Directly contradicts the previous fact")
                    .build(); 
            assertThat(link.getDescription()).isEqualTo("Directly contradicts the previous fact");
        }
    }

    @Nested
    @DisplayName("LinkType enum values")
    class LinkTypeEnumTests {

        @Test
        @DisplayName("All five LinkType values are present")
        void allLinkTypeValues_present() { 
            assertThat(LinkType.values()).containsExactlyInAnyOrder( 
                    LinkType.SUPPORTS,
                    LinkType.CONTRADICTS,
                    LinkType.DERIVED_FROM,
                    LinkType.SUPERSEDES,
                    LinkType.RELATED);
        }

        @Test
        @DisplayName("SUPERSEDES link type can be set")
        void supersedesLinkType_settable() { 
            MemoryLink link = MemoryLink.builder() 
                    .targetItemId("old-item")
                    .linkType(LinkType.SUPERSEDES) 
                    .build(); 
            assertThat(link.getLinkType()).isEqualTo(LinkType.SUPERSEDES); 
        }
    }
}
