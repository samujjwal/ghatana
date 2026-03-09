import { render, screen, fireEvent } from '@testing-library/react';
import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

import { useAccessibility, useFocusTrap, useKeyboardNavigation } from '../useAccessibility';

// Mock console methods
const originalConsoleWarn = console.warn;
const originalConsoleError = console.error;
const originalConsoleGroup = console.group;
const originalConsoleGroupEnd = console.groupEnd;

describe('useAccessibility Hook', () => {
  beforeEach(() => {
    // Mock console methods to prevent noise in test output
    console.warn = vi.fn();
    console.error = vi.fn();
    console.group = vi.fn();
    console.groupEnd = vi.fn();
  });

  afterEach(() => {
    // Restore console methods
    console.warn = originalConsoleWarn;
    console.error = originalConsoleError;
    console.group = originalConsoleGroup;
    console.groupEnd = originalConsoleGroupEnd;
  });

  it('returns a ref', () => {
    const TestComponent = () => {
      const { ref } = useAccessibility({
        componentName: 'TestComponent',
        devOnly: false,
        logResults: false,
      });

      return <div ref={ref} data-testid="test-element">Test</div>;
    };

    render(<TestComponent />);

    const element = screen.getByTestId('test-element');
    expect(element).toBeInTheDocument();
  });

  it('does not throw when throwOnFailure is false', () => {
    const TestComponent = () => {
      const { ref } = useAccessibility({
        componentName: 'TestComponent',
        devOnly: false,
        logResults: false,
        throwOnFailure: false,
      });

      return <div ref={ref} data-testid="test-element">Test</div>;
    };

    expect(() => render(<TestComponent />)).not.toThrow();
  });
});

describe('useFocusTrap Hook', () => {
  it('returns a ref and methods', () => {
    const TestComponent = () => {
      const { ref, activate, deactivate, active } = useFocusTrap();

      return (
        <div ref={ref} data-testid="trap-container">
          <button onClick={() => activate()}>Activate</button>
          <button onClick={() => deactivate()}>Deactivate</button>
          <div data-testid="active-state">{active ? 'Active' : 'Inactive'}</div>
        </div>
      );
    };

    render(<TestComponent />);

    const container = screen.getByTestId('trap-container');
    expect(container).toBeInTheDocument();

    // Check initial state
    expect(screen.getByTestId('active-state')).toHaveTextContent('Inactive');

    // Activate focus trap
    fireEvent.click(screen.getByText('Activate'));
    expect(screen.getByTestId('active-state')).toHaveTextContent('Active');

    // Deactivate focus trap
    fireEvent.click(screen.getByText('Deactivate'));
    expect(screen.getByTestId('active-state')).toHaveTextContent('Inactive');
  });
});

describe('useKeyboardNavigation Hook', () => {
  it('returns a ref and navigation methods', () => {
    const TestComponent = () => {
      const { ref, activeIndex, focusItem } = useKeyboardNavigation({
        vertical: true,
        loop: true,
      });

      return (
        <ul ref={ref as unknown} data-testid="nav-container">
          <li tabIndex={0} data-testid="item-0">Item 1</li>
          <li tabIndex={0} data-testid="item-1">Item 2</li>
          <li tabIndex={0} data-testid="item-2">Item 3</li>
          <div data-testid="active-index">{activeIndex}</div>
          <button onClick={() => focusItem(1)}>Focus Item 2</button>
        </ul>
      );
    };

    render(<TestComponent />);

    const container = screen.getByTestId('nav-container');
    expect(container).toBeInTheDocument();

    // Check initial state
    expect(screen.getByTestId('active-index')).toHaveTextContent('-1');

    // Focus item
    fireEvent.click(screen.getByText('Focus Item 2'));
    expect(screen.getByTestId('active-index')).toHaveTextContent('1');
  });

  it('handles keyboard navigation', () => {
    const TestComponent = () => {
      const { ref, activeIndex } = useKeyboardNavigation({
        vertical: true,
        loop: true,
      });

      return (
        <ul ref={ref as unknown} data-testid="nav-container">
          <li tabIndex={0} data-testid="item-0">Item 1</li>
          <li tabIndex={0} data-testid="item-1">Item 2</li>
          <li tabIndex={0} data-testid="item-2">Item 3</li>
          <div data-testid="active-index">{activeIndex}</div>
        </ul>
      );
    };

    render(<TestComponent />);

    const container = screen.getByTestId('nav-container');

    // Simulate arrow down key press
    fireEvent.keyDown(container, { key: 'ArrowDown' });
    expect(screen.getByTestId('active-index')).toHaveTextContent('0');

    // Simulate arrow down key press again
    fireEvent.keyDown(container, { key: 'ArrowDown' });
    expect(screen.getByTestId('active-index')).toHaveTextContent('1');

    // Simulate end key press
    fireEvent.keyDown(container, { key: 'End' });
    expect(screen.getByTestId('active-index')).toHaveTextContent('2');

    // Simulate home key press
    fireEvent.keyDown(container, { key: 'Home' });
    expect(screen.getByTestId('active-index')).toHaveTextContent('0');
  });
});
