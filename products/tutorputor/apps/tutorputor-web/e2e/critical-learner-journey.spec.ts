import { expect, type Page, test } from "@playwright/test";

const journeyStages = [
  { path: "/onboarding", text: "Welcome to Your Learning Journey" },
  { path: "/diagnostic", text: "Diagnostic" },
  { path: "/pathways", text: "Learning Pathways" },
  { path: "/modules/intro-to-motion", text: "Motion From Evidence" },
  { path: "/simulations", text: "Simulations" },
  { path: "/assessments", text: "Assessments" },
  { path: "/analytics", text: "Learning Analytics" },
  { path: "/credentials", text: "Credentials" },
  { path: "/settings", text: "Settings" },
  { path: "/settings/privacy", text: "Privacy Center" },
] as const;

async function mockCriticalJourneyApis(page: Page) {
  await page.route(/\/api\/v1\//, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

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
              progress: 0.48,
              progressPercent: 48,
              timeSpentSeconds: 1860,
            },
          ],
          recommendedModules: [],
          stats: { totalEnrollments: 1, completedModules: 0, averageProgress: 48 },
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
            id: "enrollment-1",
            moduleId: "module-motion",
            moduleSlug: "intro-to-motion",
            moduleTitle: "Motion From Evidence",
            userId: "learner-1",
            status: "active",
            progressPercent: 48,
            timeSpentSeconds: 1860,
            enrolledAt: "2026-05-06T00:00:00.000Z",
          },
        },
      });
    }

    if (path.endsWith("/v1/pathways")) {
      return route.fulfill({
        json: {
          enrollments: [
            {
              id: "path-enrollment-1",
              userId: "learner-1",
              pathId: "path-motion",
              status: "active",
              currentNodeId: "module-motion",
              enrolledAt: "2026-05-06T00:00:00.000Z",
              path: {
                id: "path-motion",
                title: "Motion Evidence Pathway",
                description: "A claim-based route through simulations and assessment.",
                nodes: [{ id: "module-motion", title: "Motion From Evidence", type: "module", estimatedMinutes: 18, order: 1 }],
              },
              nodeProgress: [{ nodeId: "module-motion", status: "in_progress" }],
            },
          ],
        },
      });
    }

    if (path.endsWith("/v1/gamification/progress")) {
      return route.fulfill({
        json: {
          totalPoints: 640,
          currentStreak: 3,
          level: 4,
          xpToNextLevel: 500,
          badges: [],
        },
      });
    }

    if (path.endsWith("/api/v1/assessments") || path.endsWith("/v1/assessments")) {
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

    if (path.endsWith("/v1/analytics/summary")) {
      return route.fulfill({
        json: {
          totalEvents: 24,
          activeLearners: 1,
          eventsByType: {
            "sim.capture": 4,
            "assess.answer": 3,
            module_completed: 0,
          },
        },
      });
    }

    if (path.endsWith("/v1/analytics/usage-trends")) {
      return route.fulfill({
        json: {
          periods: [
            { periodStart: "2026-05-04", eventCount: 5 },
            { periodStart: "2026-05-05", eventCount: 8 },
            { periodStart: "2026-05-06", eventCount: 11 },
          ],
        },
      });
    }

    if (path.endsWith("/v1/analytics/at-risk")) {
      return route.fulfill({
        json: [
          {
            userId: "learner-1",
            displayName: "Ava Learner",
            riskLevel: "medium",
            riskFactors: ["misconception: velocity vs acceleration"],
          },
        ],
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

    if (path.endsWith("/v1/simulations/intro-to-motion/evaluate") && method === "POST") {
      return route.fulfill({
        json: {
          isCorrect: false,
          explanation: "Your explanation used evidence, but it should distinguish velocity from acceleration.",
          misconceptions: ["Velocity and acceleration are not the same quantity."],
          hints: ["Tie each observation to a variable in the simulation."],
          remediationLinks: [{ label: "Review velocity vs acceleration", href: "/modules/intro-to-motion" }],
        },
      });
    }

    if (path.endsWith("/v1/simulations/intro-to-motion/mastery-challenge") && method === "GET") {
      return route.fulfill({
        json: {
          question: "Which observation best supports the claim that acceleration was positive?",
          options: [
            "Position stayed constant.",
            "Velocity increased between snapshots.",
            "The object disappeared.",
            "The timer stopped.",
          ],
          correctIndex: 1,
          explanation: "Increasing velocity between snapshots is evidence of positive acceleration.",
        },
      });
    }

    if (path.endsWith("/v1/simulations/intro-to-motion/mastery-challenge/submit") && method === "POST") {
      return route.fulfill({ json: { passed: true, explanation: "Mastery evidence accepted." } });
    }

    return route.fulfill({ json: {} });
  });
}

test.describe("critical learner journey", () => {
  test.beforeEach(async ({ page }) => {
    await mockCriticalJourneyApis(page);
    await page.addInitScript(() => {
      window.localStorage.setItem("auth_token", "critical-journey-token");
      window.localStorage.setItem("tenant_id", "tenant-critical");
    });
  });

  test("renders grounded AI tutor help during the learner journey", async ({ page }) => {
    await page.goto("/dashboard");
    await expect(page.getByRole("heading", { name: /Hello, Ava/i })).toBeVisible();

    await page.getByTitle("Ask AI Tutor").click();
    await expect(page.getByText("AI Tutor").last()).toBeVisible();

    await page.getByPlaceholder("Ask me anything...").fill("Give me a hint without the answer.");
    await page.getByPlaceholder("Ask me anything...").press("Enter");

    await expect(page.getByText(
      "What evidence from the simulation",
    )).toBeVisible();
  });

  for (const stage of journeyStages) {
    test(`renders ${stage.path}`, async ({ page }) => {
      await page.goto(stage.path);
      await expect(page.getByText(stage.text).first()).toBeVisible();
      await expect(page.getByText("Error loading").first()).toBeHidden();
    });
  }

  test("completes simulation capture through feedback, remediation, mastery, credential, and privacy", async ({ page }) => {
    await page.goto("/learn/intro-to-motion");

    await expect(page.getByRole("heading", { name: "Motion Evidence Simulation" })).toBeVisible();
    await page.getByPlaceholder("I predict that... because...").fill(
      "I predict the object will cover more distance each second because acceleration increases velocity.",
    );
    await page.getByRole("button", { name: "Very Confident" }).click();
    await page.getByRole("button", { name: /Run Simulation/i }).click();

    await expect(page.getByRole("heading", { name: "Run the Simulation" })).toBeVisible();
    await page.getByRole("button", { name: /Launch Simulation/i }).click();
    await expect(page.getByText("Simulation complete!")).toBeVisible();
    await page.getByRole("button", { name: /View Results/i }).click();

    await expect(page.getByRole("heading", { name: "What did you observe?" })).toBeVisible();
    await expect(page.getByText("Velocity increased")).toBeVisible();
    await page.getByRole("button", { name: /Explain What Happened/i }).click();

    await page.getByPlaceholder("The simulation showed... because... This is consistent with the principle that...").fill(
      "The simulation showed increasing distance per time interval because velocity increased under acceleration.",
    );
    await page.getByRole("button", { name: /Get Feedback/i }).click();

    await expect(page.getByRole("heading", { name: "Feedback" })).toBeVisible();
    await expect(page.getByText("Velocity and acceleration are not the same quantity.")).toBeVisible();
    await page.getByRole("button", { name: /Prove Mastery/i }).click();

    await expect(page.getByRole("heading", { name: "Prove Your Mastery" })).toBeVisible();
    await page.getByText("Velocity increased between snapshots.").click();
    await page.getByRole("button", { name: /Submit Answer/i }).click();

    await expect(page.getByRole("heading", { name: "Mastery Achieved!" })).toBeVisible();

    await page.goto("/credentials");
    await expect(page.getByText("Evidence-Based Simulation Foundations")).toBeVisible();

    await page.goto("/settings/privacy");
    await expect(page.getByRole("heading", { name: "Privacy Center" })).toBeVisible();
    await expect(page.getByRole("button", { name: /Export My Data/i })).toBeVisible();
  });
});
