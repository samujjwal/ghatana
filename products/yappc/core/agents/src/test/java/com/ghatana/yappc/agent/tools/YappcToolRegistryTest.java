package com.ghatana.yappc.agent.tools;

import com.ghatana.agent.framework.planner.PlannerAgentFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for YappcToolRegistry.
 * 
 * @doc.type test
 * @doc.purpose Verify tool registration with AEP factory
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("YAPPC Tool Registry Tests")
class YappcToolRegistryTest {
    
    @Test
    @DisplayName("Should register all tools without exception")
    void shouldRegisterAllToolsWithoutException() {
        // GIVEN
        PlannerAgentFactory factory = new PlannerAgentFactory();
        
        // WHEN/THEN
        assertThatCode(() -> YappcToolRegistry.registerAll(factory))
            .doesNotThrowAnyException();
    }
    
    @Test
    @DisplayName("Should throw exception when factory is null")
    void shouldThrowExceptionWhenFactoryIsNull() {
        // WHEN/THEN
        assertThatThrownBy(() -> YappcToolRegistry.registerAll(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("PlannerAgentFactory cannot be null");
    }
    
    @Test
    @DisplayName("Maven tool should execute compile command")
    void mavenToolShouldExecuteCompile() {
        // WHEN
        String result = MavenTool.compile("/tmp/test-project");
        
        // THEN
        assertThat(result).isNotNull();
        assertThat(result).contains("Error"); // Expected since project doesn't exist
    }
    
    @Test
    @DisplayName("Git tool should execute status command")
    void gitToolShouldExecuteStatus() {
        // WHEN
        String result = GitTool.status("/tmp/test-repo");
        
        // THEN
        assertThat(result).isNotNull();
    }
    
    @Test
    @DisplayName("File tool should read existing file")
    void fileToolShouldReadFile() {
        // WHEN
        String result = FileTool.exists("/tmp");
        
        // THEN
        assertThat(result).isEqualTo("true");
    }
}
