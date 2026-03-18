import { atom } from 'jotai';

/**
 *
 */
export type LifecyclePhase =
  | 'design'
  | 'backlog'
  | 'build'
  | 'test'
  | 'deploy'
  | 'monitor';

/**
 *
 */
export interface LifecycleState {
  currentPhase: LifecyclePhase;
  completedPhases: LifecyclePhase[];
  activeTransition?: {
    from: LifecyclePhase;
    to: LifecyclePhase;
    startedAt: Date;
  };
}

export const lifecycleAtom = atom<LifecycleState>({
  currentPhase: 'design',
  completedPhases: [],
});

// Derived atoms for specific phases
export const isInPhaseAtom = (phase: LifecyclePhase) =>
  atom((get) => get(lifecycleAtom).currentPhase === phase);

export const isPhaseCompletedAtom = (phase: LifecyclePhase) =>
  atom((get) => get(lifecycleAtom).completedPhases.includes(phase));

// Actions
export const transitionToPhaseAtom = atom(
  null,
  (get, set, newPhase: LifecyclePhase) => {
    const current = get(lifecycleAtom);
    set(lifecycleAtom, {
      ...current,
      currentPhase: newPhase,
      activeTransition: {
        from: current.currentPhase,
        to: newPhase,
        startedAt: new Date(),
      },
    });
  }
);

export const completePhaseAtom = atom(
  null,
  (get, set, phase: LifecyclePhase) => {
    const current = get(lifecycleAtom);
    if (!current.completedPhases.includes(phase)) {
      set(lifecycleAtom, {
        ...current,
        completedPhases: [...current.completedPhases, phase],
        activeTransition: undefined,
      });
    }
  }
);
