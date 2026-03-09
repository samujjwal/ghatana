/**
 * Error Panel Component
 *
 * Displays validation issues with filtering and navigation.
 *
 * @module canvas/validation/ErrorPanel
 */

import React, { useState, useMemo } from 'react';

import type { ValidationIssue, ValidationResult, ValidationSeverity } from './CanvasValidator';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export interface ErrorPanelProps {
  /**
   * Validation result
   */
  validationResult: ValidationResult;

  /**
   * Callback when issue is clicked (to navigate to node)
   */
  onIssueClick?: (issue: ValidationIssue) => void;

  /**
   * Callback when auto-fix is requested
   */
  onAutoFix?: (issue: ValidationIssue) => void;

  /**
   * Show filters
   */
  showFilters?: boolean;

  /**
   * Collapsible
   */
  collapsible?: boolean;
}

// ============================================================================
// Error Panel Component
// ============================================================================

export const ErrorPanel: React.FC<ErrorPanelProps> = ({
  validationResult,
  onIssueClick,
  onAutoFix,
  showFilters = true,
  collapsible = true,
}) => {
  const [isExpanded, setIsExpanded] = useState(true);
  const [severityFilter, setSeverityFilter] = useState<ValidationSeverity | 'all'>('all');
  const [categoryFilter, setCategoryFilter] = useState<ValidationIssue['category'] | 'all'>('all');

  // Filter issues
  const filteredIssues = useMemo(() => {
    let issues = validationResult.issues;

    if (severityFilter !== 'all') {
      issues = issues.filter((i) => i.severity === severityFilter);
    }

    if (categoryFilter !== 'all') {
      issues = issues.filter((i) => i.category === categoryFilter);
    }

    return issues;
  }, [validationResult.issues, severityFilter, categoryFilter]);

  // Get icon for severity
  const getSeverityIcon = (severity: ValidationSeverity) => {
    switch (severity) {
      case 'error':
        return '❌';
      case 'warning':
        return '⚠️';
      case 'info':
        return 'ℹ️';
    }
  };

  // Get color for severity
  const getSeverityColor = (severity: ValidationSeverity) => {
    switch (severity) {
      case 'error':
        return '#f44336';
      case 'warning':
        return '#ff9800';
      case 'info':
        return '#2196f3';
    }
  };

  if (validationResult.issues.length === 0) {
    return (
      <div
        style={{
          padding: 16,
          backgroundColor: '#e8f5e9',
          border: '1px solid #4caf50',
          borderRadius: 8,
          textAlign: 'center',
        }}
      >
        <div style={{ fontSize: 32, marginBottom: 8 }}>✅</div>
        <div style={{ fontSize: 14, fontWeight: 600, color: '#2e7d32' }}>
          No Issues Found
        </div>
        <div style={{ fontSize: 12, color: '#4caf50', marginTop: 4 }}>
          Canvas is valid and ready to use
        </div>
      </div>
    );
  }

  return (
    <div
      style={{
        backgroundColor: '#fff',
        border: '1px solid #e0e0e0',
        borderRadius: 8,
        overflow: 'hidden',
      }}
    >
      {/* Header */}
      <div
        style={{
          padding: 16,
          backgroundColor: validationResult.valid ? '#fff3cd' : '#ffebee',
          borderBottom: '1px solid #e0e0e0',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}
      >
        <div style={{ flex: 1 }}>
          <h3 style={{ margin: '0 0 4px', fontSize: 16, fontWeight: 600 }}>
            Validation Results
          </h3>
          <div style={{ display: 'flex', gap: 16, fontSize: 12 }}>
            {validationResult.errorCount > 0 && (
              <span style={{ color: '#f44336', fontWeight: 500 }}>
                {validationResult.errorCount} Error{validationResult.errorCount !== 1 ? 's' : ''}
              </span>
            )}
            {validationResult.warningCount > 0 && (
              <span style={{ color: '#ff9800', fontWeight: 500 }}>
                {validationResult.warningCount} Warning{validationResult.warningCount !== 1 ? 's' : ''}
              </span>
            )}
            {validationResult.infoCount > 0 && (
              <span style={{ color: '#2196f3', fontWeight: 500 }}>
                {validationResult.infoCount} Info
              </span>
            )}
          </div>
        </div>
        {collapsible && (
          <button
            onClick={() => setIsExpanded(!isExpanded)}
            style={{
              padding: '6px 12px',
              backgroundColor: 'transparent',
              border: 'none',
              cursor: 'pointer',
              fontSize: 14,
            }}
          >
            {isExpanded ? '▼' : '▶'}
          </button>
        )}
      </div>

      {isExpanded && (
        <>
          {/* Filters */}
          {showFilters && (
            <div
              style={{
                padding: 12,
                backgroundColor: '#f5f5f5',
                borderBottom: '1px solid #e0e0e0',
                display: 'flex',
                gap: 12,
              }}
            >
              <div style={{ flex: 1 }}>
                <label style={{ display: 'block', marginBottom: 4, fontSize: 11, fontWeight: 500 }}>
                  Severity
                </label>
                <select
                  value={severityFilter}
                  onChange={(e) => setSeverityFilter(e.target.value as unknown)}
                  style={{
                    width: '100%',
                    padding: '4px 8px',
                    border: '1px solid #ccc',
                    borderRadius: 4,
                    fontSize: 12,
                  }}
                >
                  <option value="all">All ({validationResult.issues.length})</option>
                  <option value="error">Errors ({validationResult.errorCount})</option>
                  <option value="warning">Warnings ({validationResult.warningCount})</option>
                  <option value="info">Info ({validationResult.infoCount})</option>
                </select>
              </div>
              <div style={{ flex: 1 }}>
                <label style={{ display: 'block', marginBottom: 4, fontSize: 11, fontWeight: 500 }}>
                  Category
                </label>
                <select
                  value={categoryFilter}
                  onChange={(e) => setCategoryFilter(e.target.value as unknown)}
                  style={{
                    width: '100%',
                    padding: '4px 8px',
                    border: '1px solid #ccc',
                    borderRadius: 4,
                    fontSize: 12,
                  }}
                >
                  <option value="all">All</option>
                  <option value="component">Component</option>
                  <option value="binding">Binding</option>
                  <option value="event">Event</option>
                  <option value="accessibility">Accessibility</option>
                  <option value="performance">Performance</option>
                </select>
              </div>
            </div>
          )}

          {/* Issues List */}
          <div style={{ maxHeight: 400, overflow: 'auto' }}>
            {filteredIssues.length === 0 ? (
              <div style={{ padding: 24, textAlign: 'center', color: '#999', fontSize: 13 }}>
                No issues match the selected filters
              </div>
            ) : (
              filteredIssues.map((issue) => (
                <div
                  key={issue.id}
                  onClick={() => onIssueClick?.(issue)}
                  style={{
                    padding: 12,
                    borderBottom: '1px solid #f0f0f0',
                    cursor: onIssueClick ? 'pointer' : 'default',
                    transition: 'background-color 0.2s',
                  }}
                  onMouseEnter={(e) => {
                    if (onIssueClick) {
                      e.currentTarget.style.backgroundColor = '#f9f9f9';
                    }
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.backgroundColor = 'transparent';
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'start', gap: 12 }}>
                    {/* Icon */}
                    <div style={{ fontSize: 20 }}>{getSeverityIcon(issue.severity)}</div>

                    {/* Content */}
                    <div style={{ flex: 1 }}>
                      <div
                        style={{
                          fontSize: 13,
                          fontWeight: 500,
                          color: getSeverityColor(issue.severity),
                          marginBottom: 4,
                        }}
                      >
                        {issue.message}
                      </div>
                      <div style={{ display: 'flex', gap: 12, fontSize: 11, color: '#666' }}>
                        <span style={{ textTransform: 'capitalize' }}>{issue.category}</span>
                        {issue.nodeId && <span>Node: {issue.nodeId}</span>}
                      </div>
                      {issue.suggestion && (
                        <div
                          style={{
                            marginTop: 6,
                            padding: 8,
                            backgroundColor: '#e3f2fd',
                            borderRadius: 4,
                            fontSize: 12,
                          }}
                        >
                          💡 {issue.suggestion}
                        </div>
                      )}
                    </div>

                    {/* Actions */}
                    {issue.autoFixable && onAutoFix && (
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          onAutoFix(issue);
                        }}
                        style={{
                          padding: '4px 12px',
                          backgroundColor: '#1976d2',
                          color: '#fff',
                          border: 'none',
                          borderRadius: 4,
                          cursor: 'pointer',
                          fontSize: 11,
                          fontWeight: 500,
                        }}
                      >
                        Fix
                      </button>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>

          {/* Footer */}
          <div
            style={{
              padding: 12,
              backgroundColor: '#f5f5f5',
              borderTop: '1px solid #e0e0e0',
              fontSize: 11,
              color: '#666',
              display: 'flex',
              justifyContent: 'space-between',
            }}
          >
            <span>
              Showing {filteredIssues.length} of {validationResult.issues.length} issues
            </span>
            {onAutoFix && (
              <span>
                {validationResult.issues.filter((i) => i.autoFixable).length} auto-fixable
              </span>
            )}
          </div>
        </>
      )}
    </div>
  );
};
