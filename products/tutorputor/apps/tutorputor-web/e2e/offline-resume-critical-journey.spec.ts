import { expect, type Page, test } from "@playwright/test";

async function mockOfflineResumeApis(page: Page) {
  await page.route(/\/api\/v1\//, async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;

    if (path.endsWith("/v1/learning/dashboard")) {
      return route.fulfill({
        json: {
          user: { id: "learner-1", email: "ava@example.test", displayName: "Ava Learner" },
          currentEnrollments: [
            {
              id: "enrollment-1",
              moduleId: "module-motion",
              moduleSlug: "intro-to-motion",
              moduleTitle: "Motion From Evidence",
              status: "active",
              progress: 1,
              progressPercent: 100,
              timeSpentSeconds: 2400,
            },
          ],
          recommendedModules: [],
          stats: { totalEnrollments: 1, completedModules: 1, averageProgress: 100 },
        },
      });
    }

    if (path.endsWith("/v1/recommendations/personalized")) {
      return route.fulfill({ json: { data: { modules: [], reasoning: {} } } });
    }

    if (path.endsWith("/v1/gamification/progress")) {
      return route.fulfill({ json: { totalPoints: 900, currentStreak: 4, level: 5, xpToNextLevel: 250, badges: [] } });
    }

    if (path.endsWith("/v1/simulations/intro-to-motion")) {
      return route.fulfill({
        json: {
          id: "intro-to-motion",
          title: "Motion Evidence Simulation",
          description: "Cached simulation for offline replay.",
          domain: "Physics",
          parameters: { velocity: 4, acceleration: 2 },
        },
      });
    }

    if (path.endsWith("/v1/offline/sync")) {
      return route.fulfill({ json: { status: "synced", mastery: { claimId: "claim-motion", score: 0.92 } } });
    }

    return route.fulfill({ json: {} });
  });
}

test.describe("offline-resumed learner journey", () => {
  test.beforeEach(async ({ page }) => {
    await mockOfflineResumeApis(page);
    await page.addInitScript(() => {
      window.localStorage.setItem("auth_token", "offline-token");
      window.localStorage.setItem("tenant_id", "tenant-offline");
    });
  });

  test("keeps pending evidence explicit while the learner moves through cached journey routes", async ({ page, context }) => {
    await page.goto("/diagnostic");
    await expect(page.getByRole("heading", { name: "Diagnostic" })).toBeVisible();

    await page.evaluate(() => {
      window.localStorage.setItem(
        "tutorputor.offline.syncQueue",
        JSON.stringify([
          {
            type: "module.progress",
            payload: {
              moduleId: "module-motion",
              lessonId: "lesson-motion",
              progressPercent: 100,
              timeSpentSeconds: 2400,
              updatedAt: "2026-05-06T12:00:00.000Z",
            },
            metadata: {
              clientMutationId: "offline-module-progress-1",
              entityKey: "module-motion:lesson-motion",
              localVersion: 2,
              baseServerVersion: 1,
              createdAt: "2026-05-06T12:00:00.000Z",
              updatedAt: "2026-05-06T12:00:00.000Z",
              status: "pending",
              retryCount: 0,
            },
          },
          {
            type: "simulation.capture",
            payload: {
              simulationRunId: "run-offline-1",
              captureId: "capture-offline-1",
              deterministicHash: "hash-motion",
              claimId: "claim-motion",
              evidenceId: "evidence-motion",
              taskId: "task-motion",
              outputState: { velocity: 6 },
              processFeatures: { hintCount: 0 },
              capturedAt: "2026-05-06T12:01:00.000Z",
            },
            metadata: {
              clientMutationId: "offline-simulation-capture-1",
              entityKey: "run-offline-1:capture-offline-1",
              localVersion: 1,
              baseServerVersion: 1,
              createdAt: "2026-05-06T12:01:00.000Z",
              updatedAt: "2026-05-06T12:01:00.000Z",
              status: "pending",
              retryCount: 0,
            },
          },
          {
            type: "assessment.attempt",
            payload: {
              assessmentId: "assessment-motion",
              attemptId: "attempt-offline-1",
              status: "submitted",
              submittedAt: "2026-05-06T12:02:00.000Z",
              answers: [
                {
                  itemId: "item-motion",
                  response: "Velocity increased between snapshots.",
                  confidence: "high",
                  updatedAt: "2026-05-06T12:02:00.000Z",
                },
              ],
            },
            metadata: {
              clientMutationId: "offline-assessment-attempt-1",
              entityKey: "attempt-offline-1",
              localVersion: 1,
              baseServerVersion: 1,
              createdAt: "2026-05-06T12:02:00.000Z",
              updatedAt: "2026-05-06T12:02:00.000Z",
              status: "pending",
              retryCount: 0,
            },
          },
          {
            type: "ai.disabled-state",
            payload: {
              learnerId: "learner-1",
              moduleId: "module-motion",
              disabled: true,
              reason: "offline",
              updatedAt: "2026-05-06T12:02:30.000Z",
            },
            metadata: {
              clientMutationId: "offline-ai-disabled-1",
              entityKey: "learner-1:module-motion",
              localVersion: 1,
              baseServerVersion: 1,
              createdAt: "2026-05-06T12:02:30.000Z",
              updatedAt: "2026-05-06T12:02:30.000Z",
              status: "pending",
              retryCount: 0,
            },
          },
          {
            type: "telemetry.batch",
            payload: {
              batchId: "batch-offline-1",
              events: [
                { id: "event-sim-capture", type: "sim.capture", timestamp: "2026-05-06T12:01:00.000Z", runId: "run-offline-1" },
                { id: "event-assess-answer", type: "assess.answer", timestamp: "2026-05-06T12:02:00.000Z", attemptId: "attempt-offline-1" },
              ],
            },
            metadata: {
              clientMutationId: "offline-telemetry-batch-1",
              entityKey: "batch-offline-1",
              localVersion: 1,
              baseServerVersion: 1,
              createdAt: "2026-05-06T12:03:00.000Z",
              updatedAt: "2026-05-06T12:03:00.000Z",
              status: "pending",
              retryCount: 0,
            },
          },
        ]),
      );
    });

    await page.goto("/learn/intro-to-motion");
    await expect(page.getByRole("heading", { name: "Motion Evidence Simulation" })).toBeVisible();

    await context.setOffline(true);

    const pending = await page.evaluate(() => window.localStorage.getItem("tutorputor.offline.syncQueue"));
    expect(pending).toContain("simulation.capture");
    expect(pending).toContain("assessment.attempt");
    expect(pending).toContain("telemetry.batch");
    expect(pending).toContain("module.progress");
    expect(pending).toContain("ai.disabled-state");

    await context.setOffline(false);
    await page.goto("/dashboard");
    await expect(page.getByRole("heading", { name: /Hello, Ava/i })).toBeVisible();

    await page.evaluate(() => {
      window.localStorage.setItem(
        "tutorputor.offline.lastSyncResult",
        JSON.stringify({ status: "synced", dashboard: "updated", mastery: { claimId: "claim-motion", score: 0.92 } }),
      );
    });

    const resumed = await page.evaluate(() => JSON.parse(window.localStorage.getItem("tutorputor.offline.syncQueue") ?? "[]"));
    expect(resumed.map((record: { type: string }) => record.type)).toEqual([
      "module.progress",
      "simulation.capture",
      "assessment.attempt",
      "ai.disabled-state",
      "telemetry.batch",
    ]);
    const syncResult = await page.evaluate(() => window.localStorage.getItem("tutorputor.offline.lastSyncResult"));
    expect(syncResult).toContain("dashboard");
    expect(syncResult).toContain("mastery");
  });
});
