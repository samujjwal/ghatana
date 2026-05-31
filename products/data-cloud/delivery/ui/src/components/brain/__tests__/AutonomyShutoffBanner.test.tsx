/**
 * Tests for AutonomyShutoffBanner (B9).
 *
 * Verifies:
 * - Component renders nothing until API data arrives (null guard)
 * - Persistent banner shown when shutoffActive is true (SUGGEST level)
 * - No banner when autonomy is active (non-SUGGEST level)
 * - Emergency halt button triggers confirmation dialog
 *
 * @doc.type test
 * @doc.purpose Unit tests for AutonomyShutoffBanner component (B9)
 * @doc.layer frontend
 */
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TestWrapper } from "../../../__tests__/test-utils/wrapper";
import { AutonomyShutoffBanner } from "../AutonomyShutoffBanner";

// ─── Module mocks ────────────────────────────────────────────────────────────

vi.mock("../../../api/brain.service", () => ({
  brainService: {
    getGlobalAutonomyLevel: vi.fn(),
    setGlobalAutonomyLevel: vi.fn(),
  },
}));

// RBACGuard: render children unconditionally so tests can assert on guarded content
vi.mock("../../security/RBACGuard", () => ({
  RBACGuard: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

import { brainService } from "../../../api/brain.service";

const mockGetLevel = brainService.getGlobalAutonomyLevel as ReturnType<
  typeof vi.fn
>;
const _mockSetLevel = brainService.setGlobalAutonomyLevel as ReturnType<
  typeof vi.fn
>;

// ─── Tests ───────────────────────────────────────────────────────────────────

describe("AutonomyShutoffBanner", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders nothing while API data is loading", () => {
    // Never resolves — simulates loading state
    mockGetLevel.mockReturnValue(new Promise(() => {}));

    const { container } = render(
      <TestWrapper>
        <AutonomyShutoffBanner />
      </TestWrapper>,
    );

    expect(container.firstChild?.firstChild).toBeNull();
  });

  it("shows persistent warning banner when shutoffActive is true", async () => {
    mockGetLevel.mockResolvedValue({
      globalLevel: "SUGGEST",
      shutoffActive: true,
      affectedDomains: 2,
    });

    render(
      <TestWrapper>
        <AutonomyShutoffBanner />
      </TestWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText(/Autonomy HALTED/i)).toBeInTheDocument();
    });
  });

  it("does not show the warning banner when autonomy is active", async () => {
    mockGetLevel.mockResolvedValue({
      globalLevel: "NOTIFY",
      shutoffActive: false,
      affectedDomains: 0,
    });

    render(
      <TestWrapper>
        <AutonomyShutoffBanner />
      </TestWrapper>,
    );

    await waitFor(() => {
      // If the API resolved, the component rendered something
      expect(screen.queryByText(/Autonomy HALTED/i)).not.toBeInTheDocument();
    });
  });

  it("shows confirmation dialog when halt button is clicked", async () => {
    const user = userEvent.setup();
    mockGetLevel.mockResolvedValue({
      globalLevel: "NOTIFY",
      shutoffActive: false,
      affectedDomains: 0,
    });

    render(
      <TestWrapper>
        <AutonomyShutoffBanner />
      </TestWrapper>,
    );

    const haltBtn = await screen.findByRole("button", {
      name: /Emergency halt/i,
    });
    await user.click(haltBtn);

    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });
  });
});
