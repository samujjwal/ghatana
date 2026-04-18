import { expect, test, type Page } from "@playwright/test";

const BASE_URL = process.env.BASE_URL ?? "http://localhost:5173";

const canonicalLearnerPaths = [
  "/dashboard",
  "/pathways",
  "/search",
  "/assessments",
  "/marketplace",
  "/collaboration",
  "/analytics",
  "/ai-tutor",
  "/teacher",
  "/settings",
  "/simulations",
] as const;

async function collectPageErrors(
  page: Page,
  action: () => Promise<void>,
): Promise<string[]> {
  const errors: string[] = [];
  page.on("pageerror", (error: Error) => {
    errors.push(error.message);
  });

  await action();
  return errors;
}

test.describe("TutorPutor canonical learner route flows", () => {
  test("shared login entrypoint supports admin redirect handoff", async ({
    page,
  }) => {
    await page.goto(`${BASE_URL}/login?redirect=/admin`);
    await page.waitForLoadState("domcontentloaded");

    await expect(
      page.getByRole("heading", { name: "Sign in to TutorPutor" }),
    ).toBeVisible();
    await expect(page).toHaveURL(/\/login\?redirect=%2Fadmin|\/login\?redirect=\/admin/);
  });

  for (const path of canonicalLearnerPaths) {
    test(`route ${path} resolves without uncaught page errors`, async ({
      page,
    }) => {
      const errors = await collectPageErrors(page, async () => {
        await page.goto(`${BASE_URL}${path}`);
        await page.waitForLoadState("domcontentloaded");
      });

      await expect(page.locator("main, nav").first()).toBeVisible();
      expect(errors).toEqual([]);
    });
  }

  test("legacy /modules alias resolves to the canonical browse surface", async ({
    page,
  }) => {
    await page.goto(`${BASE_URL}/modules`);
    await page.waitForLoadState("domcontentloaded");

    await expect(page).toHaveURL(/\/search$/);
  });
});
