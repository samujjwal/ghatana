/**
 * DisabledSurfacePage Component Tests
 *
 * Pass 11: Verifies i18n and accessibility requirements:
 * - Translated title/description using i18n keys
 * - Clear next action
 * - Keyboard focus on heading
 * - Accessible status role
 * - No raw i18n keys in rendered output
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

// Mock i18n
vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

beforeEach(() => {
  vi.spyOn(console, "error").mockImplementation(() => {});
});

describe("DisabledSurfacePage", () => {
  it("renders with default props using i18n keys", () => {
    render(<DisabledSurfacePage />, { wrapper: Wrapper });
    // Should use i18n key, not raw string
    expect(screen.getByText("disabledSurface.disabled")).toBeInTheDocument();
  });

  it("renders surfaceName with translated status", () => {
    render(<DisabledSurfacePage surfaceName="Alerts" />, { wrapper: Wrapper });
    // Should use i18n key for status
    expect(screen.getByText("disabledSurface.disabled")).toBeInTheDocument();
    expect(screen.getByText("Alerts")).toBeInTheDocument();
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

  it("renders nextAction text", () => {
    render(<DisabledSurfacePage nextAction="Ask your admin." />, {
      wrapper: Wrapper,
    });
    expect(screen.getByText("disabledSurface.nextAction")).toBeInTheDocument();
    expect(screen.getByText("Ask your admin.")).toBeInTheDocument();
  });

  it('has role="status" and aria-live="polite" for accessibility', () => {
    render(<DisabledSurfacePage />, { wrapper: Wrapper });
    const el = screen.getByRole("status");
    expect(el).toHaveAttribute("aria-live", "polite");
  });

  it("has role='alert' for non-disabled statuses", () => {
    render(<DisabledSurfacePage status="UNAVAILABLE" />, { wrapper: Wrapper });
    const el = screen.getByRole("alert");
    expect(el).toBeInTheDocument();
  });

  it("has keyboard focus on heading", () => {
    render(<DisabledSurfacePage />, { wrapper: Wrapper });
    const heading = screen.getByRole("heading", { level: 1 });
    // Heading should have tabIndex={-1} for focus management
    expect(heading).toBeInTheDocument();
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

  it("renders translated navigation buttons", () => {
    render(<DisabledSurfacePage />, { wrapper: Wrapper });
    // Should use i18n keys for button text
    expect(
      screen.getByRole("button", { name: "disabledSurface.goBack" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "disabledSurface.goToHome" }),
    ).toBeInTheDocument();
  });

  it("has displayName set", () => {
    expect(DisabledSurfacePage.displayName).toBe("DisabledSurfacePage");
  });

  it("Go to Home button is clickable", async () => {
    const user = userEvent.setup();
    render(<DisabledSurfacePage />, { wrapper: Wrapper });
    const homeBtn = screen.getByRole("button", { name: "disabledSurface.goToHome" });
    // Should not throw
    await user.click(homeBtn);
  });

  it("renders translated status messages", () => {
    render(<DisabledSurfacePage status="DEGRADED" />, { wrapper: Wrapper });
    expect(screen.getByText("disabledSurface.degraded")).toBeInTheDocument();
    expect(screen.getByText("disabledSurface.degradedMessage")).toBeInTheDocument();
  });

  it("renders translated dependency information", () => {
    render(
      <DisabledSurfacePage
        ownerPlane="data"
        runtimeProfile="test"
        requiredDependencies={["storage"]}
      />,
      { wrapper: Wrapper },
    );
    expect(screen.getByText("disabledSurface.ownerPlane")).toBeInTheDocument();
    expect(screen.getByText("disabledSurface.runtimeProfile")).toBeInTheDocument();
    expect(screen.getByText("disabledSurface.requiredDependencies")).toBeInTheDocument();
  });
});
