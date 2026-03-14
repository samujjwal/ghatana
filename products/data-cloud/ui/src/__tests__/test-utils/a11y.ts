/**
 * Accessibility test utility.
 *
 * <p>Wraps {@code @testing-library/react} render with an automatic axe-core
 * accessibility scan. Use this in component tests to assert WCAG2AA
 * compliance in-place, in addition to the global afterEach scan.
 *
 * @example
 * ```typescript
 * import { renderWithA11y } from '../test-utils/a11y';
 *
 * it('renders without accessibility violations', async () => {
 *   await renderWithA11y(<MyButton label="Save" />);
 * });
 * ```
 *
 * @doc.type util
 * @doc.purpose Accessibility testing helper
 * @doc.layer frontend
 */

import { render, type RenderOptions, type RenderResult } from '@testing-library/react';
import { expect } from 'vitest';
import { toHaveNoViolations } from 'vitest-axe';
import { axe } from '../setup';
import type { ReactElement } from 'react';

// Extend vitest expect with axe matchers (idempotent — safe to call multiple times)
expect.extend(toHaveNoViolations);

export interface A11yRenderResult extends RenderResult {
  /** The axe violations found (empty array means accessible). */
  violations: import('axe-core').Result[];
}

/**
 * Renders {@code ui} into a JSDOM container and runs an axe-core audit.
 * Automatically asserts that there are no violations.
 *
 * @param ui - React element to render
 * @param options - Optional RTL render options (wrapper providers, etc.)
 * @returns RTL render result enriched with an axe violations array
 */
export async function renderWithA11y(
  ui: ReactElement,
  options?: RenderOptions
): Promise<A11yRenderResult> {
  const renderResult = render(ui, options);

  const results = await axe(renderResult.container);
  // Assert immediately so failures show the component under test in context
  expect(results).toHaveNoViolations();

  return { ...renderResult, violations: results.violations };
}
