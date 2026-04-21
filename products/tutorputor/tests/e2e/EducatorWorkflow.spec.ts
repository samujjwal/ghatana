import { expect, test, type Page } from "@playwright/test";
import { createHmac } from "node:crypto";

const adminUrl = process.env.ADMIN_URL ?? "http://127.0.0.1:3202";
const gatewayUrl = process.env.GATEWAY_URL ?? "http://127.0.0.1:3200";
const jwtSecret =
  process.env.JWT_SECRET ?? "test-secret-do-not-use-in-prod-1234567890";

function createAdminAccessToken(): string {
  const header = Buffer.from(
    JSON.stringify({ alg: "HS256", typ: "JWT" }),
  ).toString("base64url");
  const payload = Buffer.from(
    JSON.stringify({
      sub: "user-admin-001",
      email: "admin@demo.tutorputor.com",
      name: "Sarah Admin",
      role: "admin",
      tenantId: "default",
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 60 * 60,
    }),
  ).toString("base64url");
  const signature = createHmac("sha256", jwtSecret)
    .update(`${header}.${payload}`)
    .digest("base64url");

  return `${header}.${payload}.${signature}`;
}

function createAuthHeaders(): Record<string, string> {
  return {
    Authorization: `Bearer ${createAdminAccessToken()}`,
  };
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
  const filtered = errors.filter(
    (message) =>
      !message.includes("ResizeObserver") && !message.includes("Non-Error"),
  );
  expect(filtered).toEqual([]);
}

test.describe("TutorPutor educator workflows", () => {
  test("renders the canonical authoring workspace with publish readiness and analytics panels for a real experience", async ({
    page,
    request,
  }) => {
    const authHeaders = createAuthHeaders();
    const meAuthHeaders: string[] = [];

    await page.addInitScript((token: string) => {
      window.localStorage.setItem("auth_token", token);
      window.localStorage.setItem("refresh_token", "refresh-token-admin-2");
      window.localStorage.setItem("tenant_id", "default");
    }, authHeaders.Authorization.replace("Bearer ", ""));

    await page.route("**/api/v1/auth/me", async (route) => {
      const authorization = route.request().headers()["authorization"];
      if (authorization) {
        meAuthHeaders.push(authorization);
      }

      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: "user-admin-001",
          email: "admin@demo.tutorputor.com",
          role: "admin",
          firstName: "Sarah",
          lastName: "Admin",
          fullName: "Sarah Admin",
          tenantId: "default",
        }),
      });
    });

    const title = `Educator Workflow Experience ${Date.now()}`;
    const createResponse = await request.post(
      `${gatewayUrl}/api/content-studio/experiences`,
      {
        headers: authHeaders,
        data: {
          title,
          description: "Educator workspace regression verification",
          gradeRange: "grade_6_8",
        },
      },
    );
    expect(createResponse.ok()).toBe(true);

    const createPayload = (await createResponse.json()) as {
      data?: {
        id?: string;
        experience?: { id?: string };
      };
    };
    const experienceId =
      createPayload.data?.experience?.id ?? createPayload.data?.id;
    expect(experienceId).toBeTruthy();

    await expectNoPageErrors(page, async () => {
      await page.goto(`${adminUrl}/authoring/${experienceId}`);
      await page.waitForLoadState("domcontentloaded");
    });

    await expect.poll(() => meAuthHeaders.length).toBeGreaterThan(0);
    expect(meAuthHeaders).toContain(authHeaders.Authorization);

    await expect(
      page
        .getByRole("navigation", { name: "Breadcrumb" })
        .getByText(title, { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: /claims & evidence/i }),
    ).toBeVisible();
    await expect(page.getByText(/review and publication status/i)).toBeVisible();
    await expect(page.getByText(/analytics dashboard/i)).toBeVisible();
    await expect(page.getByText(title).first()).toBeVisible();
  });

  test("loads the consolidated admin analytics route with real dashboard visuals", async ({
    page,
  }) => {
    await page.route("**/api/v1/auth/me", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: "user-admin-001",
          email: "admin@demo.tutorputor.com",
          role: "admin",
          firstName: "Sarah",
          lastName: "Admin",
          fullName: "Sarah Admin",
          tenantId: "default",
        }),
      });
    });

    await page.route("**/admin/api/v1/analytics/overview?*", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          totalLearners: 128,
          totalModules: 14,
          avgCompletionRate: 72.4,
          totalEnrollments: 342,
        }),
      });
    });

    await page.route("**/admin/api/v1/analytics/concepts?*", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          {
            slug: "forces-and-motion",
            title: "Forces and Motion",
            enrollments: 86,
            avgCompletion: 78,
            avgTimeMinutes: 24,
          },
          {
            slug: "kinematics-basics",
            title: "Kinematics Basics",
            enrollments: 74,
            avgCompletion: 69,
            avgTimeMinutes: 28,
          },
        ]),
      });
    });

    await page.route("**/admin/api/v1/analytics/trends?*", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          { date: "2026-04-18", count: 21 },
          { date: "2026-04-19", count: 26 },
          { date: "2026-04-20", count: 31 },
        ]),
      });
    });

    await page.route(
      "**/admin/api/v1/analytics/simulations?*",
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([
            {
              id: "sim-1",
              type: "physics",
              title: "Balanced Forces Lab",
              uniqueUsers: 41,
              avgDuration: 12,
              totalSessions: 58,
              avgInteractions: 19,
            },
          ]),
        });
      },
    );

    await expectNoPageErrors(page, async () => {
      await page.goto(`${adminUrl}/analytics`);
      await page.waitForLoadState("domcontentloaded");
    });

    await expect(
      page.getByRole("heading", { name: /analytics dashboard/i }),
    ).toBeVisible();
    await expect(page.getByText("128")).toBeVisible();
    await expect(page.getByText("Forces and Motion").first()).toBeVisible();
    await expect(page.getByText("Balanced Forces Lab")).toBeVisible();
  });
});