import { expect, test } from "@playwright/test";

const learnerTargets = [
  { name: "learner-dashboard", path: "/" },
  { name: "module-page", path: "/modules/intro-to-motion" },
  { name: "assessment-page", path: "/assessments/demo" },
  { name: "ai-tutor", path: "/ai-tutor" },
];

test.describe("TutorPutor web visual regression", () => {
  for (const target of learnerTargets) {
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
