package com.ghatana.refactorer.server.kg.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Unit tests for AdvancedPatternQueryBuilder with temporal constraints.
 * @doc.type class
 * @doc.purpose Handles advanced pattern query builder test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class AdvancedPatternQueryBuilderTest extends EventloopTestBase {

  @Test
  void testCreateSimpleEventPattern() { // GH-90000
    String spec = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("login [GH-90000]")
        .build(); // GH-90000
    assertThat(spec).contains("ADVANCED( [GH-90000]").contains("EVENT(login) [GH-90000]").contains("@50 [GH-90000]");
  }

  @Test
  void testEventWithAfterConstraint() { // GH-90000
    String spec = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("login [GH-90000]")
        .after(Duration.ofSeconds(30)) // GH-90000
        .event("access [GH-90000]")
        .build(); // GH-90000
    assertThat(spec) // GH-90000
        .contains("EVENT(login) [GH-90000]")
        .contains("AFTER [GH-90000]")
        .contains("30 [GH-90000]")
        .contains("EVENT(access) [GH-90000]");
  }

  @Test
  void testEventWithBeforeConstraint() { // GH-90000
    String spec = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("login [GH-90000]")
        .before(Duration.ofMinutes(5)) // GH-90000
        .event("logout [GH-90000]")
        .build(); // GH-90000
    assertThat(spec) // GH-90000
        .contains("EVENT(login) [GH-90000]")
        .contains("BEFORE [GH-90000]")
        .contains("300 [GH-90000]")
        .contains("EVENT(logout) [GH-90000]");
  }

  @Test
  void testEventWithinTimeWindow() { // GH-90000
    String spec = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("transaction-start [GH-90000]")
        .within(Duration.ofMinutes(1)) // GH-90000
        .event("transaction-complete [GH-90000]")
        .build(); // GH-90000
    assertThat(spec) // GH-90000
        .contains("EVENT(transaction-start) [GH-90000]")
        .contains("WITHIN [GH-90000]")
        .contains("60 [GH-90000]")
        .contains("EVENT(transaction-complete) [GH-90000]");
  }

  @Test
  void testEventNegation() { // GH-90000
    String spec = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("login [GH-90000]")
        .not("admin-approval [GH-90000]")
        .event("suspicious-access [GH-90000]")
        .build(); // GH-90000
    assertThat(spec) // GH-90000
        .contains("EVENT(login) [GH-90000]")
        .contains("NOT(admin-approval) [GH-90000]")
        .contains("EVENT(suspicious-access) [GH-90000]");
  }

  @Test
  void testComplexTemporalPattern() { // GH-90000
    String spec = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("login [GH-90000]")
        .after(Duration.ofSeconds(30)) // GH-90000
        .event("data-access [GH-90000]")
        .before(Duration.ofMinutes(5)) // GH-90000
        .not("admin-log [GH-90000]")
        .event("logout [GH-90000]")
        .confidence(0.85) // GH-90000
        .build(); // GH-90000
    assertThat(spec) // GH-90000
        .contains("ADVANCED( [GH-90000]")
        .contains("EVENT(login) [GH-90000]")
        .contains("AFTER(login)[30] [GH-90000]")
        .contains("EVENT(data-access) [GH-90000]")
        .contains("BEFORE(data-access)[300] [GH-90000]")
        .contains("NOT(admin-log) [GH-90000]")
        .contains("EVENT(logout) [GH-90000]")
        .contains("@85 [GH-90000]");
  }

  @Test
  void testWithConfidence() { // GH-90000
    String spec = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("e1 [GH-90000]")
        .event("e2 [GH-90000]")
        .confidence(0.72) // GH-90000
        .build(); // GH-90000
    assertThat(spec).contains("@72 [GH-90000]");
  }

  @Test
  void testAfterConstraintRequiresPrecedingEvent() { // GH-90000
    AdvancedPatternQueryBuilder builder = AdvancedPatternQueryBuilder.create(); // GH-90000
    assertThatThrownBy(() -> builder.after(Duration.ofSeconds(10))) // GH-90000
        .isInstanceOf(IllegalStateException.class) // GH-90000
        .hasMessage("Cannot add 'after' constraint without a preceding event [GH-90000]");
  }

  @Test
  void testBeforeConstraintRequiresPrecedingEvent() { // GH-90000
    AdvancedPatternQueryBuilder builder = AdvancedPatternQueryBuilder.create(); // GH-90000
    assertThatThrownBy(() -> builder.before(Duration.ofMinutes(1))) // GH-90000
        .isInstanceOf(IllegalStateException.class) // GH-90000
        .hasMessage("Cannot add 'before' constraint without a preceding event [GH-90000]");
  }

  @Test
  void testWithinConstraintRequiresPrecedingEvent() { // GH-90000
    AdvancedPatternQueryBuilder builder = AdvancedPatternQueryBuilder.create(); // GH-90000
    assertThatThrownBy(() -> builder.within(Duration.ofSeconds(5))) // GH-90000
        .isInstanceOf(IllegalStateException.class) // GH-90000
        .hasMessage("Cannot add 'within' constraint without a preceding event [GH-90000]");
  }

  @Test
  void testRejectsNullEventType() { // GH-90000
    assertThatThrownBy(() -> AdvancedPatternQueryBuilder.create().event(null)) // GH-90000
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessage("Event type cannot be null or empty [GH-90000]");
  }

  @Test
  void testRejectsBlankEventType() { // GH-90000
    assertThatThrownBy(() -> AdvancedPatternQueryBuilder.create().event("   [GH-90000]"))
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessage("Event type cannot be null or empty [GH-90000]");
  }

  @Test
  void testRejectsInvalidConfidence() { // GH-90000
    AdvancedPatternQueryBuilder builder = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("e1 [GH-90000]");
    assertThatThrownBy(() -> builder.confidence(1.5)) // GH-90000
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessage("Confidence must be between 0 and 1 [GH-90000]");
  }

  @Test
  void testRequiresAtLeastOneConstraintToBuild() { // GH-90000
    assertThatThrownBy(() -> AdvancedPatternQueryBuilder.create().build()) // GH-90000
        .isInstanceOf(IllegalStateException.class) // GH-90000
        .hasMessage("At least one event constraint must be added before building [GH-90000]");
  }

  @Test
  void testGetConstraintCount() { // GH-90000
    AdvancedPatternQueryBuilder builder = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("e1 [GH-90000]")
        .after(Duration.ofSeconds(10)) // GH-90000
        .event("e2 [GH-90000]")
        .not("e3 [GH-90000]");
    assertThat(builder.getConstraintCount()).isEqualTo(4); // GH-90000
  }

  @Test
  void testGetConfidence() { // GH-90000
    AdvancedPatternQueryBuilder builder = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("e1 [GH-90000]")
        .confidence(0.77); // GH-90000
    assertThat(builder.getConfidence()).isEqualTo(0.77); // GH-90000
  }

  @Test
  void testChaining() { // GH-90000
    String spec = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("a [GH-90000]")
        .after(Duration.ofSeconds(5)) // GH-90000
        .event("b [GH-90000]")
        .before(Duration.ofMinutes(2)) // GH-90000
        .not("c [GH-90000]")
        .within(Duration.ofMinutes(10)) // GH-90000
        .event("d [GH-90000]")
        .confidence(0.9) // GH-90000
        .description("Complex temporal pattern [GH-90000]")
        .build(); // GH-90000
    assertThat(spec).isNotBlank().contains("ADVANCED( [GH-90000]");
  }
}
