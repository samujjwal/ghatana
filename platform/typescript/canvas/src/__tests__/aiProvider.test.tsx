/**
 * @file aiProvider.test.tsx
 * Tests for AICanvasProvider and hooks — pure adapter contract + render smoke tests.
 *
 * @doc.type module
 * @doc.purpose Tests for AI canvas provider context and hooks
 * @doc.layer platform
 * @doc.pattern Test
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { AICanvasProvider, useCanvasAI } from "../ai/index.js";
import type { CanvasAIAdapter, CanvasAIContext, AISuggestion } from "../ai/types.js";

// ---------------------------------------------------------------------------
// Mock adapter
// ---------------------------------------------------------------------------

function makeMockAdapter(
  overrides: Partial<CanvasAIAdapter> = {},
): CanvasAIAdapter {
  return {
    getSuggestions: vi.fn().mockResolvedValue([
      {
        id: "s1",
        kind: "layout" as const,
        title: "Auto-arrange nodes",
        confidence: 0.9,
        targetElementIds: ["el-1"],
        payload: {},
      } as AISuggestion,
    ]),
    acceptSuggestion: vi
      .fn()
      .mockResolvedValue({ kind: "layout" as const, result: { positions: {} } }),
    dismissSuggestion: vi.fn().mockResolvedValue(undefined),
    query: vi.fn().mockResolvedValue({ kind: "error" as const, message: "noop", retryable: false }),
    autoLayout: vi
      .fn()
      .mockResolvedValue({ positions: {} }),
    generateElements: vi.fn().mockResolvedValue([]),
    ...overrides,
  };
}

const mockCtx: CanvasAIContext = {
  selectedElementIds: [],
  activeLayer: "default",
  visibleElementIds: [],
  userQuery: "",
};

// ---------------------------------------------------------------------------
// Pure adapter contract tests (no React)
// ---------------------------------------------------------------------------

describe("CanvasAIAdapter contract", () => {
  it("getSuggestions returns AISuggestion array", async () => {
    const adapter = makeMockAdapter();
    const result = await adapter.getSuggestions(mockCtx);
    expect(Array.isArray(result)).toBe(true);
    expect(result[0]?.id).toBe("s1");
    expect(result[0]?.kind).toBe("layout");
  });

  it("acceptSuggestion returns an AIResult-shaped object", async () => {
    const adapter = makeMockAdapter();
    const suggestion = result_suggestion();
    const result = await adapter.acceptSuggestion(suggestion, mockCtx);
    expect(result).toHaveProperty("kind");
  });

  it("dismissSuggestion resolves without error", async () => {
    const adapter = makeMockAdapter();
    await expect(adapter.dismissSuggestion("s1")).resolves.toBeUndefined();
    expect(adapter.dismissSuggestion).toHaveBeenCalledWith("s1");
  });

  it("autoLayout returns an object with positions", async () => {
    const adapter = makeMockAdapter();
    const result = await adapter.autoLayout(mockCtx);
    expect(result).toHaveProperty("positions");
  });

  it("generateElements returns an array", async () => {
    const adapter = makeMockAdapter();
    const result = await adapter.generateElements("flowchart", mockCtx);
    expect(Array.isArray(result)).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// Provider smoke tests
// ---------------------------------------------------------------------------

describe("AICanvasProvider", () => {
  it("renders children without error", async () => {
    const adapter = makeMockAdapter();
    const container = document.createElement("div");
    document.body.appendChild(container);

    await act(async () => {
      const inner = React.createElement("span", { id: "inner" }, "hello");
      createRoot(container).render(
        React.createElement(AICanvasProvider, { adapter, children: inner }),
      );
    });

    expect(container.querySelector("#inner")?.textContent).toBe("hello");
    document.body.removeChild(container);
  });

  it("useCanvasAI throws when used outside AICanvasProvider", async () => {
    function BadHook() {
      useCanvasAI();
      return null;
    }

    class Boundary extends React.Component<
      React.PropsWithChildren<Record<never, never>>,
      { caught: boolean }
    > {
      state = { caught: false };
      static getDerivedStateFromError() {
        return { caught: true };
      }
      override render() {
        return this.state.caught
          ? React.createElement("div", { id: "err" }, "caught")
          : this.props.children;
      }
    }

    const container = document.createElement("div");
    document.body.appendChild(container);
    const spy = vi.spyOn(console, "error").mockImplementation(() => {});

    await act(async () => {
      createRoot(container).render(
        React.createElement(
          Boundary,
          null,
          React.createElement(BadHook),
        ),
      );
    });

    expect(container.querySelector("#err")?.textContent).toBe("caught");
    spy.mockRestore();
    document.body.removeChild(container);
  });
});

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function result_suggestion(): AISuggestion {
  return {
    id: "s1",
    kind: "layout",
    title: "auto-arrange",
    confidence: 0.9,
    payload: {},
  };
}
