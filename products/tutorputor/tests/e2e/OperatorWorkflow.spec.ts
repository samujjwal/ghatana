import { expect, test, type Page } from "@playwright/test";
import { createHmac } from "node:crypto";

const adminUrl = process.env.ADMIN_URL ?? "http://127.0.0.1:3202";
const gatewayUrl = process.env.GATEWAY_URL ?? "http://127.0.0.1:3200";
const jwtSecret =
  process.env.JWT_SECRET ?? "test-secret-do-not-use-in-prod-1234567890";

function createOperatorAccessToken(): string {
  const header = Buffer.from(
    JSON.stringify({ alg: "HS256", typ: "JWT" }),
  ).toString("base64url");
  const payload = Buffer.from(
    JSON.stringify({
      sub: "user-operator-001",
      email: "operator@tutorputor.com",
      name: "System Operator",
      role: "operator",
      tenantId: "system",
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
    Authorization: `Bearer ${createOperatorAccessToken()}`,
    "x-tenant-id": "system",
    "x-user-role": "operator",
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

test.describe("TutorPutor operator workflows", () => {
  test("system health monitoring and alert management", async ({ page, request }) => {
    const authHeaders = createAuthHeaders();

    await page.addInitScript((token: string) => {
      window.localStorage.setItem("auth_token", token);
      window.localStorage.setItem("refresh_token", "refresh-token-operator-1");
      window.localStorage.setItem("tenant_id", "system");
    }, authHeaders.Authorization.replace("Bearer ", ""));

    await page.route("**/api/v1/auth/me", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: "user-operator-001",
          email: "operator@tutorputor.com",
          role: "operator",
          firstName: "System",
          lastName: "Operator",
          fullName: "System Operator",
          tenantId: "system",
        }),
      });
    });

    await page.route("**/api/v1/operator/health?*", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          status: "healthy",
          services: {
            api: "healthy",
            database: "healthy",
            redis: "healthy",
            contentGeneration: "healthy",
          },
          uptime: 99.9,
          lastCheck: new Date().toISOString(),
        }),
      });
    });

    await page.route("**/api/v1/operator/alerts?*", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          alerts: [
            {
              id: "alert-001",
              severity: "warning",
              service: "contentGeneration",
              message: "High latency detected",
              createdAt: new Date().toISOString(),
            },
          ],
        }),
      });
    });

    await expectNoPageErrors(page, async () => {
      await page.goto(`${adminUrl}/operator/health`);
      await page.waitForLoadState("domcontentloaded");
    });

    await expect(
      page.getByRole("heading", { name: /system health/i }),
    ).toBeVisible();

    await expect(page.getByText("healthy")).toBeVisible();
    await expect(page.getByText("99.9%")).toBeVisible();

    await page.getByRole("link", { name: "Alerts" }).click();
    await page.waitForURL(/\/operator\/alerts$/);

    await expect(
      page.getByRole("heading", { name: /system alerts/i }),
    ).toBeVisible();
    await expect(page.getByText("High latency detected")).toBeVisible();
  });

  test("tenant management and provisioning", async ({ page, request }) => {
    const authHeaders = createAuthHeaders();

    await page.addInitScript((token: string) => {
      window.localStorage.setItem("auth_token", token);
      window.localStorage.setItem("refresh_token", "refresh-token-operator-2");
      window.localStorage.setItem("tenant_id", "system");
    }, authHeaders.Authorization.replace("Bearer ", ""));

    await page.route("**/api/v1/auth/me", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: "user-operator-001",
          email: "operator@tutorputor.com",
          role: "operator",
          firstName: "System",
          lastName: "Operator",
          fullName: "System Operator",
          tenantId: "system",
        }),
      });
    });

    await page.route("**/api/v1/operator/tenants?*", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          tenants: [
            {
              id: "tenant-001",
              name: "Demo School",
              status: "active",
              userCount: 150,
              createdAt: "2026-04-20T00:00:00.000Z",
            },
            {
              id: "tenant-002",
              name: "Test University",
              status: "active",
              userCount: 300,
              createdAt: "2026-04-20T00:00:00.000Z",
            },
          ],
        }),
      });
    });

    await page.route("**/api/v1/operator/tenants", async (route) => {
      if (route.request().method() === "POST") {
        await route.fulfill({
          status: 201,
          contentType: "application/json",
          body: JSON.stringify({
            id: "tenant-003",
            name: "New Tenant",
            status: "active",
          }),
        });
      }
    });

    await expectNoPageErrors(page, async () => {
      await page.goto(`${adminUrl}/operator/tenants`);
      await page.waitForLoadState("domcontentloaded");
    });

    await expect(
      page.getByRole("heading", { name: /tenant management/i }),
    ).toBeVisible();

    await expect(page.getByText("Demo School")).toBeVisible();
    await expect(page.getByText("Test University")).toBeVisible();

    await page.getByRole("button", { name: /create tenant/i }).click();

    await page.getByRole("textbox", { name: "Tenant Name" }).fill("New Tenant");
    await page.getByRole("button", { name: /create/i }).click();

    await expect(page.getByText("Tenant created successfully")).toBeVisible();
  });

  test("system metrics and observability dashboard", async ({ page }) => {
    const authHeaders = createAuthHeaders();

    await page.addInitScript((token: string) => {
      window.localStorage.setItem("auth_token", token);
      window.localStorage.setItem("refresh_token", "refresh-token-operator-3");
      window.localStorage.setItem("tenant_id", "system");
    }, authHeaders.Authorization.replace("Bearer ", ""));

    await page.route("**/api/v1/auth/me", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: "user-operator-001",
          email: "operator@tutorputor.com",
          role: "operator",
          firstName: "System",
          lastName: "Operator",
          fullName: "System Operator",
          tenantId: "system",
        }),
      });
    });

    await page.route("**/api/v1/operator/metrics?*", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          requestsPerSecond: 150,
          averageResponseTime: 45,
          errorRate: 0.01,
          activeConnections: 500,
          memoryUsage: 65,
          cpuUsage: 40,
        }),
      });
    });

    await expectNoPageErrors(page, async () => {
      await page.goto(`${adminUrl}/operator/metrics`);
      await page.waitForLoadState("domcontentloaded");
    });

    await expect(
      page.getByRole("heading", { name: /system metrics/i }),
    ).toBeVisible();

    await expect(page.getByText("150")).toBeVisible();
    await expect(page.getByText("45ms")).toBeVisible();
    await expect(page.getByText("1%")).toBeVisible();
  });
});
