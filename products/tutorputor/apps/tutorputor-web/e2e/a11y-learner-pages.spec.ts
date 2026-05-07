import AxeBuilder from "@axe-core/playwright";
import { expect, type Page, test } from "@playwright/test";

async function mockLearnerA11yApis(page: Page) {
  await page.route(/\/api\/v1\//, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    if (path.endsWith("/v1/learning/dashboard")) {
      return route.fulfill({
        json: {
          user: { id: "learner-a11y", email: "ava@example.test", displayName: "Ava Learner" },
          currentEnrollments: [
            {
              id: "enrollment-a11y",
              moduleId: "module-motion",
              moduleSlug: "intro-to-motion",
              moduleTitle: "Motion From Evidence",
              status: "active",
              progress: 0.58,
              progressPercent: 58,
              timeSpentSeconds: 2100,
            },
          ],
          recommendedModules: [],
          stats: { totalEnrollments: 1, completedModules: 0, averageProgress: 58 },
        },
      });
    }

    if (path.endsWith("/v1/recommendations/personalized")) {
      return route.fulfill({
        json: {
          data: {
            modules: [
              {
                id: "module-motion",
                title: "Motion From Evidence",
                slug: "intro-to-motion",
                description: "Prediction, simulation, feedback, and mastery.",
                domain: "Physics",
                difficultyLevel: "beginner",
                estimatedTimeMinutes: 18,
                tags: ["simulation", "evidence"],
                isAiRecommended: true,
                recommendationReason: "Builds on your diagnostic result.",
                matchScore: 0.93,
              },
            ],
            reasoning: {
              basedOn: "recent diagnostic",
              userLevel: "emerging",
              suggestedDomains: ["Physics"],
            },
          },
        },
      });
    }

    if (path.endsWith("/v1/gamification/progress")) {
      return route.fulfill({
        json: { totalPoints: 640, currentStreak: 3, level: 4, xpToNextLevel: 500, badges: [] },
      });
    }

    if (path.endsWith("/v1/modules/intro-to-motion")) {
      return route.fulfill({
        json: {
          module: {
            id: "module-motion",
            title: "Motion From Evidence",
            slug: "intro-to-motion",
            description: "Learn motion by making a prediction and checking visual evidence.",
            domain: "Physics",
            difficulty: "beginner",
            estimatedTimeMinutes: 18,
            learningObjectives: [
              { id: "claim-motion", label: "Predict motion from initial conditions", taxonomyLevel: "apply" },
              { id: "claim-evidence", label: "Explain observation with evidence", taxonomyLevel: "analyze" },
            ],
            contentBlocks: [
              {
                id: "block-1",
                blockType: "text",
                payload: { markdown: "Use the simulation to connect prediction, observation, and explanation." },
              },
            ],
            authorId: "author-1",
            publishedAt: "2026-05-06T00:00:00.000Z",
          },
          userEnrollment: {
            id: "enrollment-a11y",
            moduleId: "module-motion",
            moduleSlug: "intro-to-motion",
            moduleTitle: "Motion From Evidence",
            userId: "learner-a11y",
            status: "active",
            progressPercent: 58,
            timeSpentSeconds: 2100,
            enrolledAt: "2026-05-06T00:00:00.000Z",
          },
        },
      });
    }

    if (path.endsWith("/v1/assessments")) {
      return route.fulfill({
        json: {
          items: [
            {
              id: "assessment-motion",
              title: "Motion Evidence Check",
              description: "A scored CBM assessment from simulation evidence.",
              status: "ACTIVE",
              itemCount: 2,
              timeLimitMinutes: 12,
              moduleId: "module-motion",
              createdAt: "2026-05-06T00:00:00.000Z",
              updatedAt: "2026-05-06T00:00:00.000Z",
            },
          ],
          nextCursor: null,
        },
      });
    }

    if (path.endsWith("/v1/ai/tutor/query") && method === "POST") {
      return route.fulfill({
        json: {
          response: {
            answer: "What evidence from the simulation would let you compare your prediction with what happened?",
            confidence: 0.82,
            followUpQuestions: ["Which variable changed first?"],
          },
        },
      });
    }

    if (path.endsWith("/v1/simulations/intro-to-motion") && method === "GET") {
      return route.fulfill({
        json: {
          id: "intro-to-motion",
          title: "Motion Evidence Simulation",
          description: "Predict, simulate, observe, explain, and prove mastery.",
          domain: "Physics",
          parameters: { velocity: 4, acceleration: 2 },
        },
      });
    }

    if (path.endsWith("/v1/simulations/intro-to-motion/run") && method === "POST") {
      return route.fulfill({
        json: {
          summary: "The object moved farther each second as acceleration increased velocity.",
          details: "Distance changed non-linearly because velocity was not constant.",
          keyObservations: ["Velocity increased", "Distance per interval grew", "Prediction partly matched"],
        },
      });
    }

    return route.fulfill({ json: {} });
  });
}

async function expectNoCriticalA11yViolations(page: Page, label: string) {
  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
    .analyze();

  const criticalViolations = results.violations.filter((violation) => violation.impact === "critical");
  expect(criticalViolations, `${label} has critical accessibility violations`).toEqual([]);
}

test.describe("automated learner accessibility gates", () => {
  test.beforeEach(async ({ page }) => {
    await mockLearnerA11yApis(page);
    await page.addInitScript(() => {
      window.localStorage.setItem("auth_token", "a11y-token");
      window.localStorage.setItem("tenant_id", "tenant-a11y");
    });
  });

  test("dashboard, module, assessment, and simulation pages have no critical axe violations", async ({ page }) => {
    const pages = [
      { path: "/dashboard", heading: /Hello, Ava/i, label: "learner dashboard" },
      { path: "/modules/intro-to-motion", heading: /Motion From Evidence/i, label: "module page" },
      { path: "/assessments", heading: /Assessments/i, label: "assessment index" },
      { path: "/learn/intro-to-motion", heading: /Motion Evidence Simulation/i, label: "simulation journey" },
    ];

    for (const entry of pages) {
      await page.goto(entry.path);
      await expect(page.getByRole("heading", { name: entry.heading }).first()).toBeVisible();
      await expectNoCriticalA11yViolations(page, entry.label);
    }
  });

  test("AI tutor panel has no critical axe violations and works from the keyboard", async ({ page }) => {
    await page.goto("/dashboard");
    await expect(page.getByRole("heading", { name: /Hello, Ava/i })).toBeVisible();

    await page.keyboard.press("Tab");
    for (let i = 0; i < 30; i += 1) {
      const title = await page.evaluate(() => document.activeElement?.getAttribute("title"));
      if (title === "Ask AI Tutor") break;
      await page.keyboard.press("Tab");
    }

    await expect(page.getByTitle("Ask AI Tutor")).toBeFocused();
    await page.keyboard.press("Enter");
    await expect(page.getByText("AI Tutor").last()).toBeVisible();

    await expectNoCriticalA11yViolations(page, "AI tutor panel");

    await page.keyboard.press("Tab");
    await page.getByPlaceholder("Ask me anything...").fill("Give me a hint without the answer.");
    await page.keyboard.press("Enter");

    await expect(page.getByText("What evidence from the simulation")).toBeVisible();
  });

  test("simulation prediction controls can be reached and operated from the keyboard", async ({ page }) => {
    await page.goto("/learn/intro-to-motion");
    await expect(page.getByRole("heading", { name: "Motion Evidence Simulation" })).toBeVisible();

    await page.keyboard.press("Tab");
    await page.getByPlaceholder("I predict that... because...").fill(
      "I predict the object will cover more distance each second because acceleration increases velocity.",
    );

    await page.getByRole("button", { name: "Very Confident" }).focus();
    await page.keyboard.press("Enter");
    await page.getByRole("button", { name: /Run Simulation/i }).focus();
    await page.keyboard.press("Enter");

    await expect(page.getByRole("heading", { name: "Run the Simulation" })).toBeVisible();
  });
});
