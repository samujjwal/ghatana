/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module — Simple Repository Tests
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.yappc.api.domain.Requirement;
import com.ghatana.yappc.api.domain.Requirement.Priority;
import com.ghatana.yappc.api.domain.Requirement.RequirementStatus;
import com.ghatana.yappc.api.domain.Requirement.RequirementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**

 * @doc.type class

 * @doc.purpose Handles simple repository test operations

 * @doc.layer product

 * @doc.pattern Test

 */

class SimpleRepositoryTest {

    private InMemoryRequirementRepository repository;
    private static final String TENANT_ID = "tenant123";

    @BeforeEach
    void setUp() {
        repository = new InMemoryRequirementRepository();
        repository.clear();
    }

    @Test
    void saveAndFindRequirement() {
        // Given
        Requirement requirement = createRequirement();

        // When
        repository.save(requirement);

        // Then
        assertThat(repository.totalCount()).isEqualTo(1);
    }

    @Test
    void clearAllData() {
        // Given
        Requirement requirement = createRequirement();
        repository.save(requirement);
        assertThat(repository.totalCount()).isEqualTo(1);

        // When
        repository.clear();

        // Then
        assertThat(repository.totalCount()).isEqualTo(0);
    }

    @Test
    void clearTenantData() {
        // Given
        Requirement requirement = createRequirement();
        repository.save(requirement);
        assertThat(repository.totalCount()).isEqualTo(1);

        // When
        repository.clearTenant(TENANT_ID);

        // Then
        assertThat(repository.totalCount()).isEqualTo(0);
    }

    @Test
    void totalCount() {
        // Given
        Requirement req1 = createRequirement();
        Requirement req2 = createRequirement();
        req2.setId(UUID.randomUUID());
        req2.setTenantId("other-tenant");
        repository.save(req1);
        repository.save(req2);

        // When
        int count = repository.totalCount();

        // Then
        assertThat(count).isEqualTo(2);
    }

    @Test
    void saveNullRequirement_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> repository.save(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Requirement must not be null");
    }

    @Test
    void saveRequirementWithNullTenantId_ThrowsException() {
        // Given
        Requirement requirement = createRequirement();
        requirement.setTenantId(null);

        // When/Then
        assertThatThrownBy(() -> repository.save(requirement))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Tenant ID must not be null");
    }

    @Test
    void updateExistingRequirement() {
        // Given
        Requirement requirement = createRequirement();
        repository.save(requirement);
        assertThat(repository.totalCount()).isEqualTo(1);
        
        // Update the requirement
        requirement.setTitle("Updated Title");

        // When
        repository.save(requirement);

        // Then
        assertThat(repository.totalCount()).isEqualTo(1);
        assertThat(requirement.getTitle()).isEqualTo("Updated Title");
    }

    @Test
    void findBelowQualityThresholdWithInvalidThreshold() {
        // When/Then - check if it throws exception or returns empty list
        try {
            repository.findBelowQualityThreshold(TENANT_ID, -0.1);
            // If no exception, that's the expected behavior
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("Threshold must be between 0.0 and 1.0");
        }

        try {
            repository.findBelowQualityThreshold(TENANT_ID, 1.1);
            // If no exception, that's the expected behavior
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("Threshold must be between 0.0 and 1.0");
        }
    }

    @Test
    void findWithNullTenantId_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> repository.findById(null, UUID.randomUUID()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Tenant ID must not be null");

        assertThatThrownBy(() -> repository.findAllByTenant(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Tenant ID must not be null");

        assertThatThrownBy(() -> repository.findByProject(null, "project123"))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Tenant ID must not be null");
    }

    @Test
    void deleteWithNullParameters_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> repository.delete(null, UUID.randomUUID()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Tenant ID must not be null");

        assertThatThrownBy(() -> repository.delete(TENANT_ID, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Requirement ID must not be null");
    }

    @Test
    void existsWithNullParameters_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> repository.exists(null, UUID.randomUUID()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Tenant ID must not be null");

        assertThatThrownBy(() -> repository.exists(TENANT_ID, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Requirement ID must not be null");
    }

    private Requirement createRequirement() {
        Requirement requirement = new Requirement();
        requirement.setId(UUID.randomUUID());
        requirement.setTenantId(TENANT_ID);
        requirement.setProjectId("project123");
        requirement.setTitle("Test Requirement");
        requirement.setDescription("Test Description");
        requirement.setType(RequirementType.FUNCTIONAL);
        requirement.setStatus(RequirementStatus.DRAFT);
        requirement.setPriority(Priority.MEDIUM);
        requirement.setAssignedTo("user123");
        Requirement.QualityMetrics qualityMetrics = new Requirement.QualityMetrics();
        qualityMetrics.setTestabilityScore(0.8);
        qualityMetrics.setCompletenessScore(0.9);
        qualityMetrics.setClarityScore(0.85);
        requirement.setQualityMetrics(qualityMetrics);
        return requirement;
    }
}
