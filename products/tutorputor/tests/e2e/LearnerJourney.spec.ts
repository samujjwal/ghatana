import { expect, test, type Page } from "@playwright/test";

const learnerBaseUrl = process.env.BASE_URL ?? "http://127.0.0.1:3201";
const gatewayUrl = process.env.GATEWAY_URL ?? "http://127.0.0.1:3200";
const platformUrl = process.env.PLATFORM_URL ?? "http://127.0.0.1:7105";

async function expectNoPageErrors(page: Page, action: () => Promise<void>) {
  const errors: string[] = [];
  page.on("pageerror", (error: Error) => {
    errors.push(error.message);
  });

  await action();
  expect(errors).toEqual([]);
}

test.describe("TutorPutor learner journey", () => {
  test("supports the canonical learner progression from login through learning surfaces", async ({
    page,
    request,
  }) => {
    const platformHealth = await request.get(`${platformUrl}/health`);
    expect(platformHealth.ok()).toBe(true);

    const gatewayHealth = await request.get(`${gatewayUrl}/health`);
    expect(gatewayHealth.ok()).toBe(true);

    await expectNoPageErrors(page, async () => {
      await page.goto(`${learnerBaseUrl}/login?redirect=/dashboard`);
      await page.waitForLoadState("domcontentloaded");
    });

    await expect(
      page.getByRole("heading", { name: /sign in to tutorputor/i }),
    ).toBeVisible();

    await page.goto(`${learnerBaseUrl}/dashboard`);
    await expect(page.getByRole("link", { name: "Dashboard" })).toBeVisible();
    await expect(
      page.getByRole("link", { name: "Learning Paths" }),
    ).toBeVisible();
    await expect(
      page.getByRole("link", { name: "Browse Content" }),
    ).toBeVisible();
    await expect(page.getByRole("link", { name: "AI Tutor" })).toBeVisible();

    await page.getByRole("link", { name: "Learning Paths" }).click();
    await expect(page).toHaveURL(/\/pathways$/);

    await page.getByRole("link", { name: "Browse Content" }).click();
    await expect(page).toHaveURL(/\/search$/);
    await expect(page.locator("main")).toBeVisible();

    await page.getByRole("link", { name: "AI Tutor" }).click();
    await expect(page).toHaveURL(/\/ai-tutor$/);
    await expect(
      page.getByRole("heading", { name: "AI Tutor" }),
    ).toBeVisible();
    await expect(page.getByRole("textbox").last()).toBeVisible();

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
