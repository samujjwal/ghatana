/**
 * Trust Center Page
 *
 * Simplified governance and compliance page.
 * Replaces generic policy-management framing with an action-backed Trust Center.
 *
 * Features:
 * - Live governance operations and lifecycle truth
 * - Visual compliance status
 * - AI-suggested policies
 * - Audit trail
 *
 * @doc.type page
 * @doc.purpose Simplified governance and compliance
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Button, IconButton, Input } from '@ghatana/design-system';
import { useCapabilityGate } from '../hooks/useCapabilityGate';
import { useOperationHistory } from '../hooks/useOperationHistory';
import { OperationHistory, OperationHistoryAlert } from '../components/common/OperationHistory';
import { useOperations } from '../contexts/OperationsContext';
import {
  Shield,
  CheckCircle,
  AlertTriangle,
  Lock,
  Eye,
  FileText,
  Clock,
  ChevronRight,
  Sparkles,
  Search,
  Filter,
  RefreshCw,
  Loader2,
  X,
  Trash2,
} from 'lucide-react';
import { cn } from '../lib/theme';
import { CommandBar, CommandBarTrigger, AmbientIntelligenceBar } from '../components/core';
import { toast } from 'sonner';
import {
  governanceService,
  type Policy as GovPolicy,
  type AuditLog,
  type GovernanceLifecycleSurface,
  type GovernanceOperationalAction,
  type GovernanceRecommendation,
  type RetentionPolicyResult,
  type RetentionPurgeDryRunResult,
  type RetentionTier,
} from '../api/governance.service';

/**
 * Compliance status
 */
type ComplianceStatus = 'compliant' | 'warning' | 'non-compliant' | 'pending';

/**
 * Display-friendly Policy shape (mapped from GovPolicy)
 */
interface Policy {
  id: string;
  name: string;
  description: string;
  type: 'GDPR' | 'HIPAA' | 'SOC2' | 'PCI' | 'CUSTOM';
  status: ComplianceStatus;
  appliedTo: number;
  lastChecked: string;
  aiSuggested?: boolean;
}

/**
 * Audit event interface (display-friendly, mapped from AuditLog)
 */
interface AuditEvent {
  id: string;
  action: string;
  resource: string;
  user: string;
  timestamp: string;
  status: 'success' | 'failed' | 'pending';
}

interface AuditTimelineEntry {
  key: string;
  action: string;
  resource: string;
  lastUser: string;
  lastTimestamp: string;
  status: 'success' | 'failed' | 'pending';
  count: number;
  events: AuditEvent[];
}

type GovernanceQuickAction = 'classify-retention' | 'redact-pii' | 'purge-retention';

interface RetentionClassificationFormState {
  collection: string;
  tier: RetentionTier;
  reason: string;
  piiFields: string;
}

interface RedactionFormState {
  collection: string;
  entityId: string;
  fields: string;
  reason: string;
}

interface GovernanceActionSummary {
  title: string;
  detail: string;
  tone: 'success' | 'warning';
}

interface LifecycleStatusConfig {
  label: string;
  badgeClassName: string;
  panelClassName: string;
}

interface RetentionPurgeFormState {
  collection: string;
}

interface RetentionPurgePreview {
  policy: RetentionPolicyResult;
  dryRun: RetentionPurgeDryRunResult;
}

interface PolicyFormState {
  name: string;
  type: 'SECURITY' | 'PRIVACY' | 'RETENTION' | 'ACCESS' | 'QUALITY';
  description: string;
  enabled: boolean;
}

const DEFAULT_CLASSIFICATION_FORM: RetentionClassificationFormState = {
  collection: '',
  tier: 'compliance',
  reason: 'GDPR Article 17 review',
  piiFields: '',
};

const DEFAULT_REDACTION_FORM: RedactionFormState = {
  collection: '',
  entityId: '',
  fields: '',
  reason: 'PII redaction request',
};

const DEFAULT_PURGE_FORM: RetentionPurgeFormState = {
  collection: '',
};

const DEFAULT_POLICY_FORM: PolicyFormState = {
  name: '',
  type: 'PRIVACY',
  description: '',
  enabled: true,
};

function parseCommaSeparatedValues(value: string): string[] | undefined {
  const normalized = value
    .split(',')
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0);

  return normalized.length > 0 ? normalized : undefined;
}

/** Map governance Policy type → display label */
function mapPolicyType(type: GovPolicy['type']): Policy['type'] {
  switch (type) {
    case 'PRIVACY': return 'GDPR';
    case 'RETENTION': return 'HIPAA';
    case 'SECURITY': return 'SOC2';
    case 'ACCESS': return 'PCI';
    default: return 'CUSTOM';
  }
}

/** Map governance Policy → display Policy */
function mapPolicy(p: GovPolicy): Policy {
  return {
    id: p.id,
    name: p.name,
    description: Object.values(p.metadata ?? {}).join(' ') || p.type,
    type: mapPolicyType(p.type),
    status: p.enabled ? 'compliant' : 'pending',
    appliedTo: (p.scope?.datasets?.length ?? 0) + (p.scope?.users?.length ?? 0),
    lastChecked: new Date(p.updatedAt).toLocaleString(),
  };
}

/** Map AuditLog → display AuditEvent */
function mapAuditLog(log: AuditLog): AuditEvent {
  return {
    id: log.id,
    action: log.action,
    resource: `${log.resourceType}:${log.resourceId}`,
    user: log.userName ?? log.userId,
    timestamp: new Date(log.timestamp).toLocaleString(),
    status: log.outcome === 'SUCCESS' ? 'success' : log.outcome === 'FAILURE' ? 'failed' : 'pending',
  };
}

function buildAuditTimeline(events: AuditEvent[]): AuditTimelineEntry[] {
  const grouped = new Map<string, AuditTimelineEntry>();

  for (const event of events) {
    const key = `${event.action}::${event.resource}`;
    const existing = grouped.get(key);
    if (!existing) {
      grouped.set(key, {
        key,
        action: event.action,
        resource: event.resource,
        lastUser: event.user,
        lastTimestamp: event.timestamp,
        status: event.status,
        count: 1,
        events: [event],
      });
      continue;
    }

    existing.count += 1;
    existing.events.push(event);
    existing.lastUser = event.user;
    existing.lastTimestamp = event.timestamp;
    if (event.status === 'failed') {
      existing.status = 'failed';
    } else if (existing.status !== 'failed' && event.status === 'pending') {
      existing.status = 'pending';
    }
  }

  return Array.from(grouped.values()).sort((left, right) =>
    right.lastTimestamp.localeCompare(left.lastTimestamp),
  );
}

/**
 * Status badge configurations
 */
const STATUS_CONFIG: Record<
  ComplianceStatus,
  { icon: React.ReactNode; color: string; label: string }
> = {
  compliant: {
    icon: <CheckCircle className="h-4 w-4" />,
    color: 'text-green-600 bg-green-100 dark:bg-green-900/30 dark:text-green-400',
    label: 'Compliant',
  },
  warning: {
    icon: <AlertTriangle className="h-4 w-4" />,
    color: 'text-amber-600 bg-amber-100 dark:bg-amber-900/30 dark:text-amber-400',
    label: 'Warning',
  },
  'non-compliant': {
    icon: <AlertTriangle className="h-4 w-4" />,
    color: 'text-red-600 bg-red-100 dark:bg-red-900/30 dark:text-red-400',
    label: 'Non-Compliant',
  },
  pending: {
    icon: <Clock className="h-4 w-4" />,
    color: 'text-gray-600 bg-gray-100 dark:bg-gray-800 dark:text-gray-400',
    label: 'Pending',
  },
};

/**
 * Policy Type Badges
 */
const POLICY_TYPE_COLORS: Record<string, string> = {
  GDPR: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
  HIPAA: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300',
  SOC2: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
  PCI: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-300',
  CUSTOM: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
};

/**
 * Compliance Score Card
 */
function ComplianceScoreCard({
  score,
  total,
  compliant,
  warnings,
}: {
  score: number;
  total: number;
  compliant: number;
  warnings: number;
}) {
  return (
    <div className="bg-gradient-to-br from-green-50 to-emerald-50 dark:from-green-900/20 dark:to-emerald-900/20 border border-green-200 dark:border-green-800 rounded-2xl p-6">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm text-gray-500 mb-1">Overall Compliance Score</p>
          <div className="flex items-baseline gap-2">
            <span className="text-4xl font-bold text-green-600 dark:text-green-400">
              {score}%
            </span>
            <span className="text-sm text-gray-500">
              {compliant}/{total} policies compliant
            </span>
          </div>
        </div>
        <div className="flex items-center justify-center w-20 h-20 rounded-full bg-white dark:bg-gray-800 shadow-lg">
          <Shield className="h-10 w-10 text-green-500" />
        </div>
      </div>
      {warnings > 0 && (
        <div className="mt-4 pt-4 border-t border-green-200 dark:border-green-800">
          <div className="flex items-center gap-2 text-amber-600 dark:text-amber-400">
            <AlertTriangle className="h-4 w-4" />
            <span className="text-sm">{warnings} warning{warnings > 1 ? 's' : ''} need attention</span>
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * Policy Card
 */
function PolicyCard({ policy, 'data-testid': dataTestId }: { policy: Policy; 'data-testid'?: string }) {
  const statusConfig = STATUS_CONFIG[policy.status];

  return (
    <div
      data-testid={dataTestId}
      className={cn(
        'bg-white dark:bg-gray-800',
        'border border-gray-200 dark:border-gray-700',
        'rounded-xl p-4',
        'hover:shadow-md transition-shadow'
      )}
    >
      <div className="flex items-start gap-4">
        <div className="p-2 bg-gray-100 dark:bg-gray-700 rounded-lg">
          <Shield className="h-5 w-5 text-gray-600 dark:text-gray-400" />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <h3 className="font-medium text-gray-900 dark:text-gray-100">
              {policy.name}
            </h3>
            {policy.aiSuggested && (
              <span className="inline-flex items-center gap-1 px-1.5 py-0.5 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 text-xs rounded">
                <Sparkles className="h-3 w-3" />
                AI Suggested
              </span>
            )}
          </div>
          <p className="text-sm text-gray-500 mb-2">{policy.description}</p>
          <div className="flex items-center gap-3">
            <span
              className={cn('px-2 py-0.5 rounded text-xs font-medium', POLICY_TYPE_COLORS[policy.type])}
            >
              {policy.type}
            </span>
            <span className="text-xs text-gray-400">
              Applied to {policy.appliedTo} resources
            </span>
            <span className="text-xs text-gray-400">
              Checked {policy.lastChecked}
            </span>
          </div>
        </div>
        <span
          className={cn(
            'inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium',
            statusConfig.color
          )}
        >
          {statusConfig.icon}
          {statusConfig.label}
        </span>
      </div>
    </div>
  );
}

const LIFECYCLE_STATUS_CONFIG: Record<GovernanceLifecycleSurface['status'], LifecycleStatusConfig> = {
  'live-action': {
    label: 'Live action',
    badgeClassName: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
    panelClassName: 'border-green-200 bg-green-50/70 dark:border-green-900/40 dark:bg-green-900/10',
  },
  'derived-read-only': {
    label: 'Derived read-only',
    badgeClassName: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300',
    panelClassName: 'border-amber-200 bg-amber-50/70 dark:border-amber-900/40 dark:bg-amber-900/10',
  },
  unavailable: {
    label: 'Unavailable',
    badgeClassName: 'bg-gray-200 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
    panelClassName: 'border-gray-200 bg-gray-50/80 dark:border-gray-700 dark:bg-gray-900/40',
  },
};

function LifecycleTruthCard({
  surface,
  onAction,
}: {
  surface: GovernanceLifecycleSurface;
  onAction: (action: GovernanceOperationalAction) => void;
}) {
  const statusConfig = LIFECYCLE_STATUS_CONFIG[surface.status];

  return (
    <article
      data-testid={`trust-lifecycle-${surface.id}`}
      className={cn('rounded-xl border p-4', statusConfig.panelClassName)}
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">{surface.title}</h3>
          <p className="mt-2 text-sm text-gray-600 dark:text-gray-300">{surface.summary}</p>
        </div>
        <span className={cn('rounded-full px-2 py-1 text-[11px] font-semibold uppercase tracking-wide', statusConfig.badgeClassName)}>
          {statusConfig.label}
        </span>
      </div>
      <ul className="mt-4 space-y-2 text-xs text-gray-600 dark:text-gray-300">
        {surface.evidence.map((detail) => (
          <li key={detail} className="rounded-lg bg-white/70 px-3 py-2 dark:bg-gray-950/40">
            {detail}
          </li>
        ))}
      </ul>
      {surface.action && surface.actionLabel ? (
        <Button
          variant="outline"
          size="sm"
          onClick={() => onAction(surface.action!)}
          data-testid={`trust-lifecycle-action-${surface.id}`}
          className="mt-4"
        >
          {surface.actionLabel}
        </Button>
      ) : null}
    </article>
  );
}

/**
 * Quick Apply Command Card
 */
function QuickApplyCard({
  title,
  description,
  icon,
  onClick,
  'data-testid': dataTestId,
}: {
  title: string;
  description: string;
  icon: React.ReactNode;
  onClick: () => void;
  'data-testid'?: string;
}) {
  return (
    <button
      onClick={onClick}
      data-testid={dataTestId}
      className={cn(
        'flex items-center gap-3 p-4',
        'bg-white dark:bg-gray-800',
        'border border-gray-200 dark:border-gray-700',
        'rounded-xl text-left',
        'hover:border-primary-300 dark:hover:border-primary-700',
        'hover:shadow-md transition-all'
      )}
    >
      <div className="p-2 bg-primary-100 dark:bg-primary-900/30 rounded-lg">
        {icon}
      </div>
      <div className="flex-1">
        <h3 className="text-sm font-medium text-gray-900 dark:text-gray-100">
          {title}
        </h3>
        <p className="text-xs text-gray-500">{description}</p>
      </div>
      <ChevronRight className="h-5 w-5 text-gray-300" />
    </button>
  );
}

function RecommendationCard({
  recommendation,
  onApply,
}: {
  recommendation: GovernanceRecommendation;
  onApply: () => void;
}) {
  return (
    <article
      className="rounded-xl border border-purple-200 bg-purple-50/60 p-4 dark:border-purple-900/40 dark:bg-purple-900/10"
      data-testid={`trust-recommendation-${recommendation.id}`}
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="inline-flex items-center gap-2 rounded-full bg-white px-2 py-1 text-[11px] font-semibold uppercase tracking-wide text-purple-700 dark:bg-gray-900 dark:text-purple-300">
            <Sparkles className="h-3 w-3" />
            {recommendation.priority === 'high' ? 'High-priority recommendation' : 'Suggested follow-up'}
          </div>
          <h3 className="mt-3 text-sm font-semibold text-gray-900 dark:text-gray-100">{recommendation.title}</h3>
          <p className="mt-2 text-sm text-gray-600 dark:text-gray-300">{recommendation.summary}</p>
        </div>
        <Button
          variant="solid"
          size="sm"
          onClick={onApply}
          data-testid={`trust-recommendation-apply-${recommendation.id}`}
        >
          {recommendation.actionLabel}
        </Button>
      </div>
      <ul className="mt-4 space-y-2 text-xs text-purple-900 dark:text-purple-100">
        {recommendation.evidence.map((detail) => (
          <li key={detail} className="rounded-lg bg-white/80 px-3 py-2 dark:bg-gray-900/60">
            {detail}
          </li>
        ))}
      </ul>
    </article>
  );
}

/**
 * Audit Log Item
 */
function AuditLogItem({ event }: { event: AuditEvent }) {
  const statusColors = {
    success: 'text-green-500',
    failed: 'text-red-500',
    pending: 'text-gray-400',
  };

  return (
    <div className="flex items-center gap-3 py-2">
      {event.status === 'success' ? (
        <CheckCircle className={cn('h-4 w-4', statusColors[event.status])} />
      ) : event.status === 'failed' ? (
        <AlertTriangle className={cn('h-4 w-4', statusColors[event.status])} />
      ) : (
        <Clock className={cn('h-4 w-4', statusColors[event.status])} />
      )}
      <div className="flex-1 min-w-0">
        <p className="text-sm text-gray-900 dark:text-gray-100 truncate">
          {event.action}
        </p>
        <p className="text-xs text-gray-500">
          {event.resource} • {event.user}
        </p>
      </div>
      <span className="text-xs text-gray-400">{event.timestamp}</span>
    </div>
  );
}

function AuditTimelineCard({ entry, 'data-testid': dataTestId }: { entry: AuditTimelineEntry; 'data-testid'?: string }) {
  const statusColors = {
    success: 'text-green-500 bg-green-50 dark:bg-green-900/20',
    failed: 'text-red-500 bg-red-50 dark:bg-red-900/20',
    pending: 'text-amber-500 bg-amber-50 dark:bg-amber-900/20',
  };

  return (
    <article data-testid={dataTestId} className="rounded-xl border border-gray-200 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900/40">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-sm font-medium text-gray-900 dark:text-gray-100">{entry.action}</h3>
          <p className="mt-1 text-xs text-gray-500">{entry.resource}</p>
        </div>
        <span className={cn('rounded-full px-2 py-1 text-[11px] font-medium', statusColors[entry.status])}>
          {entry.count} event{entry.count > 1 ? 's' : ''}
        </span>
      </div>
      <p className="mt-3 text-xs text-gray-500">
        Last updated by {entry.lastUser} at {entry.lastTimestamp}
      </p>
      <div className="mt-3 space-y-2">
        {entry.events.slice(0, 3).map((event) => (
          <AuditLogItem key={event.id} event={event} />
        ))}
      </div>
    </article>
  );
}

/**
 * Trust Center Page
 */
export function TrustCenter() {
  const [searchQuery, setSearchQuery] = useState('');
  const [activeQuickAction, setActiveQuickAction] = useState<GovernanceQuickAction | 'create-policy' | null>(null);
  const [classificationForm, setClassificationForm] = useState<RetentionClassificationFormState>(DEFAULT_CLASSIFICATION_FORM);
  const [redactionForm, setRedactionForm] = useState<RedactionFormState>(DEFAULT_REDACTION_FORM);
  const [purgeForm, setPurgeForm] = useState<RetentionPurgeFormState>(DEFAULT_PURGE_FORM);
  const [policyForm, setPolicyForm] = useState<PolicyFormState>(DEFAULT_POLICY_FORM);
  const [editingPolicyId, setEditingPolicyId] = useState<string | null>(null);
  const [actionSummary, setActionSummary] = useState<GovernanceActionSummary | null>(null);
  const [purgePreview, setPurgePreview] = useState<RetentionPurgePreview | null>(null);

  const canWriteGovernance = useCapabilityGate(
    ['governance.write', 'governance.policy.write', 'policy.write'],
    'active',
  );

  const { records: opHistory, addRecord: trackOp, updateOutcome: resolveOp, clearRecords: clearOpHistory } = useOperationHistory();
  const { startJob, completeJob } = useOperations();

  const { data: rawPolicies = [], isLoading: policiesLoading, refetch: refetchPolicies } = useQuery({
    queryKey: ['governance-policies'],
    queryFn: () => governanceService.getPolicies(),
    staleTime: 60_000,
  });

  const { data: rawAuditLogs = [], isLoading: auditLoading, refetch: refetchAuditLogs } = useQuery({
    queryKey: ['audit-logs'],
    queryFn: () => governanceService.getAuditLogs(undefined, undefined, 10),
    staleTime: 60_000,
  });

  const { data: complianceReport, refetch: refetchComplianceReport } = useQuery({
    queryKey: ['compliance-report'],
    queryFn: () => governanceService.getComplianceReport('30d'),
    staleTime: 120_000,
  });

  const { data: recommendations = [], isLoading: recommendationsLoading } = useQuery({
    queryKey: ['governance-recommendations'],
    queryFn: () => governanceService.getRecommendations(),
    staleTime: 120_000,
  });

  const { data: lifecycleSurfaces = [], isLoading: lifecycleLoading } = useQuery({
    queryKey: ['governance-lifecycle-surfaces'],
    queryFn: () => governanceService.getLifecycleSurfaces(),
    staleTime: 120_000,
  });

  const refreshGovernanceData = (): void => {
    void refetchPolicies();
    void refetchAuditLogs();
    void refetchComplianceReport();
  };

  const closeQuickAction = (): void => {
    setActiveQuickAction(null);
    setPurgePreview(null);
  };

  const triggerOperationalAction = (action: GovernanceOperationalAction): void => {
    if (action === 'classify-retention') {
      setActiveQuickAction('classify-retention');
      return;
    }

    if (action === 'redact-pii') {
      setActiveQuickAction('redact-pii');
      return;
    }

    if (action === 'purge-retention') {
      setActiveQuickAction('purge-retention');
      return;
    }

    if (action === 'refresh-compliance') {
      complianceRefreshMutation.mutate();
      return;
    }

    showAccessReviewBoundary();
  };

  const showAccessReviewBoundary = (): void => {
    toast.info('Access review remains derived from audit and compliance summary data in this deployment.');
    setActionSummary({
      title: 'Access review remains read-only',
      detail: 'The launcher currently exposes audit posture only. Access request review mutations are planned for the Data Cloud governance backlog in Q3 2026.',
      tone: 'warning',
    });
  };

  const classifyMutation = useMutation({
    mutationFn: () => governanceService.classifyRetention({
      collection: classificationForm.collection,
      tier: classificationForm.tier,
      reason: classificationForm.reason,
      piiFields: parseCommaSeparatedValues(classificationForm.piiFields),
    }),
    onMutate: () => {
      const jobId = startJob(`Classify retention: ${classificationForm.collection}`);
      const op = trackOp({ action: 'Classify retention', resource: classificationForm.collection, outcome: 'pending' });
      return { op, jobId };
    },
    onSuccess: (result, _vars, ctx) => {
      if (ctx?.op) resolveOp(ctx.op.id, 'success');
      if (ctx?.jobId) completeJob(ctx.jobId, 'success');
      toast.success(`Retention tier applied to ${result.collection}`);
      setActionSummary({
        title: 'Retention policy applied',
        detail: `${result.collection} is now classified as ${result.tier} for ${result.reason}.`,
        tone: 'success',
      });
      setClassificationForm(DEFAULT_CLASSIFICATION_FORM);
      closeQuickAction();
      refreshGovernanceData();
    },
    onError: (error, _vars, ctx) => {
      const msg = error instanceof Error ? error.message : 'Failed';
      if (ctx?.op) resolveOp(ctx.op.id, 'failure', msg);
      if (ctx?.jobId) completeJob(ctx.jobId, 'failure', msg);
      toast.error(error instanceof Error ? error.message : 'Failed to classify retention policy');
    },
  });

  const redactionMutation = useMutation({
    mutationFn: () => governanceService.redactEntity({
      collection: redactionForm.collection,
      entityId: redactionForm.entityId,
      fields: parseCommaSeparatedValues(redactionForm.fields),
      reason: redactionForm.reason,
    }),
    onMutate: () => {
      const jobId = startJob(`Redact PII: ${redactionForm.collection}/${redactionForm.entityId}`);
      const op = trackOp({ action: 'Redact PII', resource: `${redactionForm.collection}/${redactionForm.entityId}`, outcome: 'pending' });
      return { op, jobId };
    },
    onSuccess: (result, _vars, ctx) => {
      if (ctx?.op) resolveOp(ctx.op.id, 'success');
      if (ctx?.jobId) completeJob(ctx.jobId, 'success');
      const redactedCount = result.redactedFields.length;
      toast.success(redactedCount > 0 ? `Redacted ${redactedCount} field${redactedCount > 1 ? 's' : ''}` : 'No additional fields required redaction');
      setActionSummary({
        title: result.status === 'REDACTED' ? 'PII redaction completed' : 'PII redaction verified',
        detail: `${result.collection}/${result.entityId} processed with ${result.redactedFields.length} redacted field${result.redactedFields.length === 1 ? '' : 's'}.`,
        tone: result.status === 'REDACTED' ? 'success' : 'warning',
      });
      setRedactionForm(DEFAULT_REDACTION_FORM);
      closeQuickAction();
      refreshGovernanceData();
    },
    onError: (error, _vars, ctx) => {
      const msg = error instanceof Error ? error.message : 'Failed';
      if (ctx?.op) resolveOp(ctx.op.id, 'failure', msg);
      if (ctx?.jobId) completeJob(ctx.jobId, 'failure', msg);
      toast.error(error instanceof Error ? error.message : 'Failed to redact PII fields');
    },
  });

  const complianceRefreshMutation = useMutation({
    mutationFn: () => governanceService.generateComplianceReport('30d'),
    onSuccess: (result) => {
      toast.success(`Compliance summary refreshed: ${result.reportId}`);
      setActionSummary({
        title: 'Compliance summary refreshed',
        detail: `Report ${result.reportId} is ready for operator review.`,
        tone: 'success',
      });
      refreshGovernanceData();
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : 'Failed to refresh compliance summary');
    },
  });

  const purgeDryRunMutation = useMutation({
    mutationFn: async (): Promise<RetentionPurgePreview> => {
      const collection = purgeForm.collection.trim();
      const [policy, dryRun] = await Promise.all([
        governanceService.getRetentionPolicy(collection),
        governanceService.purgeRetentionDryRun({ collection }),
      ]);
      return { policy, dryRun };
    },
    onSuccess: (result) => {
      setPurgePreview(result);
      toast.success(`Dry run completed for ${result.dryRun.collection}`);
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : 'Failed to run retention purge dry run');
    },
  });

  const purgeExecuteMutation = useMutation({
    mutationFn: async () => {
      if (!purgePreview) {
        throw new Error('Run a dry run before executing a purge.');
      }
      return governanceService.purgeRetentionExecute({
        collection: purgePreview.dryRun.collection,
        confirmationToken: purgePreview.dryRun.confirmationToken,
      });
    },
    onMutate: () => {
      const resource = purgePreview?.dryRun.collection ?? 'unknown';
      const jobId = startJob(`Purge retention: ${resource}`);
      const op = trackOp({ action: 'Purge retention', resource, outcome: 'pending' });
      return { op, jobId };
    },
    onSuccess: (result, _vars, ctx) => {
      if (ctx?.op) resolveOp(ctx.op.id, 'success');
      if (ctx?.jobId) completeJob(ctx.jobId, 'success');
      toast.success(`Retention purge completed for ${result.collection}`);
      setActionSummary({
        title: 'Retention purge completed',
        detail: `${result.collection} purge finished with ${result.deletedRows} deleted row${result.deletedRows === 1 ? '' : 's'}.`,
        tone: result.deletedRows > 0 ? 'success' : 'warning',
      });
      setPurgeForm(DEFAULT_PURGE_FORM);
      closeQuickAction();
      refreshGovernanceData();
    },
    onError: (error, _vars, ctx) => {
      const msg = error instanceof Error ? error.message : 'Failed';
      if (ctx?.op) resolveOp(ctx.op.id, 'failure', msg);
      if (ctx?.jobId) completeJob(ctx.jobId, 'failure', msg);
      toast.error(error instanceof Error ? error.message : 'Failed to execute retention purge');
    },
  });

  // DC-P1-009: Policy CRUD lifecycle mutations
  const createPolicyMutation = useMutation({
    mutationFn: (policy: Partial<Policy>) => {
      // Map UI Policy type to governance service Policy type
      const servicePolicy: Partial<import('../api/governance.service').Policy> = {
        ...policy,
        type: policy.type as any, // Type mapping handled by service
      };
      return governanceService.createPolicy(servicePolicy);
    },
    onMutate: () => {
      const jobId = startJob('Create policy');
      const op = trackOp({ action: 'Create policy', resource: 'governance/policies', outcome: 'pending' });
      return { op, jobId };
    },
    onSuccess: (result, _vars, ctx) => {
      if (ctx?.op) resolveOp(ctx.op.id, 'success');
      if (ctx?.jobId) completeJob(ctx.jobId, 'success');
      toast.success(`Policy created: ${result.name}`);
      setPolicyForm(DEFAULT_POLICY_FORM);
      setEditingPolicyId(null);
      closeQuickAction();
      refreshGovernanceData();
    },
    onError: (error, _vars, ctx) => {
      const msg = error instanceof Error ? error.message : 'Failed';
      if (ctx?.op) resolveOp(ctx.op.id, 'failure', msg);
      if (ctx?.jobId) completeJob(ctx.jobId, 'failure', msg);
      toast.error(error instanceof Error ? error.message : 'Failed to create policy');
    },
  });

  const updatePolicyMutation = useMutation({
    mutationFn: ({ policyId, policy }: { policyId: string; policy: Partial<Policy> }) => {
      // Map UI Policy type to governance service Policy type
      const servicePolicy: Partial<import('../api/governance.service').Policy> = {
        ...policy,
        type: policy.type as any, // Type mapping handled by service
      };
      return governanceService.updatePolicy(policyId, servicePolicy);
    },
    onMutate: () => {
      const jobId = startJob('Update policy');
      const op = trackOp({ action: 'Update policy', resource: 'governance/policies', outcome: 'pending' });
      return { op, jobId };
    },
    onSuccess: (result, _vars, ctx) => {
      if (ctx?.op) resolveOp(ctx.op.id, 'success');
      if (ctx?.jobId) completeJob(ctx.jobId, 'success');
      toast.success(`Policy updated: ${result.name}`);
      setPolicyForm(DEFAULT_POLICY_FORM);
      setEditingPolicyId(null);
      closeQuickAction();
      refreshGovernanceData();
    },
    onError: (error, _vars, ctx) => {
      const msg = error instanceof Error ? error.message : 'Failed';
      if (ctx?.op) resolveOp(ctx.op.id, 'failure', msg);
      if (ctx?.jobId) completeJob(ctx.jobId, 'failure', msg);
      toast.error(error instanceof Error ? error.message : 'Failed to update policy');
    },
  });

  const deletePolicyMutation = useMutation({
    mutationFn: (policyId: string) => governanceService.deletePolicy(policyId),
    onMutate: () => {
      const jobId = startJob('Delete policy');
      const op = trackOp({ action: 'Delete policy', resource: 'governance/policies', outcome: 'pending' });
      return { op, jobId };
    },
    onSuccess: (_result, _vars, ctx) => {
      if (ctx?.op) resolveOp(ctx.op.id, 'success');
      if (ctx?.jobId) completeJob(ctx.jobId, 'success');
      toast.success('Policy deleted');
      setPolicyForm(DEFAULT_POLICY_FORM);
      setEditingPolicyId(null);
      refreshGovernanceData();
    },
    onError: (error, _vars, ctx) => {
      const msg = error instanceof Error ? error.message : 'Failed';
      if (ctx?.op) resolveOp(ctx.op.id, 'failure', msg);
      if (ctx?.jobId) completeJob(ctx.jobId, 'failure', msg);
      toast.error(error instanceof Error ? error.message : 'Failed to delete policy');
    },
  });

  const togglePolicyMutation = useMutation({
    mutationFn: ({ policyId, enabled }: { policyId: string; enabled: boolean }) => 
      governanceService.togglePolicy(policyId, enabled),
    onMutate: () => {
      const jobId = startJob('Toggle policy');
      const op = trackOp({ action: 'Toggle policy', resource: 'governance/policies', outcome: 'pending' });
      return { op, jobId };
    },
    onSuccess: (result, _vars, ctx) => {
      if (ctx?.op) resolveOp(ctx.op.id, 'success');
      if (ctx?.jobId) completeJob(ctx.jobId, 'success');
      toast.success(`Policy ${result.enabled ? 'enabled' : 'disabled'}: ${result.name}`);
      refreshGovernanceData();
    },
    onError: (error, _vars, ctx) => {
      const msg = error instanceof Error ? error.message : 'Failed';
      if (ctx?.op) resolveOp(ctx.op.id, 'failure', msg);
      if (ctx?.jobId) completeJob(ctx.jobId, 'failure', msg);
      toast.error(error instanceof Error ? error.message : 'Failed to toggle policy');
    },
  });

  const applyRecommendation = (recommendation: GovernanceRecommendation): void => {
    if (recommendation.action === 'classify-retention') {
      setClassificationForm({
        ...DEFAULT_CLASSIFICATION_FORM,
        tier: recommendation.payload?.tier ?? DEFAULT_CLASSIFICATION_FORM.tier,
        reason: recommendation.payload?.reason ?? DEFAULT_CLASSIFICATION_FORM.reason,
        piiFields: recommendation.payload?.piiFields?.join(', ') ?? '',
      });
      setActiveQuickAction('classify-retention');
      return;
    }

    if (recommendation.action === 'redact-pii') {
      setRedactionForm({
        ...DEFAULT_REDACTION_FORM,
        fields: recommendation.payload?.fields?.join(', ') ?? '',
        reason: recommendation.payload?.reason ?? DEFAULT_REDACTION_FORM.reason,
      });
      setActiveQuickAction('redact-pii');
      return;
    }

    if (recommendation.action === 'refresh-compliance') {
      complianceRefreshMutation.mutate();
      return;
    }

    showAccessReviewBoundary();
  };

  const aiSuggestedPolicyIds = new Set(
    recommendations
      .map((recommendation) => recommendation.policyId)
      .filter((policyId): policyId is string => typeof policyId === 'string' && policyId.length > 0),
  );

  const policies: Policy[] = rawPolicies
    .map(mapPolicy)
    .map((policy) => ({
      ...policy,
      aiSuggested: aiSuggestedPolicyIds.has(policy.id),
    }))
    .filter(p =>
      !searchQuery ||
      p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.type.toLowerCase().includes(searchQuery.toLowerCase())
    );

  const auditEvents: AuditEvent[] = rawAuditLogs.map(mapAuditLog);
  const auditTimeline = buildAuditTimeline(auditEvents);

  const compliantCount = policies.filter((p) => p.status === 'compliant').length;
  const warningCount = policies.filter((p) => p.status === 'warning').length;
  const complianceScore = complianceReport?.summary.complianceScore
    ?? (policies.length > 0 ? Math.round((compliantCount / policies.length) * 100) : 0);

  return (
    <div className="flex flex-col h-full" data-testid="trust-center-page">
      {/* Header */}
      <header className="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
              Trust Center
            </h1>
            <p className="text-sm text-gray-500 mt-0.5">
              Governance operations, compliance posture, and explicit lifecycle truth
            </p>
          </div>
          <div className="flex items-center gap-3">
            <CommandBarTrigger />
            <Button
              variant="solid"
              leadingIcon={<Shield className="h-4 w-4" />}
              onClick={() => triggerOperationalAction('classify-retention')}
              data-testid="trust-header-open-live-action"
              disabled={!canWriteGovernance}
              title={canWriteGovernance ? undefined : 'Governance write capability is not available in this deployment'}
            >
              Open Live Action
            </Button>
          </div>
        </div>

        {/* Search */}
        <div className="flex items-center gap-2">
          <div className="relative flex-1 max-w-md">
            <Input
              value={searchQuery}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setSearchQuery(e.target.value)}
              placeholder="Search live safeguards or derived policy coverage"
              aria-label="Search live safeguards or derived policy coverage"
              data-testid="trust-search-input"
              leadingIcon={<Search className="h-4 w-4 text-gray-400" />}
              className="w-full"
            />
          </div>
          <IconButton
            variant="ghost"
            tone="neutral"
            icon={<Filter className="h-4 w-4" />}
            label="Filter"
          />
          <IconButton
            variant="ghost"
            tone="neutral"
            icon={<RefreshCw className="h-4 w-4" />}
            label="Refresh"
            onClick={() => refetchPolicies()}
            data-testid="trust-refresh-policies"
          />
        </div>
      </header>

      {/* Content */}
      <section className="flex-1 overflow-y-auto p-6">
        {/* Compliance Score */}
        <section className="mb-8">
          <ComplianceScoreCard
            score={complianceScore}
            total={policies.length}
            compliant={compliantCount}
            warnings={warningCount}
          />
        </section>

        <section className="mb-8">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-medium text-gray-500 uppercase tracking-wide">
              Governance Lifecycle Truth
            </h2>
            <span className="text-xs text-gray-400">Live actions, derived read-only posture, and unsupported lifecycle areas</span>
          </div>
          {lifecycleLoading ? (
            <div className="flex items-center gap-2 text-gray-500 py-4">
              <Loader2 className="h-4 w-4 animate-spin" />
              <span className="text-sm">Loading governance lifecycle truth...</span>
            </div>
          ) : (
            <div className="grid grid-cols-1 xl:grid-cols-2 gap-4" data-testid="trust-lifecycle-section">
              {lifecycleSurfaces.map((surface) => (
                <LifecycleTruthCard
                  key={surface.id}
                  surface={surface}
                  onAction={triggerOperationalAction}
                />
              ))}
            </div>
          )}
        </section>

        {/* Quick Apply */}
        <section className="mb-8">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-medium text-gray-500 uppercase tracking-wide">
              AI Recommendations
            </h2>
            <span className="text-xs text-gray-400">Derived from live compliance, PII, and audit summary data</span>
          </div>
          {recommendationsLoading ? (
            <div className="flex items-center gap-2 text-gray-500 py-4">
              <Loader2 className="h-4 w-4 animate-spin" />
              <span className="text-sm">Loading recommendations...</span>
            </div>
          ) : recommendations.length === 0 ? (
            <p className="text-sm text-gray-500">No operator recommendations are active right now.</p>
          ) : (
            <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
              {recommendations.map((recommendation) => (
                <RecommendationCard
                  key={recommendation.id}
                  recommendation={recommendation}
                  onApply={() => applyRecommendation(recommendation)}
                />
              ))}
            </div>
          )}
        </section>

        {/* Quick Apply */}
        <section className="mb-8">
          <h2 className="text-sm font-medium text-gray-500 uppercase tracking-wide mb-4">
            Action Center
          </h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
            <QuickApplyCard
              title="Classify Retention"
              description="Apply a real retention tier to one collection"
              icon={<Shield className="h-5 w-5 text-blue-600" />}
              onClick={() => setActiveQuickAction('classify-retention')}
              data-testid="trust-quick-action-classify-retention"
            />
            <QuickApplyCard
              title="Redact PII"
              description="Redact known sensitive fields on one entity"
              icon={<Eye className="h-5 w-5 text-purple-600" />}
              onClick={() => setActiveQuickAction('redact-pii')}
              data-testid="trust-quick-action-redact-pii"
            />
            <QuickApplyCard
              title="Refresh Compliance"
              description="Rebuild the current compliance view from live governance data"
              icon={<FileText className="h-5 w-5 text-green-600" />}
              onClick={() => complianceRefreshMutation.mutate()}
              data-testid="trust-quick-action-refresh-compliance"
            />
            <QuickApplyCard
              title="Dry Run Purge"
              description="Preview retention deletions and confirm before execution"
              icon={<Trash2 className="h-5 w-5 text-red-600" />}
              onClick={() => setActiveQuickAction('purge-retention')}
              data-testid="trust-quick-action-purge-retention"
            />
            <QuickApplyCard
              title="Access Review Status"
              description="Current deployment keeps access governance in read-only posture"
              icon={<Lock className="h-5 w-5 text-amber-600" />}
              onClick={showAccessReviewBoundary}
              data-testid="trust-quick-action-access-review"
            />
          </div>
          {actionSummary && (
            <div
              data-testid="trust-action-summary"
              className={cn(
                'mt-4 rounded-xl border px-4 py-3',
                actionSummary.tone === 'success'
                  ? 'border-green-200 bg-green-50 text-green-800 dark:border-green-900/40 dark:bg-green-900/10 dark:text-green-200'
                  : 'border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-900/40 dark:bg-amber-900/10 dark:text-amber-200',
              )}
            >
              <p className="text-sm font-semibold">{actionSummary.title}</p>
              <p className="mt-1 text-sm">{actionSummary.detail}</p>
            </div>
          )}
        </section>

        {/* Two Column Layout */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Policies */}
          <section className="lg:col-span-2">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h2 className="text-sm font-medium text-gray-500 uppercase tracking-wide">
                  Derived Policy Coverage
                </h2>
                <p className="mt-1 text-xs text-gray-400">
                  These cards summarize live compliance, PII, and audit posture. General policy CRUD remains outside the current launcher contract.
                </p>
              </div>
            </div>
            <div className="space-y-4" data-testid="trust-policies-section">
              {policiesLoading ? (
                <div className="flex items-center gap-2 text-gray-500 py-4">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  <span className="text-sm">Loading policies...</span>
                </div>
              ) : policies.length === 0 ? (
                <p className="text-sm text-gray-500 py-4">No derived policy coverage is available from the current compliance summary.</p>
              ) : (
                policies.map((policy) => (
                  <PolicyCard
                    key={policy.id}
                    policy={policy}
                    data-testid="policy-item"
                  />
                ))
              )}
            </div>
          </section>

          {/* Audit Log */}
          <section>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-sm font-medium text-gray-500 uppercase tracking-wide">
                Audit Timeline
              </h2>
              <Button variant="link" size="sm">
                View all
              </Button>
            </div>
            <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4" data-testid="audit-timeline">
              {auditLoading ? (
                <div className="flex items-center gap-2 text-gray-500 py-2">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  <span className="text-sm">Loading audit logs...</span>
                </div>
              ) : auditTimeline.length === 0 ? (
                <p className="text-sm text-gray-500 py-2">No recent governance audit events.</p>
              ) : (
                <div className="space-y-3">
                  {auditTimeline.map((entry) => (
                    <AuditTimelineCard key={entry.key} entry={entry} data-testid="audit-timeline-entry" />
                  ))}
                </div>
              )}
            </div>
          </section>

          {/* Session Operation History (DC-UX-044) */}
          <section>
            <OperationHistoryAlert records={opHistory} />
            <div className="mt-3 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4">
              <OperationHistory
                records={opHistory}
                onClear={clearOpHistory}
              />
            </div>
          </section>
        </div>
      </section>

      {activeQuickAction && (
        <div className="fixed inset-0 z-50 overflow-y-auto">
          <button
            aria-label="Close quick action dialog"
            className="fixed inset-0 bg-black/50 backdrop-blur-sm"
            onClick={closeQuickAction}
          />
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 pointer-events-none">
            <div
              role="dialog"
              aria-modal="true"
              aria-labelledby="governance-quick-action-title"
              data-testid="trust-quick-action-dialog"
              className="pointer-events-auto w-full max-w-xl rounded-2xl border border-gray-200 bg-white shadow-xl dark:border-gray-700 dark:bg-gray-800"
              onClick={(event) => event.stopPropagation()}
            >
              <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4 dark:border-gray-700">
                <div>
                  <h2 id="governance-quick-action-title" className="text-lg font-semibold text-gray-900 dark:text-gray-100">
                    {activeQuickAction === 'classify-retention'
                      ? 'Classify Retention'
                      : activeQuickAction === 'redact-pii'
                        ? 'Redact PII'
                        : 'Retention Purge Assistant'}
                  </h2>
                  <p className="mt-1 text-sm text-gray-500">
                    {activeQuickAction === 'classify-retention'
                      ? 'Apply a live retention tier through the launcher governance contract.'
                      : activeQuickAction === 'redact-pii'
                        ? 'Redact known sensitive fields for a specific entity through the launcher governance contract.'
                        : 'Run a retention purge dry run, inspect the policy and candidates, then execute with the issued confirmation token.'}
                  </p>
                </div>
                <IconButton
                  variant="ghost"
                  tone="neutral"
                  icon={<X className="h-4 w-4" />}
                  label="Close quick action dialog"
                  onClick={closeQuickAction}
                />
              </div>

              <div className="space-y-4 px-6 py-5">
                {activeQuickAction === 'classify-retention' ? (
                  <>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300" htmlFor="retention-collection">
                      Collection
                    </label>
                    <input
                      id="retention-collection"
                      data-testid="trust-retention-collection"
                      type="text"
                      value={classificationForm.collection}
                      onChange={(event) => setClassificationForm((previous) => ({ ...previous, collection: event.target.value }))}
                      placeholder="customers"
                      className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 outline-none transition-colors focus:border-primary-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                    />

                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300" htmlFor="retention-tier">
                      Retention Tier
                    </label>
                    <select
                      id="retention-tier"
                      data-testid="trust-retention-tier"
                      value={classificationForm.tier}
                      onChange={(event) => setClassificationForm((previous) => ({ ...previous, tier: event.target.value as RetentionTier }))}
                      className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 outline-none transition-colors focus:border-primary-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                    >
                      <option value="transient">Transient</option>
                      <option value="short-term">Short-term</option>
                      <option value="standard">Standard</option>
                      <option value="compliance">Compliance</option>
                      <option value="permanent">Permanent</option>
                    </select>

                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300" htmlFor="retention-reason">
                      Reason
                    </label>
                    <input
                      id="retention-reason"
                      data-testid="trust-retention-reason"
                      type="text"
                      value={classificationForm.reason}
                      onChange={(event) => setClassificationForm((previous) => ({ ...previous, reason: event.target.value }))}
                      className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 outline-none transition-colors focus:border-primary-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                    />

                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300" htmlFor="retention-pii-fields">
                      PII Fields
                    </label>
                    <input
                      id="retention-pii-fields"
                      data-testid="trust-retention-pii-fields"
                      type="text"
                      value={classificationForm.piiFields}
                      onChange={(event) => setClassificationForm((previous) => ({ ...previous, piiFields: event.target.value }))}
                      placeholder="email, phone, ssn"
                      className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 outline-none transition-colors focus:border-primary-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                    />
                  </>
                ) : activeQuickAction === 'redact-pii' ? (
                  <>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300" htmlFor="redaction-collection">
                      Collection
                    </label>
                    <input
                      id="redaction-collection"
                      data-testid="trust-redaction-collection"
                      type="text"
                      value={redactionForm.collection}
                      onChange={(event) => setRedactionForm((previous) => ({ ...previous, collection: event.target.value }))}
                      placeholder="customers"
                      className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 outline-none transition-colors focus:border-primary-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                    />

                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300" htmlFor="redaction-entity-id">
                      Entity ID
                    </label>
                    <input
                      id="redaction-entity-id"
                      data-testid="trust-redaction-entity-id"
                      type="text"
                      value={redactionForm.entityId}
                      onChange={(event) => setRedactionForm((previous) => ({ ...previous, entityId: event.target.value }))}
                      placeholder="ent-123"
                      className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 outline-none transition-colors focus:border-primary-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                    />

                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300" htmlFor="redaction-fields">
                      Fields
                    </label>
                    <input
                      id="redaction-fields"
                      data-testid="trust-redaction-fields"
                      type="text"
                      value={redactionForm.fields}
                      onChange={(event) => setRedactionForm((previous) => ({ ...previous, fields: event.target.value }))}
                      placeholder="email, phone"
                      className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 outline-none transition-colors focus:border-primary-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                    />

                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300" htmlFor="redaction-reason">
                      Reason
                    </label>
                    <input
                      id="redaction-reason"
                      data-testid="trust-redaction-reason"
                      type="text"
                      value={redactionForm.reason}
                      onChange={(event) => setRedactionForm((previous) => ({ ...previous, reason: event.target.value }))}
                      className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 outline-none transition-colors focus:border-primary-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                    />
                  </>
                ) : (
                  <>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300" htmlFor="purge-collection">
                      Collection
                    </label>
                    <input
                      id="purge-collection"
                      data-testid="trust-purge-collection"
                      type="text"
                      value={purgeForm.collection}
                      onChange={(event) => {
                        setPurgeForm({ collection: event.target.value });
                        setPurgePreview(null);
                      }}
                      placeholder="customers"
                      className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 outline-none transition-colors focus:border-primary-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                    />

                    {purgePreview && (
                      <div data-testid="trust-purge-preview" className="rounded-xl border border-amber-200 bg-amber-50 p-4 dark:border-amber-900/40 dark:bg-amber-900/10">
                        <div className="flex items-start gap-3">
                          <Trash2 className="mt-0.5 h-5 w-5 text-amber-600 dark:text-amber-300" />
                          <div className="space-y-3 text-sm text-amber-900 dark:text-amber-100">
                            <div>
                              <p className="font-semibold">Policy preview</p>
                              <p className="mt-1">{purgePreview.policy.collection} is on the {purgePreview.policy.tier} tier for {purgePreview.policy.retentionDays} days with status {purgePreview.policy.status}.</p>
                            </div>
                            <div>
                              <p className="font-semibold">Dry run result</p>
                              <p className="mt-1">Estimated rows: {purgePreview.dryRun.estimatedRows}. Token expires in {purgePreview.dryRun.tokenExpiresInSec} seconds.</p>
                              <p className="mt-1">Sample entity IDs: {purgePreview.dryRun.sampleEntityIds.length > 0 ? purgePreview.dryRun.sampleEntityIds.join(', ') : 'No candidates returned.'}</p>
                            </div>
                          </div>
                        </div>
                      </div>
                    )}
                  </>
                )}
              </div>

              <div className="flex items-center justify-end gap-3 border-t border-gray-200 px-6 py-4 dark:border-gray-700">
                <Button
                  variant="outline"
                  onClick={closeQuickAction}
                  data-testid="trust-quick-action-cancel"
                >
                  Cancel
                </Button>
                <Button
                  variant="solid"
                  loading={classifyMutation.isPending || redactionMutation.isPending || purgeDryRunMutation.isPending || purgeExecuteMutation.isPending}
                  disabled={classifyMutation.isPending || redactionMutation.isPending || purgeDryRunMutation.isPending || purgeExecuteMutation.isPending}
                  data-testid="trust-quick-action-submit"
                  onClick={() => {
                    if (activeQuickAction === 'classify-retention') {
                      classifyMutation.mutate();
                      return;
                    }
                    if (activeQuickAction === 'purge-retention') {
                      if (purgePreview) {
                        purgeExecuteMutation.mutate();
                        return;
                      }
                      purgeDryRunMutation.mutate();
                      return;
                    }
                    redactionMutation.mutate();
                  }}
                >
                  {activeQuickAction === 'classify-retention'
                    ? 'Apply retention tier'
                    : activeQuickAction === 'redact-pii'
                      ? 'Redact entity'
                      : purgePreview
                        ? 'Execute purge'
                        : 'Run dry run'}
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Ambient Intelligence Bar */}
      <AmbientIntelligenceBar />

      {/* Command Bar Modal */}
      <CommandBar />
    </div>
  );
}

export default TrustCenter;

