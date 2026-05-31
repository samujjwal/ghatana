import { settingsSurfaceBoundaries } from "@/components/common/unsupportedSurfaceRegistry";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { SettingsPage } from "../../pages/SettingsPage";
import { TestWrapper } from "../test-utils/wrapper";

// The settings service raises a boundary error when the identity backend is
// unavailable. Mock it to keep tests deterministic and network-free.
vi.mock("../../api/settings.service", () => ({
  settingsService: {
    listApiKeys: vi
      .fn()
      .mockRejectedValue(new Error("Settings API not available")),
    getProfile: vi
      .fn()
      .mockRejectedValue(new Error("Settings API not available")),
    getPreferences: vi
      .fn()
      .mockRejectedValue(new Error("Settings API not available")),
    getNotificationPreferences: vi
      .fn()
      .mockRejectedValue(new Error("Settings API not available")),
  },
}));

describe("SettingsPage", () => {
  it("renders the settings shell with sidebar navigation and profile defaults", () => {
    render(<SettingsPage />, { wrapper: TestWrapper });

    expect(
      screen.getByRole("heading", { name: "Settings" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", {
        name: settingsSurfaceBoundaries.profile.title,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(settingsSurfaceBoundaries.profile.summary),
    ).toBeInTheDocument();
  });

  it("shows all four settings sections", () => {
    render(<SettingsPage />, { wrapper: TestWrapper });
    expect(screen.getByText("Profile")).toBeInTheDocument();
    expect(screen.getByText("Preferences")).toBeInTheDocument();
    expect(screen.getByText("Notifications")).toBeInTheDocument();
    expect(screen.getByText("API Keys")).toBeInTheDocument();
  });

  it("defaults to Profile section", () => {
    render(<SettingsPage />, { wrapper: TestWrapper });
    expect(
      screen.getByRole("heading", {
        name: settingsSurfaceBoundaries.profile.title,
      }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /profile/i })).toHaveClass(
      "bg-blue-50",
    );
  });

  it("switches to Preferences section on click", async () => {
    const user = userEvent.setup();
    render(<SettingsPage />, { wrapper: TestWrapper });
    await user.click(screen.getByText("Preferences"));

    expect(
      screen.getByRole("heading", {
        name: settingsSurfaceBoundaries.preferences.title,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(settingsSurfaceBoundaries.preferences.summary),
    ).toBeInTheDocument();
  });

  it("switches to Notifications section on click", async () => {
    const user = userEvent.setup();
    render(<SettingsPage />, { wrapper: TestWrapper });
    await user.click(screen.getByText("Notifications"));

    expect(
      screen.getByRole("heading", {
        name: settingsSurfaceBoundaries.notifications.title,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(settingsSurfaceBoundaries.notifications.summary),
    ).toBeInTheDocument();
  });

  it("switches to API Keys section on click", async () => {
    const user = userEvent.setup();
    render(<SettingsPage />, { wrapper: TestWrapper });
    await user.click(screen.getByText("API Keys"));

    expect(
      screen.getByRole("heading", {
        name: settingsSurfaceBoundaries.api.title,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(settingsSurfaceBoundaries.api.summary),
    ).toBeInTheDocument();
  });
});
