package com.ghatana.virtualorg.framework.hierarchy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Role record.
 *
 * Tests validate:
 * - Role creation and validation
 * - Role properties
 * - Authority comparison
 * - Display names
 *
 * @doc.type test
 * @doc.purpose Unit tests for Role record
 * @doc.layer product
 */
@DisplayName("Role Record Tests")
class RoleTest {
    
    @Test
    @DisplayName("Create role with valid name and layer")
    void testCreateValidRole() {
        Role role = Role.of("Engineer", Layer.INDIVIDUAL_CONTRIBUTOR);
        
        assertThat(role.name()).isEqualTo("Engineer");
        assertThat(role.layer()).isEqualTo(Layer.INDIVIDUAL_CONTRIBUTOR);
    }
    
    @Test
    @DisplayName("Reject role with null name")
    void testRejectNullName() {
        assertThatThrownBy(() -> Role.of(null, Layer.INDIVIDUAL_CONTRIBUTOR))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name cannot be null or empty");
    }
    
    @Test
    @DisplayName("Reject role with empty name")
    void testRejectEmptyName() {
        assertThatThrownBy(() -> Role.of("", Layer.INDIVIDUAL_CONTRIBUTOR))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name cannot be null or empty");
    }
    
    @Test
    @DisplayName("Reject role with blank name")
    void testRejectBlankName() {
        assertThatThrownBy(() -> Role.of("   ", Layer.INDIVIDUAL_CONTRIBUTOR))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name cannot be null or empty");
    }
    
    @Test
    @DisplayName("Reject role with null layer")
    void testRejectNullLayer() {
        assertThatThrownBy(() -> Role.of("Engineer", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Layer cannot be null");
    }
    
    @Test
    @DisplayName("Role is leadership if layer is leadership")
    void testIsLeadership() {
        Role cto = Role.of("CTO", Layer.EXECUTIVE);
        Role pm = Role.of("Product Manager", Layer.MANAGEMENT);
        Role engineer = Role.of("Engineer", Layer.INDIVIDUAL_CONTRIBUTOR);
        
        assertThat(cto.isLeadership()).isTrue();
        assertThat(pm.isLeadership()).isTrue();
        assertThat(engineer.isLeadership()).isFalse();
    }
    
    @Test
    @DisplayName("Compare authority between roles")
    void testHigherAuthority() {
        Role cto = Role.of("CTO", Layer.EXECUTIVE);
        Role architect = Role.of("Architect Lead", Layer.MANAGEMENT);
        Role engineer = Role.of("Engineer", Layer.INDIVIDUAL_CONTRIBUTOR);
        
        assertThat(cto.hasHigherAuthority(architect)).isTrue();
        assertThat(cto.hasHigherAuthority(engineer)).isTrue();
        assertThat(architect.hasHigherAuthority(engineer)).isTrue();
        assertThat(engineer.hasHigherAuthority(architect)).isFalse();
    }
    
    @Test
    @DisplayName("Get display name")
    void testDisplayName() {
        Role engineer = Role.of("Engineer", Layer.INDIVIDUAL_CONTRIBUTOR);
        
        assertThat(engineer.getDisplayName())
            .contains("Engineer")
            .contains("Individual Contributor");
    }
    
    @Test
    @DisplayName("Roles are immutable")
    void testImmutability() {
        Role role1 = Role.of("Engineer", Layer.INDIVIDUAL_CONTRIBUTOR);
        Role role2 = Role.of("Engineer", Layer.INDIVIDUAL_CONTRIBUTOR);
        
        assertThat(role1).isEqualTo(role2);
    }
}
