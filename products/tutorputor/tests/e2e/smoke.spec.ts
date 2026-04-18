import { expect, test, type Page } from "@playwright/test";

const BASE_URL = process.env.BASE_URL ?? "http://localhost:5173";
const PLATFORM_URL = process.env.PLATFORM_URL ?? "http://localhost:7105";

async function expectVisibleShell(page: Page): Promise<void> {
  await expect(page.locator("nav").first()).toBeVisible();
  await expect(page.locator("main").first()).toBeVisible();
}

test.describe("TutorPutor smoke tests", () => {
  test("dashboard shell loads", async ({ page }) => {
    await page.goto(`${BASE_URL}/dashboard`);
    await page.waitForLoadState("domcontentloaded");

    await expectVisibleShell(page);
  });

  test("shared login entrypoint loads", async ({ page }) => {
    await page.goto(`${BASE_URL}/login`);
    await page.waitForLoadState("domcontentloaded");

    await expect(
      page.getByRole("heading", { name: "Sign in to TutorPutor" }),
    ).toBeVisible();
  });

  test("AI tutor page loads from the canonical route", async ({ page }) => {
    await page.goto(`${BASE_URL}/ai-tutor`);
    await page.waitForLoadState("domcontentloaded");

    await expectVisibleShell(page);
  });

  test("platform health endpoint is available", async ({ request }) => {
    const response = await request.get(`${PLATFORM_URL}/health`);
    expect(response.status()).toBe(200);
  });

  test("unknown routes do not resolve to retired learner flows", async ({
    page,
  }) => {
    await page.goto(`${BASE_URL}/non-existent-page`);
    await page.waitForLoadState("domcontentloaded");

    expect(page.url()).not.toContain("/generate");
  });
});
