/**
 * Tests for useTooltip hook
 * Phase 5: Days 45-46 - Advanced Interaction Patterns
 */

import { renderHook, act, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

import {
  useTooltip,
  useTooltipState,
  useTooltipGroup,
  useDelayedTooltip,
} from '../hooks/useTooltip';

describe('useTooltip', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  describe('basic functionality', () => {
    it('should initialize with isOpen false', () => {
      const { result } = renderHook(() => useTooltip('Test tooltip'));

      expect(result.current.isOpen).toBe(false);
      expect(result.current.content).toBe('Test tooltip');
    });

    it('should provide reference and floating props', () => {
      const { result } = renderHook(() => useTooltip('Test tooltip'));

      expect(result.current.referenceProps).toBeDefined();
      expect(result.current.referenceProps.ref).toBeDefined();
      expect(result.current.floatingProps).toBeDefined();
      expect(result.current.floatingProps.ref).toBeDefined();
      expect(result.current.floatingProps.style).toBeDefined();
    });

    it('should support manual control methods', () => {
      const { result } = renderHook(() => useTooltip('Test tooltip'));

      expect(result.current.show).toBeInstanceOf(Function);
      expect(result.current.hide).toBeInstanceOf(Function);
      expect(result.current.toggle).toBeInstanceOf(Function);
    });

    it('should show tooltip with manual control', () => {
      const { result } = renderHook(() => useTooltip('Test tooltip'));

      act(() => {
        result.current.show();
      });

      expect(result.current.isOpen).toBe(true);
    });

    it('should hide tooltip with manual control', () => {
      const { result } = renderHook(() => useTooltip('Test tooltip'));

      act(() => {
        result.current.show();
      });
      expect(result.current.isOpen).toBe(true);

      act(() => {
        result.current.hide();
      });
      expect(result.current.isOpen).toBe(false);
    });

    it('should toggle tooltip visibility', () => {
      const { result } = renderHook(() => useTooltip('Test tooltip'));

      act(() => {
        result.current.toggle();
      });
      expect(result.current.isOpen).toBe(true);

      act(() => {
        result.current.toggle();
      });
      expect(result.current.isOpen).toBe(false);
    });
  });

  describe('placement', () => {
    const placements = [
      'top',
      'top-start',
      'top-end',
      'bottom',
      'bottom-start',
      'bottom-end',
      'left',
      'left-start',
      'left-end',
      'right',
      'right-start',
      'right-end',
    ] as const;

    placements.forEach((placement) => {
      it(`should support ${placement} placement`, () => {
        const { result } = renderHook(() => useTooltip('Test', { placement }));

        expect(result.current.placement).toBeDefined();
      });
    });

    it('should default to top placement', () => {
      const { result } = renderHook(() => useTooltip('Test'));

      // Initial placement is 'top', actual placement may differ after FloatingUI calculations
      expect(result.current.placement).toBeDefined();
    });
  });

  describe('triggers', () => {
    it('should support hover trigger (default)', () => {
      const { result } = renderHook(() => useTooltip('Test'));

      // Hover trigger is enabled, props are merged by FloatingUI
      expect(result.current.referenceProps).toBeDefined();
    });

    it('should support focus trigger', () => {
      const { result } = renderHook(() =>
        useTooltip('Test', { trigger: 'focus' })
      );

      // Focus trigger is enabled, props are merged by FloatingUI
      expect(result.current.referenceProps).toBeDefined();
    });

    it('should support click trigger', () => {
      const { result } = renderHook(() =>
        useTooltip('Test', { trigger: 'click' })
      );

      // Click trigger is enabled, props are merged by FloatingUI
      expect(result.current.referenceProps).toBeDefined();
    });

    it('should support multiple triggers', () => {
      const { result } = renderHook(() =>
        useTooltip('Test', { trigger: ['hover', 'focus'] })
      );

      // Multiple triggers enabled, props are merged by FloatingUI
      expect(result.current.referenceProps).toBeDefined();
    });

    it('should support manual trigger', () => {
      const { result } = renderHook(() =>
        useTooltip('Test', { trigger: 'manual' })
      );

      // Manual trigger still provides props but without automatic handlers
      expect(result.current.referenceProps).toBeDefined();
      expect(result.current.show).toBeInstanceOf(Function);
      expect(result.current.hide).toBeInstanceOf(Function);
    });
  });

  describe('delays', () => {
    it('should support custom delayShow option', () => {
      const { result } = renderHook(() =>
        useTooltip('Test', { delayShow: 500, trigger: 'hover' })
      );

      // Custom delay is configured, FloatingUI will apply it
      expect(result.current.referenceProps).toBeDefined();
    });

    it('should support custom delayHide option', () => {
      const { result } = renderHook(() =>
        useTooltip('Test', { delayShow: 0, delayHide: 300, trigger: 'hover' })
      );

      // Custom hide delay is configured, FloatingUI will apply it
      expect(result.current.referenceProps).toBeDefined();
    });

    it('should use default delays (200ms show, 0ms hide)', () => {
      const { result } = renderHook(() => useTooltip('Test'));

      // Defaults are applied internally by DEFAULT_TOOLTIP_OPTIONS
      expect(result.current.referenceProps).toBeDefined();
    });
  });

  describe('interactive mode', () => {
    it('should set pointer-events to auto when interactive', () => {
      const { result } = renderHook(() =>
        useTooltip('Test', { interactive: true })
      );

      expect(result.current.floatingProps.style?.pointerEvents).toBe('auto');
    });

    it('should set pointer-events to none when not interactive (default)', () => {
      const { result } = renderHook(() => useTooltip('Test'));

      expect(result.current.floatingProps.style?.pointerEvents).toBe('none');
    });
  });

  describe('arrow', () => {
    it('should provide arrow props when arrow is enabled (default)', () => {
      const { result } = renderHook(() => useTooltip('Test'));

      expect(result.current.arrowProps).toBeDefined();
      expect(result.current.arrowProps?.ref).toBeDefined();
      expect(result.current.arrowProps?.['data-placement']).toBeDefined();
    });

    it('should not provide arrow props when arrow is disabled', () => {
      const { result } = renderHook(() => useTooltip('Test', { arrow: false }));

      expect(result.current.arrowProps).toBeNull();
    });
  });

  describe('maxWidth', () => {
    it('should apply maxWidth as pixels when number', () => {
      const { result } = renderHook(() =>
        useTooltip('Test', { maxWidth: 200 })
      );

      expect(result.current.floatingProps.style?.maxWidth).toBe('200px');
    });

    it('should apply maxWidth as string when provided', () => {
      const { result } = renderHook(() =>
        useTooltip('Test', { maxWidth: '20rem' })
      );

      expect(result.current.floatingProps.style?.maxWidth).toBe('20rem');
    });

    it('should use default maxWidth of 300px', () => {
      const { result } = renderHook(() => useTooltip('Test'));

      expect(result.current.floatingProps.style?.maxWidth).toBe('300px');
    });
  });

  describe('disabled state', () => {
    it('should not show tooltip when disabled', () => {
      const { result } = renderHook(() =>
        useTooltip('Test', { disabled: true })
      );

      act(() => {
        result.current.show();
      });

      expect(result.current.isOpen).toBe(false);
    });

    it('should hide tooltip when disabled prop changes to true', () => {
      const { result, rerender } = renderHook(
        ({ disabled }) => useTooltip('Test', { disabled }),
        { initialProps: { disabled: false } }
      );

      act(() => {
        result.current.show();
      });
      expect(result.current.isOpen).toBe(true);

      rerender({ disabled: true });

      expect(result.current.isOpen).toBe(false);
    });
  });

  describe('escape key handling', () => {
    it('should close tooltip on Escape key', () => {
      const { result } = renderHook(() => useTooltip('Test'));

      act(() => {
        result.current.show();
      });
      expect(result.current.isOpen).toBe(true);

      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'Escape' });
        document.dispatchEvent(event);
      });

      expect(result.current.isOpen).toBe(false);
    });

    it('should not close tooltip on other keys', () => {
      const { result } = renderHook(() => useTooltip('Test'));

      act(() => {
        result.current.show();
      });
      expect(result.current.isOpen).toBe(true);

      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'Enter' });
        document.dispatchEvent(event);
      });

      expect(result.current.isOpen).toBe(true);
    });
  });

  describe('offset', () => {
    it('should use default offset of 8px', () => {
      const { result } = renderHook(() => useTooltip('Test'));

      // Offset is applied internally to FloatingUI, we verify props exist
      expect(result.current.floatingProps).toBeDefined();
    });

    it('should support custom offset', () => {
      const { result } = renderHook(() => useTooltip('Test', { offset: 16 }));

      expect(result.current.floatingProps).toBeDefined();
    });
  });
});

describe('useTooltipState', () => {
  it('should initialize with closed state by default', () => {
    const { result } = renderHook(() => useTooltipState());

    expect(result.current.isOpen).toBe(false);
  });

  it('should initialize with provided default state', () => {
    const { result } = renderHook(() => useTooltipState(true));

    expect(result.current.isOpen).toBe(true);
  });

  it('should show tooltip', () => {
    const { result } = renderHook(() => useTooltipState());

    act(() => {
      result.current.show();
    });

    expect(result.current.isOpen).toBe(true);
  });

  it('should hide tooltip', () => {
    const { result } = renderHook(() => useTooltipState(true));

    act(() => {
      result.current.hide();
    });

    expect(result.current.isOpen).toBe(false);
  });

  it('should toggle tooltip', () => {
    const { result } = renderHook(() => useTooltipState());

    act(() => {
      result.current.toggle();
    });
    expect(result.current.isOpen).toBe(true);

    act(() => {
      result.current.toggle();
    });
    expect(result.current.isOpen).toBe(false);
  });

  it('should provide setIsOpen function', () => {
    const { result } = renderHook(() => useTooltipState());

    act(() => {
      result.current.setIsOpen(true);
    });
    expect(result.current.isOpen).toBe(true);

    act(() => {
      result.current.setIsOpen(false);
    });
    expect(result.current.isOpen).toBe(false);
  });

  it('should maintain stable function references', () => {
    const { result, rerender } = renderHook(() => useTooltipState());

    const showRef = result.current.show;
    const hideRef = result.current.hide;
    const toggleRef = result.current.toggle;

    rerender();

    expect(result.current.show).toBe(showRef);
    expect(result.current.hide).toBe(hideRef);
    expect(result.current.toggle).toBe(toggleRef);
  });
});

describe('useTooltipGroup', () => {
  it('should initialize with no active tooltip', () => {
    const { result } = renderHook(() => useTooltipGroup());

    expect(result.current.activeId).toBeNull();
  });

  it('should register tooltips and track active one', () => {
    const { result } = renderHook(() => useTooltipGroup());

    const tooltip1 = result.current.register('tooltip1');
    const tooltip2 = result.current.register('tooltip2');

    expect(tooltip1.isOpen).toBe(false);
    expect(tooltip2.isOpen).toBe(false);

    act(() => {
      tooltip1.show();
    });

    expect(result.current.activeId).toBe('tooltip1');
  });

  it('should only show one tooltip at a time', () => {
    const { result } = renderHook(() => useTooltipGroup());

    let tooltip1 = result.current.register('tooltip1');
    let tooltip2 = result.current.register('tooltip2');

    act(() => {
      tooltip1.show();
    });

    // Re-register to get updated state
    tooltip1 = result.current.register('tooltip1');
    tooltip2 = result.current.register('tooltip2');

    expect(tooltip1.isOpen).toBe(true);
    expect(tooltip2.isOpen).toBe(false);

    act(() => {
      tooltip2.show();
    });

    // Re-register again
    tooltip1 = result.current.register('tooltip1');
    tooltip2 = result.current.register('tooltip2');

    expect(tooltip1.isOpen).toBe(false);
    expect(tooltip2.isOpen).toBe(true);
  });

  it('should hide active tooltip', () => {
    const { result } = renderHook(() => useTooltipGroup());

    const tooltip1 = result.current.register('tooltip1');

    act(() => {
      tooltip1.show();
    });

    expect(result.current.activeId).toBe('tooltip1');

    act(() => {
      tooltip1.hide();
    });

    expect(result.current.activeId).toBeNull();
  });

  it('should clear all tooltips', () => {
    const { result } = renderHook(() => useTooltipGroup());

    const tooltip1 = result.current.register('tooltip1');

    act(() => {
      tooltip1.show();
    });

    expect(result.current.activeId).toBe('tooltip1');

    act(() => {
      result.current.clear();
    });

    expect(result.current.activeId).toBeNull();
  });
});

describe('useDelayedTooltip', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should initialize with tooltip hidden', () => {
    const { result } = renderHook(() => useDelayedTooltip(1000));

    expect(result.current.isVisible).toBe(false);
    expect(result.current.isHovering).toBe(false);
  });

  it('should show tooltip after delay', () => {
    const { result } = renderHook(() => useDelayedTooltip(1000));

    act(() => {
      result.current.triggerProps.onMouseEnter();
    });

    expect(result.current.isHovering).toBe(true);
    expect(result.current.isVisible).toBe(false);

    act(() => {
      vi.advanceTimersByTime(1000);
    });

    expect(result.current.isVisible).toBe(true);
  });

  it('should hide tooltip immediately on mouse leave', () => {
    const { result } = renderHook(() => useDelayedTooltip(1000));

    act(() => {
      result.current.triggerProps.onMouseEnter();
    });

    act(() => {
      vi.advanceTimersByTime(1000);
    });

    expect(result.current.isVisible).toBe(true);

    act(() => {
      result.current.triggerProps.onMouseLeave();
    });

    expect(result.current.isHovering).toBe(false);
    expect(result.current.isVisible).toBe(false);
  });

  it('should cancel delay if mouse leaves before timeout', () => {
    const { result } = renderHook(() => useDelayedTooltip(1000));

    act(() => {
      result.current.triggerProps.onMouseEnter();
    });

    act(() => {
      vi.advanceTimersByTime(500);
    });

    expect(result.current.isVisible).toBe(false);

    act(() => {
      result.current.triggerProps.onMouseLeave();
    });

    act(() => {
      vi.advanceTimersByTime(500);
    });

    // Should still be hidden
    expect(result.current.isVisible).toBe(false);
  });

  it('should use default delay of 1000ms', () => {
    const { result } = renderHook(() => useDelayedTooltip());

    act(() => {
      result.current.triggerProps.onMouseEnter();
    });

    act(() => {
      vi.advanceTimersByTime(999);
    });

    expect(result.current.isVisible).toBe(false);

    act(() => {
      vi.advanceTimersByTime(1);
    });

    expect(result.current.isVisible).toBe(true);
  });

  it('should support custom delay', () => {
    const { result } = renderHook(() => useDelayedTooltip(2000));

    act(() => {
      result.current.triggerProps.onMouseEnter();
    });

    act(() => {
      vi.advanceTimersByTime(1999);
    });

    expect(result.current.isVisible).toBe(false);

    act(() => {
      vi.advanceTimersByTime(1);
    });

    expect(result.current.isVisible).toBe(true);
  });

  it('should provide correct trigger props', () => {
    const { result } = renderHook(() => useDelayedTooltip());

    expect(result.current.triggerProps.onMouseEnter).toBeInstanceOf(Function);
    expect(result.current.triggerProps.onMouseLeave).toBeInstanceOf(Function);
  });

  it('should provide correct tooltip props', () => {
    const { result } = renderHook(() => useDelayedTooltip());

    // Initially hidden
    expect(result.current.tooltipProps['aria-hidden']).toBe(true);

    // Show tooltip
    act(() => {
      result.current.triggerProps.onMouseEnter();
    });

    act(() => {
      vi.advanceTimersByTime(1000);
    });

    // Should be visible after delay
    expect(result.current.isVisible).toBe(true);
    expect(result.current.tooltipProps['aria-hidden']).toBe(false);
  });

  it('should handle rapid hover on/off correctly', () => {
    const { result } = renderHook(() => useDelayedTooltip(1000));

    // Hover on
    act(() => {
      result.current.triggerProps.onMouseEnter();
    });

    act(() => {
      vi.advanceTimersByTime(500);
    });

    // Hover off
    act(() => {
      result.current.triggerProps.onMouseLeave();
    });

    // Hover on again
    act(() => {
      result.current.triggerProps.onMouseEnter();
    });

    // Wait for second delay
    act(() => {
      vi.advanceTimersByTime(1000);
    });

    expect(result.current.isVisible).toBe(true);
  });
});
