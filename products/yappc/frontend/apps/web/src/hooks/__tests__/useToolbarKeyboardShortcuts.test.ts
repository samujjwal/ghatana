/**
 * useToolbarKeyboardShortcuts Hook Tests
 * 
 * Tests for the unified toolbar keyboard shortcuts hook including:
 * - Mode shortcuts (1-7 keys)
 * - Level shortcuts (Alt+Up/Down)
 * - Undo/Redo shortcuts (Cmd+Z, Cmd+Shift+Z)
 * - Reset to default level (Home key)
 * - Disabled state
 * 
 * @doc.type test
 * @doc.purpose useToolbarKeyboardShortcuts unit tests
 * @doc.layer product
 */

import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { Provider } from 'jotai';
import { useHydrateAtoms } from 'jotai/utils';
import type { ReactNode } from 'react';

import { useToolbarKeyboardShortcuts, TOOLBAR_SHORTCUTS, getShortcutDisplay } from '../useToolbarKeyboardShortcuts';
import { canvasModeAtom, abstractionLevelAtom } from '../../state/atoms/toolbarAtom';
import type { CanvasMode } from '../../types/canvasMode';
import type { AbstractionLevel } from '../../types/abstractionLevel';

// Helper to create wrapper with initial atom values
function createWrapper(initialMode: CanvasMode = 'diagram', initialLevel: AbstractionLevel = 'component') {
    function HydrateAtoms({ children }: { children: ReactNode }) {
        useHydrateAtoms([
            [canvasModeAtom, initialMode],
            [abstractionLevelAtom, initialLevel],
        ]);
        return <>{children}</>;
    }

    return function Wrapper({ children }: { children: ReactNode }) {
        return (
            <Provider>
                <HydrateAtoms>{children}</HydrateAtoms>
            </Provider>
        );
    };
}

// Helper to simulate keyboard events
function fireKeyboardEvent(key: string, options: Partial<KeyboardEvent> = {}) {
    const event = new KeyboardEvent('keydown', {
        key,
        bubbles: true,
        cancelable: true,
        ...options,
    });
    window.dispatchEvent(event);
    return event;
}

describe('useToolbarKeyboardShortcuts', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('Initial State', () => {
        it('returns current mode from atom', () => {
            const { result } = renderHook(() => useToolbarKeyboardShortcuts(), {
                wrapper: createWrapper('code', 'file'),
            });

            expect(result.current.mode).toBe('code');
        });

        it('returns current level from atom', () => {
            const { result } = renderHook(() => useToolbarKeyboardShortcuts(), {
                wrapper: createWrapper('diagram', 'file'),
            });

            expect(result.current.level).toBe('file');
        });

        it('returns canZoomOut based on current level', () => {
            const { result } = renderHook(() => useToolbarKeyboardShortcuts(), {
                wrapper: createWrapper('diagram', 'component'),
            });

            expect(result.current.canZoomOut).toBe(true);
        });

        it('returns canZoomOut as false at system level', () => {
            const { result } = renderHook(() => useToolbarKeyboardShortcuts(), {
                wrapper: createWrapper('diagram', 'system'),
            });

            expect(result.current.canZoomOut).toBe(false);
        });

        it('returns canDrillDown based on current level', () => {
            const { result } = renderHook(() => useToolbarKeyboardShortcuts(), {
                wrapper: createWrapper('diagram', 'component'),
            });

            expect(result.current.canDrillDown).toBe(true);
        });

        it('returns canDrillDown as false at code level', () => {
            const { result } = renderHook(() => useToolbarKeyboardShortcuts(), {
                wrapper: createWrapper('diagram', 'code'),
            });

            expect(result.current.canDrillDown).toBe(false);
        });
    });

    describe('Mode Shortcuts (1-7 keys)', () => {
        it('changes mode to brainstorm on key 1', () => {
            const onModeChange = vi.fn();
            renderHook(() => useToolbarKeyboardShortcuts({ onModeChange }), {
                wrapper: createWrapper('diagram', 'component'),
            });

            act(() => {
                fireKeyboardEvent('1');
            });

            expect(onModeChange).toHaveBeenCalledWith('brainstorm');
        });

        it('changes mode to diagram on key 2', () => {
            const onModeChange = vi.fn();
            renderHook(() => useToolbarKeyboardShortcuts({ onModeChange }), {
                wrapper: createWrapper('brainstorm', 'component'),
            });

            act(() => {
                fireKeyboardEvent('2');
            });

            expect(onModeChange).toHaveBeenCalledWith('diagram');
        });

        it('changes mode to design on key 3', () => {
            const onModeChange = vi.fn();
            renderHook(() => useToolbarKeyboardShortcuts({ onModeChange }), {
                wrapper: createWrapper('diagram', 'component'),
            });

            act(() => {
                fireKeyboardEvent('3');
            });

            expect(onModeChange).toHaveBeenCalledWith('design');
        });

        it('changes mode to code on key 4', () => {
            const onModeChange = vi.fn();
            renderHook(() => useToolbarKeyboardShortcuts({ onModeChange }), {
                wrapper: createWrapper('diagram', 'component'),
            });

            act(() => {
                fireKeyboardEvent('4');
            });

            expect(onModeChange).toHaveBeenCalledWith('code');
        });

        it('changes mode to test on key 5', () => {
            const onModeChange = vi.fn();
            renderHook(() => useToolbarKeyboardShortcuts({ onModeChange }), {
                wrapper: createWrapper('diagram', 'component'),
            });

            act(() => {
                fireKeyboardEvent('5');
            });

            expect(onModeChange).toHaveBeenCalledWith('test');
        });

        it('changes mode to deploy on key 6', () => {
            const onModeChange = vi.fn();
            renderHook(() => useToolbarKeyboardShortcuts({ onModeChange }), {
                wrapper: createWrapper('diagram', 'component'),
            });

            act(() => {
                fireKeyboardEvent('6');
            });

            expect(onModeChange).toHaveBeenCalledWith('deploy');
        });

        it('changes mode to observe on key 7', () => {
            const onModeChange = vi.fn();
            renderHook(() => useToolbarKeyboardShortcuts({ onModeChange }), {
                wrapper: createWrapper('diagram', 'component'),
            });

            act(() => {
                fireKeyboardEvent('7');
            });

            expect(onModeChange).toHaveBeenCalledWith('observe');
        });

        it('does not trigger mode change when enableModeShortcuts is false', () => {
            const onModeChange = vi.fn();
            renderHook(() => useToolbarKeyboardShortcuts({ 
                onModeChange,
                enableModeShortcuts: false,
            }), {
                wrapper: createWrapper('diagram', 'component'),
            });

            act(() => {
                fireKeyboardEvent('1');
            });

            expect(onModeChange).not.toHaveBeenCalled();
        });

        it('does not trigger mode change with modifier keys', () => {
            const onModeChange = vi.fn();
            renderHook(() => useToolbarKeyboardShortcuts({ onModeChange }), {
                wrapper: createWrapper('diagram', 'component'),
            });

            act(() => {
                fireKeyboardEvent('1', { altKey: true });
            });

            expect(onModeChange).not.toHaveBeenCalled();
        });
    });

    describe('Level Shortcuts (Alt+Up/Down)', () => {
        it('zooms out on Alt+ArrowUp', () => {
            const onLevelChange = vi.fn();
            renderHook(() => useToolbarKeyboardShortcuts({ onLevelChange }), {
                wrapper: createWrapper('diagram', 'component'),
            });

            act(() => {
                fireKeyboardEvent('ArrowUp', { altKey: true });
            });

            expect(onLevelChange).toHaveBeenCalledWith('system');
        });

        it('drills down on Alt+ArrowDown', () => {
            const onLevelChange = vi.fn();
            renderHook(() => useToolbarKeyboardShortcuts({ onLevelChange }), {
                wrapper: createWrapper('diagram', 'component'),
            });

            act(() => {
                fireKeyboardEvent('ArrowDown', { altKey: true });
            });

            expect(onLevelChange).toHaveBeenCalledWith('file');
        });

        it('does not zoom out at system level', () => {
            const onLevelChange = vi.fn();
            renderHook(() => useToolbarKeyboardShortcuts({ onLevelChange }), {
                wrapper: createWrapper('diagram', 'system'),
            });

            act(() => {
                fireKeyboardEvent('ArrowUp', { altKey: true });
            });

            expect(onLevelChange).not.toHaveBeenCalled();
        });

        it('does not drill down at code level', () => {
            const onLevelChange = vi.fn();
            renderHook(() => useToolbarKeyboardShortcuts({ onLevelChange }), {
                wrapper: createWrapper('diagram', 'code'),
            });

            act(() => {
                fireKeyboardEvent('ArrowDown', { altKey: true });
            });

            expect(onLevelChange).not.toHaveBeenCalled();
        });

        it('does not trigger level change when enableLevelShortcuts is false', () => {
            const onLevelChange = vi.fn();
            renderHook(() => useToolbarKeyboardShortcuts({ 
                onLevelChange,
                enableLevelShortcuts: false,
            }), {
                wrapper: createWrapper('diagram', 'component'),
            });

            act(() => {
                fireKeyboardEvent('ArrowUp', { altKey: true });
            });

            expect(onLevelChange).not.toHaveBeenCalled();
        });

        it('resets to component level on Home key', () => {
            const onLevelChange = vi.fn();
            renderHook(() => useToolbarKeyboardShortcuts({ onLevelChange }), {
                wrapper: createWrapper('diagram', 'code'),
            });

            act(() => {
                fireKeyboardEvent('Home');
            });

            expect(onLevelChange).toHaveBeenCalledWith('component');
        });
    });

    describe('History Shortcuts (Cmd+Z, Cmd+Shift+Z)', () => {
        it('calls onUndo on Cmd+Z', () => {
            const onUndo = vi.fn();
            renderHook(() => useToolbarKeyboardShortcuts({ onUndo }), {
                wrapper: createWrapper('diagram', 'component'),
            });

            act(() => {
                fireKeyboardEvent('z', { metaKey: true });
            });

            expect(onUndo).toHaveBeenCalledTimes(1);
        });

        it('calls onRedo on Cmd+Shift+Z', () => {
            const onRedo = vi.fn();
            renderHook(() => useToolbarKeyboardShortcuts({ onRedo }), {
                wrapper: createWrapper('diagram', 'component'),
            });

            act(() => {
                fireKeyboardEvent('z', { metaKey: true, shiftKey: true });
            });

            expect(onRedo).toHaveBeenCalledTimes(1);
        });

        it('calls onUndo on Ctrl+Z (Windows)', () => {
            const onUndo = vi.fn();
            renderHook(() => useToolbarKeyboardShortcuts({ onUndo }), {
                wrapper: createWrapper('diagram', 'component'),
            });

            act(() => {
                fireKeyboardEvent('z', { ctrlKey: true });
            });

            expect(onUndo).toHaveBeenCalledTimes(1);
        });

        it('does not trigger history shortcuts when enableHistoryShortcuts is false', () => {
            const onUndo = vi.fn();
            const onRedo = vi.fn();
            renderHook(() => useToolbarKeyboardShortcuts({ 
                onUndo,
                onRedo,
                enableHistoryShortcuts: false,
            }), {
                wrapper: createWrapper('diagram', 'component'),
            });

            act(() => {
                fireKeyboardEvent('z', { metaKey: true });
            });

            expect(onUndo).not.toHaveBeenCalled();
        });
    });

    describe('Disabled State', () => {
        it('does not trigger any shortcuts when disabled', () => {
            const onModeChange = vi.fn();
            const onLevelChange = vi.fn();
            const onUndo = vi.fn();
            
            renderHook(() => useToolbarKeyboardShortcuts({ 
                onModeChange,
                onLevelChange,
                onUndo,
                disabled: true,
            }), {
                wrapper: createWrapper('diagram', 'component'),
            });

            act(() => {
                fireKeyboardEvent('1');
                fireKeyboardEvent('ArrowUp', { altKey: true });
                fireKeyboardEvent('z', { metaKey: true });
            });

            expect(onModeChange).not.toHaveBeenCalled();
            expect(onLevelChange).not.toHaveBeenCalled();
            expect(onUndo).not.toHaveBeenCalled();
        });
    });

    describe('Programmatic Actions', () => {
        it('setMode updates mode and calls callback', () => {
            const onModeChange = vi.fn();
            const { result } = renderHook(() => useToolbarKeyboardShortcuts({ onModeChange }), {
                wrapper: createWrapper('diagram', 'component'),
            });

            act(() => {
                result.current.setMode('code');
            });

            expect(onModeChange).toHaveBeenCalledWith('code');
        });

        it('setLevel updates level and calls callback', () => {
            const onLevelChange = vi.fn();
            const { result } = renderHook(() => useToolbarKeyboardShortcuts({ onLevelChange }), {
                wrapper: createWrapper('diagram', 'component'),
            });

            act(() => {
                result.current.setLevel('file');
            });

            expect(onLevelChange).toHaveBeenCalledWith('file');
        });

        it('zoomOut navigates to previous level', () => {
            const onLevelChange = vi.fn();
            const { result } = renderHook(() => useToolbarKeyboardShortcuts({ onLevelChange }), {
                wrapper: createWrapper('diagram', 'component'),
            });

            act(() => {
                result.current.zoomOut();
            });

            expect(onLevelChange).toHaveBeenCalledWith('system');
        });

        it('drillDown navigates to next level', () => {
            const onLevelChange = vi.fn();
            const { result } = renderHook(() => useToolbarKeyboardShortcuts({ onLevelChange }), {
                wrapper: createWrapper('diagram', 'component'),
            });

            act(() => {
                result.current.drillDown();
            });

            expect(onLevelChange).toHaveBeenCalledWith('file');
        });

        it('resetLevel resets to component level', () => {
            const onLevelChange = vi.fn();
            const { result } = renderHook(() => useToolbarKeyboardShortcuts({ onLevelChange }), {
                wrapper: createWrapper('diagram', 'code'),
            });

            act(() => {
                result.current.resetLevel();
            });

            expect(onLevelChange).toHaveBeenCalledWith('component');
        });
    });
});

describe('getShortcutDisplay', () => {
    it('converts Cmd to ⌘ on Mac', () => {
        // Mock Mac platform
        Object.defineProperty(navigator, 'platform', {
            value: 'MacIntel',
            configurable: true,
        });

        expect(getShortcutDisplay('Cmd+Z')).toBe('⌘+Z');
    });

    it('converts arrow keys to symbols', () => {
        expect(getShortcutDisplay('ArrowUp')).toBe('↑');
        expect(getShortcutDisplay('ArrowDown')).toBe('↓');
        expect(getShortcutDisplay('ArrowLeft')).toBe('←');
        expect(getShortcutDisplay('ArrowRight')).toBe('→');
    });
});

describe('TOOLBAR_SHORTCUTS', () => {
    it('contains all mode shortcuts', () => {
        const modeShortcuts = TOOLBAR_SHORTCUTS.filter(s => s.category === 'Mode');
        expect(modeShortcuts).toHaveLength(7);
    });

    it('contains level shortcuts', () => {
        const levelShortcuts = TOOLBAR_SHORTCUTS.filter(s => s.category === 'Level');
        expect(levelShortcuts.length).toBeGreaterThanOrEqual(2);
    });

    it('contains history shortcuts', () => {
        const historyShortcuts = TOOLBAR_SHORTCUTS.filter(s => s.category === 'History');
        expect(historyShortcuts.length).toBeGreaterThanOrEqual(2);
    });
});
