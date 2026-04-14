/**
 * @group unit
 * @tier U
 *
 * Barrel shape tests for @ghatana/ui.
 *
 * @ghatana/ui is an internal implementation layer — all sub-modules currently
 * expose `export {}` stubs. These tests verify that the barrel index resolves
 * without throwing, and that each sub-barrel can be imported cleanly.
 * When concrete components are added, replace the stub guards with real
 * behaviour assertions.
 */
import { describe, it, expect } from 'vitest';

describe('@ghatana/ui barrel exports', () => {
  it('imports the root index without throwing', async () => {
    await expect(import('../index')).resolves.not.toThrow();
  });

  it('imports components/index without throwing', async () => {
    await expect(import('../components/index')).resolves.not.toThrow();
  });

  it('imports compositions/index without throwing', async () => {
    await expect(import('../compositions/index')).resolves.not.toThrow();
  });

  it('imports hooks/index without throwing', async () => {
    await expect(import('../hooks/index')).resolves.not.toThrow();
  });

  it('imports utils/index without throwing', async () => {
    await expect(import('../utils/index')).resolves.not.toThrow();
  });

  it('resolved barrel module is defined', async () => {
    const barrel = await import('../index');
    expect(barrel).toBeDefined();
  });
});
