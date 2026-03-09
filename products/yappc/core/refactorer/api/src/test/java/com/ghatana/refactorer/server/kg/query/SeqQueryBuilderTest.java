package com.ghatana.refactorer.server.kg.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Unit tests for SeqQueryBuilder DSL. 
 * @doc.type class
 * @doc.purpose Handles seq query builder test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class SeqQueryBuilderTest extends EventloopTestBase {

  @Test
  void testCreateSeqWithTwoEvents() {
    String spec = SeqQueryBuilder.create("login", "access").build();
    assertThat(spec).contains("SEQ(login, access)").contains("[5m]").contains("@50");
  }

  @Test
  void testSeqWithMultipleEvents() {
    String spec =
        SeqQueryBuilder.create("login", "data-access", "file-read", "logout").build();
    assertThat(spec).contains("SEQ(login, data-access, file-read, logout)");
  }

  @Test
  void testSeqWithCustomTimeWindow() {
    String spec =
        SeqQueryBuilder.create("login", "access")
            .within(Duration.ofMinutes(30))
            .build();
    assertThat(spec).contains("[30m]");
  }

  @Test
  void testSeqWithConfidence() {
    String spec =
        SeqQueryBuilder.create("login", "access")
            .confidence(0.85)
            .build();
    assertThat(spec).contains("@85");
  }

  @Test
  void testSeqWithAllParameters() {
    String spec =
        SeqQueryBuilder.create("event1", "event2", "event3")
            .within(Duration.ofMinutes(10))
            .confidence(0.92)
            .description("Test sequence pattern")
            .build();
    assertThat(spec)
        .contains("SEQ(event1, event2, event3)")
        .contains("[10m]")
        .contains("@92");
  }

  @Test
  void testSeqRequiresAtLeastTwoEvents() {
    assertThatThrownBy(() -> SeqQueryBuilder.create("single"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SEQ pattern requires at least 2 event types");
  }

  @Test
  void testSeqWithInvalidConfidence() {
    SeqQueryBuilder builder = SeqQueryBuilder.create("a", "b");
    assertThatThrownBy(() -> builder.confidence(1.5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Confidence must be between 0 and 1");
  }

  @Test
  void testSeqGettersReturnCorrectValues() {
    SeqQueryBuilder builder =
        SeqQueryBuilder.create("login", "access")
            .within(Duration.ofMinutes(15))
            .confidence(0.75);

    assertThat(builder.getEventTypes()).containsExactly("login", "access");
    assertThat(builder.getTimeWindow()).isEqualTo(Duration.ofMinutes(15));
    assertThat(builder.getConfidence()).isEqualTo(0.75);
  }

  @Test
  void testSeqChaining() {
    String spec =
        SeqQueryBuilder.create("a", "b")
            .within(Duration.ofSeconds(30))
            .confidence(0.5)
            .description("desc")
            .build();
    assertThat(spec).isNotBlank();
  }
}
