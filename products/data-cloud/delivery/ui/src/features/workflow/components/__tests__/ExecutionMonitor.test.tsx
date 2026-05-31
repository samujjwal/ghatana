import {
  EXECUTION_MONITOR_GUIDANCE_NOTE,
  EXECUTION_MONITOR_UNAVAILABLE_TITLE,
} from "@/lib/runtime-boundaries";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import React from "react";
import { describe, expect, it } from "vitest";
import {
  EXECUTION_MONITOR_BOUNDARY_NOTE,
  ExecutionMonitor,
} from "../ExecutionMonitor";

function Wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe("ExecutionMonitor", () => {
  it("renders an explicit launcher boundary instead of calling unsupported execution detail APIs", () => {
    render(<ExecutionMonitor executionId="exec-123" />, { wrapper: Wrapper });

    expect(
      screen.getByText(EXECUTION_MONITOR_UNAVAILABLE_TITLE),
    ).toBeInTheDocument();
    expect(
      screen.getByText(EXECUTION_MONITOR_BOUNDARY_NOTE),
    ).toBeInTheDocument();
    expect(
      screen.getByText(EXECUTION_MONITOR_GUIDANCE_NOTE),
    ).toBeInTheDocument();
  });
});
