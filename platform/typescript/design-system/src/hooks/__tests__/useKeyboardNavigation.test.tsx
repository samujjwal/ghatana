import { act, render, renderHook } from '@testing-library/react';
import { createRef } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  useFocusRestore,
  useFocusVisible,
  useKeyboardNavigation,
} from '../useKeyboardNavigation';

function mockVisibleLayout(): void {
  Object.defineProperty(HTMLElement.prototype, 'offsetParent', {
    configurable: true,
    get() {
      return this.hasAttribute('hidden') ? null : document.body;
    },
  });
}

function KeyboardNavigationHarness({
  orientation = 'vertical',
  loop = false,
  onActivate,
}: {
  orientation?: 'vertical' | 'horizontal' | 'both';
  loop?: boolean;
  onActivate?: (element: HTMLElement, index: number) => void;
}) {
  const containerRef = createRef<HTMLDivElement>();
  useKeyboardNavigation({
    containerRef,
    itemSelector: '[data-nav-item="true"]',
    orientation,
    loop,
    onActivate,
  });

  return (
    <div ref={containerRef} data-testid="nav-container">
      <button data-testid="item-1" data-nav-item="true" type="button">
        One
      </button>
      <button data-testid="item-2" data-nav-item="true" type="button">
        Two
      </button>
      <button data-testid="item-3" data-nav-item="true" type="button">
        Three
      </button>
    </div>
  );
}

describe('useKeyboardNavigation', () => {
  const originalOffsetParentDescriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetParent');

  afterEach(() => {
    if (originalOffsetParentDescriptor) {
      Object.defineProperty(HTMLElement.prototype, 'offsetParent', originalOffsetParentDescriptor);
    } else {
      delete (HTMLElement.prototype as HTMLElement & { offsetParent?: Element | null }).offsetParent;
    }
  });

  it('moves focus vertically with arrow keys', () => {
    mockVisibleLayout();
    const { getByTestId } = render(<KeyboardNavigationHarness />);

    const container = getByTestId('nav-container');
    const firstItem = getByTestId('item-1');
    const secondItem = getByTestId('item-2');

    act(() => {
      firstItem.focus();
      container.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    });

    expect(document.activeElement).toBe(secondItem);
  });

  it('loops focus when loop is enabled', () => {
    mockVisibleLayout();
    const { getByTestId } = render(<KeyboardNavigationHarness loop />);

    const container = getByTestId('nav-container');
    const firstItem = getByTestId('item-1');
    const lastItem = getByTestId('item-3');

    act(() => {
      lastItem.focus();
      container.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    });

    expect(document.activeElement).toBe(firstItem);
  });

  it('supports horizontal activation and Home/End navigation', () => {
    mockVisibleLayout();
    const onActivate = vi.fn();
    const { getByTestId } = render(
      <KeyboardNavigationHarness orientation="horizontal" onActivate={onActivate} />
    );

    const container = getByTestId('nav-container');
    const firstItem = getByTestId('item-1');
    const secondItem = getByTestId('item-2');
    const lastItem = getByTestId('item-3');

    act(() => {
      firstItem.focus();
      container.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    });
    expect(document.activeElement).toBe(secondItem);

    act(() => {
      container.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
    });
    expect(document.activeElement).toBe(lastItem);

    act(() => {
      container.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
      container.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    });

    expect(onActivate).toHaveBeenCalledWith(firstItem, 0);
  });
});

describe('useFocusRestore', () => {
  it('restores focus when the condition becomes true', () => {
    const targetRef = createRef<HTMLButtonElement>();
    const focusSpy = vi.fn();

    const { rerender } = renderHook(
      ({ shouldRestore }) => {
        useFocusRestore(targetRef, shouldRestore);
      },
      { initialProps: { shouldRestore: false } }
    );

    targetRef.current = { focus: focusSpy } as unknown as HTMLButtonElement;

    rerender({ shouldRestore: true });

    expect(focusSpy).toHaveBeenCalled();
  });
});

describe('useFocusVisible', () => {
  it('adds and removes data-focus-visible for keyboard focus', () => {
    renderHook(() => useFocusVisible());
    const button = document.createElement('button');
    document.body.appendChild(button);

    act(() => {
      document.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true }));
      button.dispatchEvent(new FocusEvent('focus', { bubbles: true }));
    });

    expect(button.getAttribute('data-focus-visible')).toBe('true');

    act(() => {
      button.dispatchEvent(new FocusEvent('blur', { bubbles: true }));
    });

    expect(button.hasAttribute('data-focus-visible')).toBe(false);
    button.remove();
  });

  it('does not mark mouse-driven focus as focus-visible', () => {
    renderHook(() => useFocusVisible());
    const button = document.createElement('button');
    document.body.appendChild(button);

    act(() => {
      document.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
      button.dispatchEvent(new FocusEvent('focus', { bubbles: true }));
    });

    expect(button.hasAttribute('data-focus-visible')).toBe(false);
    button.remove();
  });
});