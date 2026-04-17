import { atom } from 'jotai';

/**
 *
 */
export type GateType =
  | 'quality'
  | 'security'
  | 'performance'
  | 'accessibility'
  | 'compliance';

/**
 *
 */
export type GateStatus =
  | 'pending'
  | 'running'
  | 'passed'
  | 'failed'
  | 'skipped';

/**
 *
 */
export interface QualityGate {
  id: string;
  type: GateType;
  name: string;
  description: string;
  status: GateStatus;
  criteria: {
    metric: string;
    threshold: number;
    operator: '<' | '>' | '=' | '<=' | '>=';
    currentValue?: number;
  }[];
  lastRunAt?: Date;
  duration?: number; // milliseconds
  errorMessage?: string;
}

/**
 *
 */
export interface GatesState {
  gates: QualityGate[];
  isRunning: boolean;
  lastRunAt?: Date;
  overallStatus: 'pending' | 'running' | 'passed' | 'failed';
}

export const gatesAtom = atom<GatesState>({
  gates: [],
  isRunning: false,
  overallStatus: 'pending',
});

// Derived atoms
export const gatesByTypeAtom = (type: GateType) =>
  atom((get) => get(gatesAtom).gates.filter((gate) => gate.type === type));

export const failedGatesAtom = atom((get) =>
  get(gatesAtom).gates.filter((gate) => gate.status === 'failed')
);

export const passedGatesAtom = atom((get) =>
  get(gatesAtom).gates.filter((gate) => gate.status === 'passed')
);

// Actions
export const runGateAtom = atom(null, (get, set, gateId: string) => {
  const current = get(gatesAtom);
  const updatedGates = current.gates.map((gate) =>
    gate.id === gateId
      ? { ...gate, status: 'running' as GateStatus, lastRunAt: new Date() }
      : gate
  );

  set(gatesAtom, {
    ...current,
    gates: updatedGates,
    isRunning: true,
  });
});

export const updateGateStatusAtom = atom(
  null,
  (
    get,
    set,
    gateId: string,
    status: GateStatus,
    errorMessage?: string,
    duration?: number
  ) => {
    const current = get(gatesAtom);
    const updatedGates = current.gates.map((gate) =>
      gate.id === gateId ? { ...gate, status, errorMessage, duration } : gate
    );

    const isStillRunning = updatedGates.some(
      (gate) => gate.status === 'running'
    );
    const overallStatus = updatedGates.some((gate) => gate.status === 'failed')
      ? 'failed'
      : updatedGates.every((gate) => gate.status === 'passed')
        ? 'passed'
        : 'pending';

    set(gatesAtom, {
      ...current,
      gates: updatedGates,
      isRunning: isStillRunning,
      overallStatus,
      lastRunAt: !isStillRunning ? new Date() : current.lastRunAt,
    });
  }
);

export const addGateAtom = atom(
  null,
  (get, set, gate: Omit<QualityGate, 'id'>) => {
    const current = get(gatesAtom);
    const newGate: QualityGate = {
      ...gate,
      id: `gate-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    };

    set(gatesAtom, {
      ...current,
      gates: [...current.gates, newGate],
    });
  }
);
