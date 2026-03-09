/**
 * usePhaseNavigation Hook Tests
 * 
 * Tests for phase navigation utilities.
 * 
 * @doc.type test
 * @doc.purpose Test phase navigation functionality
 * @doc.layer product
 */

import { renderHook, act } from '@testing-library/react';
import { Provider, createStore } from 'jotai';
import { useHydrateAtoms } from 'jotai/utils';
import React from 'react';
import {
    usePhaseNavigation,
    getPhaseFromX,
    PHASE_ZONE_POSITIONS,
    PHASE_ORDER
} from '../usePhaseNavigation';
import { LifecyclePhase } from '@/types/lifecycle';

// Wrapper component for Jotai provider with hydrated atoms
const HydrateAtoms: React.FC<{ initialValues: Array<[any, any]>; children: React.ReactNode }> = ({
    initialValues,
    children,
}) => {
    useHydrateAtoms(initialValues);
    return <>{ children } </>;
};

const createWrapper = (initialValues: Array<[any, any]> = []) => {
    const store = createStore();

    return ({ children }: { children: React.ReactNode }) => (
        <Provider store= { store } >
        <HydrateAtoms initialValues={ initialValues }>
            { children }
            </HydrateAtoms>
            </Provider>
    );
};

describe('getPhaseFromX', () => {
    it('returns INTENT phase for positions near the start', () => {
        expect(getPhaseFromX(0)).toBe(LifecyclePhase.INTENT);
        expect(getPhaseFromX(100)).toBe(LifecyclePhase.INTENT);
        expect(getPhaseFromX(399)).toBe(LifecyclePhase.INTENT);
    });

    it('returns SHAPE phase for positions in the shape zone', () => {
        expect(getPhaseFromX(800)).toBe(LifecyclePhase.SHAPE);
        expect(getPhaseFromX(1000)).toBe(LifecyclePhase.SHAPE);
    });

    it('returns VALIDATE phase for positions in the validate zone', () => {
        expect(getPhaseFromX(1700)).toBe(LifecyclePhase.VALIDATE);
        expect(getPhaseFromX(2000)).toBe(LifecyclePhase.VALIDATE);
    });

    it('returns GENERATE phase for positions in the generate zone', () => {
        expect(getPhaseFromX(2700)).toBe(LifecyclePhase.GENERATE);
        expect(getPhaseFromX(3000)).toBe(LifecyclePhase.GENERATE);
    });

    it('returns RUN phase for positions in the run zone', () => {
        expect(getPhaseFromX(3500)).toBe(LifecyclePhase.RUN);
        expect(getPhaseFromX(4000)).toBe(LifecyclePhase.RUN);
    });

    it('returns OBSERVE phase for positions in the observe zone', () => {
        expect(getPhaseFromX(4200)).toBe(LifecyclePhase.OBSERVE);
        expect(getPhaseFromX(4500)).toBe(LifecyclePhase.OBSERVE);
    });

    it('returns IMPROVE phase for positions at the end', () => {
        expect(getPhaseFromX(4900)).toBe(LifecyclePhase.IMPROVE);
        expect(getPhaseFromX(5500)).toBe(LifecyclePhase.IMPROVE);
        expect(getPhaseFromX(10000)).toBe(LifecyclePhase.IMPROVE);
    });
});

describe('PHASE_ZONE_POSITIONS', () => {
    it('has all seven phases defined', () => {
        expect(Object.keys(PHASE_ZONE_POSITIONS)).toHaveLength(7);
        expect(PHASE_ZONE_POSITIONS[LifecyclePhase.INTENT]).toBeDefined();
        expect(PHASE_ZONE_POSITIONS[LifecyclePhase.SHAPE]).toBeDefined();
        expect(PHASE_ZONE_POSITIONS[LifecyclePhase.VALIDATE]).toBeDefined();
        expect(PHASE_ZONE_POSITIONS[LifecyclePhase.GENERATE]).toBeDefined();
        expect(PHASE_ZONE_POSITIONS[LifecyclePhase.RUN]).toBeDefined();
        expect(PHASE_ZONE_POSITIONS[LifecyclePhase.OBSERVE]).toBeDefined();
        expect(PHASE_ZONE_POSITIONS[LifecyclePhase.IMPROVE]).toBeDefined();
    });

    it('has increasing X positions', () => {
        let lastX = -1;
        for (const phase of PHASE_ORDER) {
            const currentX = PHASE_ZONE_POSITIONS[phase];
            expect(currentX).toBeGreaterThan(lastX);
            lastX = currentX;
        }
    });
});

describe('PHASE_ORDER', () => {
    it('has all seven phases in correct order', () => {
        expect(PHASE_ORDER).toHaveLength(7);
        expect(PHASE_ORDER[0]).toBe(LifecyclePhase.INTENT);
        expect(PHASE_ORDER[1]).toBe(LifecyclePhase.SHAPE);
        expect(PHASE_ORDER[2]).toBe(LifecyclePhase.VALIDATE);
        expect(PHASE_ORDER[3]).toBe(LifecyclePhase.GENERATE);
        expect(PHASE_ORDER[4]).toBe(LifecyclePhase.RUN);
        expect(PHASE_ORDER[5]).toBe(LifecyclePhase.OBSERVE);
        expect(PHASE_ORDER[6]).toBe(LifecyclePhase.IMPROVE);
    });
});

describe('usePhaseNavigation', () => {
    it('returns current phase based on viewport', () => {
        const wrapper = createWrapper();
        const { result } = renderHook(() => usePhaseNavigation(), { wrapper });

        expect(result.current.currentPhase).toBeDefined();
        expect(PHASE_ORDER).toContain(result.current.currentPhase);
    });

    it('returns currentPhaseIndex', () => {
        const wrapper = createWrapper();
        const { result } = renderHook(() => usePhaseNavigation(), { wrapper });

        expect(result.current.currentPhaseIndex).toBeGreaterThanOrEqual(0);
        expect(result.current.currentPhaseIndex).toBeLessThan(7);
    });

    it('canNavigatePrevious returns false for first phase', () => {
        const wrapper = createWrapper();
        const { result } = renderHook(() => usePhaseNavigation(), { wrapper });

        // If at INTENT phase, should not be able to navigate previous
        if (result.current.currentPhase === LifecyclePhase.INTENT) {
            expect(result.current.canNavigatePrevious).toBe(false);
        }
    });

    it('canNavigateNext returns false for last phase', () => {
        const wrapper = createWrapper();
        const { result } = renderHook(() => usePhaseNavigation(), { wrapper });

        // If at IMPROVE phase, should not be able to navigate next
        if (result.current.currentPhase === LifecyclePhase.IMPROVE) {
            expect(result.current.canNavigateNext).toBe(false);
        }
    });

    it('provides navigation functions', () => {
        const wrapper = createWrapper();
        const { result } = renderHook(() => usePhaseNavigation(), { wrapper });

        expect(typeof result.current.navigateToPhase).toBe('function');
        expect(typeof result.current.navigatePrevious).toBe('function');
        expect(typeof result.current.navigateNext).toBe('function');
    });

    it('getPhasePosition returns correct X coordinates', () => {
        const wrapper = createWrapper();
        const { result } = renderHook(() => usePhaseNavigation(), { wrapper });

        expect(result.current.getPhasePosition(LifecyclePhase.INTENT)).toBe(PHASE_ZONE_POSITIONS[LifecyclePhase.INTENT]);
        expect(result.current.getPhasePosition(LifecyclePhase.SHAPE)).toBe(PHASE_ZONE_POSITIONS[LifecyclePhase.SHAPE]);
        expect(result.current.getPhasePosition(LifecyclePhase.IMPROVE)).toBe(PHASE_ZONE_POSITIONS[LifecyclePhase.IMPROVE]);
    });
});
