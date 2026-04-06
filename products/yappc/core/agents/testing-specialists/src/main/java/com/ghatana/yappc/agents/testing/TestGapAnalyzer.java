package com.ghatana.yappc.agents.testing;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * @doc.type class
 * @doc.purpose Identifies uncovered code paths by parsing Jacoco reports and prioritizing risky methods
 * @doc.layer product
 * @doc.pattern Analyzer
 */
public final class TestGapAnalyzer {

  public List<TestGap> analyze(Path jacocoXmlReport) {
    if (jacocoXmlReport == null || !Files.exists(jacocoXmlReport)) {
      throw new IllegalArgumentException("Jacoco report path must exist");
    }
    try {
      return analyzeXml(Files.readString(jacocoXmlReport));
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to read Jacoco report", exception);
    }
  }

  public List<TestGap> analyzeXml(String jacocoXml) {
    if (jacocoXml == null || jacocoXml.isBlank()) {
      return List.of();
    }
    Document document = parseDocument(jacocoXml);
    List<TestGap> gaps = new ArrayList<>();
    NodeList classes = document.getElementsByTagName("class");
    for (int classIndex = 0; classIndex < classes.getLength(); classIndex++) {
      Element classElement = (Element) classes.item(classIndex);
      String className = classElement.getAttribute("name").replace('/', '.');
      NodeList methods = classElement.getElementsByTagName("method");
      for (int methodIndex = 0; methodIndex < methods.getLength(); methodIndex++) {
        Element methodElement = (Element) methods.item(methodIndex);
        TestGap gap = toGap(className, methodElement);
        if (gap != null) {
          gaps.add(gap);
        }
      }
    }
    return gaps.stream()
        .sorted(Comparator.comparingDouble(TestGap::riskScore).reversed())
        .toList();
  }

  private Document parseDocument(String xml) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid Jacoco XML report", exception);
    }
  }

  private TestGap toGap(String className, Element methodElement) {
    Counter lineCounter = readCounter(methodElement, "LINE");
    Counter branchCounter = readCounter(methodElement, "BRANCH");
    Counter complexityCounter = readCounter(methodElement, "COMPLEXITY");

    int totalLines = lineCounter.covered() + lineCounter.missed();
    if (totalLines == 0) {
      return null;
    }

    double coverage = lineCounter.covered() / (double) totalLines;
    if (lineCounter.missed() == 0 && branchCounter.missed() == 0) {
      return null;
    }

    int complexity = Math.max(1, complexityCounter.covered() + complexityCounter.missed());
    double riskScore = (lineCounter.missed() * 2.0) + (branchCounter.missed() * 3.0) + complexity;
    String methodName = methodElement.getAttribute("name");
    String recommendation =
        "Generate test for "
            + simpleClassName(className)
            + "#"
            + methodName
            + suggestRecommendation(branchCounter.missed(), lineCounter.missed());

    return new TestGap(
        className,
        methodName,
        coverage,
        lineCounter.missed(),
        branchCounter.missed(),
        complexity,
        riskScore,
        recommendation);
  }

  private Counter readCounter(Element methodElement, String type) {
    NodeList counters = methodElement.getElementsByTagName("counter");
    for (int index = 0; index < counters.getLength(); index++) {
      Element counterElement = (Element) counters.item(index);
      if (type.equals(counterElement.getAttribute("type"))) {
        return new Counter(
            Integer.parseInt(counterElement.getAttribute("missed")),
            Integer.parseInt(counterElement.getAttribute("covered")));
      }
    }
    return new Counter(0, 0);
  }

  private String suggestRecommendation(int missedBranches, int missedLines) {
    if (missedBranches > 0) {
      return " covering the missed conditional branches";
    }
    if (missedLines > 3) {
      return " covering the currently untested execution path";
    }
    return " covering the remaining missed line";
  }

  private String simpleClassName(String className) {
    int index = className.lastIndexOf('.');
    return index >= 0 ? className.substring(index + 1) : className;
  }

  private record Counter(int missed, int covered) {}

  public record TestGap(
      String className,
      String methodName,
      double coverage,
      int missedLines,
      int missedBranches,
      int complexity,
      double riskScore,
      String recommendation) {

    public String methodId() {
      return className + "#" + methodName;
    }

    public String severity() {
      if (riskScore >= 15.0) {
        return "high";
      }
      if (riskScore >= 8.0) {
        return "medium";
      }
      return "low";
    }

    public String summary() {
      return String.format(
          Locale.ROOT,
          "%s has %.0f%% line coverage with %d missed lines and %d missed branches",
          methodId(),
          coverage * 100.0,
          missedLines,
          missedBranches);
    }
  }
}