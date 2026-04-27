/**
 * useKeyboardShortcuts Hook Tests
 */

import { describe, it, expect, vi, afterEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useKeyboardShortcuts } from '../useKeyboardShortcuts';

function fireKeydown(
  key: string,
  mods: { ctrlKey?: boolean; metaKey?: boolean; shiftKey?: boolean; altKey?: boolean } = {}
) {
  const event = new KeyboardEvent('keydown', {
    key,
    bubbles: true,
    ...mods,
  });
  window.dispatchEvent(event);
}

describe('useKeyboardShortcuts', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns a shortcuts array', () => {
    const { result } = renderHook(() => useKeyboardShortcuts({}));
    expect(Array.isArray(result.current.shortcuts)).toBe(true);
  });

  it('shortcuts have key and description properties', () => {
    const { result } = renderHook(() =>
      useKeyboardShortcuts({ onUndo: vi.fn() })
    );
    const shortcut = result.current.shortcuts[0];
    expect(shortcut).toHaveProperty('key');
    expect(shortcut).toHaveProperty('description');
  });

  it('registers keydown listener on mount', () => {
    const addSpy = vi.spyOn(window, 'addEventListener');
    renderHook(() => useKeyboardShortcuts({}));
    expect(addSpy).toHaveBeenCalledWith('keydown', expect.any(Function));
  });

  it('removes keydown listener on unmount', () => {
    const removeSpy = vi.spyOn(window, 'removeEventListener');
    const { unmount } = renderHook(() => useKeyboardShortcuts({}));
    unmount();
    expect(removeSpy).toHaveBeenCalledWith('keydown', expect.any(Function));
  });

  it('calls onUndo when Ctrl+Z is pressed', () => {
    const onUndo = vi.fn();
    renderHook(() => useKeyboardShortcuts({ onUndo }));
    fireKeydown('z', { ctrlKey: true });
    expect(onUndo).toHaveBeenCalledTimes(1);
  });

  it('calls onRedo when Ctrl+Shift+Z is pressed', () => {
    const onRedo = vi.fn();
    renderHook(() => useKeyboardShortcuts({ onRedo }));
    fireKeydown('z', { ctrlKey: true, shiftKey: true });
    expect(onRedo).toHaveBeenCalledTimes(1);
  });

  it('calls onCopy when Ctrl+C is pressed', () => {
    const onCopy = vi.fn();
    renderHook(() => useKeyboardShortcuts({ onCopy }));
    fireKeydown('c', { ctrlKey: true });
    expect(onCopy).toHaveBeenCalledTimes(1);
  });

  it('calls onDelete when Delete key is pressed', () => {
    const onDelete = vi.fn();
    renderHook(() => useKeyboardShortcuts({ onDelete }));
    fireKeydown('Delete');
    expect(onDelete).toHaveBeenCalledTimes(1);
  });

  it('does not fire callbacks when enabled is false', () => {
    const onUndo = vi.fn();
    renderHook(() => useKeyboardShortcuts({ onUndo, enabled: false }));
    fireKeydown('z', { ctrlKey: true });
    expect(onUndo).not.toHaveBeenCalled();
  });

  it('calls onSelectAll when Ctrl+A is pressed', () => {
    const onSelectAll = vi.fn();
    renderHook(() => useKeyboardShortcuts({ onSelectAll }));
    fireKeydown('a', { ctrlKey: true });
    expect(onSelectAll).toHaveBeenCalledTimes(1);
  });
});
