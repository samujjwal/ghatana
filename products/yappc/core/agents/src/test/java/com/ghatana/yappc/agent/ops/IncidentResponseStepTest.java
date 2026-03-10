package com.ghatana.yappc.agent.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Ops IncidentResponseStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles incident response step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class IncidentResponseStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventCloud eventClient;
  private IncidentResponseStep step;

  @BeforeEach
  void setUp() {
    dbClient = mock(DatabaseClient.class);
    eventClient = mock(EventCloud.class);
    step = new IncidentResponseStep(dbClient, eventClient);
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() {
    assertThat(step.getStepId()).isEqualTo("ops.incidentresponse");
  }
}
