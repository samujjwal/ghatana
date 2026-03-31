import { beforeEach, describe, expect, it, vi } from "vitest";
import type { PrismaClient } from "@tutorputor/core/db";
import {
  createSimulationAssessmentIntegration,
  scoreSimulationAssessmentResponse,
  summarizeSimulationAttempt,
} from "./service";

function makePrisma() {
  return {
    simulationManifest: {
      findMany: vi.fn(),
    },
  } as unknown as PrismaClient;
}

describe("SimulationAssessmentIntegration", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let service: ReturnType<typeof createSimulationAssessmentIntegration>;

  beforeEach(() => {
    prisma = makePrisma();
    service = createSimulationAssessmentIntegration(prisma);
  });

  it("creates simulation-backed assessment items for module manifests", async () => {
    (prisma.simulationManifest.findMany as any).mockResolvedValue([
      {
        id: "sim-1",
        title: "Gas Law Explorer",
        description: "Explore pressure, volume, and temperature relationships.",
        domain: "CHEMISTRY",
        moduleId: "module-1",
        manifest: {
          interactionType: "parameter_exploration",
          steps: [
            {
              actions: [
                { action: "adjust_pressure", parameterId: "pressure" },
                { action: "adjust_temperature", parameterId: "temperature" },
              ],
            },
          ],
        },
      },
    ]);

    const items = await service.createModuleAssessmentItems({
      tenantId: "tenant-1",
      moduleId: "module-1",
      count: 2,
      difficulty: "INTERMEDIATE",
      objectiveLabels: ["Relate pressure, volume, and temperature"],
    });

    expect(items).toHaveLength(1);
    expect(items[0]?.type).toBe("simulation_interaction");
    expect(items[0]?.metadata).toEqual(
      expect.objectContaining({
        simulationManifestId: "sim-1",
        expectedParameters: expect.arrayContaining(["pressure", "temperature"]),
      }),
    );
  });

  it("scores simulation traces from interaction coverage and explanation quality", () => {
    const result = scoreSimulationAssessmentResponse({
      item: {
        id: "sim-item-1",
        points: 10,
        metadata: {
          simulationManifestId: "sim-1",
          simulationTitle: "Gas Law Explorer",
          simulationDomain: "CHEMISTRY",
          questionType: "prediction",
          interactionType: "parameter_exploration",
          expectedParameters: ["pressure", "temperature"],
          expectedOutcomeKeywords: ["adjust pressure", "adjust temperature"],
          irt: { discrimination: 1.1, difficulty: 0, guessing: 0.15 },
        },
      },
      response: {
        type: "simulation_interaction",
        trace: {
          interactions: [
            {
              type: "parameter_change",
              parameterId: "pressure",
              predictedOutcome: "volume decreases",
              observedOutcome: "volume decreases",
            },
            {
              type: "parameter_change",
              parameterId: "temperature",
              predictedOutcome: "volume increases",
              observedOutcome: "volume increases",
            },
          ],
          summary:
            "I had to adjust pressure and adjust temperature to see the volume change.",
        },
      },
    });

    expect(result.feedback.scorePercent).toBeGreaterThanOrEqual(85);
    expect(result.earnedPoints).toBeGreaterThan(7);
  });

  it("summarizes simulation-backed attempt responses", () => {
    const summary = summarizeSimulationAttempt({
      items: [
        {
          id: "sim-item-1",
          type: "simulation_interaction",
          metadata: {
            simulationManifestId: "sim-1",
            simulationTitle: "Gas Law Explorer",
            simulationDomain: "CHEMISTRY",
            questionType: "prediction",
            interactionType: "parameter_exploration",
            expectedParameters: ["pressure"],
            expectedOutcomeKeywords: ["adjust pressure"],
            irt: { discrimination: 1.1, difficulty: 0, guessing: 0.15 },
          },
        },
      ],
      responses: {
        "sim-item-1": {
          type: "simulation_interaction",
          trace: {
            interactions: [{ type: "parameter_change", parameterId: "pressure" }],
            durationMs: 12000,
            summary: "I had to adjust pressure to reduce the volume.",
          },
        },
      },
      feedback: [
        {
          itemId: "sim-item-1" as any,
          scorePercent: 88,
          improvements: ["Check a second variable next time."],
        },
      ],
    });

    expect(summary.totalSimulationItems).toBe(1);
    expect(summary.completedSimulationItems).toBe(1);
    expect(summary.averageScorePercent).toBe(88);
    expect(summary.insights[0]?.simulationManifestId).toBe("sim-1");
  });
});
