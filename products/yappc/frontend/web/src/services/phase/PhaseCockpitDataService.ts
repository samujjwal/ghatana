import type {
  PhaseFeatureFlag,
  PhaseProjectSnapshot,
  PhaseTransitionPreviewSnapshot,
  TenantTier,
} from './types';
import type { Project } from '@/lib/api/client';
import { yappcApi } from '@/lib/api/client';

export type LifecyclePhase =
  | 'INTENT'
  | 'SHAPE'
  | 'VALIDATE'
  | 'GENERATE'
  | 'RUN'
  | 'OBSERVE'
  | 'LEARN'
  | 'EVOLVE';

interface RawPhaseProjectSnapshot extends Omit<PhaseProjectSnapshot, 'healthScore' | 'nextActionHints'> {
  currentPhase?: string;
  healthScore?: number | null;
  nextActionHints?: string[];
  aiHealthScore?: number | null;
  aiNextActions?: string[];
  tier?: TenantTier;
  subscriptionTier?: TenantTier;
  featureFlags?: string[];
  enabledFlags?: string[];
}

const KNOWN_TENANT_TIERS: readonly TenantTier[] = ['free', 'starter', 'pro', 'enterprise'];
const KNOWN_PHASE_FLAGS: readonly PhaseFeatureFlag[] = [
  'phase.generate.enabled',
  'phase.run.preview.enabled',
  'phase.run.production.enabled',
  'phase.observe.enabled',
  'phase.learn.patterns.enabled',
  'phase.evolve.enabled',
];

function normalizeTenantTier(value: unknown): TenantTier | undefined {
  return typeof value === 'string' && KNOWN_TENANT_TIERS.includes(value as TenantTier)
    ? (value as TenantTier)
    : undefined;
}

function normalizePhaseFlags(value: unknown): PhaseFeatureFlag[] | undefined {
  if (!Array.isArray(value)) {
    return undefined;
  }

  const flags = value.filter(
    (flag): flag is PhaseFeatureFlag =>
      typeof flag === 'string' && KNOWN_PHASE_FLAGS.includes(flag as PhaseFeatureFlag),
  );

  return flags.length > 0 ? flags : undefined;
}

const LIFECYCLE_PHASES: readonly LifecyclePhase[] = [
  'INTENT',
  'SHAPE',
  'VALIDATE',
  'GENERATE',
  'RUN',
  'OBSERVE',
  'LEARN',
  'EVOLVE',
];

export function isLifecyclePhase(value: string): value is LifecyclePhase {
  return (LIFECYCLE_PHASES as readonly string[]).includes(value);
}

export function formatTimestamp(timestamp: string): string {
  return new Date(timestamp).toLocaleString([], {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

async function readResponseBody(response: Response): Promise<string> {
  const maybeText = (response as Response & { text?: () => Promise<string> }).text;
  if (typeof maybeText === 'function') {
    return maybeText.call(response);
  }

  const maybeJson = (response as Response & { json?: () => Promise<unknown> }).json;
  if (typeof maybeJson === 'function') {
    const payload = await maybeJson.call(response);
    if (typeof payload === 'string') {
      return payload;
    }
    return JSON.stringify(payload ?? {});
  }

  return '';
}

export async function parseJsonResponse<T>(
  response: Response,
  context: string,
): Promise<T> {
  const raw = await readResponseBody(response);

  if (!raw) {
    throw new Error(`${context} returned an empty response`);
  }

  try {
    return JSON.parse(raw) as T;
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`${context} returned invalid JSON: ${detail}`, {
      cause: error,
    });
  }
}

export async function parseProjectResponse(
  response: Response,
  context: string,
): Promise<PhaseProjectSnapshot> {
  const payload = await parseJsonResponse<unknown>(response, context);

  const rawProject = (
    typeof payload === 'object' &&
    payload !== null &&
    'project' in payload
      ? (payload as { project: RawPhaseProjectSnapshot }).project
      : (payload as RawPhaseProjectSnapshot)
  );

  return {
    ...rawProject,
    healthScore: rawProject.aiHealthScore ?? null,
    nextActionHints: rawProject.aiNextActions ?? [],
    tenantTier:
      normalizeTenantTier(rawProject.tenantTier) ??
      normalizeTenantTier(rawProject.tier) ??
      normalizeTenantTier(rawProject.subscriptionTier),
    enabledPhaseFlags:
      normalizePhaseFlags(rawProject.enabledPhaseFlags) ??
      normalizePhaseFlags(rawProject.featureFlags) ??
      normalizePhaseFlags(rawProject.enabledFlags),
  };
}

export function normalizeProjectSnapshot(
  project: Project | RawPhaseProjectSnapshot | { readonly project: RawPhaseProjectSnapshot },
): PhaseProjectSnapshot {
  const rawProject = (
    typeof project === 'object' &&
    project !== null &&
    'project' in project
      ? project.project
      : project
  ) as RawPhaseProjectSnapshot;
  return {
    ...rawProject,
    lifecyclePhase: rawProject.lifecyclePhase ?? rawProject.currentPhase,
    healthScore: rawProject.aiHealthScore ?? rawProject.healthScore ?? null,
    nextActionHints: rawProject.aiNextActions ?? rawProject.nextActionHints ?? [],
    tenantTier:
      normalizeTenantTier(rawProject.tenantTier) ??
      normalizeTenantTier(rawProject.tier) ??
      normalizeTenantTier(rawProject.subscriptionTier),
    enabledPhaseFlags:
      normalizePhaseFlags(rawProject.enabledPhaseFlags) ??
      normalizePhaseFlags(rawProject.featureFlags) ??
      normalizePhaseFlags(rawProject.enabledFlags),
  };
}

export async function fetchProjectSnapshot(
  projectId: string,
  workspaceId?: string,
): Promise<PhaseProjectSnapshot> {
  const project = workspaceId
    ? await yappcApi.projects.getScoped(projectId, workspaceId)
    : await yappcApi.projects.get(projectId);
  return normalizeProjectSnapshot(project);
}

export async function fetchPhaseTransitionPreview(
  currentPhase: LifecyclePhase,
  projectId: string,
): Promise<PhaseTransitionPreviewSnapshot> {
  return yappcApi.phases.next(currentPhase, projectId);
}
