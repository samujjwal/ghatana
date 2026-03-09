package com.ghatana.virtualorg.framework.hierarchy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Layer enum.
 *
 * Tests validate:
 * - Layer properties (display name, level)
 * - Leadership determination
 * - Authority comparison
 *
 * @doc.type test
 * @doc.purpose Unit tests for Layer enum
 * @doc.layer product
 */
@DisplayName("Layer Enum Tests")
class LayerTest {
    
    @Test
    @DisplayName("EXECUTIVE layer has level 3")
    void testExecutiveLevel() {
        assertThat(Layer.EXECUTIVE.getLevel()).isEqualTo(3);
    }
    
    @Test
    @DisplayName("MANAGEMENT layer has level 2")
    void testManagementLevel() {
        assertThat(Layer.MANAGEMENT.getLevel()).isEqualTo(2);
    }
    
    @Test
    @DisplayName("INDIVIDUAL_CONTRIBUTOR layer has level 1")
    void testIndividualContributorLevel() {
        assertThat(Layer.INDIVIDUAL_CONTRIBUTOR.getLevel()).isEqualTo(1);
    }
    
    @Test
    @DisplayName("EXECUTIVE is leadership")
    void testExecutiveIsLeadership() {
        assertThat(Layer.EXECUTIVE.isLeadership()).isTrue();
    }
    
    @Test
    @DisplayName("MANAGEMENT is leadership")
    void testManagementIsLeadership() {
        assertThat(Layer.MANAGEMENT.isLeadership()).isTrue();
    }
    
    @Test
    @DisplayName("INDIVIDUAL_CONTRIBUTOR is not leadership")
    void testIndividualContributorIsNotLeadership() {
        assertThat(Layer.INDIVIDUAL_CONTRIBUTOR.isLeadership()).isFalse();
    }
    
    @Test
    @DisplayName("EXECUTIVE has higher authority than MANAGEMENT")
    void testExecutiveHigherThanManagement() {
        assertThat(Layer.EXECUTIVE.hasHigherAuthority(Layer.MANAGEMENT)).isTrue();
    }
    
    @Test
    @DisplayName("EXECUTIVE has higher authority than INDIVIDUAL_CONTRIBUTOR")
    void testExecutiveHigherThanIC() {
        assertThat(Layer.EXECUTIVE.hasHigherAuthority(Layer.INDIVIDUAL_CONTRIBUTOR)).isTrue();
    }
    
    @Test
    @DisplayName("MANAGEMENT has higher authority than INDIVIDUAL_CONTRIBUTOR")
    void testManagementHigherThanIC() {
        assertThat(Layer.MANAGEMENT.hasHigherAuthority(Layer.INDIVIDUAL_CONTRIBUTOR)).isTrue();
    }
    
    @Test
    @DisplayName("MANAGEMENT does not have higher authority than EXECUTIVE")
    void testManagementNotHigherThanExecutive() {
        assertThat(Layer.MANAGEMENT.hasHigherAuthority(Layer.EXECUTIVE)).isFalse();
    }
    
    @Test
    @DisplayName("Layer does not have higher authority than itself")
    void testLayerNotHigherThanItself() {
        assertThat(Layer.EXECUTIVE.hasHigherAuthority(Layer.EXECUTIVE)).isFalse();
        assertThat(Layer.MANAGEMENT.hasHigherAuthority(Layer.MANAGEMENT)).isFalse();
        assertThat(Layer.INDIVIDUAL_CONTRIBUTOR.hasHigherAuthority(Layer.INDIVIDUAL_CONTRIBUTOR)).isFalse();
    }
    
    @Test
    @DisplayName("All layers have display names")
    void testDisplayNames() {
        assertThat(Layer.EXECUTIVE.getDisplayName()).isNotBlank();
        assertThat(Layer.MANAGEMENT.getDisplayName()).isNotBlank();
        assertThat(Layer.INDIVIDUAL_CONTRIBUTOR.getDisplayName()).isNotBlank();
    }
}
