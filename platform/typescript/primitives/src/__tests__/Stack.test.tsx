/**
 * @group unit
 * @tier U, I-br
 *
 * Tests for @ghatana/primitives — Stack, VStack, HStack layout components.
 */
import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';

import { Stack, VStack, HStack } from '../layout';

// ─── Stack ────────────────────────────────────────────────────────────────────

describe('Stack', () => {
  it('renders children', () => {
    render(<Stack><span data-testid="child">hi</span></Stack>);
    expect(screen.getByTestId('child')).toBeInTheDocument();
  });

  it('defaults to a <div> element', () => {
    render(<Stack data-testid="stack">x</Stack>);
    expect(screen.getByTestId('stack').tagName.toLowerCase()).toBe('div');
  });

  it('accepts a polymorphic `as` prop and renders the correct element', () => {
    render(<Stack as="section" data-testid="stack">x</Stack>);
    expect(screen.getByTestId('stack').tagName.toLowerCase()).toBe('section');
  });

  it('applies the vertical direction class when direction="vertical"', () => {
    render(<Stack direction="vertical" data-testid="stack">x</Stack>);
    const el = screen.getByTestId('stack');
    expect(el.className).toMatch(/flex-col/);
  });

  it('applies the horizontal direction class when direction="horizontal"', () => {
    render(<Stack direction="horizontal" data-testid="stack">x</Stack>);
    const el = screen.getByTestId('stack');
    expect(el.className).toMatch(/flex-row/);
  });

  it('applies a gap class for the provided gap value', () => {
    render(<Stack gap="md" data-testid="stack">x</Stack>);
    const el = screen.getByTestId('stack');
    // The exact Tailwind class for md gap — verify it contains a gap utility
    expect(el.className).toMatch(/gap-/);
  });

  it('applies no gap class when gap="none"', () => {
    render(<Stack gap="none" data-testid="stack">x</Stack>);
    const el = screen.getByTestId('stack');
    // Should either be absent or be gap-0
    const hasGap = el.className.includes('gap-') && !el.className.includes('gap-0');
    expect(hasGap).toBe(false);
  });

  it('applies different gap sizes distinctly', () => {
    const { rerender } = render(<Stack gap="sm" data-testid="stack">x</Stack>);
    const smClass = screen.getByTestId('stack').className;

    rerender(<Stack gap="xl" data-testid="stack">x</Stack>);
    const xlClass = screen.getByTestId('stack').className;

    expect(smClass).not.toBe(xlClass);
  });

  it('applies align class for the provided align value', () => {
    render(<Stack align="center" data-testid="stack">x</Stack>);
    expect(screen.getByTestId('stack').className).toMatch(/items-center/);
  });

  it('applies justify class for the provided justify value', () => {
    render(<Stack justify="between" data-testid="stack">x</Stack>);
    expect(screen.getByTestId('stack').className).toMatch(/justify-between/);
  });

  it('adds flex-wrap when wrap={true}', () => {
    render(<Stack wrap data-testid="stack">x</Stack>);
    expect(screen.getByTestId('stack').className).toMatch(/flex-wrap/);
  });

  it('does not add flex-wrap when wrap={false}', () => {
    render(<Stack wrap={false} data-testid="stack">x</Stack>);
    const classes = screen.getByTestId('stack').className;
    // flex-wrap should be absent or explicitly nowrap
    expect(classes).not.toMatch(/\bflex-wrap\b/);
  });

  it('merges additional className without overwriting layout classes', () => {
    render(<Stack className="custom-extra" direction="vertical" data-testid="stack">x</Stack>);
    const classes = screen.getByTestId('stack').className;
    expect(classes).toContain('custom-extra');
    expect(classes).toMatch(/flex-col/);
  });

  it('has the displayName "Stack" for DevTools', () => {
    expect(Stack.displayName).toBe('Stack');
  });

  it('renders multiple children', () => {
    render(
      <Stack>
        <span data-testid="a">a</span>
        <span data-testid="b">b</span>
        <span data-testid="c">c</span>
      </Stack>,
    );
    expect(screen.getByTestId('a')).toBeInTheDocument();
    expect(screen.getByTestId('b')).toBeInTheDocument();
    expect(screen.getByTestId('c')).toBeInTheDocument();
  });
});

// ─── VStack ───────────────────────────────────────────────────────────────────

describe('VStack', () => {
  it('renders children', () => {
    render(<VStack><span data-testid="v-child">v</span></VStack>);
    expect(screen.getByTestId('v-child')).toBeInTheDocument();
  });

  it('defaults to vertical direction (flex-col)', () => {
    render(<VStack data-testid="vstack">x</VStack>);
    expect(screen.getByTestId('vstack').className).toMatch(/flex-col/);
  });

  it('passes additional props through to Stack', () => {
    render(<VStack gap="lg" align="start" data-testid="vstack">x</VStack>);
    const el = screen.getByTestId('vstack');
    expect(el.className).toMatch(/gap-/);
    expect(el.className).toMatch(/items-start/);
  });
});

// ─── HStack ───────────────────────────────────────────────────────────────────

describe('HStack', () => {
  it('renders children', () => {
    render(<HStack><span data-testid="h-child">h</span></HStack>);
    expect(screen.getByTestId('h-child')).toBeInTheDocument();
  });

  it('defaults to horizontal direction (flex-row)', () => {
    render(<HStack data-testid="hstack">x</HStack>);
    expect(screen.getByTestId('hstack').className).toMatch(/flex-row/);
  });

  it('passes additional props through to Stack', () => {
    render(<HStack gap="sm" justify="end" data-testid="hstack">x</HStack>);
    const el = screen.getByTestId('hstack');
    expect(el.className).toMatch(/gap-/);
    expect(el.className).toMatch(/justify-end/);
  });
});
