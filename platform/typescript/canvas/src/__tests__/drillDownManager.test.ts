/**
 * @file drillDownManager.test.ts
 * Tests for DrillDownManager — hierarchical navigation stack.
 *
 * @doc.type module
 * @doc.purpose Tests for DrillDownManager navigation
 * @doc.layer platform
 * @doc.pattern Test
 */

import { describe, it, expect, beforeEach, vi } from "vitest";
import { DrillDownManager } from "../core/drill-down-manager.js";

describe("DrillDownManager", () => {
  let manager: DrillDownManager;

  beforeEach(() => {
    manager = new DrillDownManager();
  });

  it("starts at root with empty stack", () => {
    expect(manager.depth).toBe(0);
    expect(manager.isAtRoot).toBe(true);
    expect(manager.breadcrumbs).toHaveLength(1); // root
  });

  it("enter adds a level", () => {
    manager.enter({ documentId: "doc-a", label: "Module A" });
    expect(manager.depth).toBe(1);
    expect(manager.isAtRoot).toBe(false);
    expect(manager.current.documentId).toBe("doc-a");
  });

  it("back returns to previous level", () => {
    manager.enter({ documentId: "doc-a", label: "Module A" });
    manager.enter({ documentId: "doc-b", label: "Component B" });
    expect(manager.depth).toBe(2);

    manager.back();
    expect(manager.depth).toBe(1);
    expect(manager.current.documentId).toBe("doc-a");
  });

  it("backToRoot clears the stack", () => {
    manager.enter({ documentId: "doc-a", label: "A" });
    manager.enter({ documentId: "doc-b", label: "B" });
    manager.enter({ documentId: "doc-c", label: "C" });
    manager.backToRoot();
    expect(manager.isAtRoot).toBe(true);
    expect(manager.depth).toBe(0);
  });

  it("goTo navigates to a specific depth", () => {
    manager.enter({ documentId: "doc-a", label: "A" });
    manager.enter({ documentId: "doc-b", label: "B" });
    manager.enter({ documentId: "doc-c", label: "C" });

    manager.goTo(2); // go to "B" — stack index 2 (root=0, A=1, B=2)
    expect(manager.depth).toBe(2);
    expect(manager.current.documentId).toBe("doc-b");
  });

  it("breadcrumbs includes root + all levels", () => {
    manager.setRootLabel("Root Canvas");
    manager.enter({ documentId: "doc-a", label: "Page A" });
    manager.enter({ documentId: "doc-b", label: "Component B" });

    const crumbs = manager.breadcrumbs;
    expect(crumbs).toHaveLength(3);
    expect(crumbs[0]?.label).toBe("Root Canvas");
    expect(crumbs[1]?.documentId).toBe("doc-a");
    expect(crumbs[2]?.documentId).toBe("doc-b");
  });

  it("notifies subscribers on navigation change", () => {
    const listener = vi.fn();
    const unsub = manager.subscribe(listener);

    manager.enter({ documentId: "doc-x", label: "X" });
    expect(listener).toHaveBeenCalledTimes(1);

    manager.back();
    expect(listener).toHaveBeenCalledTimes(2);

    unsub();
    manager.enter({ documentId: "doc-y", label: "Y" });
    // After unsubscribe, should not receive further calls
    expect(listener).toHaveBeenCalledTimes(2);
  });

  it("back() is a no-op when already at root", () => {
    expect(() => manager.back()).not.toThrow();
    expect(manager.depth).toBe(0);
  });
});
