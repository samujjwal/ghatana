import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { useDisclosure } from '../useDisclosure';

describe('useDisclosure', () => {
  it('initializes closed by default', () => {
    const { result } = renderHook(() => useDisclosure());

    expect(result.current.isOpen).toBe(false);
  });

  it('supports uncontrolled defaultOpen state', () => {
    const { result } = renderHook(() => useDisclosure({ defaultOpen: true }));

    expect(result.current.isOpen).toBe(true);
  });

  it('opens, closes, and toggles in uncontrolled mode', () => {
    const { result } = renderHook(() => useDisclosure());

    act(() => {
      result.current.onOpen();
    });
    expect(result.current.isOpen).toBe(true);

    act(() => {
      result.current.onToggle();
    });
    expect(result.current.isOpen).toBe(false);

    act(() => {
      result.current.setOpen(true);
    });
    expect(result.current.isOpen).toBe(true);

    act(() => {
      result.current.onClose();
    });
    expect(result.current.isOpen).toBe(false);
  });

  it('fires onOpenChange when the state changes', () => {
    const onOpenChange = vi.fn();
    const { result } = renderHook(() => useDisclosure({ onOpenChange }));

    act(() => {
      result.current.onOpen();
      result.current.onOpen();
      result.current.onClose();
    });

    expect(onOpenChange).toHaveBeenCalledTimes(2);
    expect(onOpenChange).toHaveBeenNthCalledWith(1, true);
    expect(onOpenChange).toHaveBeenNthCalledWith(2, false);
  });

  it('supports controlled usage by delegating state updates through onOpenChange', () => {
    const onOpenChange = vi.fn();
    const { result, rerender } = renderHook(
      ({ isOpen }) => useDisclosure({ isOpen, onOpenChange }),
      { initialProps: { isOpen: false } }
    );

    act(() => {
      result.current.onOpen();
    });

    expect(result.current.isOpen).toBe(false);
    expect(onOpenChange).toHaveBeenCalledWith(true);

    rerender({ isOpen: true });
    expect(result.current.isOpen).toBe(true);

    act(() => {
      result.current.onToggle();
    });

    expect(onOpenChange).toHaveBeenCalledWith(false);
  });
});