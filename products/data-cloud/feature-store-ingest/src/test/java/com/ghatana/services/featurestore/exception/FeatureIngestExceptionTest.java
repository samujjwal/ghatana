/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.services.featurestore.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the feature ingest exception hierarchy.
 *
 * @doc.type class
 * @doc.purpose Tests for FeatureIngestException, FeatureStoreWriteException, FeatureExtractionException
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Feature Ingest Exception Hierarchy")
class FeatureIngestExceptionTest {

    @Nested
    @DisplayName("FeatureIngestException")
    class FeatureIngestExceptionTests {

        @Test
        @DisplayName("constructor sets message and category")
        void constructorSetsMessageAndCategory() { // GH-90000
            FeatureIngestException ex = new FeatureIngestException( // GH-90000
                    "Offset advance failed", FeatureIngestException.ErrorCategory.OFFSET_FAILURE);

            assertThat(ex.getMessage()).isEqualTo("Offset advance failed");
            assertThat(ex.getCategory()).isEqualTo(FeatureIngestException.ErrorCategory.OFFSET_FAILURE); // GH-90000
            assertThat(ex.getCause()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("cause constructor preserves cause")
        void causeConstructorPreservesCause() { // GH-90000
            RuntimeException root = new RuntimeException("connection refused");
            FeatureIngestException ex = new FeatureIngestException( // GH-90000
                    "Store unavailable", FeatureIngestException.ErrorCategory.STORE_UNAVAILABLE, root);

            assertThat(ex.getMessage()).isEqualTo("Store unavailable");
            assertThat(ex.getCause()).isSameAs(root); // GH-90000
            assertThat(ex.getCategory()).isEqualTo(FeatureIngestException.ErrorCategory.STORE_UNAVAILABLE); // GH-90000
        }

        @Test
        @DisplayName("ErrorCategory enum has all four expected values")
        void errorCategoryEnumValues() { // GH-90000
            assertThat(FeatureIngestException.ErrorCategory.values()).containsExactlyInAnyOrder( // GH-90000
                    FeatureIngestException.ErrorCategory.EXTRACTION_FAILURE,
                    FeatureIngestException.ErrorCategory.STORE_WRITE_FAILURE,
                    FeatureIngestException.ErrorCategory.STORE_UNAVAILABLE,
                    FeatureIngestException.ErrorCategory.OFFSET_FAILURE
            );
        }

        @Test
        @DisplayName("is a RuntimeException")
        void isRuntimeException() { // GH-90000
            FeatureIngestException ex = new FeatureIngestException( // GH-90000
                    "test", FeatureIngestException.ErrorCategory.STORE_UNAVAILABLE);

            assertThat(ex).isInstanceOf(RuntimeException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("FeatureStoreWriteException")
    class FeatureStoreWriteExceptionTests {

        @Test
        @DisplayName("constructor sets all fields and category to STORE_WRITE_FAILURE")
        void constructorSetsAllFields() { // GH-90000
            RuntimeException cause = new RuntimeException("timeout");
            FeatureStoreWriteException ex = new FeatureStoreWriteException( // GH-90000
                    "user_click_count", "tenant-123", 3, "Write failed after 3 attempts", cause);

            assertThat(ex.getFeatureName()).isEqualTo("user_click_count");
            assertThat(ex.getTenantId()).isEqualTo("tenant-123");
            assertThat(ex.getAttemptCount()).isEqualTo(3); // GH-90000
            assertThat(ex.getMessage()).isEqualTo("Write failed after 3 attempts");
            assertThat(ex.getCause()).isSameAs(cause); // GH-90000
            assertThat(ex.getCategory()).isEqualTo(FeatureIngestException.ErrorCategory.STORE_WRITE_FAILURE); // GH-90000
        }

        @Test
        @DisplayName("extends FeatureIngestException")
        void extendsBaseException() { // GH-90000
            FeatureStoreWriteException ex = new FeatureStoreWriteException( // GH-90000
                    "feat", "tenant", 1, "msg", new RuntimeException()); // GH-90000

            assertThat(ex).isInstanceOf(FeatureIngestException.class); // GH-90000
        }

        @Test
        @DisplayName("attempt count is zero when first attempt fails")
        void attemptCountZeroOnFirstFailure() { // GH-90000
            FeatureStoreWriteException ex = new FeatureStoreWriteException( // GH-90000
                    "feat", "t", 0, "immediate failure", null);

            assertThat(ex.getAttemptCount()).isZero(); // GH-90000
        }
    }

    @Nested
    @DisplayName("FeatureExtractionException")
    class FeatureExtractionExceptionTests {

        @Test
        @DisplayName("constructor sets eventId, tenantId, and EXTRACTION_FAILURE category")
        void constructorSetsFields() { // GH-90000
            RuntimeException cause = new RuntimeException("parse error");
            FeatureExtractionException ex = new FeatureExtractionException( // GH-90000
                    "evt-9872", "tenant-456", "Failed to extract feature vector", cause);

            assertThat(ex.getEventId()).isEqualTo("evt-9872");
            assertThat(ex.getTenantId()).isEqualTo("tenant-456");
            assertThat(ex.getMessage()).isEqualTo("Failed to extract feature vector");
            assertThat(ex.getCause()).isSameAs(cause); // GH-90000
            assertThat(ex.getCategory()).isEqualTo(FeatureIngestException.ErrorCategory.EXTRACTION_FAILURE); // GH-90000
        }

        @Test
        @DisplayName("extends FeatureIngestException")
        void extendsBaseException() { // GH-90000
            FeatureExtractionException ex = new FeatureExtractionException( // GH-90000
                    "e", "t", "msg", null);

            assertThat(ex).isInstanceOf(FeatureIngestException.class); // GH-90000
        }

        @Test
        @DisplayName("cause can be null for unknown extraction failures")
        void causeCanBeNull() { // GH-90000
            FeatureExtractionException ex = new FeatureExtractionException( // GH-90000
                    "evt-x", "tenant-y", "no cause available", null);

            assertThat(ex.getCause()).isNull(); // GH-90000
        }
    }
}
