/**
 * Tests for RouteErrorBoundary component.
 *
 * Validates React Router error handling, including 404 responses,
 * server errors, and generic error display with proper accessibility.
 *
 * @doc.type test
 * @doc.purpose Unit tests for RouteErrorBoundary component
 * @doc.layer frontend
 */

import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouteErrorBoundary } from "../../components/common/RouteErrorBoundary";

// Mock React Router hooks
vi.mock("react-router", () => ({
  useRouteError: vi.fn(),
  isRouteErrorResponse: vi.fn(),
}));

import { isRouteErrorResponse, useRouteError } from "react-router";

// Mock import.meta.env
const mockImportMeta = { env: { DEV: false } };
Object.defineProperty(global, "import", {
  value: {
    meta: mockImportMeta,
  },
  writable: true,
});

// Suppress console errors in tests
beforeEach(() => {
  vi.spyOn(console, "error").mockImplementation(() => {});
  vi.clearAllMocks();
  mockImportMeta.env.DEV = false;
});

// Mock window.location.href
const mockLocation = { href: "" };
Object.defineProperty(window, "location", {
  value: mockLocation,
  writable: true,
});

describe("RouteErrorBoundary", () => {
  it("renders default error message for generic error", () => {
    const error = new Error("Something went wrong");
    vi.mocked(useRouteError).mockReturnValue(error);
    vi.mocked(isRouteErrorResponse).mockReturnValue(false);
    mockImportMeta.env.DEV = false;

    render(<RouteErrorBoundary />);

    expect(
      screen.getByRole("heading", { level: 1, name: "Something went wrong" }),
    ).toBeInTheDocument();
    expect(screen.getAllByText("Something went wrong").length).toBeGreaterThan(
      0,
    );
  });

  it("renders 404 status for route error response", () => {
    const error = {
      status: 404,
      statusText: "Not Found",
      data: "Page not found",
    };
    vi.mocked(useRouteError).mockReturnValue(error);
    vi.mocked(isRouteErrorResponse).mockReturnValue(true);
    mockImportMeta.env.DEV = false;

    render(<RouteErrorBoundary />);

    expect(screen.getByText("404 Not Found")).toBeInTheDocument();
    expect(screen.getByText("Page not found")).toBeInTheDocument();
  });

  it("renders 500 status for server error", () => {
    const error = {
      status: 500,
      statusText: "Internal Server Error",
      data: "Server error occurred",
    };
    vi.mocked(useRouteError).mockReturnValue(error);
    vi.mocked(isRouteErrorResponse).mockReturnValue(true);
    mockImportMeta.env.DEV = false;

    render(<RouteErrorBoundary />);

    expect(screen.getByText("500 Internal Server Error")).toBeInTheDocument();
    expect(screen.getByText("Server error occurred")).toBeInTheDocument();
  });

  it("renders error message from Error object", () => {
    const error = new Error("Custom error message");
    vi.mocked(useRouteError).mockReturnValue(error);
    vi.mocked(isRouteErrorResponse).mockReturnValue(false);
    mockImportMeta.env.DEV = false;

    render(<RouteErrorBoundary />);

    expect(screen.getByText("Custom error message")).toBeInTheDocument();
  });

  it("shows developer details in DEV mode", () => {
    const error = new Error("Test error");
    error.stack =
      "Error: Test error\n    at Component.render (component.tsx:10)";
    vi.mocked(useRouteError).mockReturnValue(error);
    vi.mocked(isRouteErrorResponse).mockReturnValue(false);
    mockImportMeta.env.DEV = true;

    render(<RouteErrorBoundary />);

    expect(screen.getByText(/developer details/i)).toBeInTheDocument();
    expect(screen.getByText(/component\.tsx:10/i)).toBeInTheDocument();
  });

  it("renders the error message even when developer details are unavailable", () => {
    const error = new Error("Test error");
    error.stack =
      "Error: Test error\n    at Component.render (component.tsx:10)";
    vi.mocked(useRouteError).mockReturnValue(error);
    vi.mocked(isRouteErrorResponse).mockReturnValue(false);
    mockImportMeta.env.DEV = false;

    render(<RouteErrorBoundary />);

    expect(screen.getAllByText("Test error").length).toBeGreaterThan(0);
  });

  it("navigates to homepage when Go to Homepage button is clicked", async () => {
    const user = userEvent.setup();
    const error = new Error("Test error");
    vi.mocked(useRouteError).mockReturnValue(error);
    vi.mocked(isRouteErrorResponse).mockReturnValue(false);
    mockImportMeta.env.DEV = false;

    render(<RouteErrorBoundary />);

    const homeButton = screen.getByText("Go to Homepage");
    await user.click(homeButton);

    expect(window.location.href).toBe("/");
  });

  it("has accessible alert role", () => {
    const error = new Error("Test error");
    vi.mocked(useRouteError).mockReturnValue(error);
    vi.mocked(isRouteErrorResponse).mockReturnValue(false);
    mockImportMeta.env.DEV = false;

    render(<RouteErrorBoundary />);

    const alert = screen.getByRole("alert");
    expect(alert).toBeInTheDocument();
  });

  it("has proper heading structure", () => {
    const error = new Error("Test error");
    vi.mocked(useRouteError).mockReturnValue(error);
    vi.mocked(isRouteErrorResponse).mockReturnValue(false);
    mockImportMeta.env.DEV = false;

    render(<RouteErrorBoundary />);

    const heading = screen.getByRole("heading", { level: 1 });
    expect(heading).toBeInTheDocument();
    expect(heading).toHaveTextContent("Something went wrong");
  });

  it("uses default message when route error has no data", () => {
    const error = {
      status: 404,
      statusText: "Not Found",
      data: null,
    };
    vi.mocked(useRouteError).mockReturnValue(error);
    vi.mocked(isRouteErrorResponse).mockReturnValue(true);
    mockImportMeta.env.DEV = false;

    render(<RouteErrorBoundary />);

    expect(screen.getByText("404 Not Found")).toBeInTheDocument();
    expect(
      screen.getByText(/an unexpected error occurred/i),
    ).toBeInTheDocument();
  });

  it("handles Error object without stack trace in DEV mode", () => {
    const error = new Error("Test error");
    error.stack = undefined;
    vi.mocked(useRouteError).mockReturnValue(error);
    vi.mocked(isRouteErrorResponse).mockReturnValue(false);
    mockImportMeta.env.DEV = true;

    render(<RouteErrorBoundary />);

    expect(screen.queryByText(/developer details/i)).not.toBeInTheDocument();
  });

  it("renders with dark mode support", () => {
    const error = new Error("Test error");
    vi.mocked(useRouteError).mockReturnValue(error);
    vi.mocked(isRouteErrorResponse).mockReturnValue(false);
    mockImportMeta.env.DEV = false;

    const { container } = render(<RouteErrorBoundary />);

    const wrapper = container.firstChild as HTMLElement;
    expect(wrapper.className).toContain("dark:bg-gray-900");
  });
});
