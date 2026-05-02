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

@DisplayName("Ops PromoteOrRollbackStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles promote or rollback step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class PromoteOrRollbackStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventPublisher eventClient;
  private PromoteOrRollbackStep step;

  @BeforeEach
  void setUp() { 
    dbClient = mock(DatabaseClient.class); 
    eventClient = mock(EventPublisher.class); 
    step = new PromoteOrRollbackStep(dbClient, eventClient); 
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() { 
    assertThat(step.getStepId()).isEqualTo("ops.promoteorrollback");
  }
}
