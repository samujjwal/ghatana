/**
 * DisabledSurfacePage Component Tests
 *
 * Verifies that the DisabledSurfacePage component renders correctly for
 * unavailable capability-gated surfaces (DC-UI-001).
 */
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { DisabledSurfacePage } from "../../pages/DisabledSurfacePage";

// react-router navigate is called internally — provide a router context
const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <MemoryRouter>{children}</MemoryRouter>
);

beforeEach(() => {
  vi.spyOn(console, "error").mockImplementation(() => {});
});

describe("DisabledSurfacePage", () => {
  it("renders with default props", () => {
    render(<DisabledSurfacePage />, { wrapper: Wrapper });
    expect(screen.getByText(/is not available/i)).toBeInTheDocument();
  });

  it("renders surfaceName in heading", () => {
    render(<DisabledSurfacePage surfaceName="Alerts" />, { wrapper: Wrapper });
    expect(screen.getByText(/Alerts is not available/i)).toBeInTheDocument();
  });

  it("renders surfaceDescription when provided", () => {
    render(
      <DisabledSurfacePage surfaceDescription="Alerting is not provisioned." />,
      { wrapper: Wrapper },
    );
    expect(
      screen.getByText("Alerting is not provisioned."),
    ).toBeInTheDocument();
  });

  it("renders actionHint text", () => {
    render(<DisabledSurfacePage actionHint="Ask your admin." />, {
      wrapper: Wrapper,
    });
    expect(screen.getByText(/Ask your admin\./i)).toBeInTheDocument();
  });

  it('has role="status" and aria-live="polite" for accessibility', () => {
    render(<DisabledSurfacePage />, { wrapper: Wrapper });
    const el = screen.getByRole("status");
    expect(el).toHaveAttribute("aria-live", "polite");
  });

  it("has a data-testid attribute", () => {
    render(<DisabledSurfacePage />, { wrapper: Wrapper });
    expect(screen.getByTestId("disabled-surface-page")).toBeInTheDocument();
  });

  it("respects custom data-testid", () => {
    render(<DisabledSurfacePage data-testid="custom-id" />, {
      wrapper: Wrapper,
    });
    expect(screen.getByTestId("custom-id")).toBeInTheDocument();
  });

  it("renders Go back and Go to Home buttons", () => {
    render(<DisabledSurfacePage />, { wrapper: Wrapper });
    expect(
      screen.getByRole("button", { name: /Go back/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Go to Home/i }),
    ).toBeInTheDocument();
  });

  it("has displayName set", () => {
    expect(DisabledSurfacePage.displayName).toBe("DisabledSurfacePage");
  });

  it("Go to Home button is clickable", async () => {
    const user = userEvent.setup();
    render(<DisabledSurfacePage />, { wrapper: Wrapper });
    const homeBtn = screen.getByRole("button", { name: /Go to Home/i });
    // Should not throw
    await user.click(homeBtn);
  });
});
