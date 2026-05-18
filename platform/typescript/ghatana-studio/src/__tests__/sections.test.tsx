/**
 * @ghatana/ghatana-studio sections test suite
 * Tests for Ghatana Studio section components
 *
 * @test.type integration-browser
 * @test.execution 1-10s
 * @test.infra jsdom
 */

import { describe, it, expect } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import BuilderStudio from "../sections/BuilderStudio";
import ThemeStudio from "../sections/ThemeStudio";
import ComponentPlayground from "../sections/ComponentPlayground";
import CanvasDiagnostics from "../sections/CanvasDiagnostics";
import AIReviewConsole from "../sections/AIReviewConsole";
import ImportMigrationLab from "../sections/ImportMigrationLab";
import PreviewLab from "../sections/PreviewLab";

describe("@ghatana/ghatana-studio - Section Components", () => {
  describe("Builder Studio", () => {
    it("should render Builder Studio section with title and button", () => {
      render(<BuilderStudio />);
      expect(screen.getByText("Builder Studio")).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /^new document$/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByText(
          /select a document to view details or create a new document to get started/i,
        ),
      ).toBeInTheDocument();
      expect(screen.getByText(/no documents yet/i)).toBeInTheDocument();
    });

    it("should have proper heading hierarchy", () => {
      const { container } = render(<BuilderStudio />);
      const heading = container.querySelector("h2");
      expect(heading).toBeInTheDocument();
      expect(heading?.textContent).toBe("Builder Studio");
    });

    it("should display empty state message", () => {
      render(<BuilderStudio />);
      expect(
        screen.getByText(/create your first builderdocument/i),
      ).toBeInTheDocument();
    });
  });

  describe("Theme Studio", () => {
    it("should render Theme Studio section with title and button", () => {
      render(<ThemeStudio />);
      expect(screen.getByText("Theme Studio")).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /create theme/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/materialize and customize design system presets/i),
      ).toBeInTheDocument();
      expect(screen.getByText(/no themes yet/i)).toBeInTheDocument();
    });

    it("should have proper heading hierarchy", () => {
      const { container } = render(<ThemeStudio />);
      const heading = container.querySelector("h2");
      expect(heading).toBeInTheDocument();
      expect(heading?.textContent).toBe("Theme Studio");
    });

    it("should display empty state message", () => {
      render(<ThemeStudio />);
      expect(
        screen.getByText(/create your first design system theme/i),
      ).toBeInTheDocument();
    });
  });

  describe("Component Playground", () => {
    it("should render Component Playground section with title and button", () => {
      render(<ComponentPlayground />);
      expect(screen.getByText("Component Playground")).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /select component/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/explore and test design system components/i),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/select a component to explore/i),
      ).toBeInTheDocument();
    });

    it("should have proper heading hierarchy", () => {
      const { container } = render(<ComponentPlayground />);
      const heading = container.querySelector("h2");
      expect(heading).toBeInTheDocument();
      expect(heading?.textContent).toBe("Component Playground");
    });

    it("should display empty state message", () => {
      render(<ComponentPlayground />);
      expect(
        screen.getByText(
          /select a component to explore its props and variants/i,
        ),
      ).toBeInTheDocument();
    });
  });

  describe("Canvas Diagnostics", () => {
    it("should render Canvas Diagnostics section with title and button", () => {
      render(<CanvasDiagnostics />);
      expect(screen.getByText("Canvas Diagnostics")).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /refresh diagnostics/i }),
      ).toBeInTheDocument();
      expect(screen.getByText(/inspect canvas plugins/i)).toBeInTheDocument();
      expect(
        screen.getByText(/no canvas diagnostics data available/i),
      ).toBeInTheDocument();
    });

    it("should have proper heading hierarchy", () => {
      const { container } = render(<CanvasDiagnostics />);
      const heading = container.querySelector("h2");
      expect(heading).toBeInTheDocument();
      expect(heading?.textContent).toBe("Canvas Diagnostics");
    });

    it("should display empty state message", () => {
      render(<CanvasDiagnostics />);
      expect(
        screen.getByText(/no canvas diagnostics data available yet/i),
      ).toBeInTheDocument();
    });
  });

  describe("Agentic Development Review", () => {
    it("should render governed action proposal context", () => {
      render(<AIReviewConsole />);
      expect(
        screen.getByText("Agentic Development Review"),
      ).toBeInTheDocument();
      expect(screen.getByText(/execute-lifecycle-phase/i)).toBeInTheDocument();
      expect(screen.getByText(/high risk/i)).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /approve proposal/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /reject proposal/i }),
      ).toBeInTheDocument();
    });

    it("should have proper heading hierarchy", () => {
      const { container } = render(<AIReviewConsole />);
      const heading = container.querySelector("h2");
      expect(heading).toBeInTheDocument();
      expect(heading?.textContent).toBe("Agentic Development Review");
    });

    it("should display evidence confidence, risk, approval, verification, and rollback state", () => {
      render(<AIReviewConsole />);
      expect(screen.getByText("ProductUnitIntent")).toBeInTheDocument();
      expect(screen.getByText(/94%/i)).toBeInTheDocument();
      expect(screen.getAllByText(/redacted:/i).length).toBeGreaterThan(0);
      expect(screen.getAllByText(/provenance:/i).length).toBeGreaterThan(0);
      expect(screen.getByText("Preview trust")).toBeInTheDocument();
      expect(screen.getByText("Policy")).toBeInTheDocument();
      expect(screen.getByText("Mastery")).toBeInTheDocument();
      expect(screen.getByText("Approval")).toBeInTheDocument();
      expect(screen.getByText("Rollback")).toBeInTheDocument();
      expect(screen.getByText(/policy, health/i)).toBeInTheDocument();
      expect(
        screen.getByText(/product-owner, release-captain/i),
      ).toBeInTheDocument();
    });

    it("should support approve and reject audit trail decisions", () => {
      render(<AIReviewConsole />);
      fireEvent.click(
        screen.getByRole("button", { name: /approve proposal/i }),
      );
      expect(screen.getByText("Proposal approved")).toBeInTheDocument();
      expect(screen.getAllByText("approved").length).toBeGreaterThan(0);

      fireEvent.click(screen.getByRole("button", { name: /reject proposal/i }));
      expect(screen.getByText("Proposal rejected")).toBeInTheDocument();
      expect(screen.getAllByText("rejected").length).toBeGreaterThan(0);
    });
  });

  describe("Import/Migration Lab", () => {
    it("should render Import/Migration Lab section with title and button", () => {
      render(<ImportMigrationLab />);
      expect(screen.getByText("Import/Migration Lab")).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /import code/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/test code import from json/i),
      ).toBeInTheDocument();
      expect(screen.getByText(/paste or upload code/i)).toBeInTheDocument();
    });

    it("should have proper heading hierarchy", () => {
      const { container } = render(<ImportMigrationLab />);
      const heading = container.querySelector("h2");
      expect(heading).toBeInTheDocument();
      expect(heading?.textContent).toBe("Import/Migration Lab");
    });

    it("should display empty state message", () => {
      render(<ImportMigrationLab />);
      expect(
        screen.getByText(
          /paste or upload code to test import and reconciliation/i,
        ),
      ).toBeInTheDocument();
    });
  });

  describe("Preview Lab", () => {
    it("should render Preview Lab section with title and button", () => {
      render(<PreviewLab />);
      expect(screen.getByText("Preview Lab")).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /launch preview/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByText(
          /no preview sessions\. load a sample document to get started\./i,
        ),
      ).toBeInTheDocument();
      expect(
        screen.getByText(
          /select a preview session or load a sample document to get started\./i,
        ),
      ).toBeInTheDocument();
    });

    it("should have proper heading hierarchy", () => {
      const { container } = render(<PreviewLab />);
      const heading = container.querySelector("h2");
      expect(heading).toBeInTheDocument();
      expect(heading?.textContent).toBe("Preview Lab");
    });

    it("should display empty state message", () => {
      render(<PreviewLab />);
      expect(
        screen.getByText(
          /select a preview session or load a sample document to get started\./i,
        ),
      ).toBeInTheDocument();
    });
  });
});
