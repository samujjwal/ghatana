import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { useFocusRing } from '../useFocusRing';

describe('useFocusRing', () => {
  it('tracks focused and focus-visible state when the target matches :focus-visible', () => {
    const { result } = renderHook(() => useFocusRing<HTMLInputElement>());

    act(() => {
      result.current.focusProps.onFocus({
        target: {
          matches: vi.fn().mockReturnValue(true),
        },
      } as unknown as React.FocusEvent<HTMLInputElement>);
    });

    expect(result.current.isFocused).toBe(true);
    expect(result.current.isFocusVisible).toBe(true);
  });

  it('clears focused state and focus-visible state on blur', () => {
    const { result } = renderHook(() => useFocusRing<HTMLInputElement>());

    act(() => {
      result.current.focusProps.onFocus({
        target: {
          matches: vi.fn().mockReturnValue(true),
        },
      } as unknown as React.FocusEvent<HTMLInputElement>);
      result.current.focusProps.onBlur({} as React.FocusEvent<HTMLInputElement>);
    });

    expect(result.current.isFocused).toBe(false);
    expect(result.current.isFocusVisible).toBe(false);
  });

  it('falls back to visible focus when matches is unavailable', () => {
    const { result } = renderHook(() => useFocusRing<HTMLButtonElement>());

    act(() => {
      result.current.focusProps.onFocus({
        target: {},
      } as unknown as React.FocusEvent<HTMLButtonElement>);
    });

    expect(result.current.isFocused).toBe(true);
    expect(result.current.isFocusVisible).toBe(true);
  });

  it('composes external focus and blur handlers', () => {
    const onFocus = vi.fn();
    const onBlur = vi.fn();
    const { result } = renderHook(() =>
      useFocusRing<HTMLInputElement>({ onFocus, onBlur })
    );

    const focusEvent = {
      target: {
        matches: vi.fn().mockReturnValue(false),
      },
    } as unknown as React.FocusEvent<HTMLInputElement>;
    const blurEvent = {} as React.FocusEvent<HTMLInputElement>;

    act(() => {
      result.current.focusProps.onFocus(focusEvent);
      result.current.focusProps.onBlur(blurEvent);
    });

    expect(onFocus).toHaveBeenCalledWith(focusEvent);
    expect(onBlur).toHaveBeenCalledWith(blurEvent);
  });
});