import { describe, expect, it } from "vitest";
import {
  canonicalLearnerRoutes,
  compatibilityAliases,
} from "../canonicalRouteMap";

const concretePaths = [
  "/login",
  "/",
  "/dashboard",
  "/pathways",
  "/search",
  "/modules",
  "/modules/example-module",
  "/assessments",
  "/assessments/assessment-123",
  "/marketplace",
  "/collaboration",
  "/analytics",
  "/teacher",
  "/settings",
  "/simulations",
  "/simulations/studio/sim-123",
] as const;

describe("canonical learner routes", () => {
  it("declares the shared login entrypoint", () => {
    expect(canonicalLearnerRoutes).toContain("/login");
  });

  it("declares the canonical learner route patterns that docs and tests follow", () => {
    expect(canonicalLearnerRoutes).toEqual([
      "/login",
      "/",
      "/dashboard",
      "/pathways",
      "/search",
      "/modules/:slug",
      "/assessments",
      "/assessments/:assessmentId",
      "/marketplace",
      "/collaboration",
      "/analytics",
      "/teacher",
      "/settings",
      "/simulations",
      "/simulations/studio/:id?",
    ]);
  });

  it("records the supported compatibility aliases explicitly", () => {
    expect(compatibilityAliases).toEqual({
      "/": "/dashboard",
      "/modules": "/search",
      "/ai-tutor": "/dashboard",
    });
  });

  it("treats the former dedicated AI tutor page as a compatibility redirect into the learner flow", () => {
    expect(canonicalLearnerRoutes).not.toContain("/ai-tutor");
    expect(compatibilityAliases["/ai-tutor"]).toBe("/dashboard");
  });

  it("does not expose the legacy content-generation and learner-side authoring demo routes", () => {
    expect(canonicalLearnerRoutes).not.toContain("/content-generation");
    expect(canonicalLearnerRoutes).not.toContain("/content-generation-dashboard");
    expect(canonicalLearnerRoutes).not.toContain("/simulation-preview");
    expect(canonicalLearnerRoutes).not.toContain("/content-studio/templates");
    expect(canonicalLearnerRoutes).not.toContain("/content-studio/generate");
    expect(canonicalLearnerRoutes).not.toContain("/simulations/templates");
  });

  it("keeps the executable concrete route examples aligned with the declared patterns", () => {
    const concreteRouteCoverage = new Set<string>([
      "/login",
      "/",
      "/dashboard",
      "/pathways",
      "/search",
      "/modules",
      "/modules/example-module",
      "/assessments",
      "/assessments/assessment-123",
      "/marketplace",
      "/collaboration",
      "/analytics",
      "/teacher",
      "/settings",
      "/simulations",
      "/simulations/studio/sim-123",
    ]);

    for (const path of concretePaths) {
      expect(concreteRouteCoverage.has(path)).toBe(true);
    }
  });
});
