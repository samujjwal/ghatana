// @ts-nocheck
/**
 * AgentActionPanel Component
 * 
 * Displays Virtual Agent actions, warnings, and blocking notifications.
 * Allows users to dismiss, override, or act on agent suggestions.
 * 
 * @doc.type component
 * @doc.purpose Virtual Agent notification UI
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState } from 'react';
import { PERSONA_DEFINITIONS, type PersonaType } from '../../context/PersonaContext';
import type { AgentAction, AgentActionSeverity } from '../../services/VirtualAgentService';

interface AgentActionPanelProps {
  actions: AgentAction[];
  onDismiss: (actionId: string) => void;
  onOverride: (actionId: string) => void;
  onAutoFix?: (action: AgentAction) => void;
  compact?: boolean;
  className?: string;
}

const SEVERITY_STYLES: Record<AgentActionSeverity, { bg: string; border: string; icon: string; text: string }> = {
  error: {
    bg: 'bg-destructive-bg dark:bg-destructive-bg',
    border: 'border-destructive-border dark:border-destructive-border',
    icon: '🚫',
    text: 'text-destructive dark:text-destructive',
  },
  warning: {
    bg: 'bg-warning-bg dark:bg-warning-bg',
    border: 'border-warning-border dark:border-warning-border',
    icon: '⚠️',
    text: 'text-warning-color dark:text-warning-color',
  },
  info: {
    bg: 'bg-info-bg dark:bg-info-bg',
    border: 'border-info-border dark:border-info-border',
    icon: '💡',
    text: 'text-info-color dark:text-info-color',
  },
  success: {
    bg: 'bg-success-bg dark:bg-success-bg',
    border: 'border-success-border dark:border-success-border',
    icon: '✅',
    text: 'text-success-color dark:text-success-color',
  },
};

export function AgentActionPanel({
  actions,
  onDismiss,
  onOverride,
  onAutoFix,
  compact = false,
  className = '',
}: AgentActionPanelProps) {
  const [expandedId, setExpandedId] = useState<string | null>(null);

  // Group actions by persona
  const groupedActions = actions.reduce((acc, action) => {
    if (!acc[action.agentPersona]) {
      acc[action.agentPersona] = [];
    }
    acc[action.agentPersona].push(action);
    return acc;
  }, {} as Record<PersonaType, AgentAction[]>);

  // Count by severity
  const errorCount = actions.filter(a => a.severity === 'error').length;
  const warningCount = actions.filter(a => a.severity === 'warning').length;
  const blockingCount = actions.filter(a => a.type === 'block_deploy').length;

  if (actions.length === 0) {
    return (
      <div className={`p-4 text-center text-text-secondary ${className}`}>
        <div className="text-2xl mb-2">✨</div>
        <p className="text-sm">All AI agents report no issues</p>
      </div>
    );
  }

  if (compact) {
    return (
      <div className={`flex items-center gap-2 ${className}`}>
        {blockingCount > 0 && (
          <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full bg-destructive-bg dark:bg-destructive-bg text-destructive dark:text-destructive text-xs font-medium">
            🚫 {blockingCount} blocking
          </span>
        )}
        {errorCount > 0 && blockingCount === 0 && (
          <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full bg-destructive-bg dark:bg-destructive-bg text-destructive dark:text-destructive text-xs font-medium">
            {errorCount} error{errorCount > 1 ? 's' : ''}
          </span>
        )}
        {warningCount > 0 && (
          <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full bg-warning-bg dark:bg-warning-bg text-warning-color dark:text-warning-color text-xs font-medium">
            {warningCount} warning{warningCount > 1 ? 's' : ''}
          </span>
        )}
        {actions.length - errorCount - warningCount > 0 && (
          <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full bg-info-bg dark:bg-info-bg text-info-color dark:text-info-color text-xs font-medium">
            {actions.length - errorCount - warningCount} suggestion{actions.length - errorCount - warningCount > 1 ? 's' : ''}
          </span>
        )}
      </div>
    );
  }

  return (
    <div className={`space-y-4 ${className}`}>
      {/* Summary Header */}
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-text-primary">AI Agent Reports</h3>
        <div className="flex items-center gap-2">
          {blockingCount > 0 && (
            <span className="text-xs px-2 py-0.5 rounded-full bg-destructive-bg dark:bg-destructive-bg text-destructive dark:text-destructive font-medium">
              {blockingCount} blocking
            </span>
          )}
        </div>
      </div>

      {/* Blocking Actions (always shown first) */}
      {blockingCount > 0 && (
        <div className="p-3 rounded-lg bg-destructive-bg dark:bg-destructive-bg border border-destructive-border dark:border-destructive-border">
          <div className="flex items-start gap-2">
            <span className="text-lg">🚫</span>
            <div className="flex-1">
              <h4 className="text-sm font-semibold text-destructive dark:text-destructive">
                Deploy Blocked
              </h4>
              <p className="text-xs text-destructive dark:text-destructive mt-0.5">
                {blockingCount} issue{blockingCount > 1 ? 's' : ''} must be resolved or overridden before deploying.
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Actions grouped by persona */}
      {Object.entries(groupedActions).map(([persona, personaActions]) => {
        const personaDef = PERSONA_DEFINITIONS[persona as PersonaType];
        
        return (
          <div key={persona} className="space-y-2">
            {/* Persona Header */}
            <div className="flex items-center gap-2">
              <div
                className="w-6 h-6 rounded-full flex items-center justify-center text-sm"
                style={{ backgroundColor: `${personaDef.color}20` }}
              >
                {personaDef.icon}
              </div>
              <span className="text-xs font-medium text-text-secondary">
                Virtual {personaDef.shortName}
              </span>
              <span className="text-xs text-text-tertiary">
                ({personaActions.length})
              </span>
            </div>

            {/* Actions */}
            <div className="space-y-2 pl-8">
              {personaActions.map((action) => {
                const styles = SEVERITY_STYLES[action.severity];
                const isExpanded = expandedId === action.id;

                return (
                  <div
                    key={action.id}
                    className={`
                      rounded-lg border ${styles.bg} ${styles.border}
                      transition-all duration-200
                    `}
                  >
                    {/* Action Header */}
                    <button
                      className="w-full px-3 py-2 flex items-start gap-2 text-left"
                      onClick={() => setExpandedId(isExpanded ? null : action.id)}
                    >
                      <span className="text-sm mt-0.5">{styles.icon}</span>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <span className={`text-sm font-medium ${styles.text}`}>
                            {action.title}
                          </span>
                          {action.type === 'block_deploy' && (
                            <span className="text-[10px] px-1.5 py-0.5 rounded bg-destructive-bg dark:bg-destructive-bg text-destructive dark:text-destructive font-medium">
                              BLOCKING
                            </span>
                          )}
                        </div>
                        {!isExpanded && (
                          <p className="text-xs text-text-secondary truncate mt-0.5">
                            {action.message}
                          </p>
                        )}
                      </div>
                      <svg
                        className={`w-4 h-4 text-text-secondary transition-transform ${isExpanded ? 'rotate-180' : ''}`}
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                      >
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                      </svg>
                    </button>

                    {/* Expanded Content */}
                    {isExpanded && (
                      <div className="px-3 pb-3 pt-1 border-t border-divider/50">
                        <p className="text-sm text-text-secondary mb-3">
                          {action.message}
                        </p>

                        {action.targetId && (
                          <p className="text-xs text-text-tertiary mb-3">
                            Target: <code className="px-1 py-0.5 rounded bg-grey-100 dark:bg-grey-800">{action.targetId}</code>
                          </p>
                        )}

                        <div className="flex items-center gap-2">
                          {action.autoFixAvailable && onAutoFix && (
                            <button
                              onClick={() => onAutoFix(action)}
                              className="px-3 py-1.5 text-xs font-medium rounded-md bg-primary-600 text-white hover:bg-primary-700 transition-colors"
                            >
                              Auto-Fix
                            </button>
                          )}
                          
                          {action.requiresHumanOverride && (
                            <button
                              onClick={() => onOverride(action.id)}
                              className="px-3 py-1.5 text-xs font-medium rounded-md bg-warning-bg text-white hover:bg-warning-bg transition-colors"
                            >
                              Override & Continue
                            </button>
                          )}

                          <button
                            onClick={() => onDismiss(action.id)}
                            className="px-3 py-1.5 text-xs font-medium rounded-md border border-divider text-text-secondary hover:bg-grey-50 dark:hover:bg-grey-800 transition-colors"
                          >
                            Dismiss
                          </button>
                        </div>

                        <p className="text-[10px] text-text-tertiary mt-2">
                          {new Date(action.timestamp).toLocaleTimeString()}
                        </p>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
}

/**
 * AgentActionBadge - Compact indicator for toolbar/status bar
 */
export function AgentActionBadge({
  actions,
  onClick,
  className = '',
}: {
  actions: AgentAction[];
  onClick?: () => void;
  className?: string;
}) {
  const blockingCount = actions.filter(a => a.type === 'block_deploy' && !a.dismissed).length;
  const errorCount = actions.filter(a => a.severity === 'error' && !a.dismissed).length;
  const warningCount = actions.filter(a => a.severity === 'warning' && !a.dismissed).length;
  const totalActive = actions.filter(a => !a.dismissed).length;

  if (totalActive === 0) {
    return (
      <button
        onClick={onClick}
        className={`flex items-center gap-1.5 px-2 py-1 rounded-md text-xs text-success-color dark:text-success-color hover:bg-success-bg dark:hover:bg-success-bg transition-colors ${className}`}
      >
        <span>✓</span>
        <span>AI OK</span>
      </button>
    );
  }

  const bgColor = blockingCount > 0 || errorCount > 0
    ? 'bg-destructive-bg dark:bg-destructive-bg hover:bg-destructive-bg dark:hover:bg-destructive-bg'
    : warningCount > 0
    ? 'bg-warning-bg dark:bg-warning-bg hover:bg-warning-bg dark:hover:bg-warning-bg'
    : 'bg-info-bg dark:bg-info-bg hover:bg-info-bg dark:hover:bg-info-bg';

  const textColor = blockingCount > 0 || errorCount > 0
    ? 'text-destructive dark:text-destructive'
    : warningCount > 0
    ? 'text-warning-color dark:text-warning-color'
    : 'text-info-color dark:text-info-color';

  return (
    <button
      onClick={onClick}
      className={`flex items-center gap-1.5 px-2 py-1 rounded-md text-xs font-medium transition-colors ${bgColor} ${textColor} ${className}`}
    >
      {blockingCount > 0 ? (
        <>
          <span>🚫</span>
          <span>{blockingCount} blocking</span>
        </>
      ) : (
        <>
          <span>🤖</span>
          <span>{totalActive} AI issue{totalActive > 1 ? 's' : ''}</span>
        </>
      )}
    </button>
  );
}

export default AgentActionPanel;
