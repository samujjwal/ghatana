/**
 * RollbackConfirmDialog Component
 *
 * @description Confirmation dialog for rolling back initialization steps
 * with impact assessment and resource cleanup warnings.
 *
 * @doc.type component
 * @doc.purpose rollback-confirmation
 * @doc.layer ui
 * @doc.phase initialization
 *
 * @example
 * ```tsx
 * <RollbackConfirmDialog
 *   open={showRollback}
 *   onOpenChange={setShowRollback}
 *   step={currentStep}
 *   affectedResources={['PostgreSQL Database', 'S3 Bucket']}
 *   onConfirm={handleRollback}
 * />
 * ```
 */

import React, { useState, useCallback } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 * Step information for rollback
 */
export interface RollbackStep {
  /** Step identifier */
  id: string;
  /** Step name */
  name: string;
  /** When the step was completed */
  completedAt?: Date;
  /** Duration of the step */
  durationMs?: number;
}

/**
 * Resource that will be affected by rollback
 */
export interface AffectedResource {
  /** Resource identifier */
  id: string;
  /** Resource name */
  name: string;
  /** Resource type */
  type: string;
  /** What happens on rollback */
  impact: 'delete' | 'revert' | 'orphan' | 'keep';
  /** Impact description */
  impactDescription: string;
  /** Whether this is a critical resource */
  critical?: boolean;
}

/**
 * Props for the RollbackConfirmDialog component
 */
export interface RollbackConfirmDialogProps {
  /** Whether the dialog is open */
  open: boolean;
  /** Callback when open state changes */
  onOpenChange: (open: boolean) => void;
  /** Step being rolled back */
  step: RollbackStep;
  /** Resources affected by rollback */
  affectedResources: AffectedResource[];
  /** Callback when rollback is confirmed */
  onConfirm: () => void | Promise<void>;
  /** Whether rollback is in progress */
  loading?: boolean;
  /** Title override */
  title?: string;
  /** Description override */
  description?: string;
  /** Confirmation text that user must type */
  confirmationText?: string;
  /** Whether to require confirmation text */
  requireConfirmation?: boolean;
}

// ============================================================================
// Utility Functions
// ============================================================================

const formatDuration = (ms: number): string => {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${Math.round(ms / 1000)}s`;
  const minutes = Math.floor(ms / 60000);
  const seconds = Math.round((ms % 60000) / 1000);
  return `${minutes}m ${seconds}s`;
};

const formatDate = (date: Date): string => {
  return date.toLocaleString(undefined, {
    dateStyle: 'short',
    timeStyle: 'short',
  });
};

const getImpactIcon = (impact: AffectedResource['impact']): React.ReactNode => {
  const icons: Record<typeof impact, React.ReactNode> = {
    delete: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <polyline points="3 6 5 6 21 6" />
        <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
        <line x1="10" y1="11" x2="10" y2="17" />
        <line x1="14" y1="11" x2="14" y2="17" />
      </svg>
    ),
    revert: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <polyline points="1 4 1 10 7 10" />
        <path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10" />
      </svg>
    ),
    orphan: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="12" cy="12" r="10" />
        <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
        <line x1="12" y1="17" x2="12.01" y2="17" />
      </svg>
    ),
    keep: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
        <polyline points="22 4 12 14.01 9 11.01" />
      </svg>
    ),
  };
  return icons[impact];
};

const getImpactColor = (impact: AffectedResource['impact']): string => {
  const colors: Record<typeof impact, string> = {
    delete: '#EF4444',
    revert: '#F59E0B',
    orphan: '#8B5CF6',
    keep: '#10B981',
  };
  return colors[impact];
};

// ============================================================================
// Sub-Components
// ============================================================================

interface ResourceImpactItemProps {
  resource: AffectedResource;
}

const ResourceImpactItem: React.FC<ResourceImpactItemProps> = ({ resource }) => {
  const color = getImpactColor(resource.impact);

  return (
    <div
      className={`rollback-resource ${resource.critical ? 'rollback-resource--critical' : ''}`}
    >
      <div className="rollback-resource-icon" style={{ color }}>
        {getImpactIcon(resource.impact)}
      </div>
      <div className="rollback-resource-info">
        <div className="rollback-resource-header">
          <span className="rollback-resource-name">{resource.name}</span>
          <span className="rollback-resource-type">{resource.type}</span>
        </div>
        <span className="rollback-resource-impact" style={{ color }}>
          {resource.impactDescription}
        </span>
      </div>
      {resource.critical && (
        <span className="rollback-resource-critical-badge">Critical</span>
      )}
    </div>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const RollbackConfirmDialog: React.FC<RollbackConfirmDialogProps> = ({
  open,
  onOpenChange,
  step,
  affectedResources,
  onConfirm,
  loading = false,
  title,
  description,
  confirmationText = 'rollback',
  requireConfirmation = true,
}) => {
  const [confirmInput, setConfirmInput] = useState('');
  const [error, setError] = useState<string | null>(null);

  const hasCriticalResources = affectedResources.some((r) => r.critical);
  const deleteCount = affectedResources.filter((r) => r.impact === 'delete').length;

  const isConfirmDisabled =
    loading || (requireConfirmation && confirmInput.toLowerCase() !== confirmationText.toLowerCase());

  const handleConfirm = useCallback(async () => {
    if (isConfirmDisabled) return;

    setError(null);
    try {
      await onConfirm();
      onOpenChange(false);
      setConfirmInput('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Rollback failed');
    }
  }, [isConfirmDisabled, onConfirm, onOpenChange]);

  const handleCancel = useCallback(() => {
    if (loading) return;
    onOpenChange(false);
    setConfirmInput('');
    setError(null);
  }, [loading, onOpenChange]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape' && !loading) {
        handleCancel();
      }
      if (e.key === 'Enter' && !isConfirmDisabled) {
        handleConfirm();
      }
    },
    [loading, isConfirmDisabled, handleCancel, handleConfirm]
  );

  if (!open) return null;

  return (
    <div
      className="rollback-dialog-overlay"
      onClick={handleCancel}
      onKeyDown={handleKeyDown}
      role="presentation"
    >
      <div
        className="rollback-dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="rollback-dialog-title"
        aria-describedby="rollback-dialog-description"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="rollback-dialog-header">
          <div className="rollback-dialog-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
              <line x1="12" y1="9" x2="12" y2="13" />
              <line x1="12" y1="17" x2="12.01" y2="17" />
            </svg>
          </div>
          <div className="rollback-dialog-title-group">
            <h2 id="rollback-dialog-title" className="rollback-dialog-title">
              {title || `Rollback "${step.name}"?`}
            </h2>
            <p id="rollback-dialog-description" className="rollback-dialog-description">
              {description ||
                'This action will undo the changes made during this step. This cannot be undone.'}
            </p>
          </div>
        </div>

        {/* Step Info */}
        <div className="rollback-step-info">
          <div className="rollback-step-detail">
            <span className="rollback-step-label">Step</span>
            <span className="rollback-step-value">{step.name}</span>
          </div>
          {step.completedAt && (
            <div className="rollback-step-detail">
              <span className="rollback-step-label">Completed</span>
              <span className="rollback-step-value">{formatDate(step.completedAt)}</span>
            </div>
          )}
          {step.durationMs && (
            <div className="rollback-step-detail">
              <span className="rollback-step-label">Duration</span>
              <span className="rollback-step-value">{formatDuration(step.durationMs)}</span>
            </div>
          )}
        </div>

        {/* Affected Resources */}
        {affectedResources.length > 0 && (
          <div className="rollback-resources">
            <h3 className="rollback-resources-title">
              Affected Resources ({affectedResources.length})
            </h3>
            <div className="rollback-resources-list">
              {affectedResources.map((resource) => (
                <ResourceImpactItem key={resource.id} resource={resource} />
              ))}
            </div>
          </div>
        )}

        {/* Warning */}
        {(hasCriticalResources || deleteCount > 0) && (
          <div className="rollback-warning" role="alert">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
            <div>
              {hasCriticalResources && (
                <p>
                  <strong>Critical resources will be affected.</strong> Please review
                  carefully before proceeding.
                </p>
              )}
              {deleteCount > 0 && (
                <p>
                  <strong>{deleteCount} resource{deleteCount > 1 ? 's' : ''}</strong> will
                  be permanently deleted.
                </p>
              )}
            </div>
          </div>
        )}

        {/* Confirmation Input */}
        {requireConfirmation && (
          <div className="rollback-confirmation">
            <label htmlFor="rollback-confirm-input" className="rollback-confirmation-label">
              Type <strong>{confirmationText}</strong> to confirm:
            </label>
            <input
              id="rollback-confirm-input"
              type="text"
              value={confirmInput}
              onChange={(e) => setConfirmInput(e.target.value)}
              placeholder={confirmationText}
              className="rollback-confirmation-input"
              disabled={loading}
              autoComplete="off"
              autoFocus
            />
          </div>
        )}

        {/* Error */}
        {error && (
          <div className="rollback-error" role="alert">
            {error}
          </div>
        )}

        {/* Actions */}
        <div className="rollback-dialog-actions">
          <button
            type="button"
            className="rollback-cancel-btn"
            onClick={handleCancel}
            disabled={loading}
          >
            Cancel
          </button>
          <button
            type="button"
            className="rollback-confirm-btn"
            onClick={handleConfirm}
            disabled={isConfirmDisabled}
          >
            {loading ? (
              <>
                <span className="rollback-spinner" />
                Rolling back...
              </>
            ) : (
              'Confirm Rollback'
            )}
          </button>
        </div>
      </div>

      {/* CSS-in-JS Styles */}
      <style>{`
        .rollback-dialog-overlay {
          position: fixed;
          inset: 0;
          display: flex;
          align-items: center;
          justify-content: center;
          background: rgba(0, 0, 0, 0.5);
          backdrop-filter: blur(2px);
          z-index: 1000;
          padding: 1rem;
        }

        .rollback-dialog {
          width: 100%;
          max-width: 500px;
          max-height: 90vh;
          overflow-y: auto;
          background: #fff;
          border-radius: 12px;
          box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
        }

        .rollback-dialog-header {
          display: flex;
          gap: 1rem;
          padding: 1.5rem 1.5rem 1rem;
        }

        .rollback-dialog-icon {
          width: 40px;
          height: 40px;
          padding: 8px;
          background: #FEE2E2;
          border-radius: 10px;
          color: #DC2626;
          flex-shrink: 0;
        }

        .rollback-dialog-icon svg {
          width: 100%;
          height: 100%;
        }

        .rollback-dialog-title-group {
          flex: 1;
        }

        .rollback-dialog-title {
          margin: 0;
          font-size: 1.125rem;
          font-weight: 600;
          color: #111827;
        }

        .rollback-dialog-description {
          margin: 0.25rem 0 0;
          font-size: 0.875rem;
          color: #6B7280;
        }

        .rollback-step-info {
          display: flex;
          gap: 1.5rem;
          padding: 0.75rem 1.5rem;
          background: #F9FAFB;
          border-top: 1px solid #E5E7EB;
          border-bottom: 1px solid #E5E7EB;
        }

        .rollback-step-detail {
          display: flex;
          flex-direction: column;
        }

        .rollback-step-label {
          font-size: 0.625rem;
          text-transform: uppercase;
          color: #9CA3AF;
        }

        .rollback-step-value {
          font-size: 0.875rem;
          font-weight: 500;
          color: #111827;
        }

        .rollback-resources {
          padding: 1rem 1.5rem;
        }

        .rollback-resources-title {
          margin: 0 0 0.75rem;
          font-size: 0.875rem;
          font-weight: 500;
          color: #374151;
        }

        .rollback-resources-list {
          display: flex;
          flex-direction: column;
          gap: 0.5rem;
          max-height: 200px;
          overflow-y: auto;
        }

        .rollback-resource {
          display: flex;
          align-items: flex-start;
          gap: 0.75rem;
          padding: 0.75rem;
          background: #F9FAFB;
          border-radius: 8px;
        }

        .rollback-resource--critical {
          background: #FEF2F2;
          border: 1px solid #FECACA;
        }

        .rollback-resource-icon {
          width: 20px;
          height: 20px;
          flex-shrink: 0;
        }

        .rollback-resource-icon svg {
          width: 100%;
          height: 100%;
        }

        .rollback-resource-info {
          flex: 1;
          min-width: 0;
        }

        .rollback-resource-header {
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .rollback-resource-name {
          font-size: 0.875rem;
          font-weight: 500;
          color: #111827;
        }

        .rollback-resource-type {
          font-size: 0.625rem;
          padding: 0.125rem 0.375rem;
          background: #E5E7EB;
          color: #6B7280;
          border-radius: 4px;
          text-transform: uppercase;
        }

        .rollback-resource-impact {
          display: block;
          font-size: 0.75rem;
          margin-top: 0.25rem;
        }

        .rollback-resource-critical-badge {
          padding: 0.125rem 0.5rem;
          font-size: 0.625rem;
          font-weight: 600;
          text-transform: uppercase;
          color: #DC2626;
          background: #FEE2E2;
          border-radius: 9999px;
          flex-shrink: 0;
        }

        .rollback-warning {
          display: flex;
          gap: 0.75rem;
          margin: 0 1.5rem;
          padding: 0.75rem 1rem;
          background: #FEF3C7;
          border: 1px solid #FDE68A;
          border-radius: 8px;
          color: #92400E;
        }

        .rollback-warning svg {
          width: 20px;
          height: 20px;
          flex-shrink: 0;
        }

        .rollback-warning p {
          margin: 0;
          font-size: 0.75rem;
        }

        .rollback-warning p + p {
          margin-top: 0.25rem;
        }

        .rollback-confirmation {
          padding: 1rem 1.5rem;
        }

        .rollback-confirmation-label {
          display: block;
          font-size: 0.875rem;
          color: #374151;
          margin-bottom: 0.5rem;
        }

        .rollback-confirmation-input {
          width: 100%;
          padding: 0.5rem 0.75rem;
          font-size: 0.875rem;
          color: #111827;
          background: #fff;
          border: 1px solid #D1D5DB;
          border-radius: 6px;
        }

        .rollback-confirmation-input:focus {
          outline: none;
          border-color: #EF4444;
          box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.2);
        }

        .rollback-confirmation-input:disabled {
          background: #F3F4F6;
          cursor: not-allowed;
        }

        .rollback-error {
          margin: 0 1.5rem;
          padding: 0.75rem 1rem;
          font-size: 0.875rem;
          color: #DC2626;
          background: #FEE2E2;
          border-radius: 6px;
        }

        .rollback-dialog-actions {
          display: flex;
          justify-content: flex-end;
          gap: 0.75rem;
          padding: 1rem 1.5rem;
          border-top: 1px solid #E5E7EB;
        }

        .rollback-cancel-btn,
        .rollback-confirm-btn {
          padding: 0.5rem 1rem;
          font-size: 0.875rem;
          font-weight: 500;
          border-radius: 6px;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .rollback-cancel-btn {
          color: #374151;
          background: #fff;
          border: 1px solid #D1D5DB;
        }

        .rollback-cancel-btn:hover:not(:disabled) {
          background: #F9FAFB;
        }

        .rollback-cancel-btn:disabled {
          opacity: 0.5;
          cursor: not-allowed;
        }

        .rollback-confirm-btn {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          color: #fff;
          background: #DC2626;
          border: none;
        }

        .rollback-confirm-btn:hover:not(:disabled) {
          background: #B91C1C;
        }

        .rollback-confirm-btn:disabled {
          background: #F87171;
          cursor: not-allowed;
        }

        .rollback-spinner {
          width: 16px;
          height: 16px;
          border: 2px solid rgba(255, 255, 255, 0.3);
          border-top-color: #fff;
          border-radius: 50%;
          animation: spin 1s linear infinite;
        }

        @keyframes spin {
          to { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
};

RollbackConfirmDialog.displayName = 'RollbackConfirmDialog';

export default RollbackConfirmDialog;
