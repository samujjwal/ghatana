/**
 * Mock for @yappc/state package
 * Provides jotai atoms without importing StateProvider (which causes oxc transform errors in tests)
 */
import { atom } from 'jotai';

// Canvas chrome atoms (used by canvas.tsx)
export const chromeCalmModeAtom = atom<boolean>(true);
export const chromeInspectorVisibleAtom = atom<boolean>(false);
export const chromeLeftRailVisibleAtom = atom<boolean>(false);
export const chromeMinimapVisibleAtom = atom<boolean>(false);
export const chromeZoomLevelAtom = atom<number>(1);

// Canvas atoms
export const canvasStateAtom = atom<Record<string, unknown> | null>(null);
export const canvasNodesAtom = atom<unknown[]>([]);
export const canvasEdgesAtom = atom<unknown[]>([]);
export const selectedCanvasNodeAtom = atom<unknown>(null);
export const canvasViewportAtom = atom({ x: 0, y: 0, zoom: 1 });
export const canvasModeAtom = atom<'select' | 'pan' | 'draw' | 'text'>('select');

// Session atoms
export const sessionAtom = atom<Record<string, unknown> | null>(null);
export const bootstrapSessionAtom = sessionAtom;
export const conversationHistoryAtom = atom<unknown[]>([]);
export const currentPhaseAtom = atom<string>('discover');
export const confidenceScoreAtom = atom<number>(0);
export const questionsAnsweredAtom = atom<number>(0);
export const totalQuestionsAtom = atom<number>(0);
export const currentQuestionAtom = atom<unknown>(null);
export const pendingAnswerAtom = atom<string>('');
export const agentStatusAtom = atom<string>('idle');
export const agentStatusMessageAtom = atom<string>('');
export const inputModeAtom = atom<string>('text');
export const aiAgentStateAtom = atom<Record<string, unknown>>({});
export const collaboratorsAtom = atom<unknown[]>([]);
export const onlineCollaboratorsAtom = atom((get: (a: typeof collaboratorsAtom) => unknown[]) =>
  get(collaboratorsAtom)
);
export const wizardStateAtom = atom<Record<string, unknown> | null>(null);
export const currentWizardStepAtom = atom<number>(0);
export const wizardProgressAtom = atom<number>(0);
export const infrastructureStateAtom = atom<Record<string, unknown> | null>(null);
export const provisioningStatusAtom = atom<Record<string, unknown> | null>(null);
export const environmentsAtom = atom<unknown[]>([]);
export const costEstimateAtom = atom<Record<string, unknown> | null>(null);
export const validationStateAtom = atom<Record<string, unknown>>({});
export const validationReportAtom = atom<Record<string, unknown> | null>(null);
export const commandSuggestionsAtom = atom<unknown[]>([]);

// StateProvider (no-op in tests)
import React from 'react';
export interface StateProviderProps {
  children: React.ReactNode;
  initialState?: Record<string, unknown>;
}
export const StateProvider = ({ children }: StateProviderProps) => children as React.ReactElement;

// StateManager stub
export class StateManager {
  static create() { return new StateManager(); }
}

// Sync stubs
export const syncStateAcrossTabs = () => {};
export const writeAtomToStorage = () => {};
export const readAtomFromStorage = () => null;
export const subscribeToSync = () => () => {};
export const getSyncStatistics = () => ({});

// Workspace atoms
export const workspaceAtom = atom<Record<string, unknown> | null>(null);
export const activeWorkspaceAtom = atom<string | null>(null);

// Project atoms
export const projectAtom = atom<Record<string, unknown> | null>(null);
export const activeProjectAtom = atom<string | null>(null);

// AI atoms
export const aiConfigAtom = atom<Record<string, unknown>>({});
export const aiSessionAtom = atom<Record<string, unknown> | null>(null);
