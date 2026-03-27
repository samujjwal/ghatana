import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { useControllableState } from '../useControllableState';

describe('useControllableState', () => {
  it('uses the default value in uncontrolled mode', () => {
    const { result } = renderHook(() =>
      useControllableState<string>({ defaultValue: 'initial' })
    );

    expect(result.current[0]).toBe('initial');
  });

  it('supports lazy default values in uncontrolled mode', () => {
    const { result } = renderHook(() =>
      useControllableState<number>({ defaultValue: () => 42 })
    );

    expect(result.current[0]).toBe(42);
  });

  it('updates internal state and fires onChange in uncontrolled mode', () => {
    const onChange = vi.fn();
    const { result } = renderHook(() =>
      useControllableState<number>({ defaultValue: 1, onChange })
    );

    act(() => {
      result.current[1](2);
    });

    expect(result.current[0]).toBe(2);
    expect(onChange).toHaveBeenCalledWith(2);
  });

  it('supports updater functions based on the previous value', () => {
    const { result } = renderHook(() =>
      useControllableState<number>({ defaultValue: 3 })
    );

    act(() => {
      result.current[1]((prev) => prev + 4);
    });

    expect(result.current[0]).toBe(7);
  });

  it('treats value as controlled and delegates updates through onChange', () => {
    const onChange = vi.fn();
    const { result, rerender } = renderHook(
      ({ value }) => useControllableState<number>({ value, onChange }),
      { initialProps: { value: 5 } }
    );

    act(() => {
      result.current[1](8);
    });

    expect(result.current[0]).toBe(5);
    expect(onChange).toHaveBeenCalledWith(8);

    rerender({ value: 8 });
    expect(result.current[0]).toBe(8);
  });

  it('does not fire onChange when the resolved value does not change', () => {
    const onChange = vi.fn();
    const { result } = renderHook(() =>
      useControllableState<string>({ defaultValue: 'same', onChange })
    );

    act(() => {
      result.current[1]('same');
    });

    expect(onChange).not.toHaveBeenCalled();
  });
});