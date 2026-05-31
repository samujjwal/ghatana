/**
 * Accessibility (axe-core) tests for common components.
 *
 * Enforces WCAG 2.1 AA compliance via vitest-axe for components that are
 * widely reused across the Data Cloud UI.
 *
 * @doc.type test
 * @doc.purpose WCAG2AA compliance for shared components
 * @doc.layer frontend
 */

import { render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Plugin } from "../../api/plugin.service";
import { LoadingState } from "../../components/common/LoadingState";
import { PluginCard } from "../../components/plugins/PluginCard";
import { renderWithA11y } from "../test-utils/a11y";

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

vi.mock("@ghatana/design-system", () => ({
  Spinner: ({
    "aria-hidden": ariaHidden,
  }: {
    "aria-hidden"?: "true" | boolean;
  }) => (
    <svg role="img" aria-hidden={ariaHidden ?? "true"} data-testid="spinner" />
  ),
}));

vi.mock("../../lib/theme", () => ({
  cn: (...classes: (string | undefined)[]) => classes.filter(Boolean).join(" "),
  textStyles: { muted: "text-gray-500", h4: "text-base font-semibold" },
  cardStyles: { base: "rounded-lg border border-gray-200" },
}));

vi.mock("lucide-react", () => ({
  Package: () => <svg />,
  Power: () => <svg />,
  Settings: () => <svg />,
  Trash2: () => <svg />,
  Download: () => <svg />,
  ExternalLink: () => <svg />,
  AlertCircle: () => <svg />,
  CheckCircle: () => <svg />,
  Clock: () => <svg />,
  Star: () => <svg />,
  Shield: () => <svg />,
  TrendingUp: () => <svg />,
  Activity: () => <svg />,
  FileText: () => <svg />,
}));

// ---------------------------------------------------------------------------
// LoadingState
// ---------------------------------------------------------------------------

describe("LoadingState accessibility", () => {
  it("renders without axe violations (default message)", async () => {
    await renderWithA11y(<LoadingState />);
  });

  it("renders without axe violations (custom message)", async () => {
    await renderWithA11y(<LoadingState message="Loading collections..." />);
  });

  it('has role="status" for screen readers', () => {
    const { container } = render(<LoadingState message="Loading..." />);
    const statusEl = container.querySelector('[role="status"]');
    expect(statusEl).not.toBeNull();
  });

  it("has aria-label matching the message", () => {
    const { container } = render(<LoadingState message="Loading plugins..." />);
    const statusEl = container.querySelector('[role="status"]');
    expect(statusEl?.getAttribute("aria-label")).toBe("Loading plugins...");
  });

  it('has aria-live="polite" for non-disruptive announcements', () => {
    const { container } = render(<LoadingState />);
    const statusEl = container.querySelector('[role="status"]');
    expect(statusEl?.getAttribute("aria-live")).toBe("polite");
  });
});

// ---------------------------------------------------------------------------
// PluginCard
// ---------------------------------------------------------------------------

const makePlugin = (overrides: Partial<Plugin> = {}): Plugin => ({
  id: "plugin-001",
  metadata: {
    id: "plugin-001-meta",
    name: "Kafka Connector",
    description: "Stream events from Kafka",
    version: "1.0.0",
    category: "connector",
    author: "Ghatana",
    tags: [],
    icon: undefined,
    license: "MIT",
  },
  status: "active",
  installedAt: "2026-01-01T00:00:00Z",
  capabilities: [],
  configuration: {},
  ...overrides,
});

describe("PluginCard accessibility (installed mode)", () => {
  it("renders without axe violations", async () => {
    const plugin = makePlugin();
    await renderWithA11y(
      <PluginCard
        plugin={plugin}
        mode="installed"
        onEnable={vi.fn()}
        onDisable={vi.fn()}
        onConfigure={vi.fn()}
        onUninstall={vi.fn()}
        onViewDetails={vi.fn()}
      />,
    );
  });

  it("Configure button has aria-label", () => {
    const plugin = makePlugin();
    const { getByRole } = render(
      <PluginCard
        plugin={plugin}
        mode="installed"
        onConfigure={vi.fn()}
        onViewDetails={vi.fn()}
      />,
    );
    const configBtn = getByRole("button", {
      name: /Configure Kafka Connector/i,
    });
    expect(configBtn).toBeTruthy();
  });

  it("Disable button has aria-label when plugin is active", () => {
    const plugin = makePlugin({ status: "active" });
    const { getByRole } = render(
      <PluginCard
        plugin={plugin}
        mode="installed"
        onDisable={vi.fn()}
        onViewDetails={vi.fn()}
      />,
    );
    const disableBtn = getByRole("button", {
      name: /Disable Kafka Connector/i,
    });
    expect(disableBtn).toBeTruthy();
  });

  it("Uninstall button has aria-label", () => {
    const plugin = makePlugin();
    const { getByRole } = render(
      <PluginCard
        plugin={plugin}
        mode="installed"
        onUninstall={vi.fn()}
        onViewDetails={vi.fn()}
      />,
    );
    const uninstallBtn = getByRole("button", {
      name: /Uninstall Kafka Connector/i,
    });
    expect(uninstallBtn).toBeTruthy();
  });
});
