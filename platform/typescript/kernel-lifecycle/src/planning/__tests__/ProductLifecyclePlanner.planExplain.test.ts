/**
 * Tests for PlanExplain generation in ProductLifecyclePlanner.
 *
 * @doc.type test
 * @doc.purpose Conformance tests for PlanExplain generation with dependency graph, provider checks, gate checks, artifact expectations, approval policy, and environment preflight
 * @doc.layer kernel-lifecycle
 * @doc.pattern ConformanceTest
 */

import { describe, expect, it } from "vitest";
import { ProductLifecyclePlanner } from "../ProductLifecyclePlanner";
import type { ProductLifecyclePlan } from "../../domain/ProductLifecyclePhase";

describe("ProductLifecyclePlanner PlanExplain generation", () => {
  let planner: ProductLifecyclePlanner;

  beforeEach(() => {
    planner = new ProductLifecyclePlanner("/Users/samujjwal/Development/ghatana");
  });

  it("generates PlanExplain with dependency graph", async () => {
    const plan = await planner.plan("digital-marketing", "build", { shapeOnly: true });
    const planExplain = await planner.generatePlanExplain(plan);

    expect(planExplain.dependencyGraph).toBeDefined();
    expect(planExplain.dependencyGraph.nodes).toBeInstanceOf(Array);
    expect(planExplain.dependencyGraph.edges).toBeInstanceOf(Array);
    expect(planExplain.dependencyGraph.criticalPath).toBeInstanceOf(Array);
  });

  it("generates PlanExplain with provider checks", async () => {
    const plan = await planner.plan("digital-marketing", "build", { shapeOnly: true });
    const planExplain = await planner.generatePlanExplain(plan);

    expect(planExplain.providerChecks).toBeDefined();
    expect(["healthy", "unknown"]).toContain(planExplain.providerChecks.overallStatus);
    expect(planExplain.providerChecks.totalProviders).toBeGreaterThan(0);
    expect(planExplain.providerChecks.checks).toBeInstanceOf(Array);
  });

  it("generates PlanExplain with gate checks", async () => {
    const plan = await planner.plan("digital-marketing", "build", { shapeOnly: true });
    const planExplain = await planner.generatePlanExplain(plan);

    expect(planExplain.gateChecks).toBeDefined();
    expect(planExplain.gateChecks.overallStatus).toBe("pending");
    expect(planExplain.gateChecks.totalGates).toBeGreaterThanOrEqual(0);
    expect(planExplain.gateChecks.checks).toBeInstanceOf(Array);
  });

  it("generates PlanExplain with artifact expectations", async () => {
    const plan = await planner.plan("digital-marketing", "build", { shapeOnly: true });
    const planExplain = await planner.generatePlanExplain(plan);

    expect(planExplain.artifactExpectations).toBeDefined();
    expect(planExplain.artifactExpectations.totalArtifacts).toBeGreaterThanOrEqual(0);
    expect(planExplain.artifactExpectations.expectations).toBeInstanceOf(Array);
  });

  it("generates PlanExplain with approval policy", async () => {
    const plan = await planner.plan("digital-marketing", "build", { shapeOnly: true });
    const planExplain = await planner.generatePlanExplain(plan);

    expect(planExplain.approvalPolicy).toBeDefined();
    expect(planExplain.approvalPolicy?.policyId).toContain("approval-");
    expect(["manual", "automatic"]).toContain(planExplain.approvalPolicy?.policyKind);
  });

  it("generates PlanExplain with approval status", async () => {
    const plan = await planner.plan("digital-marketing", "build", { shapeOnly: true });
    const planExplain = await planner.generatePlanExplain(plan);

    expect(planExplain.approvalStatus).toBeDefined();
    expect(planExplain.approvalStatus?.status).toBe("pending");
    expect(planExplain.approvalStatus?.requestedAt).toBeDefined();
  });

  it("generates PlanExplain with environment preflight", async () => {
    const plan = await planner.plan("digital-marketing", "build", { shapeOnly: true });
    const planExplain = await planner.generatePlanExplain(plan);

    expect(planExplain.environmentPreflight).toBeDefined();
    expect(planExplain.environmentPreflight?.environmentName).toBe("local");
    expect(planExplain.environmentPreflight?.overallStatus).toBe("ready");
    expect(planExplain.environmentPreflight?.checks).toBeInstanceOf(Array);
  });

  it("generates PlanExplain with overall readiness", async () => {
    const plan = await planner.plan("digital-marketing", "build", { shapeOnly: true });
    const planExplain = await planner.generatePlanExplain(plan);

    expect(planExplain.overallReadiness).toBe("ready" || "not-ready");
  });

  it("generates PlanExplain with blocking reasons when not ready", async () => {
    const plan = await planner.plan("digital-marketing", "build", { shapeOnly: true });
    const planExplain = await planner.generatePlanExplain(plan, [
      { providerId: "test-adapter", healthy: false, capabilities: [] },
    ]);

    if (planExplain.overallReadiness === "not-ready") {
      expect(planExplain.blockingReasons).toBeDefined();
      expect(planExplain.blockingReasons).toBeInstanceOf(Array);
      expect(planExplain.blockingReasons?.length).toBeGreaterThan(0);
    }
  });

  it("generates PlanExplain with warnings", async () => {
    const plan = await planner.plan("digital-marketing", "build", { shapeOnly: true });
    const planExplain = await planner.generatePlanExplain(plan);

    expect(planExplain.warnings).toBeDefined();
    expect(planExplain.warnings).toBeInstanceOf(Array);
  });

  it("generates PlanExplain with estimated duration", async () => {
    const plan = await planner.plan("digital-marketing", "build", { shapeOnly: true });
    const planExplain = await planner.generatePlanExplain(plan);

    expect(planExplain.estimatedTotalDurationMs).toBeGreaterThanOrEqual(0);
  });

  it("generates PlanExplain with correct schema version", async () => {
    const plan = await planner.plan("digital-marketing", "build", { shapeOnly: true });
    const planExplain = await planner.generatePlanExplain(plan);

    expect(planExplain.schemaVersion).toBe("1.0.0");
  });

  it("generates PlanExplain with run and correlation IDs", async () => {
    const plan = await planner.plan("digital-marketing", "build", { shapeOnly: true });
    const planExplain = await planner.generatePlanExplain(plan);

    expect(planExplain.runId).toBe(plan.runId);
    expect(planExplain.correlationId).toBe(plan.correlationId);
  });

  it("generates PlanExplain with generated timestamp", async () => {
    const plan = await planner.plan("digital-marketing", "build", { shapeOnly: true });
    const planExplain = await planner.generatePlanExplain(plan);

    expect(planExplain.generatedAt).toBeDefined();
    expect(new Date(planExplain.generatedAt)).toBeInstanceOf(Date);
  });
});
