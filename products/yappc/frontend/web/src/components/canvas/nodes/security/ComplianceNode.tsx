// ============================================================================
// ComplianceNode - Security Canvas Node
//
// Displays compliance framework status with control progress, compliance
// score visualization, findings summary, and evidence tracking.
// ============================================================================

import { memo, useMemo } from 'react';
import { Handle, Position, type Node, type NodeProps } from '@xyflow/react';
import {
  Shield,
  CheckCircle2,
  XCircle,
  AlertCircle,
  Clock,
  FileText,
  TrendingUp,
  Calendar,
  ChevronRight,
  BarChart3,
} from 'lucide-react';
import { cn } from '../../utils/cn';

// ============================================================================
// Types
// ============================================================================

export type ComplianceFramework = 'SOC2' | 'ISO27001' | 'HIPAA' | 'PCI_DSS' | 'GDPR' | 'NIST' | 'CCPA' | 'FEDRAMP';
export type ComplianceStatus = 'COMPLIANT' | 'NON_COMPLIANT' | 'PARTIAL' | 'NOT_ASSESSED';

export interface ControlSummary {
  id: string;
  controlId: string;
  title: string;
  category: string;
  status: ComplianceStatus;
}

export interface ComplianceNodeData extends Record<string, unknown> {
  id: string;
  projectId: string;
  framework: ComplianceFramework;
  enabled: boolean;
  controls?: ControlSummary[];
  lastAssessmentAt?: string;
  nextAssessmentAt?: string;
  overallStatus: ComplianceStatus;
  complianceScore: number;
  findingsCount?: number;
  criticalFindingsCount?: number;
  evidenceCount?: number;
  selected?: boolean;
  compact?: boolean;
}

type ComplianceCanvasNode = Node<ComplianceNodeData>;

// ============================================================================
// Framework Configuration
// ============================================================================

const FRAMEWORK_CONFIG: Record<ComplianceFramework, {
  name: string;
  fullName: string;
  color: string;
  bgColor: string;
  borderColor: string;
}> = {
  SOC2: {
    name: 'SOC 2',
    fullName: 'SOC 2 Type II',
    color: 'text-info-color',
    bgColor: 'bg-info-bg',
    borderColor: 'border-info-border',
  },
  ISO27001: {
    name: 'ISO 27001',
    fullName: 'ISO/IEC 27001:2022',
    color: 'text-info-color',
    bgColor: 'bg-info-bg',
    borderColor: 'border-info-border',
  },
  HIPAA: {
    name: 'HIPAA',
    fullName: 'Health Insurance Portability',
    color: 'text-info-color',
    bgColor: 'bg-info-bg',
    borderColor: 'border-info-border',
  },
  PCI_DSS: {
    name: 'PCI DSS',
    fullName: 'Payment Card Industry',
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg',
    borderColor: 'border-warning-border',
  },
  GDPR: {
    name: 'GDPR',
    fullName: 'General Data Protection',
    color: 'text-success-color',
    bgColor: 'bg-success-bg',
    borderColor: 'border-success-border',
  },
  NIST: {
    name: 'NIST',
    fullName: 'NIST Cybersecurity Framework',
    color: 'text-info-color',
    bgColor: 'bg-info-bg',
    borderColor: 'border-info-border',
  },
  CCPA: {
    name: 'CCPA',
    fullName: 'California Consumer Privacy',
    color: 'text-success-color',
    bgColor: 'bg-success-bg',
    borderColor: 'border-success-border',
  },
  FEDRAMP: {
    name: 'FedRAMP',
    fullName: 'Federal Risk Authorization',
    color: 'text-destructive',
    bgColor: 'bg-destructive-bg',
    borderColor: 'border-destructive-border',
  },
};

// ============================================================================
// Status Configuration
// ============================================================================

const STATUS_CONFIG: Record<ComplianceStatus, {
  label: string;
  color: string;
  bgColor: string;
  icon: typeof CheckCircle2;
}> = {
  COMPLIANT: {
    label: 'Compliant',
    color: 'text-success-color',
    bgColor: 'bg-success-bg',
    icon: CheckCircle2,
  },
  NON_COMPLIANT: {
    label: 'Non-Compliant',
    color: 'text-destructive',
    bgColor: 'bg-destructive-bg',
    icon: XCircle,
  },
  PARTIAL: {
    label: 'Partial',
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg',
    icon: AlertCircle,
  },
  NOT_ASSESSED: {
    label: 'Not Assessed',
    color: 'text-fg-muted',
    bgColor: 'bg-muted',
    icon: Clock,
  },
};

// ============================================================================
// Helper Components
// ============================================================================

function ComplianceScoreGauge({ score, size = 'default' }: { score: number; size?: 'small' | 'default' }) {
  const radius = size === 'small' ? 24 : 36;
  const strokeWidth = size === 'small' ? 4 : 6;
  const circumference = 2 * Math.PI * radius;
  const progress = (score / 100) * circumference;
  
  const getScoreColor = () => {
    if (score >= 90) return '#22c55e'; // Green
    if (score >= 70) return '#eab308'; // Yellow
    if (score >= 50) return '#f97316'; // Orange
    return '#ef4444'; // Red
  };

  const dimension = (radius + strokeWidth) * 2;

  return (
    <div className="relative inline-flex items-center justify-center">
      <svg 
        width={dimension} 
        height={dimension} 
        className="transform -rotate-90"
      >
        <circle
          cx={radius + strokeWidth}
          cy={radius + strokeWidth}
          r={radius}
          fill="none"
          stroke="#e5e7eb"
          strokeWidth={strokeWidth}
        />
        <circle
          cx={radius + strokeWidth}
          cy={radius + strokeWidth}
          r={radius}
          fill="none"
          stroke={getScoreColor()}
          strokeWidth={strokeWidth}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference - progress}
          className="transition-all duration-500"
        />
      </svg>
      <div className="absolute inset-0 flex items-center justify-center">
        <span className={cn(
          'font-bold',
          size === 'small' ? 'text-sm' : 'text-xl'
        )} style={{ color: getScoreColor() }}>
          {Math.round(score)}%
        </span>
      </div>
    </div>
  );
}

function ControlStatusBar({ controls }: { controls: ControlSummary[] }) {
  const statusCounts = useMemo(() => {
    const counts = {
      COMPLIANT: 0,
      NON_COMPLIANT: 0,
      PARTIAL: 0,
      NOT_ASSESSED: 0,
    };
    controls.forEach((c) => {
      counts[c.status]++;
    });
    return counts;
  }, [controls]);

  const total = controls.length;
  if (total === 0) return null;

  return (
    <div className="space-y-2">
      <div className="flex h-2 rounded-full overflow-hidden bg-muted">
        {statusCounts.COMPLIANT > 0 && (
          <div
            className="bg-success-color transition-all duration-300"
            style={{ width: `${(statusCounts.COMPLIANT / total) * 100}%` }}
          />
        )}
        {statusCounts.PARTIAL > 0 && (
          <div
            className="bg-warning-color transition-all duration-300"
            style={{ width: `${(statusCounts.PARTIAL / total) * 100}%` }}
          />
        )}
        {statusCounts.NOT_ASSESSED > 0 && (
          <div
            className="bg-muted-foreground transition-all duration-300"
            style={{ width: `${(statusCounts.NOT_ASSESSED / total) * 100}%` }}
          />
        )}
        {statusCounts.NON_COMPLIANT > 0 && (
          <div
            className="bg-destructive transition-all duration-300"
            style={{ width: `${(statusCounts.NON_COMPLIANT / total) * 100}%` }}
          />
        )}
      </div>
      <div className="flex items-center justify-between text-xs text-fg-muted">
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-1">
            <span className="h-2 w-2 rounded-full bg-success-color" />
            {statusCounts.COMPLIANT}
          </span>
          <span className="flex items-center gap-1">
            <span className="h-2 w-2 rounded-full bg-warning-color" />
            {statusCounts.PARTIAL}
          </span>
          <span className="flex items-center gap-1">
            <span className="h-2 w-2 rounded-full bg-destructive" />
            {statusCounts.NON_COMPLIANT}
          </span>
          <span className="flex items-center gap-1">
            <span className="h-2 w-2 rounded-full bg-muted-foreground" />
            {statusCounts.NOT_ASSESSED}
          </span>
        </div>
        <span>{total} controls</span>
      </div>
    </div>
  );
}

function formatDate(dateString: string): string {
  const date = new Date(dateString);
  return date.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}

function getDaysUntil(dateString: string): number {
  const date = new Date(dateString);
  const now = new Date();
  return Math.ceil((date.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
}

// ============================================================================
// Main Component
// ============================================================================

function ComplianceNodeComponent({ data, selected }: NodeProps<ComplianceCanvasNode>) {
  const frameworkConfig = FRAMEWORK_CONFIG[data.framework];
  const statusConfig = STATUS_CONFIG[data.overallStatus];
  const StatusIcon = statusConfig.icon;

  const groupedControls = useMemo(() => {
    if (!data.controls) return {};
    const groups: Record<string, ControlSummary[]> = {};
    data.controls.forEach((control) => {
      if (!groups[control.category]) {
        groups[control.category] = [];
      }
      groups[control.category].push(control);
    });
    return groups;
  }, [data.controls]);

  const daysUntilAssessment = data.nextAssessmentAt
    ? getDaysUntil(data.nextAssessmentAt)
    : null;

  if (data.compact) {
    return (
      <div
        className={cn(
          'rounded-lg border-2 bg-white p-3 shadow-sm transition-all duration-200',
          frameworkConfig.borderColor,
          selected && 'ring-2 ring-info-border ring-offset-2',
          'min-w-[180px]'
        )}
      >
        <Handle type="target" position={Position.Left} className="!bg-muted-foreground" />
        <Handle type="source" position={Position.Right} className="!bg-muted-foreground" />

        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Shield className={cn('h-4 w-4', frameworkConfig.color)} />
            <span className="font-medium text-fg">{frameworkConfig.name}</span>
          </div>
          <ComplianceScoreGauge score={data.complianceScore} size="small" />
        </div>
      </div>
    );
  }

  return (
    <div
      className={cn(
        'rounded-lg border-2 bg-white shadow-md transition-all duration-200',
        frameworkConfig.borderColor,
        selected && 'ring-2 ring-info-border ring-offset-2',
        'min-w-[320px] max-w-[400px]'
      )}
    >
      <Handle type="target" position={Position.Left} className="!bg-muted-foreground" />
      <Handle type="source" position={Position.Right} className="!bg-muted-foreground" />

      {/* Header */}
      <div className={cn('rounded-t-lg px-4 py-3', frameworkConfig.bgColor)}>
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-3">
            <Shield className={cn('h-6 w-6', frameworkConfig.color)} />
            <div>
              <h3 className="font-semibold text-fg">{frameworkConfig.name}</h3>
              <p className="text-xs text-fg-muted">{frameworkConfig.fullName}</p>
            </div>
          </div>
          <ComplianceScoreGauge score={data.complianceScore} />
        </div>
      </div>

      {/* Content */}
      <div className="p-4 space-y-4">
        {/* Overall Status */}
        <div className="flex items-center justify-between">
          <span className="text-sm text-fg-muted">Overall Status</span>
          <div className={cn(
            'flex items-center gap-1.5 rounded-full px-3 py-1 text-sm font-medium',
            statusConfig.bgColor,
            statusConfig.color
          )}>
            <StatusIcon className="h-4 w-4" />
            <span>{statusConfig.label}</span>
          </div>
        </div>

        {/* Control Progress */}
        {data.controls && data.controls.length > 0 && (
          <div className="space-y-2">
            <span className="text-sm font-medium text-fg">Control Status</span>
            <ControlStatusBar controls={data.controls} />
          </div>
        )}

        {/* Control Categories */}
        {Object.keys(groupedControls).length > 0 && (
          <div className="space-y-2">
            <span className="text-sm font-medium text-fg">Categories</span>
            <div className="space-y-1">
              {Object.entries(groupedControls).slice(0, 4).map(([category, controls]) => {
                const compliant = controls.filter((c) => c.status === 'COMPLIANT').length;
                const total = controls.length;
                const percentage = Math.round((compliant / total) * 100);
                
                return (
                  <div key={category} className="flex items-center justify-between text-sm">
                    <span className="text-fg truncate flex-1">{category}</span>
                    <div className="flex items-center gap-2">
                      <div className="w-16 h-1.5 rounded-full bg-muted">
                        <div
                          className={cn(
                            'h-full rounded-full',
                            percentage >= 80 ? 'bg-success-color' : percentage >= 50 ? 'bg-warning-color' : 'bg-destructive'
                          )}
                          style={{ width: `${percentage}%` }}
                        />
                      </div>
                      <span className="text-xs text-fg-muted w-12 text-right">
                        {compliant}/{total}
                      </span>
                    </div>
                  </div>
                );
              })}
              {Object.keys(groupedControls).length > 4 && (
                <div className="flex items-center text-xs text-info-color hover:opacity-80 cursor-pointer">
                  <span>View all categories</span>
                  <ChevronRight className="h-3 w-3" />
                </div>
              )}
            </div>
          </div>
        )}

        {/* Stats Grid */}
        <div className="grid grid-cols-3 gap-2">
          {data.findingsCount !== undefined && (
            <div className="rounded-lg bg-surface-muted p-2 text-center">
              <div className="flex items-center justify-center gap-1">
                <AlertCircle className="h-3.5 w-3.5 text-warning-color" />
                <span className="font-semibold text-fg">{data.findingsCount}</span>
              </div>
              <span className="text-xs text-fg-muted">Findings</span>
              {data.criticalFindingsCount !== undefined && data.criticalFindingsCount > 0 && (
                <span className="text-xs text-destructive">
                  ({data.criticalFindingsCount} critical)
                </span>
              )}
            </div>
          )}
          {data.evidenceCount !== undefined && (
            <div className="rounded-lg bg-surface-muted p-2 text-center">
              <div className="flex items-center justify-center gap-1">
                <FileText className="h-3.5 w-3.5 text-info-color" />
                <span className="font-semibold text-fg">{data.evidenceCount}</span>
              </div>
              <span className="text-xs text-fg-muted">Evidence</span>
            </div>
          )}
          {data.controls && (
            <div className="rounded-lg bg-surface-muted p-2 text-center">
              <div className="flex items-center justify-center gap-1">
                <BarChart3 className="h-3.5 w-3.5 text-success-color" />
                <span className="font-semibold text-fg">{data.controls.length}</span>
              </div>
              <span className="text-xs text-fg-muted">Controls</span>
            </div>
          )}
        </div>

        {/* Assessment Dates */}
        <div className="flex items-center justify-between text-sm">
          {data.lastAssessmentAt && (
            <div className="flex items-center gap-1.5 text-fg-muted">
              <Clock className="h-4 w-4" />
              <span>Last: {formatDate(data.lastAssessmentAt)}</span>
            </div>
          )}
          {data.nextAssessmentAt && (
            <div className={cn(
              'flex items-center gap-1.5',
              daysUntilAssessment !== null && daysUntilAssessment < 30 
                ? 'text-warning-color' 
                : 'text-fg-muted'
            )}>
              <Calendar className="h-4 w-4" />
              <span>
                Next: {formatDate(data.nextAssessmentAt)}
                {daysUntilAssessment !== null && daysUntilAssessment <= 30 && (
                  <span className="ml-1">({daysUntilAssessment}d)</span>
                )}
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Footer */}
      <div className="border-t border-border px-4 py-2">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            {data.enabled ? (
              <span className="inline-flex items-center gap-1 text-xs text-success-color">
                <span className="h-1.5 w-1.5 rounded-full bg-success-color" />
                Active
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 text-xs text-fg-muted">
                <span className="h-1.5 w-1.5 rounded-full bg-muted-foreground" />
                Inactive
              </span>
            )}
          </div>
          <div className="flex items-center gap-1 text-xs text-fg-muted">
            <TrendingUp className="h-3 w-3" />
            <span>+5% this quarter</span>
          </div>
        </div>
      </div>
    </div>
  );
}

export const ComplianceNode = memo(ComplianceNodeComponent);
export default ComplianceNode;
