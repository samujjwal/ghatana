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

@DisplayName("TestGapAnalyzer Tests")
class TestGapAnalyzerTest {

  @TempDir Path tempDir;

  @Test
  @DisplayName("analyze parses jacoco xml and prioritizes risky uncovered methods")
  void analyzeParsesJacocoXmlAndPrioritizesRiskyMethods() throws IOException {
    Path report = tempDir.resolve("jacoco.xml");
    Files.writeString(report, sampleJacocoXml());

    TestGapAnalyzer analyzer = new TestGapAnalyzer();
    List<TestGapAnalyzer.TestGap> gaps = analyzer.analyze(report);

    assertThat(gaps).hasSize(2);
    assertThat(gaps.getFirst().methodId()).isEqualTo("com.example.Calculator#branchyMethod");
    assertThat(gaps.getFirst().severity()).isEqualTo("high");
    assertThat(gaps.getFirst().recommendation()).contains("Generate test for Calculator#branchyMethod");
    assertThat(gaps.getFirst().summary()).contains("missed branches");
  }

  @Test
  @DisplayName("analyzeXml returns empty when report has no missed coverage")
  void analyzeXmlReturnsEmptyWhenNoCoverageGapsRemain() {
    TestGapAnalyzer analyzer = new TestGapAnalyzer();

    List<TestGapAnalyzer.TestGap> gaps =
        analyzer.analyzeXml(
            "<report><package name=\"com/example\"><class name=\"com/example/Ready\"><method name=\"done\">"
                + "<counter type=\"LINE\" missed=\"0\" covered=\"5\"/>"
                + "<counter type=\"BRANCH\" missed=\"0\" covered=\"2\"/>"
                + "<counter type=\"COMPLEXITY\" missed=\"0\" covered=\"1\"/>"
                + "</method></class></package></report>");

    assertThat(gaps).isEmpty();
  }

  @Test
  @DisplayName("analyze rejects missing report paths")
  void analyzeRejectsMissingReportPaths() {
    TestGapAnalyzer analyzer = new TestGapAnalyzer();

    assertThatThrownBy(() -> analyzer.analyze(tempDir.resolve("missing.xml")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must exist");
  }

  @Test
  @DisplayName("analyzeXml rejects malformed xml")
  void analyzeXmlRejectsMalformedXml() {
    TestGapAnalyzer analyzer = new TestGapAnalyzer();

    assertThatThrownBy(() -> analyzer.analyzeXml("<report>"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid Jacoco XML report");
  }

  @Test
  @DisplayName("analyze handles blank reports and directory read failures")
  void analyzeHandlesBlankReportsAndDirectoryReadFailures() throws IOException {
    TestGapAnalyzer analyzer = new TestGapAnalyzer();
    Files.createDirectory(tempDir.resolve("report-dir"));

    assertThatThrownBy(() -> analyzer.analyze(null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Jacoco report path must exist");
    assertThat(analyzer.analyzeXml(null)).isEmpty();
    assertThat(analyzer.analyzeXml(" ")).isEmpty();
    assertThatThrownBy(() -> analyzer.analyze(tempDir.resolve("report-dir")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to read Jacoco report");
  }

  @Test
  @DisplayName("analyzeXml handles zero-line methods missing counters and lower severities")
  void analyzeXmlHandlesZeroLineMethodsMissingCountersAndLowerSeverities() {
    TestGapAnalyzer analyzer = new TestGapAnalyzer();

    List<TestGapAnalyzer.TestGap> gaps =
        analyzer.analyzeXml(
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

    assertThat(gaps).hasSize(2);
    assertThat(gaps.get(0).severity()).isEqualTo("medium");
    assertThat(gaps.get(0).recommendation()).contains("remaining missed line");
    assertThat(gaps.get(1).severity()).isEqualTo("low");
    assertThat(gaps.get(1).recommendation()).contains("remaining missed line");
    assertThat(gaps.get(0).methodId()).startsWith("PlainName#");
  }

  @Test
  @DisplayName("analyzeXml covers missed branch only and long uncovered path recommendations")
  void analyzeXmlCoversMissedBranchOnlyAndLongUncoveredPaths() {
    TestGapAnalyzer analyzer = new TestGapAnalyzer();

    List<TestGapAnalyzer.TestGap> gaps =
        analyzer.analyzeXml(
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

    assertThat(gaps).hasSize(2);
  assertThat(gaps)
    .extracting(TestGapAnalyzer.TestGap::recommendation)
    .anySatisfy(recommendation -> assertThat(recommendation).contains("missed conditional branches"))
    .anySatisfy(recommendation -> assertThat(recommendation).contains("currently untested execution path"));
  }

  private String sampleJacocoXml() {
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