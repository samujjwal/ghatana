import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  GovernedContentDispatchGuard,
} from "../GovernedContentDispatchGuard";
import type { GenerationRequestExecutionJobData } from "../../../modules/content/generation/queue-dispatcher";

function makeJobData(
  overrides: Partial<GenerationRequestExecutionJobData> = {},
): GenerationRequestExecutionJobData {
  return {
    generationRequestId: "req-1",
    generationJobId: "job-1",
    tenantId: "tenant-1",
    requestedBy: "author-1",
    requestTitle: "Newton's Laws",
    domain: "physics",
    targetGrades: ["GRADE_9_12"],
    generationJobType: "claim",
    ...overrides,
  } as GenerationRequestExecutionJobData;
}

function makeLogger() {
  return {
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    debug: vi.fn(),
  } as unknown as import("pino").Logger;
}

describe("GovernedContentDispatchGuard", () => {
  describe("in hardened/production mode", () => {
    let guard: GovernedContentDispatchGuard;
    let logger: ReturnType<typeof makeLogger>;

    beforeEach(() => {
      logger = makeLogger();
      guard = new GovernedContentDispatchGuard({
        productReleaseStatus: "hardened",
        allowInCandidateMode: false,
        logger,
      });
    });

    it("allows a valid governed job type", () => {
      const decision = guard.evaluate(makeJobData({ generationJobType: "claim" }));
      expect(decision.allowed).toBe(true);
    });

    it("allows all governed job types", () => {
      const types = ["claim", "explainer", "worked_example", "simulation", "animation", "assessment", "evaluation"];
      for (const jobType of types) {
        const decision = guard.evaluate(makeJobData({ generationJobType: jobType as GenerationRequestExecutionJobData["generationJobType"] }));
        expect(decision.allowed).toBe(true);
      }
    });

    it("emits a structured audit log entry when dispatch is allowed", () => {
      guard.evaluate(makeJobData());
      expect(logger.info).toHaveBeenCalledWith(
        expect.objectContaining({
          event: "governed_content_dispatch_decision",
          verdict: "ALLOWED",
          tenantId: "tenant-1",
          generationJobType: "claim",
        }),
        expect.any(String),
      );
    });

    it("blocks an unknown job type with JOB_TYPE_NOT_GOVERNED", () => {
      const decision = guard.evaluate(makeJobData({ generationJobType: "unknown_type" as GenerationRequestExecutionJobData["generationJobType"] }));
      expect(decision.allowed).toBe(false);
      if (!decision.allowed) {
        expect(decision.code).toBe("JOB_TYPE_NOT_GOVERNED");
      }
    });

    it("blocks a job with empty tenantId with TENANT_MISSING", () => {
      const decision = guard.evaluate(makeJobData({ tenantId: "" }));
      expect(decision.allowed).toBe(false);
      if (!decision.allowed) {
        expect(decision.code).toBe("TENANT_MISSING");
      }
    });

    it("blocks a job with whitespace-only tenantId with TENANT_MISSING", () => {
      const decision = guard.evaluate(makeJobData({ tenantId: "   " }));
      expect(decision.allowed).toBe(false);
      if (!decision.allowed) {
        expect(decision.code).toBe("TENANT_MISSING");
      }
    });

    it("emits a warn log when dispatch is blocked", () => {
      guard.evaluate(makeJobData({ tenantId: "" }));
      expect(logger.warn).toHaveBeenCalledWith(
        expect.objectContaining({
          event: "governed_content_dispatch_blocked",
          code: "TENANT_MISSING",
        }),
        expect.any(String),
      );
    });
  });

  describe("in candidate mode with allowInCandidateMode=false", () => {
    let guard: GovernedContentDispatchGuard;

    beforeEach(() => {
      guard = new GovernedContentDispatchGuard({
        productReleaseStatus: "candidate",
        allowInCandidateMode: false,
        logger: makeLogger(),
      });
    });

    it("blocks all dispatches with RELEASE_GUARD_CANDIDATE", () => {
      const decision = guard.evaluate(makeJobData());
      expect(decision.allowed).toBe(false);
      if (!decision.allowed) {
        expect(decision.code).toBe("RELEASE_GUARD_CANDIDATE");
        expect(decision.reason).toMatch(/candidate/i);
      }
    });
  });

  describe("in candidate mode with allowInCandidateMode=true", () => {
    let guard: GovernedContentDispatchGuard;

    beforeEach(() => {
      guard = new GovernedContentDispatchGuard({
        productReleaseStatus: "candidate",
        allowInCandidateMode: true,
        logger: makeLogger(),
      });
    });

    it("allows dispatch when candidate mode is explicitly permitted (integration/dev override)", () => {
      const decision = guard.evaluate(makeJobData());
      expect(decision.allowed).toBe(true);
    });
  });

  describe("priority of checks", () => {
    it("TENANT_MISSING takes precedence over RELEASE_GUARD_CANDIDATE", () => {
      const guard = new GovernedContentDispatchGuard({
        productReleaseStatus: "candidate",
        allowInCandidateMode: false,
        logger: makeLogger(),
      });
      const decision = guard.evaluate(makeJobData({ tenantId: "" }));
      expect(decision.allowed).toBe(false);
      if (!decision.allowed) {
        expect(decision.code).toBe("TENANT_MISSING");
      }
    });

    it("JOB_TYPE_NOT_GOVERNED takes precedence over RELEASE_GUARD_CANDIDATE", () => {
      const guard = new GovernedContentDispatchGuard({
        productReleaseStatus: "candidate",
        allowInCandidateMode: false,
        logger: makeLogger(),
      });
      const decision = guard.evaluate(makeJobData({ generationJobType: "not_a_type" as GenerationRequestExecutionJobData["generationJobType"] }));
      expect(decision.allowed).toBe(false);
      if (!decision.allowed) {
        expect(decision.code).toBe("JOB_TYPE_NOT_GOVERNED");
      }
    });
  });
});
