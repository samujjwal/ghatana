import { expect, test } from "@playwright/test";

const adminTargets = [
  { name: "admin-authoring-page", path: "/authoring" },
];

test.describe("TutorPutor admin visual regression", () => {
  for (const target of adminTargets) {
    test(`${target.name} matches the design-system baseline`, async ({ page }) => {
      await page.goto(target.path);
      await page.waitForLoadState("networkidle");
      await expect(page).toHaveScreenshot(`${target.name}.png`, {
        fullPage: true,
        animations: "disabled",
        maxDiffPixelRatio: 0.02,
      });
    });
  }
});
