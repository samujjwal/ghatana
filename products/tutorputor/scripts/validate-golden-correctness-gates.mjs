#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = path.resolve(process.cwd(), "products", "tutorputor");

const goldenSuites = [
  {
    category: "CBM scoring",
    file: "contracts/tests/cbm-scoring.test.ts",
    markers: [
      "expect(getCBMScore(true, 'high')).toBe(3)",
      "expect(getCBMScore(false, 'high')).toBe(-6)",
      "expect(normalizeCBMScore(0)).toBeCloseTo(6 / 9, 3)",
    ],
  },
  {
    category: "viva selection",
    file: "libs/tutorputor-core/src/kernel/engine/analytics/__tests__/VivaEngine.test.ts",
    markers: [
      "random",
      "anomaly",
      "overconfident_wrong",
      "speed_anomaly",
      "expect",
    ],
  },
  {
    category: "viva intervention workflow",
    file: "services/tutorputor-platform/src/modules/intervention/VivaInterventionWorkflow.test.ts",
    markers: [
      "anomaly-triggered viva",
      "remediation",
      "re-viva",
      "expect(queue).toHaveLength(10)",
      "random_sampling",
    ],
  },
  {
    category: "claim mastery",
    file: "libs/tutorputor-core/src/kernel/engine/analytics/__tests__/ClaimMastery.test.ts",
    markers: [
      "confident mastery maps",
      "critical misconception",
      "calculateClaimMastery",
      "expect(result.masteryScore)",
    ],
  },
  {
    category: "simulation output",
    file: "libs/tutorputor-simulation/src/engine/starter-correctness.test.ts",
    markers: [
      "matches deterministic seeded golden outputs",
      "starter-newton-cart",
      "starter-supply-demand-dynamics",
      "expect(result.outputValue, starterId).toBe(result.expectedValue)",
    ],
  },
  {
    category: "telemetry schema",
    file: "contracts/tests/telemetry-events.test.ts",
    markers: [
      "\"sim.capture\"",
      "\"assess.answer\"",
      "\"assist.hint\"",
      "\"ai.tutor.response\"",
      "expect(learningEvents.map((event) => event.type)).toEqual",
    ],
  },
  {
    category: "generated content validation",
    file: "services/tutorputor-platform/src/modules/content/evaluation/__tests__/unified-content-evaluator.test.ts",
    markers: [
      "overall_score",
      "publish_decision",
      "pedagogical_score",
      "factual_score",
      "simulation_score",
      "accessibility_score",
    ],
  },
  {
    category: "AI grading",
    file: "services/tutorputor-platform/src/modules/assessment/ai-grading/__tests__/AIGradingService.test.ts",
    markers: [
      "expect(result.scorePercent).toBe(90)",
      "expect(result.confidence).toBe(0.85)",
      "expect(stats.avgScorePercent).toBe(80)",
      "expect(stats.avgConfidence).toBe(0.75)",
    ],
  },
  {
    category: "dashboard metrics",
    file: "services/tutorputor-platform/src/modules/analytics/__tests__/TeacherAnalyticsService.test.ts",
    markers: [
      "getInstructorEvidenceDashboardTiles",
      "expect(tiles.brierScore).toBe(0.41)",
      "expect(tiles.masteryByClaim).toEqual",
      "expect(tiles.processScoreDistribution).toEqual",
      "expect(tiles.remediationCompletion).toEqual",
    ],
  },
];

const errors = [];

for (const suite of goldenSuites) {
  const absolutePath = path.join(root, suite.file);
  if (!fs.existsSync(absolutePath)) {
    errors.push(`Missing golden correctness suite for ${suite.category}: ${suite.file}`);
    continue;
  }

  const source = fs.readFileSync(absolutePath, "utf8");
  for (const marker of suite.markers) {
    if (!source.includes(marker)) {
      errors.push(`Golden correctness suite for ${suite.category} is missing marker: ${marker}`);
    }
  }

  if (!/\bto(Be|Equal|Contain|BeCloseTo|BeGreaterThan|BeLessThan)/.test(source)) {
    errors.push(`Golden correctness suite for ${suite.category} must use exact semantic assertions.`);
  }
}

if (errors.length > 0) {
  console.error("Golden-data semantic correctness validation failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Golden-data semantic correctness is covered for CBM, viva, mastery, simulation, telemetry, generated content, AI grading, and dashboard metrics.");
