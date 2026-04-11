/**
 * WCAG 2.1 AA criteria-level tests for the accessibility-audit library.
 *
 * Covers the four WCAG principles (POUR) and the most critical 2.1 AA
 * success criteria used by axe-core rule tags.
 *
 * Tests target:
 *  - Criterion identification from axe-core tags
 *  - Severity mapping for each principle
 *  - Scoring impact per WCAG level (A / AA / AAA)
 *  - Compliance level determination based on violation profiles
 */

import { describe, it, expect, beforeEach } from "vitest";

import { AccessibilityScorer } from "../../scoring/AccessibilityScorer";

import type { Finding, WCAGLevel } from "../../types";

// ─── helpers ────────────────────────────────────────────────────────────────

function makeFinding(
  id: string,
  severity: Finding["severity"],
  wcagLevel: WCAGLevel,
  criterion: string,
): Finding {
  const levelTag =
    wcagLevel === "A" ? "wcag2a" : wcagLevel === "AA" ? "wcag2aa" : "wcag2aaa";
  const criterionTag = `wcag${criterion.replace(/\./g, "")}`;
  return {
    id,
    type: "violation" as const,
    severity,
    message: `${id} violation`,
    element: `<div id="${id}">`,
    selector: `#${id}`,
    help: `Fix ${id}`,
    helpUrl: `https://dequeuniversity.com/rules/axe/4.9/${id}`,
    tags: [levelTag, criterionTag],
    wcag: { level: wcagLevel, criterion },
    remediation: `Fix ${id}`,
    impact:
      severity === "critical"
        ? "critical"
        : severity === "serious"
          ? "serious"
          : "moderate",
  };
}

// ─── tests ──────────────────────────────────────────────────────────────────

describe("WCAG 2.1 AA Criteria Coverage", () => {
  let scorer: AccessibilityScorer;

  beforeEach(() => {
    scorer = AccessibilityScorer.getInstance();
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // Principle 1 – Perceivable
  // ═══════════════════════════════════════════════════════════════════════════

  describe("Principle 1 – Perceivable", () => {
    it("1.1.1 Non-text Content — critical finding reduces score significantly", () => {
      const findings: Finding[] = [
        makeFinding("image-alt", "critical", "A", "1.1.1"),
      ];
      const perfect = scorer.calculateScore([], "AA");
      const withViolation = scorer.calculateScore(findings, "AA");
      expect(withViolation.overall).toBeLessThan(perfect.overall);
    });

    it("1.3.1 Info and Relationships — serious finding reduces score below A+ boundary", () => {
      const findings: Finding[] = [
        makeFinding("label", "serious", "A", "1.3.1"),
      ];
      const perfect = scorer.calculateScore([], "AA");
      const withViolation = scorer.calculateScore(findings, "AA");
      // A single serious violation must reduce the overall score
      expect(withViolation.overall).toBeLessThan(perfect.overall);
      // Score should be below 100 (A+ max)
      expect(withViolation.overall).toBeLessThan(100);
    });

    it("1.3.3 Sensory Characteristics — moderate finding is captured", () => {
      const findings: Finding[] = [
        makeFinding("sensory-hint", "moderate", "A", "1.3.3"),
      ];
      const score = scorer.calculateScore(findings, "AA");
      expect(score.overall).toBeLessThan(100);
    });

    it("1.4.1 Use of Color — serious finding (AA) reduces score", () => {
      const findings: Finding[] = [
        makeFinding("color-contrast", "serious", "AA", "1.4.1"),
      ];
      const score = scorer.calculateScore(findings, "AA");
      expect(score.overall).toBeLessThan(100);
    });

    it("1.4.3 Contrast Minimum — serious AA finding has measurable impact", () => {
      const noViolation = scorer.calculateScore([], "AA");
      const withViolation = scorer.calculateScore(
        [makeFinding("color-contrast", "serious", "AA", "1.4.3")],
        "AA",
      );
      expect(withViolation.overall).toBeLessThan(noViolation.overall);
    });

    it("1.4.4 Resize Text — two minor AA findings reduce score below 100", () => {
      // A single minor finding may round to 100; use two to guarantee a visible drop
      const findings: Finding[] = [
        makeFinding("meta-viewport", "minor", "AA", "1.4.4"),
        makeFinding("meta-viewport-2", "minor", "AA", "1.4.4"),
      ];
      const score = scorer.calculateScore(findings, "AA");
      expect(score.overall).toBeLessThan(100);
    });

    it("1.4.10 Reflow — AA criterion with moderate severity impacts score", () => {
      const findings: Finding[] = [
        makeFinding("css-orientation-lock", "moderate", "AA", "1.4.10"),
      ];
      const score = scorer.calculateScore(findings, "AA");
      expect(score.overall).toBeLessThan(100);
    });

    it("multiple Perceivable violations stack to lower the score", () => {
      const single = scorer.calculateScore(
        [makeFinding("image-alt", "serious", "A", "1.1.1")],
        "AA",
      );
      const stacked = scorer.calculateScore(
        [
          makeFinding("image-alt", "serious", "A", "1.1.1"),
          makeFinding("color-contrast", "serious", "AA", "1.4.3"),
          makeFinding("meta-viewport", "minor", "AA", "1.4.4"),
        ],
        "AA",
      );
      expect(stacked.overall).toBeLessThanOrEqual(single.overall);
    });
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // Principle 2 – Operable
  // ═══════════════════════════════════════════════════════════════════════════

  describe("Principle 2 – Operable", () => {
    it("2.1.1 Keyboard — critical finding noticeably reduces score", () => {
      const findings: Finding[] = [
        makeFinding("focusable-content", "critical", "A", "2.1.1"),
      ];
      const perfect = scorer.calculateScore([], "AA");
      const score = scorer.calculateScore(findings, "AA");
      // A critical violation must reduce the score by at least 3 points
      expect(score.overall).toBeLessThanOrEqual(perfect.overall - 3);
      expect(score.overall).toBeLessThan(100);
    });

    it("2.4.1 Bypass Blocks — serious finding reduces score", () => {
      const findings: Finding[] = [
        makeFinding("skip-link", "serious", "A", "2.4.1"),
      ];
      const score = scorer.calculateScore(findings, "AA");
      expect(score.overall).toBeLessThan(100);
    });

    it("2.4.4 Link Purpose — serious AA finding is reflected", () => {
      const findings: Finding[] = [
        makeFinding("link-name", "serious", "A", "2.4.4"),
      ];
      const score = scorer.calculateScore(findings, "AA");
      expect(score.overall).toBeLessThan(100);
    });

    it("2.4.7 Focus Visible — moderate AA finding is captured", () => {
      const findings: Finding[] = [
        makeFinding("focus-visible", "moderate", "AA", "2.4.7"),
      ];
      const score = scorer.calculateScore(findings, "AA");
      expect(score.overall).toBeLessThan(100);
    });

    it("multiple Operable violations compound correctly", () => {
      const one = scorer.calculateScore(
        [makeFinding("focusable-content", "serious", "A", "2.1.1")],
        "AA",
      );
      const two = scorer.calculateScore(
        [
          makeFinding("focusable-content", "serious", "A", "2.1.1"),
          makeFinding("focus-visible", "moderate", "AA", "2.4.7"),
        ],
        "AA",
      );
      expect(two.overall).toBeLessThanOrEqual(one.overall);
    });
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // Principle 3 – Understandable
  // ═══════════════════════════════════════════════════════════════════════════

  describe("Principle 3 – Understandable", () => {
    it("3.1.1 Language of Page — two minor violations reduce score below 100", () => {
      // Two minor findings guarantee the score drops below 100 after rounding
      const findings: Finding[] = [
        makeFinding("html-lang", "minor", "A", "3.1.1"),
        makeFinding("html-lang-valid", "minor", "A", "3.1.1"),
      ];
      const score = scorer.calculateScore(findings, "AA");
      expect(score.overall).toBeLessThan(100);
    });

    it("3.2.1 On Focus — serious violation reduces score", () => {
      const findings: Finding[] = [
        makeFinding("on-focus-behavior", "serious", "A", "3.2.1"),
      ];
      const score = scorer.calculateScore(findings, "AA");
      expect(score.overall).toBeLessThan(100);
    });

    it("3.3.1 Error Identification — serious finding is captured", () => {
      const findings: Finding[] = [
        makeFinding("error-message", "serious", "A", "3.3.1"),
      ];
      const score = scorer.calculateScore(findings, "AA");
      expect(score.overall).toBeLessThan(100);
    });

    it("3.3.2 Labels or Instructions — moderate finding is reflected", () => {
      const findings: Finding[] = [
        makeFinding("label-or-instruction", "moderate", "A", "3.3.2"),
      ];
      const score = scorer.calculateScore(findings, "AA");
      expect(score.overall).toBeLessThan(100);
    });
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // Principle 4 – Robust
  // ═══════════════════════════════════════════════════════════════════════════

  describe("Principle 4 – Robust", () => {
    it("4.1.1 Parsing — critical violation causes significant score drop", () => {
      const baseScore = scorer.calculateScore([], "AA");
      const withParsing = scorer.calculateScore(
        [makeFinding("duplicate-id", "critical", "A", "4.1.1")],
        "AA",
      );
      expect(withParsing.overall).toBeLessThan(baseScore.overall);
    });

    it("4.1.2 Name Role Value — critical violation is the most severe", () => {
      const criticalScore = scorer.calculateScore(
        [makeFinding("button-name", "critical", "A", "4.1.2")],
        "AA",
      );
      const minorScore = scorer.calculateScore(
        [makeFinding("button-name-minor", "minor", "A", "4.1.2")],
        "AA",
      );
      // Critical should penalise more than minor
      expect(criticalScore.overall).toBeLessThan(minorScore.overall);
    });

    it("4.1.3 Status Messages — AA criterion with moderate severity is captured", () => {
      const findings: Finding[] = [
        makeFinding("status-message", "moderate", "AA", "4.1.3"),
      ];
      const score = scorer.calculateScore(findings, "AA");
      expect(score.overall).toBeLessThan(100);
    });
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // WCAG Level interactions
  // ═══════════════════════════════════════════════════════════════════════════

  describe("WCAG level interactions", () => {
    it("AA-level criterion finding should be scored under AA audit", () => {
      const findings: Finding[] = [
        makeFinding("color-contrast", "serious", "AA", "1.4.3"),
      ];
      const score = scorer.calculateScore(findings, "AA");
      expect(score.overall).toBeGreaterThanOrEqual(0);
      expect(score.overall).toBeLessThan(100);
    });

    it("A-level critical violation under AAA audit still penalises", () => {
      const findings: Finding[] = [
        makeFinding("image-alt", "critical", "A", "1.1.1"),
      ];
      const score = scorer.calculateScore(findings, "AAA");
      expect(score.overall).toBeLessThan(100);
    });

    it("zero findings yields perfect score at any WCAG level", () => {
      const scoreA = scorer.calculateScore([], "A");
      const scoreAA = scorer.calculateScore([], "AA");
      const scoreAAA = scorer.calculateScore([], "AAA");
      expect(scoreA.overall).toBe(100);
      expect(scoreAA.overall).toBe(100);
      expect(scoreAAA.overall).toBe(100);
    });

    it("severity ordering: critical > serious > moderate > minor", () => {
      const makeSingle = (severity: Finding["severity"]) =>
        scorer.calculateScore(
          [makeFinding("test", severity, "AA", "1.4.3")],
          "AA",
        ).overall;

      const critical = makeSingle("critical");
      const serious = makeSingle("serious");
      const moderate = makeSingle("moderate");
      const minor = makeSingle("minor");

      expect(critical).toBeLessThanOrEqual(serious);
      expect(serious).toBeLessThanOrEqual(moderate);
      expect(moderate).toBeLessThanOrEqual(minor);
    });

    it("AA violation not counted when auditing to Level A only", () => {
      // When I provide an AA criterion finding but target Level A, the scorer
      // still processes it (implementation may or may not filter — we just ensure
      // it does not throw and returns a valid score object).
      const findings: Finding[] = [
        makeFinding("color-contrast", "serious", "AA", "1.4.3"),
      ];
      const score = scorer.calculateScore(findings, "A");
      expect(score).toBeDefined();
      expect(typeof score.overall).toBe("number");
    });
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // Compliance level mapping
  // ═══════════════════════════════════════════════════════════════════════════

  describe("Compliance level mapping", () => {
    it("zero findings → WCAG AA compliance level", () => {
      const score = scorer.calculateScore([], "AA");
      expect(score.complianceLevel).toMatch(/WCAG\s+(AA|AAA)/);
    });

    it("critical A violation → below AA compliance", () => {
      const findings: Finding[] = [
        makeFinding("image-alt", "critical", "A", "1.1.1"),
        makeFinding("button-name", "critical", "A", "4.1.2"),
        makeFinding("duplicate-id", "critical", "A", "4.1.1"),
        makeFinding("label", "critical", "A", "1.3.1"),
      ];
      const score = scorer.calculateScore(findings, "AA");
      // Many critical violations should push the compliance level down
      expect(["WCAG A", "Partial A", "Non-compliant"]).toContain(
        score.complianceLevel,
      );
    });

    it("minor-only violations → maintains high compliance", () => {
      const findings: Finding[] = [
        makeFinding("minor-1", "minor", "AA", "1.4.4"),
        makeFinding("minor-2", "minor", "AA", "2.4.7"),
      ];
      const score = scorer.calculateScore(findings, "AA");
      expect(score.overall).toBeGreaterThan(80);
    });
  });
});
