import AxeBuilder from "@axe-core/playwright";
import { expect, type Page, test } from "@playwright/test";

async function mockAdminA11yApis(page: Page) {
  await page.route(/\/(api|admin\/api)\//, async (route) => {
    const url = new URL(route.request().url());

    if (url.pathname.includes("/api/learning/units")) {
      return route.fulfill({
        json: {
          units: [],
          items: [],
          data: [],
        },
      });
    }

    if (url.pathname.includes("/api/content/versions")) {
      return route.fulfill({ json: { versions: [] } });
    }

    return route.fulfill({ json: {} });
  });
}

async function expectNoCriticalA11yViolations(page: Page, label: string) {
  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
    .analyze();

  const criticalViolations = results.violations.filter((violation) => violation.impact === "critical");
  expect(criticalViolations, `${label} has critical accessibility violations`).toEqual([]);
}

test.describe("automated admin accessibility gates", () => {
  test.beforeEach(async ({ page }) => {
    await mockAdminA11yApis(page);
  });

  test("authoring workflow has no critical axe violations", async ({ page }) => {
    await page.goto("/authoring");

    await expect(page.getByRole("heading", { name: "TutorPutor" })).toBeVisible();
    await expect(page.getByText(/Content Studio|Authoring|Publish Readiness/i).first()).toBeVisible();
    await page.getByRole("button", { name: /skip tour/i }).first().click();

    await expectNoCriticalA11yViolations(page, "admin authoring workflow");
  });

  test("authoring navigation exposes keyboard focus and skip-link access", async ({ page }) => {
    await page.goto("/authoring");
    await page.getByRole("button", { name: /skip tour/i }).first().click();

    await page.keyboard.press("Tab");
    await expect(page.getByRole("link", { name: /skip to main content/i })).toBeFocused();
    await page.keyboard.press("Enter");

    await expect(page).toHaveURL(/#main-content$/);

    await page.keyboard.press("Tab");
    await expect(page.getByRole("link", { name: /authoring/i })).toBeVisible();
  });
});
