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
        .event("login")
        .build(); // GH-90000
    assertThat(spec).contains("ADVANCED(").contains("EVENT(login)").contains("@50");
  }

  @Test
  void testEventWithAfterConstraint() { // GH-90000
    String spec = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("login")
        .after(Duration.ofSeconds(30)) // GH-90000
        .event("access")
        .build(); // GH-90000
    assertThat(spec) // GH-90000
        .contains("EVENT(login)")
        .contains("AFTER")
        .contains("30")
        .contains("EVENT(access)");
  }

  @Test
  void testEventWithBeforeConstraint() { // GH-90000
    String spec = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("login")
        .before(Duration.ofMinutes(5)) // GH-90000
        .event("logout")
        .build(); // GH-90000
    assertThat(spec) // GH-90000
        .contains("EVENT(login)")
        .contains("BEFORE")
        .contains("300")
        .contains("EVENT(logout)");
  }

  @Test
  void testEventWithinTimeWindow() { // GH-90000
    String spec = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("transaction-start")
        .within(Duration.ofMinutes(1)) // GH-90000
        .event("transaction-complete")
        .build(); // GH-90000
    assertThat(spec) // GH-90000
        .contains("EVENT(transaction-start)")
        .contains("WITHIN")
        .contains("60")
        .contains("EVENT(transaction-complete)");
  }

  @Test
  void testEventNegation() { // GH-90000
    String spec = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("login")
        .not("admin-approval")
        .event("suspicious-access")
        .build(); // GH-90000
    assertThat(spec) // GH-90000
        .contains("EVENT(login)")
        .contains("NOT(admin-approval)")
        .contains("EVENT(suspicious-access)");
  }

  @Test
  void testComplexTemporalPattern() { // GH-90000
    String spec = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("login")
        .after(Duration.ofSeconds(30)) // GH-90000
        .event("data-access")
        .before(Duration.ofMinutes(5)) // GH-90000
        .not("admin-log")
        .event("logout")
        .confidence(0.85) // GH-90000
        .build(); // GH-90000
    assertThat(spec) // GH-90000
        .contains("ADVANCED(")
        .contains("EVENT(login)")
        .contains("AFTER(login)[30]")
        .contains("EVENT(data-access)")
        .contains("BEFORE(data-access)[300]")
        .contains("NOT(admin-log)")
        .contains("EVENT(logout)")
        .contains("@85");
  }

  @Test
  void testWithConfidence() { // GH-90000
    String spec = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("e1")
        .event("e2")
        .confidence(0.72) // GH-90000
        .build(); // GH-90000
    assertThat(spec).contains("@72");
  }

  @Test
  void testAfterConstraintRequiresPrecedingEvent() { // GH-90000
    AdvancedPatternQueryBuilder builder = AdvancedPatternQueryBuilder.create(); // GH-90000
    assertThatThrownBy(() -> builder.after(Duration.ofSeconds(10))) // GH-90000
        .isInstanceOf(IllegalStateException.class) // GH-90000
        .hasMessage("Cannot add 'after' constraint without a preceding event");
  }

  @Test
  void testBeforeConstraintRequiresPrecedingEvent() { // GH-90000
    AdvancedPatternQueryBuilder builder = AdvancedPatternQueryBuilder.create(); // GH-90000
    assertThatThrownBy(() -> builder.before(Duration.ofMinutes(1))) // GH-90000
        .isInstanceOf(IllegalStateException.class) // GH-90000
        .hasMessage("Cannot add 'before' constraint without a preceding event");
  }

  @Test
  void testWithinConstraintRequiresPrecedingEvent() { // GH-90000
    AdvancedPatternQueryBuilder builder = AdvancedPatternQueryBuilder.create(); // GH-90000
    assertThatThrownBy(() -> builder.within(Duration.ofSeconds(5))) // GH-90000
        .isInstanceOf(IllegalStateException.class) // GH-90000
        .hasMessage("Cannot add 'within' constraint without a preceding event");
  }

  @Test
  void testRejectsNullEventType() { // GH-90000
    assertThatThrownBy(() -> AdvancedPatternQueryBuilder.create().event(null)) // GH-90000
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessage("Event type cannot be null or empty");
  }

  @Test
  void testRejectsBlankEventType() { // GH-90000
    assertThatThrownBy(() -> AdvancedPatternQueryBuilder.create().event("  "))
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessage("Event type cannot be null or empty");
  }

  @Test
  void testRejectsInvalidConfidence() { // GH-90000
    AdvancedPatternQueryBuilder builder = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("e1");
    assertThatThrownBy(() -> builder.confidence(1.5)) // GH-90000
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessage("Confidence must be between 0 and 1");
  }

  @Test
  void testRequiresAtLeastOneConstraintToBuild() { // GH-90000
    assertThatThrownBy(() -> AdvancedPatternQueryBuilder.create().build()) // GH-90000
        .isInstanceOf(IllegalStateException.class) // GH-90000
        .hasMessage("At least one event constraint must be added before building");
  }

  @Test
  void testGetConstraintCount() { // GH-90000
    AdvancedPatternQueryBuilder builder = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("e1")
        .after(Duration.ofSeconds(10)) // GH-90000
        .event("e2")
        .not("e3");
    assertThat(builder.getConstraintCount()).isEqualTo(4); // GH-90000
  }

  @Test
  void testGetConfidence() { // GH-90000
    AdvancedPatternQueryBuilder builder = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("e1")
        .confidence(0.77); // GH-90000
    assertThat(builder.getConfidence()).isEqualTo(0.77); // GH-90000
  }

  @Test
  void testChaining() { // GH-90000
    String spec = AdvancedPatternQueryBuilder.create() // GH-90000
        .event("a")
        .after(Duration.ofSeconds(5)) // GH-90000
        .event("b")
        .before(Duration.ofMinutes(2)) // GH-90000
        .not("c")
        .within(Duration.ofMinutes(10)) // GH-90000
        .event("d")
        .confidence(0.9) // GH-90000
        .description("Complex temporal pattern")
        .build(); // GH-90000
    assertThat(spec).isNotBlank().contains("ADVANCED(");
  }
}
