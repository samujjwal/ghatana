import { expect, test } from "@playwright/test";

test.describe("Ghatana Studio navigation", () => {
  test("loads home and navigates to blueprint and canvas views", async ({
    page,
  }) => {
    await page.goto("/");

    await expect(page.getByRole("heading", { name: "Home" })).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "Product development journey" }),
    ).toBeVisible();

    await page.goto("/blueprints");
    await expect(page).toHaveURL(/\/blueprints$/);
    await expect(
      page.getByText("intent:yappc:commerce-studio:corr-yappc-1").first(),
    ).toBeVisible();

    await page.goto("/canvas");
    await expect(page).toHaveURL(/\/canvas$/);
    await expect(
      page.getByText("evidence:graph-commerce").first(),
    ).toBeVisible();
  });

  test("shows disabled access message for ideas in unconfigured mode", async ({
    page,
  }) => {
    await page.goto("/ideas");

    await expect(
      page.getByText("Route access is disabled in this runtime mode."),
    ).toBeVisible();
    await expect(page.getByText(/Required capability:/)).toBeVisible();
  });

  test("keeps lifecycle route fail-closed when Kernel runtime is unconfigured", async ({
    page,
  }) => {
    await page.goto("/lifecycle");

    await expect(
      page.getByRole("heading", { name: "Lifecycle" }),
    ).toBeVisible();
    await expect(
      page.getByText("Route access is disabled in this runtime mode."),
    ).toBeVisible();
    await expect(
      page.getByText("Required capability: kernel.lifecycle.view"),
    ).toBeVisible();
  });

  test("shows opening lifecycle pilots with actionable bootstrap truth", async ({
    page,
  }) => {
    await page.goto("/lifecycle/pilots");

    await expect(
      page.getByRole("heading", { name: "Opening lifecycle pilots" }),
    ).toBeVisible();
    await expect(
      page.getByLabel("Digital Marketing opening pilot"),
    ).toBeVisible();
    await expect(page.getByLabel("PHR opening pilot")).toBeVisible();
    await expect(page.getByText("gate:bridge-compliance")).toBeVisible();
    await expect(page.getByText("gate:consent")).toBeVisible();
    await expect(page.getByText("not reported").first()).toBeVisible();

    await page.getByLabel("Pilot filter").selectOption("phr");
    await expect(page.getByLabel("PHR opening pilot")).toBeVisible();
    await expect(
      page.getByLabel("Digital Marketing opening pilot"),
    ).toHaveCount(0);

    await page.getByLabel("Pilot filter").focus();
    await expect(page.getByLabel("Pilot filter")).toBeFocused();
    await page.keyboard.press("Tab");
    await expect(page.getByLabel("PHR opening pilot")).toBeFocused();
  });
});
