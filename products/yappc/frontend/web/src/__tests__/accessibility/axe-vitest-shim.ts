/**
 * Lightweight axe-core wrapper providing a jest-axe / vitest-axe compatible API.
 *
 * Uses axe-core (already a devDependency) directly so we avoid adding a
 * separate jest-axe / vitest-axe peer dependency.
 */
import axeCore, { type RunOptions, type AxeResults } from 'axe-core';
import { expect } from 'vitest';

export type { AxeResults };

/**
 * Run axe against a container element (or the document body by default).
 */
export async function axe(
  element: Element | Document | null = document.body,
  options?: RunOptions,
): Promise<AxeResults> {
  const target = element ?? document.body;
  return axeCore.run(target as Element, options);
}

/**
 * Vitest matcher — registers toHaveNoViolations on `expect`.
 *
 * Usage:
 *   import { toHaveNoViolations } from './axe-vitest-shim';
 *   expect.extend(toHaveNoViolations);
 */
export const toHaveNoViolations = {
  toHaveNoViolations(received: AxeResults) {
    const violations = received.violations ?? [];

    if (violations.length === 0) {
      return { pass: true, message: () => 'Expected accessibility violations' };
    }

    const formatted = violations
      .map(
        (v) =>
          `[${v.id}] ${v.description}\n  Impact: ${v.impact ?? 'unknown'}\n  Nodes: ${v.nodes.length}`,
      )
      .join('\n\n');

    return {
      pass: false,
      message: () =>
        `Expected no accessibility violations, but found ${violations.length}:\n\n${formatted}`,
    };
  },
};

// Augment Vitest's `expect` type with the custom matcher.
declare module 'vitest' {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  interface Assertion<R = unknown> extends ExpectationWithAxe<R> {}
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  interface AsymmetricMatchersContaining extends ExpectationWithAxe {}
}

interface ExpectationWithAxe<_R = unknown> {
  toHaveNoViolations(): void;
}
