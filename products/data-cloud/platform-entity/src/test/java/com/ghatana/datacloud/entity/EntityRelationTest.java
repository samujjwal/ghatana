/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link EntityRelation} record.
 */
class EntityRelationTest {

    private static final UUID TEST_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void shouldCreateValidEntityRelation() { // GH-90000
        EntityRelation relation = new EntityRelation( // GH-90000
            TEST_UUID,
            "products",
            RelationType.SIMILAR,
            0.87
        );

        assertThat(relation.relatedEntityId()).isEqualTo(TEST_UUID); // GH-90000
        assertThat(relation.collectionName()).isEqualTo("products");
        assertThat(relation.relationType()).isEqualTo(RelationType.SIMILAR); // GH-90000
        assertThat(relation.similarity()).isEqualTo(0.87); // GH-90000
    }

    @Test
    void shouldRejectNullRelatedEntityId() { // GH-90000
        assertThatThrownBy(() -> new EntityRelation( // GH-90000
            null,
            "products",
            RelationType.SIMILAR,
            0.5
        )).isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("Related entity ID must not be null");
    }

    @Test
    void shouldRejectNullCollectionName() { // GH-90000
        assertThatThrownBy(() -> new EntityRelation( // GH-90000
            TEST_UUID,
            null,
            RelationType.SIMILAR,
            0.5
        )).isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("Collection name must not be blank");
    }

    @Test
    void shouldRejectBlankCollectionName() { // GH-90000
        assertThatThrownBy(() -> new EntityRelation( // GH-90000
            TEST_UUID,
            "   ",
            RelationType.SIMILAR,
            0.5
        )).isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("Collection name must not be blank");
    }

    @Test
    void shouldRejectNullRelationType() { // GH-90000
        assertThatThrownBy(() -> new EntityRelation( // GH-90000
            TEST_UUID,
            "products",
            null,
            0.5
        )).isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("Relation type must not be null");
    }

    @Test
    void shouldRejectNegativeSimilarity() { // GH-90000
        assertThatThrownBy(() -> new EntityRelation( // GH-90000
            TEST_UUID,
            "products",
            RelationType.SIMILAR,
            -0.1
        )).isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("Similarity must be between 0.0 and 1.0");
    }

    @Test
    void shouldRejectSimilarityGreaterThanOne() { // GH-90000
        assertThatThrownBy(() -> new EntityRelation( // GH-90000
            TEST_UUID,
            "products",
            RelationType.SIMILAR,
            1.1
        )).isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("Similarity must be between 0.0 and 1.0");
    }

    @Test
    void shouldAcceptBoundarySimilarityValues() { // GH-90000
        // Minimum boundary
        EntityRelation minRelation = new EntityRelation( // GH-90000
            TEST_UUID, "products", RelationType.SIMILAR, 0.0
        );
        assertThat(minRelation.similarity()).isZero(); // GH-90000

        // Maximum boundary
        EntityRelation maxRelation = new EntityRelation( // GH-90000
            TEST_UUID, "products", RelationType.SIMILAR, 1.0
        );
        assertThat(maxRelation.similarity()).isEqualTo(1.0); // GH-90000
    }

    @Test
    void isHighSimilarityShouldReturnTrueForSimilarityAboveThreshold() { // GH-90000
        EntityRelation highRelation = new EntityRelation( // GH-90000
            TEST_UUID, "products", RelationType.SIMILAR, 0.85
        );
        assertThat(highRelation.isHighSimilarity()).isTrue(); // GH-90000

        EntityRelation boundaryRelation = new EntityRelation( // GH-90000
            TEST_UUID, "products", RelationType.SIMILAR, 0.8
        );
        assertThat(boundaryRelation.isHighSimilarity()).isTrue(); // GH-90000
    }

    @Test
    void isHighSimilarityShouldReturnFalseForSimilarityBelowThreshold() { // GH-90000
        EntityRelation lowRelation = new EntityRelation( // GH-90000
            TEST_UUID, "products", RelationType.SIMILAR, 0.79
        );
        assertThat(lowRelation.isHighSimilarity()).isFalse(); // GH-90000
    }

    @Test
    void isSimilarShouldReturnTrueForSimilarType() { // GH-90000
        EntityRelation similar = new EntityRelation( // GH-90000
            TEST_UUID, "products", RelationType.SIMILAR, 0.5
        );
        assertThat(similar.isSimilar()).isTrue(); // GH-90000
    }

    @Test
    void isSimilarShouldReturnFalseForNonSimilarTypes() { // GH-90000
        EntityRelation referenced = new EntityRelation( // GH-90000
            TEST_UUID, "products", RelationType.REFERENCED, 0.5
        );
        assertThat(referenced.isSimilar()).isFalse(); // GH-90000

        EntityRelation related = new EntityRelation( // GH-90000
            TEST_UUID, "products", RelationType.RELATED, 0.5
        );
        assertThat(related.isSimilar()).isFalse(); // GH-90000
    }

    @Test
    void isReferencedShouldReturnTrueForReferencedType() { // GH-90000
        EntityRelation referenced = new EntityRelation( // GH-90000
            TEST_UUID, "products", RelationType.REFERENCED, 0.5
        );
        assertThat(referenced.isReferenced()).isTrue(); // GH-90000
    }

    @Test
    void isReferencedShouldReturnFalseForNonReferencedTypes() { // GH-90000
        EntityRelation similar = new EntityRelation( // GH-90000
            TEST_UUID, "products", RelationType.SIMILAR, 0.5
        );
        assertThat(similar.isReferenced()).isFalse(); // GH-90000
    }

    @Test
    void shouldImplementEqualsAndHashCode() { // GH-90000
        EntityRelation relation1 = new EntityRelation( // GH-90000
            TEST_UUID, "products", RelationType.SIMILAR, 0.87
        );
        EntityRelation relation2 = new EntityRelation( // GH-90000
            TEST_UUID, "products", RelationType.SIMILAR, 0.87
        );

        assertThat(relation1).isEqualTo(relation2); // GH-90000
        assertThat(relation1.hashCode()).isEqualTo(relation2.hashCode()); // GH-90000
    }

    @Test
    void shouldHaveMeaningfulToString() { // GH-90000
        EntityRelation relation = new EntityRelation( // GH-90000
            TEST_UUID, "products", RelationType.SIMILAR, 0.87
        );
        assertThat(relation.toString()).contains("EntityRelation");
    }
}
