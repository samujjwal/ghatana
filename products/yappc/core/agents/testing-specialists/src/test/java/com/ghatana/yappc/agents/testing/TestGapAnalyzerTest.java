package com.ghatana.yappc.agents.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("TestGapAnalyzer Tests [GH-90000]")
class TestGapAnalyzerTest {

  @TempDir Path tempDir;

  @Test
  @DisplayName("analyze parses jacoco xml and prioritizes risky uncovered methods [GH-90000]")
  void analyzeParsesJacocoXmlAndPrioritizesRiskyMethods() throws IOException { // GH-90000
    Path report = tempDir.resolve("jacoco.xml [GH-90000]");
    Files.writeString(report, sampleJacocoXml()); // GH-90000

    TestGapAnalyzer analyzer = new TestGapAnalyzer(); // GH-90000
    List<TestGapAnalyzer.TestGap> gaps = analyzer.analyze(report); // GH-90000

    assertThat(gaps).hasSize(2); // GH-90000
    assertThat(gaps.getFirst().methodId()).isEqualTo("com.example.Calculator#branchyMethod [GH-90000]");
    assertThat(gaps.getFirst().severity()).isEqualTo("high [GH-90000]");
    assertThat(gaps.getFirst().recommendation()).contains("Generate test for Calculator#branchyMethod [GH-90000]");
    assertThat(gaps.getFirst().summary()).contains("missed branches [GH-90000]");
  }

  @Test
  @DisplayName("analyzeXml returns empty when report has no missed coverage [GH-90000]")
  void analyzeXmlReturnsEmptyWhenNoCoverageGapsRemain() { // GH-90000
    TestGapAnalyzer analyzer = new TestGapAnalyzer(); // GH-90000

    List<TestGapAnalyzer.TestGap> gaps =
        analyzer.analyzeXml( // GH-90000
            "<report><package name=\"com/example\"><class name=\"com/example/Ready\"><method name=\"done\">"
                + "<counter type=\"LINE\" missed=\"0\" covered=\"5\"/>"
                + "<counter type=\"BRANCH\" missed=\"0\" covered=\"2\"/>"
                + "<counter type=\"COMPLEXITY\" missed=\"0\" covered=\"1\"/>"
                + "</method></class></package></report>");

    assertThat(gaps).isEmpty(); // GH-90000
  }

  @Test
  @DisplayName("analyze rejects missing report paths [GH-90000]")
  void analyzeRejectsMissingReportPaths() { // GH-90000
    TestGapAnalyzer analyzer = new TestGapAnalyzer(); // GH-90000

    assertThatThrownBy(() -> analyzer.analyze(tempDir.resolve("missing.xml [GH-90000]")))
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("must exist [GH-90000]");
  }

  @Test
  @DisplayName("analyzeXml rejects malformed xml [GH-90000]")
  void analyzeXmlRejectsMalformedXml() { // GH-90000
    TestGapAnalyzer analyzer = new TestGapAnalyzer(); // GH-90000

    assertThatThrownBy(() -> analyzer.analyzeXml("<report> [GH-90000]"))
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("Invalid Jacoco XML report [GH-90000]");
  }

  @Test
  @DisplayName("analyze handles blank reports and directory read failures [GH-90000]")
  void analyzeHandlesBlankReportsAndDirectoryReadFailures() throws IOException { // GH-90000
    TestGapAnalyzer analyzer = new TestGapAnalyzer(); // GH-90000
    Files.createDirectory(tempDir.resolve("report-dir [GH-90000]"));

    assertThatThrownBy(() -> analyzer.analyze(null)) // GH-90000
      .isInstanceOf(IllegalArgumentException.class) // GH-90000
      .hasMessageContaining("Jacoco report path must exist [GH-90000]");
    assertThat(analyzer.analyzeXml(null)).isEmpty(); // GH-90000
    assertThat(analyzer.analyzeXml("  [GH-90000]")).isEmpty();
    assertThatThrownBy(() -> analyzer.analyze(tempDir.resolve("report-dir [GH-90000]")))
        .isInstanceOf(IllegalStateException.class) // GH-90000
        .hasMessageContaining("Failed to read Jacoco report [GH-90000]");
  }

  @Test
  @DisplayName("analyzeXml handles zero-line methods missing counters and lower severities [GH-90000]")
  void analyzeXmlHandlesZeroLineMethodsMissingCountersAndLowerSeverities() { // GH-90000
    TestGapAnalyzer analyzer = new TestGapAnalyzer(); // GH-90000

    List<TestGapAnalyzer.TestGap> gaps =
        analyzer.analyzeXml( // GH-90000
            """
            <report>
              <package name="demo">
                <class name="PlainName">
                  <method name="noLines">
                    <counter type="COMPLEXITY" missed="0" covered="1"/>
                  </method>
                  <method name="mediumRisk">
                    <counter type="LINE" missed="1" covered="4"/>
                    <counter type="BRANCH" missed="0" covered="0"/>
                    <counter type="COMPLEXITY" missed="0" covered="6"/>
                  </method>
                  <method name="lowRisk">
                    <counter type="LINE" missed="1" covered="9"/>
                    <counter type="BRANCH" missed="0" covered="0"/>
                    <counter type="COMPLEXITY" missed="0" covered="1"/>
                  </method>
                </class>
              </package>
            </report>
            """);

    assertThat(gaps).hasSize(2); // GH-90000
    assertThat(gaps.get(0).severity()).isEqualTo("medium [GH-90000]");
    assertThat(gaps.get(0).recommendation()).contains("remaining missed line [GH-90000]");
    assertThat(gaps.get(1).severity()).isEqualTo("low [GH-90000]");
    assertThat(gaps.get(1).recommendation()).contains("remaining missed line [GH-90000]");
    assertThat(gaps.get(0).methodId()).startsWith("PlainName# [GH-90000]");
  }

  @Test
  @DisplayName("analyzeXml covers missed branch only and long uncovered path recommendations [GH-90000]")
  void analyzeXmlCoversMissedBranchOnlyAndLongUncoveredPaths() { // GH-90000
    TestGapAnalyzer analyzer = new TestGapAnalyzer(); // GH-90000

    List<TestGapAnalyzer.TestGap> gaps =
        analyzer.analyzeXml( // GH-90000
            """
            <report>
              <package name="demo">
                <class name="demo.MoreCoverage">
                  <method name="branchOnly">
                    <counter type="LINE" missed="0" covered="5"/>
                    <counter type="BRANCH" missed="1" covered="1"/>
                    <counter type="COMPLEXITY" missed="0" covered="2"/>
                  </method>
                  <method name="longPath">
                    <counter type="LINE" missed="4" covered="1"/>
                    <counter type="BRANCH" missed="0" covered="0"/>
                    <counter type="COMPLEXITY" missed="0" covered="1"/>
                  </method>
                </class>
              </package>
            </report>
            """);

    assertThat(gaps).hasSize(2); // GH-90000
  assertThat(gaps) // GH-90000
    .extracting(TestGapAnalyzer.TestGap::recommendation) // GH-90000
    .anySatisfy(recommendation -> assertThat(recommendation).contains("missed conditional branches [GH-90000]"))
    .anySatisfy(recommendation -> assertThat(recommendation).contains("currently untested execution path [GH-90000]"));
  }

  private String sampleJacocoXml() { // GH-90000
    return """
        <report name="sample">
          <package name="com/example">
            <class name="com/example/Calculator">
              <method name="branchyMethod">
                <counter type="LINE" missed="4" covered="1"/>
                <counter type="BRANCH" missed="2" covered="0"/>
                <counter type="COMPLEXITY" missed="1" covered="3"/>
              </method>
              <method name="lineOnlyMethod">
                <counter type="LINE" missed="2" covered="2"/>
                <counter type="BRANCH" missed="0" covered="0"/>
                <counter type="COMPLEXITY" missed="0" covered="1"/>
              </method>
              <method name="coveredMethod">
                <counter type="LINE" missed="0" covered="5"/>
                <counter type="BRANCH" missed="0" covered="2"/>
                <counter type="COMPLEXITY" missed="0" covered="1"/>
              </method>
            </class>
          </package>
        </report>
        """;
  }
}
