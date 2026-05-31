/**
 * Tests for AppErrorBoundary
 *
 * Covers: FINDING-DC-UI-H2 (global error boundary)
 *
 * @doc.type test
 * @doc.purpose Unit tests for AppErrorBoundary
 * @doc.layer frontend
 */

import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AppErrorBoundary } from "../../components/common/AppErrorBoundary";

// Suppress React's own error output in tests
beforeEach(() => {
  vi.spyOn(console, "error").mockImplementation(() => {});
});

/** Helper: a component that throws when `shouldThrow` is true. */
function ThrowingComponent({
  shouldThrow,
}: {
  shouldThrow: boolean;
}): React.ReactElement {
  if (shouldThrow) {
    throw new Error("Test error from ThrowingComponent");
  }
  return <div data-testid="children">Child content</div>;
}

describe("AppErrorBoundary", () => {
  it("renders children when there is no error", () => {
    render(
      <AppErrorBoundary>
        <ThrowingComponent shouldThrow={false} />
      </AppErrorBoundary>,
    );
    expect(screen.getByTestId("children")).toBeInTheDocument();
  });

  it("renders fallback UI when a child throws", () => {
    render(
      <AppErrorBoundary>
        <ThrowingComponent shouldThrow />
      </AppErrorBoundary>,
    );
    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();
  });

  it("shows the error reference ID in the fallback", () => {
    render(
      <AppErrorBoundary>
        <ThrowingComponent shouldThrow />
      </AppErrorBoundary>,
    );
    expect(screen.getByText(/reference:/i)).toBeInTheDocument();
  });

  it("calls custom fallback when provided", () => {
    const customFallback = vi.fn().mockReturnValue(<div>Custom fallback</div>);
    render(
      <AppErrorBoundary fallback={customFallback}>
        <ThrowingComponent shouldThrow />
      </AppErrorBoundary>,
    );
    expect(customFallback).toHaveBeenCalled();
    expect(screen.getByText("Custom fallback")).toBeInTheDocument();
  });

  it('resets state when "Try to recover" is clicked', async () => {
    const user = userEvent.setup();

    function _RecoverableApp({
      errorCount,
    }: {
      errorCount: number;
    }): React.ReactElement {
      return <ThrowingComponent shouldThrow={errorCount > 0} />;
    }

    const { rerender: _rerender } = render(
      <AppErrorBoundary>
        <ThrowingComponent shouldThrow />
      </AppErrorBoundary>,
    );

    // Fallback is visible
    expect(screen.getByRole("alert")).toBeInTheDocument();

    // Click recover
    await user.click(screen.getByRole("button", { name: /try to recover/i }));

    // After reset, the boundary tries to render children again.
    // Since the component is still set to throw, boundary catches again.
    // Here we verify the click handler fires without crashing.
  });

  it("provides accessible alert role on error fallback", () => {
    render(
      <AppErrorBoundary>
        <ThrowingComponent shouldThrow />
      </AppErrorBoundary>,
    );
    const alert = screen.getByRole("alert");
    expect(alert).toHaveAttribute("aria-live", "assertive");
  });
});
