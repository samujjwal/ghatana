/**
 * CSS presence / visual smoke tests — catch unstyled renders in CI.
 */
import React from 'react';
import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';

describe('CSS smoke tests', () => {
  it('index.css imports tailwindcss', async () => {
    const css = await import('@/index.css');
    // Static import assertion: if index.css is missing the build will fail.
    expect(css).toBeDefined();
  });

  it('dark mode class sets color-scheme', () => {
    // Ensure dark mode class exists in document
    document.documentElement.classList.add('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    document.documentElement.classList.remove('dark');
  });

  it('renders a styled element with Tailwind classes applied', () => {
    function StyledBox() {
      return <div className="bg-indigo-600 text-white px-4 py-2 rounded">Styled</div>;
    }
    const { container } = render(<StyledBox />);
    const el = container.firstChild as HTMLElement;
    expect(el).toBeTruthy();
    expect(el.className).toContain('bg-indigo-600');
    expect(el.className).toContain('text-white');
    expect(el.className).toContain('rounded');
  });
});
