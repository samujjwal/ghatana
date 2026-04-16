/**
 * Panel Registry - Plugin Architecture
 *
 * Implements the registry pattern for dynamic panel registration and discovery.
 * Allows plugins to extend the left rail without modifying core code.
 *
 * @doc.type service
 * @doc.purpose Panel plugin system
 * @doc.layer components
 * @doc.pattern Registry + Plugin Architecture
 */

import type {
  RailTabId,
  RailPanelDefinition,
  RailPanelRegistry,
  RailContext,
  RailPlugin,
} from './UnifiedLeftRail.types';
import { matchesVisibilityRule, PANEL_VISIBILITY_RULES } from './rail-config';
import {
  AssetsPanel,
  LayersPanel,
  ComponentsPanel,
  InfrastructurePanel,
  HistoryPanel,
  FilesPanel,
  DataPanel,
  AIPanel,
  FavoritesPanel,
} from './panels';

/**
 * Singleton registry for panel management
 */
class PanelRegistryImpl implements RailPanelRegistry {
  private panels: Map<RailTabId, RailPanelDefinition> = new Map();
  private plugins: Map<string, RailPlugin> = new Map();

  constructor() {
    // Register default panels
    this.registerDefaultPanels();
  }

  /**
   * Register all default system panels
   */
  private registerDefaultPanels(): void {
    // Assets Panel - Available in all modes
    this.register({
      id: 'assets',
      label: 'Assets',
      icon: '🎨',
      category: 'content',
      order: 10,
      component: AssetsPanel,
      description: 'Shape library and templates',
      visibility: PANEL_VISIBILITY_RULES.assets,
    });

    // Layers Panel - Available in all modes
    this.register({
      id: 'layers',
      label: 'Layers',
      icon: '📐',
      category: 'structure',
      order: 20,
      component: LayersPanel,
      description: 'Canvas layer management',
      visibility: PANEL_VISIBILITY_RULES.layers,
    });

    // Components Panel - Design/Code/Architecture
    this.register({
      id: 'components',
      label: 'Components',
      icon: '🧩',
      category: 'content',
      order: 30,
      component: ComponentsPanel,
      description: 'Reusable component library',
      visibility: PANEL_VISIBILITY_RULES.components,
    });

    // Infrastructure Panel - Architecture/Deploy
    this.register({
      id: 'infrastructure',
      label: 'Infrastructure',
      icon: '☁️',
      category: 'technical',
      order: 40,
      component: InfrastructurePanel,
      description: 'Cloud resources and infrastructure',
      visibility: PANEL_VISIBILITY_RULES.infrastructure,
    });

    // History Panel - Available in all modes
    this.register({
      id: 'history',
      label: 'History',
      icon: '📜',
      category: 'utility',
      order: 50,
      component: HistoryPanel,
      description: 'Action history and undo/redo',
      visibility: PANEL_VISIBILITY_RULES.history,
    });

    // Files Panel - Code mode
    this.register({
      id: 'files',
      label: 'Files',
      icon: '📁',
      category: 'technical',
      order: 60,
      component: FilesPanel,
      description: 'File explorer',
      visibility: PANEL_VISIBILITY_RULES.files,
    });

    // Data Panel - Architecture/Code/Test
    this.register({
      id: 'data',
      label: 'Data',
      icon: '🗄️',
      category: 'technical',
      order: 70,
      component: DataPanel,
      description: 'Data sources and APIs',
      visibility: PANEL_VISIBILITY_RULES.data,
    });

    // AI Panel - Available in all modes
    this.register({
      id: 'ai',
      label: 'AI',
      icon: '✨',
      category: 'utility',
      order: 80,
      component: AIPanel,
      description: 'AI suggestions and patterns',
      visibility: PANEL_VISIBILITY_RULES.ai,
    });

    // Favorites Panel - Available in all modes
    this.register({
      id: 'favorites',
      label: 'Favorites',
      icon: '⭐',
      category: 'utility',
      order: 90,
      component: FavoritesPanel,
      description: 'Your saved items',
      visibility: PANEL_VISIBILITY_RULES.favorites,
    });

    console.log(
      `[PanelRegistry] Registered ${this.panels.size} default panels`
    );
  }

  /**
   * Get panel definition by ID
   */
  get(id: RailTabId): RailPanelDefinition | undefined {
    return this.panels.get(id);
  }

  /**
   * Register a new panel
   */
  register(panel: RailPanelDefinition): void {
    if (this.panels.has(panel.id)) {
      console.warn(
        `[PanelRegistry] Panel "${panel.id}" is already registered. Overwriting.`
      );
    }
    this.panels.set(panel.id, panel);
    console.log(`[PanelRegistry] Registered panel: ${panel.id}`);
  }

  /**
   * Unregister a panel
   */
  unregister(id: RailTabId): void {
    const deleted = this.panels.delete(id);
    if (deleted) {
      console.log(`[PanelRegistry] Unregistered panel: ${id}`);
    }
  }

  /**
   * Get all visible panels for current context
   */
  getVisiblePanels(context: RailContext): RailPanelDefinition[] {
    return Array.from(this.panels.values())
      .filter((panel) => {
        const visibility = panel.visibility || PANEL_VISIBILITY_RULES[panel.id];
        return matchesVisibilityRule(visibility, {
          mode: context.mode,
          role: context.role,
          phase: context.phase,
        });
      })
      .sort((a, b) => (a.order || 999) - (b.order || 999));
  }

  /**
   * Get panels specifically for a mode
   */
  getPanelsForMode(mode: string): RailPanelDefinition[] {
    return Array.from(this.panels.values())
      .filter((panel) => {
        const visibility = panel.visibility || PANEL_VISIBILITY_RULES[panel.id];
        if (!visibility) return true;
        if (visibility.modes && !visibility.modes.includes(mode as unknown)) {
          return false;
        }
        return true;
      })
      .sort((a, b) => (a.order || 999) - (b.order || 999));
  }

  /**
   * Install a plugin
   */
  installPlugin(plugin: RailPlugin): void {
    if (this.plugins.has(plugin.id)) {
      console.warn(`[PanelRegistry] Plugin "${plugin.id}" already installed`);
      return;
    }

    console.log(
      `[PanelRegistry] Installing plugin: ${plugin.name} v${plugin.version}`
    );

    // Register panels
    if (plugin.panels) {
      plugin.panels.forEach((panel) => this.register(panel));
    }

    // Run initialization
    if (plugin.initialize) {
      plugin.initialize(this);
    }

    this.plugins.set(plugin.id, plugin);
  }

  /**
   * Uninstall a plugin
   */
  uninstallPlugin(pluginId: string): void {
    const plugin = this.plugins.get(pluginId);
    if (!plugin) return;

    console.log(`[PanelRegistry] Uninstalling plugin: ${plugin.name}`);

    // Cleanup
    if (plugin.cleanup) {
      plugin.cleanup();
    }

    // Unregister panels
    if (plugin.panels) {
      plugin.panels.forEach((panel) => this.unregister(panel.id));
    }

    this.plugins.delete(pluginId);
  }

  /**
   * Get all registered plugins
   */
  getPlugins(): RailPlugin[] {
    return Array.from(this.plugins.values());
  }

  /**
   * Get all panel IDs
   */
  getAllPanelIds(): RailTabId[] {
    return Array.from(this.panels.keys());
  }
}

/**
 * Global panel registry instance
 */
export const panelRegistry = new PanelRegistryImpl();

/**
 * React hook for accessing the registry
 */
export function usePanelRegistry(): RailPanelRegistry {
  return panelRegistry;
}

/**
 * Helper to create a simple plugin
 */
export function createPlugin(config: {
  id: string;
  name: string;
  version?: string;
  panels?: RailPanelDefinition[];
  initialize?: (registry: RailPanelRegistry) => void;
}): RailPlugin {
  return {
    version: '1.0.0',
    ...config,
  };
}
