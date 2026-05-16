/**
 * @fileoverview Import Summary Panel for Artifact Compiler
 *
 * P2-3: Import summary panel with:
 * - Show understood vs skipped vs residual files
 * - Confidence metrics
 * - Review requirements display
 *
 * @doc.type component
 * @doc.purpose UI component for displaying import summary and review requirements
 * @doc.layer product
 * @doc.pattern Panel
 */

import { X, CheckCircle2, AlertTriangle, FileText, Skip, Layers } from 'lucide-react';

import { cn } from '@/lib/utils';
import { Button } from '../ui/Button';

// ============================================================================
// Types
// ============================================================================

export interface ImportSummary {
  snapshotId: string;
  versionId: string;
  totalFiles: number;
  understoodFiles: number;
  skippedFiles: number;
  residualFiles: number;
  confidence: number;
  reviewRequired: boolean;
  reviewItems: ReviewItem[];
}

export interface ReviewItem {
  id: string;
  type: 'residual' | 'low-confidence' | 'complex-structure';
  filePath: string;
  reason: string;
  confidence?: number;
  priority: 'high' | 'medium' | 'low';
}

export interface ImportSummaryPanelProps {
  open: boolean;
  summary: ImportSummary;
  onClose: () => void;
  onReview?: (itemId: string) => void;
}

// ============================================================================
// Component
// ============================================================================

export function ImportSummaryPanel({
  open,
  summary,
  onClose,
  onReview,
}: ImportSummaryPanelProps) {
  if (!open) {
    return null;
  }

  const confidenceLevel = getConfidenceLevel(summary.confidence);
  const ConfidenceIcon = confidenceLevel.icon;

  return (
    <aside
      className="fixed right-0 top-14 z-40 flex h-[calc(100vh-3.5rem)] w-full max-w-lg flex-col border-l border-divider bg-bg-paper shadow-2xl"
      role="complementary"
      aria-label="Import Summary Panel"
      data-testid="import-summary-panel"
    >
      {/* Header */}
      <div className="flex items-center justify-between border-b border-divider px-5 py-4">
        <div className="flex items-center gap-3">
          <div className="rounded-full bg-primary-100 p-2 text-primary-700">
            <FileText className="h-4 w-4" />
          </div>
          <div>
            <h2 className="text-sm font-semibold text-text-primary">Import Summary</h2>
            <p className="text-xs text-text-secondary">Snapshot: {summary.snapshotId}</p>
          </div>
        </div>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={onClose}
          className="rounded-md p-1 text-text-secondary hover:bg-grey-100"
          aria-label="Close panel"
        >
          <X className="h-4 w-4" />
        </Button>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-auto px-5 py-4 space-y-4">
        {/* Overall Stats */}
        <div className="rounded-lg border border-divider bg-bg-default p-4 space-y-3">
          <h3 className="text-sm font-semibold text-text-primary">Overview</h3>
          <div className="grid grid-cols-3 gap-3">
            <div className="rounded-lg bg-success-bg/10 border border-success-border p-3 text-center">
              <CheckCircle2 className="mx-auto h-5 w-5 text-success-color" />
              <p className="mt-1 text-2xl font-semibold text-text-primary">{summary.understoodFiles}</p>
              <p className="text-xs text-text-secondary">Understood</p>
            </div>
            <div className="rounded-lg bg-warning-bg/10 border border-warning-border p-3 text-center">
              <Skip className="mx-auto h-5 w-5 text-warning-color" />
              <p className="mt-1 text-2xl font-semibold text-text-primary">{summary.skippedFiles}</p>
              <p className="text-xs text-text-secondary">Skipped</p>
            </div>
            <div className="rounded-lg bg-destructive-bg/10 border border-destructive-border p-3 text-center">
              <Layers className="mx-auto h-5 w-5 text-destructive" />
              <p className="mt-1 text-2xl font-semibold text-text-primary">{summary.residualFiles}</p>
              <p className="text-xs text-text-secondary">Residual</p>
            </div>
          </div>
          <div className="flex items-center justify-between text-sm">
            <span className="text-text-secondary">Total files processed:</span>
            <span className="font-medium text-text-primary">{summary.totalFiles}</span>
          </div>
        </div>

        {/* Confidence */}
        <div className="rounded-lg border border-divider bg-bg-default p-4 space-y-3">
          <h3 className="text-sm font-semibold text-text-primary">Confidence Score</h3>
          <div className="flex items-center gap-3">
            <div className={cn('rounded-full p-2', confidenceLevel.bg)}>
              <ConfidenceIcon className={cn('h-5 w-5', confidenceLevel.color)} />
            </div>
            <div className="flex-1">
              <div className="flex items-center justify-between mb-1">
                <span className="text-sm font-medium text-text-primary">{summary.confidence.toFixed(1)}%</span>
                <span className={cn('text-xs font-medium', confidenceLevel.color)}>
                  {confidenceLevel.label}
                </span>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-divider">
                <div
                  className={cn('h-full transition-all duration-300', confidenceLevel.barColor)}
                  style={{ width: `${summary.confidence}%` }}
                />
              </div>
            </div>
          </div>
        </div>

        {/* Review Requirements */}
        {summary.reviewRequired && summary.reviewItems.length > 0 && (
          <div className="rounded-lg border border-warning-border bg-warning-bg/10 p-4 space-y-3">
            <div className="flex items-center gap-2">
              <AlertTriangle className="h-5 w-5 text-warning-color" />
              <h3 className="text-sm font-semibold text-text-primary">Review Required</h3>
              <span className="rounded-full bg-warning-color px-2 py-0.5 text-xs font-medium text-white">
                {summary.reviewItems.length}
              </span>
            </div>
            <div className="space-y-2">
              {summary.reviewItems.map((item) => (
                <ReviewItemCard key={item.id} item={item} onReview={onReview} />
              ))}
            </div>
          </div>
        )}

        {!summary.reviewRequired && (
          <div className="rounded-lg border border-success-border bg-success-bg/10 p-4 space-y-2">
            <div className="flex items-center gap-2">
              <CheckCircle2 className="h-5 w-5 text-success-color" />
              <h3 className="text-sm font-semibold text-text-primary">No Review Required</h3>
            </div>
            <p className="text-sm text-text-secondary">
              All files were processed successfully with high confidence.
            </p>
          </div>
        )}
      </div>

      {/* Footer */}
      <div className="border-t border-divider px-5 py-4">
        <Button
          type="button"
          variant="outline"
          onClick={onClose}
          className="w-full"
        >
          Close Summary
        </Button>
      </div>
    </aside>
  );
}

// ============================================================================
// Sub-components
// ============================================================================

interface ReviewItemCardProps {
  item: ReviewItem;
  onReview?: (itemId: string) => void;
}

function ReviewItemCard({ item, onReview }: ReviewItemCardProps) {
  const priorityStyles = {
    high: 'bg-destructive-bg/10 border-destructive-border text-destructive',
    medium: 'bg-warning-bg/10 border-warning-border text-warning-color',
    low: 'bg-primary-bg/10 border-primary-border text-primary-700',
  };

  const typeIcons = {
    residual: Layers,
    'low-confidence': AlertTriangle,
    'complex-structure': FileText,
  };

  const TypeIcon = typeIcons[item.type];

  return (
    <div className={cn(
      'rounded-lg border p-3 space-y-2',
      priorityStyles[item.priority]
    )}>
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2">
          <TypeIcon className="h-4 w-4" />
          <span className="text-xs font-medium uppercase tracking-wide">
            {item.type.replace('-', ' ')}
          </span>
          <span className={cn(
            'rounded px-1.5 py-0.5 text-[10px] font-medium uppercase',
            item.priority === 'high' ? 'bg-destructive text-white' :
            item.priority === 'medium' ? 'bg-warning-color text-white' :
            'bg-primary-100 text-primary-700'
          )}>
            {item.priority}
          </span>
        </div>
        {item.confidence !== undefined && (
          <span className="text-xs font-medium">{item.confidence.toFixed(1)}%</span>
        )}
      </div>
      <div>
        <p className="text-sm font-medium text-text-primary">{item.filePath}</p>
        <p className="text-xs text-text-secondary">{item.reason}</p>
      </div>
      {onReview && (
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => onReview(item.id)}
          className="w-full text-xs"
        >
          Review
        </Button>
      )}
    </div>
  );
}

// ============================================================================
// Utilities
// ============================================================================

interface ConfidenceLevel {
  label: string;
  icon: typeof CheckCircle2;
  bg: string;
  color: string;
  barColor: string;
}

function getConfidenceLevel(confidence: number): ConfidenceLevel {
  if (confidence >= 90) {
    return {
      label: 'High',
      icon: CheckCircle2,
      bg: 'bg-success-bg/10',
      color: 'text-success-color',
      barColor: 'bg-success-color',
    };
  }
  if (confidence >= 70) {
    return {
      label: 'Medium',
      icon: AlertTriangle,
      bg: 'bg-warning-bg/10',
      color: 'text-warning-color',
      barColor: 'bg-warning-color',
    };
  }
  return {
    label: 'Low',
    icon: AlertTriangle,
    bg: 'bg-destructive-bg/10',
    color: 'text-destructive',
    barColor: 'bg-destructive',
  };
}
