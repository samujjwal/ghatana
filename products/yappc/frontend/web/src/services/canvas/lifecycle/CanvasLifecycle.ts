// @ts-nocheck
/**
 * Canvas Lifecycle Manager
 * 
 * Manages lifecycle phase transitions and phase-aware behaviors
 * 
 * @doc.type service
 * @doc.purpose Manage canvas lifecycle phases
 * @doc.layer product
 * @doc.pattern Service
 */

import {
    LifecyclePhase,
    PhaseTransition,
    canTransitionToPhase,
    getOperationsForPhase,
    getPhaseDescription,
} from '@/types/lifecycle';
import type { CanvasState } from '@/components/canvas/workspace/canvasAtoms';

/**
 * Event emitted when phase changes
 */
export interface PhaseChangeEvent {
    phase: LifecyclePhase;
    previousPhase?: LifecyclePhase;
    timestamp: number;
    triggeredBy: 'user' | 'agent' | 'system';
}

/**
 * Callback for phase change events
 */
export type PhaseChangeListener = (event: PhaseChangeEvent) => void;

/**
 * Canvas Lifecycle Manager
 * Manages phase transitions and notifies listeners
 */
export class CanvasLifecycle {
    private currentPhase: LifecyclePhase;
    private listeners: Set<PhaseChangeListener> = new Set();
    private phaseHistory: PhaseTransition[] = [];

    constructor(initialPhase: LifecyclePhase = LifecyclePhase.INTENT) {
        this.currentPhase = initialPhase;
        // Seed history with the initial state so getPhaseHistory() always reflects visited phases
        this.phaseHistory.push({
            fromPhase: initialPhase,
            toPhase: initialPhase,
            timestamp: Date.now(),
            triggeredBy: 'system',
        });
    }

    /**
     * Get current lifecycle phase
     */
    getCurrentPhase(): LifecyclePhase {
        return this.currentPhase;
    }

    /**
     * Get phase transition history
     */
    getPhaseHistory(): PhaseTransition[] {
        return [...this.phaseHistory];
    }

    /**
     * Check if transition to given phase is valid from current phase
     */
    canTransitionTo(toPhase: LifecyclePhase): boolean {
        return canTransitionToPhase(this.currentPhase, toPhase);
    }

    /**
     * Attempt to transition to a new phase
     * @throws Error if transition is invalid
     */
    transitionToPhase(
        toPhase: LifecyclePhase,
        triggeredBy: 'user' | 'agent' | 'system' = 'user',
        reason?: string,
    ): void {
        // Validate transition
        if (!canTransitionToPhase(this.currentPhase, toPhase)) {
            throw new Error(
                `Invalid phase transition from ${this.currentPhase} to ${toPhase}`
            );
        }

        const previousPhase = this.currentPhase;
        const timestamp = Date.now();

        // Record transition
        const transition: PhaseTransition = {
            fromPhase: previousPhase,
            toPhase,
            timestamp,
            triggeredBy,
            reason,
        };
        this.phaseHistory.push(transition);

        // Update current phase
        this.currentPhase = toPhase;

        // Notify listeners
        const event: PhaseChangeEvent = {
            phase: toPhase,
            previousPhase,
            timestamp,
            triggeredBy,
        };
        this.notifyListeners(event);
    }

    /**
     * Check if specific operation is allowed in current phase
     */
    canPerformOperation(operation: keyof ReturnType<typeof getOperationsForPhase>): boolean {
        const operations = getOperationsForPhase(this.currentPhase);
        return operations[operation];
    }

    /**
     * Get all allowed operations for current phase
     */
    getAllowedOperations() {
        return getOperationsForPhase(this.currentPhase);
    }

    /**
     * Get human-readable description of current phase
     */
    getPhaseDescription(): string {
        return getPhaseDescription(this.currentPhase);
    }

    /**
     * Add listener for phase changes
     */
    addListener(listener: PhaseChangeListener): () => void {
        this.listeners.add(listener);

        // Return unsubscribe function
        return () => {
            this.listeners.delete(listener);
        };
    }

    /**
     * Notify all listeners of phase change
     */
    private notifyListeners(event: PhaseChangeEvent): void {
        for (const listener of this.listeners) {
            try {
                listener(event);
            } catch (error) {
                console.error('Error in phase change listener:', error);
            }
        }
    }

    /**
     * Restore lifecycle state from canvas state
     */
    restoreFromCanvasState(canvasState: CanvasState): void {
        if (canvasState.lifecyclePhase) {
            this.currentPhase = canvasState.lifecyclePhase;
        }
        if (canvasState.phaseHistory) {
            this.phaseHistory = [...canvasState.phaseHistory];
        }
    }

    /**
     * Export lifecycle state to be saved in canvas state
     */
    exportToCanvasState(): Pick<CanvasState, 'lifecyclePhase' | 'phaseHistory'> {
        return {
            lifecyclePhase: this.currentPhase,
            phaseHistory: [...this.phaseHistory],
        };
    }
}

/**
 * React Hook for Canvas Lifecycle
 */
export function useCanvasLifecycle(canvasState?: CanvasState) {
    const [lifecycle] = React.useState(() => {
        const initialPhase = canvasState?.lifecyclePhase || LifecyclePhase.INTENT;
        const mgr = new CanvasLifecycle(initialPhase);
        if (canvasState) {
            mgr.restoreFromCanvasState(canvasState);
        }
        return mgr;
    });

    const [currentPhase, setCurrentPhase] = React.useState(lifecycle.getCurrentPhase());

    React.useEffect(() => {
        const unsubscribe = lifecycle.addListener((event) => {
            setCurrentPhase(event.phase);
        });
        return unsubscribe;
    }, [lifecycle]);

    const transitionToPhase = React.useCallback(
        (phase: LifecyclePhase, triggeredBy: 'user' | 'agent' | 'system' = 'user', reason?: string) => {
            lifecycle.transitionToPhase(phase, triggeredBy, reason);
        },
        [lifecycle]
    );

    const canTransitionTo = React.useCallback(
        (toPhase: LifecyclePhase) => lifecycle.canTransitionTo(toPhase),
        [lifecycle, currentPhase],
    );

    const getPhaseHistory = React.useCallback(
        () => lifecycle.getPhaseHistory(),
        [lifecycle],
    );

    const canPerformOperation = React.useCallback(
        (operation: Parameters<typeof lifecycle.canPerformOperation>[0]) => {
            return lifecycle.canPerformOperation(operation);
        },
        [lifecycle, currentPhase] // Re-check when phase changes
    );

    const getAllowedOperations = React.useCallback(() => {
        return lifecycle.getAllowedOperations();
    }, [lifecycle, currentPhase]);

    return {
        currentPhase,
        transitionToPhase,
        canTransitionTo,
        canPerformOperation,
        getAllowedOperations,
        getPhaseHistory,
        lifecycle,
    };
}

import React from 'react';
