/**
 * Accessibility (axe-core) tests for page-level components (Pass 11).
 *
 * Enforces WCAG 2.1 AA compliance via vitest-axe for page components
 * that were updated with i18n and accessibility improvements.
 *
 * @doc.type test
 * @doc.purpose WCAG2AA compliance for page components
 * @doc.layer frontend
 */

import { render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { DisabledSurfacePage } from "../../pages/DisabledSurfacePage";
import { renderWithA11y } from "../test-utils/a11y";

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

vi.mock("lucide-react", () => ({
  AlertCircle: () => <svg />,
  CheckCircle: () => <svg />,
  ExternalLink: () => <svg />,
  Lock: () => <svg />,
  Settings: () => <svg />,
  XCircle: () => <svg />,
}));

vi.mock("react-router", () => ({
  useNavigate: () => vi.fn(),
}));

vi.mock("../../lib/theme", () => ({
  cn: (...classes: (string | undefined)[]) => classes.filter(Boolean).join(" "),
}));

// ---------------------------------------------------------------------------
// DisabledSurfacePage
// ---------------------------------------------------------------------------

describe("DisabledSurfacePage accessibility", () => {
  it("renders without axe violations (DISABLED status)", async () => {
    await renderWithA11y(
      <DisabledSurfacePage
        surfaceName="Test Surface"
        status="DISABLED"
      />,
    );
  });

  it("renders without axe violations (DEGRADED status)", async () => {
    await renderWithA11y(
      <DisabledSurfacePage
        surfaceName="Test Surface"
        status="DEGRADED"
      />,
    );
  });

  it("renders without axe violations (UNAVAILABLE status)", async () => {
    await renderWithA11y(
      <DisabledSurfacePage
        surfaceName="Test Surface"
        status="UNAVAILABLE"
      />,
    );
  });

  it("renders without axe violations (MISCONFIGURED status)", async () => {
    await renderWithA11y(
      <DisabledSurfacePage
        surfaceName="Test Surface"
        status="MISCONFIGURED"
      />,
    );
  });

  it('has role="status" for DISABLED (non-critical)', () => {
    const { container } = render(
      <DisabledSurfacePage surfaceName="Test Surface" status="DISABLED" />,
    );
    const mainEl = container.querySelector('[role="status"]');
    expect(mainEl).not.toBeNull();
  });

  it('has role="alert" for UNAVAILABLE (critical)', () => {
    const { container } = render(
      <DisabledSurfacePage surfaceName="Test Surface" status="UNAVAILABLE" />,
    );
    const mainEl = container.querySelector('[role="alert"]');
    expect(mainEl).not.toBeNull();
  });

  it('has aria-live="polite" for DISABLED status', () => {
    const { container } = render(
      <DisabledSurfacePage surfaceName="Test Surface" status="DISABLED" />,
    );
    const mainEl = container.querySelector('[role="status"]');
    expect(mainEl?.getAttribute("aria-live")).toBe("polite");
  });

  it('has aria-live="assertive" for UNAVAILABLE status', () => {
    const { container } = render(
      <DisabledSurfacePage surfaceName="Test Surface" status="UNAVAILABLE" />,
    );
    const mainEl = container.querySelector('[role="alert"]');
    expect(mainEl?.getAttribute("aria-live")).toBe("assertive");
  });

  it("heading is focusable for keyboard navigation", () => {
    const { container } = render(
      <DisabledSurfacePage surfaceName="Test Surface" status="DISABLED" />,
    );
    const heading = container.querySelector("h1");
    expect(heading).not.toBeNull();
    // The heading should have tabIndex={-1} for programmatic focus
    expect(heading?.getAttribute("tabindex")).toBe("-1");
  });

  it("buttons have accessible names", () => {
    const { getByRole } = render(
      <DisabledSurfacePage surfaceName="Test Surface" status="DISABLED" />,
    );
    const backBtn = getByRole("button", { name: /Go back/i });
    const homeBtn = getByRole("button", { name: /Go to Home/i });
    expect(backBtn).toBeTruthy();
    expect(homeBtn).toBeTruthy();
  });

  it("remediation link has accessible name", () => {
    const { getByRole } = render(
      <DisabledSurfacePage
        surfaceName="Test Surface"
        status="DISABLED"
        remediationLink="https://example.com/docs"
      />,
    );
    const link = getByRole("link", { name: /View remediation/i });
    expect(link).toBeTruthy();
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("dependency status icons are aria-hidden", () => {
    const { container } = render(
      <DisabledSurfacePage
        surfaceName="Test Surface"
        status="UNAVAILABLE"
        dependencies={[
          { name: "Database", status: "DOWN" },
          { name: "Cache", status: "HEALTHY" },
        ]}
      />,
    );
    const icons = container.querySelectorAll("svg");
    icons.forEach((icon) => {
      expect(icon).toHaveAttribute("aria-hidden", "true");
    });
  });
});
