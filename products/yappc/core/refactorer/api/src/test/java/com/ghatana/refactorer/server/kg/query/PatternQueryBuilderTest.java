package com.ghatana.refactorer.server.kg.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Unit tests for PatternQueryBuilder DSL.
 * @doc.type class
 * @doc.purpose Handles pattern query builder test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class PatternQueryBuilderTest extends EventloopTestBase {

  @Test
  void testCreateCompositePatternWithAnd() { // GH-90000
    String spec =
        PatternQueryBuilder.create() // GH-90000
            .operator(PatternQueryBuilder.LogicalOperator.AND) // GH-90000
            .pattern("SEQ(a, b)")
            .pattern("SEQ(c, d)")
            .build(); // GH-90000
    assertThat(spec).contains("AND(SEQ(a, b), SEQ(c, d))").contains("@50");
  }

  @Test
  void testCreateCompositePatternWithOr() { // GH-90000
    String spec =
        PatternQueryBuilder.create() // GH-90000
            .operator(PatternQueryBuilder.LogicalOperator.OR) // GH-90000
            .pattern("pattern1")
            .pattern("pattern2")
            .pattern("pattern3")
            .build(); // GH-90000
    assertThat(spec).contains("OR(pattern1, pattern2, pattern3)");
  }

  @Test
  void testCompositePatternWithConfidence() { // GH-90000
    String spec =
        PatternQueryBuilder.create() // GH-90000
            .operator(PatternQueryBuilder.LogicalOperator.AND) // GH-90000
            .pattern("p1")
            .pattern("p2")
            .confidence(0.88) // GH-90000
            .build(); // GH-90000
    assertThat(spec).contains("@88");
  }

  @Test
  void testCompositePatternWithSeqBuilderIntegration() { // GH-90000
    String seqPattern =
        SeqQueryBuilder.create("login", "access") // GH-90000
            .within(Duration.ofMinutes(5)) // GH-90000
            .confidence(0.8) // GH-90000
            .build(); // GH-90000

    String compositeSpec =
        PatternQueryBuilder.create() // GH-90000
            .operator(PatternQueryBuilder.LogicalOperator.AND) // GH-90000
            .pattern(seqPattern) // GH-90000
            .pattern("other-pattern")
            .confidence(0.75) // GH-90000
            .build(); // GH-90000

    assertThat(compositeSpec).contains("AND(").contains("SEQ(login, access)");
  }

  @Test
  void testPatternFromBuilderMethod() { // GH-90000
    SeqQueryBuilder seqBuilder =
        SeqQueryBuilder.create("a", "b").within(Duration.ofSeconds(10)); // GH-90000

    String spec =
        PatternQueryBuilder.create() // GH-90000
            .operator(PatternQueryBuilder.LogicalOperator.OR) // GH-90000
            .patternFromBuilder(seqBuilder) // GH-90000
            .patternFromBuilder(PatternQueryBuilder.create().pattern("p1"))
            .build(); // GH-90000

    assertThat(spec).contains("OR(");
  }

  @Test
  void testCompositePatternRequiresAtLeastOnePattern() { // GH-90000
    PatternQueryBuilder builder = PatternQueryBuilder.create(); // GH-90000
    assertThatThrownBy(builder::build) // GH-90000
        .isInstanceOf(IllegalStateException.class) // GH-90000
        .hasMessage("At least one pattern must be added before building");
  }

  @Test
  void testCompositePatternRejectsNullPattern() { // GH-90000
    assertThatThrownBy( // GH-90000
            () -> PatternQueryBuilder.create().pattern(null)) // GH-90000
        .isInstanceOf(IllegalArgumentException.class); // GH-90000
  }

  @Test
  void testCompositePatternRejectsBlankPattern() { // GH-90000
    assertThatThrownBy( // GH-90000
            () -> PatternQueryBuilder.create().pattern("   "))
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessage("Pattern specification cannot be null or empty");
  }

  @Test
  void testCompositePatternWithInvalidConfidence() { // GH-90000
    PatternQueryBuilder builder =
        PatternQueryBuilder.create() // GH-90000
            .operator(PatternQueryBuilder.LogicalOperator.AND) // GH-90000
            .pattern("p1");
    assertThatThrownBy(() -> builder.confidence(-0.1)) // GH-90000
        .isInstanceOf(IllegalArgumentException.class); // GH-90000
  }

  @Test
  void testCompositePatternGetters() { // GH-90000
    PatternQueryBuilder builder =
        PatternQueryBuilder.create() // GH-90000
            .operator(PatternQueryBuilder.LogicalOperator.OR) // GH-90000
            .pattern("pattern1")
            .pattern("pattern2")
            .confidence(0.65) // GH-90000
            .description("Test composite");

    assertThat(builder.getOperator()) // GH-90000
        .isEqualTo(PatternQueryBuilder.LogicalOperator.OR); // GH-90000
    assertThat(builder.getPatterns()).containsExactly("pattern1", "pattern2"); // GH-90000
    assertThat(builder.getConfidence()).isEqualTo(0.65); // GH-90000
  }

  @Test
  void testCompositePatternChaining() { // GH-90000
    String spec =
        PatternQueryBuilder.create() // GH-90000
            .operator(PatternQueryBuilder.LogicalOperator.AND) // GH-90000
            .pattern("p1")
            .pattern("p2")
            .pattern("p3")
            .confidence(0.9) // GH-90000
            .description("Complex pattern")
            .build(); // GH-90000
    assertThat(spec).contains("AND(p1, p2, p3)").contains("@90");
  }

  @Test
  void testLogicalOperatorSymbols() { // GH-90000
    assertThat(PatternQueryBuilder.LogicalOperator.AND.getSymbol()).isEqualTo("AND");
    assertThat(PatternQueryBuilder.LogicalOperator.OR.getSymbol()).isEqualTo("OR");
  }
}
