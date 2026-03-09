/**
 * Action Registry System
 *
 * Generic, extensible action registry for canvas operations.
 * Supports layer-based, phase-based, role-based, and universal actions.
 *
 * @doc.type core
 * @doc.purpose Centralized action management
 * @doc.layer core
 * @doc.pattern Registry + Strategy
 */

export interface ActionContext {
  layer?: string;
  phase?: string;
  roles?: string[];
  selection?: "none" | "single" | "multiple";
  elementType?: string;
}

export interface ActionDefinition {
  id: string;
  label: string;
  icon: string;
  shortcut?: string;
  category: "layer" | "phase" | "role" | "universal" | "selection";
  description?: string;
  handler: (context: ActionContext) => void | Promise<void>;
  isEnabled?: (context: ActionContext) => boolean;
  isVisible?: (context: ActionContext) => boolean;
  priority?: number; // Higher = more important (default: 0)
}

export interface ActionGroup {
  id: string;
  label: string;
  actions: ActionDefinition[];
}

/**
 * Action Registry
 *
 * Manages all available actions and provides filtering/querying capabilities.
 */
export class ActionRegistry {
  private actions: Map<string, ActionDefinition> = new Map();
  private layerActions: Map<string, Set<string>> = new Map();
  private phaseActions: Map<string, Set<string>> = new Map();
  private roleActions: Map<string, Set<string>> = new Map();
  private universalActions: Set<string> = new Set();

  /**
   * Register a new action
   */
  register(action: ActionDefinition): void {
    this.actions.set(action.id, action);

    // Index by category for fast lookup
    if (action.category === "universal") {
      this.universalActions.add(action.id);
    }
  }

  /**
   * Register multiple actions at once
   */
  registerMany(actions: ActionDefinition[]): void {
    actions.forEach((action) => this.register(action));
  }

  /**
   * Register actions for a specific layer
   */
  registerLayerActions(layer: string, actions: ActionDefinition[]): void {
    if (!this.layerActions.has(layer)) {
      this.layerActions.set(layer, new Set());
    }
    const layerSet = this.layerActions.get(layer)!;

    actions.forEach((action) => {
      this.register({ ...action, category: "layer" });
      layerSet.add(action.id);
    });
  }

  /**
   * Register actions for a specific phase
   */
  registerPhaseActions(phase: string, actions: ActionDefinition[]): void {
    if (!this.phaseActions.has(phase)) {
      this.phaseActions.set(phase, new Set());
    }
    const phaseSet = this.phaseActions.get(phase)!;

    actions.forEach((action) => {
      this.register({ ...action, category: "phase" });
      phaseSet.add(action.id);
    });
  }

  /**
   * Register actions for a specific role
   */
  registerRoleActions(role: string, actions: ActionDefinition[]): void {
    if (!this.roleActions.has(role)) {
      this.roleActions.set(role, new Set());
    }
    const roleSet = this.roleActions.get(role)!;

    actions.forEach((action) => {
      this.register({ ...action, category: "role" });
      roleSet.add(action.id);
    });
  }

  /**
   * Get action by ID
   */
  getAction(id: string): ActionDefinition | undefined {
    return this.actions.get(id);
  }

  /**
   * Get all actions
   */
  getAllActions(): ActionDefinition[] {
    return Array.from(this.actions.values());
  }

  /**
   * Get actions for a specific context
   *
   * This is the main method for retrieving context-aware actions.
   */
  getActionsForContext(context: ActionContext): ActionDefinition[] {
    const actionIds = new Set<string>();

    // 1. Add layer-specific actions
    if (context.layer && this.layerActions.has(context.layer)) {
      this.layerActions.get(context.layer)!.forEach((id) => actionIds.add(id));
    }

    // 2. Add phase-specific actions
    if (context.phase && this.phaseActions.has(context.phase)) {
      this.phaseActions.get(context.phase)!.forEach((id) => actionIds.add(id));
    }

    // 3. Add role-specific actions
    if (context.roles) {
      context.roles.forEach((role) => {
        if (this.roleActions.has(role)) {
          this.roleActions.get(role)!.forEach((id) => actionIds.add(id));
        }
      });
    }

    // 4. Add universal actions
    this.universalActions.forEach((id) => actionIds.add(id));

    // Convert IDs to actions and filter
    const actions = Array.from(actionIds)
      .map((id) => this.actions.get(id))
      .filter((action): action is ActionDefinition => {
        if (!action) return false;

        // Check if action is visible
        if (action.isVisible && !action.isVisible(context)) {
          return false;
        }

        return true;
      });

    // Sort by priority (higher first), then by label
    return actions.sort((a, b) => {
      const priorityA = a.priority ?? 0;
      const priorityB = b.priority ?? 0;
      if (priorityA !== priorityB) {
        return priorityB - priorityA;
      }
      return a.label.localeCompare(b.label);
    });
  }

  /**
   * Get actions grouped by category
   */
  getActionsByCategory(context: ActionContext): ActionGroup[] {
    const actions = this.getActionsForContext(context);
    const groups: Map<string, ActionDefinition[]> = new Map();

    actions.forEach((action) => {
      if (!groups.has(action.category)) {
        groups.set(action.category, []);
      }
      groups.get(action.category)!.push(action);
    });

    const categoryLabels: Record<string, string> = {
      layer: "Layer Actions",
      phase: "Phase Actions",
      role: "Role Actions",
      selection: "Selection Actions",
      universal: "Universal Actions",
    };

    return Array.from(groups.entries()).map(([category, actions]) => ({
      id: category,
      label: categoryLabels[category] || category,
      actions,
    }));
  }

  /**
   * Search actions by query
   */
  searchActions(query: string, context?: ActionContext): ActionDefinition[] {
    const lowerQuery = query.toLowerCase();
    const allActions = context
      ? this.getActionsForContext(context)
      : this.getAllActions();

    return allActions.filter(
      (action) =>
        action.label.toLowerCase().includes(lowerQuery) ||
        action.description?.toLowerCase().includes(lowerQuery) ||
        action.id.toLowerCase().includes(lowerQuery),
    );
  }

  /**
   * Execute an action by ID
   */
  async executeAction(actionId: string, context: ActionContext): Promise<void> {
    const action = this.actions.get(actionId);
    if (!action) {
      throw new Error(`Action not found: ${actionId}`);
    }

    // Check if action is enabled
    if (action.isEnabled && !action.isEnabled(context)) {
      throw new Error(`Action is disabled: ${actionId}`);
    }

    await action.handler(context);
  }

  /**
   * Get action by keyboard shortcut
   */
  getActionByShortcut(shortcut: string): ActionDefinition | undefined {
    return Array.from(this.actions.values()).find(
      (action) => action.shortcut === shortcut,
    );
  }

  /**
   * Clear all actions
   */
  clear(): void {
    this.actions.clear();
    this.layerActions.clear();
    this.phaseActions.clear();
    this.roleActions.clear();
    this.universalActions.clear();
  }

  /**
   * Unregister an action
   */
  unregister(actionId: string): void {
    this.actions.delete(actionId);
    this.universalActions.delete(actionId);

    // Remove from layer actions
    this.layerActions.forEach((set) => set.delete(actionId));

    // Remove from phase actions
    this.phaseActions.forEach((set) => set.delete(actionId));

    // Remove from role actions
    this.roleActions.forEach((set) => set.delete(actionId));
  }

  /**
   * Get statistics about registered actions
   */
  getStats(): {
    total: number;
    byCategory: Record<string, number>;
    byLayer: Record<string, number>;
    byPhase: Record<string, number>;
    byRole: Record<string, number>;
  } {
    const byCategory: Record<string, number> = {};
    this.actions.forEach((action) => {
      byCategory[action.category] = (byCategory[action.category] || 0) + 1;
    });

    const byLayer: Record<string, number> = {};
    this.layerActions.forEach((set, layer) => {
      byLayer[layer] = set.size;
    });

    const byPhase: Record<string, number> = {};
    this.phaseActions.forEach((set, phase) => {
      byPhase[phase] = set.size;
    });

    const byRole: Record<string, number> = {};
    this.roleActions.forEach((set, role) => {
      byRole[role] = set.size;
    });

    return {
      total: this.actions.size,
      byCategory,
      byLayer,
      byPhase,
      byRole,
    };
  }
}

/**
 * Global action registry instance
 */
let globalRegistry: ActionRegistry | null = null;

/**
 * Get the global action registry
 */
export function getActionRegistry(): ActionRegistry {
  if (!globalRegistry) {
    globalRegistry = new ActionRegistry();
  }
  return globalRegistry;
}

/**
 * Reset the global action registry (useful for testing)
 */
export function resetActionRegistry(): void {
  globalRegistry = new ActionRegistry();
}
