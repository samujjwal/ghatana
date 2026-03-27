import { act, render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createRef, type RefObject } from 'react';

import { getFocusableElements, useFocusTrap, type UseFocusTrapOptions } from '../useFocusTrap';

const originalOffsetParentDescriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetParent');

type FocusTrapHarnessProps = {
  isActive: boolean;
  options?: UseFocusTrapOptions;
  includeHidden?: boolean;
  includeDisabled?: boolean;
};

function FocusTrapHarness({
  isActive,
  options,
  includeHidden = false,
  includeDisabled = false,
}: FocusTrapHarnessProps) {
  const containerRef = createRef<HTMLDivElement>();
  useFocusTrap(containerRef as RefObject<HTMLElement>, isActive, options);

  return (
    <div>
      <button data-testid="outside-button" type="button">
        Outside
      </button>
      <div data-testid="trap-container" ref={containerRef} tabIndex={-1}>
        <button data-testid="first-button" type="button">
          First
        </button>
        <button data-testid="second-button" type="button">
          Second
        </button>
        {includeDisabled ? (
          <button data-testid="disabled-button" type="button" disabled>
            Disabled
          </button>
        ) : null}
        {includeHidden ? (
          <button data-testid="hidden-button" type="button" hidden>
            Hidden
          </button>
        ) : null}
      </div>
    </div>
  );
}

function markAsVisible(element: HTMLElement | null): void {
  if (!element) {
    return;
  }

  Object.defineProperty(element, 'offsetParent', {
    configurable: true,
    get: () => document.body,
  });
}

function markTrapElementsVisible(container: HTMLElement): void {
  markAsVisible(container);
  container.querySelectorAll<HTMLElement>('button, [tabindex]').forEach((element) => {
    markAsVisible(element);
  });
}

function mockVisibleLayout(): void {
  Object.defineProperty(HTMLElement.prototype, 'offsetParent', {
    configurable: true,
    get() {
      return this.hasAttribute('hidden') ? null : document.body;
    },
  });
}

describe('useFocusTrap', () => {
  afterEach(() => {
    if (originalOffsetParentDescriptor) {
      Object.defineProperty(HTMLElement.prototype, 'offsetParent', originalOffsetParentDescriptor);
    } else {
      delete (HTMLElement.prototype as HTMLElement & { offsetParent?: Element | null }).offsetParent;
    }
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('returns only visible focusable elements', () => {
    mockVisibleLayout();

    const { getByTestId } = render(
      <FocusTrapHarness isActive={false} includeDisabled includeHidden />
    );

    const container = getByTestId('trap-container');
    markTrapElementsVisible(container);

    const focusableElements = getFocusableElements(container as HTMLElement);

    expect(focusableElements.map((element) => element.dataset.testid)).toEqual([
      'first-button',
      'second-button',
    ]);
  });

  it('focuses the first focusable element when activated', () => {
    vi.useFakeTimers();
    mockVisibleLayout();

    const { getByTestId } = render(<FocusTrapHarness isActive />);
    const container = getByTestId('trap-container');
    const firstButton = getByTestId('first-button');

    markTrapElementsVisible(container);

    act(() => {
      vi.runAllTimers();
    });

    expect(document.activeElement).toBe(firstButton);
  });

  it('respects the initialFocus selector when provided', () => {
    vi.useFakeTimers();
    mockVisibleLayout();

    const { getByTestId } = render(
      <FocusTrapHarness isActive options={{ initialFocus: '[data-testid="second-button"]' }} />
    );
    const container = getByTestId('trap-container');
    const secondButton = getByTestId('second-button');

    markTrapElementsVisible(container);

    act(() => {
      vi.runAllTimers();
    });

    expect(document.activeElement).toBe(secondButton);
  });

  it('wraps focus from the last element to the first and back again', () => {
    vi.useFakeTimers();
    mockVisibleLayout();

    const { getByTestId } = render(<FocusTrapHarness isActive />);
    const container = getByTestId('trap-container');
    const firstButton = getByTestId('first-button');
    const secondButton = getByTestId('second-button');

    markTrapElementsVisible(container);

    act(() => {
      vi.runAllTimers();
      secondButton.focus();
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
    });

    expect(document.activeElement).toBe(firstButton);

    act(() => {
      firstButton.focus();
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true }));
    });

    expect(document.activeElement).toBe(secondButton);
  });

  it('returns focus to the previously focused element when deactivated', () => {
    vi.useFakeTimers();
    mockVisibleLayout();

    const { getByTestId, rerender } = render(<FocusTrapHarness isActive={false} />);
    const outsideButton = getByTestId('outside-button');
    const container = getByTestId('trap-container');

    markTrapElementsVisible(container);

    act(() => {
      outsideButton.focus();
    });

    rerender(<FocusTrapHarness isActive />);
    markTrapElementsVisible(getByTestId('trap-container'));

    act(() => {
      vi.runAllTimers();
    });

    expect(document.activeElement).toBe(getByTestId('first-button'));

    rerender(<FocusTrapHarness isActive={false} />);

    expect(document.activeElement).toBe(outsideButton);
  });

  it('does not trap focus when disabled in options', () => {
    vi.useFakeTimers();
    mockVisibleLayout();

    const { getByTestId } = render(<FocusTrapHarness isActive options={{ enabled: false }} />);
    const container = getByTestId('trap-container');
    const outsideButton = getByTestId('outside-button');

    markTrapElementsVisible(container);

    act(() => {
      outsideButton.focus();
      vi.runAllTimers();
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
    });

    expect(document.activeElement).toBe(outsideButton);
  });
});