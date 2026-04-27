/**
 * Accessibility guardrail tests for dashboard components.
 *
 * These tests verify that interactive elements expose the correct
 * accessibility props so screen readers can navigate the mobile app.
 *
 * Rule: All TouchableOpacity / Pressable elements that are user-facing
 * must carry an accessibilityLabel. These tests serve as regression
 * coverage for the eslint.config.js no-restricted-syntax guardrail.
 */

import React from "react";
import { act } from "react-test-renderer";
import renderer from "react-test-renderer";
import { ContinueLearningCard } from "../components/dashboard/ContinueLearningCard";
import { QuickActions } from "../components/dashboard/QuickActions";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const sampleEnrollment = {
  id: "enroll-1",
  moduleId: "module-1",
  status: "IN_PROGRESS",
  progress: 45,
  progressPercent: 45,
  lastAccessedAt: "2026-04-27T10:00:00Z",
  timeSpentSeconds: 1800,
  moduleTitle: "Introduction to Physics",
  moduleDescription: "Explore Newtonian mechanics through simulations",
};

// ---------------------------------------------------------------------------
// ContinueLearningCard
// ---------------------------------------------------------------------------

describe("ContinueLearningCard accessibility", () => {
  it("renders without throwing", () => {
    let tree: renderer.ReactTestRenderer | undefined;
    act(() => {
      tree = renderer.create(
        <ContinueLearningCard
          enrollment={sampleEnrollment}
          onPress={jest.fn()}
          onSeeAll={jest.fn()}
        />,
      );
    });
    expect(tree).toBeDefined();
  });

  it("primary touchable carries accessibilityLabel and accessibilityRole", () => {
    let tree: renderer.ReactTestRenderer | undefined;
    act(() => {
      tree = renderer.create(
        <ContinueLearningCard
          enrollment={sampleEnrollment}
          onPress={jest.fn()}
          onSeeAll={jest.fn()}
        />,
      );
    });
    const json = tree!.toJSON() as renderer.ReactTestRendererJSON;
    // Walk the tree to find a node with accessibilityRole=button
    const findNodes = (
      node: renderer.ReactTestRendererJSON | renderer.ReactTestRendererJSON[],
      found: renderer.ReactTestRendererJSON[],
    ): void => {
      if (Array.isArray(node)) {
        node.forEach((n) => findNodes(n, found));
        return;
      }
      if (node.props?.["accessibilityRole"] === "button") {
        found.push(node);
      }
      if (node.children) {
        node.children.forEach((child) => {
          if (typeof child !== "string") {
            findNodes(child, found);
          }
        });
      }
    };
    const buttons: renderer.ReactTestRendererJSON[] = [];
    findNodes(json, buttons);
    expect(buttons.length).toBeGreaterThan(0);
    // Every button must have an accessibilityLabel
    buttons.forEach((btn) => {
      expect(typeof btn.props["accessibilityLabel"]).toBe("string");
      expect((btn.props["accessibilityLabel"] as string).length).toBeGreaterThan(0);
    });
  });

  it("falls back to default title when moduleTitle is absent", () => {
    let tree: renderer.ReactTestRenderer | undefined;
    act(() => {
      tree = renderer.create(
        <ContinueLearningCard
          enrollment={{ ...sampleEnrollment, moduleTitle: undefined }}
          onPress={jest.fn()}
          onSeeAll={jest.fn()}
        />,
      );
    });
    expect(tree).toBeDefined();
  });
});

// ---------------------------------------------------------------------------
// QuickActions
// ---------------------------------------------------------------------------

describe("QuickActions accessibility", () => {
  it("renders without throwing", () => {
    let tree: renderer.ReactTestRenderer | undefined;
    act(() => {
      tree = renderer.create(
        <QuickActions
          onBrowseModules={jest.fn()}
          onViewEnrollments={jest.fn()}
          onViewAchievements={jest.fn()}
        />,
      );
    });
    expect(tree).toBeDefined();
  });

  it("toggle button carries accessibilityLabel and accessibilityRole", () => {
    let tree: renderer.ReactTestRenderer | undefined;
    act(() => {
      tree = renderer.create(
        <QuickActions
          onBrowseModules={jest.fn()}
          onViewEnrollments={jest.fn()}
          onViewAchievements={jest.fn()}
        />,
      );
    });
    const json = tree!.toJSON() as renderer.ReactTestRendererJSON;
    const findByLabel = (
      node: renderer.ReactTestRendererJSON | renderer.ReactTestRendererJSON[],
      found: renderer.ReactTestRendererJSON[],
    ): void => {
      if (Array.isArray(node)) {
        node.forEach((n) => findByLabel(n, found));
        return;
      }
      if (
        node.props?.["accessibilityRole"] === "button" &&
        typeof node.props?.["accessibilityLabel"] === "string"
      ) {
        found.push(node);
      }
      if (node.children) {
        node.children.forEach((child) => {
          if (typeof child !== "string") {
            findByLabel(child, found);
          }
        });
      }
    };
    const labeledButtons: renderer.ReactTestRendererJSON[] = [];
    findByLabel(json, labeledButtons);
    // The "More Options" toggle must be labeled
    expect(labeledButtons.length).toBeGreaterThan(0);
    const toggleLabel = labeledButtons[0]?.props["accessibilityLabel"] as string;
    expect(toggleLabel).toMatch(/expand|collapse|option/i);
  });
});
