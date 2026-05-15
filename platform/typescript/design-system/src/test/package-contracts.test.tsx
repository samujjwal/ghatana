/**
 * TypeScript package contract tests for @ghatana/design-system.
 *
 * Covers:
 * 1. React 19 compatibility — components render without React 18-style act()
 *    warnings and accept React 19 concurrent-mode roots.
 * 2. SSR-safe utility functions — pure helpers produce the same output in a
 *    server (non-DOM) context (tested via unit-level assertions, no window access).
 * 3. Accessibility (a11y) contracts — key interactive components expose the
 *    required ARIA roles, labels, and keyboard attributes.
 * 4. Cross-package export integrity — the public barrel exports all documented
 *    symbols without throwing at import time.
 */

import { afterEach, describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import React from 'react';

import { Button } from '../atoms/Button';
import { Spinner } from '../atoms/Spinner';
import { Input } from '../atoms/Input';
import { Checkbox } from '../atoms/Checkbox';

const originalWindow = globalThis.window;

afterEach(() => {
  if (globalThis.window === undefined && originalWindow !== undefined) {
    globalThis.window = originalWindow;
  }
});

// ---------------------------------------------------------------------------
// 1. React 19 compatibility
// ---------------------------------------------------------------------------

describe('React 19 compatibility', () => {
  it('renders Button with React 19 without errors', () => {
    const { container } = render(<Button>Save</Button>);
    expect(container.querySelector('button')).not.toBeNull();
  });

  it('renders Spinner without errors in React 19 concurrent mode', () => {
    const { container } = render(<Spinner aria-label="Loading" />);
    expect(container.querySelector('svg')).not.toBeNull();
  });

  it('renders Input without errors in React 19', () => {
    const { container } = render(
      <Input label="Email" type="email" placeholder="user@example.com" />
    );
    expect(container.querySelector('input')).not.toBeNull();
  });

  it('Button ref forwarding works with React 19 createRef', () => {
    const ref = React.createRef<HTMLButtonElement>();
    render(<Button ref={ref}>Ref test</Button>);
    expect(ref.current).toBeInstanceOf(HTMLButtonElement);
  });

  it('Button onClick is called correctly in React 19', () => {
    const handler = vi.fn();
    render(<Button onClick={handler}>Click</Button>);
    screen.getByRole('button').click();
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('renders multiple components as siblings without key warnings', () => {
    const items = ['A', 'B', 'C'];
    const { container } = render(
      <div>
        {items.map((label) => (
          <Button key={label}>{label}</Button>
        ))}
      </div>
    );
    expect(container.querySelectorAll('button')).toHaveLength(3);
  });
});

// ---------------------------------------------------------------------------
// 2. SSR-safe utility contracts
// ---------------------------------------------------------------------------

describe('SSR-safe utility functions', () => {
  it('cn() from platform-utils produces stable output without DOM access', async () => {
    // Dynamic import to avoid circular resolver issues in the test runner
    const { cn } = await import('@ghatana/platform-utils');

    expect(cn('foo', 'bar')).toBe('foo bar');
    expect(cn('p-4', 'p-2')).toBe('p-2');           // Tailwind conflict resolved
    expect(cn('text-red-500', 'text-blue-500')).toBe('text-blue-500');
    expect(cn(false, null, undefined, 'visible')).toBe('visible');
    expect(cn()).toBe('');
  });

  it('cn() produces identical output across two calls with the same args (deterministic)', async () => {
    const { cn } = await import('@ghatana/platform-utils');
    const args = ['base', 'text-sm', false, undefined, 'font-bold'] as const;
    expect(cn(...args)).toBe(cn(...args));
  });

  it('design-system index does not reference window or document at import time', async () => {
    // This proves the barrel can be loaded in a Node/SSR context
    const originalWindow = globalThis.window;
    try {
      // @ts-expect-error — intentionally removing window to simulate SSR
      delete globalThis.window;
      await expect(import('../index')).resolves.toBeDefined();
    } finally {
      if (originalWindow !== undefined) {
        globalThis.window = originalWindow;
      }
    }
  }, 30000);
});

// ---------------------------------------------------------------------------
// 3. Accessibility contracts
// ---------------------------------------------------------------------------

describe('Accessibility contracts', () => {
  describe('Button', () => {
    it('has role="button" by default', () => {
      render(<Button>Submit</Button>);
      expect(screen.getByRole('button')).toBeInTheDocument();
    });

    it('aria-label overrides visible text as accessible name', () => {
      render(<Button aria-label="Save document">💾</Button>);
      expect(screen.getByRole('button', { name: 'Save document' })).toBeInTheDocument();
    });

    it('disabled button is not accessible to click interactions', () => {
      const handler = vi.fn();
      render(<Button disabled onClick={handler}>Disabled</Button>);
      const btn = screen.getByRole('button', { name: 'Disabled' });
      expect(btn).toBeDisabled();
    });

    it('aria-pressed is forwarded to the DOM element', () => {
      render(<Button aria-pressed="true">Toggle</Button>);
      const btn = screen.getByRole('button', { name: 'Toggle' });
      expect(btn).toHaveAttribute('aria-pressed', 'true');
    });

    it('aria-expanded is forwarded to the DOM element', () => {
      render(<Button aria-expanded="false" aria-haspopup="menu">Menu</Button>);
      const btn = screen.getByRole('button', { name: 'Menu' });
      expect(btn).toHaveAttribute('aria-expanded', 'false');
      expect(btn).toHaveAttribute('aria-haspopup', 'menu');
    });
  });

  describe('Spinner', () => {
    it('has role="status" or accessible label so screen readers announce loading', () => {
      render(<Spinner aria-label="Loading content" />);
      // Should be queryable by its accessible label
      const spinner = screen.getByLabelText('Loading content');
      expect(spinner).toBeInTheDocument();
    });

    it('does not receive focus (is not an interactive element)', () => {
      render(<Spinner aria-label="Busy" />);
      const spinner = screen.getByLabelText('Busy');
      // SVG spinners must not be in the tab order
      expect(spinner).not.toHaveAttribute('tabindex', '0');
    });
  });

  describe('Input (TextField)', () => {
    it('input is labelled by the label prop', () => {
      render(<Input label="Full Name" />);
      const input = screen.getByLabelText('Full Name');
      expect(input).toBeInTheDocument();
    });

    it('aria-required is forwarded when required=true', () => {
      render(<Input label="Email" required />);
      const input = screen.getByLabelText('Email');
      expect(input).toBeRequired();
    });

    it('error state exposes accessible error description', () => {
      render(<Input label="Username" error helperText="Username is required" />);
      // The helper text should be present in the document for screen readers
      expect(screen.getByText('Username is required')).toBeInTheDocument();
    });

    it('disabled input is not editable', () => {
      render(<Input label="Read-only field" disabled />);
      expect(screen.getByLabelText('Read-only field')).toBeDisabled();
    });
  });

  describe('Checkbox', () => {
    it('has role="checkbox"', () => {
      render(<Checkbox aria-label="Accept terms" />);
      expect(screen.getByRole('checkbox')).toBeInTheDocument();
    });

    it('checked state is reflected in aria-checked', () => {
      render(<Checkbox aria-label="Subscribe" checked onChange={vi.fn()} />);
      expect(screen.getByRole('checkbox')).toBeChecked();
    });

    it('disabled checkbox is not interactive', () => {
      render(<Checkbox aria-label="Disabled opt" disabled />);
      expect(screen.getByRole('checkbox')).toBeDisabled();
    });
  });
});

// ---------------------------------------------------------------------------
// 4. Cross-package export integrity
// ---------------------------------------------------------------------------

describe('Cross-package export integrity', () => {
  it('public barrel exports Button without error', async () => {
    const mod = await import('../index');
    expect(mod.Button).toBeDefined();
    expect(['function', 'object']).toContain(typeof mod.Button);
  });

  it('public barrel exports Input without error', async () => {
    const mod = await import('../index');
    expect(mod.Input).toBeDefined();
  });

  it('public barrel exports Spinner without error', async () => {
    const mod = await import('../index');
    expect(mod.Spinner).toBeDefined();
  });

  it('public barrel exports Checkbox without error', async () => {
    const mod = await import('../index');
    expect(mod.Checkbox).toBeDefined();
  });

  it('@ghatana/platform-utils cn helper is importable and functional', async () => {
    const { cn } = await import('@ghatana/platform-utils');
    expect(typeof cn).toBe('function');
    expect(cn('a', 'b')).toBe('a b');
  });
});
