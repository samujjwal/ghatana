/**
 * AI Tutor Widget E2E Tests
 *
 * Tests for the Omnipresent AI Tutor floating widget including:
 * - Open/close functionality
 * - Context awareness (page-specific behavior)
 * - Quick action buttons
 * - Chat functionality
 * - Proactive help indicator
 *
 * @doc.type test
 * @doc.purpose E2E tests for omnipresent AI tutor widget
 * @doc.layer product
 * @doc.pattern Test
 */
import { test, expect } from "@playwright/test";

test.describe("AI Tutor Widget", () => {
  test.describe("Widget Visibility", () => {
    test("should display floating AI Tutor button on dashboard", async ({ page }) => {
      await page.goto("/dashboard");

      // Verify floating AI button is visible
      const aiButton = page.locator("[data-testid='ai-tutor-floating-button']");
      await expect(aiButton).toBeVisible();
    });

    test("should display floating AI Tutor button on module pages", async ({ page }) => {
      await page.goto("/modules/intro-bubble-sort");

      // Verify floating AI button is visible
      const aiButton = page.locator("[data-testid='ai-tutor-floating-button']");
      await expect(aiButton).toBeVisible();
    });

    test("should NOT display widget on dedicated AI Tutor page", async ({ page }) => {
      await page.goto("/ai-tutor");

      // Verify floating AI button is NOT visible on dedicated page
      const aiButton = page.locator("[data-testid='ai-tutor-floating-button']");
      await expect(aiButton).not.toBeVisible();
    });

    test("should NOT display widget on auth pages", async ({ page }) => {
      await page.goto("/login");

      // Verify floating AI button is NOT visible on auth pages
      const aiButton = page.locator("[data-testid='ai-tutor-floating-button']");
      await expect(aiButton).not.toBeVisible();
    });
  });

  test.describe("Widget Open/Close", () => {
    test.beforeEach(async ({ page }) => {
      await page.goto("/dashboard");
    });

    test("should open chat panel when clicking floating button", async ({ page }) => {
      // Click the floating button
      await page.locator("[data-testid='ai-tutor-floating-button']").click();

      // Verify chat panel opens
      const chatPanel = page.locator("[data-testid='ai-tutor-chat-panel']");
      await expect(chatPanel).toBeVisible();

      // Verify chat header
      await expect(chatPanel.locator("text=AI Tutor")).toBeVisible();
    });

    test("should close chat panel when clicking X button", async ({ page }) => {
      // Open the chat panel
      await page.locator("[data-testid='ai-tutor-floating-button']").click();
      const chatPanel = page.locator("[data-testid='ai-tutor-chat-panel']");
      await expect(chatPanel).toBeVisible();

      // Click close button
      await page.locator("[data-testid='ai-tutor-close-button']").click();

      // Verify panel is closed
      await expect(chatPanel).not.toBeVisible();
    });

    test("should close chat panel when clicking outside", async ({ page }) => {
      // Open the chat panel
      await page.locator("[data-testid='ai-tutor-floating-button']").click();
      const chatPanel = page.locator("[data-testid='ai-tutor-chat-panel']");
      await expect(chatPanel).toBeVisible();

      // Click outside (on the backdrop)
      await page.locator("[data-testid='ai-tutor-backdrop']").click({ position: { x: 10, y: 10 } });

      // Verify panel is closed
      await expect(chatPanel).not.toBeVisible();
    });

    test("should toggle chat panel on repeated button clicks", async ({ page }) => {
      const button = page.locator("[data-testid='ai-tutor-floating-button']");
      const chatPanel = page.locator("[data-testid='ai-tutor-chat-panel']");

      // Open
      await button.click();
      await expect(chatPanel).toBeVisible();

      // Close
      await button.click();
      await expect(chatPanel).not.toBeVisible();

      // Open again
      await button.click();
      await expect(chatPanel).toBeVisible();
    });
  });

  test.describe("Context Awareness", () => {
    test("should show dashboard-specific quick actions on dashboard", async ({ page }) => {
      await page.goto("/dashboard");

      // Open chat panel
      await page.locator("[data-testid='ai-tutor-floating-button']").click();

      // Verify dashboard-specific quick actions
      await expect(page.locator("text=Browse modules")).toBeVisible();
      await expect(page.locator("text=My progress")).toBeVisible();
    });

    test("should show module-specific quick actions on module page", async ({ page }) => {
      await page.goto("/modules/intro-bubble-sort");

      // Open chat panel
      await page.locator("[data-testid='ai-tutor-floating-button']").click();

      // Verify module-specific quick actions
      await expect(page.locator("text=Explain this lesson")).toBeVisible();
      await expect(page.locator("text=Practice problem")).toBeVisible();
    });

    test("should show assessment-specific quick actions on assessment page", async ({ page }) => {
      await page.goto("/assessments/bubble-sort-quiz");

      // Open chat panel
      await page.locator("[data-testid='ai-tutor-floating-button']").click();

      // Verify assessment-specific quick actions
      await expect(page.locator("text=Help with this question")).toBeVisible();
      await expect(page.locator("text=Study tips")).toBeVisible();
    });

    test("should show pathway-specific quick actions on pathway page", async ({ page }) => {
      await page.goto("/pathways/computer-science-fundamentals");

      // Open chat panel
      await page.locator("[data-testid='ai-tutor-floating-button']").click();

      // Verify pathway-specific quick actions
      await expect(page.locator("text=Next recommended module")).toBeVisible();
    });
  });

  test.describe("Chat Functionality", () => {
    test.beforeEach(async ({ page }) => {
      await page.goto("/dashboard");
      await page.locator("[data-testid='ai-tutor-floating-button']").click();
    });

    test("should allow typing a message", async ({ page }) => {
      const input = page.locator("[data-testid='ai-tutor-input']");
      await input.fill("What should I learn next?");

      // Verify input value
      await expect(input).toHaveValue("What should I learn next?");
    });

    test("should send message and show user message in chat", async ({ page }) => {
      // Type and send message
      await page.locator("[data-testid='ai-tutor-input']").fill("Hello, can you help me?");
      await page.locator("[data-testid='ai-tutor-send-button']").click();

      // Verify user message appears in chat
      await expect(page.locator("[data-testid='user-message']")).toContainText("Hello, can you help me?");
    });

    test("should show AI response after sending message", async ({ page }) => {
      // Type and send message
      await page.locator("[data-testid='ai-tutor-input']").fill("What is bubble sort?");
      await page.locator("[data-testid='ai-tutor-send-button']").click();

      // Wait for AI response (may take a moment)
      const aiMessage = page.locator("[data-testid='assistant-message']").first();
      await expect(aiMessage).toBeVisible({ timeout: 15000 });

      // Verify response contains content
      const responseText = await aiMessage.textContent();
      expect(responseText).toBeTruthy();
      expect(responseText.length).toBeGreaterThan(10);
    });

    test("should clear input after sending message", async ({ page }) => {
      const input = page.locator("[data-testid='ai-tutor-input']");

      await input.fill("Test message");
      await page.locator("[data-testid='ai-tutor-send-button']").click();

      // Verify input is cleared
      await expect(input).toHaveValue("");
    });

    test("should show typing indicator while waiting for response", async ({ page }) => {
      // Type and send message
      await page.locator("[data-testid='ai-tutor-input']").fill("Explain algorithms");
      await page.locator("[data-testid='ai-tutor-send-button']").click();

      // Verify typing indicator appears
      const typingIndicator = page.locator("[data-testid='ai-tutor-typing-indicator']");
      await expect(typingIndicator).toBeVisible({ timeout: 5000 });
    });
  });

  test.describe("Quick Actions", () => {
    test.beforeEach(async ({ page }) => {
      await page.goto("/modules/intro-bubble-sort");
      await page.locator("[data-testid='ai-tutor-floating-button']").click();
    });

    test("should trigger quick action when clicked", async ({ page }) => {
      // Click a quick action button
      await page.locator("text=Explain this lesson").click();

      // Verify AI response appears
      const aiMessage = page.locator("[data-testid='assistant-message']").first();
      await expect(aiMessage).toBeVisible({ timeout: 15000 });
    });

    test("should show quick actions in suggested actions section", async ({ page }) => {
      const quickActionsSection = page.locator("[data-testid='ai-tutor-quick-actions']");
      await expect(quickActionsSection).toBeVisible();

      // Verify at least one quick action is visible
      const quickActionButtons = quickActionsSection.locator("button");
      await expect(quickActionButtons.first()).toBeVisible();
    });
  });

  test.describe("Proactive Help Indicator", () => {
    test("should show pulsing indicator when proactive help is available", async ({ page }) => {
      // Navigate to a module page and simulate struggle
      await page.goto("/modules/intro-bubble-sort");

      // Wait a while (simulating time on task)
      await page.waitForTimeout(35000); // 35 seconds > 30 second threshold

      // Check for pulsing indicator on floating button
      const button = page.locator("[data-testid='ai-tutor-floating-button']");
      await expect(button.locator("[data-testid='proactive-help-indicator']")).toBeVisible();
    });

    test("should open panel automatically on first proactive trigger", async ({ page }) => {
      await page.goto("/modules/intro-bubble-sort");

      // Wait for proactive help timer
      await page.waitForTimeout(35000);

      // Panel should open automatically
      const chatPanel = page.locator("[data-testid='ai-tutor-chat-panel']");
      await expect(chatPanel).toBeVisible();

      // Should show proactive help message
      await expect(page.locator("text=Need help")).toBeVisible();
    });

    test("should not show proactive indicator after user dismisses", async ({ page }) => {
      await page.goto("/modules/intro-bubble-sort");

      // Trigger proactive help
      await page.waitForTimeout(35000);
      await expect(page.locator("[data-testid='ai-tutor-chat-panel']")).toBeVisible();

      // Dismiss the help
      await page.locator("[data-testid='ai-tutor-dismiss-help']").click();

      // Indicator should be gone
      const button = page.locator("[data-testid='ai-tutor-floating-button']");
      await expect(button.locator("[data-testid='proactive-help-indicator']")).not.toBeVisible();
    });
  });

  test.describe("Keyboard Navigation", () => {
    test.beforeEach(async ({ page }) => {
      await page.goto("/dashboard");
    });

    test("should open widget with Enter key when button is focused", async ({ page }) => {
      // Focus the button using keyboard
      await page.keyboard.press("Tab");

      const button = page.locator("[data-testid='ai-tutor-floating-button']");
      await expect(button).toBeFocused();

      // Press Enter to open
      await page.keyboard.press("Enter");

      // Verify panel opens
      await expect(page.locator("[data-testid='ai-tutor-chat-panel']")).toBeVisible();
    });

    test("should send message with Enter key", async ({ page }) => {
      // Open panel
      await page.locator("[data-testid='ai-tutor-floating-button']").click();

      const input = page.locator("[data-testid='ai-tutor-input']");
      await input.fill("Test message");

      // Press Enter to send
      await input.press("Enter");

      // Verify message appears
      await expect(page.locator("[data-testid='user-message']")).toContainText("Test message");
    });

    test("should close widget with Escape key", async ({ page }) => {
      // Open panel
      await page.locator("[data-testid='ai-tutor-floating-button']").click();
      await expect(page.locator("[data-testid='ai-tutor-chat-panel']")).toBeVisible();

      // Press Escape to close
      await page.keyboard.press("Escape");

      // Verify panel is closed
      await expect(page.locator("[data-testid='ai-tutor-chat-panel']")).not.toBeVisible();
    });
  });

  test.describe("Mobile Responsiveness", () => {
    test("should adapt layout on mobile viewport", async ({ page }) => {
      // Set mobile viewport
      await page.setViewportSize({ width: 375, height: 667 });
      await page.goto("/dashboard");

      // Open chat panel
      await page.locator("[data-testid='ai-tutor-floating-button']").click();

      // Verify panel takes full width on mobile
      const chatPanel = page.locator("[data-testid='ai-tutor-chat-panel']");
      const box = await chatPanel.boundingBox();

      if (box) {
        expect(box.width).toBeLessThanOrEqual(375);
      }
    });
  });
});
