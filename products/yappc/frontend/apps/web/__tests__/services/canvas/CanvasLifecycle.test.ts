/**
 * CanvasLifecycle Tests
 * 
 * @doc.type test
 * @doc.purpose Test lifecycle phase transitions and validation
 * @doc.layer product
 */

import { describe, it, expect, beforeEach } from '@jest/globals';
import { renderHook, act } from '@testing-library/react';
import { CanvasLifecycle, useCanvasLifecycle } from '../../../src/services/canvas/lifecycle/CanvasLifecycle';
import { LifecyclePhase } from '../../../src/types/lifecycle';

describe('CanvasLifecycle', () => {
    let lifecycle: CanvasLifecycle;

    beforeEach(() => {
        lifecycle = new CanvasLifecycle();
    });

    describe('Phase Transitions', () => {
        it('should start in INTENT phase', () => {
            expect(lifecycle.getCurrentPhase()).toBe(LifecyclePhase.INTENT);
        });

        it('should allow valid phase transitions', () => {
            expect(lifecycle.canTransitionTo(LifecyclePhase.SHAPE)).toBe(true);

            act(() => {
                lifecycle.transitionTo(LifecyclePhase.SHAPE, 'test', 'Moving to SHAPE');
            });

            expect(lifecycle.getCurrentPhase()).toBe(LifecyclePhase.SHAPE);
        });

        it('should prevent invalid phase transitions', () => {
            expect(lifecycle.canTransitionTo(LifecyclePhase.GENERATE)).toBe(false);
        });

        it('should track phase history', () => {
            act(() => {
                lifecycle.transitionTo(LifecyclePhase.SHAPE, 'test', 'Step 1');
                lifecycle.transitionTo(LifecyclePhase.VALIDATE, 'test', 'Step 2');
            });

            const history = lifecycle.getPhaseHistory();
            expect(history).toHaveLength(3); // INTENT + SHAPE + VALIDATE
            expect(history[0].phase).toBe(LifecyclePhase.INTENT);
            expect(history[1].phase).toBe(LifecyclePhase.SHAPE);
            expect(history[2].phase).toBe(LifecyclePhase.VALIDATE);
        });

        it('should emit events on phase change', () => {
            const listener = jest.fn();
            lifecycle.on('phaseChanged', listener);

            act(() => {
                lifecycle.transitionTo(LifecyclePhase.SHAPE, 'test', 'Test transition');
            });

            expect(listener).toHaveBeenCalledWith({
                fromPhase: LifecyclePhase.INTENT,
                toPhase: LifecyclePhase.SHAPE,
                trigger: 'test',
                reason: 'Test transition',
            });
        });
    });

    describe('Phase Operations', () => {
        it('should return operations for current phase', () => {
            const operations = lifecycle.getCurrentOperations();
            expect(operations).toContain('sketch');
            expect(operations).toContain('voice_input');
        });

        it('should update operations when phase changes', () => {
            act(() => {
                lifecycle.transitionTo(LifecyclePhase.SHAPE, 'test', 'Moving to SHAPE');
            });

            const operations = lifecycle.getCurrentOperations();
            expect(operations).toContain('add_node');
            expect(operations).toContain('connect_nodes');
            expect(operations).toContain('organize');
        });
    });

    describe('Phase Validation', () => {
        it('should validate INTENT -> SHAPE transition', () => {
            expect(lifecycle.canTransitionTo(LifecyclePhase.SHAPE)).toBe(true);
        });

        it('should validate SHAPE -> VALIDATE transition', () => {
            act(() => {
                lifecycle.transitionTo(LifecyclePhase.SHAPE, 'test', 'Step 1');
            });
            expect(lifecycle.canTransitionTo(LifecyclePhase.VALIDATE)).toBe(true);
        });

        it('should validate VALIDATE -> GENERATE transition', () => {
            act(() => {
                lifecycle.transitionTo(LifecyclePhase.SHAPE, 'test', 'Step 1');
                lifecycle.transitionTo(LifecyclePhase.VALIDATE, 'test', 'Step 2');
            });
            expect(lifecycle.canTransitionTo(LifecyclePhase.GENERATE)).toBe(true);
        });

        it('should allow returning to previous phase', () => {
            act(() => {
                lifecycle.transitionTo(LifecyclePhase.SHAPE, 'test', 'Step 1');
            });
            expect(lifecycle.canTransitionTo(LifecyclePhase.INTENT)).toBe(true);
        });
    });
});

describe('useCanvasLifecycle Hook', () => {
    it('should provide current phase', () => {
        const { result } = renderHook(() => useCanvasLifecycle());
        expect(result.current.currentPhase).toBe(LifecyclePhase.INTENT);
    });

    it('should handle phase transitions', () => {
        const { result } = renderHook(() => useCanvasLifecycle());

        act(() => {
            result.current.transitionToPhase(LifecyclePhase.SHAPE, 'test', 'Moving to SHAPE');
        });

        expect(result.current.currentPhase).toBe(LifecyclePhase.SHAPE);
    });

    it('should provide transition validation', () => {
        const { result } = renderHook(() => useCanvasLifecycle());

        expect(result.current.canTransitionTo(LifecyclePhase.SHAPE)).toBe(true);
        expect(result.current.canTransitionTo(LifecyclePhase.GENERATE)).toBe(false);
    });

    it('should provide current operations', () => {
        const { result } = renderHook(() => useCanvasLifecycle());

        expect(result.current.getCurrentOperations()).toContain('sketch');
        expect(result.current.getCurrentOperations()).toContain('voice_input');
    });

    it('should provide phase history', () => {
        const { result } = renderHook(() => useCanvasLifecycle());

        act(() => {
            result.current.transitionToPhase(LifecyclePhase.SHAPE, 'test', 'Step 1');
            result.current.transitionToPhase(LifecyclePhase.VALIDATE, 'test', 'Step 2');
        });

        const history = result.current.getPhaseHistory();
        expect(history).toHaveLength(3);
    });

    it('should handle multiple subscribers', () => {
        const { result } = renderHook(() => useCanvasLifecycle());
        const listener1 = jest.fn();
        const listener2 = jest.fn();

        act(() => {
            result.current.lifecycle.on('phaseChanged', listener1);
            result.current.lifecycle.on('phaseChanged', listener2);
            result.current.transitionToPhase(LifecyclePhase.SHAPE, 'test', 'Test');
        });

        expect(listener1).toHaveBeenCalled();
        expect(listener2).toHaveBeenCalled();
    });
});
