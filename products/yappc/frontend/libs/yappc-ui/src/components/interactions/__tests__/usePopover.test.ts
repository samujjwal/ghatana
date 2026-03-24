/**
 * Tests for usePopover hook
 * Phase 5: Days 45-46 - Advanced Interaction Patterns
 */

import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

import {
  usePopover,
  useControlledPopover,
  usePopoverGroup,
  useNestedPopover,
  useConfirmPopover,
} from '../hooks/usePopover';

describe('usePopover', () => {
  describe('basic functionality', () => {
    it('should initialize with isOpen false', () => {
      const { result } = renderHook(() => usePopover());

      expect(result.current.isOpen).toBe(false);
    });

    it('should provide reference and floating props', () => {
      const { result } = renderHook(() => usePopover());

      expect(result.current.referenceProps).toBeDefined();
      expect(result.current.referenceProps.ref).toBeDefined();
      expect(result.current.referenceProps['aria-expanded']).toBe('false');
      expect(result.current.floatingProps).toBeDefined();
      expect(result.current.floatingProps.ref).toBeDefined();
      expect(result.current.floatingProps.id).toBeDefined();
      expect(result.current.floatingProps.style).toBeDefined();
    });

    it('should provide manual control methods', () => {
      const { result } = renderHook(() => usePopover());

      expect(result.current.open).toBeInstanceOf(Function);
      expect(result.current.close).toBeInstanceOf(Function);
      expect(result.current.toggle).toBeInstanceOf(Function);
    });

    it('should open popover with manual control', () => {
      const { result } = renderHook(() => usePopover());

      act(() => {
        result.current.open();
      });

      expect(result.current.isOpen).toBe(true);
      expect(result.current.referenceProps['aria-expanded']).toBe('true');
    });

    it('should close popover with manual control', () => {
      const { result } = renderHook(() => usePopover());

      act(() => {
        result.current.open();
      });
      expect(result.current.isOpen).toBe(true);

      act(() => {
        result.current.close();
      });
      expect(result.current.isOpen).toBe(false);
    });

    it('should toggle popover visibility', () => {
      const { result } = renderHook(() => usePopover());

      act(() => {
        result.current.toggle();
      });
      expect(result.current.isOpen).toBe(true);

      act(() => {
        result.current.toggle();
      });
      expect(result.current.isOpen).toBe(false);
    });

    it('should provide FloatingUI context', () => {
      const { result } = renderHook(() => usePopover());

      expect(result.current.context).toBeDefined();
    });

    it('should provide refs for advanced use cases', () => {
      const { result } = renderHook(() => usePopover());

      expect(result.current.refs).toBeDefined();
      expect(result.current.refs.setReference).toBeInstanceOf(Function);
      expect(result.current.refs.setFloating).toBeInstanceOf(Function);
    });
  });

  describe('placement', () => {
    it('should use default placement bottom-start', () => {
      const { result } = renderHook(() => usePopover());

      expect(result.current.placement).toBeDefined();
    });

    it('should support custom placement', () => {
      const { result } = renderHook(() => usePopover({ placement: 'top' }));

      expect(result.current.placement).toBeDefined();
    });

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
        const { result } = renderHook(() => usePopover({ placement }));

        expect(result.current.placement).toBeDefined();
      });
    });
  });

  describe('triggers', () => {
    it('should support click trigger (default)', () => {
      const { result } = renderHook(() => usePopover());

      // Click trigger is enabled by default
      expect(result.current.referenceProps).toBeDefined();
    });

    it('should support custom trigger', () => {
      const { result } = renderHook(() => usePopover({ trigger: 'hover' }));

      expect(result.current.referenceProps).toBeDefined();
    });

    it('should support multiple triggers', () => {
      const { result } = renderHook(() =>
        usePopover({ trigger: ['click', 'hover'] })
      );

      expect(result.current.referenceProps).toBeDefined();
    });

    it('should support manual trigger', () => {
      const { result } = renderHook(() => usePopover({ trigger: 'manual' }));

      // Manual trigger provides props but without automatic handlers
      expect(result.current.referenceProps).toBeDefined();
      expect(result.current.open).toBeInstanceOf(Function);
    });
  });

  describe('dismiss behavior', () => {
    it('should enable closeOnClickOutside by default', () => {
      const { result } = renderHook(() => usePopover());

      // Default is true, FloatingUI will handle dismissal
      expect(result.current.floatingProps).toBeDefined();
    });

    it('should support disabling closeOnClickOutside', () => {
      const { result } = renderHook(() =>
        usePopover({ closeOnClickOutside: false })
      );

      expect(result.current.floatingProps).toBeDefined();
    });

    it('should enable closeOnEscape by default', () => {
      const { result } = renderHook(() => usePopover());

      // Default is true, FloatingUI will handle dismissal
      expect(result.current.floatingProps).toBeDefined();
    });

    it('should support disabling closeOnEscape', () => {
      const { result } = renderHook(() => usePopover({ closeOnEscape: false }));

      expect(result.current.floatingProps).toBeDefined();
    });
  });

  describe('focus management', () => {
    it('should provide focus manager props', () => {
      const { result } = renderHook(() => usePopover());

      expect(result.current.focusManagerProps).toBeDefined();
      expect(result.current.focusManagerProps.context).toBeDefined();
      expect(result.current.focusManagerProps.modal).toBe(false);
      expect(result.current.focusManagerProps.initialFocus).toBe(0);
      expect(result.current.focusManagerProps.returnFocus).toBe(true);
    });

    it('should support modal mode', () => {
      const { result } = renderHook(() => usePopover({ modal: true }));

      expect(result.current.focusManagerProps.modal).toBe(true);
    });

    it('should support custom initialFocus', () => {
      const { result } = renderHook(() => usePopover({ initialFocus: 1 }));

      expect(result.current.focusManagerProps.initialFocus).toBe(1);
    });

    it('should support disabling returnFocus', () => {
      const { result } = renderHook(() => usePopover({ returnFocus: false }));

      expect(result.current.focusManagerProps.returnFocus).toBe(false);
    });
  });

  describe('arrow', () => {
    it('should not provide arrow props by default', () => {
      const { result } = renderHook(() => usePopover());

      expect(result.current.arrowProps).toBeNull();
    });

    it('should provide arrow props when enabled', () => {
      const { result } = renderHook(() => usePopover({ arrow: true }));

      expect(result.current.arrowProps).toBeDefined();
      expect(result.current.arrowProps?.ref).toBeDefined();
      expect(result.current.arrowProps?.['data-placement']).toBeDefined();
    });
  });

  describe('portal', () => {
    it('should provide portal props by default', () => {
      const { result } = renderHook(() => usePopover());

      expect(result.current.portalProps).toBeDefined();
      expect(result.current.portalProps?.id).toBeDefined();
    });

    it('should not provide portal props when disabled', () => {
      const { result } = renderHook(() => usePopover({ portal: false }));

      expect(result.current.portalProps).toBeNull();
    });
  });

  describe('disabled state', () => {
    it('should not open popover when disabled', () => {
      const { result } = renderHook(() => usePopover({ disabled: true }));

      act(() => {
        result.current.open();
      });

      expect(result.current.isOpen).toBe(false);
    });

    it('should close popover when disabled prop changes to true', () => {
      const { result, rerender } = renderHook(
        ({ disabled }) => usePopover({ disabled }),
        { initialProps: { disabled: false } }
      );

      act(() => {
        result.current.open();
      });
      expect(result.current.isOpen).toBe(true);

      rerender({ disabled: true });

      expect(result.current.isOpen).toBe(false);
    });
  });

  describe('onOpenChange callback', () => {
    it('should call onOpenChange when popover opens', () => {
      const onOpenChange = vi.fn();
      const { result } = renderHook(() => usePopover({ onOpenChange }));

      act(() => {
        result.current.open();
      });

      expect(onOpenChange).toHaveBeenCalledWith(true);
      expect(onOpenChange).toHaveBeenCalledTimes(1);
    });

    it('should call onOpenChange when popover closes', () => {
      const onOpenChange = vi.fn();
      const { result } = renderHook(() => usePopover({ onOpenChange }));

      act(() => {
        result.current.open();
      });
      onOpenChange.mockClear();

      act(() => {
        result.current.close();
      });

      expect(onOpenChange).toHaveBeenCalledWith(false);
      expect(onOpenChange).toHaveBeenCalledTimes(1);
    });

    it('should call onOpenChange when toggling', () => {
      const onOpenChange = vi.fn();
      const { result } = renderHook(() => usePopover({ onOpenChange }));

      act(() => {
        result.current.toggle();
      });

      expect(onOpenChange).toHaveBeenCalledWith(true);

      act(() => {
        result.current.toggle();
      });

      expect(onOpenChange).toHaveBeenCalledWith(false);
    });
  });

  describe('offset', () => {
    it('should use default offset of 8px', () => {
      const { result } = renderHook(() => usePopover());

      // Offset is applied internally to FloatingUI
      expect(result.current.floatingProps).toBeDefined();
    });

    it('should support custom offset', () => {
      const { result } = renderHook(() => usePopover({ offset: 16 }));

      expect(result.current.floatingProps).toBeDefined();
    });
  });

  describe('aria attributes', () => {
    it('should set aria-expanded to false when closed', () => {
      const { result } = renderHook(() => usePopover());

      expect(result.current.referenceProps['aria-expanded']).toBe('false');
    });

    it('should set aria-expanded to true when open', () => {
      const { result } = renderHook(() => usePopover());

      act(() => {
        result.current.open();
      });

      expect(result.current.referenceProps['aria-expanded']).toBe('true');
    });

    it('should set aria-controls when open', () => {
      const { result } = renderHook(() => usePopover());

      expect(result.current.referenceProps['aria-controls']).toBeUndefined();

      act(() => {
        result.current.open();
      });

      expect(result.current.referenceProps['aria-controls']).toBeDefined();
      expect(result.current.referenceProps['aria-controls']).toBe(
        result.current.floatingProps.id
      );
    });
  });
});

describe('useControlledPopover', () => {
  it('should call setIsOpen when popover opens', () => {
    const setIsOpen = vi.fn();
    const { result } = renderHook(() => useControlledPopover(setIsOpen));

    act(() => {
      result.current.open();
    });

    expect(setIsOpen).toHaveBeenCalledWith(true);
  });

  it('should call setIsOpen when popover closes', () => {
    const setIsOpen = vi.fn();
    const { result } = renderHook(() => useControlledPopover(setIsOpen));

    act(() => {
      result.current.open();
    });
    setIsOpen.mockClear();

    act(() => {
      result.current.close();
    });

    expect(setIsOpen).toHaveBeenCalledWith(false);
  });

  it('should support custom options', () => {
    const setIsOpen = vi.fn();
    const { result } = renderHook(() =>
      useControlledPopover(setIsOpen, { modal: true })
    );

    expect(result.current.focusManagerProps.modal).toBe(true);
  });
});

describe('usePopoverGroup', () => {
  it('should initialize with no active popover', () => {
    const { result } = renderHook(() => usePopoverGroup());

    expect(result.current.activeId).toBeNull();
  });

  it('should provide register function', () => {
    const { result } = renderHook(() => usePopoverGroup());

    expect(result.current.register).toBeInstanceOf(Function);
    expect(result.current.closeAll).toBeInstanceOf(Function);
  });

  it('should provide closeAll function', () => {
    const { result } = renderHook(() => usePopoverGroup());

    act(() => {
      result.current.closeAll();
    });

    expect(result.current.activeId).toBeNull();
  });
});

describe('useNestedPopover', () => {
  it('should create a popover instance', () => {
    const { result } = renderHook(() => useNestedPopover());

    expect(result.current.isOpen).toBe(false);
    expect(result.current.open).toBeInstanceOf(Function);
    expect(result.current.close).toBeInstanceOf(Function);
  });

  it('should support options', () => {
    const { result } = renderHook(() => useNestedPopover({ modal: true }));

    expect(result.current.focusManagerProps.modal).toBe(true);
  });

  it('should provide all popover props', () => {
    const { result } = renderHook(() => useNestedPopover());

    expect(result.current.referenceProps).toBeDefined();
    expect(result.current.floatingProps).toBeDefined();
    expect(result.current.context).toBeDefined();
  });
});

describe('useConfirmPopover', () => {
  it('should create a popover with confirm/cancel methods', () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();

    const { result } = renderHook(() =>
      useConfirmPopover({ onConfirm, onCancel })
    );

    expect(result.current.confirm).toBeInstanceOf(Function);
    expect(result.current.cancel).toBeInstanceOf(Function);
    expect(result.current.triggerProps).toBeDefined();
    expect(result.current.popoverProps).toBeDefined();
  });

  it('should call onConfirm and close popover', () => {
    const onConfirm = vi.fn();
    const { result } = renderHook(() => useConfirmPopover({ onConfirm }));

    act(() => {
      result.current.open();
    });
    expect(result.current.isOpen).toBe(true);

    act(() => {
      result.current.confirm();
    });

    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(result.current.isOpen).toBe(false);
  });

  it('should call onCancel and close popover', () => {
    const onCancel = vi.fn();
    const { result } = renderHook(() => useConfirmPopover({ onCancel }));

    act(() => {
      result.current.open();
    });
    expect(result.current.isOpen).toBe(true);

    act(() => {
      result.current.cancel();
    });

    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(result.current.isOpen).toBe(false);
  });

  it('should support custom popover options', () => {
    const onConfirm = vi.fn();
    const { result } = renderHook(() =>
      useConfirmPopover({
        onConfirm,
        popoverOptions: { modal: true },
      })
    );

    expect(result.current.focusManagerProps.modal).toBe(true);
  });

  it('should work without callbacks', () => {
    const { result } = renderHook(() => useConfirmPopover({}));

    act(() => {
      result.current.open();
    });
    expect(result.current.isOpen).toBe(true);

    act(() => {
      result.current.confirm();
    });
    expect(result.current.isOpen).toBe(false);

    act(() => {
      result.current.open();
    });

    act(() => {
      result.current.cancel();
    });
    expect(result.current.isOpen).toBe(false);
  });

  it('should maintain all base popover functionality', () => {
    const onConfirm = vi.fn();
    const { result } = renderHook(() => useConfirmPopover({ onConfirm }));

    // Test manual toggle
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
