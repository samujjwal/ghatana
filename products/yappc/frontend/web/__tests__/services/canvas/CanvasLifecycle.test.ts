/**
 * CanvasLifecycle Tests
 * 
 * @doc.type test
 * @doc.purpose Test lifecycle phase transitions and validation
 * @doc.layer product
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
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
                lifecycle.transitionToPhase(LifecyclePhase.SHAPE, 'user', 'Moving to SHAPE');
            });

            expect(lifecycle.getCurrentPhase()).toBe(LifecyclePhase.SHAPE);
        });

        it('should prevent invalid phase transitions', () => {
            expect(lifecycle.canTransitionTo(LifecyclePhase.GENERATE)).toBe(false);
        });

        it('should track phase history', () => {
            act(() => {
                lifecycle.transitionToPhase(LifecyclePhase.SHAPE, 'user', 'Step 1');
                lifecycle.transitionToPhase(LifecyclePhase.VALIDATE, 'user', 'Step 2');
            });

            const history = lifecycle.getPhaseHistory();
            // Initial seed + INTENT→SHAPE + SHAPE→VALIDATE
            expect(history).toHaveLength(3);
            expect(history[0].toPhase).toBe(LifecyclePhase.INTENT);
            expect(history[1].toPhase).toBe(LifecyclePhase.SHAPE);
            expect(history[2].toPhase).toBe(LifecyclePhase.VALIDATE);
        });

        it('should emit events on phase change', () => {
            const listener = vi.fn();
            lifecycle.addListener(listener);

            act(() => {
                lifecycle.transitionToPhase(LifecyclePhase.SHAPE, 'user', 'Test transition');
            });

            expect(listener).toHaveBeenCalledWith(
                expect.objectContaining({
                    phase: LifecyclePhase.SHAPE,
                    previousPhase: LifecyclePhase.INTENT,
                    triggeredBy: 'user',
                }),
            );
        });
    });

    describe('Phase Operations', () => {
        it('should return operations for current phase', () => {
            const operations = lifecycle.getAllowedOperations();
            expect(operations.canEdit).toBe(true);
            expect(operations.aiActive).toBe(true);
        });

        it('should update operations when phase changes', () => {
            act(() => {
                lifecycle.transitionToPhase(LifecyclePhase.SHAPE, 'user', 'Moving to SHAPE');
            });

            const operations = lifecycle.getAllowedOperations();
            expect(operations.canEdit).toBe(true);
            expect(operations.canValidate).toBe(true);
            expect(operations.aiActive).toBe(true);
        });
    });

    describe('Phase Validation', () => {
        it('should validate INTENT -> SHAPE transition', () => {
            expect(lifecycle.canTransitionTo(LifecyclePhase.SHAPE)).toBe(true);
        });

        it('should validate SHAPE -> VALIDATE transition', () => {
            act(() => {
                lifecycle.transitionToPhase(LifecyclePhase.SHAPE, 'user', 'Step 1');
            });
            expect(lifecycle.canTransitionTo(LifecyclePhase.VALIDATE)).toBe(true);
        });

        it('should validate VALIDATE -> GENERATE transition', () => {
            act(() => {
                lifecycle.transitionToPhase(LifecyclePhase.SHAPE, 'user', 'Step 1');
                lifecycle.transitionToPhase(LifecyclePhase.VALIDATE, 'user', 'Step 2');
            });
            expect(lifecycle.canTransitionTo(LifecyclePhase.GENERATE)).toBe(true);
        });

        it('should allow returning to previous phase', () => {
            act(() => {
                lifecycle.transitionToPhase(LifecyclePhase.SHAPE, 'user', 'Step 1');
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
            result.current.transitionToPhase(LifecyclePhase.SHAPE, 'user', 'Moving to SHAPE');
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

        const ops = result.current.getAllowedOperations();
        expect(ops.canEdit).toBe(true);
        expect(ops.aiActive).toBe(true);
    });

    it('should provide phase history', () => {
        const { result } = renderHook(() => useCanvasLifecycle());

        act(() => {
            result.current.transitionToPhase(LifecyclePhase.SHAPE, 'user', 'Step 1');
            result.current.transitionToPhase(LifecyclePhase.VALIDATE, 'user', 'Step 2');
        });

        const history = result.current.getPhaseHistory();
        expect(history).toHaveLength(3);
    });

    it('should handle multiple subscribers', () => {
        const { result } = renderHook(() => useCanvasLifecycle());
        const listener1 = vi.fn();
        const listener2 = vi.fn();

        act(() => {
            result.current.lifecycle.addListener(listener1);
            result.current.lifecycle.addListener(listener2);
            result.current.transitionToPhase(LifecyclePhase.SHAPE, 'user', 'Test');
        });

        expect(listener1).toHaveBeenCalled();
        expect(listener2).toHaveBeenCalled();
    });
});
