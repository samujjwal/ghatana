import { expect, test, type Page } from "@playwright/test";
import { createHmac } from "node:crypto";

const learnerBaseUrl = process.env.BASE_URL ?? "http://127.0.0.1:3201";
const gatewayUrl = process.env.GATEWAY_URL ?? "http://127.0.0.1:3200";
const jwtSecret =
  process.env.JWT_SECRET ?? "test-secret-do-not-use-in-prod-1234567890";

function createAccessToken(userId: string, role: string): string {
  const header = Buffer.from(
    JSON.stringify({ alg: "HS256", typ: "JWT" }),
  ).toString("base64url");
  const payload = Buffer.from(
    JSON.stringify({
      sub: userId,
      email: `${userId}@example.com`,
      name: role === "admin" ? "Admin User" : "Test User",
      role,
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

test.describe("TutorPutor privacy and compliance workflows", () => {
  test("user consent management and privacy settings", async ({ page }) => {
    const accessToken = createAccessToken("user-001", "student");

    await page.addInitScript((token: string) => {
      window.localStorage.setItem("auth_token", token);
      window.localStorage.setItem("tenant_id", "default");
    }, accessToken);

    await page.route("**/api/v1/auth/me", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: "user-001",
          email: "user@example.com",
          role: "student",
          displayName: "Test User",
          tenantId: "default",
        }),
      });
    });

    await page.route("**/api/v1/consents?*", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          consents: [
            {
              category: "essential",
              granted: true,
              required: true,
            },
            {
              category: "analytics",
              granted: true,
              required: false,
            },
            {
              category: "ai_processing",
              granted: false,
              required: false,
            },
          ],
        }),
      });
    });

    await page.route("**/api/v1/consents", async (route) => {
      if (route.request().method() === "PUT") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ success: true }),
        });
      }
    });

    await expectNoPageErrors(page, async () => {
      await page.goto(`${learnerBaseUrl}/settings/privacy`);
      await page.waitForLoadState("domcontentloaded");
    });

    await expect(
      page.getByRole("heading", { name: /privacy settings/i }),
    ).toBeVisible();

    await expect(
      page.getByRole("checkbox", { name: /essential/i }),
    ).toBeChecked();
    await expect(
      page.getByRole("checkbox", { name: /analytics/i }),
    ).toBeChecked();
    await expect(
      page.getByRole("checkbox", { name: /ai processing/i }),
    ).not.toBeChecked();

    await page.getByRole("checkbox", { name: /ai processing/i }).click();

    await page.getByRole("button", { name: /save preferences/i }).click();

    await expect(page.getByText("Preferences saved")).toBeVisible();
  });

  test("data export and GDPR compliance", async ({ page }) => {
    const accessToken = createAccessToken("user-002", "student");

    await page.addInitScript((token: string) => {
      window.localStorage.setItem("auth_token", token);
      window.localStorage.setItem("tenant_id", "default");
    }, accessToken);

    await page.route("**/api/v1/auth/me", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: "user-002",
          email: "user@example.com",
          role: "student",
          displayName: "Test User",
          tenantId: "default",
        }),
      });
    });

    await page.route("**/api/v1/privacy/data-export", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          exportId: "export-001",
          status: "processing",
          estimatedCompletion: "2026-04-20T01:00:00.000Z",
        }),
      });
    });

    await page.route("**/api/v1/privacy/data-export/export-001", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          exportId: "export-001",
          status: "completed",
          downloadUrl: "/api/v1/privacy/data-export/export-001/download",
          completedAt: new Date().toISOString(),
        }),
      });
    });

    await expectNoPageErrors(page, async () => {
      await page.goto(`${learnerBaseUrl}/settings/privacy/data-export`);
      await page.waitForLoadState("domcontentloaded");
    });

    await expect(
      page.getByRole("heading", { name: /data export/i }),
    ).toBeVisible();

    await page.getByRole("button", { name: /request data export/i }).click();

    await expect(page.getByText("Export requested")).toBeVisible();

    await page.getByRole("button", { name: /refresh status/i }).click();

    await expect(page.getByText("completed")).toBeVisible();
  });

  test("account deletion and right to be forgotten", async ({ page }) => {
    const accessToken = createAccessToken("user-003", "student");

    await page.addInitScript((token: string) => {
      window.localStorage.setItem("auth_token", token);
      window.localStorage.setItem("tenant_id", "default");
    }, accessToken);

    await page.route("**/api/v1/auth/me", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: "user-003",
          email: "user@example.com",
          role: "student",
          displayName: "Test User",
          tenantId: "default",
        }),
      });
    });

    await page.route("**/api/v1/privacy/account-deletion", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          deletionId: "deletion-001",
          status: "scheduled",
          scheduledFor: "2026-04-27T00:00:00.000Z",
        }),
      });
    });

    await expectNoPageErrors(page, async () => {
      await page.goto(`${learnerBaseUrl}/settings/privacy/account-deletion`);
      await page.waitForLoadState("domcontentloaded");
    });

    await expect(
      page.getByRole("heading", { name: /account deletion/i }),
    ).toBeVisible();

    await expect(
      page.getByText(/this action cannot be undone/i),
    ).toBeVisible();

    await page.getByRole("textbox", { name: "DELETE" }).fill("DELETE");

    await page.getByRole("button", { name: /confirm deletion/i }).click();

    await expect(page.getByText("Deletion scheduled")).toBeVisible();
  });

  test("admin privacy audit and compliance reporting", async ({ page, request }) => {
    const adminToken = createAccessToken("admin-001", "admin");
    const authHeaders = { Authorization: `Bearer ${adminToken}` };

    await page.addInitScript((token: string) => {
      window.localStorage.setItem("auth_token", token);
      window.localStorage.setItem("tenant_id", "default");
    }, adminToken);

    await page.route("**/api/v1/auth/me", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: "admin-001",
          email: "admin@example.com",
          role: "admin",
          displayName: "Admin User",
          tenantId: "default",
        }),
      });
    });

    await page.route("**/api/v1/admin/privacy/audit?*", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          events: [
            {
              id: "audit-001",
              userId: "user-001",
              action: "consent_updated",
              category: "analytics",
              timestamp: new Date().toISOString(),
            },
            {
              id: "audit-002",
              userId: "user-002",
              action: "data_export_requested",
              timestamp: new Date().toISOString(),
            },
          ],
        }),
      });
    });

    await page.route("**/api/v1/admin/privacy/compliance-report", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          reportId: "report-001",
          generatedAt: new Date().toISOString(),
          summary: {
            totalUsers: 150,
            consentsGranted: 140,
            dataExportsRequested: 5,
            deletionRequests: 2,
          },
        }),
      });
    });

    await expectNoPageErrors(page, async () => {
      await page.goto(`${learnerBaseUrl}/admin/privacy/audit`);
      await page.waitForLoadState("domcontentloaded");
    });

    await expect(
      page.getByRole("heading", { name: /privacy audit/i }),
    ).toBeVisible();

    await expect(page.getByText("consent_updated")).toBeVisible();
    await expect(page.getByText("data_export_requested")).toBeVisible();

    await page.getByRole("button", { name: /generate compliance report/i }).click();

    await expect(page.getByText("Report generated")).toBeVisible();
  });
});
