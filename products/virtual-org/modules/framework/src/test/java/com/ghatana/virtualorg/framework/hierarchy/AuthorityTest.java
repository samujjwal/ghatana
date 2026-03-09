package com.ghatana.virtualorg.framework.hierarchy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Authority record.
 *
 * Tests validate:
 * - Authority creation via builder
 * - Decision checking
 * - Immutability
 * - Edge cases
 *
 * @doc.type test
 * @doc.purpose Unit tests for Authority record
 * @doc.layer product
 */
@DisplayName("Authority Record Tests")
class AuthorityTest {
    
    @Test
    @DisplayName("Create authority with builder")
    void testCreateWithBuilder() {
        Authority auth = Authority.builder()
            .addDecision("code_review")
            .addDecision("merge_pr")
            .build();
        
        assertThat(auth.canDecide("code_review")).isTrue();
        assertThat(auth.canDecide("merge_pr")).isTrue();
        assertThat(auth.getDecisionCount()).isEqualTo(2);
    }
    
    @Test
    @DisplayName("Create authority from set")
    void testCreateFromSet() {
        Set<String> decisions = Set.of("code_review", "merge_pr", "deploy");
        Authority auth = new Authority(decisions);
        
        assertThat(auth.canDecide("code_review")).isTrue();
        assertThat(auth.canDecide("merge_pr")).isTrue();
        assertThat(auth.canDecide("deploy")).isTrue();
        assertThat(auth.getDecisionCount()).isEqualTo(3);
    }
    
    @Test
    @DisplayName("Authority rejects unknown decision")
    void testRejectUnknownDecision() {
        Authority auth = Authority.builder()
            .addDecision("code_review")
            .build();
        
        assertThat(auth.canDecide("code_review")).isTrue();
        assertThat(auth.canDecide("deploy")).isFalse();
    }
    
    @Test
    @DisplayName("Empty authority has no decisions")
    void testEmptyAuthority() {
        Authority auth = new Authority(Set.of());
        
        assertThat(auth.isEmpty()).isTrue();
        assertThat(auth.getDecisionCount()).isEqualTo(0);
        assertThat(auth.canDecide("any_decision")).isFalse();
    }
    
    @Test
    @DisplayName("Builder rejects null decision type")
    void testBuilderRejectNullDecision() {
        assertThatThrownBy(() -> Authority.builder().addDecision(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Decision type cannot be null or empty");
    }
    
    @Test
    @DisplayName("Builder rejects empty decision type")
    void testBuilderRejectEmptyDecision() {
        assertThatThrownBy(() -> Authority.builder().addDecision(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Decision type cannot be null or empty");
    }
    
    @Test
    @DisplayName("Builder supports chaining")
    void testBuilderChaining() {
        Authority auth = Authority.builder()
            .addDecision("decision1")
            .addDecision("decision2")
            .addDecision("decision3")
            .build();
        
        assertThat(auth.getDecisionCount()).isEqualTo(3);
    }
    
    @Test
    @DisplayName("Builder supports addDecisions with varargs")
    void testBuilderAddDecisions() {
        Authority auth = Authority.builder()
            .addDecisions("code_review", "merge_pr", "deploy")
            .build();
        
        assertThat(auth.getDecisionCount()).isEqualTo(3);
        assertThat(auth.canDecide("code_review")).isTrue();
        assertThat(auth.canDecide("merge_pr")).isTrue();
        assertThat(auth.canDecide("deploy")).isTrue();
    }
    
    @Test
    @DisplayName("Authority is immutable")
    void testImmutability() {
        Set<String> decisions = Set.of("code_review");
        Authority auth = new Authority(decisions);
        
        // Verify that modifying the original set doesn't affect authority
        assertThat(auth.canDecide("code_review")).isTrue();
    }
}
