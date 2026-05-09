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

test.describe("TutorPutor admin workflows", () => {
  test("complete admin dashboard navigation and user management", async ({
    page,
    request,
  }) => {
    const authHeaders = createAuthHeaders();
    const meAuthHeaders: string[] = [];

    await page.addInitScript((token: string) => {
      window.localStorage.setItem("auth_token", token);
      window.localStorage.setItem("refresh_token", "refresh-token-admin-1");
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

    await page.route("**/api/v1/admin/users?*", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          users: [
            {
              id: "user-001",
              email: "student@example.com",
              displayName: "Student User",
              role: "student",
              status: "active",
              createdAt: "2026-04-20T00:00:00.000Z",
            },
            {
              id: "user-002",
              email: "instructor@example.com",
              displayName: "Instructor User",
              role: "instructor",
              status: "active",
              createdAt: "2026-04-20T00:00:00.000Z",
            },
          ],
          total: 2,
        }),
      });
    });

    await page.route("**/api/v1/admin/analytics/overview?*", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          totalUsers: 150,
          activeUsers: 120,
          totalEnrollments: 500,
          completedModules: 320,
          averageProgress: 65,
        }),
      });
    });

    await expectNoPageErrors(page, async () => {
      await page.goto(`${adminUrl}/dashboard`);
      await page.waitForLoadState("domcontentloaded");
    });

    await expect(meAuthHeaders.length).toBeGreaterThan(0);
    expect(meAuthHeaders).toContain(authHeaders.Authorization);

    await expect(
      page.getByRole("heading", { name: /admin dashboard/i }),
    ).toBeVisible();

    await page.getByRole("link", { name: "Users" }).click();
    await page.waitForURL(/\/admin\/users$/);

    await expect(
      page.getByRole("heading", { name: /user management/i }),
    ).toBeVisible();
    await expect(page.getByText("Student User")).toBeVisible();
    await expect(page.getByText("Instructor User")).toBeVisible();

    await page.getByRole("link", { name: "Dashboard" }).click();
    await page.waitForURL(/\/admin\/dashboard$/);

    await expect(page.getByText("150")).toBeVisible();
    await expect(page.getByText("120")).toBeVisible();
    await expect(page.getByText("500")).toBeVisible();
  });

  test("admin content review and approval workflow", async ({ page, request }) => {
    const authHeaders = createAuthHeaders();

    await page.addInitScript((token: string) => {
      window.localStorage.setItem("auth_token", token);
      window.localStorage.setItem("refresh_token", "refresh-token-admin-2");
      window.localStorage.setItem("tenant_id", "default");
    }, authHeaders.Authorization.replace("Bearer ", ""));

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

    await page.route("**/api/v1/content/review/pending?*", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          items: [
            {
              id: "artifact-001",
              type: "claim",
              title: "Newton's First Law",
              status: "pending_review",
              submittedAt: "2026-04-20T00:00:00.000Z",
              submittedBy: "instructor-001",
            },
            {
              id: "artifact-002",
              type: "simulation",
              title: "Velocity Simulation",
              status: "pending_review",
              submittedAt: "2026-04-20T00:00:00.000Z",
              submittedBy: "instructor-002",
            },
          ],
        }),
      });
    });

    await page.route("**/api/v1/content/review/artifact-001/approve", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ success: true }),
      });
    });

    await expectNoPageErrors(page, async () => {
      await page.goto(`${adminUrl}/content/review`);
      await page.waitForLoadState("domcontentloaded");
    });

    await expect(
      page.getByRole("heading", { name: /content review/i }),
    ).toBeVisible();

    await expect(page.getByText("Newton's First Law")).toBeVisible();
    await expect(page.getByText("Velocity Simulation")).toBeVisible();

    await page.getByRole("button", { name: /approve/i }).first().click();

    await expect(page.getByText("Approved successfully")).toBeVisible();
  });

  test("admin settings and configuration management", async ({ page }) => {
    const authHeaders = createAuthHeaders();

    await page.addInitScript((token: string) => {
      window.localStorage.setItem("auth_token", token);
      window.localStorage.setItem("refresh_token", "refresh-token-admin-3");
      window.localStorage.setItem("tenant_id", "default");
    }, authHeaders.Authorization.replace("Bearer ", ""));

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

    await page.route("**/api/v1/admin/settings?*", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          siteName: "TutorPutor Demo",
          maintenanceMode: false,
          registrationOpen: true,
          maxUsersPerTenant: 1000,
        }),
      });
    });

    await expectNoPageErrors(page, async () => {
      await page.goto(`${adminUrl}/settings`);
      await page.waitForLoadState("domcontentloaded");
    });

    await expect(
      page.getByRole("heading", { name: /admin settings/i }),
    ).toBeVisible();

    await expect(page.getByDisplayValue("TutorPutor Demo")).toBeVisible();
    await expect(page.getByRole("checkbox", { name: /maintenance mode/i })).not.toBeChecked();

    await page.getByRole("button", { name: /save settings/i }).click();

    await expect(page.getByText("Settings saved successfully")).toBeVisible();
  });
});
