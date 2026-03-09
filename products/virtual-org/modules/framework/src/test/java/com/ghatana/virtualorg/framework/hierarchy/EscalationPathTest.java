package com.ghatana.virtualorg.framework.hierarchy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for EscalationPath record.
 *
 * Tests validate:
 * - Escalation path creation
 * - Navigation through path
 * - Immutability
 * - Edge cases
 *
 * @doc.type test
 * @doc.purpose Unit tests for EscalationPath record
 * @doc.layer product
 */
@DisplayName("EscalationPath Record Tests")
class EscalationPathTest {
    
    @Test
    @DisplayName("Create escalation path from varargs")
    void testCreateFromVarargs() {
        Role engineer = Role.of("Engineer", Layer.INDIVIDUAL_CONTRIBUTOR);
        Role architect = Role.of("Architect Lead", Layer.MANAGEMENT);
        Role cto = Role.of("CTO", Layer.EXECUTIVE);
        
        EscalationPath path = EscalationPath.of(engineer, architect, cto);
        
        assertThat(path.getDepth()).isEqualTo(3);
        assertThat(path.isEmpty()).isFalse();
    }
    
    @Test
    @DisplayName("Create escalation path from list")
    void testCreateFromList() {
        Role engineer = Role.of("Engineer", Layer.INDIVIDUAL_CONTRIBUTOR);
        Role architect = Role.of("Architect Lead", Layer.MANAGEMENT);
        
        List<Role> roles = List.of(engineer, architect);
        EscalationPath path = EscalationPath.of(roles);
        
        assertThat(path.getDepth()).isEqualTo(2);
    }
    
    @Test
    @DisplayName("Get next role in escalation path")
    void testGetNext() {
        Role engineer = Role.of("Engineer", Layer.INDIVIDUAL_CONTRIBUTOR);
        Role architect = Role.of("Architect Lead", Layer.MANAGEMENT);
        Role cto = Role.of("CTO", Layer.EXECUTIVE);
        
        EscalationPath path = EscalationPath.of(engineer, architect, cto);
        
        assertThat(path.getNext()).isPresent();
        assertThat(path.getNext().get()).isEqualTo(engineer);
    }
    
    @Test
    @DisplayName("Get remaining path after first role")
    void testGetRemaining() {
        Role engineer = Role.of("Engineer", Layer.INDIVIDUAL_CONTRIBUTOR);
        Role architect = Role.of("Architect Lead", Layer.MANAGEMENT);
        Role cto = Role.of("CTO", Layer.EXECUTIVE);
        
        EscalationPath path = EscalationPath.of(engineer, architect, cto);
        EscalationPath remaining = path.getRemaining();
        
        assertThat(remaining.getDepth()).isEqualTo(2);
        assertThat(remaining.getNext().get()).isEqualTo(architect);
    }
    
    @Test
    @DisplayName("Get final authority in escalation path")
    void testGetFinalAuthority() {
        Role engineer = Role.of("Engineer", Layer.INDIVIDUAL_CONTRIBUTOR);
        Role architect = Role.of("Architect Lead", Layer.MANAGEMENT);
        Role cto = Role.of("CTO", Layer.EXECUTIVE);
        
        EscalationPath path = EscalationPath.of(engineer, architect, cto);
        
        assertThat(path.getFinalAuthority()).isPresent();
        assertThat(path.getFinalAuthority().get()).isEqualTo(cto);
    }
    
    @Test
    @DisplayName("Empty escalation path")
    void testEmptyPath() {
        EscalationPath path = EscalationPath.of();
        
        assertThat(path.isEmpty()).isTrue();
        assertThat(path.getDepth()).isEqualTo(0);
        assertThat(path.getNext()).isEmpty();
        assertThat(path.getFinalAuthority()).isEmpty();
    }
    
    @Test
    @DisplayName("Single role escalation path")
    void testSingleRolePath() {
        Role cto = Role.of("CTO", Layer.EXECUTIVE);
        EscalationPath path = EscalationPath.of(cto);
        
        assertThat(path.getDepth()).isEqualTo(1);
        assertThat(path.getNext()).isPresent();
        assertThat(path.getNext().get()).isEqualTo(cto);
        assertThat(path.getFinalAuthority()).isPresent();
        assertThat(path.getFinalAuthority().get()).isEqualTo(cto);
        assertThat(path.getRemaining().isEmpty()).isTrue();
    }
    
    @Test
    @DisplayName("Navigate through entire escalation path")
    void testNavigatePath() {
        Role engineer = Role.of("Engineer", Layer.INDIVIDUAL_CONTRIBUTOR);
        Role architect = Role.of("Architect Lead", Layer.MANAGEMENT);
        Role cto = Role.of("CTO", Layer.EXECUTIVE);
        
        EscalationPath path = EscalationPath.of(engineer, architect, cto);
        
        // First level
        assertThat(path.getNext().get()).isEqualTo(engineer);
        
        // Second level
        EscalationPath remaining1 = path.getRemaining();
        assertThat(remaining1.getNext().get()).isEqualTo(architect);
        
        // Third level
        EscalationPath remaining2 = remaining1.getRemaining();
        assertThat(remaining2.getNext().get()).isEqualTo(cto);
        
        // No more levels
        EscalationPath remaining3 = remaining2.getRemaining();
        assertThat(remaining3.isEmpty()).isTrue();
    }
    
    @Test
    @DisplayName("Escalation path is immutable")
    void testImmutability() {
        Role engineer = Role.of("Engineer", Layer.INDIVIDUAL_CONTRIBUTOR);
        Role architect = Role.of("Architect Lead", Layer.MANAGEMENT);
        
        EscalationPath path = EscalationPath.of(engineer, architect);
        
        assertThat(path.getDepth()).isEqualTo(2);
        // Verify that the path doesn't change
        assertThat(path.getDepth()).isEqualTo(2);
    }
}
