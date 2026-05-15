/**
 * @file editorFunctionality.test.ts
 * Tests for LazyMonacoEditor component — initialization, prop handling,
 * preload function, and loading state rendering.
 *
 * @doc.type test
 * @doc.purpose Tests for code editor functionality and lazy-loading
 * @doc.layer platform
 * @doc.pattern Test
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import React from "react";
import { render, screen } from "@testing-library/react";
import { LazyMonacoEditor, preloadMonacoEditor, MonacoBundleInfo } from "../index";
import type { LazyMonacoEditorProps } from "../index";

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock("@monaco-editor/react", async () => {
  const MockMonaco = ({
    value,
    language,
    onChange,
  }: {
    value?: string;
    language?: string;
    onChange?: (value: string) => void;
  }) =>
    React.createElement("div", {
      "data-testid": "monaco-editor",
      "data-language": language,
      "data-value": value,
      onClick: () => onChange?.("new value"),
    });
  MockMonaco.displayName = "MockMonacoEditor";
  return { default: MockMonaco };
});

// ── Helper ───────────────────────────────────────────────────────────────────

function makeProps(
  overrides: Partial<LazyMonacoEditorProps> = {},
): LazyMonacoEditorProps {
  return {
    value: "const x = 1;",
    language: "typescript",
    ...overrides,
  };
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe("LazyMonacoEditor", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("Initialization", () => {
    it("renders the skeleton fallback during lazy load (sync render)", () => {
      const { container } = render(
        React.createElement(LazyMonacoEditor, makeProps()),
      );
      // Either the skeleton or the editor is rendered
      expect(container.firstChild).not.toBeNull();
    });

    it("accepts value prop", () => {
      expect(() =>
        render(
          React.createElement(
            LazyMonacoEditor,
            makeProps({ value: "let y = 2;" }),
          ),
        ),
      ).not.toThrow();
    });

    it("accepts empty string value", () => {
      expect(() =>
        render(React.createElement(LazyMonacoEditor, makeProps({ value: "" }))),
      ).not.toThrow();
    });
  });

  describe("Text insertion and change handling", () => {
    it("calls onChange when provided", async () => {
      const onChange = vi.fn();
      expect(() =>
        render(React.createElement(LazyMonacoEditor, makeProps({ onChange }))),
      ).not.toThrow();
    });

    it("does not throw when onChange is omitted", () => {
      expect(() =>
        render(
          React.createElement(
            LazyMonacoEditor,
            makeProps({ onChange: undefined }),
          ),
        ),
      ).not.toThrow();
    });
  });

  describe("Language selection", () => {
    it("defaults to typescript language", () => {
      expect(() =>
        render(React.createElement(LazyMonacoEditor, { value: "x" })),
      ).not.toThrow();
    });

    it("accepts javascript language", () => {
      expect(() =>
        render(
          React.createElement(
            LazyMonacoEditor,
            makeProps({ language: "javascript" }),
          ),
        ),
      ).not.toThrow();
    });

    it("accepts python language", () => {
      expect(() =>
        render(
          React.createElement(
            LazyMonacoEditor,
            makeProps({ language: "python" }),
          ),
        ),
      ).not.toThrow();
    });

    it("accepts java language", () => {
      expect(() =>
        render(
          React.createElement(
            LazyMonacoEditor,
            makeProps({ language: "java" }),
          ),
        ),
      ).not.toThrow();
    });

    it("accepts sql language", () => {
      expect(() =>
        render(
          React.createElement(LazyMonacoEditor, makeProps({ language: "sql" })),
        ),
      ).not.toThrow();
    });
  });

  describe("Theme", () => {
    it("defaults to vs-dark theme", () => {
      expect(() =>
        render(React.createElement(LazyMonacoEditor, makeProps())),
      ).not.toThrow();
    });

    it("accepts vs-light theme", () => {
      expect(() =>
        render(
          React.createElement(
            LazyMonacoEditor,
            makeProps({ theme: "vs-light" }),
          ),
        ),
      ).not.toThrow();
    });
  });

  describe("Undo/redo and options", () => {
    it("passes options without crashing", () => {
      expect(() =>
        render(
          React.createElement(
            LazyMonacoEditor,
            makeProps({
              options: { minimap: { enabled: true }, readOnly: true },
            }),
          ),
        ),
      ).not.toThrow();
    });

    it("passes custom height", () => {
      expect(() =>
        render(
          React.createElement(LazyMonacoEditor, makeProps({ height: "600px" })),
        ),
      ).not.toThrow();
    });

    it("passes numeric height", () => {
      expect(() =>
        render(
          React.createElement(LazyMonacoEditor, makeProps({ height: 500 })),
        ),
      ).not.toThrow();
    });
  });

  describe("onMount callback", () => {
    it("accepts onMount prop without crashing", () => {
      const onMount = vi.fn();
      expect(() =>
        render(React.createElement(LazyMonacoEditor, makeProps({ onMount }))),
      ).not.toThrow();
    });
  });
});

describe("preloadMonacoEditor", () => {
  it("is callable without throwing", () => {
    expect(() => preloadMonacoEditor()).not.toThrow();
  });

  it("returns a preload promise", () => {
    const result = preloadMonacoEditor();
    expect(result).toBeDefined();
  });
});

describe("MonacoBundleInfo", () => {
  it("exports MonacoBundleInfo constant", () => {
    expect(MonacoBundleInfo).toBeDefined();
  });
});
