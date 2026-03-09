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
  void testCreateCompositePatternWithAnd() {
    String spec =
        PatternQueryBuilder.create()
            .operator(PatternQueryBuilder.LogicalOperator.AND)
            .pattern("SEQ(a, b)")
            .pattern("SEQ(c, d)")
            .build();
    assertThat(spec).contains("AND(SEQ(a, b), SEQ(c, d))").contains("@50");
  }

  @Test
  void testCreateCompositePatternWithOr() {
    String spec =
        PatternQueryBuilder.create()
            .operator(PatternQueryBuilder.LogicalOperator.OR)
            .pattern("pattern1")
            .pattern("pattern2")
            .pattern("pattern3")
            .build();
    assertThat(spec).contains("OR(pattern1, pattern2, pattern3)");
  }

  @Test
  void testCompositePatternWithConfidence() {
    String spec =
        PatternQueryBuilder.create()
            .operator(PatternQueryBuilder.LogicalOperator.AND)
            .pattern("p1")
            .pattern("p2")
            .confidence(0.88)
            .build();
    assertThat(spec).contains("@88");
  }

  @Test
  void testCompositePatternWithSeqBuilderIntegration() {
    String seqPattern =
        SeqQueryBuilder.create("login", "access")
            .within(Duration.ofMinutes(5))
            .confidence(0.8)
            .build();

    String compositeSpec =
        PatternQueryBuilder.create()
            .operator(PatternQueryBuilder.LogicalOperator.AND)
            .pattern(seqPattern)
            .pattern("other-pattern")
            .confidence(0.75)
            .build();

    assertThat(compositeSpec).contains("AND(").contains("SEQ(login, access)");
  }

  @Test
  void testPatternFromBuilderMethod() {
    SeqQueryBuilder seqBuilder =
        SeqQueryBuilder.create("a", "b").within(Duration.ofSeconds(10));

    String spec =
        PatternQueryBuilder.create()
            .operator(PatternQueryBuilder.LogicalOperator.OR)
            .patternFromBuilder(seqBuilder)
            .patternFromBuilder(PatternQueryBuilder.create().pattern("p1"))
            .build();

    assertThat(spec).contains("OR(");
  }

  @Test
  void testCompositePatternRequiresAtLeastOnePattern() {
    PatternQueryBuilder builder = PatternQueryBuilder.create();
    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("At least one pattern must be added before building");
  }

  @Test
  void testCompositePatternRejectsNullPattern() {
    assertThatThrownBy(
            () -> PatternQueryBuilder.create().pattern(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void testCompositePatternRejectsBlankPattern() {
    assertThatThrownBy(
            () -> PatternQueryBuilder.create().pattern("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Pattern specification cannot be null or empty");
  }

  @Test
  void testCompositePatternWithInvalidConfidence() {
    PatternQueryBuilder builder =
        PatternQueryBuilder.create()
            .operator(PatternQueryBuilder.LogicalOperator.AND)
            .pattern("p1");
    assertThatThrownBy(() -> builder.confidence(-0.1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void testCompositePatternGetters() {
    PatternQueryBuilder builder =
        PatternQueryBuilder.create()
            .operator(PatternQueryBuilder.LogicalOperator.OR)
            .pattern("pattern1")
            .pattern("pattern2")
            .confidence(0.65)
            .description("Test composite");

    assertThat(builder.getOperator())
        .isEqualTo(PatternQueryBuilder.LogicalOperator.OR);
    assertThat(builder.getPatterns()).containsExactly("pattern1", "pattern2");
    assertThat(builder.getConfidence()).isEqualTo(0.65);
  }

  @Test
  void testCompositePatternChaining() {
    String spec =
        PatternQueryBuilder.create()
            .operator(PatternQueryBuilder.LogicalOperator.AND)
            .pattern("p1")
            .pattern("p2")
            .pattern("p3")
            .confidence(0.9)
            .description("Complex pattern")
            .build();
    assertThat(spec).contains("AND(p1, p2, p3)").contains("@90");
  }

  @Test
  void testLogicalOperatorSymbols() {
    assertThat(PatternQueryBuilder.LogicalOperator.AND.getSymbol()).isEqualTo("AND");
    assertThat(PatternQueryBuilder.LogicalOperator.OR.getSymbol()).isEqualTo("OR");
  }
}
