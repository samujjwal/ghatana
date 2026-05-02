/*
 * Copyright (c) 2026 Ghatana Inc. 
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
    void shouldCreateValidEntityRelation() { 
        EntityRelation relation = new EntityRelation( 
            TEST_UUID,
            "products",
            RelationType.SIMILAR,
            0.87
        );

        assertThat(relation.relatedEntityId()).isEqualTo(TEST_UUID); 
        assertThat(relation.collectionName()).isEqualTo("products");
        assertThat(relation.relationType()).isEqualTo(RelationType.SIMILAR); 
        assertThat(relation.similarity()).isEqualTo(0.87); 
    }

    @Test
    void shouldRejectNullRelatedEntityId() { 
        assertThatThrownBy(() -> new EntityRelation( 
            null,
            "products",
            RelationType.SIMILAR,
            0.5
        )).isInstanceOf(IllegalArgumentException.class) 
            .hasMessageContaining("Related entity ID must not be null");
    }

    @Test
    void shouldRejectNullCollectionName() { 
        assertThatThrownBy(() -> new EntityRelation( 
            TEST_UUID,
            null,
            RelationType.SIMILAR,
            0.5
        )).isInstanceOf(IllegalArgumentException.class) 
            .hasMessageContaining("Collection name must not be blank");
    }

    @Test
    void shouldRejectBlankCollectionName() { 
        assertThatThrownBy(() -> new EntityRelation( 
            TEST_UUID,
            "   ",
            RelationType.SIMILAR,
            0.5
        )).isInstanceOf(IllegalArgumentException.class) 
            .hasMessageContaining("Collection name must not be blank");
    }

    @Test
    void shouldRejectNullRelationType() { 
        assertThatThrownBy(() -> new EntityRelation( 
            TEST_UUID,
            "products",
            null,
            0.5
        )).isInstanceOf(IllegalArgumentException.class) 
            .hasMessageContaining("Relation type must not be null");
    }

    @Test
    void shouldRejectNegativeSimilarity() { 
        assertThatThrownBy(() -> new EntityRelation( 
            TEST_UUID,
            "products",
            RelationType.SIMILAR,
            -0.1
        )).isInstanceOf(IllegalArgumentException.class) 
            .hasMessageContaining("Similarity must be between 0.0 and 1.0");
    }

    @Test
    void shouldRejectSimilarityGreaterThanOne() { 
        assertThatThrownBy(() -> new EntityRelation( 
            TEST_UUID,
            "products",
            RelationType.SIMILAR,
            1.1
        )).isInstanceOf(IllegalArgumentException.class) 
            .hasMessageContaining("Similarity must be between 0.0 and 1.0");
    }

    @Test
    void shouldAcceptBoundarySimilarityValues() { 
        // Minimum boundary
        EntityRelation minRelation = new EntityRelation( 
            TEST_UUID, "products", RelationType.SIMILAR, 0.0
        );
        assertThat(minRelation.similarity()).isZero(); 

        // Maximum boundary
        EntityRelation maxRelation = new EntityRelation( 
            TEST_UUID, "products", RelationType.SIMILAR, 1.0
        );
        assertThat(maxRelation.similarity()).isEqualTo(1.0); 
    }

    @Test
    void isHighSimilarityShouldReturnTrueForSimilarityAboveThreshold() { 
        EntityRelation highRelation = new EntityRelation( 
            TEST_UUID, "products", RelationType.SIMILAR, 0.85
        );
        assertThat(highRelation.isHighSimilarity()).isTrue(); 

        EntityRelation boundaryRelation = new EntityRelation( 
            TEST_UUID, "products", RelationType.SIMILAR, 0.8
        );
        assertThat(boundaryRelation.isHighSimilarity()).isTrue(); 
    }

    @Test
    void isHighSimilarityShouldReturnFalseForSimilarityBelowThreshold() { 
        EntityRelation lowRelation = new EntityRelation( 
            TEST_UUID, "products", RelationType.SIMILAR, 0.79
        );
        assertThat(lowRelation.isHighSimilarity()).isFalse(); 
    }

    @Test
    void isSimilarShouldReturnTrueForSimilarType() { 
        EntityRelation similar = new EntityRelation( 
            TEST_UUID, "products", RelationType.SIMILAR, 0.5
        );
        assertThat(similar.isSimilar()).isTrue(); 
    }

    @Test
    void isSimilarShouldReturnFalseForNonSimilarTypes() { 
        EntityRelation referenced = new EntityRelation( 
            TEST_UUID, "products", RelationType.REFERENCED, 0.5
        );
        assertThat(referenced.isSimilar()).isFalse(); 

        EntityRelation related = new EntityRelation( 
            TEST_UUID, "products", RelationType.RELATED, 0.5
        );
        assertThat(related.isSimilar()).isFalse(); 
    }

    @Test
    void isReferencedShouldReturnTrueForReferencedType() { 
        EntityRelation referenced = new EntityRelation( 
            TEST_UUID, "products", RelationType.REFERENCED, 0.5
        );
        assertThat(referenced.isReferenced()).isTrue(); 
    }

    @Test
    void isReferencedShouldReturnFalseForNonReferencedTypes() { 
        EntityRelation similar = new EntityRelation( 
            TEST_UUID, "products", RelationType.SIMILAR, 0.5
        );
        assertThat(similar.isReferenced()).isFalse(); 
    }

    @Test
    void shouldImplementEqualsAndHashCode() { 
        EntityRelation relation1 = new EntityRelation( 
            TEST_UUID, "products", RelationType.SIMILAR, 0.87
        );
        EntityRelation relation2 = new EntityRelation( 
            TEST_UUID, "products", RelationType.SIMILAR, 0.87
        );

        assertThat(relation1).isEqualTo(relation2); 
        assertThat(relation1.hashCode()).isEqualTo(relation2.hashCode()); 
    }

    @Test
    void shouldHaveMeaningfulToString() { 
        EntityRelation relation = new EntityRelation( 
            TEST_UUID, "products", RelationType.SIMILAR, 0.87
        );
        assertThat(relation.toString()).contains("EntityRelation");
    }
}
