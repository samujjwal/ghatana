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
  void shouldCreateStepContract() { // GH-90000
    List<String> capabilities = List.of("DATA_CLOUD", "EVENT_LOG"); // GH-90000
    Map<String, String> metadata = Map.of("key", "value"); // GH-90000

    StepContract contract = new StepContract( // GH-90000
        "test.step",
        "input-schema.json",
        "output-schema.json",
        capabilities,
        metadata
    );

    assertThat(contract.name()).isEqualTo("test.step");
    assertThat(contract.inputSchemaRef()).isEqualTo("input-schema.json");
    assertThat(contract.outputSchemaRef()).isEqualTo("output-schema.json");
    assertThat(contract.requiredCapabilities()).isEqualTo(capabilities); // GH-90000
    assertThat(contract.metadata()).isEqualTo(metadata); // GH-90000
  }

  @Test
  @DisplayName("capabilities should return requiredCapabilities")
  void capabilitiesShouldReturnRequiredCapabilities() { // GH-90000
    List<String> capabilities = List.of("CAP_1", "CAP_2"); // GH-90000

    StepContract contract = new StepContract( // GH-90000
        "test.step", null, null, capabilities, null
    );

    assertThat(contract.capabilities()).isEqualTo(capabilities); // GH-90000
    assertThat(contract.capabilities()).isSameAs(contract.requiredCapabilities()); // GH-90000
  }

  @Test
  @DisplayName("should handle null fields")
  void shouldHandleNullFields() { // GH-90000
    StepContract contract = new StepContract(null, null, null, null, null); // GH-90000

    assertThat(contract.name()).isNull(); // GH-90000
    assertThat(contract.inputSchemaRef()).isNull(); // GH-90000
    assertThat(contract.outputSchemaRef()).isNull(); // GH-90000
    assertThat(contract.requiredCapabilities()).isNull(); // GH-90000
    assertThat(contract.metadata()).isNull(); // GH-90000
    assertThat(contract.capabilities()).isNull(); // GH-90000
  }

  @Test
  @DisplayName("should implement equals correctly")
  void shouldImplementEquals() { // GH-90000
    StepContract c1 = new StepContract("step", "in", "out", List.of("cap"), Map.of("k", "v"));
    StepContract c2 = new StepContract("step", "in", "out", List.of("cap"), Map.of("k", "v"));
    StepContract c3 = new StepContract("other", "in", "out", List.of("cap"), Map.of("k", "v"));

    assertThat(c1).isEqualTo(c2); // GH-90000
    assertThat(c1).isNotEqualTo(c3); // GH-90000
  }

  @Test
  @DisplayName("should handle empty collections")
  void shouldHandleEmptyCollections() { // GH-90000
    StepContract contract = new StepContract( // GH-90000
        "step", "in", "out", List.of(), Map.of() // GH-90000
    );

    assertThat(contract.requiredCapabilities()).isEmpty(); // GH-90000
    assertThat(contract.metadata()).isEmpty(); // GH-90000
    assertThat(contract.capabilities()).isEmpty(); // GH-90000
  }
}
