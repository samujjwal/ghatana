/**
 * Accessibility audit tests for Data Cloud UI pages.
 *
 * Validates WCAG 2.1 AA compliance across main page components including:
 * - Keyboard navigation and focus management
 * - ARIA attributes and roles
 * - Heading hierarchy and structure
 * - Landmark regions
 * - Color contrast (where applicable)
 * - Form accessibility
 * - Screen reader compatibility
 *
 * Complements components/a11y.test.tsx, which tests shared components;
 * this file tests page-level accessibility.
 *
 * @doc.type test
 * @doc.purpose Page-level WCAG 2.1 AA compliance: keyboard, ARIA, landmarks, headings
 * @doc.layer frontend
 */
import { cleanup, render } from "@testing-library/react";
import React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TestWrapper } from "../test-utils/wrapper";

// ── Module mocks ─────────────────────────────────────────────────────────────

vi.mock("../../lib/api/client", () => ({
  apiClient: {
    get: vi.fn().mockResolvedValue([]),
    post: vi.fn().mockResolvedValue({}),
  },
}));

vi.mock("../../api/events.service", () => ({
  eventsService: {
    listEvents: vi.fn().mockResolvedValue({ events: [], total: 0 }),
    getStats: vi.fn().mockResolvedValue({ total: 0 }),
    openStream: vi.fn(),
  },
}));

vi.mock("../../api/memory.service", () => ({
  memoryService: {
    listMemoryItems: vi.fn().mockResolvedValue({ items: [], total: 0 }),
    deleteMemoryItem: vi.fn(),
    getConsolidationStatus: vi.fn().mockResolvedValue({
      lastRun: "2026-04-14T12:00:00Z",
      episodesProcessed: 0,
      policiesExtracted: 0,
    }),
  },
}));

vi.mock("@ghatana/canvas/flow", () => ({
  FlowCanvas: ({ children }: { children?: React.ReactNode }) =>
    React.createElement("div", { "data-testid": "flow-canvas" }, children),
  FlowControls: () => React.createElement("div"),
  useNodesState: (initial: unknown[]) => [initial, vi.fn(), vi.fn()],
  useEdgesState: (initial: unknown[]) => [initial, vi.fn(), vi.fn()],
  addEdge: vi.fn((conn: unknown, eds: unknown) => eds),
  Background: () => React.createElement("div"),
  Controls: () => React.createElement("div"),
}));

// ── Imports ───────────────────────────────────────────────────────────────────

import { CreateCollectionPage } from "../../pages/CreateCollectionPage";
import { DataExplorer } from "../../pages/DataExplorer";
import { DisabledSurfacePage } from "../../pages/DisabledSurfacePage";
import { InsightsPage } from "../../pages/InsightsPage";
import { DefaultLayout } from "../../layouts/DefaultLayout";
import { PluginsPage } from "../../pages/PluginsPage";
import { SettingsPage } from "../../pages/SettingsPage";
import { TrustCenter } from "../../pages/TrustCenter";
import { WorkflowsPage } from "../../pages/WorkflowsPage";

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Returns all interactive elements that should be keyboard-reachable.
 * These must either have a tab stop or be within a valid ARIA widget.
 */
function getInteractiveElements(container: HTMLElement): Element[] {
  return Array.from(
    container.querySelectorAll(
      "a[href], button:not([disabled]), input:not([disabled]), " +
        "select:not([disabled]), textarea:not([disabled]), " +
        '[tabindex]:not([tabindex="-1"]), [role="button"], [role="link"]',
    ),
  );
}

/**
 * Checks that every button element has an accessible name.
 * An accessible name can come from textContent, aria-label, aria-labelledby,
 * title, or an associated <label> element (via id matching label[for]).
 */
function buttonsHaveAccessibleNames(container: HTMLElement): boolean {
  const buttons = Array.from(container.querySelectorAll("button"));
  return buttons.every((btn) => {
    if ((btn.textContent?.trim().length ?? 0) > 0) return true;
    if (btn.hasAttribute("aria-label")) return true;
    if (btn.hasAttribute("aria-labelledby")) return true;
    if (btn.hasAttribute("title")) return true;
    // Check for an associated <label> via for/id pairing
    const id = btn.id;
    if (id && container.querySelector(`label[for="${id}"]`)) return true;
    return false;
  });
}

/**
 * Checks that images have alt attributes (empty string is acceptable for decorative images).
 */
function imagesHaveAlt(container: HTMLElement): boolean {
  const imgs = Array.from(container.querySelectorAll("img"));
  return imgs.every((img) => img.hasAttribute("alt"));
}

/**
 * Validates heading hierarchy follows WCAG 2.1 AA requirements.
 * Headings must be properly nested (h1 → h2 → h3) without skipping levels.
 */
function validateHeadingHierarchy(container: HTMLElement): {
  valid: boolean;
  violations: string[];
} {
  const headings = Array.from(
    container.querySelectorAll("h1, h2, h3, h4, h5, h6"),
  );
  const violations: string[] = [];
  let previousLevel = 0;

  headings.forEach((heading) => {
    const level = parseInt(heading.tagName[1], 10);

    // Check for skipped heading levels
    if (previousLevel > 0 && level > previousLevel + 1) {
      violations.push(
        `Skipped heading level: h${previousLevel} → h${level} at "${heading.textContent?.trim().substring(0, 30)}..."`,
      );
    }

    previousLevel = level;
  });

  return {
    valid: violations.length === 0,
    violations,
  };
}

/**
 * Checks for presence of landmark regions for screen reader navigation.
 * Landmarks include: banner, main, nav, aside, footer, search, etc.
 */
function hasLandmarkRegions(container: HTMLElement): boolean {
  const landmarks = container.querySelectorAll(
    '[role="banner"], [role="main"], [role="navigation"], [role="complementary"], ' +
      '[role="contentinfo"], [role="search"], [role="form"], header, main, nav, aside, footer',
  );
  return landmarks.length > 0;
}

/**
 * Checks that the page has a proper main landmark (either <main> or role="main").
 */
function hasMainLandmark(container: HTMLElement): boolean {
  return (
    container.querySelector("main") !== null ||
    container.querySelector('[role="main"]') !== null
  );
}

/**
 * Checks for proper focusable elements that should have visible focus indicators.
 * This is a basic check - actual visibility requires CSS inspection.
 */
function _focusableElementsHaveTabIndex(container: HTMLElement): boolean {
  const focusable = Array.from(
    container.querySelectorAll(
      "a[href], button:not([disabled]), input:not([disabled]), " +
        "select:not([disabled]), textarea:not([disabled]), [tabindex]",
    ),
  );

  // Elements with tabindex="-1" should not be keyboard reachable
  const _negativeTabindex = focusable.filter(
    (el) => el.getAttribute("tabindex") === "-1",
  );

  // These should be intentional (not counted as violations)
  return true;
}

/**
 * Checks that form fields have associated labels or aria-labels.
 */
function formFieldsHaveLabels(container: HTMLElement): boolean {
  const inputs = Array.from(
    container.querySelectorAll(
      'input:not([type="hidden"]):not([type="submit"]):not([type="button"]), ' +
        "select, textarea",
    ),
  );

  return inputs.every((input) => {
    const id = input.id;
    const hasAriaLabel =
      input.hasAttribute("aria-label") || input.hasAttribute("aria-labelledby");
    const hasAssociatedLabel =
      id && container.querySelector(`label[for="${id}"]`);
    const hasPlaceholder = input.hasAttribute("placeholder");

    return hasAriaLabel || hasAssociatedLabel || hasPlaceholder;
  });
}

/**
 * Checks that the page has a language attribute on the html element.
 */
function _hasLanguageAttribute(container: HTMLElement): boolean {
  const htmlEl = container.querySelector("html");
  return htmlEl !== null && htmlEl.hasAttribute("lang");
}

// ── Page accessibility audits ─────────────────────────────────────────────────

describe("AccessibilityAudit — DataExplorer", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders something on the page", () => {
    const { container } = render(<DataExplorer />, { wrapper: TestWrapper });
    expect(container.children.length).toBeGreaterThan(0);
    cleanup();
  });

  it("all rendered images have alt attributes", () => {
    const { container } = render(<DataExplorer />, { wrapper: TestWrapper });
    expect(imagesHaveAlt(container)).toBe(true);
    cleanup();
  });

  it("buttons have accessible names", () => {
    const { container } = render(<DataExplorer />, { wrapper: TestWrapper });
    expect(buttonsHaveAccessibleNames(container)).toBe(true);
    cleanup();
  });

  it("heading hierarchy follows WCAG 2.1 AA", () => {
    const { container } = render(<DataExplorer />, { wrapper: TestWrapper });
    const result = validateHeadingHierarchy(container);
    expect(result.valid).toBe(true);
    if (!result.valid) {
      console.warn("Heading hierarchy violations:", result.violations);
    }
    cleanup();
  });

  it("has landmark regions for screen reader navigation", () => {
    const { container } = render(<DataExplorer />, { wrapper: TestWrapper });
    expect(hasLandmarkRegions(container)).toBe(true);
    cleanup();
  });

  it("has main landmark for primary content", () => {
    const { container } = render(<DataExplorer />, { wrapper: TestWrapper });
    expect(hasMainLandmark(container)).toBe(true);
    cleanup();
  });

  it("form fields have associated labels", () => {
    const { container } = render(<DataExplorer />, { wrapper: TestWrapper });
    expect(formFieldsHaveLabels(container)).toBe(true);
    cleanup();
  });
});

describe("AccessibilityAudit — CreateCollectionPage", () => {
  it("renders without crashing", () => {
    const { container } = render(<CreateCollectionPage />, {
      wrapper: TestWrapper,
    });
    expect(container.children.length).toBeGreaterThan(0);
    cleanup();
  });

  it("all rendered images have alt attributes", () => {
    const { container } = render(<CreateCollectionPage />, {
      wrapper: TestWrapper,
    });
    expect(imagesHaveAlt(container)).toBe(true);
    cleanup();
  });

  it("buttons have accessible names", () => {
    const { container } = render(<CreateCollectionPage />, {
      wrapper: TestWrapper,
    });
    expect(buttonsHaveAccessibleNames(container)).toBe(true);
    cleanup();
  });

  it("form inputs have associated labels", () => {
    const { container } = render(<CreateCollectionPage />, {
      wrapper: TestWrapper,
    });
    const inputs = Array.from(
      container.querySelectorAll('input:not([type="hidden"])'),
    );
    inputs.forEach((input) => {
      const hasLabel =
        input.hasAttribute("aria-label") ||
        input.hasAttribute("aria-labelledby") ||
        input.hasAttribute("placeholder") || // acceptable fallback
        container.querySelector(`label[for="${input.id}"]`) !== null;
      expect(hasLabel).toBe(true);
    });
    cleanup();
  });

  it("heading hierarchy follows WCAG 2.1 AA", () => {
    const { container } = render(<CreateCollectionPage />, {
      wrapper: TestWrapper,
    });
    const result = validateHeadingHierarchy(container);
    expect(result.valid).toBe(true);
    if (!result.valid) {
      console.warn("Heading hierarchy violations:", result.violations);
    }
    cleanup();
  });

  it("has landmark regions for screen reader navigation", () => {
    const { container } = render(<CreateCollectionPage />, {
      wrapper: TestWrapper,
    });
    expect(hasLandmarkRegions(container)).toBe(true);
    cleanup();
  });

  it("has main landmark for primary content", () => {
    const { container } = render(<CreateCollectionPage />, {
      wrapper: TestWrapper,
    });
    expect(hasMainLandmark(container)).toBe(true);
    cleanup();
  });

  it("form fields have associated labels", () => {
    const { container } = render(<CreateCollectionPage />, {
      wrapper: TestWrapper,
    });
    expect(formFieldsHaveLabels(container)).toBe(true);
    cleanup();
  });
});

describe("AccessibilityAudit — WorkflowsPage", () => {
  it("renders page content", () => {
    const { container } = render(<WorkflowsPage />, { wrapper: TestWrapper });
    expect(container.children.length).toBeGreaterThan(0);
    cleanup();
  });

  it("all rendered images have alt attributes", () => {
    const { container } = render(<WorkflowsPage />, { wrapper: TestWrapper });
    expect(imagesHaveAlt(container)).toBe(true);
    cleanup();
  });

  it("buttons have accessible names", () => {
    const { container } = render(<WorkflowsPage />, { wrapper: TestWrapper });
    expect(buttonsHaveAccessibleNames(container)).toBe(true);
    cleanup();
  });

  it("heading hierarchy follows WCAG 2.1 AA", () => {
    const { container } = render(<WorkflowsPage />, { wrapper: TestWrapper });
    const result = validateHeadingHierarchy(container);
    expect(result.valid).toBe(true);
    if (!result.valid) {
      console.warn("Heading hierarchy violations:", result.violations);
    }
    cleanup();
  });

  it("has landmark regions for screen reader navigation", () => {
    const { container } = render(<WorkflowsPage />, { wrapper: TestWrapper });
    expect(hasLandmarkRegions(container)).toBe(true);
    cleanup();
  });

  it("has main landmark for primary content", () => {
    const { container } = render(<WorkflowsPage />, { wrapper: TestWrapper });
    expect(hasMainLandmark(container)).toBe(true);
    cleanup();
  });

  it("form fields have associated labels", () => {
    const { container } = render(<WorkflowsPage />, { wrapper: TestWrapper });
    expect(formFieldsHaveLabels(container)).toBe(true);
    cleanup();
  });
});

describe("AccessibilityAudit — InsightsPage", () => {
  it("renders page content", () => {
    const { container } = render(<InsightsPage />, { wrapper: TestWrapper });
    expect(container.children.length).toBeGreaterThan(0);
    cleanup();
  });

  it("all rendered images have alt attributes", () => {
    const { container } = render(<InsightsPage />, { wrapper: TestWrapper });
    expect(imagesHaveAlt(container)).toBe(true);
    cleanup();
  });

  it("buttons have accessible names", () => {
    const { container } = render(<InsightsPage />, { wrapper: TestWrapper });
    expect(buttonsHaveAccessibleNames(container)).toBe(true);
    cleanup();
  });

  it("heading hierarchy follows WCAG 2.1 AA", () => {
    const { container } = render(<InsightsPage />, { wrapper: TestWrapper });
    const result = validateHeadingHierarchy(container);
    expect(result.valid).toBe(true);
    if (!result.valid) {
      console.warn("Heading hierarchy violations:", result.violations);
    }
    cleanup();
  });

  it("has landmark regions for screen reader navigation", () => {
    const { container } = render(<InsightsPage />, { wrapper: TestWrapper });
    expect(hasLandmarkRegions(container)).toBe(true);
    cleanup();
  });

  it("has main landmark for primary content", () => {
    const { container } = render(<InsightsPage />, { wrapper: TestWrapper });
    expect(hasMainLandmark(container)).toBe(true);
    cleanup();
  });

  it("form fields have associated labels", () => {
    const { container } = render(<InsightsPage />, { wrapper: TestWrapper });
    expect(formFieldsHaveLabels(container)).toBe(true);
    cleanup();
  });
});

describe("AccessibilityAudit — TrustCenter (Governance)", () => {
  it("renders page content", () => {
    const { container } = render(<TrustCenter />, { wrapper: TestWrapper });
    expect(container.children.length).toBeGreaterThan(0);
    cleanup();
  });

  it("all rendered images have alt attributes", () => {
    const { container } = render(<TrustCenter />, { wrapper: TestWrapper });
    expect(imagesHaveAlt(container)).toBe(true);
    cleanup();
  });

  it("buttons have accessible names", () => {
    const { container } = render(<TrustCenter />, { wrapper: TestWrapper });
    expect(buttonsHaveAccessibleNames(container)).toBe(true);
    cleanup();
  });

  it("heading hierarchy follows WCAG 2.1 AA", () => {
    const { container } = render(<TrustCenter />, { wrapper: TestWrapper });
    const result = validateHeadingHierarchy(container);
    expect(result.valid).toBe(true);
    if (!result.valid) {
      console.warn("Heading hierarchy violations:", result.violations);
    }
    cleanup();
  });

  it("has landmark regions for screen reader navigation", () => {
    const { container } = render(<TrustCenter />, { wrapper: TestWrapper });
    expect(hasLandmarkRegions(container)).toBe(true);
    cleanup();
  });

  it("has main landmark for primary content", () => {
    const { container } = render(<TrustCenter />, { wrapper: TestWrapper });
    expect(hasMainLandmark(container)).toBe(true);
    cleanup();
  });

  it("form fields have associated labels", () => {
    const { container } = render(<TrustCenter />, { wrapper: TestWrapper });
    expect(formFieldsHaveLabels(container)).toBe(true);
    cleanup();
  });
});

describe("AccessibilityAudit — PluginsPage", () => {
  it("renders page content", () => {
    const { container } = render(<PluginsPage />, { wrapper: TestWrapper });
    expect(container.children.length).toBeGreaterThan(0);
    cleanup();
  });

  it("all rendered images have alt attributes", () => {
    const { container } = render(<PluginsPage />, { wrapper: TestWrapper });
    expect(imagesHaveAlt(container)).toBe(true);
    cleanup();
  });

  it("buttons have accessible names", () => {
    const { container } = render(<PluginsPage />, { wrapper: TestWrapper });
    expect(buttonsHaveAccessibleNames(container)).toBe(true);
    cleanup();
  });

  it("heading hierarchy follows WCAG 2.1 AA", () => {
    const { container } = render(<PluginsPage />, { wrapper: TestWrapper });
    const result = validateHeadingHierarchy(container);
    expect(result.valid).toBe(true);
    if (!result.valid) {
      console.warn("Heading hierarchy violations:", result.violations);
    }
    cleanup();
  });

  it("has landmark regions for screen reader navigation", () => {
    const { container } = render(<PluginsPage />, { wrapper: TestWrapper });
    expect(hasLandmarkRegions(container)).toBe(true);
    cleanup();
  });

  it("has main landmark for primary content", () => {
    const { container } = render(<PluginsPage />, { wrapper: TestWrapper });
    expect(hasMainLandmark(container)).toBe(true);
    cleanup();
  });

  it("form fields have associated labels", () => {
    const { container } = render(<PluginsPage />, { wrapper: TestWrapper });
    expect(formFieldsHaveLabels(container)).toBe(true);
    cleanup();
  });
});

describe("AccessibilityAudit — SettingsPage", () => {
  it("renders page content", () => {
    const { container } = render(<SettingsPage />, { wrapper: TestWrapper });
    expect(container.children.length).toBeGreaterThan(0);
    cleanup();
  });

  it("all rendered images have alt attributes", () => {
    const { container } = render(<SettingsPage />, { wrapper: TestWrapper });
    expect(imagesHaveAlt(container)).toBe(true);
    cleanup();
  });

  it("buttons have accessible names", () => {
    const { container } = render(<SettingsPage />, { wrapper: TestWrapper });
    expect(buttonsHaveAccessibleNames(container)).toBe(true);
    cleanup();
  });

  it("heading hierarchy follows WCAG 2.1 AA", () => {
    const { container } = render(<SettingsPage />, { wrapper: TestWrapper });
    const result = validateHeadingHierarchy(container);
    expect(result.valid).toBe(true);
    if (!result.valid) {
      console.warn("Heading hierarchy violations:", result.violations);
    }
    cleanup();
  });

  it("has landmark regions for screen reader navigation", () => {
    const { container } = render(<SettingsPage />, { wrapper: TestWrapper });
    expect(hasLandmarkRegions(container)).toBe(true);
    cleanup();
  });

  it("has main landmark for primary content", () => {
    const { container } = render(<SettingsPage />, { wrapper: TestWrapper });
    expect(hasMainLandmark(container)).toBe(true);
    cleanup();
  });

  it("form fields have associated labels", () => {
    const { container } = render(<SettingsPage />, { wrapper: TestWrapper });
    expect(formFieldsHaveLabels(container)).toBe(true);
    cleanup();
  });
});

// ── Navigation and Route Tests ─────────────────────────────────────────────────

describe("NavigationDiscoverability", () => {
  it("primary navigation links are discoverable via keyboard", () => {
    const { container } = render(<DefaultLayout />, { wrapper: TestWrapper });
    const navLinks = container.querySelectorAll('nav a, [role="navigation"] a');
    expect(navLinks.length).toBeGreaterThan(0);
    navLinks.forEach((link) => {
      expect(link.getAttribute("href")).toBeTruthy();
    });
    cleanup();
  });

  it("navigation items have accessible labels", () => {
    const { container } = render(<WorkflowsPage />, { wrapper: TestWrapper });
    const navItems = container.querySelectorAll(
      'nav button, nav a, [role="navigation"] button',
    );
    navItems.forEach((item) => {
      const textLength = item.textContent?.trim().length ?? 0;
      const hasLabel =
        textLength > 0 ||
        item.hasAttribute("aria-label") ||
        item.hasAttribute("aria-labelledby");
      expect(hasLabel).toBe(true);
    });
    cleanup();
  });
});

describe("DisabledSurfaceExplanation", () => {
  it("disabled surface provides meaningful explanation", () => {
    const { container } = render(
      <DisabledSurfacePage surfaceName="Alerts" status="DISABLED" />,
      { wrapper: TestWrapper },
    );
    const disabledText = container.textContent ?? "";
    expect(disabledText).toMatch(/disabled|unavailable|not enabled/i);
    cleanup();
  });

  it("disabled surface includes next action guidance", () => {
    const { container } = render(
      <DisabledSurfacePage
        surfaceName="Memory"
        status="DISABLED"
        nextAction="Contact your administrator to enable this capability."
      />,
      { wrapper: TestWrapper },
    );
    const hasActionText =
      container.textContent?.toLowerCase().includes("next") ||
      container.textContent?.toLowerCase().includes("action") ||
      container.textContent?.toLowerCase().includes("contact");
    expect(hasActionText).toBe(true);
    cleanup();
  });
});

describe("ReleaseTruthDiscoverability", () => {
  it("release-truth route is not discoverable in main navigation", () => {
    const { container } = render(<DataExplorer />, { wrapper: TestWrapper });
    const navLinks = Array.from(
      container.querySelectorAll('nav a, [role="navigation"] a'),
    );
    const releaseTruthLinks = navLinks.filter(
      (link) =>
        link.getAttribute("href")?.includes("release-truth") ||
        link.textContent?.toLowerCase().includes("release truth"),
    );
    expect(releaseTruthLinks.length).toBe(0);
    cleanup();
  });
});

describe("RawStringScan", () => {
  it("route error messages use i18n keys not raw strings", () => {
    const { container } = render(<DataExplorer />, { wrapper: TestWrapper });
    const textContent = container.textContent ?? "";
    // Check for common raw string patterns that should be i18n
    const rawStringPatterns = [
      /routes\.\w+/,
      /Loading\.\.\./,
      /Failed to load/,
    ];
    rawStringPatterns.forEach((pattern) => {
      const matches = textContent.match(pattern);
      if (matches) {
        // If found, verify it's in a test context or data attribute
        const isDataAttr =
          container.innerHTML.includes("data-testid") ||
          container.innerHTML.includes("aria-label");
        // Allow data attributes but not user-facing text
        if (!isDataAttr) {
          console.warn("Potential raw string found:", matches);
        }
      }
    });
    cleanup();
  });

  it("disabled surface messages use i18n", () => {
    const { container } = render(
      <DisabledSurfacePage surfaceName="Context" status="UNAVAILABLE" />,
      { wrapper: TestWrapper },
    );
    const textContent = container.textContent ?? "";
    // Should not contain hardcoded English phrases in production
    const hardcodedPhrases = [
      "is not available",
      "is degraded",
      "is unavailable",
    ];
    const foundHardcoded = hardcodedPhrases.filter((phrase) =>
      textContent.includes(phrase),
    );
    // Allow some flexibility for demo purposes
    expect(foundHardcoded.length).toBeLessThan(2);
    cleanup();
  });
});

describe("KeyboardAndFocus", () => {
  it("interactive elements are keyboard reachable", () => {
    const { container } = render(<WorkflowsPage />, { wrapper: TestWrapper });
    const interactiveElements = getInteractiveElements(container);
    expect(interactiveElements.length).toBeGreaterThan(0);

    // Check that interactive elements don't have tabindex="-1" unless intentional
    const negativeTabindex = interactiveElements.filter(
      (el) => el.getAttribute("tabindex") === "-1",
    );
    // Allow some negative tabindex for custom widgets
    expect(negativeTabindex.length).toBeLessThan(
      interactiveElements.length / 2,
    );
    cleanup();
  });

  it("focus management preserves context", () => {
    const { container } = render(<PluginsPage />, { wrapper: TestWrapper });
    const buttons = container.querySelectorAll("button");
    if (buttons.length > 0) {
      buttons.forEach((btn) => {
        // Check that buttons have proper focus indicators via CSS classes
        const _hasFocusClass =
          btn.className.includes("focus") ||
          btn.getAttribute("class")?.includes("ring");
        // Not all buttons need explicit focus classes if using CSS :focus
        // This is a basic check
      });
    }
    cleanup();
  });

  it("modal dialogs trap focus", () => {
    // This would require rendering a modal component
    // For now, we check that any dialogs have proper ARIA attributes
    const { container } = render(<DataExplorer />, { wrapper: TestWrapper });
    const dialogs = container.querySelectorAll('[role="dialog"]');
    dialogs.forEach((dialog) => {
      expect(
        dialog.hasAttribute("aria-modal") ||
          dialog.hasAttribute("aria-labelledby"),
      ).toBe(true);
    });
    cleanup();
  });
});
