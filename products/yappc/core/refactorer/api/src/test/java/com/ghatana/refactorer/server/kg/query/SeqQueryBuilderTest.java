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
  void testCreateSeqWithTwoEvents() { // GH-90000
    String spec = SeqQueryBuilder.create("login", "access").build(); // GH-90000
    assertThat(spec).contains("SEQ(login, access)").contains("[5m]").contains("@50");
  }

  @Test
  void testSeqWithMultipleEvents() { // GH-90000
    String spec =
        SeqQueryBuilder.create("login", "data-access", "file-read", "logout").build(); // GH-90000
    assertThat(spec).contains("SEQ(login, data-access, file-read, logout)");
  }

  @Test
  void testSeqWithCustomTimeWindow() { // GH-90000
    String spec =
        SeqQueryBuilder.create("login", "access") // GH-90000
            .within(Duration.ofMinutes(30)) // GH-90000
            .build(); // GH-90000
    assertThat(spec).contains("[30m]");
  }

  @Test
  void testSeqWithConfidence() { // GH-90000
    String spec =
        SeqQueryBuilder.create("login", "access") // GH-90000
            .confidence(0.85) // GH-90000
            .build(); // GH-90000
    assertThat(spec).contains("@85");
  }

  @Test
  void testSeqWithAllParameters() { // GH-90000
    String spec =
        SeqQueryBuilder.create("event1", "event2", "event3") // GH-90000
            .within(Duration.ofMinutes(10)) // GH-90000
            .confidence(0.92) // GH-90000
            .description("Test sequence pattern")
            .build(); // GH-90000
    assertThat(spec) // GH-90000
        .contains("SEQ(event1, event2, event3)")
        .contains("[10m]")
        .contains("@92");
  }

  @Test
  void testSeqRequiresAtLeastTwoEvents() { // GH-90000
    assertThatThrownBy(() -> SeqQueryBuilder.create("single"))
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessage("SEQ pattern requires at least 2 event types");
  }

  @Test
  void testSeqWithInvalidConfidence() { // GH-90000
    SeqQueryBuilder builder = SeqQueryBuilder.create("a", "b"); // GH-90000
    assertThatThrownBy(() -> builder.confidence(1.5)) // GH-90000
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessage("Confidence must be between 0 and 1");
  }

  @Test
  void testSeqGettersReturnCorrectValues() { // GH-90000
    SeqQueryBuilder builder =
        SeqQueryBuilder.create("login", "access") // GH-90000
            .within(Duration.ofMinutes(15)) // GH-90000
            .confidence(0.75); // GH-90000

    assertThat(builder.getEventTypes()).containsExactly("login", "access"); // GH-90000
    assertThat(builder.getTimeWindow()).isEqualTo(Duration.ofMinutes(15)); // GH-90000
    assertThat(builder.getConfidence()).isEqualTo(0.75); // GH-90000
  }

  @Test
  void testSeqChaining() { // GH-90000
    String spec =
        SeqQueryBuilder.create("a", "b") // GH-90000
            .within(Duration.ofSeconds(30)) // GH-90000
            .confidence(0.5) // GH-90000
            .description("desc")
            .build(); // GH-90000
    assertThat(spec).isNotBlank(); // GH-90000
  }
}
