package com.ghatana.yappc.agent.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.yappc.agent.EventPublisher;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Ops ValidateReleaseStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles validate release step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class ValidateReleaseStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventPublisher eventClient;
  private ValidateReleaseStep step;

  @BeforeEach
  void setUp() { // GH-90000
    dbClient = mock(DatabaseClient.class); // GH-90000
    eventClient = mock(EventPublisher.class); // GH-90000
    step = new ValidateReleaseStep(dbClient, eventClient); // GH-90000
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() { // GH-90000
    assertThat(step.getStepId()).isEqualTo("ops.validaterelease");
  }
}
