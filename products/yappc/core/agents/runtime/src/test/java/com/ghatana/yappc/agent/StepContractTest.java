package com.ghatana.yappc.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StepContract}.
 *
 * @doc.type class
 * @doc.purpose Verify StepContract record behavior
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("StepContract Tests")
class StepContractTest {

  @Test
  @DisplayName("should create StepContract with all fields")
  void shouldCreateStepContract() { 
    List<String> capabilities = List.of("DATA_CLOUD", "EVENT_LOG"); 
    Map<String, String> metadata = Map.of("key", "value"); 

    StepContract contract = new StepContract( 
        "test.step",
        "input-schema.json",
        "output-schema.json",
        capabilities,
        metadata
    );

    assertThat(contract.name()).isEqualTo("test.step");
    assertThat(contract.inputSchemaRef()).isEqualTo("input-schema.json");
    assertThat(contract.outputSchemaRef()).isEqualTo("output-schema.json");
    assertThat(contract.requiredCapabilities()).isEqualTo(capabilities); 
    assertThat(contract.metadata()).isEqualTo(metadata); 
  }

  @Test
  @DisplayName("capabilities should return requiredCapabilities")
  void capabilitiesShouldReturnRequiredCapabilities() { 
    List<String> capabilities = List.of("CAP_1", "CAP_2"); 

    StepContract contract = new StepContract( 
        "test.step", null, null, capabilities, null
    );

    assertThat(contract.capabilities()).isEqualTo(capabilities); 
    assertThat(contract.capabilities()).isSameAs(contract.requiredCapabilities()); 
  }

  @Test
  @DisplayName("should handle null fields")
  void shouldHandleNullFields() { 
    StepContract contract = new StepContract(null, null, null, null, null); 

    assertThat(contract.name()).isNull(); 
    assertThat(contract.inputSchemaRef()).isNull(); 
    assertThat(contract.outputSchemaRef()).isNull(); 
    assertThat(contract.requiredCapabilities()).isNull(); 
    assertThat(contract.metadata()).isNull(); 
    assertThat(contract.capabilities()).isNull(); 
  }

  @Test
  @DisplayName("should implement equals correctly")
  void shouldImplementEquals() { 
    StepContract c1 = new StepContract("step", "in", "out", List.of("cap"), Map.of("k", "v"));
    StepContract c2 = new StepContract("step", "in", "out", List.of("cap"), Map.of("k", "v"));
    StepContract c3 = new StepContract("other", "in", "out", List.of("cap"), Map.of("k", "v"));

    assertThat(c1).isEqualTo(c2); 
    assertThat(c1).isNotEqualTo(c3); 
  }

  @Test
  @DisplayName("should handle empty collections")
  void shouldHandleEmptyCollections() { 
    StepContract contract = new StepContract( 
        "step", "in", "out", List.of(), Map.of() 
    );

    assertThat(contract.requiredCapabilities()).isEmpty(); 
    assertThat(contract.metadata()).isEmpty(); 
    assertThat(contract.capabilities()).isEmpty(); 
  }
}
