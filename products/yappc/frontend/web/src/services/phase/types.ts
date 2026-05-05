export type MountedPhase =
  | 'intent'
  | 'shape'
  | 'validate'
  | 'generate'
  | 'run'
  | 'observe'
  | 'learn'
  | 'evolve';

export interface PhaseConfig {
  name: string;
  description: string;
  primaryTitle: string;
  primaryDescription: string;
  primaryLabel: string;
  primaryTestId: string;
  secondaryLabel: string;
  secondaryTestId: string;
  icon: string;
  supportingTitle: string;
  actionFeedback: string;
}

export interface PhaseProjectSnapshot {
  name?: string;
  description?: string | null;
  lifecyclePhase?: string;
  updatedAt?: string;
  healthScore?: number | null;
  nextActionHints?: string[];
}

export interface PhaseActivityEvent {
  id: string;
  source: 'lifecycle' | 'audit';
  action: string;
  summary: string;
  timestamp: string;
  actor: string | null;
  severity?: string | null;
  success?: boolean | null;
}

export interface PhaseActivityResponse {
  projectId: string;
  activity: PhaseActivityEvent[];
}

export interface PhaseTransitionPreviewSnapshot {
  projectId: string;
  currentPhase: string;
  nextPhase: string | null;
  canAdvance: boolean;
  readiness: number;
  blockers: string[];
  requiredArtifacts: string[];
  completedArtifacts: string[];
  estimatedReadyIn: string | null;
  estimatedReadyInHours: number | null;
  predictionConfidence: number | null;
  checkedAt: string;
}
