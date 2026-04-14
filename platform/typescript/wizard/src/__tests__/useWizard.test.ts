/**
 * @group unit
 * @tier U
 *
 * Tests for @ghatana/wizard — useWizard hook.
 */
import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';

import { useWizard, type WizardStep } from '../hooks/useWizard';

const makeSteps = (count: number): WizardStep[] =>
  Array.from({ length: count }, (_, i) => ({
    id: `step-${i}`,
    title: `Step ${i + 1}`,
  }));

describe('useWizard', () => {
  describe('initial state', () => {
    it('starts at step index 0 by default', () => {
      const { result } = renderHook(() => useWizard(makeSteps(3)));
      expect(result.current.currentStepIndex).toBe(0);
    });

    it('respects a custom initialStep', () => {
      const { result } = renderHook(() => useWizard(makeSteps(3), 2));
      expect(result.current.currentStepIndex).toBe(2);
    });

    it('reports totalSteps correctly', () => {
      const { result } = renderHook(() => useWizard(makeSteps(5)));
      expect(result.current.totalSteps).toBe(5);
    });

    it('exposes the current step object', () => {
      const steps = makeSteps(2);
      const { result } = renderHook(() => useWizard(steps));
      expect(result.current.currentStep).toBe(steps[0]);
    });

    it('marks isFirstStep = true at index 0', () => {
      const { result } = renderHook(() => useWizard(makeSteps(3)));
      expect(result.current.isFirstStep).toBe(true);
    });

    it('marks isLastStep = false at index 0 with multiple steps', () => {
      const { result } = renderHook(() => useWizard(makeSteps(3)));
      expect(result.current.isLastStep).toBe(false);
    });

    it('marks isLastStep = true when starting at the last step', () => {
      const { result } = renderHook(() => useWizard(makeSteps(3), 2));
      expect(result.current.isLastStep).toBe(true);
    });

    it('starts with an empty completedSteps set', () => {
      const { result } = renderHook(() => useWizard(makeSteps(3)));
      expect(result.current.completedSteps.size).toBe(0);
    });
  });

  describe('goToNext', () => {
    it('advances to the next step', () => {
      const { result } = renderHook(() => useWizard(makeSteps(3)));
      act(() => result.current.goToNext());
      expect(result.current.currentStepIndex).toBe(1);
    });

    it('marks the previous step as completed when advancing', () => {
      const steps = makeSteps(3);
      const { result } = renderHook(() => useWizard(steps));
      act(() => result.current.goToNext());
      expect(result.current.completedSteps.has(steps[0].id)).toBe(true);
    });

    it('does not advance beyond the last step', () => {
      const { result } = renderHook(() => useWizard(makeSteps(3), 2));
      act(() => result.current.goToNext());
      expect(result.current.currentStepIndex).toBe(2);
    });

    it('updates isFirstStep and isLastStep as steps advance', () => {
      const { result } = renderHook(() => useWizard(makeSteps(3)));
      act(() => result.current.goToNext());
      expect(result.current.isFirstStep).toBe(false);
      expect(result.current.isLastStep).toBe(false);
      act(() => result.current.goToNext());
      expect(result.current.isLastStep).toBe(true);
    });
  });

  describe('goToPrev', () => {
    it('retreats to the previous step', () => {
      const { result } = renderHook(() => useWizard(makeSteps(3), 2));
      act(() => result.current.goToPrev());
      expect(result.current.currentStepIndex).toBe(1);
    });

    it('does not retreat before the first step', () => {
      const { result } = renderHook(() => useWizard(makeSteps(3)));
      act(() => result.current.goToPrev());
      expect(result.current.currentStepIndex).toBe(0);
    });
  });

  describe('goToStep', () => {
    it('navigates directly to a valid step index', () => {
      const { result } = renderHook(() => useWizard(makeSteps(5)));
      act(() => result.current.goToStep(3));
      expect(result.current.currentStepIndex).toBe(3);
    });

    it('ignores a negative index', () => {
      const { result } = renderHook(() => useWizard(makeSteps(3)));
      act(() => result.current.goToStep(-1));
      expect(result.current.currentStepIndex).toBe(0);
    });

    it('ignores an index beyond the last step', () => {
      const { result } = renderHook(() => useWizard(makeSteps(3), 1));
      act(() => result.current.goToStep(10));
      expect(result.current.currentStepIndex).toBe(1);
    });
  });

  describe('markComplete', () => {
    it('adds the given step id to completedSteps', () => {
      const steps = makeSteps(3);
      const { result } = renderHook(() => useWizard(steps));
      act(() => result.current.markComplete(steps[2].id));
      expect(result.current.completedSteps.has(steps[2].id)).toBe(true);
    });

    it('accumulates multiple completed steps', () => {
      const steps = makeSteps(3);
      const { result } = renderHook(() => useWizard(steps));
      act(() => {
        result.current.markComplete(steps[0].id);
        result.current.markComplete(steps[1].id);
      });
      expect(result.current.completedSteps.size).toBe(2);
    });
  });

  describe('reset', () => {
    it('returns to the initial step index', () => {
      const { result } = renderHook(() => useWizard(makeSteps(3)));
      act(() => result.current.goToNext());
      act(() => result.current.goToNext());
      act(() => result.current.reset());
      expect(result.current.currentStepIndex).toBe(0);
    });

    it('clears completedSteps', () => {
      const steps = makeSteps(3);
      const { result } = renderHook(() => useWizard(steps));
      act(() => result.current.goToNext());
      act(() => result.current.reset());
      expect(result.current.completedSteps.size).toBe(0);
    });
  });

  describe('edge cases', () => {
    it('handles a single-step wizard (both isFirstStep and isLastStep = true)', () => {
      const { result } = renderHook(() => useWizard(makeSteps(1)));
      expect(result.current.isFirstStep).toBe(true);
      expect(result.current.isLastStep).toBe(true);
    });

    it('handles optional isValid callback on step', () => {
      const steps: WizardStep[] = [
        { id: 's0', title: 'A', isValid: () => false },
        { id: 's1', title: 'B' },
      ];
      const { result } = renderHook(() => useWizard(steps));
      // Hook exposes isValid on the currentStep; consumers decide whether to gate
      expect(result.current.currentStep.isValid?.()).toBe(false);
    });
  });
});
