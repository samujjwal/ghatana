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
  void testCreateSimpleEventPattern() {
    String spec = AdvancedPatternQueryBuilder.create()
        .event("login")
        .build();
    assertThat(spec).contains("ADVANCED(").contains("EVENT(login)").contains("@50");
  }

  @Test
  void testEventWithAfterConstraint() {
    String spec = AdvancedPatternQueryBuilder.create()
        .event("login")
        .after(Duration.ofSeconds(30))
        .event("access")
        .build();
    assertThat(spec)
        .contains("EVENT(login)")
        .contains("AFTER")
        .contains("30")
        .contains("EVENT(access)");
  }

  @Test
  void testEventWithBeforeConstraint() {
    String spec = AdvancedPatternQueryBuilder.create()
        .event("login")
        .before(Duration.ofMinutes(5))
        .event("logout")
        .build();
    assertThat(spec)
        .contains("EVENT(login)")
        .contains("BEFORE")
        .contains("300")
        .contains("EVENT(logout)");
  }

  @Test
  void testEventWithinTimeWindow() {
    String spec = AdvancedPatternQueryBuilder.create()
        .event("transaction-start")
        .within(Duration.ofMinutes(1))
        .event("transaction-complete")
        .build();
    assertThat(spec)
        .contains("EVENT(transaction-start)")
        .contains("WITHIN")
        .contains("60")
        .contains("EVENT(transaction-complete)");
  }

  @Test
  void testEventNegation() {
    String spec = AdvancedPatternQueryBuilder.create()
        .event("login")
        .not("admin-approval")
        .event("suspicious-access")
        .build();
    assertThat(spec)
        .contains("EVENT(login)")
        .contains("NOT(admin-approval)")
        .contains("EVENT(suspicious-access)");
  }

  @Test
  void testComplexTemporalPattern() {
    String spec = AdvancedPatternQueryBuilder.create()
        .event("login")
        .after(Duration.ofSeconds(30))
        .event("data-access")
        .before(Duration.ofMinutes(5))
        .not("admin-log")
        .event("logout")
        .confidence(0.85)
        .build();
    assertThat(spec)
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
  void testWithConfidence() {
    String spec = AdvancedPatternQueryBuilder.create()
        .event("e1")
        .event("e2")
        .confidence(0.72)
        .build();
    assertThat(spec).contains("@72");
  }

  @Test
  void testAfterConstraintRequiresPrecedingEvent() {
    AdvancedPatternQueryBuilder builder = AdvancedPatternQueryBuilder.create();
    assertThatThrownBy(() -> builder.after(Duration.ofSeconds(10)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Cannot add 'after' constraint without a preceding event");
  }

  @Test
  void testBeforeConstraintRequiresPrecedingEvent() {
    AdvancedPatternQueryBuilder builder = AdvancedPatternQueryBuilder.create();
    assertThatThrownBy(() -> builder.before(Duration.ofMinutes(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Cannot add 'before' constraint without a preceding event");
  }

  @Test
  void testWithinConstraintRequiresPrecedingEvent() {
    AdvancedPatternQueryBuilder builder = AdvancedPatternQueryBuilder.create();
    assertThatThrownBy(() -> builder.within(Duration.ofSeconds(5)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Cannot add 'within' constraint without a preceding event");
  }

  @Test
  void testRejectsNullEventType() {
    assertThatThrownBy(() -> AdvancedPatternQueryBuilder.create().event(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Event type cannot be null or empty");
  }

  @Test
  void testRejectsBlankEventType() {
    assertThatThrownBy(() -> AdvancedPatternQueryBuilder.create().event("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Event type cannot be null or empty");
  }

  @Test
  void testRejectsInvalidConfidence() {
    AdvancedPatternQueryBuilder builder = AdvancedPatternQueryBuilder.create()
        .event("e1");
    assertThatThrownBy(() -> builder.confidence(1.5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Confidence must be between 0 and 1");
  }

  @Test
  void testRequiresAtLeastOneConstraintToBuild() {
    assertThatThrownBy(() -> AdvancedPatternQueryBuilder.create().build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("At least one event constraint must be added before building");
  }

  @Test
  void testGetConstraintCount() {
    AdvancedPatternQueryBuilder builder = AdvancedPatternQueryBuilder.create()
        .event("e1")
        .after(Duration.ofSeconds(10))
        .event("e2")
        .not("e3");
    assertThat(builder.getConstraintCount()).isEqualTo(4);
  }

  @Test
  void testGetConfidence() {
    AdvancedPatternQueryBuilder builder = AdvancedPatternQueryBuilder.create()
        .event("e1")
        .confidence(0.77);
    assertThat(builder.getConfidence()).isEqualTo(0.77);
  }

  @Test
  void testChaining() {
    String spec = AdvancedPatternQueryBuilder.create()
        .event("a")
        .after(Duration.ofSeconds(5))
        .event("b")
        .before(Duration.ofMinutes(2))
        .not("c")
        .within(Duration.ofMinutes(10))
        .event("d")
        .confidence(0.9)
        .description("Complex temporal pattern")
        .build();
    assertThat(spec).isNotBlank().contains("ADVANCED(");
  }
}
