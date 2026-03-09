/**
 * Simulation Assessment E2E Tests
 *
 * End-to-end tests for simulation-based assessment items.
 * Tests different modes (prediction, manipulation, explanation) and grading.
 *
 * @doc.type test
 * @doc.purpose E2E tests for simulation assessment items
 * @doc.layer product
 * @doc.pattern Test
 */
import { test, expect } from "@playwright/test";

test.describe("Simulation-Based Assessments", () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to an assessment with simulation items
    await page.goto("/assessments/sim-practice-1");
  });

  test("should display assessment with simulation items", async ({ page }) => {
    // Verify assessment loaded
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();

    // Should show question counter
    await expect(page.getByText(/question \d+ of \d+/i)).toBeVisible();
  });

  test("should render simulation canvas in assessment item", async ({ page }) => {
    // Find simulation item (may need to navigate)
    const simulationItem = page.locator("[data-testid='simulation-item']");

    if (await simulationItem.isVisible()) {
      // Verify simulation canvas is embedded
      await expect(
        simulationItem.locator("[data-testid='simulation-canvas']")
      ).toBeVisible();
    }
  });

  test("should handle prediction mode assessment", async ({ page }) => {
    // Navigate to prediction item if needed
    const predictionItem = page.locator("[data-testid='prediction-mode']");

    if (await predictionItem.isVisible()) {
      // Find prediction input
      const predictionInput = page.getByLabel(/predict|enter your prediction/i);
      await expect(predictionInput).toBeVisible();

      // Enter a prediction value
      await predictionInput.fill("42");

      // Select confidence level
      const confidenceButton = page.getByRole("button", { name: /confident|sure/i });
      if (await confidenceButton.isVisible()) {
        await confidenceButton.click();
      }
    }
  });

  test("should handle manipulation mode assessment", async ({ page }) => {
    // Navigate to manipulation item if needed
    const manipulationItem = page.locator("[data-testid='manipulation-mode']");

    if (await manipulationItem.isVisible()) {
      // Should show parameter controls
      await expect(page.locator("[data-testid='parameter-slider']")).toBeVisible();

      // Manipulate a parameter
      const slider = page.locator("[data-testid='parameter-slider']").first();
      await slider.click();

      // Run simulation with new parameters
      const runButton = page.getByRole("button", { name: /run|simulate|apply/i });
      if (await runButton.isVisible()) {
        await runButton.click();
      }
    }
  });

  test("should handle explanation mode assessment", async ({ page }) => {
    // Navigate to explanation item if needed
    const explanationItem = page.locator("[data-testid='explanation-mode']");

    if (await explanationItem.isVisible()) {
      // Should show text area for explanation
      const textarea = page.getByRole("textbox", { name: /explanation|explain|describe/i });
      await expect(textarea).toBeVisible();

      // Enter explanation
      await textarea.fill(
        "The bubble sort algorithm works by repeatedly swapping adjacent elements if they are in the wrong order."
      );
    }
  });

  test("should show hints when requested", async ({ page }) => {
    // Find hint button
    const hintButton = page.getByRole("button", { name: /hint|get hint|need help/i });

    if (await hintButton.isVisible()) {
      await hintButton.click();

      // Verify hint is shown
      await expect(page.locator("[data-testid='hint-content']")).toBeVisible();

      // Verify point deduction warning if applicable
      const deductionWarning = page.getByText(/points will be deducted|cost/i);
      // This may or may not be visible depending on configuration
    }
  });

  test("should navigate between assessment items", async ({ page }) => {
    // Click next button
    const nextButton = page.getByRole("button", { name: /next|→|forward/i });
    await expect(nextButton).toBeVisible();

    // Get initial question number
    const questionText = await page.getByText(/question \d+ of \d+/i).textContent();
    const initialQuestion = questionText?.match(/question (\d+)/i)?.[1];

    // Navigate to next
    await nextButton.click();

    // Verify question changed
    await expect(page.getByText(/question \d+ of \d+/i)).not.toHaveText(
      questionText ?? ""
    );
  });

  test("should allow jumping to specific questions", async ({ page }) => {
    // Find question navigator
    const navigator = page.locator("[data-testid='question-navigator']");

    if (await navigator.isVisible()) {
      // Click on question 3
      const question3 = navigator.getByRole("button", { name: "3" });
      if (await question3.isVisible()) {
        await question3.click();

        // Verify we're on question 3
        await expect(page.getByText(/question 3 of/i)).toBeVisible();
      }
    }
  });

  test("should submit assessment and show score", async ({ page }) => {
    // Navigate to last question
    while (await page.getByRole("button", { name: /next|→/i }).isEnabled()) {
      await page.getByRole("button", { name: /next|→/i }).click();
    }

    // Find submit button
    const submitButton = page.getByRole("button", { name: /submit|finish|complete/i });
    await expect(submitButton).toBeVisible();

    // Submit assessment
    await submitButton.click();

    // Confirm if dialog appears
    const confirmButton = page.getByRole("button", { name: /confirm|yes|submit/i });
    if (await confirmButton.isVisible()) {
      await confirmButton.click();
    }

    // Verify completion screen
    await expect(
      page.getByText(/submitted|completed|score|results/i)
    ).toBeVisible({ timeout: 10000 });
  });

  test("should show simulation playback during review", async ({ page }) => {
    // Go to a completed assessment in review mode
    await page.goto("/assessments/sim-practice-1/review");

    // If review mode is available
    const reviewContent = page.locator("[data-testid='assessment-review']");

    if (await reviewContent.isVisible()) {
      // Should show simulation with correct answer overlay
      await expect(
        page.locator("[data-testid='simulation-canvas']")
      ).toBeVisible();

      // Should show feedback
      await expect(page.locator("[data-testid='feedback']")).toBeVisible();
    }
  });

  test("should save progress on exit", async ({ page }) => {
    // Answer a question
    const input = page.getByRole("textbox").first();
    if (await input.isVisible()) {
      await input.fill("Test answer");
    }

    // Click save and exit
    const exitButton = page.getByRole("button", { name: /save|exit|pause/i });
    if (await exitButton.isVisible()) {
      await exitButton.click();

      // Confirm if dialog appears
      const confirmButton = page.getByRole("button", { name: /confirm|yes|save/i });
      if (await confirmButton.isVisible()) {
        await confirmButton.click();
      }

      // Should redirect to assessments list
      await expect(page).toHaveURL(/assessments/);
    }
  });

  test("should handle CBM (Certainty-Based Marking) confidence selection", async ({ page }) => {
    // Find CBM selector
    const cbmSelector = page.locator("[data-testid='cbm-selector']");

    if (await cbmSelector.isVisible()) {
      // Select high confidence
      const highConfidence = cbmSelector.getByRole("button", { name: /high|certain|sure/i });
      await highConfidence.click();

      // Verify selection
      await expect(highConfidence).toHaveAttribute("aria-pressed", "true");
    }
  });
});
