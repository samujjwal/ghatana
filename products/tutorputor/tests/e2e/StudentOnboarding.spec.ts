import { expect, test, type Page } from "@playwright/test";

const learnerBaseUrl = process.env.BASE_URL ?? "http://127.0.0.1:3201";

function createLearnerJwt(overrides: Record<string, unknown> = {}): string {
  const header = Buffer.from(
    JSON.stringify({ alg: "HS256", typ: "JWT" }),
  ).toString("base64url");
  const payload = Buffer.from(
    JSON.stringify({
      sub: "learner-onboarding-001",
      email: "onboarding.learner@example.com",
      name: "Avery Learner",
      role: "student",
      tenantId: "tenant-school",
      ...overrides,
    }),
  ).toString("base64url");

  return `${header}.${payload}.signature`;
}

async function expectNoPageErrors(
  page: Page,
  action: () => Promise<void>,
): Promise<void> {
  const errors: string[] = [];
  page.on("pageerror", (error: Error) => {
    errors.push(error.message);
  });

  await action();
  expect(errors).toEqual([]);
}

test.describe("TutorPutor student onboarding", () => {
  test("bootstraps the canonical first-sign-in flow and first-visit learner dashboard", async ({
    page,
  }) => {
    const accessToken = createLearnerJwt();
    const refreshToken = "refresh-token-onboarding-1";

    await page.route("**/api/v1/auth/sso/providers?*", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          providers: [
            {
              id: "google-workspace",
              displayName: "Google Workspace",
              type: "oidc",
            },
          ],
        }),
      });
    });

    await page.route(
      "**/api/v1/auth/sso/login/google-workspace?*",
      async (route) => {
        await route.fulfill({
          status: 302,
          headers: {
            location: `${learnerBaseUrl}/dashboard?accessToken=${encodeURIComponent(accessToken)}&refreshToken=${encodeURIComponent(refreshToken)}`,
          },
          body: "",
        });
      },
    );

    await page.route("**/api/v1/auth/me", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: "learner-onboarding-001",
          email: "onboarding.learner@example.com",
          displayName: "Avery Learner",
          role: "student",
          tenantId: "tenant-school",
        }),
      });
    });

    await page.route("**/api/v1/learning/dashboard", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          user: {
            id: "learner-onboarding-001",
            email: "onboarding.learner@example.com",
            displayName: "Avery Learner",
          },
          currentEnrollments: [],
          recommendedModules: [
            {
              id: "module-onboarding-1",
              title: "Forces and Motion",
              slug: "forces-and-motion",
              description: "Explore balanced and unbalanced forces.",
              tags: ["physics"],
              domain: "PHYSICS",
              difficulty: "beginner",
              estimatedMinutes: 20,
            },
          ],
          stats: {
            totalEnrollments: 0,
            completedModules: 0,
            averageProgress: 0,
          },
        }),
      });
    });

    await page.route(
      "**/api/v1/recommendations/personalized?limit=6",
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            data: {
              modules: [
                {
                  id: "module-onboarding-1",
                  title: "Forces and Motion",
                  slug: "forces-and-motion",
                  description: "Explore balanced and unbalanced forces.",
                  tags: ["physics"],
                  domain: "PHYSICS",
                  difficultyLevel: "beginner",
                  estimatedTimeMinutes: 20,
                  isAiRecommended: true,
                  recommendationReason:
                    "A strong first module for new learners starting physics.",
                  matchScore: 0.88,
                },
              ],
            },
          }),
        });
      },
    );

    await page.route("**/api/v1/analytics/summary", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          totalLearningTimeMinutes: 45,
          totalCompletedAssessments: 1,
          averageAssessmentScore: 92,
          activeLearningStreakDays: 3,
        }),
      });
    });

    await page.route("**/api/v1/analytics/usage-trends?*", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          { date: "2026-04-18", minutes: 10 },
          { date: "2026-04-19", minutes: 15 },
          { date: "2026-04-20", minutes: 20 },
        ]),
      });
    });

    await page.route("**/api/v1/analytics/at-risk", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([]),
      });
    });

    await expectNoPageErrors(page, async () => {
      await page.goto(`${learnerBaseUrl}/login?redirect=/dashboard&tenant=school`);
      await page.waitForLoadState("domcontentloaded");
    });

    await expect(
      page.getByRole("heading", { name: /sign in to tutorputor/i }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: /sign in with google workspace/i }),
    ).toBeVisible();

    await page
      .getByRole("button", { name: /sign in with google workspace/i })
      .click();
    await page.waitForURL(/\/dashboard$/);

    await expect(
      page.getByRole("heading", { name: /hello, avery/i }),
    ).toBeVisible();
    await expect(page.getByText(/start something new/i)).toBeVisible();
    await expect(page.getByText("Forces and Motion")).toBeVisible();
    await expect(
      page.getByText(/a strong first module for new learners/i),
    ).toBeVisible();

    await expect.poll(async () => {
      return page.evaluate(() => window.location.search);
    }).toBe("");
    await expect.poll(async () => {
      return page.evaluate(() => window.localStorage.getItem("auth_token"));
    }).toBe(accessToken);
    await expect.poll(async () => {
      return page.evaluate(() => window.localStorage.getItem("refresh_token"));
    }).toBe(refreshToken);
    await expect.poll(async () => {
      return page.evaluate(() => window.localStorage.getItem("tenant_id"));
    }).toBe("tenant-school");

    await page.getByRole("link", { name: "Analytics" }).click();
    await page.waitForURL(/\/analytics$/);
    await expect(
      page.getByRole("heading", { name: "Learning Analytics" }),
    ).toBeVisible();
  });

  test("guides a newly authenticated learner from dashboard discovery to module exploration", async ({
    page,
  }) => {
    const accessToken = createLearnerJwt();

    await page.addInitScript((token: string) => {
      window.localStorage.setItem("auth_token", token);
      window.localStorage.setItem("tenant_id", "tenant-school");
    }, accessToken);

    await page.route("**/api/v1/auth/me", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: "learner-onboarding-001",
          email: "onboarding.learner@example.com",
          displayName: "Avery Learner",
          role: "student",
          tenantId: "tenant-school",
        }),
      });
    });

    await page.route("**/api/v1/learning/dashboard", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          user: {
            id: "learner-onboarding-001",
            email: "onboarding.learner@example.com",
            displayName: "Avery Learner",
          },
          currentEnrollments: [],
          recommendedModules: [],
          stats: {
            totalEnrollments: 0,
            completedModules: 0,
            averageProgress: 0,
          },
        }),
      });
    });

    await page.route(
      "**/api/v1/recommendations/personalized?limit=6",
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ data: { modules: [] } }),
        });
      },
    );

    await page.route("**/api/v1/search**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          modules: [
            {
              id: "module-kinematics-1",
              title: "Kinematics Basics",
              slug: "kinematics-basics",
              description: "Understand speed, velocity, and acceleration.",
              domain: "PHYSICS",
              estimatedMinutes: 30,
              tags: ["physics", "motion"],
            },
          ],
        }),
      });
    });

    await page.route("**/api/v1/modules/kinematics-basics", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          module: {
            id: "module-kinematics-1",
            title: "Kinematics Basics",
            slug: "kinematics-basics",
            description: "Understand speed, velocity, and acceleration.",
            difficulty: "beginner",
            estimatedTimeMinutes: 30,
            learningObjectives: [
              "Describe speed and velocity",
              "Interpret acceleration in motion graphs",
            ],
            contentBlocks: [],
          },
          userEnrollment: null,
        }),
      });
    });

    await expectNoPageErrors(page, async () => {
      await page.goto(`${learnerBaseUrl}/dashboard`);
      await page.waitForLoadState("domcontentloaded");
    });

    await expect(
      page.getByRole("heading", { name: /hello, avery/i }),
    ).toBeVisible();
    await expect(page.getByRole("link", { name: /browse modules/i })).toBeVisible();

    await page.getByRole("link", { name: /browse modules/i }).click();
    await page.waitForURL(/\/search$/);
    await expect(page.locator("main")).toBeVisible();

    await page.goto(`${learnerBaseUrl}/modules/kinematics-basics`);
    await page.waitForLoadState("domcontentloaded");
    await expect(
      page.getByRole("heading", { name: /kinematics basics/i }),
    ).toBeVisible();
    await expect(
      page.getByText(/understand speed, velocity, and acceleration/i),
    ).toBeVisible();
  });
});
