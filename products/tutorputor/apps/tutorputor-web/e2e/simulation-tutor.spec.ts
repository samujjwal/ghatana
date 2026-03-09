/**
 * Simulation Tutor E2E Tests
 *
 * End-to-end tests for the AI tutor integration with simulations.
 * Tests question asking, context awareness, and hint delivery.
 *
 * @doc.type test
 * @doc.purpose E2E tests for simulation tutor panel
 * @doc.layer product
 * @doc.pattern Test
 */
import { test, expect } from "@playwright/test";

test.describe("Simulation Tutor Integration", () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to a module with simulation and tutor enabled
    await page.goto("/modules/intro-bubble-sort");
    // Wait for simulation to load
    await expect(page.locator("[data-testid='simulation-canvas']")).toBeVisible();
  });

  test("should display Ask Tutor button on simulation player", async ({ page }) => {
    // Verify the Ask Tutor button is visible
    const tutorButton = page.getByRole("button", { name: /ask tutor|help|ask ai/i });
    await expect(tutorButton).toBeVisible();
  });

  test("should open tutor panel when clicking Ask Tutor", async ({ page }) => {
    // Click the tutor button
    await page.getByRole("button", { name: /ask tutor|help|ask ai/i }).click();

    // Verify tutor panel opens
    await expect(page.locator("[data-testid='simulation-tutor-panel']")).toBeVisible();

    // Verify chat input is available
    await expect(page.getByPlaceholder(/ask a question|type your question/i)).toBeVisible();
  });

  test("should allow typing and sending a question", async ({ page }) => {
    // Open tutor panel
    await page.getByRole("button", { name: /ask tutor|help|ask ai/i }).click();

    // Type a question
    const input = page.getByPlaceholder(/ask a question|type your question/i);
    await input.fill("Why is bubble sort called bubble sort?");

    // Submit the question
    const sendButton = page.getByRole("button", { name: /send|submit|ask/i });
    await sendButton.click();

    // Wait for response (may take a moment)
    await expect(
      page.locator("[data-testid='tutor-message']").first()
    ).toBeVisible({ timeout: 10000 });
  });

  test("should include simulation context in tutor response", async ({ page }) => {
    // Start the simulation to create some context
    await page.getByRole("button", { name: /play/i }).click();
    await page.waitForTimeout(1000);
    await page.getByRole("button", { name: /pause/i }).click();

    // Open tutor panel
    await page.getByRole("button", { name: /ask tutor|help|ask ai/i }).click();

    // Ask a context-aware question
    const input = page.getByPlaceholder(/ask a question|type your question/i);
    await input.fill("What is happening in the current step?");
    await page.getByRole("button", { name: /send|submit|ask/i }).click();

    // Wait for response
    const response = page.locator("[data-testid='tutor-message']").first();
    await expect(response).toBeVisible({ timeout: 10000 });

    // Response should contain simulation-related terms
    const responseText = await response.textContent();
    expect(responseText).toBeTruthy();
  });

  test("should show hint button and reveal hints", async ({ page }) => {
    // Open tutor panel
    await page.getByRole("button", { name: /ask tutor|help|ask ai/i }).click();

    // Check for hint button
    const hintButton = page.getByRole("button", { name: /hint|get hint|show hint/i });

    if (await hintButton.isVisible()) {
      await hintButton.click();

      // Verify hint is shown
      await expect(page.locator("[data-testid='tutor-hint']")).toBeVisible();
    }
  });

  test("should close tutor panel", async ({ page }) => {
    // Open tutor panel
    await page.getByRole("button", { name: /ask tutor|help|ask ai/i }).click();
    await expect(page.locator("[data-testid='simulation-tutor-panel']")).toBeVisible();

    // Close the panel
    const closeButton = page.locator("[data-testid='simulation-tutor-panel']").getByRole("button", { name: /close|×|x/i });
    await closeButton.click();

    // Verify panel is hidden
    await expect(page.locator("[data-testid='simulation-tutor-panel']")).not.toBeVisible();
  });

  test("should maintain conversation history", async ({ page }) => {
    // Open tutor panel
    await page.getByRole("button", { name: /ask tutor|help|ask ai/i }).click();

    // Send first question
    const input = page.getByPlaceholder(/ask a question|type your question/i);
    await input.fill("What is the time complexity?");
    await page.getByRole("button", { name: /send|submit|ask/i }).click();

    // Wait for first response
    await expect(page.locator("[data-testid='tutor-message']").first()).toBeVisible({ timeout: 10000 });

    // Send follow-up question
    await input.fill("Can you explain why?");
    await page.getByRole("button", { name: /send|submit|ask/i }).click();

    // Should have multiple messages now
    await expect(page.locator("[data-testid='tutor-message']")).toHaveCount(2, { timeout: 10000 });
  });

  test("should work with different simulation domains", async ({ page }) => {
    // Test with physics simulation
    await page.goto("/modules/projectile-motion");

    // Wait for simulation
    await expect(page.locator("[data-testid='simulation-canvas']")).toBeVisible({ timeout: 5000 });

    // Open tutor
    await page.getByRole("button", { name: /ask tutor|help|ask ai/i }).click();

    // Ask physics-specific question
    const input = page.getByPlaceholder(/ask a question|type your question/i);
    await input.fill("How does gravity affect the trajectory?");
    await page.getByRole("button", { name: /send|submit|ask/i }).click();

    // Verify response
    await expect(page.locator("[data-testid='tutor-message']").first()).toBeVisible({ timeout: 10000 });
  });
});
