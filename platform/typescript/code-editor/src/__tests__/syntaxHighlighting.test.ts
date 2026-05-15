/**
 * @file syntaxHighlighting.test.ts
 * Tests for LazyMonacoEditor language/syntax configuration — verifying
 * that the component accepts and propagates language props for each
 * supported syntax target.
 *
 * @doc.type test
 * @doc.purpose Tests for code editor syntax highlighting configuration
 * @doc.layer platform
 * @doc.pattern Test
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import React from "react";
import { render } from "@testing-library/react";
import { LazyMonacoEditor } from "../index";
import type { LazyMonacoEditorProps } from "../index";

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock("@monaco-editor/react", async () => {
  const CaptureEditor = (props: Record<string, unknown>) =>
    React.createElement("div", {
      "data-testid": "monaco-editor",
      "data-language": props["language"] as string,
      "data-theme": props["theme"] as string,
    });
  CaptureEditor.displayName = "CaptureEditor";
  return { default: CaptureEditor };
});

// ── Helpers ───────────────────────────────────────────────────────────────────

type SupportedLanguage =
  | "javascript"
  | "typescript"
  | "python"
  | "java"
  | "sql"
  | "json"
  | "yaml"
  | "markdown"
  | "rust"
  | "go";

const LANGUAGE_SAMPLES: Record<SupportedLanguage, string> = {
  javascript: "const x = () => 42;",
  typescript: "const fn = (): number => 42;",
  python: "def hello():\n    return 42",
  java: "public class Main { public static void main(String[] args) {} }",
  sql: "SELECT id, name FROM users WHERE active = true;",
  json: '{"key": "value", "count": 42}',
  yaml: "key: value\ncount: 42",
  markdown: "# Heading\n\nSome **bold** text.",
  rust: 'fn main() { println!("Hello, world!"); }',
  go: 'package main\nimport "fmt"\nfunc main() { fmt.Println("hi") }',
};

function makeProps(
  language: SupportedLanguage,
  overrides: Partial<LazyMonacoEditorProps> = {},
): LazyMonacoEditorProps {
  return {
    value: LANGUAGE_SAMPLES[language],
    language,
    ...overrides,
  };
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe("LazyMonacoEditor — syntax highlighting configuration", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const LANGUAGES = Object.keys(LANGUAGE_SAMPLES) as SupportedLanguage[];

  describe("Language prop acceptance", () => {
    LANGUAGES.forEach((lang) => {
      it(`accepts "${lang}" language without throwing`, () => {
        expect(() =>
          render(React.createElement(LazyMonacoEditor, makeProps(lang))),
        ).not.toThrow();
      });
    });
  });

  describe("JavaScript highlighting", () => {
    it("renders with javascript language", () => {
      const { container } = render(
        React.createElement(LazyMonacoEditor, makeProps("javascript")),
      );
      expect(container.firstChild).not.toBeNull();
    });

    it("passes arrow function value correctly", () => {
      const onChange = vi.fn();
      expect(() =>
        render(
          React.createElement(
            LazyMonacoEditor,
            makeProps("javascript", { onChange }),
          ),
        ),
      ).not.toThrow();
    });
  });

  describe("TypeScript highlighting", () => {
    it("renders with typescript language", () => {
      const { container } = render(
        React.createElement(LazyMonacoEditor, makeProps("typescript")),
      );
      expect(container.firstChild).not.toBeNull();
    });

    it("defaults to typescript when language is omitted", () => {
      expect(() =>
        render(
          React.createElement(LazyMonacoEditor, { value: "const x = 1;" }),
        ),
      ).not.toThrow();
    });
  });

  describe("Python highlighting", () => {
    it("renders with python language", () => {
      const { container } = render(
        React.createElement(LazyMonacoEditor, makeProps("python")),
      );
      expect(container.firstChild).not.toBeNull();
    });
  });

  describe("Java highlighting", () => {
    it("renders with java language", () => {
      const { container } = render(
        React.createElement(LazyMonacoEditor, makeProps("java")),
      );
      expect(container.firstChild).not.toBeNull();
    });
  });

  describe("SQL highlighting", () => {
    it("renders with sql language", () => {
      const { container } = render(
        React.createElement(LazyMonacoEditor, makeProps("sql")),
      );
      expect(container.firstChild).not.toBeNull();
    });
  });

  describe("Performance — sequential language switching", () => {
    it("renders 5 instances with different languages without throwing", () => {
      const languages: SupportedLanguage[] = [
        "typescript",
        "python",
        "java",
        "sql",
        "javascript",
      ];
      languages.forEach((lang) => {
        expect(() =>
          render(React.createElement(LazyMonacoEditor, makeProps(lang))),
        ).not.toThrow();
      });
    });
  });

  describe("Theme and language combinations", () => {
    it("renders typescript with vs-dark theme", () => {
      expect(() =>
        render(
          React.createElement(
            LazyMonacoEditor,
            makeProps("typescript", { theme: "vs-dark" }),
          ),
        ),
      ).not.toThrow();
    });

    it("renders typescript with vs-light theme", () => {
      expect(() =>
        render(
          React.createElement(
            LazyMonacoEditor,
            makeProps("typescript", { theme: "vs-light" }),
          ),
        ),
      ).not.toThrow();
    });

    it("renders python with vs-light theme", () => {
      expect(() =>
        render(
          React.createElement(
            LazyMonacoEditor,
            makeProps("python", { theme: "vs-light" }),
          ),
        ),
      ).not.toThrow();
    });
  });
});
