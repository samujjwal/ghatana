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
import { axe } from '../setup';
import type { ReactElement } from 'react';
import type { AxeCore } from 'vitest-axe';

export interface A11yRenderResult extends RenderResult {
  /** The axe violations found (empty array means accessible). */
  violations: AxeCore.Result[];
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
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (expect(results) as any).toHaveNoViolations();

  return { ...renderResult, violations: results.violations };
}
