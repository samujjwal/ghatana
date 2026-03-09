/**
 * @ghatana/canvas Plugin Registry
 *
 * Registry for plugin-contributed elements, nodes, edges, tools, and panels.
 * Provides lookup and validation for all registered items.
 *
 * @doc.type class
 * @doc.purpose Plugin contribution registry
 * @doc.layer core
 * @doc.pattern Registry
 */

import type {
  ElementDefinition,
  NodeTypeDefinition,
  EdgeTypeDefinition,
  ToolDefinition,
  PanelDefinition,
  KeyboardShortcut,
  ContextMenuItem,
} from "./types";

/**
 * Registry entry with source plugin info
 */
interface RegistryEntry<T> {
  definition: T;
  pluginId: string;
  registeredAt: number;
}

/**
 * Lookup function type
 */
type LookupFn<T> = (item: T) => string;

/**
 * Generic registry for plugin contributions
 */
class GenericRegistry<T> {
  private items = new Map<string, RegistryEntry<T>>();
  private byPlugin = new Map<string, Set<string>>();

  constructor(
    private readonly name: string,
    private readonly getId: LookupFn<T>,
  ) {}

  /**
   * Register an item
   */
  register(item: T, pluginId: string): void {
    const id = this.getId(item);

    if (this.items.has(id)) {
      const existing = this.items.get(id)!;
      console.warn(
        `[${this.name}Registry] Overwriting "${id}" from plugin "${existing.pluginId}" with plugin "${pluginId}"`,
      );
    }

    this.items.set(id, {
      definition: item,
      pluginId,
      registeredAt: Date.now(),
    });

    // Track by plugin
    if (!this.byPlugin.has(pluginId)) {
      this.byPlugin.set(pluginId, new Set());
    }
    this.byPlugin.get(pluginId)!.add(id);
  }

  /**
   * Unregister an item
   */
  unregister(id: string): boolean {
    const entry = this.items.get(id);
    if (!entry) return false;

    this.items.delete(id);
    this.byPlugin.get(entry.pluginId)?.delete(id);
    return true;
  }

  /**
   * Unregister all items from a plugin
   */
  unregisterByPlugin(pluginId: string): number {
    const ids = this.byPlugin.get(pluginId);
    if (!ids) return 0;

    let count = 0;
    for (const id of ids) {
      if (this.items.delete(id)) {
        count++;
      }
    }

    this.byPlugin.delete(pluginId);
    return count;
  }

  /**
   * Get an item by ID
   */
  get(id: string): T | undefined {
    return this.items.get(id)?.definition;
  }

  /**
   * Check if an item exists
   */
  has(id: string): boolean {
    return this.items.has(id);
  }

  /**
   * Get all items
   */
  getAll(): readonly T[] {
    return Array.from(this.items.values()).map((e) => e.definition);
  }

  /**
   * Get all items from a specific plugin
   */
  getByPlugin(pluginId: string): readonly T[] {
    const ids = this.byPlugin.get(pluginId);
    if (!ids) return [];

    return Array.from(ids)
      .map((id) => this.items.get(id)?.definition)
      .filter((d): d is T => d !== undefined);
  }

  /**
   * Get count of registered items
   */
  get size(): number {
    return this.items.size;
  }

  /**
   * Clear all items
   */
  clear(): void {
    this.items.clear();
    this.byPlugin.clear();
  }
}

/**
 * Element Registry
 *
 * Registry for freeform canvas elements (custom Lit components)
 */
export class ElementRegistry extends GenericRegistry<ElementDefinition> {
  constructor() {
    super("Element", (e) => e.type);
  }

  /**
   * Get elements by category
   */
  getByCategory(category: string): readonly ElementDefinition[] {
    return this.getAll().filter((e) => e.category === category);
  }

  /**
   * Get all categories
   */
  getCategories(): readonly string[] {
    const categories = new Set<string>();
    for (const element of this.getAll()) {
      if (element.category) {
        categories.add(element.category);
      }
    }
    return Array.from(categories);
  }
}

/**
 * Node Type Registry
 *
 * Registry for ReactFlow node types
 */
export class NodeTypeRegistry extends GenericRegistry<NodeTypeDefinition> {
  constructor() {
    super("NodeType", (n) => n.type);
  }

  /**
   * Get node types by category
   */
  getByCategory(category: string): readonly NodeTypeDefinition[] {
    return this.getAll().filter((n) => n.category === category);
  }

  /**
   * Get node types with validation
   */
  getValidatable(): readonly NodeTypeDefinition[] {
    return this.getAll().filter((n) => n.validate !== undefined);
  }
}

/**
 * Edge Type Registry
 *
 * Registry for ReactFlow edge types
 */
export class EdgeTypeRegistry extends GenericRegistry<EdgeTypeDefinition> {
  constructor() {
    super("EdgeType", (e) => e.type);
  }

  /**
   * Get connectable edge types for source/target nodes
   */
  getConnectableTypes(
    sourceNodeType: string,
    targetNodeType: string,
  ): readonly EdgeTypeDefinition[] {
    return this.getAll().filter((edgeDef) => {
      if (!edgeDef.connectionRules) return true;

      const { allowedSources, allowedTargets } = edgeDef.connectionRules;
      const sourceOk =
        !allowedSources || allowedSources.includes(sourceNodeType);
      const targetOk =
        !allowedTargets || allowedTargets.includes(targetNodeType);

      return sourceOk && targetOk;
    });
  }
}

/**
 * Tool Registry
 *
 * Registry for canvas tools (select, pan, draw, etc.)
 */
export class ToolRegistry extends GenericRegistry<ToolDefinition> {
  constructor() {
    super("Tool", (t) => t.id);
  }

  /**
   * Get tools by category
   */
  getByCategory(
    category: ToolDefinition["category"],
  ): readonly ToolDefinition[] {
    return this.getAll().filter((t) => t.category === category);
  }

  /**
   * Get default tools (select, pan)
   */
  getDefaultTools(): readonly ToolDefinition[] {
    return this.getAll().filter((t) => ["select", "pan"].includes(t.id));
  }

  /**
   * Get exclusive tools
   */
  getExclusiveTools(): readonly ToolDefinition[] {
    return this.getAll().filter((t) => t.exclusive !== false);
  }
}

/**
 * Panel Registry
 *
 * Registry for canvas panels (properties, layers, etc.)
 */
export class PanelRegistry extends GenericRegistry<PanelDefinition> {
  constructor() {
    super("Panel", (p) => p.id);
  }

  /**
   * Get panels by position
   */
  getByPosition(
    position: PanelDefinition["position"],
  ): readonly PanelDefinition[] {
    return this.getAll()
      .filter((p) => p.position === position)
      .sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
  }

  /**
   * Get initially visible panels
   */
  getVisiblePanels(): readonly PanelDefinition[] {
    return this.getAll().filter((p) => p.initiallyVisible !== false);
  }
}

/**
 * Keyboard Shortcut Registry
 *
 * Registry for keyboard shortcuts with conflict detection
 */
export class ShortcutRegistry {
  private shortcuts = new Map<
    string,
    KeyboardShortcut & { pluginId: string }
  >();

  /**
   * Register a shortcut
   */
  register(shortcut: KeyboardShortcut, pluginId: string): void {
    const key = this.normalizeKey(shortcut.key);

    if (this.shortcuts.has(key)) {
      const existing = this.shortcuts.get(key)!;
      console.warn(
        `[ShortcutRegistry] Shortcut "${shortcut.key}" conflicts with existing shortcut ` +
          `"${existing.name}" from plugin "${existing.pluginId}"`,
      );
    }

    this.shortcuts.set(key, { ...shortcut, pluginId });
  }

  /**
   * Unregister a shortcut
   */
  unregister(key: string): boolean {
    return this.shortcuts.delete(this.normalizeKey(key));
  }

  /**
   * Unregister all shortcuts from a plugin
   */
  unregisterByPlugin(pluginId: string): number {
    let count = 0;
    for (const [key, shortcut] of this.shortcuts) {
      if (shortcut.pluginId === pluginId) {
        this.shortcuts.delete(key);
        count++;
      }
    }
    return count;
  }

  /**
   * Get shortcut handler for a key
   */
  getHandler(key: string): ((event: KeyboardEvent) => void) | undefined {
    const shortcut = this.shortcuts.get(this.normalizeKey(key));
    return shortcut?.handler;
  }

  /**
   * Get all shortcuts
   */
  getAll(): readonly KeyboardShortcut[] {
    return Array.from(this.shortcuts.values());
  }

  /**
   * Normalize key string for comparison
   */
  private normalizeKey(key: string): string {
    return key.toLowerCase().split("+").sort().join("+");
  }
}

/**
 * Context Menu Registry
 *
 * Registry for context menu items
 */
export class ContextMenuRegistry {
  private items = new Map<string, ContextMenuItem & { pluginId: string }>();

  /**
   * Register a context menu item
   */
  register(item: ContextMenuItem, pluginId: string): void {
    const key = `${item.target}:${item.id}`;
    this.items.set(key, { ...item, pluginId });
  }

  /**
   * Unregister a context menu item
   */
  unregister(target: string, id: string): boolean {
    return this.items.delete(`${target}:${id}`);
  }

  /**
   * Get items for a target
   */
  getForTarget(target: ContextMenuItem["target"]): readonly ContextMenuItem[] {
    return Array.from(this.items.values())
      .filter((i) => i.target === target)
      .sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
  }

  /**
   * Get all items
   */
  getAll(): readonly ContextMenuItem[] {
    return Array.from(this.items.values());
  }
}

// ============================================================================
// GLOBAL REGISTRIES
// ============================================================================

let elementRegistry: ElementRegistry | null = null;
let nodeTypeRegistry: NodeTypeRegistry | null = null;
let edgeTypeRegistry: EdgeTypeRegistry | null = null;
let toolRegistry: ToolRegistry | null = null;
let panelRegistry: PanelRegistry | null = null;
let shortcutRegistry: ShortcutRegistry | null = null;
let contextMenuRegistry: ContextMenuRegistry | null = null;

/**
 * Get the global element registry
 */
export function getElementRegistry(): ElementRegistry {
  if (!elementRegistry) {
    elementRegistry = new ElementRegistry();
  }
  return elementRegistry;
}

/**
 * Get the global node type registry
 */
export function getNodeTypeRegistry(): NodeTypeRegistry {
  if (!nodeTypeRegistry) {
    nodeTypeRegistry = new NodeTypeRegistry();
  }
  return nodeTypeRegistry;
}

/**
 * Get the global edge type registry
 */
export function getEdgeTypeRegistry(): EdgeTypeRegistry {
  if (!edgeTypeRegistry) {
    edgeTypeRegistry = new EdgeTypeRegistry();
  }
  return edgeTypeRegistry;
}

/**
 * Get the global tool registry
 */
export function getToolRegistry(): ToolRegistry {
  if (!toolRegistry) {
    toolRegistry = new ToolRegistry();
  }
  return toolRegistry;
}

/**
 * Get the global panel registry
 */
export function getPanelRegistry(): PanelRegistry {
  if (!panelRegistry) {
    panelRegistry = new PanelRegistry();
  }
  return panelRegistry;
}

/**
 * Get the global shortcut registry
 */
export function getShortcutRegistry(): ShortcutRegistry {
  if (!shortcutRegistry) {
    shortcutRegistry = new ShortcutRegistry();
  }
  return shortcutRegistry;
}

/**
 * Get the global context menu registry
 */
export function getContextMenuRegistry(): ContextMenuRegistry {
  if (!contextMenuRegistry) {
    contextMenuRegistry = new ContextMenuRegistry();
  }
  return contextMenuRegistry;
}

/**
 * Reset all registries (for testing)
 */
export function resetAllRegistries(): void {
  elementRegistry = null;
  nodeTypeRegistry = null;
  edgeTypeRegistry = null;
  toolRegistry = null;
  panelRegistry = null;
  shortcutRegistry = null;
  contextMenuRegistry = null;
}

/**
 * Register all contributions from a plugin
 */
export function registerPluginContributions(
  pluginId: string,
  elements?: readonly ElementDefinition[],
  nodeTypes?: readonly NodeTypeDefinition[],
  edgeTypes?: readonly EdgeTypeDefinition[],
  tools?: readonly ToolDefinition[],
  panels?: readonly PanelDefinition[],
  shortcuts?: readonly KeyboardShortcut[],
  contextMenuItems?: readonly ContextMenuItem[],
): void {
  elements?.forEach((e) => getElementRegistry().register(e, pluginId));
  nodeTypes?.forEach((n) => getNodeTypeRegistry().register(n, pluginId));
  edgeTypes?.forEach((e) => getEdgeTypeRegistry().register(e, pluginId));
  tools?.forEach((t) => getToolRegistry().register(t, pluginId));
  panels?.forEach((p) => getPanelRegistry().register(p, pluginId));
  shortcuts?.forEach((s) => getShortcutRegistry().register(s, pluginId));
  contextMenuItems?.forEach((i) =>
    getContextMenuRegistry().register(i, pluginId),
  );
}

/**
 * Unregister all contributions from a plugin
 */
export function unregisterPluginContributions(pluginId: string): void {
  getElementRegistry().unregisterByPlugin(pluginId);
  getNodeTypeRegistry().unregisterByPlugin(pluginId);
  getEdgeTypeRegistry().unregisterByPlugin(pluginId);
  getToolRegistry().unregisterByPlugin(pluginId);
  getPanelRegistry().unregisterByPlugin(pluginId);
  getShortcutRegistry().unregisterByPlugin(pluginId);
  // Context menu doesn't have plugin tracking, skip
}
