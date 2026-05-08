import { atom } from 'jotai';
import type { ReactNode } from 'react';
import { featureFlagsAtom } from './atoms';

export type BootstrapPhase =
  | 'discover'
  | 'design'
  | 'build'
  | 'test'
  | 'launch';

export type InputMode = 'text' | 'voice' | 'upload' | 'canvas' | 'template';

export type AgentStatus =
  | 'idle'
  | 'thinking'
  | 'typing'
  | 'responding'
  | 'waiting'
  | 'error';

export interface QuestionOption {
  id: string;
  label: string;
  value: string;
  icon?: ReactNode;
}

export interface Question {
  id: string;
  text: string;
  hint?: string;
  required?: boolean;
  options?: QuestionOption[];
}

export interface CanvasViewport {
  x: number;
  y: number;
  zoom: number;
}

export interface CanvasNode {
  id: string;
  type?: string;
  position?: { x: number; y: number };
  data?: Record<string, unknown>;
}

export interface CanvasEdge {
  id: string;
  source: string;
  target: string;
  data?: Record<string, unknown>;
}

export interface Collaborator {
  id: string;
  name: string;
  isOnline: boolean;
  avatarUrl?: string;
}

export const sessionAtom = atom<Record<string, unknown> | null>(null);
export const bootstrapSessionAtom = sessionAtom;

export const conversationHistoryAtom = atom<
  Array<{
    id: string;
    role: 'user' | 'assistant' | 'system';
    content: string;
    timestamp?: number;
  }>
>([]);

export const canvasNodesAtom = atom<CanvasNode[]>([]);
export const canvasEdgesAtom = atom<CanvasEdge[]>([]);
export const selectedCanvasNodeAtom = atom<CanvasNode | null>(null);
export const canvasViewportAtom = atom<CanvasViewport>({ x: 0, y: 0, zoom: 1 });
export const canvasModeAtom = atom<'select' | 'pan' | 'draw' | 'text'>('select');

export const validationStateAtom = atom<{
  isValid: boolean;
  errors: string[];
  warnings: string[];
} | null>(null);

export const validationReportAtom = atom<Record<string, unknown> | null>(null);
export const commandSuggestionsAtom = atom<Record<string, unknown>[]>([]);

export const currentPhaseAtom = atom<BootstrapPhase>('discover');
export const confidenceScoreAtom = atom<number>(0);
export const questionsAnsweredAtom = atom<number>(0);
export const totalQuestionsAtom = atom<number>(0);
export const currentQuestionAtom = atom<Question | null>(null);
export const pendingAnswerAtom = atom<string>('');
export const agentStatusAtom = atom<AgentStatus>('idle');
export const agentStatusMessageAtom = atom<string>('');
export const inputModeAtom = atom<InputMode>('text');
export const aiAgentStateAtom = atom<{
  status: 'idle' | 'thinking' | 'responding';
  currentTask?: string;
  isProcessing?: boolean;
}>({ status: 'idle' });

export const collaboratorsAtom = atom<Collaborator[]>([]);
export const onlineCollaboratorsAtom = atom((get) =>
  get(collaboratorsAtom).filter((collaborator) => collaborator.isOnline)
);

export const wizardStateAtom = atom<Record<string, unknown> | null>(null);
export const currentWizardStepAtom = atom<number>(0);
export const wizardProgressAtom = atom<number>(0);
export const infrastructureStateAtom = atom<Record<string, unknown> | null>(null);
export const provisioningStatusAtom = atom<Record<string, unknown> | null>(null);
export const environmentsAtom = atom<Record<string, unknown>[]>([]);
export const costEstimateAtom = atom<Record<string, unknown> | null>(null);
export const teamInvitesAtom = atom<Record<string, unknown>[]>([]);

export const activeSprintAtom = atom<Record<string, unknown> | null>(null);
export const sprintStoriesAtom = atom<Record<string, unknown>[]>([]);
export const selectedStoryAtom = atom<Record<string, unknown> | null>(null);
export const sprintBoardAtom = atom<Record<string, unknown> | null>(null);
export const sprintsAtom = atom<Record<string, unknown>[]>([]);
export const storiesMapAtom = atom<Record<string, Record<string, unknown>>>({});
export const boardColumnsAtom = atom<Record<string, unknown>[]>([]);
export const pullRequestsAtom = atom<Record<string, unknown>[]>([]);
// Note: featureFlagsAtom is defined in atoms.ts
export const deploymentsAtom = atom<Record<string, unknown>[]>([]);
export const epicsAtom = atom<Record<string, unknown>[]>([]);
export const velocityDataAtom = atom<Record<string, unknown> | null>(null);

export const vulnerabilitiesAtom = atom<Record<string, unknown>[]>([]);
export const selectedVulnerabilityAtom = atom<Record<string, unknown> | null>(null);
export const securityScansAtom = atom<Record<string, unknown>[]>([]);
export const complianceStatusAtom = atom<Record<string, unknown> | null>(null);
export const secretsAtom = atom<Record<string, unknown>[]>([]);
export const securityPoliciesAtom = atom<Record<string, unknown>[]>([]);
export const securityScoreAtom = atom<Record<string, unknown> | null>(null);
export const securityAlertsAtom = atom<Record<string, unknown>[]>([]);
export const auditLogsAtom = atom<Record<string, unknown>[]>([]);

export const incidentsAtom = atom<Record<string, unknown>[]>([]);
export const activeIncidentAtom = atom<Record<string, unknown> | null>(null);
export const alertsAtom = atom<Record<string, unknown>[]>([]);
export const dashboardsAtom = atom<Record<string, unknown>[]>([]);
export const activeDashboardAtom = atom<Record<string, unknown> | null>(null);
export const runbooksAtom = atom<Record<string, unknown>[]>([]);
export const onCallScheduleAtom = atom<Record<string, unknown> | null>(null);
export const serviceHealthAtom = atom<Record<string, unknown>[]>([]);
export const metricsAtom = atom<Record<string, unknown>[]>([]);

export const chromeFocusModeAtom = atom<boolean>(false);
export const chromeCalmModeAtom = atom<boolean>(true);
export const chromeLeftRailVisibleAtom = atom<boolean>(false);
export type LeftPanelType = 'outline' | 'layers' | 'palette' | 'tasks' | null;
export const chromeLeftPanelAtom = atom<LeftPanelType>(null);
export const chromeInspectorVisibleAtom = atom<boolean>(false);
export const chromeMinimapVisibleAtom = atom<boolean>(false);
export const chromeContextBarVisibleAtom = atom<boolean>(false);
export const chromeDistractionFreeAtom = atom<boolean>(false);
export const chromeZoomLevelAtom = atom<number>(1);
export const chromeBreadcrumbVisibleAtom = atom<boolean>(true);
export const chromeCurrentPhaseAtom = atom<string | null>(null);
export const chromePhaseIndicatorVisibleAtom = atom<boolean>(true);

export const openLeftPanelAtom = atom(
  null,
  (_get, set, panel: LeftPanelType) => {
    set(chromeLeftPanelAtom, panel);
    if (panel !== null) {
      set(chromeLeftRailVisibleAtom, true);
    }
  }
);

export const addConversationTurnAction = atom(
  null,
  (get, set, turn: { id: string; role: 'user' | 'assistant' | 'system'; content: string; timestamp?: number }) => {
    set(conversationHistoryAtom, [...get(conversationHistoryAtom), turn]);
  }
);

export const addCanvasNodeAction = atom(null, (get, set, node: CanvasNode) => {
  set(canvasNodesAtom, [...get(canvasNodesAtom), node]);
});

export const updateCanvasNodeAction = atom(
  null,
  (get, set, node: CanvasNode) => {
    set(
      canvasNodesAtom,
      get(canvasNodesAtom).map((currentNode) =>
        currentNode.id === node.id ? { ...currentNode, ...node } : currentNode
      )
    );
  }
);

export const removeCanvasNodeAction = atom(
  null,
  (get, set, nodeId: string) => {
    set(
      canvasNodesAtom,
      get(canvasNodesAtom).filter((currentNode) => currentNode.id !== nodeId)
    );
  }
);

export const undoCanvasAction = atom(null, () => undefined);
export const redoCanvasAction = atom(null, () => undefined);

export const updateStoryStatusAction = atom(
  null,
  () => undefined
);

export const moveStoryToSprintAction = atom(
  null,
  () => undefined
);

export const toggleFeatureFlagAction = atom(
  null,
  (get, set, flagName: string) => {
    const featureFlags = get(featureFlagsAtom);
    set(featureFlagsAtom, {
      ...featureFlags,
      [flagName]: !featureFlags[flagName],
    });
  }
);
