/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RelationType} enum.
 */
class RelationTypeTest {

    @Test
    void allEnumValuesShouldBeAccessible() { 
        // This test ensures all enum values are referenced for coverage
        RelationType[] types = RelationType.values(); 
        assertThat(types).hasSize(4); 
        assertThat(types).containsExactly( 
            RelationType.SIMILAR,
            RelationType.REFERENCED,
            RelationType.RELATED,
            RelationType.HIERARCHICAL
        );
    }

    @ParameterizedTest
    @EnumSource(RelationType.class) 
    void eachEnumValueShouldHaveNonNullName(RelationType type) { 
        assertThat(type.name()).isNotNull().isNotEmpty(); 
    }

    @Test
    void similarShouldBeContentBased() { 
        assertThat(RelationType.SIMILAR.isContentBased()).isTrue(); 
        assertThat(RelationType.SIMILAR.isExplicit()).isFalse(); 
        assertThat(RelationType.SIMILAR.isUsageBased()).isFalse(); 
    }

    @Test
    void referencedShouldBeExplicit() { 
        assertThat(RelationType.REFERENCED.isContentBased()).isFalse(); 
        assertThat(RelationType.REFERENCED.isExplicit()).isTrue(); 
        assertThat(RelationType.REFERENCED.isUsageBased()).isFalse(); 
    }

    @Test
    void relatedShouldBeUsageBased() { 
        assertThat(RelationType.RELATED.isContentBased()).isFalse(); 
        assertThat(RelationType.RELATED.isExplicit()).isFalse(); 
        assertThat(RelationType.RELATED.isUsageBased()).isTrue(); 
    }

    @Test
    void hierarchicalShouldBeExplicit() { 
        assertThat(RelationType.HIERARCHICAL.isContentBased()).isFalse(); 
        assertThat(RelationType.HIERARCHICAL.isExplicit()).isTrue(); 
        assertThat(RelationType.HIERARCHICAL.isUsageBased()).isFalse(); 
    }

    @Test
    void valueOfShouldReturnCorrectEnum() { 
        assertThat(RelationType.valueOf("SIMILAR")).isEqualTo(RelationType.SIMILAR);
        assertThat(RelationType.valueOf("REFERENCED")).isEqualTo(RelationType.REFERENCED);
        assertThat(RelationType.valueOf("RELATED")).isEqualTo(RelationType.RELATED);
        assertThat(RelationType.valueOf("HIERARCHICAL")).isEqualTo(RelationType.HIERARCHICAL);
    }
}
