/**
 * CMS Authoring E2E Tests
 * 
 * Tests for the CMS module creation, editing, and publishing flow.
 * Covers Day 7/8 acceptance criteria.
 * 
 * @doc.type test
 * @doc.purpose E2E tests for CMS authoring
 * @doc.layer product
 * @doc.pattern Test
 */
import { test, expect } from "@playwright/test";

test.describe("CMS Module Authoring", () => {
    test.beforeEach(async ({ page }) => {
        // Navigate to CMS modules page
        await page.goto("/cms");
    });

    test("should display CMS modules list page", async ({ page }) => {
        // Verify page header
        await expect(page.getByRole("heading", { name: /content management/i })).toBeVisible();

        // Verify create button is present
        await expect(page.getByRole("button", { name: /create new module/i })).toBeVisible();

        // Verify status tabs
        await expect(page.getByRole("button", { name: /all/i })).toBeVisible();
        await expect(page.getByRole("button", { name: /drafts/i })).toBeVisible();
        await expect(page.getByRole("button", { name: /published/i })).toBeVisible();
    });

    test("should navigate to create new module page", async ({ page }) => {
        await page.getByRole("button", { name: /create new module/i }).click();

        // Should navigate to /cms/new
        await expect(page).toHaveURL(/\/cms\/new/);

        // Verify form elements
        await expect(page.getByLabel(/slug/i)).toBeVisible();
        await expect(page.getByLabel(/title/i)).toBeVisible();
        await expect(page.getByLabel(/description/i)).toBeVisible();
    });

    test("should fill in module metadata form", async ({ page }) => {
        await page.goto("/cms/new");

        // Fill basic info
        await page.getByLabel(/slug/i).fill("test-module-e2e");
        await page.getByLabel(/title/i).fill("E2E Test Module");
        await page.getByLabel(/description/i).fill("A module created during E2E testing");

        // Select domain and difficulty
        await page.getByLabel(/domain/i).selectOption("computer_science");
        await page.getByLabel(/difficulty/i).selectOption("beginner");

        // Set estimated time
        await page.getByLabel(/estimated time/i).fill("30");

        // Verify values are set
        await expect(page.getByLabel(/slug/i)).toHaveValue("test-module-e2e");
        await expect(page.getByLabel(/title/i)).toHaveValue("E2E Test Module");
    });

    test("should add and remove tags", async ({ page }) => {
        await page.goto("/cms/new");

        // Add a tag
        await page.getByPlaceholder(/add tag/i).fill("algorithms");
        await page.getByRole("button", { name: /^add$/i }).click();

        // Verify tag appears
        await expect(page.getByText("algorithms")).toBeVisible();

        // Add another tag
        await page.getByPlaceholder(/add tag/i).fill("sorting");
        await page.getByRole("button", { name: /^add$/i }).click();

        // Verify both tags
        await expect(page.getByText("algorithms")).toBeVisible();
        await expect(page.getByText("sorting")).toBeVisible();

        // Remove first tag
        const algorithmTag = page.locator("span", { hasText: "algorithms" });
        await algorithmTag.getByRole("button").click();

        // Verify tag removed
        await expect(page.getByText("algorithms")).not.toBeVisible();
        await expect(page.getByText("sorting")).toBeVisible();
    });

    test("should add learning objectives", async ({ page }) => {
        await page.goto("/cms/new");

        // Click add objective
        await page.getByRole("button", { name: /\+ add/i }).click();

        // Fill objective
        await page.getByPlaceholder(/objective description/i).first().fill("Understand bubble sort algorithm");

        // Select taxonomy level
        await page.locator("select").last().selectOption("UNDERSTAND");

        // Add another objective
        await page.getByRole("button", { name: /\+ add/i }).click();
        await page.getByPlaceholder(/objective description/i).last().fill("Apply sorting to real problems");

        // Verify objectives are present using input values
        await expect(page.locator("input[value='Understand bubble sort algorithm']")).toBeVisible();
        await expect(page.locator("input[value='Apply sorting to real problems']")).toBeVisible();
    });
});

test.describe("Content Block Management", () => {
    test.beforeEach(async ({ page }) => {
        await page.goto("/cms/new");
        // Fill required fields
        await page.getByLabel(/slug/i).fill("block-test-module");
        await page.getByLabel(/title/i).fill("Block Test Module");
    });

    test("should show block picker when clicking add block", async ({ page }) => {
        await page.getByRole("button", { name: /\+ add block/i }).click();

        // Verify block picker modal/overlay appears
        await expect(page.getByText(/text/i)).toBeVisible();
        await expect(page.getByText(/simulation/i)).toBeVisible();
        await expect(page.getByText(/video/i)).toBeVisible();
        await expect(page.getByText(/exercise/i)).toBeVisible();
    });

    test("should add text block", async ({ page }) => {
        await page.getByRole("button", { name: /\+ add block/i }).click();

        // Click text block option
        await page.locator("button", { hasText: "📝" }).click();

        // Verify block was added
        await expect(page.getByText(/text/i)).toBeVisible();
        await expect(page.getByText(/no content blocks yet/i)).not.toBeVisible();
    });

    test("should add simulation block", async ({ page }) => {
        await page.getByRole("button", { name: /\+ add block/i }).click();

        // Click simulation block option
        await page.locator("button", { hasText: "🎮" }).click();

        // Verify simulation block was added
        await expect(page.getByText(/simulation/i)).toBeVisible();
    });

    test("should cancel block picker", async ({ page }) => {
        await page.getByRole("button", { name: /\+ add block/i }).click();

        // Click cancel
        await page.getByRole("button", { name: /cancel/i }).click();

        // Verify block picker is hidden
        await expect(page.getByText(/no content blocks yet/i)).toBeVisible();
    });
});

test.describe("Simulation Block Editor", () => {
    test.beforeEach(async ({ page }) => {
        await page.goto("/cms/new");
        await page.getByLabel(/slug/i).fill("sim-test-module");
        await page.getByLabel(/title/i).fill("Simulation Test Module");

        // Add simulation block
        await page.getByRole("button", { name: /\+ add block/i }).click();
        await page.locator("button", { hasText: "🎮" }).click();
    });

    test("should open simulation block editor", async ({ page }) => {
        // Click edit on simulation block
        await page.getByRole("button", { name: /edit/i }).first().click();

        // Verify editor tabs
        await expect(page.getByRole("button", { name: /visual/i })).toBeVisible();
        await expect(page.getByRole("button", { name: /json/i })).toBeVisible();
        await expect(page.getByRole("button", { name: /preview/i })).toBeVisible();
    });

    test("should select simulation domain", async ({ page }) => {
        await page.getByRole("button", { name: /edit/i }).first().click();

        // Select physics domain
        await page.getByLabel(/domain/i).selectOption("PHYSICS");

        // Verify domain changed
        await expect(page.getByLabel(/domain/i)).toHaveValue("PHYSICS");
    });

    test("should add entity to simulation", async ({ page }) => {
        await page.getByRole("button", { name: /edit/i }).first().click();

        // Click add entity
        await page.getByRole("button", { name: /add entity/i }).click();

        // Verify entity count increased
        await expect(page.getByText(/1 entities/i)).toBeVisible();
    });

    test("should switch to JSON editor tab", async ({ page }) => {
        await page.getByRole("button", { name: /edit/i }).first().click();

        // Click JSON tab
        await page.getByRole("button", { name: /json/i }).click();

        // Verify JSON editor is visible (textarea with JSON content)
        await expect(page.locator("textarea")).toBeVisible();
    });

    test("should show preview tab", async ({ page }) => {
        await page.getByRole("button", { name: /edit/i }).first().click();

        // Click preview tab
        await page.getByRole("button", { name: /preview/i }).click();

        // Verify preview panel
        await expect(page.getByText(/preview/i)).toBeVisible();
    });
});

test.describe("Module Save and Publish", () => {
    test("should save module as draft", async ({ page }) => {
        await page.goto("/cms/new");

        // Fill required fields
        await page.getByLabel(/slug/i).fill("save-test-module");
        await page.getByLabel(/title/i).fill("Save Test Module");
        await page.getByLabel(/description/i).fill("Testing save functionality");

        // Click save draft
        await page.getByRole("button", { name: /save draft/i }).click();

        // Should show saving state or navigate
        await expect(page.getByRole("button", { name: /saving/i })).toBeVisible();
    });

    test("should validate required fields before save", async ({ page }) => {
        await page.goto("/cms/new");

        // Try to save without filling required fields
        await page.getByRole("button", { name: /save draft/i }).click();

        // Should show validation (title is required)
        // Note: Actual validation UI depends on implementation
        const titleInput = page.getByLabel(/title/i);
        await expect(titleInput).toHaveAttribute("required");
    });

    test("should cancel and return to modules list", async ({ page }) => {
        await page.goto("/cms/new");

        // Click cancel
        await page.getByRole("button", { name: /cancel/i }).click();

        // Should navigate back to /cms
        await expect(page).toHaveURL(/\/cms$/);
    });
});
