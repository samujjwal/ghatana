import { beforeEach, describe, expect, it, vi } from "vitest";
import type { PrismaClient } from "@tutorputor/core/db";
import { ABTestingService } from "./service";

function makePrisma() {
  const experiments: any[] = [];
  const assignments: any[] = [];
  const observations: any[] = [];

  return {
    _state: { experiments, assignments, observations },
    aBExperiment: {
      create: vi.fn(async ({ data }) => {
        const record = { id: `exp-${experiments.length + 1}`, ...data };
        experiments.push(record);
        return record;
      }),
      findFirst: vi.fn(async ({ where }) =>
        experiments.find(
          (experiment) =>
            experiment.id === where.id && experiment.tenantId === where.tenantId,
        ) ?? null,
      ),
      findMany: vi.fn(async ({ where }) =>
        experiments.filter((experiment) => experiment.tenantId === where.tenantId),
      ),
      update: vi.fn(async ({ where, data }) => {
        const index = experiments.findIndex((experiment) => experiment.id === where.id);
        experiments[index] = { ...experiments[index], ...data };
        return experiments[index];
      }),
    },
    aBExperimentAssignment: {
      findUnique: vi.fn(async ({ where }) =>
        assignments.find(
          (assignment) =>
            assignment.experimentId === where.experimentId_userId.experimentId &&
            assignment.userId === where.experimentId_userId.userId,
        ) ?? null,
      ),
      create: vi.fn(async ({ data }) => {
        const record = { id: `assign-${assignments.length + 1}`, ...data };
        assignments.push(record);
        return record;
      }),
      update: vi.fn(async ({ where, data }) => {
        const index = assignments.findIndex((assignment) => assignment.id === where.id);
        assignments[index] = { ...assignments[index], ...data };
        return assignments[index];
      }),
    },
    aBExperimentObservation: {
      create: vi.fn(async ({ data }) => {
        const record = { id: `obs-${observations.length + 1}`, ...data };
        observations.push(record);
        return record;
      }),
      findMany: vi.fn(async ({ where }) =>
        observations.filter(
          (observation) =>
            observation.experimentId === where.experimentId &&
            (where.tenantId ? observation.tenantId === where.tenantId : true),
        ),
      ),
    },
    learningExperience: {
      update: vi.fn(async ({ where, data }) => ({
        id: where.id,
        ...data,
      })),
    },
  } as unknown as PrismaClient & { _state: { experiments: any[]; assignments: any[]; observations: any[] } };
}

describe("ABTestingService", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let service: ABTestingService;

  beforeEach(() => {
    prisma = makePrisma();
    service = new ABTestingService(prisma);
  });

  it("creates and starts experiments", async () => {
    const experiment = await service.createExperienceExperiment("tenant-1", {
      experienceId: "experience-1",
      controlVersion: 1,
      treatmentVersion: 2,
      notes: "Hypothesis",
    });

    expect(experiment.status).toBe("draft");

    const started = await service.startExperiment("tenant-1", experiment.id);
    expect(started.status).toBe("running");
  });

  it("assigns a stable variant per user", async () => {
    const experiment = await service.createExperienceExperiment("tenant-1", {
      experienceId: "experience-1",
      controlVersion: 1,
      treatmentVersion: 2,
    });

    const first = await service.assignVariant("tenant-1", experiment.id, "user-1");
    const second = await service.assignVariant("tenant-1", experiment.id, "user-1");

    expect(first).toBe(second);
    expect(prisma._state.assignments).toHaveLength(1);
  });

  it("records observations and computes results", async () => {
    const experiment = await service.createExperienceExperiment("tenant-1", {
      experienceId: "experience-1",
      controlVersion: 1,
      treatmentVersion: 2,
    });

    const users = ["user-1", "user-2", "user-3", "user-4", "user-5", "user-6"];
    for (const userId of users) {
      const variant = await service.assignVariant("tenant-1", experiment.id, userId);
      await service.recordObservation("tenant-1", experiment.id, userId, {
        metricValue: variant === "treatment" ? 0.9 : 0.5,
        completed: variant === "treatment",
        masteryScore: variant === "treatment" ? 0.85 : 0.55,
        feedbackScore: variant === "treatment" ? 0.9 : 0.45,
      });
    }

    const results = await service.calculateResults("tenant-1", experiment.id);

    expect(results.control.sampleSize + results.treatment.sampleSize).toBe(6);
    expect(results.treatment.mean).toBeGreaterThanOrEqual(results.control.mean);

    const completed = await service.completeExperiment("tenant-1", experiment.id);
    expect(completed.status).toBe("completed");
  });

  it("evaluates active experiments and auto-promotes strong winners", async () => {
    const experiment = await service.createExperienceExperiment("tenant-1", {
      experienceId: "experience-2",
      controlVersion: 1,
      treatmentVersion: 2,
    });
    await service.startExperiment("tenant-1", experiment.id);

    for (let index = 0; index < 40; index++) {
      const userId = `user-${index}`;
      const variant = await service.assignVariant("tenant-1", experiment.id, userId);
      await service.recordObservation("tenant-1", experiment.id, userId, {
        metricValue: variant === "treatment" ? 0.95 : 0.45,
        completed: variant === "treatment",
        masteryScore: variant === "treatment" ? 0.9 : 0.4,
        feedbackScore: variant === "treatment" ? 0.92 : 0.38,
      });
    }

    const evaluation = await service.evaluateActiveExperiments("tenant-1", {
      minSampleSize: 10,
      autoPromote: true,
      minRelativeImprovement: 0.05,
    });

    expect(evaluation.evaluated).toBe(1);
    expect(evaluation.promoted).toBe(1);
    expect(evaluation.results[0]?.status).toBe("winner_promoted");
    expect((prisma as any).learningExperience.update).toHaveBeenCalled();
  });
});
