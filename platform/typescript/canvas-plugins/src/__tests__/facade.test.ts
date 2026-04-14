/**
 * @group unit
 * @tier U
 *
 * Smoke + shape tests for @ghatana/canvas-plugins.
 *
 * canvas-plugins re-exports the plugin system (types, manager, registries)
 * from @ghatana/canvas. These tests confirm the facade imports cleanly and
 * every named export remains stable.
 */
import { describe, it, expect } from 'vitest';

// ─── Type exports (checked via string key because they have no runtime value) ──
const EXPECTED_TYPE_EXPORTS: string[] = [
  'PluginManifest',
  'PluginCapability',
  'PluginState',
  'PluginContext',
  'CanvasPlugin',
  'CanvasElementData',
  'NodeTypeDefinition',
  'NodeComponentProps',
  'PortDefinition',
  'EdgeTypeDefinition',
  'EdgeComponentProps',
  'ToolDefinition',
  'ToolContext',
  'ToolHandlers',
  'CanvasPointerEvent',
  'PanelDefinition',
  'PanelComponentProps',
  'PluginCanvasAPI',
];

// ─── Value (runtime) exports ───────────────────────────────────────────────
const EXPECTED_VALUE_EXPORTS = [
  'PluginError',
  'PluginErrorCode',
  'PluginManager',
  'getPluginManager',
  'PluginElementRegistry',
  'NodeTypeRegistry',
  'EdgeTypeRegistry',
  'PluginToolRegistry',
  'PanelRegistry',
  'ShortcutRegistry',
  'ContextMenuRegistry',
  'getElementRegistry',
  'getNodeTypeRegistry',
  'getEdgeTypeRegistry',
  'getToolRegistry',
  'getPanelRegistry',
  'getShortcutRegistry',
  'getContextMenuRegistry',
  'resetAllRegistries',
  'registerPluginContributions',
  'unregisterPluginContributions',
] as const;

describe('@ghatana/canvas-plugins facade', () => {
  it('imports without throwing', async () => {
    await expect(import('../index')).resolves.not.toThrow();
  });

  it('resolved module is defined', async () => {
    const mod = await import('../index');
    expect(mod).toBeDefined();
  });

  it.each(EXPECTED_VALUE_EXPORTS)('re-exports runtime value: %s', async (name) => {
    const mod = await import('../index');
    expect(mod).toHaveProperty(name);
    expect((mod as Record<string, unknown>)[name]).toBeDefined();
  });

  it('exports PluginManager with expected lifecycle methods', async () => {
    const { PluginManager } = await import('../index');
    expect(typeof PluginManager).toBe('function');
    // PluginManager is a class — verify prototype has install / uninstall
    expect(PluginManager.prototype).toHaveProperty('install');
    expect(PluginManager.prototype).toHaveProperty('uninstall');
  });

  it('exports getPluginManager as a function', async () => {
    const { getPluginManager } = await import('../index');
    expect(typeof getPluginManager).toBe('function');
  });

  it('exports resetAllRegistries as a function', async () => {
    const { resetAllRegistries } = await import('../index');
    expect(typeof resetAllRegistries).toBe('function');
  });
});
