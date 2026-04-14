/**
 * @group unit
 * @tier U
 *
 * Smoke + shape tests for @ghatana/canvas-tools.
 *
 * canvas-tools re-exports drawing/editing tool classes from @ghatana/canvas.
 * These tests verify the facade loads cleanly and the named exports that
 * downstream tool code depends on remain stable.
 */
import { describe, it, expect } from 'vitest';

const EXPECTED_TOOL_EXPORTS = [
  'BaseTool',
  'SelectTool',
  'ShapeTool',
  'TextTool',
  'BrushTool',
  'ConnectorTool',
  'HighlighterTool',
  'PanTool',
  'EraserTool',
  'ZoomTool',
  'FrameTool',
  'LassoTool',
  'EyedropperTool',
  'ImageTool',
  'StickyNoteTool',
  'ToolManager',
] as const;

describe('@ghatana/canvas-tools facade', () => {
  it('imports without throwing', async () => {
    await expect(import('../index')).resolves.not.toThrow();
  });

  it.each(EXPECTED_TOOL_EXPORTS)('re-exports %s', async (name) => {
    const mod = await import('../index');
    expect(mod).toHaveProperty(name);
    expect((mod as Record<string, unknown>)[name]).toBeDefined();
  });

  it('exports exactly the documented tool names (no surprise additions)', async () => {
    const mod = await import('../index');
    const exportedNames = Object.keys(mod);
    const expectedSet = new Set<string>(EXPECTED_TOOL_EXPORTS);
    // All expected names must be present
    EXPECTED_TOOL_EXPORTS.forEach((name) => expect(exportedNames).toContain(name));
    // No extra names beyond the documented set
    const unknownExports = exportedNames.filter((k) => !expectedSet.has(k));
    expect(unknownExports).toHaveLength(0);
  });
});
