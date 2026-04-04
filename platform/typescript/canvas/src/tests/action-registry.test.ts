import { describe, it, expect } from "vitest";
import { ActionRegistry, ActionDefinition, ActionContext } from "../core/action-registry.js";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeAction(
  id: string,
  category: ActionDefinition["category"] = "universal",
  overrides: Partial<ActionDefinition> = {}
): ActionDefinition {
  return {
    id,
    label: `Label for ${id}`,
    icon: "⚙️",
    category,
    handler: () => undefined,
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("ActionRegistry", () => {
  describe("register / getAction", () => {
    it("registers and retrieves an action by id", () => {
      const registry = new ActionRegistry();
      const action = makeAction("test-action");
      registry.register(action);
      expect(registry.getAction("test-action")).toBe(action);
    });

    it("returns undefined for unknown action id", () => {
      const registry = new ActionRegistry();
      expect(registry.getAction("nonexistent")).toBeUndefined();
    });

    it("overrides an existing action when re-registered with same id", () => {
      const registry = new ActionRegistry();
      registry.register(makeAction("a", "universal", { label: "First" }));
      registry.register(makeAction("a", "universal", { label: "Second" }));
      expect(registry.getAction("a")?.label).toBe("Second");
    });
  });

  describe("registerMany", () => {
    it("registers multiple actions at once", () => {
      const registry = new ActionRegistry();
      registry.registerMany([makeAction("x"), makeAction("y"), makeAction("z")]);
      expect(registry.getAllActions()).toHaveLength(3);
    });
  });

  describe("getAllActions", () => {
    it("returns empty array when no actions are registered", () => {
      const registry = new ActionRegistry();
      expect(registry.getAllActions()).toHaveLength(0);
    });

    it("returns all registered actions", () => {
      const registry = new ActionRegistry();
      registry.register(makeAction("a"));
      registry.register(makeAction("b"));
      const ids = registry.getAllActions().map((a) => a.id);
      expect(ids).toContain("a");
      expect(ids).toContain("b");
    });
  });

  describe("registerLayerActions / getActionsForContext", () => {
    it("returns layer actions for matching context", () => {
      const registry = new ActionRegistry();
      registry.registerLayerActions("architecture", [makeAction("arch-action")]);
      const ctx: ActionContext = { layer: "architecture" };
      const actions = registry.getActionsForContext(ctx);
      expect(actions.some((a) => a.id === "arch-action")).toBe(true);
    });

    it("does not return layer actions for non-matching context", () => {
      const registry = new ActionRegistry();
      registry.registerLayerActions("architecture", [makeAction("arch-action")]);
      const ctx: ActionContext = { layer: "data" };
      const actions = registry.getActionsForContext(ctx);
      expect(actions.some((a) => a.id === "arch-action")).toBe(false);
    });
  });

  describe("registerPhaseActions", () => {
    it("returns phase actions for matching context", () => {
      const registry = new ActionRegistry();
      registry.registerPhaseActions("INTENT", [makeAction("intent-action")]);
      const actions = registry.getActionsForContext({ phase: "INTENT" });
      expect(actions.some((a) => a.id === "intent-action")).toBe(true);
    });

    it("does not return phase actions for non-matching phase", () => {
      const registry = new ActionRegistry();
      registry.registerPhaseActions("INTENT", [makeAction("intent-action")]);
      const actions = registry.getActionsForContext({ phase: "SHAPE" });
      expect(actions.some((a) => a.id === "intent-action")).toBe(false);
    });
  });

  describe("registerRoleActions", () => {
    it("returns role actions when context includes the role", () => {
      const registry = new ActionRegistry();
      registry.registerRoleActions("admin", [makeAction("admin-action")]);
      const actions = registry.getActionsForContext({ roles: ["admin", "viewer"] });
      expect(actions.some((a) => a.id === "admin-action")).toBe(true);
    });

    it("does not return role actions when context has different roles", () => {
      const registry = new ActionRegistry();
      registry.registerRoleActions("admin", [makeAction("admin-action")]);
      const actions = registry.getActionsForContext({ roles: ["viewer"] });
      expect(actions.some((a) => a.id === "admin-action")).toBe(false);
    });
  });

  describe("universal actions", () => {
    it("includes universal actions regardless of context", () => {
      const registry = new ActionRegistry();
      registry.register(makeAction("universal-act", "universal"));
      const actions = registry.getActionsForContext({});
      expect(actions.some((a) => a.id === "universal-act")).toBe(true);
    });
  });

  describe("isVisible filter in getActionsForContext", () => {
    it("excludes action when isVisible returns false for context", () => {
      const registry = new ActionRegistry();
      registry.register(
        makeAction("hidden-action", "universal", {
          isVisible: (ctx) => ctx.layer === "architecture",
        })
      );
      const actions = registry.getActionsForContext({ layer: "data" });
      expect(actions.some((a) => a.id === "hidden-action")).toBe(false);
    });

    it("includes action when isVisible returns true for context", () => {
      const registry = new ActionRegistry();
      registry.register(
        makeAction("visible-action", "universal", {
          isVisible: (ctx) => ctx.layer === "architecture",
        })
      );
      const actions = registry.getActionsForContext({ layer: "architecture" });
      expect(actions.some((a) => a.id === "visible-action")).toBe(true);
    });
  });

  describe("priority sorting", () => {
    it("sorts actions by priority descending", () => {
      const registry = new ActionRegistry();
      registry.register(makeAction("low", "universal", { priority: 1 }));
      registry.register(makeAction("high", "universal", { priority: 10 }));
      registry.register(makeAction("mid", "universal", { priority: 5 }));
      const actions = registry.getActionsForContext({});
      const ids = actions.map((a) => a.id);
      expect(ids.indexOf("high")).toBeLessThan(ids.indexOf("mid"));
      expect(ids.indexOf("mid")).toBeLessThan(ids.indexOf("low"));
    });

    it("sorts alphabetically by label when priorities are equal", () => {
      const registry = new ActionRegistry();
      registry.register(makeAction("b", "universal", { label: "Bravo", priority: 5 }));
      registry.register(makeAction("a", "universal", { label: "Alpha", priority: 5 }));
      const actions = registry.getActionsForContext({});
      const ids = actions.map((a) => a.id);
      expect(ids.indexOf("a")).toBeLessThan(ids.indexOf("b"));
    });
  });
});
