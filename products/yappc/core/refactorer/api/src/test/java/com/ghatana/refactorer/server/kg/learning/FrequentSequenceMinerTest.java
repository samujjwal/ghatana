package com.ghatana.refactorer.server.kg.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for FrequentSequenceMiner (Apriori algorithm). 
 * @doc.type class
 * @doc.purpose Handles frequent sequence miner test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class FrequentSequenceMinerTest extends EventloopTestBase {
  private FrequentSequenceMiner miner;

  @BeforeEach
  void setup() {
    // 30% minimum support
    miner = new FrequentSequenceMiner(0.3);
  }

  @Test
  void testAddSingleSequence() {
    miner.addSequence(List.of("a", "b", "c"));
    assertThat(miner.getSequenceCount()).isEqualTo(1);
  }

  @Test
  void testAddMultipleSequences() {
    miner.addSequence(List.of("a", "b"));
    miner.addSequence(List.of("b", "c"));
    miner.addSequence(List.of("a", "c"));
    assertThat(miner.getSequenceCount()).isEqualTo(3);
  }

  @Test
  void testMineSingleFrequentPattern() {
    // All sequences contain a->b
    miner.addSequence(List.of("a", "b", "c"));
    miner.addSequence(List.of("a", "b", "d"));
    miner.addSequence(List.of("a", "b", "e"));

    List<FrequentSequenceMiner.Pattern> patterns = miner.mine();
    assertThat(patterns).isNotEmpty();
    // Should find a, b, and a->b patterns
    boolean foundAB = patterns.stream()
        .anyMatch(p -> p.sequence().contains("a") && p.sequence().contains("b"));
    assertThat(foundAB).isTrue();
  }

  @Test
  void testMineWithMinimumSupport() {
    // 50% threshold: only patterns in 2+ sequences
    miner = new FrequentSequenceMiner(0.5);
    miner.addSequence(List.of("login", "logout"));
    miner.addSequence(List.of("login", "logout"));
    miner.addSequence(List.of("login", "error"));

    List<FrequentSequenceMiner.Pattern> patterns = miner.mine();
    assertThat(patterns).isNotEmpty();
    // login->logout should be found (support 66%)
    boolean foundLoginLogout = patterns.stream()
        .anyMatch(p -> p.support() >= 0.5);
    assertThat(foundLoginLogout).isTrue();
  }

  @Test
  void testConfidenceCalculation() {
    // login -> access occurs in 2 out of 3 sequences with login
    miner.addSequence(List.of("login", "access", "logout"));
    miner.addSequence(List.of("login", "access", "logout"));
    miner.addSequence(List.of("login", "error", "logout"));

    List<FrequentSequenceMiner.Pattern> patterns = miner.mine();
    assertThat(patterns).isNotEmpty();
    // Should find login (100%), access (66%), error (33%), etc.
    assertThat(patterns.stream().anyMatch(p -> p.confidence() > 0)).isTrue();
  }

  @Test
  void testMineWithConfidenceFilter() {
    miner.addSequence(List.of("a", "b"));
    miner.addSequence(List.of("a", "b"));
    miner.addSequence(List.of("a", "c"));

    // Filter for 70% confidence
    List<FrequentSequenceMiner.Pattern> patterns = miner.mineWithConfidence(0.7);
    assertThat(patterns).isNotNull();
    // All returned patterns should have confidence >= 0.7
    for (FrequentSequenceMiner.Pattern p : patterns) {
      assertThat(p.confidence()).isGreaterThanOrEqualTo(0.7);
    }
  }

  @Test
  void testMineReturnsEmptyWhenNoFrequent() {
    // All unique sequences with no repetition = no frequent patterns with 50% min support
    miner = new FrequentSequenceMiner(0.5);
    miner.addSequence(List.of("a", "b"));
    miner.addSequence(List.of("c", "d"));
    miner.addSequence(List.of("e", "f"));

    List<FrequentSequenceMiner.Pattern> patterns = miner.mine();
    assertThat(patterns).isEmpty();
  }

  @Test
  void testPatternRecord() {
    FrequentSequenceMiner.Pattern pattern =
        new FrequentSequenceMiner.Pattern(
            "login -> access",
            List.of("login", "access"),
            0.75,
            0.85,
            150);

    assertThat(pattern.sequence()).isEqualTo("login -> access");
    assertThat(pattern.support()).isEqualTo(0.75);
    assertThat(pattern.confidence()).isEqualTo(0.85);
    assertThat(pattern.occurrences()).isEqualTo(150);
  }

  @Test
  void testPatternToString() {
    FrequentSequenceMiner.Pattern pattern =
        new FrequentSequenceMiner.Pattern(
            "login -> access",
            List.of("login", "access"),
            0.75,
            0.85,
            150);

    String str = pattern.toString();
    assertThat(str)
        .contains("login")
        .contains("access")
        .contains("sup:")
        .contains("conf:");
  }

  @Test
  void testRejectsInvalidMinSupport() {
    assertThatThrownBy(() -> new FrequentSequenceMiner(-0.1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Minimum support must be between 0 and 1");
  }

  @Test
  void testRejectsNullSequence() {
    assertThatThrownBy(() -> miner.addSequence(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void testRejectsEmptySequence() {
    assertThatThrownBy(() -> miner.addSequence(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void testGetMinSupport() {
    assertThat(miner.getMinSupport()).isEqualTo(0.3);
  }

  @Test
  void testGetSequenceCount() {
    assertThat(miner.getSequenceCount()).isZero();
    miner.addSequence(List.of("a", "b"));
    assertThat(miner.getSequenceCount()).isOne();
  }

  @Test
  void testComplexPattern() {
    // Real-world pattern: login -> [access|error] -> [logout|timeout]
    miner = new FrequentSequenceMiner(0.4); // 40% support
    for (int i = 0; i < 10; i++) {
      miner.addSequence(List.of("login", "access", "logout"));
      miner.addSequence(List.of("login", "access", "timeout"));
      miner.addSequence(List.of("login", "error", "logout"));
    }

    List<FrequentSequenceMiner.Pattern> patterns = miner.mine();
    assertThat(patterns).isNotEmpty();
    // Should find login (100%), access (66%), logout (66%), etc.
    long highConfidencePatterns = patterns.stream()
        .filter(p -> p.confidence() >= 0.6)
        .count();
    assertThat(highConfidencePatterns).isGreaterThan(0);
  }

  @Test
  void testEmptyMineBeforeSequences() {
    List<FrequentSequenceMiner.Pattern> patterns = miner.mine();
    assertThat(patterns).isEmpty();
  }

  @Test
  void testLongSequenceProcessing() {
    // Test with longer sequences
    miner.addSequence(List.of("a", "b", "c", "d", "e", "f"));
    miner.addSequence(List.of("a", "b", "c", "d", "e", "f"));
    miner.addSequence(List.of("a", "b", "c", "d", "e", "g"));

    List<FrequentSequenceMiner.Pattern> patterns = miner.mine();
    assertThat(patterns).isNotEmpty();
  }
}
