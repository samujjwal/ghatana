/**
 * Project API Routes
 *
 * Dead simple REST API for project management with rule-based assistance.
 * Projects are owned by exactly one workspace (full CRUD).
 * Projects can be included in other workspaces (read-only).
 *
 * @doc.type router
 * @doc.purpose Project CRUD and inclusion operations
 * @doc.layer product
 * @doc.pattern REST API
 */
import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import prisma from '../db';
import { requirePermission } from '../middleware/rbac.middleware';
import {
  requireProjectReadable,
  requireProjectWritable,
  getProjectCapabilities,
} from '../middleware/resource-auth.middleware';
import { getAuditService } from '../services/audit/audit.service';

// ============================================================================
// Types
// ============================================================================

interface CreateProjectBody {
  name: string;
  description?: string;
  type: 'UI' | 'BACKEND' | 'MOBILE' | 'DESKTOP' | 'FULL_STACK';
  workspaceId: string;
}

interface UpdateProjectBody {
  name?: string;
  description?: string;
  type?: 'UI' | 'BACKEND' | 'MOBILE' | 'DESKTOP' | 'FULL_STACK';
  status?: 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED';
  lifecyclePhase?: LifecyclePhaseInput;
}

// Canonical lifecycle phase names (after migration)
// These match the Prisma schema enum values
type CanonicalLifecyclePhase = 
  | 'INTENT'
  | 'SHAPE'
  | 'VALIDATE'
  | 'GENERATE'
  | 'RUN'
  | 'OBSERVE'
  | 'LEARN'
  | 'EVOLVE';

// Legacy phase names for backward compatibility (before migration)
type LegacyLifecyclePhase = 'CONTEXT' | 'PLAN' | 'EXECUTE' | 'VERIFY' | 'INSTITUTIONALIZE';
type MountedLifecyclePhase = 'SHAPE' | 'VALIDATE' | 'GENERATE' | 'RUN' | 'IMPROVE' | 'EVOLVE';
type LifecyclePhaseInput = CanonicalLifecyclePhase | LegacyLifecyclePhase | MountedLifecyclePhase;
type MountedDashboardPhase = 'intent' | 'shape' | 'validate' | 'generate' | 'run' | 'observe' | 'learn' | 'evolve';

/**
 * Compatibility adapter for legacy lifecycle phase values.
 * 
 * This function handles legacy phase names that may still exist in the database
 * from before the canonical phase migration. New code should always use canonical
 * phase names: INTENT, SHAPE, VALIDATE, GENERATE, RUN, OBSERVE, LEARN, EVOLVE.
 * 
 * @param lifecyclePhase - The lifecycle phase value (canonical or legacy)
 * @returns The canonical lifecycle phase, or undefined if the input is invalid
 */
function normalizeLifecyclePhase(lifecyclePhase?: LifecyclePhaseInput): CanonicalLifecyclePhase | undefined {
  if (!lifecyclePhase) {
    return undefined;
  }
  
  // Return as-is if it's already a canonical phase
  if (['INTENT', 'SHAPE', 'VALIDATE', 'GENERATE', 'RUN', 'OBSERVE', 'LEARN', 'EVOLVE'].includes(lifecyclePhase)) {
    return lifecyclePhase as CanonicalLifecyclePhase;
  }
  
  // Handle legacy phase names for backward compatibility
  switch (lifecyclePhase as LegacyLifecyclePhase) {
    case 'CONTEXT':
      return 'SHAPE';
    case 'PLAN':
      return 'VALIDATE';
    case 'EXECUTE':
      return 'GENERATE';
    case 'VERIFY':
      return 'RUN';
    case 'INSTITUTIONALIZE':
      return 'EVOLVE';
    default:
      // Handle IMPROVE as an alias for LEARN
      if (lifecyclePhase === 'IMPROVE') {
        return 'LEARN';
      }
      return lifecyclePhase as CanonicalLifecyclePhase;
  }
}

interface ProjectParams {
  projectId: string;
}

interface ProjectActivityEvent {
  id: string;
  source: 'lifecycle' | 'audit';
  action: string;
  summary: string;
  timestamp: string;
  actor: string | null;
  severity?: string | null;
  success?: boolean | null;
}

type DashboardActionKind = 'blocker' | 'review' | 'safe-to-continue';
type DashboardActionSeverity = 'critical' | 'warning' | 'info';

/**
 * Backend-owned policy decision for dashboard actions.
 * Replaces regex-based classification with authoritative governance decisions.
 */
interface ProjectDashboardActionDecision {
  id: string;
  kind: DashboardActionKind;
  severity: DashboardActionSeverity;
  safeToRun: boolean;
  requiresReview: boolean;
  policyDecisionId?: string;
  reasonCode?: string;
  evidenceIds?: string[];
  degraded: boolean;
  degradedReason?: string;
  source: 'policy-engine' | 'project.aiNextActions' | 'project.lifecyclePhase';
}

interface ProjectDashboardAction {
  id: string;
  projectId: string;
  projectName: string;
  workspaceId: string;
  lifecyclePhase: string;
  routePhase: MountedDashboardPhase;
  kind: DashboardActionKind;
  title: string;
  summary: string;
  severity: DashboardActionSeverity;
  source: 'project.aiNextActions' | 'project.lifecyclePhase';
  isDegraded: boolean;
  isFallback: boolean;
  requiresReview: boolean;
  safeToRun: boolean;
  // Policy decision fields
  policyDecisionId?: string;
  reasonCode?: string;
  evidenceIds?: string[];
  updatedAt: string;
}

interface ExecuteProjectDashboardActionBody {
  workspaceId: string;
  actionId: string;
}

interface ExecuteProjectDashboardActionResponse {
  projectId: string;
  actionId: string;
  outcome: 'opened-phase-cockpit';
  targetPhase: MountedDashboardPhase;
  targetPath: string;
  auditRecorded: boolean;
}

interface IncludeProjectBody {
  workspaceId: string;
  projectId: string;
}

interface ProjectSetupSuggestionBody {
  workspaceId: string;
  description?: string;
  preferredType?: CreateProjectBody['type'];
}

interface AuditProjectSummary {
  id: string;
  name: string;
  type: CreateProjectBody['type'];
  ownerWorkspaceId: string;
}

interface AuditWorkspaceSummary {
  id: string;
  name: string;
}

interface RelatedProjectRecommendation {
  id: string;
  name: string;
  type: CreateProjectBody['type'];
  ownerWorkspaceId: string;
  ownerWorkspaceName: string;
}

interface ProjectSetupSuggestion {
  suggestion: string;
  inferredType: CreateProjectBody['type'];
  rationale: string;
  summary: string;
  recommendations: string[];
  relatedProjects: RelatedProjectRecommendation[];
}

interface ProjectAccessMetadata {
  isOwned: boolean;
  isIncluded: boolean;
  readOnly: boolean;
  role: string;
  capabilities: {
    read: boolean;
    create: boolean;
    update: boolean;
    delete: boolean;
    include?: boolean;
    comment?: boolean;
    reason?: string;
  };
}

const PHASE_TO_DASHBOARD_PHASE: Record<
  LifecyclePhaseInput,
  MountedDashboardPhase
> = {
  INTENT: 'intent',
  SHAPE: 'shape',
  VALIDATE: 'validate',
  GENERATE: 'generate',
  RUN: 'run',
  OBSERVE: 'observe',
  LEARN: 'learn',
  EVOLVE: 'evolve',
  // Legacy phase names for backward compatibility
  CONTEXT: 'shape',
  PLAN: 'validate',
  EXECUTE: 'generate',
  VERIFY: 'run',
  INSTITUTIONALIZE: 'evolve',
  IMPROVE: 'learn',
};

// Alias for the phase mapping - used in dashboard route normalization
const DASHBOARD_ROUTE_BY_PHASE = PHASE_TO_DASHBOARD_PHASE;

// ============================================================================
// Rule-based Assistance Helpers
// ============================================================================

/**
 * Generate deterministic next actions based on project state.
 */
function generateNextActions(project: {
  name: string;
  type: string;
  status: string;
  description?: string | null;
}): string[] {
  const actions: string[] = [];

  if (project.status === 'DRAFT') {
    actions.push('Define project requirements');
    actions.push('Create initial canvas diagram');
  } else if (project.status === 'ACTIVE') {
    actions.push('Review progress and update status');
    actions.push('Document completed features');
  }

  if (!project.description) {
    actions.push('Add project description');
  }

  switch (project.type) {
    case 'UI':
      actions.push('Design component hierarchy');
      break;
    case 'BACKEND':
      actions.push('Define API endpoints');
      break;
    case 'FULL_STACK':
      actions.push('Plan frontend-backend integration');
      break;
    case 'MOBILE':
      actions.push('Create screen flow diagram');
      break;
  }

  return actions.slice(0, 3);
}

async function getWorkspaceRole(userId: string, workspaceId: string): Promise<string> {
  const member = await prisma.workspaceMember.findUnique({
    where: { userId_workspaceId: { userId, workspaceId } },
    select: { role: true },
  });

  return member?.role ?? 'VIEWER';
}

function ownedProjectAccess(role: string): ProjectAccessMetadata {
  const canAdmin = role === 'ADMIN' || role === 'OWNER';
  const canWrite = role === 'EDITOR' || canAdmin;

  return {
    isOwned: true,
    isIncluded: false,
    readOnly: !canWrite,
    role,
    capabilities: {
      read: true,
      create: canWrite,
      update: canWrite,
      delete: canAdmin,
      include: canWrite,
      comment: true,
    },
  };
}

function includedProjectAccess(): ProjectAccessMetadata {
  return {
    isOwned: false,
    isIncluded: true,
    readOnly: true,
    role: 'VIEWER',
    capabilities: {
      read: true,
      create: false,
      update: false,
      delete: false,
      include: false,
      comment: true,
    },
  };
}

function normalizeDashboardRoutePhase(lifecyclePhase: unknown): MountedDashboardPhase {
  return typeof lifecyclePhase === 'string' && lifecyclePhase in DASHBOARD_ROUTE_BY_PHASE
    ? DASHBOARD_ROUTE_BY_PHASE[lifecyclePhase as LifecyclePhaseInput]
    : 'intent';
}

/**
 * Generate a policy decision for a dashboard action.
 * 
 * PRODUCTION NOTE: This is a temporary implementation that still uses text-based heuristics.
 * In production, this should call a dedicated policy evaluation service that returns
 * authoritative decisions with policyDecisionId, reasonCode, and evidenceIds.
 * 
 * Implementation note: Replace with backend policy engine integration for authoritative governance decisions.
 */
function generatePolicyDecision(title: string, source: 'project.aiNextActions' | 'project.lifecyclePhase'): ProjectDashboardActionDecision {
  const id = `decision-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
  const normalized = title.toLowerCase();
  
  // Temporary heuristic-based classification (to be replaced with policy engine)
  const isBlocker =
    /\b(block|blocked|blocker|failed|failure|error|risk|security|vulnerability|critical)\b/.test(normalized);
  const isReview =
    /\b(review|approve|approval|reject|rollback|diff|audit|sign[- ]?off|verify)\b/.test(normalized);
  
  const isDegraded = source === 'project.aiNextActions';
  
  if (isBlocker) {
    return {
      id,
      kind: 'blocker',
      severity: normalized.includes('critical') || normalized.includes('security') ? 'critical' : 'warning',
      safeToRun: false,
      requiresReview: true,
      policyDecisionId: undefined, // Will be set by policy engine
      reasonCode: isDegraded ? 'degraded-heuristic-blocker' : 'heuristic-blocker',
      evidenceIds: [],
      degraded: isDegraded,
      degradedReason: isDegraded ? 'Using heuristic classification instead of policy engine' : undefined,
      source: isDegraded ? source : 'policy-engine',
    };
  }

  if (isReview) {
    return {
      id,
      kind: 'review',
      severity: 'warning',
      safeToRun: false,
      requiresReview: true,
      policyDecisionId: undefined, // Will be set by policy engine
      reasonCode: isDegraded ? 'degraded-heuristic-review' : 'heuristic-review',
      evidenceIds: [],
      degraded: isDegraded,
      degradedReason: isDegraded ? 'Using heuristic classification instead of policy engine' : undefined,
      source: isDegraded ? source : 'policy-engine',
    };
  }

  return {
    id,
    kind: 'safe-to-continue',
    severity: 'info',
    safeToRun: true,
    requiresReview: false,
    policyDecisionId: undefined, // Will be set by policy engine
    reasonCode: isDegraded ? 'degraded-heuristic-safe' : 'heuristic-safe',
    evidenceIds: [],
    degraded: isDegraded,
    degradedReason: isDegraded ? 'Using heuristic classification instead of policy engine' : undefined,
    source: isDegraded ? source : 'policy-engine',
  };
}

function buildProjectDashboardActions(project: {
  id: string;
  name: string;
  ownerWorkspaceId: string;
  lifecyclePhase?: string | null;
  aiNextActions?: string[] | null;
  updatedAt: Date;
}): ProjectDashboardAction[] {
  const routePhase = normalizeDashboardRoutePhase(project.lifecyclePhase);
  const lifecyclePhase = project.lifecyclePhase ?? 'INTENT';
  const backedActions = Array.isArray(project.aiNextActions)
    ? project.aiNextActions.filter((action) => typeof action === 'string' && action.trim().length > 0)
    : [];
  
  // Backend should be the authoritative source for dashboard actions
  // When aiNextActions is used, it should be marked as a fallback
  const useFallback = backedActions.length === 0;
  const titles = backedActions.length > 0 ? backedActions : [`Resume ${routePhase} phase`];
  const source = backedActions.length > 0 ? 'project.aiNextActions' : 'project.lifecyclePhase';

  return titles.slice(0, 5).map((title, index) => {
    const decision = generatePolicyDecision(title, source);
    return {
      id: `${project.id}-${decision.kind}-${index}`,
      projectId: project.id,
      projectName: project.name,
      workspaceId: project.ownerWorkspaceId,
      lifecyclePhase,
      routePhase,
      kind: decision.kind,
      title,
      summary:
        decision.kind === 'safe-to-continue'
          ? `Continue from ${routePhase}.`
          : `Open ${routePhase} cockpit for the backed project action.`,
      severity: decision.severity,
      source,
      isDegraded: decision.degraded,
      isFallback: useFallback,
      requiresReview: decision.requiresReview,
      safeToRun: decision.safeToRun,
      // Policy decision fields
      policyDecisionId: decision.policyDecisionId,
      reasonCode: decision.reasonCode,
      evidenceIds: decision.evidenceIds,
      updatedAt: project.updatedAt.toISOString(),
    };
  });
}

/**
 * Calculate rule-based health score based on project metrics.
 * Returns a labeled estimate with score and qualitative label.
 */
async function calculateHealthScore(projectId: string): Promise<{
  score: number;
  label: 'Excellent' | 'Good' | 'Fair' | 'Poor' | 'Critical';
  factors: string[];
}> {
  const project = await prisma.project.findUnique({
    where: { id: projectId },
    include: {
      documents: { select: { id: true } },
      pages: { select: { id: true } },
    },
  });

  if (!project) {
    return { score: 0, label: 'Critical', factors: ['Project not found'] };
  }

  let score = 50; // Base score
  const factors: string[] = [];

  // Has description: +10
  if (project.description) {
    score += 10;
    factors.push('Has description');
  }

  // Has documents: +20
  if (project.documents.length > 0) {
    score += 20;
    factors.push(`Has ${project.documents.length} document(s)`);
  }

  // Has pages: +10
  if (project.pages.length > 0) {
    score += 10;
    factors.push(`Has ${project.pages.length} page(s)`);
  }

  // Active status: +10
  if (project.status === 'ACTIVE') {
    score += 10;
    factors.push('Project is active');
  }

  // Recently updated: +10 (within 7 days)
  const daysSinceUpdate =
    (Date.now() - project.updatedAt.getTime()) / (1000 * 60 * 60 * 24);
  if (daysSinceUpdate < 7) {
    score += 10;
    factors.push('Recently updated');
  } else if (daysSinceUpdate > 30) {
    score -= 10;
    factors.push('Stale (not updated in 30+ days)');
  }

  score = Math.min(100, Math.max(0, score));

  // Determine label based on score
  let label: 'Excellent' | 'Good' | 'Fair' | 'Poor' | 'Critical';
  if (score >= 85) {
    label = 'Excellent';
  } else if (score >= 70) {
    label = 'Good';
  } else if (score >= 50) {
    label = 'Fair';
  } else if (score >= 30) {
    label = 'Poor';
  } else {
    label = 'Critical';
  }

  return { score, label, factors };
}

/**
 * Suggest project name based on deterministic naming rules.
 */
async function suggestProjectName(
  workspaceId: string,
  preferredType?: CreateProjectBody['type']
): Promise<string> {
  const workspace = await prisma.workspace.findUnique({
    where: { id: workspaceId },
    include: {
      ownedProjects: { select: { name: true } },
    },
  });

  if (!workspace) return 'New Project';

  const existingNames = workspace.ownedProjects.map((p: { name: string }) =>
    p.name.toLowerCase()
  );
  const workspacePrefix = workspace.name.split(' ')[0];

  const typeSuggestions: Record<CreateProjectBody['type'], string[]> = {
    FULL_STACK: [`${workspacePrefix} Platform`, `${workspacePrefix} App`],
    UI: [`${workspacePrefix} UI`, `${workspacePrefix} Design System`],
    BACKEND: [`${workspacePrefix} API`, `${workspacePrefix} Service`],
    MOBILE: [`${workspacePrefix} Mobile`, `${workspacePrefix} Companion App`],
    DESKTOP: [`${workspacePrefix} Desktop`, `${workspacePrefix} Studio`],
  };

  const suggestions = [
    ...(preferredType ? typeSuggestions[preferredType] : []),
    `${workspacePrefix} App`,
    `${workspacePrefix} Service`,
    `${workspacePrefix} Platform`,
    'Main App',
    'Core Service',
    'Frontend',
    'Backend API',
  ];

  const available = suggestions.find(
    (s) => !existingNames.includes(s.toLowerCase())
  );

  return available || `Project ${workspace.ownedProjects.length + 1}`;
}

function inferProjectTypeFromDescription(params: {
  description?: string;
  preferredType?: CreateProjectBody['type'];
}): {
  inferredType: CreateProjectBody['type'];
  rationale: string;
  summary: string;
  recommendations: string[];
} {
  const { description, preferredType } = params;
  const normalizedDescription = description?.toLowerCase().trim() ?? '';

  if (!normalizedDescription) {
    const inferredType = preferredType ?? 'FULL_STACK';
    return {
      inferredType,
      rationale:
        'No description was provided, so the suggestion falls back to the selected project type.',
      summary: `Suggested as a ${inferredType.replace('_', ' ').toLowerCase()} project based on your current selection.`,
      recommendations: [
        'Add a short problem statement to improve the AI setup suggestion.',
        'Capture the primary users or interfaces before creating the project.',
      ],
    };
  }

  const keywordMap: Record<CreateProjectBody['type'], string[]> = {
    FULL_STACK: ['full stack', 'web app', 'platform', 'portal', 'dashboard', 'end-to-end'],
    UI: ['ui', 'design system', 'frontend', 'component', 'landing page', 'experience'],
    BACKEND: ['api', 'backend', 'service', 'auth', 'worker', 'queue', 'database', 'integration'],
    MOBILE: ['mobile', 'ios', 'android', 'react native', 'tablet', 'phone'],
    DESKTOP: ['desktop', 'electron', 'native app', 'workstation', 'local app'],
  };

  const scoredTypes = Object.entries(keywordMap).map(([type, keywords]) => {
    const score = keywords.reduce((total, keyword) => {
      return total + (normalizedDescription.includes(keyword) ? 1 : 0);
    }, 0);
    return { type: type as CreateProjectBody['type'], score };
  });

  const rankedTypes = scoredTypes.sort((left, right) => right.score - left.score);
  const topMatch = rankedTypes[0];
  const inferredType = topMatch.score > 0 ? topMatch.type : preferredType ?? 'FULL_STACK';

  const rationaleByType: Record<CreateProjectBody['type'], string> = {
    FULL_STACK:
      'The description points to a product flow that spans both user-facing experience and backend services.',
    UI: 'The description emphasizes interface design, components, or browser experience work.',
    BACKEND:
      'The description emphasizes APIs, services, authentication, data, or system integration work.',
    MOBILE:
      'The description references mobile platforms, handheld use cases, or native device workflows.',
    DESKTOP:
      'The description points to a workstation or locally installed desktop application.',
  };

  const recommendationsByType: Record<CreateProjectBody['type'], string[]> = {
    FULL_STACK: [
      'Define the primary frontend journeys and backend integration boundary.',
      'Start with one end-to-end workflow that proves the product spine.',
    ],
    UI: [
      'Capture the main screen hierarchy and component ownership before implementation.',
      'Reuse the existing design system first and only extend it where the product needs it.',
    ],
    BACKEND: [
      'Define the core API contract and integration boundaries before scaffolding.',
      'Identify persistence, auth, and observability requirements early.',
    ],
    MOBILE: [
      'Clarify the first-run mobile flow and offline expectations.',
      'Define the minimum supported platforms and device capabilities.',
    ],
    DESKTOP: [
      'Define the local workflow, packaging target, and runtime permissions.',
      'Map native integrations before choosing the shell architecture.',
    ],
  };

  return {
    inferredType,
    rationale: rationaleByType[inferredType],
    summary: `AI suggests a ${inferredType.replace('_', ' ').toLowerCase()} project for this description.`,
    recommendations: recommendationsByType[inferredType],
  };
}

async function getCrossWorkspaceRecommendations(params: {
  workspaceId: string;
  inferredType: CreateProjectBody['type'];
}): Promise<RelatedProjectRecommendation[]> {
  const relatedProjects = await prisma.project.findMany({
    where: {
      ownerWorkspaceId: { not: params.workspaceId },
      type: params.inferredType,
    },
    include: {
      ownerWorkspace: {
        select: {
          id: true,
          name: true,
        },
      },
    },
    orderBy: {
      updatedAt: 'desc',
    },
    take: 3,
  });

  return relatedProjects.map((project: {
    id: string;
    name: string;
    type: CreateProjectBody['type'];
    ownerWorkspaceId: string;
    ownerWorkspace: { name: string };
  }) => ({
    id: project.id,
    name: project.name,
    type: project.type,
    ownerWorkspaceId: project.ownerWorkspaceId,
    ownerWorkspaceName: project.ownerWorkspace.name,
  }));
}

async function buildProjectSetupSuggestion(
  body: ProjectSetupSuggestionBody
): Promise<ProjectSetupSuggestion> {
  const inferred = inferProjectTypeFromDescription({
    description: body.description,
    preferredType: body.preferredType,
  });
  const [suggestion, relatedProjects] = await Promise.all([
    suggestProjectName(body.workspaceId, inferred.inferredType),
    getCrossWorkspaceRecommendations({
      workspaceId: body.workspaceId,
      inferredType: inferred.inferredType,
    }),
  ]);

  return {
    suggestion,
    inferredType: inferred.inferredType,
    rationale: inferred.rationale,
    summary: inferred.summary,
    recommendations: inferred.recommendations,
    relatedProjects,
  };
}

async function logProjectCreatedAuditEvent(params: {
  request: FastifyRequest;
  project: AuditProjectSummary;
  workspace: AuditWorkspaceSummary;
}): Promise<void> {
  const { request, project, workspace } = params;

  if (!request.user?.userId) {
    return;
  }

  try {
    await getAuditService().log({
      action: 'PROJECT_CREATED',
      actor: request.user.userId,
      actorRole: request.user.role,
      resource: `/projects/${project.id}`,
      severity: 'info',
      details: `Project ${project.name} created in workspace ${workspace.name}`,
      ipAddress: request.ip,
      userAgent: request.headers['user-agent'],
      method: request.method,
      status: 201,
      tenantId: request.user.tenantId,
      success: true,
      metadata: {
        workspaceId: workspace.id,
        workspaceName: workspace.name,
        projectId: project.id,
        projectName: project.name,
        projectType: project.type,
      },
    });
  } catch (error) {
    request.log.warn(
      {
        error,
        projectId: project.id,
        workspaceId: workspace.id,
      },
      'Failed to write project creation audit event'
    );
  }
}

async function logProjectLifecycleEvent(params: {
  request: FastifyRequest;
  projectId: string;
  action: string;
  description: string;
  metadata?: Record<string, unknown>;
}): Promise<void> {
  if (!params.request.user?.userId) {
    return;
  }

  try {
    await prisma.lifecycleActivityLog.create({
      data: {
        projectId: params.projectId,
        userId: params.request.user.userId,
        action: params.action,
        description: params.description,
        metadata: params.metadata,
      },
    });
  } catch (error) {
    params.request.log.warn(
      {
        error,
        projectId: params.projectId,
        action: params.action,
      },
      'Failed to write project lifecycle activity event'
    );
  }
}

function mapLifecycleActivityEvent(entry: {
  id: string;
  action: string;
  description: string | null;
  timestamp: Date;
  userId: string;
}): ProjectActivityEvent {
  return {
    id: `lifecycle-${entry.id}`,
    source: 'lifecycle',
    action: entry.action,
    summary: entry.description ?? entry.action,
    timestamp: entry.timestamp.toISOString(),
    actor: entry.userId,
  };
}

function mapAuditActivityEvent(entry: {
  id: string;
  action: string;
  details: string | null;
  timestamp: Date;
  actor: string;
  severity: string | null;
  success: boolean | null;
}): ProjectActivityEvent {
  return {
    id: `audit-${entry.id}`,
    source: 'audit',
    action: entry.action,
    summary: entry.details ?? entry.action,
    timestamp: entry.timestamp.toISOString(),
    actor: entry.actor,
    severity: entry.severity,
    success: entry.success,
  };
}

// ============================================================================
// Routes
// ============================================================================

export default async function projectRoutes(fastify: FastifyInstance) {
  /**
   * GET /api/projects
   * List all projects in a workspace (owned + included)
   */
  fastify.get<{ Querystring: { workspaceId: string } }>(
    '/projects',
    async (request, reply) => {
      const { workspaceId } = request.query;

      if (!workspaceId) {
        return reply.status(400).send({ error: 'workspaceId is required' });
      }

      if (!request.user?.userId) {
        return reply.status(401).send({ error: 'Unauthorized' });
      }

      const workspaceRole = await getWorkspaceRole(request.user.userId, workspaceId);
      const ownedAccess = ownedProjectAccess(workspaceRole);
      const includedAccess = includedProjectAccess();

      // Get owned projects
      const ownedProjects = await prisma.project.findMany({
        where: { ownerWorkspaceId: workspaceId },
        orderBy: [{ isDefault: 'desc' }, { updatedAt: 'desc' }],
      });

      // Get included projects
      const includedProjects = await prisma.workspaceProject.findMany({
        where: { workspaceId },
        include: { project: true },
        orderBy: { addedAt: 'desc' },
      });

      return reply.send({
        owned: ownedProjects.map((project: Record<string, unknown>) => ({
          ...project,
          ...ownedAccess,
        })),
        included: includedProjects.map(
          (ip: { project: unknown; addedAt: Date }) => ({
            ...(ip.project as object),
            ...includedAccess,
            addedAt: ip.addedAt,
          })
        ),
      });
    }
  );

  /**
   * GET /api/projects/dashboard-actions
   * Return backend-owned blocker, review, and safe continuation cards for a workspace dashboard.
   */
  fastify.get<{ Querystring: { workspaceId: string } }>(
    '/projects/dashboard-actions',
    async (request, reply) => {
      const { workspaceId } = request.query;

      if (!workspaceId) {
        return reply.status(400).send({ error: 'workspaceId is required' });
      }

      if (!request.user?.userId) {
        return reply.status(401).send({ error: 'Unauthorized' });
      }

      const ownedProjects = await prisma.project.findMany({
        where: { ownerWorkspaceId: workspaceId },
        orderBy: [{ updatedAt: 'desc' }],
        take: 10,
      });
      const includedProjects = await prisma.workspaceProject.findMany({
        where: { workspaceId },
        include: { project: true },
        orderBy: { addedAt: 'desc' },
        take: 10,
      });

      const projectActions = [
        ...ownedProjects.flatMap(buildProjectDashboardActions),
        ...includedProjects.flatMap((entry: { project: Parameters<typeof buildProjectDashboardActions>[0] }) =>
          buildProjectDashboardActions(entry.project)
        ),
      ];

      return reply.send({
        workspaceId,
        blockedWork: projectActions.filter((action) => action.kind === 'blocker').slice(0, 6),
        reviewRequired: projectActions.filter((action) => action.kind === 'review').slice(0, 6),
        safeToContinue: projectActions.filter((action) => action.kind === 'safe-to-continue').slice(0, 6),
        generatedAt: new Date().toISOString(),
      });
    }
  );

  /**
   * POST /api/projects/:projectId/dashboard-actions/execute
   * Execute a safe dashboard action by recording the backend decision and returning the canonical phase target.
   */
  fastify.post<{
    Params: ProjectParams;
    Body: ExecuteProjectDashboardActionBody;
  }>(
    '/projects/:projectId/dashboard-actions/execute',
    async (request, reply) => {
      const { projectId } = request.params;
      const { workspaceId, actionId } = request.body;

      if (!workspaceId || !actionId) {
        return reply.status(400).send({ error: 'workspaceId and actionId are required' });
      }

      if (!request.user?.userId) {
        return reply.status(401).send({ error: 'Unauthorized' });
      }

      const project = await prisma.project.findUnique({
        where: { id: projectId },
      });

      if (!project) {
        return reply.status(404).send({ error: 'Project not found' });
      }

      if (project.ownerWorkspaceId !== workspaceId) {
        const inclusion = await prisma.workspaceProject.findUnique({
          where: {
            workspaceId_projectId: { workspaceId, projectId },
          },
        });
        if (!inclusion) {
          return reply.status(403).send({ error: 'Project is not visible in this workspace' });
        }
        return reply.status(403).send({
          error: 'Included projects are read-only in this workspace',
          reason: 'included_project_read_only',
          projectId,
          workspaceId,
        });
      }

      const availableActions = buildProjectDashboardActions(project);
      const action = availableActions.find((candidate) => candidate.id === actionId);
      if (!action) {
        return reply.status(404).send({ error: 'Dashboard action not found' });
      }

      if (!action.safeToRun) {
        return reply.status(409).send({
          error: 'Dashboard action requires review before execution',
          action,
        });
      }

      await logProjectLifecycleEvent({
        request,
        projectId,
        action: 'DASHBOARD_ACTION_EXECUTED',
        description: `Dashboard safe action executed: ${action.title}.`,
        metadata: {
          workspaceId,
          actionId,
          targetPhase: action.routePhase,
          source: action.source,
        },
      });

      const response: ExecuteProjectDashboardActionResponse = {
        projectId,
        actionId,
        outcome: 'opened-phase-cockpit',
        targetPhase: action.routePhase,
        targetPath: `/p/${projectId}/${action.routePhase}`,
        auditRecorded: true,
      };

      return reply.send(response);
    }
  );

  /**
   * GET /api/projects/:projectId/next-actions
   * Return authoritative next actions for a project based on lifecycle phase, artifact state, and readiness gates.
   * This is the single source of truth for dashboard next-best-action cards.
   */
  fastify.get<{ Params: ProjectParams; Querystring: { workspaceId?: string } }>(
    '/projects/:projectId/next-actions',
    async (request, reply) => {
      const { projectId } = request.params;
      const { workspaceId } = request.query;

      if (!request.user?.userId) {
        return reply.status(401).send({ error: 'Unauthorized' });
      }

      const project = await prisma.project.findUnique({
        where: { id: projectId },
      });

      if (!project) {
        return reply.status(404).send({ error: 'Project not found' });
      }

      // Verify workspace access if provided
      if (workspaceId) {
        if (project.ownerWorkspaceId !== workspaceId) {
          const inclusion = await prisma.workspaceProject.findUnique({
            where: {
              workspaceId_projectId: { workspaceId, projectId },
            },
          });
          if (!inclusion) {
            return reply.status(403).send({ error: 'Project is not visible in this workspace' });
          }
        }
      }

      // Build authoritative dashboard actions based on lifecycle phase and artifact state
      const dashboardActions = buildProjectDashboardActions(project);

      // Return only the titles of safe-to-continue and review actions
      // Blockers are handled separately in the dashboard-actions endpoint
      const nextActionTitles = dashboardActions
        .filter((action) => action.kind === 'safe-to-continue' || action.kind === 'review')
        .map((action) => action.title);

      return reply.send({
        projectId,
        nextActions: nextActionTitles,
        currentPhase: project.lifecyclePhase,
        aiNextActions: project.aiNextActions || [],
        generatedAt: new Date().toISOString(),
      });
    }
  );

  /**
   * GET /api/projects/:projectId
   * Get single project with ownership context (TRACK-004: Return capability contract)
   */
  fastify.get<{ Params: ProjectParams; Querystring: { workspaceId?: string } }>(
    '/projects/:projectId',
    async (request, reply) => {
      const { projectId } = request.params;
      const { workspaceId } = request.query;

      const project = await prisma.project.findUnique({
        where: { id: projectId },
        include: {
          ownerWorkspace: { select: { id: true, name: true } },
          documents: { select: { id: true, name: true } },
          pages: { select: { id: true, name: true, path: true } },
        },
      });

      if (!project) {
        return reply.status(404).send({ error: 'Project not found' });
      }

      // Determine if current workspace owns this project
      const isOwned = workspaceId
        ? project.ownerWorkspaceId === workspaceId
        : true;
      const access = isOwned
        ? ownedProjectAccess(
            workspaceId && request.user?.userId
              ? await getWorkspaceRole(request.user.userId, workspaceId)
              : 'OWNER'
          )
        : includedProjectAccess();

      // TRACK-004: Include capability contract in response
      const capabilities = await getProjectCapabilities(
        request.user?.userId || '',
        projectId,
        workspaceId
      );

      return reply.send({
        project: {
          ...project,
          ...access,
          // TRACK-004: Add backend capability contract
          userId: request.user?.userId,
          role: isOwned && request.user?.userId
            ? await getWorkspaceRole(request.user.userId, workspaceId || project.ownerWorkspaceId)
            : undefined,
          capabilities: {
            read: capabilities.read,
            create: capabilities.create,
            update: capabilities.update,
            delete: capabilities.delete,
            include: capabilities.include,
            comment: capabilities.comment,
          },
          isOwner: isOwned,
          isIncluded: !isOwned && workspaceId,
        },
      });
    }
  );

  fastify.get<{ Params: ProjectParams; Querystring: { workspaceId?: string } }>(
    '/projects/:projectId/activity',
    async (request, reply) => {
      const { projectId } = request.params;
      const { workspaceId } = request.query as { workspaceId?: string };

      // TRACK-002: Validate workspace access if workspaceId is provided
      // For now, we accept workspaceId but don't enforce it until full scoping is implemented
      const project = await prisma.project.findUnique({
        where: { id: projectId },
      });

      if (!project) {
        return reply.status(404).send({ error: 'Project not found' });
      }

      const [lifecycleEntries, auditEntries] = await Promise.all([
        prisma.lifecycleActivityLog.findMany({
          where: { projectId },
          orderBy: { timestamp: 'desc' },
          take: 10,
        }),
        prisma.auditLogEntry.findMany({
          where: { projectId },
          orderBy: { timestamp: 'desc' },
          take: 10,
        }),
      ]);

      const activity = [
        ...lifecycleEntries.map(mapLifecycleActivityEvent),
        ...auditEntries.map(mapAuditActivityEvent),
      ]
        .sort(
          (left, right) =>
            new Date(right.timestamp).getTime() -
            new Date(left.timestamp).getTime()
        )
        .slice(0, 10);

      return reply.send({ projectId, activity });
    }
  );

  /**
   * POST /api/projects
   * Create new project (owned by specified workspace)
   */
  fastify.post<{ Body: CreateProjectBody }>(
    '/projects',
    { preHandler: requirePermission('project', 'create') },
    async (request, reply) => {
      if (!request.user?.userId) {
        return reply.status(401).send({ error: 'Unauthorized' });
      }

      const { name, description, type, workspaceId } = request.body;
      const userId = request.user.userId;

      // Verify workspace exists
      const workspace = await prisma.workspace.findUnique({
        where: { id: workspaceId },
      });

      if (!workspace) {
        return reply.status(400).send({ error: 'Workspace not found' });
      }

      const project = await prisma.project.create({
        data: {
          name,
          description,
          type,
          status: 'DRAFT',
          lifecyclePhase: 'INTENT',
          ownerWorkspaceId: workspaceId,
          createdById: userId,
          isDefault: false,
          aiNextActions: generateNextActions({
            name,
            type,
            status: 'DRAFT',
            description,
          }),
        },
      });

      // Calculate initial health score
      const healthResult = await calculateHealthScore(project.id);
      await prisma.project.update({
        where: { id: project.id },
        data: { aiHealthScore: healthResult.score },
      });

      await logProjectCreatedAuditEvent({
        request,
        project: {
          id: project.id,
          name: project.name,
          type: project.type,
          ownerWorkspaceId: project.ownerWorkspaceId,
        },
        workspace: {
          id: workspace.id,
          name: workspace.name,
        },
      });

      await logProjectLifecycleEvent({
        request,
        projectId: project.id,
        action: 'PROJECT_CREATED',
        description: `Project ${project.name} created in INTENT with ${project.type.toLowerCase()} scope.`,
        metadata: {
          lifecyclePhase: 'INTENT',
          projectType: project.type,
          workspaceId,
        },
      });

      const workspaceRole = await getWorkspaceRole(userId, workspaceId);
      return reply.status(201).send({
        project: {
          ...project,
          aiHealthScore: healthResult.score,
          ...ownedProjectAccess(workspaceRole),
        },
      });
    }
  );

  /**
   * PATCH /api/projects/:projectId
   * Update project (only if owned by current workspace)
   */
  fastify.patch<{
    Params: ProjectParams;
    Body: UpdateProjectBody;
    Querystring: { workspaceId: string };
  }>(
    '/projects/:projectId',
    { preHandler: [requirePermission('project', 'update'), requireProjectWritable()] },
    async (request, reply) => {
      const { projectId } = request.params;
      const { workspaceId } = request.query;
      const { name, description, type, status, lifecyclePhase } = request.body;
      const normalizedLifecyclePhase = normalizeLifecyclePhase(lifecyclePhase);

      // Verify ownership
      const existing = await prisma.project.findUnique({
        where: { id: projectId },
      });

      if (!existing) {
        return reply.status(404).send({ error: 'Project not found' });
      }

      if (existing.ownerWorkspaceId !== workspaceId) {
        return reply
          .status(403)
          .send({ error: 'Cannot edit project you do not own' });
      }

      // P2-8: Fix No-op Lifecycle Saves - only update if phase has actually changed
      const hasLifecyclePhaseChanged =
        normalizedLifecyclePhase && normalizedLifecyclePhase !== existing.lifecyclePhase;

      const project = await prisma.project.update({
        where: { id: projectId },
        data: {
          ...(name && { name }),
          ...(description !== undefined && { description }),
          ...(type && { type }),
          ...(status && { status }),
          ...(hasLifecyclePhaseChanged && { lifecyclePhase: normalizedLifecyclePhase }),
          aiNextActions: generateNextActions({
            name: name || existing.name,
            type: type || existing.type,
            status: status || existing.status,
            description: description ?? existing.description,
          }),
        },
      });

      // Recalculate health score
      const healthResult = await calculateHealthScore(project.id);
      await prisma.project.update({
        where: { id: project.id },
        data: { aiHealthScore: healthResult.score },
      });

      const changeSummary: string[] = [];
      if (name && name !== existing.name) {
        changeSummary.push(`renamed to ${name}`);
      }
      if (type && type !== existing.type) {
        changeSummary.push(`retargeted as ${type.toLowerCase()}`);
      }
      if (status && status !== existing.status) {
        changeSummary.push(`status set to ${status.toLowerCase()}`);
      }
      if (normalizedLifecyclePhase && normalizedLifecyclePhase !== existing.lifecyclePhase) {
        changeSummary.push(`lifecycle moved to ${normalizedLifecyclePhase}`);
      }
      if (description !== undefined && description !== existing.description) {
        changeSummary.push('description updated');
      }

      if (changeSummary.length > 0) {
        await logProjectLifecycleEvent({
          request,
          projectId,
          action:
            normalizedLifecyclePhase && normalizedLifecyclePhase !== existing.lifecyclePhase
              ? 'PROJECT_LIFECYCLE_UPDATED'
              : 'PROJECT_UPDATED',
          description: `Project ${project.name}: ${changeSummary.join(', ')}.`,
          metadata: {
            previousLifecyclePhase: existing.lifecyclePhase,
            lifecyclePhase: project.lifecyclePhase,
            status: project.status,
          },
        });
      }

      return reply.send({
        project: { ...project, aiHealthScore: healthResult.score, isOwned: true },
      });
    }
  );

  /**
   * DELETE /api/projects/:projectId
   * Delete project (only if owned by current workspace)
   */
  fastify.delete<{
    Params: ProjectParams;
    Querystring: { workspaceId: string };
  }>(
    '/projects/:projectId',
    { preHandler: requirePermission('project', 'delete') },
    async (request, reply) => {
      const { projectId } = request.params;
      const { workspaceId } = request.query;

      // Verify ownership
      const existing = await prisma.project.findUnique({
        where: { id: projectId },
      });

      if (!existing) {
        return reply.status(404).send({ error: 'Project not found' });
      }

      if (existing.ownerWorkspaceId !== workspaceId) {
        return reply
          .status(403)
          .send({ error: 'Cannot delete project you do not own' });
      }

      if (existing.isDefault) {
        return reply
          .status(400)
          .send({ error: 'Cannot delete default project' });
      }

      await prisma.project.delete({
        where: { id: projectId },
      });

      return reply.status(204).send();
    }
  );

  /**
   * POST /api/projects/include
   * Include a project in a workspace (read-only)
   */
  fastify.post<{ Body: IncludeProjectBody }>(
    '/projects/include',
    { preHandler: requirePermission('project', 'update') },
    async (request, reply) => {
      if (!request.user?.userId) {
        return reply.status(401).send({ error: 'Unauthorized' });
      }

      const { workspaceId, projectId } = request.body;
      const userId = request.user.userId;

      // Verify project exists and is not already owned by workspace
      const project = await prisma.project.findUnique({
        where: { id: projectId },
      });

      if (!project) {
        return reply.status(404).send({ error: 'Project not found' });
      }

      if (project.ownerWorkspaceId === workspaceId) {
        return reply
          .status(400)
          .send({ error: 'Project is already owned by this workspace' });
      }

      // Check if already included
      const existing = await prisma.workspaceProject.findUnique({
        where: {
          workspaceId_projectId: { workspaceId, projectId },
        },
      });

      if (existing) {
        return reply
          .status(400)
          .send({ error: 'Project is already included in this workspace' });
      }

      const inclusion = await prisma.workspaceProject.create({
        data: {
          workspaceId,
          projectId,
          addedById: userId,
          aiInclusionReason: `Included for reference from ${project.name}`,
        },
        include: { project: true },
      });

      return reply.status(201).send({
        project: {
          ...inclusion.project,
          isOwned: false,
          addedAt: inclusion.addedAt,
        },
      });
    }
  );

  /**
   * DELETE /api/projects/include
   * Remove project inclusion from workspace
   */
  fastify.delete<{ Body: IncludeProjectBody }>(
    '/projects/include',
    { preHandler: requirePermission('project', 'update') },
    async (request, reply) => {
      const { workspaceId, projectId } = request.body;

      await prisma.workspaceProject.delete({
        where: {
          workspaceId_projectId: { workspaceId, projectId },
        },
      });

      return reply.status(204).send();
    }
  );

  /**
   * GET /api/projects/available-for-inclusion
   * List projects available to include in a workspace
   */
  fastify.get<{ Querystring: { workspaceId: string } }>(
    '/projects/available-for-inclusion',
    async (request, reply) => {
      if (!request.user?.userId) {
        return reply.status(401).send({ error: 'Unauthorized' });
      }

      const { workspaceId } = request.query;
      const userId = request.user.userId;

      // Get projects from other workspaces the user has access to
      const userWorkspaces = await prisma.workspaceMember.findMany({
        where: { userId },
        select: { workspaceId: true },
      });

      const workspaceIds = userWorkspaces
        .map((wm: { workspaceId: string }) => wm.workspaceId)
        .filter((id: string) => id !== workspaceId);

      // Get already included project IDs
      const alreadyIncluded = await prisma.workspaceProject.findMany({
        where: { workspaceId },
        select: { projectId: true },
      });
      const includedIds = alreadyIncluded.map(
        (ip: { projectId: string }) => ip.projectId
      );

      // Get available projects
      const projects = await prisma.project.findMany({
        where: {
          ownerWorkspaceId: { in: workspaceIds },
          id: { notIn: includedIds },
        },
        include: {
          ownerWorkspace: { select: { name: true } },
        },
        orderBy: { updatedAt: 'desc' },
        take: 50,
      });

      return reply.send({
        projects: projects.map((p: { ownerWorkspace: { name: string } }) => ({
          ...p,
          ownerWorkspaceName: p.ownerWorkspace.name,
        })),
      });
    }
  );

  /**
   * GET /api/projects/suggest-name
   * AI suggests project name for workspace
   */
  fastify.get<{
    Querystring: {
      workspaceId: string;
      type?: CreateProjectBody['type'];
    };
  }>(
    '/projects/suggest-name',
    async (request, reply) => {
      const { workspaceId, type } = request.query;

      const suggestion = await suggestProjectName(workspaceId, type);

      return reply.send({ suggestion });
    }
  );

  /**
   * POST /api/projects/setup-suggestion
   * Suggest the initial project setup from the description and workspace context.
   */
  fastify.post<{ Body: ProjectSetupSuggestionBody }>(
    '/projects/setup-suggestion',
    { preHandler: requirePermission('project', 'create') },
    async (request, reply) => {
      const { workspaceId } = request.body;

      if (!workspaceId) {
        return reply.status(400).send({ error: 'workspaceId is required' });
      }

      const workspace = await prisma.workspace.findUnique({
        where: { id: workspaceId },
        select: { id: true },
      });

      if (!workspace) {
        return reply.status(404).send({ error: 'Workspace not found' });
      }

      const suggestion = await buildProjectSetupSuggestion(request.body);
      return reply.send(suggestion);
    }
  );

  /**
   * POST /api/projects/:projectId/refresh-ai
   * Regenerate AI metrics for project
   */
  fastify.post<{ Params: ProjectParams }>(
    '/projects/:projectId/refresh-ai',
    { preHandler: requirePermission('ai', 'ai_generate') },
    async (request, reply) => {
      const { projectId } = request.params;

      const project = await prisma.project.findUnique({
        where: { id: projectId },
      });

      if (!project) {
        return reply.status(404).send({ error: 'Project not found' });
      }

      const [aiNextActions, healthResult] = await Promise.all([
        Promise.resolve(generateNextActions(project)),
        calculateHealthScore(projectId),
      ]);

      const updated = await prisma.project.update({
        where: { id: projectId },
        data: { aiNextActions, aiHealthScore: healthResult.score },
      });

      return reply.send({ project: updated });
    }
  );

  /**
   * GET /api/projects/:projectId/export
   * Export project as a ZIP file (TRACK-003: Backend-authorized, scoped, audited)
   */
  fastify.get<{
    Params: ProjectParams;
    Querystring: { workspaceId?: string };
  }>(
    '/projects/:projectId/export',
    { preHandler: [requirePermission('project', 'read'), requireProjectReadable()] },
    async (request, reply) => {
      const { projectId } = request.params;
      const { workspaceId } = request.query;
      const userId = request.user?.userId;

      if (!userId) {
        return reply.status(401).send({ error: 'Unauthorized' });
      }

      if (!workspaceId) {
        return reply.status(400).send({ error: 'workspaceId is required for export' });
      }

      // Verify project access and ownership
      const project = await prisma.project.findUnique({
        where: { id: projectId },
      });

      if (!project) {
        return reply.status(404).send({ error: 'Project not found' });
      }

      // TRACK-003: Verify workspace access - project must be owned by or included in the workspace
      // For now, we accept the workspaceId but should enforce it
      if (project.ownerWorkspaceId !== workspaceId) {
        // Check if project is included in the workspace
        const inclusion = await prisma.workspaceProject.findFirst({
          where: { projectId, workspaceId },
        });
        if (!inclusion) {
          return reply.status(403).send({
            error: 'Forbidden',
            message: 'Project is not accessible from this workspace',
          });
        }
      }

      // TRACK-003: Generate actual project export ZIP
      // For now, return a pending response
      try {
        // Audit log export attempt
        await logProjectLifecycleEvent({
          request,
          projectId,
          action: 'PROJECT_EXPORT_REQUESTED',
          description: `Project export requested by user ${userId}`,
          metadata: { workspaceId, userId },
        });

        // Placeholder: Return empty ZIP for now
        // TRACK-003: Implement actual project export generation
        reply.header('Content-Type', 'application/zip');
        reply.header('Content-Disposition', `attachment; filename="project-${projectId}.zip"`);
        return reply.send(Buffer.from(''));
      } catch (error) {
        console.error('[Export] Failed:', error);
        return reply.status(500).send({
          error: 'Export failed',
          message: error instanceof Error ? error.message : 'Unknown error',
        });
      }
    }
  );

  /**
   * GET /api/projects/:projectId/capabilities
   * Get capability payload for a project - what actions the current user can perform
   */
  fastify.get<{
    Params: ProjectParams;
    Querystring: { workspaceId?: string };
  }>(
    '/projects/:projectId/capabilities',
    { preHandler: requireProjectReadable() },
    async (request, reply) => {
      const { projectId } = request.params;
      const { workspaceId } = request.query;

      if (!request.user?.userId) {
        return reply.status(401).send({ error: 'Unauthorized' });
      }

      const capabilities = await getProjectCapabilities(
        request.user.userId,
        projectId,
        workspaceId
      );

      return reply.send({
        projectId,
        capabilities,
      });
    }
  );

  /**
   * GET /api/projects/:projectId/ai-cost
   * Returns aggregated AI agent run counts and estimated cost for a project.
   * Costs are derived from the AgentRun log; model rates are indicative.
   */
  fastify.get<{ Params: ProjectParams }>(
    '/projects/:projectId/ai-cost',
    { preHandler: requireProjectReadable() },
    async (request: FastifyRequest<{ Params: ProjectParams }>, reply: FastifyReply) => {
      const { projectId } = request.params;

      const runs = await prisma.agentRun.findMany({
        where: { projectId },
        select: { id: true, agentName: true, status: true, createdAt: true },
        orderBy: { createdAt: 'desc' },
        take: 500,
      });

      const succeededRuns = runs.filter((run: { status: string }) => run.status === 'SUCCEEDED');
      const totalRuns = runs.length;
      const succeededCount = succeededRuns.length;

      // Indicative per-run cost: flat rate of $0.005 per succeeded agent call.
      // Replace with summed AIMetric.costUSD once the projectId FK is added.
      const COST_PER_RUN_USD = 0.005;
      const estimatedCostUSD = succeededCount * COST_PER_RUN_USD;

      // Breakdown by agent type
      const byAgent: Record<string, { count: number; estimatedCostUSD: number }> = {};
      for (const run of succeededRuns) {
        const key = run.agentName;
        const entry = byAgent[key] ?? { count: 0, estimatedCostUSD: 0 };
        entry.count += 1;
        entry.estimatedCostUSD = Math.round((entry.count * COST_PER_RUN_USD) * 10000) / 10000;
        byAgent[key] = entry;
      }

      return reply.send({
        projectId,
        totalRuns,
        succeededRuns: succeededCount,
        estimatedCostUSD: Math.round(estimatedCostUSD * 10000) / 10000,
        currency: 'USD',
        costBasis: 'indicative',
        byAgent,
      });
    }
  );
}
