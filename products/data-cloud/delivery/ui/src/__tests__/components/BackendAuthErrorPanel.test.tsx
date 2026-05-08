/**
 * BackendAuthErrorPanel tests (DC-UI-003)
 *
 * Verifies:
 * - 401 AUTH_REQUIRED renders session-expired copy + sign-in action
 * - 403 ACCESS_DENIED renders access-denied copy + contact-admin guidance
 * - Correlation ID block appears when correlationId is present
 * - Correlation ID is absent when not provided
 * - Primary action calls the correct handler
 * - Copy button renders for correlationId (clipboard tested with vi.stubGlobal)
 *
 * @doc.type test
 * @doc.purpose DC-UI-003 backend-denied permission UX
 * @doc.layer frontend
 * @doc.pattern Unit Test
 */

import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";

import { BackendAuthErrorPanel } from "../../components/common/BackendAuthErrorPanel";
import type { ApiError } from "../../lib/api/client";
import type { AuthDenialCode } from "../../components/common/BackendAuthErrorPanel";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeError(
  code: AuthDenialCode,
  opts: Partial<ApiError> = {},
): ApiError & { code: AuthDenialCode } {
  return {
    code,
    message: code === "AUTH_REQUIRED" ? "Unauthorized" : "Forbidden",
    status: code === "AUTH_REQUIRED" ? 401 : 403,
    ...opts,
  };
}

// ---------------------------------------------------------------------------
// 401 AUTH_REQUIRED
// ---------------------------------------------------------------------------

describe("BackendAuthErrorPanel — AUTH_REQUIRED (401)", () => {
  it("renders the 'Session Expired' title", () => {
    render(<BackendAuthErrorPanel error={makeError("AUTH_REQUIRED")} />);
    expect(screen.getByTestId("backend-auth-error-title")).toHaveTextContent(
      "Session Expired",
    );
  });

  it("includes '401 Auth Required' status badge", () => {
    render(<BackendAuthErrorPanel error={makeError("AUTH_REQUIRED")} />);
    expect(screen.getByText("401 Auth Required")).toBeInTheDocument();
  });

  it("describes session expiry and prompts re-authentication", () => {
    render(<BackendAuthErrorPanel error={makeError("AUTH_REQUIRED")} />);
    expect(
      screen.getByText(/session has expired/i),
    ).toBeInTheDocument();
  });

  it("renders 'Sign In Again' primary action button", () => {
    render(<BackendAuthErrorPanel error={makeError("AUTH_REQUIRED")} />);
    expect(screen.getByTestId("primary-action-btn")).toHaveTextContent(
      "Sign In Again",
    );
  });

  it("calls onSignIn handler when primary action is clicked", () => {
    const onSignIn = vi.fn();
    render(
      <BackendAuthErrorPanel
        error={makeError("AUTH_REQUIRED")}
        onSignIn={onSignIn}
      />,
    );
    fireEvent.click(screen.getByTestId("primary-action-btn"));
    expect(onSignIn).toHaveBeenCalledOnce();
  });

  it("falls back to window.location.reload when no onSignIn is provided", () => {
    const reloadMock = vi.fn();
    Object.defineProperty(window, "location", {
      value: { reload: reloadMock },
      writable: true,
    });

    render(<BackendAuthErrorPanel error={makeError("AUTH_REQUIRED")} />);
    fireEvent.click(screen.getByTestId("primary-action-btn"));
    expect(reloadMock).toHaveBeenCalledOnce();
  });

  it("has role=alert for accessibility", () => {
    render(<BackendAuthErrorPanel error={makeError("AUTH_REQUIRED")} />);
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// 403 ACCESS_DENIED
// ---------------------------------------------------------------------------

describe("BackendAuthErrorPanel — ACCESS_DENIED (403)", () => {
  it("renders the 'Access Denied' title", () => {
    render(<BackendAuthErrorPanel error={makeError("ACCESS_DENIED")} />);
    expect(screen.getByTestId("backend-auth-error-title")).toHaveTextContent(
      "Access Denied",
    );
  });

  it("includes '403 Access Denied' status badge", () => {
    render(<BackendAuthErrorPanel error={makeError("ACCESS_DENIED")} />);
    expect(screen.getByText("403 Access Denied")).toBeInTheDocument();
  });

  it("advises contacting an administrator", () => {
    render(<BackendAuthErrorPanel error={makeError("ACCESS_DENIED")} />);
    expect(
      screen.getByText(/contact your workspace administrator/i),
    ).toBeInTheDocument();
  });

  it("does NOT render a primary action button when onRetry is absent", () => {
    render(<BackendAuthErrorPanel error={makeError("ACCESS_DENIED")} />);
    expect(screen.queryByTestId("primary-action-btn")).not.toBeInTheDocument();
  });

  it("renders 'Retry' button when onRetry is provided", () => {
    const onRetry = vi.fn();
    render(
      <BackendAuthErrorPanel
        error={makeError("ACCESS_DENIED")}
        onRetry={onRetry}
      />,
    );
    expect(screen.getByTestId("primary-action-btn")).toHaveTextContent("Retry");
  });

  it("calls onRetry when Retry button is clicked", () => {
    const onRetry = vi.fn();
    render(
      <BackendAuthErrorPanel
        error={makeError("ACCESS_DENIED")}
        onRetry={onRetry}
      />,
    );
    fireEvent.click(screen.getByTestId("primary-action-btn"));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it("has role=alert for accessibility", () => {
    render(<BackendAuthErrorPanel error={makeError("ACCESS_DENIED")} />);
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// Correlation ID display
// ---------------------------------------------------------------------------

describe("BackendAuthErrorPanel — Correlation ID", () => {
  it("shows the correlation ID block when correlationId is present", () => {
    render(
      <BackendAuthErrorPanel
        error={makeError("ACCESS_DENIED", { correlationId: "corr-abc-123" })}
      />,
    );
    expect(screen.getByTestId("correlation-id-block")).toBeInTheDocument();
    expect(screen.getByTestId("correlation-id-value")).toHaveTextContent(
      "corr-abc-123",
    );
  });

  it("hides the correlation ID block when correlationId is absent", () => {
    render(<BackendAuthErrorPanel error={makeError("AUTH_REQUIRED")} />);
    expect(
      screen.queryByTestId("correlation-id-block"),
    ).not.toBeInTheDocument();
  });

  it("renders a copy button next to the correlation ID", () => {
    render(
      <BackendAuthErrorPanel
        error={makeError("AUTH_REQUIRED", { correlationId: "corr-xyz-789" })}
      />,
    );
    expect(screen.getByTestId("copy-correlation-id-btn")).toBeInTheDocument();
  });

  it("copies the correlation ID to clipboard on button click", async () => {
    const writeTextMock = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText: writeTextMock },
      writable: true,
    });

    render(
      <BackendAuthErrorPanel
        error={makeError("AUTH_REQUIRED", { correlationId: "corr-copy-me" })}
      />,
    );

    fireEvent.click(screen.getByTestId("copy-correlation-id-btn"));

    await waitFor(() => {
      expect(writeTextMock).toHaveBeenCalledWith("corr-copy-me");
    });
  });
});

// ---------------------------------------------------------------------------
// Server message
// ---------------------------------------------------------------------------

describe("BackendAuthErrorPanel — server detail message", () => {
  it("shows extra server message when it differs from the title", () => {
    render(
      <BackendAuthErrorPanel
        error={makeError("ACCESS_DENIED", {
          message: "Tenant quota exceeded for this operation.",
        })}
      />,
    );
    expect(screen.getByTestId("backend-auth-error-message")).toHaveTextContent(
      "Tenant quota exceeded for this operation.",
    );
  });

  it("does not render the server detail block when message matches the title", () => {
    render(
      <BackendAuthErrorPanel
        error={{ ...makeError("AUTH_REQUIRED"), message: "Session Expired" }}
      />,
    );
    expect(
      screen.queryByTestId("backend-auth-error-message"),
    ).not.toBeInTheDocument();
  });
});
