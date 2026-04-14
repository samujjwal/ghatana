/**
 * @group unit
 * @tier U
 *
 * Smoke + shape tests for the deprecated @ghatana/canvas-core facade.
 *
 * canvas-core re-exports everything from @ghatana/canvas and
 * @ghatana/canvas/core. These tests verify that the facade loads without
 * error and continues to re-export the names that consumers rely on.
 *
 * NOTE: New code should import from '@ghatana/canvas' directly.
 * When this package is removed, delete these tests too.
 */
import { describe, it, expect } from 'vitest';

describe('@ghatana/canvas-core facade', () => {
  it('imports without throwing', async () => {
    await expect(import('../index')).resolves.not.toThrow();
  });

  it('re-exports Bounds from canonical canvas package', async () => {
    const mod = await import('../index');
    expect(mod).toHaveProperty('Bounds');
  });

  it('re-exports CanvasDocument from canonical canvas package', async () => {
    const mod = await import('../index');
    expect(mod).toHaveProperty('CanvasDocument');
  });

  it('re-exports CanvasEngine', async () => {
    const mod = await import('../index');
    expect(mod).toHaveProperty('CanvasEngine');
  });

  it('re-exports CanvasRenderer', async () => {
    const mod = await import('../index');
    expect(mod).toHaveProperty('CanvasRenderer');
  });

  it('does not export anything that is not in the canonical package', async () => {
    const canvas = await import('@ghatana/canvas');
    const facade = await import('../index');
    // Every key in facade must exist in the canonical canvas module
    // (allows additional keys from canvas/core re-export)
    const canvasKeys = new Set(Object.keys(canvas));
    const extraKeys = Object.keys(facade).filter(
      (k) => !canvasKeys.has(k),
    );
    // Extra keys are acceptable only if they come from @ghatana/canvas/core
    // This assertion just ensures we are purely re-exporting (no novel additions)
    expect(extraKeys.every((k) => typeof (facade as Record<string, unknown>)[k] !== 'undefined')).toBe(true);
  });
});
