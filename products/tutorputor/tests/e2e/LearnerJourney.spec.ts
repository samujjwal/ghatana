import { expect, test, type Page } from "@playwright/test";

const learnerBaseUrl = process.env.BASE_URL ?? "http://127.0.0.1:3201";
const gatewayUrl = process.env.GATEWAY_URL ?? "http://127.0.0.1:3200";
const platformUrl = process.env.PLATFORM_URL ?? "http://127.0.0.1:7105";

function createJwt(overrides: Record<string, unknown> = {}): string {
  const header = Buffer.from(
    JSON.stringify({ alg: "HS256", typ: "JWT" }),
  ).toString("base64url");
  const payload = Buffer.from(
    JSON.stringify({
      sub: "learner-123",
      email: "learner@example.com",
      name: "Jordan Learner",
      role: "student",
      tenantId: "tenant-school",
      ...overrides,
    }),
  ).toString("base64url");

  return `${header}.${payload}.signature`;
}

async function expectNoPageErrors(page: Page, action: () => Promise<void>) {
  const errors: string[] = [];
  page.on("pageerror", (error: Error) => {
    errors.push(error.message);
  });

  await action();
  expect(errors).toEqual([]);
}

test.describe("TutorPutor learner journey", () => {
  test("supports tenant-aware learner sign-in bootstrap and canonical learning surfaces", async ({
    page,
    request,
  }) => {
    const accessToken = createJwt();
    const refreshToken = 'refresh-token-123';
    const meAuthHeaders: string[] = [];

    const platformHealth = await request.get(`${platformUrl}/health`);
    expect(platformHealth.ok()).toBe(true);

    const gatewayHealth = await request.get(`${gatewayUrl}/health`);
    expect(gatewayHealth.ok()).toBe(true);

    await page.route('**/api/v1/auth/sso/providers?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          providers: [
            {
              id: 'google-workspace',
              displayName: 'Google Workspace',
              type: 'oidc',
            },
          ],
        }),
      });
    });

    await page.route('**/api/v1/auth/sso/login/google-workspace?*', async (route) => {
      await route.fulfill({
        status: 302,
        headers: {
          location: `${learnerBaseUrl}/dashboard?accessToken=${encodeURIComponent(accessToken)}&refreshToken=${encodeURIComponent(refreshToken)}`,
        },
        body: '',
      });
    });

    await page.route('**/api/v1/auth/me', async (route) => {
      const authorization = route.request().headers()['authorization'];
      if (authorization) {
        meAuthHeaders.push(authorization);
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'learner-123',
          email: 'learner@example.com',
          displayName: 'Jordan Learner',
          role: 'student',
          tenantId: 'tenant-school',
        }),
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
      page.getByRole('button', { name: /sign in with google workspace/i }),
    ).toBeVisible();

    await page.getByRole('button', { name: /sign in with google workspace/i }).click();
    await page.waitForURL(/\/dashboard$/);

    await expect(page.getByRole("link", { name: "Dashboard" })).toBeVisible();
    await expect(
      page.getByRole("link", { name: "Learning Paths" }),
    ).toBeVisible();
    await expect(
      page.getByRole("link", { name: "Browse Content" }),
    ).toBeVisible();

    await expect.poll(async () => {
      return page.evaluate(() => window.location.search);
    }).toBe('');
    await expect.poll(async () => {
      return page.evaluate(() => window.localStorage.getItem('auth_token'));
    }).toBe(accessToken);
    await expect.poll(async () => {
      return page.evaluate(() => window.localStorage.getItem('refresh_token'));
    }).toBe(refreshToken);
    await expect.poll(async () => {
      return page.evaluate(() => window.localStorage.getItem('tenant_id'));
    }).toBe('tenant-school');
    expect(meAuthHeaders).toContain(`Bearer ${accessToken}`);

    await page.reload();
    await page.waitForLoadState('domcontentloaded');
    await expect(page).toHaveURL(/\/dashboard$/);
    expect(meAuthHeaders.length).toBeGreaterThanOrEqual(2);

    await page.getByRole("link", { name: "Learning Paths" }).click();
    await expect(page).toHaveURL(/\/pathways$/);

    await page.getByRole("link", { name: "Browse Content" }).click();
    await expect(page).toHaveURL(/\/search$/);
    await expect(page.locator("main")).toBeVisible();

    await page.goto(`${learnerBaseUrl}/simulations`);
    await expect(
      page.getByRole("heading", { name: "Simulations" }),
    ).toBeVisible();

    await page.goto(`${learnerBaseUrl}/assessments`);
    await expect(
      page.getByRole("heading", { name: "Assessments" }),
    ).toBeVisible();
    await expect(page.getByText("No assessments found.")).toBeVisible();

    await page.goto(`${learnerBaseUrl}/modules`);
    await expect(page).toHaveURL(/\/search$/);
  });
});
