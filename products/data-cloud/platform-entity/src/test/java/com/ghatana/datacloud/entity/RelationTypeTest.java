/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void allEnumValuesShouldBeAccessible() { // GH-90000
        // This test ensures all enum values are referenced for coverage
        RelationType[] types = RelationType.values(); // GH-90000
        assertThat(types).hasSize(4); // GH-90000
        assertThat(types).containsExactly( // GH-90000
            RelationType.SIMILAR,
            RelationType.REFERENCED,
            RelationType.RELATED,
            RelationType.HIERARCHICAL
        );
    }

    @ParameterizedTest
    @EnumSource(RelationType.class) // GH-90000
    void eachEnumValueShouldHaveNonNullName(RelationType type) { // GH-90000
        assertThat(type.name()).isNotNull().isNotEmpty(); // GH-90000
    }

    @Test
    void similarShouldBeContentBased() { // GH-90000
        assertThat(RelationType.SIMILAR.isContentBased()).isTrue(); // GH-90000
        assertThat(RelationType.SIMILAR.isExplicit()).isFalse(); // GH-90000
        assertThat(RelationType.SIMILAR.isUsageBased()).isFalse(); // GH-90000
    }

    @Test
    void referencedShouldBeExplicit() { // GH-90000
        assertThat(RelationType.REFERENCED.isContentBased()).isFalse(); // GH-90000
        assertThat(RelationType.REFERENCED.isExplicit()).isTrue(); // GH-90000
        assertThat(RelationType.REFERENCED.isUsageBased()).isFalse(); // GH-90000
    }

    @Test
    void relatedShouldBeUsageBased() { // GH-90000
        assertThat(RelationType.RELATED.isContentBased()).isFalse(); // GH-90000
        assertThat(RelationType.RELATED.isExplicit()).isFalse(); // GH-90000
        assertThat(RelationType.RELATED.isUsageBased()).isTrue(); // GH-90000
    }

    @Test
    void hierarchicalShouldBeExplicit() { // GH-90000
        assertThat(RelationType.HIERARCHICAL.isContentBased()).isFalse(); // GH-90000
        assertThat(RelationType.HIERARCHICAL.isExplicit()).isTrue(); // GH-90000
        assertThat(RelationType.HIERARCHICAL.isUsageBased()).isFalse(); // GH-90000
    }

    @Test
    void valueOfShouldReturnCorrectEnum() { // GH-90000
        assertThat(RelationType.valueOf("SIMILAR")).isEqualTo(RelationType.SIMILAR);
        assertThat(RelationType.valueOf("REFERENCED")).isEqualTo(RelationType.REFERENCED);
        assertThat(RelationType.valueOf("RELATED")).isEqualTo(RelationType.RELATED);
        assertThat(RelationType.valueOf("HIERARCHICAL")).isEqualTo(RelationType.HIERARCHICAL);
    }
}
