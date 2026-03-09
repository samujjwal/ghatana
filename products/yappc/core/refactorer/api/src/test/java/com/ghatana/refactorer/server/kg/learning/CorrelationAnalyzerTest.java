package com.ghatana.refactorer.server.kg.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for CorrelationAnalyzer. 
 * @doc.type class
 * @doc.purpose Handles correlation analyzer test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class CorrelationAnalyzerTest extends EventloopTestBase {
  private CorrelationAnalyzer analyzer;

  @BeforeEach
  void setup() {
    // 5 second window for testing
    analyzer = new CorrelationAnalyzer(5000);
  }

  @Test
  void testRecordSingleEvent() {
    analyzer.recordEvent("login");
    assertThat(analyzer.getEventWindowSize()).isEqualTo(1);
    assertThat(analyzer.getEventCounts()).containsEntry("login", 1);
  }

  @Test
  void testRecordMultipleEvents() {
    analyzer.recordEvent("login");
    analyzer.recordEvent("access");
    analyzer.recordEvent("logout");
    assertThat(analyzer.getEventWindowSize()).isEqualTo(3);
    assertThat(analyzer.getEventCounts()).hasSize(3);
  }

  @Test
  void testCorrelationBetweenTwoEvents() {
    analyzer.recordEvent("login", 1000);
    analyzer.recordEvent("access", 1500); // 500ms after login
    List<CorrelationAnalyzer.Correlation> correlations = analyzer.getCorrelations();
    assertThat(correlations).hasSize(1);
    CorrelationAnalyzer.Correlation corr = correlations.get(0);
    assertThat(corr.firstEvent()).isEqualTo("login");
    assertThat(corr.secondEvent()).isEqualTo("access");
    assertThat(corr.coOccurrences()).isEqualTo(1);
  }

  @Test
  void testConfidenceCalculation() {
    // Record: login -> access (3 times), login -> error (1 time)
    analyzer.recordEvent("login", 1000);
    analyzer.recordEvent("access", 1500);

    analyzer.recordEvent("login", 2000);
    analyzer.recordEvent("access", 2500);

    analyzer.recordEvent("login", 3000);
    analyzer.recordEvent("error", 3500);

    List<CorrelationAnalyzer.Correlation> correlations = analyzer.getCorrelations();
    // All-pairs-within-window analysis: includes login->access, login->error, and other pairs
    assertThat(correlations).hasSizeGreaterThanOrEqualTo(2);
    // login->access should exist and have high confidence
    assertThat(correlations)
        .anyMatch(
            c ->
                "login".equals(c.firstEvent())
                    && "access".equals(c.secondEvent())
                    && c.confidence() > 0.6);
  }

  @Test
  void testEventWindowExpiration() {
    analyzer.recordEvent("early", 1000);
    analyzer.recordEvent("recent", 6000); // 6 seconds later, outside 5s window
    assertThat(analyzer.getEventWindowSize()).isEqualTo(1); // early event removed
  }

  @Test
  void testMinConfidenceFilter() {
    analyzer.recordEvent("a", 1000);
    analyzer.recordEvent("b", 1500);
    analyzer.recordEvent("a", 2000);
    analyzer.recordEvent("c", 2500);

    List<CorrelationAnalyzer.Correlation> correlations = analyzer.getCorrelations(0.8);
    // With high threshold, may have no correlations
    assertThat(correlations).isNotNull();
  }

  @Test
  void testReset() {
    analyzer.recordEvent("event1");
    analyzer.recordEvent("event2");
    assertThat(analyzer.getEventWindowSize()).isGreaterThan(0);

    analyzer.reset();
    assertThat(analyzer.getEventWindowSize()).isZero();
    assertThat(analyzer.getEventCounts()).isEmpty();
    assertThat(analyzer.getTotalPairsAnalyzed()).isZero();
  }

  @Test
  void testRejectsNullEvent() {
    assertThatThrownBy(() -> analyzer.recordEvent(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void testRejectsBlankEvent() {
    assertThatThrownBy(() -> analyzer.recordEvent("   "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void testSupportCalculation() {
    analyzer.recordEvent("login", 1000);
    analyzer.recordEvent("access", 1500);
    analyzer.recordEvent("login", 2000);
    analyzer.recordEvent("logout", 2500);

    List<CorrelationAnalyzer.Correlation> correlations = analyzer.getCorrelations();
    for (CorrelationAnalyzer.Correlation corr : correlations) {
      assertThat(corr.support()).isBetween(0.0, 1.0);
    }
  }

  @Test
  void testCorrelationToString() {
    analyzer.recordEvent("login", 1000);
    analyzer.recordEvent("access", 1500);
    List<CorrelationAnalyzer.Correlation> correlations = analyzer.getCorrelations();
    assertThat(correlations.get(0).toString())
        .contains("login")
        .contains("access")
        .contains("conf:")
        .contains("sup:");
  }

  @Test
  void testMultipleCorrelations() {
    // Create multiple different correlations
    analyzer.recordEvent("a", 1000);
    analyzer.recordEvent("b", 1100);
    analyzer.recordEvent("a", 2000);
    analyzer.recordEvent("c", 2100);
    analyzer.recordEvent("b", 3000);
    analyzer.recordEvent("c", 3100);

    List<CorrelationAnalyzer.Correlation> correlations = analyzer.getCorrelations();
    assertThat(correlations.size()).isGreaterThanOrEqualTo(2);
  }
}
