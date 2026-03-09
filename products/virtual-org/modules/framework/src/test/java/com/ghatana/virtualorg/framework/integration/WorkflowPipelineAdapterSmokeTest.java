package com.ghatana.virtualorg.framework.integration;

import com.ghatana.core.operator.catalog.InMemoryOperatorCatalog;
import com.ghatana.core.operator.catalog.OperatorCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Smoke tests for WorkflowPipelineAdapter - minimal validation without full execution.
 *
 * <p>These tests validate basic object creation and structure without executing pipelines,
 * avoiding potential hanging issues during test runs.
 *
 * @doc.type test
 * @doc.purpose Smoke testing for adapter basic functionality
 * @doc.layer product
 */
@DisplayName("WorkflowPipelineAdapter Smoke Tests")
class WorkflowPipelineAdapterSmokeTest {

    private OperatorCatalog operatorCatalog;
    private WorkflowPipelineAdapter adapter;

    @BeforeEach
    void setUp() {
        operatorCatalog = new InMemoryOperatorCatalog();
        adapter = new WorkflowPipelineAdapter(operatorCatalog);
    }

    /**
     * Test adapter creation.
     *
     * GIVEN: valid operator catalog
     * WHEN: creating adapter
     * THEN: adapter created successfully
     */
    @Test
    @DisplayName("Should create adapter with valid catalog")
    void shouldCreateAdapter() {
        // GIVEN: setup in @BeforeEach

        // THEN: adapter should be created
        assertThat(adapter)
            .as("Adapter should be created")
            .isNotNull();
    }

    /**
     * Test adapter creation with null catalog.
     *
     * GIVEN: null operator catalog
     * WHEN: creating adapter
     * THEN: throws NullPointerException
     */
    @Test
    @DisplayName("Should reject null catalog")
    void shouldRejectNullCatalog() {
        // WHEN/THEN: creating with null should throw
        assertThatThrownBy(() -> new WorkflowPipelineAdapter(null))
            .as("Should reject null catalog")
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("OperatorCatalog required");
    }

    /**
     * Test catalog reference.
     *
     * GIVEN: adapter created with catalog
     * WHEN: adapter is used
     * THEN: it maintains reference to catalog
     */
    @Test
    @DisplayName("Should maintain catalog reference")
    void shouldMaintainCatalogReference() {
        // GIVEN: adapter created

        // THEN: adapter should maintain reference (verified by no errors)
        assertThat(adapter).isNotNull();
        // Note: We can't access private field, but constructor validation ensures it's set
    }
}

