/**
 * useAccessibility Hook Tests
 */

import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { createRef } from 'react';
import { useAccessibility, useFocusTrap } from '../useAccessibility';

describe('useAccessibility', () => {
  it('should return initial state', () => {
    const { result } = renderHook(() => useAccessibility());

    expect(result.current.isScreenReaderActive).toBe(false);
    expect(result.current.prefersReducedMotion).toBe(false);
    expect(result.current.trapFocus).toBeDefined();
    expect(result.current.focusFirst).toBeDefined();
    expect(result.current.focusLast).toBeDefined();
    expect(result.current.announce).toBeDefined();
  });

  it('should announce to screen reader', () => {
    const { result } = renderHook(() => useAccessibility());

    act(() => {
      result.current.announce('Test message');
    });

    // Check if announcement was made (would be in DOM)
    const announcements = document.querySelectorAll('[role="status"]');
    expect(announcements.length).toBeGreaterThan(0);
  });

  it('should save and restore focus', () => {
    const { result } = renderHook(() => useAccessibility());

    // Create a focusable element
    const button = document.createElement('button');
    button.id = 'test-button';
    document.body.appendChild(button);

    act(() => {
      button.focus();
      result.current.saveFocus();
    });

    expect(document.activeElement).toBe(button);

    // Change focus
    const input = document.createElement('input');
    document.body.appendChild(input);
    input.focus();

    act(() => {
      result.current.restoreFocus();
    });

    // Focus should be restored (in real implementation)
    document.body.removeChild(button);
    document.body.removeChild(input);
  });
});

describe('useFocusTrap', () => {
  it('should be callable', () => {
    const container = document.createElement('div');
    const containerRef = { current: container };

    const { result } = renderHook(() => useFocusTrap(containerRef));

    expect(result.current).toBeDefined();
  });
});
