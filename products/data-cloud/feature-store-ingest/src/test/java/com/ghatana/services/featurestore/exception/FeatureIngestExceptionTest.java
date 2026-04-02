/*
 * Copyright (c) 2026 Ghatana Inc.
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
        void constructorSetsMessageAndCategory() {
            FeatureIngestException ex = new FeatureIngestException(
                    "Offset advance failed", FeatureIngestException.ErrorCategory.OFFSET_FAILURE);

            assertThat(ex.getMessage()).isEqualTo("Offset advance failed");
            assertThat(ex.getCategory()).isEqualTo(FeatureIngestException.ErrorCategory.OFFSET_FAILURE);
            assertThat(ex.getCause()).isNull();
        }

        @Test
        @DisplayName("cause constructor preserves cause")
        void causeConstructorPreservesCause() {
            RuntimeException root = new RuntimeException("connection refused");
            FeatureIngestException ex = new FeatureIngestException(
                    "Store unavailable", FeatureIngestException.ErrorCategory.STORE_UNAVAILABLE, root);

            assertThat(ex.getMessage()).isEqualTo("Store unavailable");
            assertThat(ex.getCause()).isSameAs(root);
            assertThat(ex.getCategory()).isEqualTo(FeatureIngestException.ErrorCategory.STORE_UNAVAILABLE);
        }

        @Test
        @DisplayName("ErrorCategory enum has all four expected values")
        void errorCategoryEnumValues() {
            assertThat(FeatureIngestException.ErrorCategory.values()).containsExactlyInAnyOrder(
                    FeatureIngestException.ErrorCategory.EXTRACTION_FAILURE,
                    FeatureIngestException.ErrorCategory.STORE_WRITE_FAILURE,
                    FeatureIngestException.ErrorCategory.STORE_UNAVAILABLE,
                    FeatureIngestException.ErrorCategory.OFFSET_FAILURE
            );
        }

        @Test
        @DisplayName("is a RuntimeException")
        void isRuntimeException() {
            FeatureIngestException ex = new FeatureIngestException(
                    "test", FeatureIngestException.ErrorCategory.STORE_UNAVAILABLE);

            assertThat(ex).isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("FeatureStoreWriteException")
    class FeatureStoreWriteExceptionTests {

        @Test
        @DisplayName("constructor sets all fields and category to STORE_WRITE_FAILURE")
        void constructorSetsAllFields() {
            RuntimeException cause = new RuntimeException("timeout");
            FeatureStoreWriteException ex = new FeatureStoreWriteException(
                    "user_click_count", "tenant-123", 3, "Write failed after 3 attempts", cause);

            assertThat(ex.getFeatureName()).isEqualTo("user_click_count");
            assertThat(ex.getTenantId()).isEqualTo("tenant-123");
            assertThat(ex.getAttemptCount()).isEqualTo(3);
            assertThat(ex.getMessage()).isEqualTo("Write failed after 3 attempts");
            assertThat(ex.getCause()).isSameAs(cause);
            assertThat(ex.getCategory()).isEqualTo(FeatureIngestException.ErrorCategory.STORE_WRITE_FAILURE);
        }

        @Test
        @DisplayName("extends FeatureIngestException")
        void extendsBaseException() {
            FeatureStoreWriteException ex = new FeatureStoreWriteException(
                    "feat", "tenant", 1, "msg", new RuntimeException());

            assertThat(ex).isInstanceOf(FeatureIngestException.class);
        }

        @Test
        @DisplayName("attempt count is zero when first attempt fails")
        void attemptCountZeroOnFirstFailure() {
            FeatureStoreWriteException ex = new FeatureStoreWriteException(
                    "feat", "t", 0, "immediate failure", null);

            assertThat(ex.getAttemptCount()).isZero();
        }
    }

    @Nested
    @DisplayName("FeatureExtractionException")
    class FeatureExtractionExceptionTests {

        @Test
        @DisplayName("constructor sets eventId, tenantId, and EXTRACTION_FAILURE category")
        void constructorSetsFields() {
            RuntimeException cause = new RuntimeException("parse error");
            FeatureExtractionException ex = new FeatureExtractionException(
                    "evt-9872", "tenant-456", "Failed to extract feature vector", cause);

            assertThat(ex.getEventId()).isEqualTo("evt-9872");
            assertThat(ex.getTenantId()).isEqualTo("tenant-456");
            assertThat(ex.getMessage()).isEqualTo("Failed to extract feature vector");
            assertThat(ex.getCause()).isSameAs(cause);
            assertThat(ex.getCategory()).isEqualTo(FeatureIngestException.ErrorCategory.EXTRACTION_FAILURE);
        }

        @Test
        @DisplayName("extends FeatureIngestException")
        void extendsBaseException() {
            FeatureExtractionException ex = new FeatureExtractionException(
                    "e", "t", "msg", null);

            assertThat(ex).isInstanceOf(FeatureIngestException.class);
        }

        @Test
        @DisplayName("cause can be null for unknown extraction failures")
        void causeCanBeNull() {
            FeatureExtractionException ex = new FeatureExtractionException(
                    "evt-x", "tenant-y", "no cause available", null);

            assertThat(ex.getCause()).isNull();
        }
    }
}
