/**
 * AsyncStates Component Tests — DC-UI-001
 *
 * Verifies all async state components (LoadingState, EmptyState, ErrorState,
 * UnavailableState, PreviewState, NotFoundState, DegradedState) render correctly
 * with the expected ARIA attributes.
 */
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  DegradedState,
  EmptyState,
  ErrorState,
  LoadingState,
  NotFoundState,
  PreviewState,
  UnavailableState,
} from "../../components/common/AsyncStates";

// Stub workspace packages not resolvable in this test environment.
vi.mock("@ghatana/platform-utils", () => ({
  cn: (...args: unknown[]) => args.filter(Boolean).join(" "),
}));

vi.mock("@ghatana/design-system", () => ({
  Spinner: ({ size, className }: { size?: string; className?: string }) => (
    <div
      role="status"
      aria-label="Loading"
      className={className}
      data-size={size}
    />
  ),
  EmptyState: ({
    title,
    description,
  }: {
    title?: string;
    description?: string;
  }) => (
    <div>
      {title && <p>{title}</p>}
      {description && <p>{description}</p>}
    </div>
  ),
}));

beforeEach(() => {
  vi.spyOn(console, "error").mockImplementation(() => {});
});

// ─── LoadingState ────────────────────────────────────────────────────────────

describe("LoadingState", () => {
  it("renders default message", () => {
    render(<LoadingState />);
    expect(screen.getByText("Loading...")).toBeInTheDocument();
  });

  it("renders custom message", () => {
    render(<LoadingState message="Fetching entities..." />);
    expect(screen.getByText("Fetching entities...")).toBeInTheDocument();
  });

  it('has role="status"', () => {
    render(<LoadingState />);
    expect(screen.getAllByRole("status").length).toBeGreaterThanOrEqual(1);
  });

  it("has displayName", () => {
    expect(LoadingState.displayName).toBe("LoadingState");
  });
});

// ─── EmptyState ──────────────────────────────────────────────────────────────

describe("EmptyState", () => {
  it("renders title", () => {
    render(<EmptyState title="No entities" />);
    expect(screen.getByText("No entities")).toBeInTheDocument();
  });

  it("renders description when provided", () => {
    render(<EmptyState title="Empty" description="Nothing here yet." />);
    expect(screen.getByText("Nothing here yet.")).toBeInTheDocument();
  });

  it("has displayName", () => {
    expect(EmptyState.displayName).toBe("EmptyState");
  });
});

// ─── ErrorState ──────────────────────────────────────────────────────────────

describe("ErrorState", () => {
  it("renders message", () => {
    render(<ErrorState message="Something failed." />);
    expect(screen.getByText("Something failed.")).toBeInTheDocument();
  });

  it("renders default title", () => {
    render(<ErrorState message="oops" />);
    expect(screen.getByText("Something went wrong")).toBeInTheDocument();
  });

  it("renders retry button when onRetry provided", () => {
    render(<ErrorState message="err" onRetry={vi.fn()} />);
    expect(
      screen.getByRole("button", { name: /Try again/i }),
    ).toBeInTheDocument();
  });

  it("calls onRetry when retry clicked", async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    render(<ErrorState message="err" onRetry={onRetry} />);
    await user.click(screen.getByRole("button", { name: /Try again/i }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('has role="alert"', () => {
    render(<ErrorState message="err" />);
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("has displayName", () => {
    expect(ErrorState.displayName).toBe("ErrorState");
  });
});

// ─── UnavailableState ────────────────────────────────────────────────────────

describe("UnavailableState", () => {
  it("renders title and message", () => {
    render(<UnavailableState title="Service down" message="Back soon." />);
    expect(screen.getByText("Service down")).toBeInTheDocument();
    expect(screen.getByText("Back soon.")).toBeInTheDocument();
  });

  it("renders detail when provided", () => {
    render(<UnavailableState title="T" message="M" detail="More info." />);
    expect(screen.getByText("More info.")).toBeInTheDocument();
  });

  it('has role="status"', () => {
    render(<UnavailableState title="T" message="M" />);
    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it("has displayName", () => {
    expect(UnavailableState.displayName).toBe("UnavailableState");
  });
});

// ─── PreviewState ────────────────────────────────────────────────────────────

describe("PreviewState", () => {
  it("renders default title", () => {
    render(<PreviewState message="Preview mode active." />);
    expect(screen.getByText("Preview")).toBeInTheDocument();
  });

  it("renders message", () => {
    render(<PreviewState message="Preview mode active." />);
    expect(screen.getByText("Preview mode active.")).toBeInTheDocument();
  });

  it("has displayName", () => {
    expect(PreviewState.displayName).toBe("PreviewState");
  });
});

// ─── NotFoundState ───────────────────────────────────────────────────────────

describe("NotFoundState", () => {
  it("renders no results heading", () => {
    render(<NotFoundState />);
    expect(screen.getByText("No results found")).toBeInTheDocument();
  });

  it("renders query hint when provided", () => {
    render(<NotFoundState query="my-entity" />);
    expect(screen.getByText(/my-entity/)).toBeInTheDocument();
  });

  it("renders clear button when onClear provided", () => {
    render(<NotFoundState onClear={vi.fn()} />);
    expect(
      screen.getByRole("button", { name: /Clear filters/i }),
    ).toBeInTheDocument();
  });

  it("calls onClear when button clicked", async () => {
    const user = userEvent.setup();
    const onClear = vi.fn();
    render(<NotFoundState onClear={onClear} />);
    await user.click(screen.getByRole("button", { name: /Clear filters/i }));
    expect(onClear).toHaveBeenCalledOnce();
  });

  it("has displayName", () => {
    expect(NotFoundState.displayName).toBe("NotFoundState");
  });
});

// ─── DegradedState (DC-UI-001) ───────────────────────────────────────────────

describe("DegradedState", () => {
  it("renders default title", () => {
    render(<DegradedState message="Running in degraded mode." />);
    expect(screen.getByText("Limited availability")).toBeInTheDocument();
  });

  it("renders custom title", () => {
    render(
      <DegradedState title="Partial outage" message="Some features limited." />,
    );
    expect(screen.getByText("Partial outage")).toBeInTheDocument();
  });

  it("renders message", () => {
    render(<DegradedState message="Running in degraded mode." />);
    expect(screen.getByText("Running in degraded mode.")).toBeInTheDocument();
  });

  it("renders detail when provided", () => {
    render(<DegradedState message="M" detail="Contact support." />);
    expect(screen.getByText("Contact support.")).toBeInTheDocument();
  });

  it('has role="status" and aria-live="polite"', () => {
    render(<DegradedState message="M" />);
    const el = screen.getByRole("status");
    expect(el).toHaveAttribute("aria-live", "polite");
  });

  it("has default data-testid", () => {
    render(<DegradedState message="M" />);
    expect(screen.getByTestId("degraded-state")).toBeInTheDocument();
  });

  it("respects custom data-testid", () => {
    render(<DegradedState message="M" data-testid="custom-degraded" />);
    expect(screen.getByTestId("custom-degraded")).toBeInTheDocument();
  });

  it("has displayName", () => {
    expect(DegradedState.displayName).toBe("DegradedState");
  });
});
