/**
 * @fileoverview Accessibility Report Viewer Component
 * @module @ghatana/accessibility-audit
 * 
 * A React component for displaying accessibility audit results in a user-friendly format
 */

import React, { useState, useMemo, useCallback } from 'react';

import type { AccessibilityReport, Finding, ViolationSeverity } from './types';

/**
 * Props for AccessibilityReportViewer component
 */
export interface AccessibilityReportViewerProps {
  /** The accessibility report to display */
  report: AccessibilityReport;
  
  /** Whether to show all findings or just a summary */
  showAllFindings?: boolean;
  
  /** Callback when a finding is clicked */
  onFindingClick?: (finding: Finding) => void;
  
  /** Custom class name for the container */
  className?: string;
  
  /** Maximum number of findings to show initially */
  initialFindingsLimit?: number;
}

/**
 * A component that displays accessibility audit results in a user-friendly way
 * 
 * Features:
 * - Score display with color-coded grades
 * - Expandable findings grouped by severity
 * - Category filtering
 * - Code snippets and remediation guidance
 * - Responsive design
 * 
 * @example
 * ```tsx
 * <AccessibilityReportViewer 
 *   report={auditReport}
 *   onFindingClick={(finding) => console.log(finding)}
 * />
 * ```
 */
export const AccessibilityReportViewer: React.FC<AccessibilityReportViewerProps> = ({
  report,
  showAllFindings = true,
  onFindingClick,
  className = '',
  initialFindingsLimit = 10,
}) => {
  const [expandedFindings, setExpandedFindings] = useState<Set<string>>(new Set());
  const [selectedSeverity, setSelectedSeverity] = useState<ViolationSeverity | 'all'>('all');
  const [showMore, setShowMore] = useState(false);
  
  // Group findings by severity
  const findingsBySeverity = useMemo(() => {
    return {
      critical: report.findings.filter(f => f.severity === 'critical'),
      serious: report.findings.filter(f => f.severity === 'serious'),
      moderate: report.findings.filter(f => f.severity === 'moderate'),
      minor: report.findings.filter(f => f.severity === 'minor'),
    };
  }, [report.findings]);
  
  // Filter findings based on selected severity
  const filteredFindings = useMemo(() => {
    if (selectedSeverity === 'all') return report.findings;
    return findingsBySeverity[selectedSeverity];
  }, [report.findings, selectedSeverity, findingsBySeverity]);
  
  // Limit findings if not showing all
  const displayedFindings = useMemo(() => {
    if (showAllFindings || showMore) return filteredFindings;
    return filteredFindings.slice(0, initialFindingsLimit);
  }, [filteredFindings, showAllFindings, showMore, initialFindingsLimit]);
  
  // Toggle finding expansion
  const toggleFinding = useCallback((id: string) => {
    setExpandedFindings(prev => {
      const newSet = new Set(prev);
      if (newSet.has(id)) {
        newSet.delete(id);
      } else {
        newSet.add(id);
      }
      return newSet;
    });
  }, []);
  
  // Get color for severity
  const getSeverityColor = (severity: ViolationSeverity): string => {
    const colors = {
      critical: '#dc2626',
      serious: '#ea580c',
      moderate: '#eab308',
      minor: '#3b82f6',
    };
    return colors[severity];
  };
  
  // Get emoji for severity
  const getSeverityEmoji = (severity: ViolationSeverity): string => {
    const emojis = {
      critical: '🔴',
      serious: '🟠',
      moderate: '🟡',
      minor: '🔵',
    };
    return emojis[severity];
  };
  
  // Get grade color
  const getGradeColor = (grade: string): string => {
    if (grade.startsWith('A')) return '#10b981';
    if (grade.startsWith('B')) return '#3b82f6';
    if (grade.startsWith('C')) return '#eab308';
    if (grade === 'D') return '#ea580c';
    return '#dc2626';
  };
  
  return (
    <div className={`accessibility-report-viewer ${className}`} style={{ fontFamily: 'system-ui, sans-serif' }}>
      {/* Score Card */}
      <div style={{
        backgroundColor: '#f9fafb',
        border: '1px solid #e5e7eb',
        borderRadius: '8px',
        padding: '24px',
        marginBottom: '24px',
      }}>
        <h2 style={{ margin: '0 0 16px 0', fontSize: '24px', fontWeight: 'bold' }}>
          Accessibility Score
        </h2>
        
        <div style={{ display: 'flex', alignItems: 'center', gap: '24px', marginBottom: '16px' }}>
          <div style={{
            fontSize: '48px',
            fontWeight: 'bold',
            color: getGradeColor(report.score.grade),
          }}>
            {report.score.overall}/100
          </div>
          
          <div>
            <div style={{
              display: 'inline-block',
              backgroundColor: getGradeColor(report.score.grade),
              color: 'white',
              padding: '8px 16px',
              borderRadius: '4px',
              fontSize: '24px',
              fontWeight: 'bold',
              marginBottom: '8px',
            }}>
              {report.score.grade}
            </div>
            <div style={{ fontSize: '14px', color: '#6b7280' }}>
              {report.score.complianceLevel}
            </div>
          </div>
        </div>
        
        <div style={{ fontSize: '14px', color: '#6b7280' }}>
          <div>📄 {report.target.title}</div>
          <div>🔗 {report.target.url}</div>
          <div>📅 {new Date(report.metadata.timestamp).toLocaleString()}</div>
        </div>
      </div>
      
      {/* Summary Statistics */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
        gap: '16px',
        marginBottom: '24px',
      }}>
        <div style={{
          backgroundColor: '#fef2f2',
          border: '1px solid #fecaca',
          borderRadius: '8px',
          padding: '16px',
        }}>
          <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#dc2626' }}>
            {report.summary.bySeverity.critical}
          </div>
          <div style={{ fontSize: '14px', color: '#991b1b' }}>Critical Issues</div>
        </div>
        
        <div style={{
          backgroundColor: '#fff7ed',
          border: '1px solid #fed7aa',
          borderRadius: '8px',
          padding: '16px',
        }}>
          <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#ea580c' }}>
            {report.summary.bySeverity.serious}
          </div>
          <div style={{ fontSize: '14px', color: '#9a3412' }}>Serious Issues</div>
        </div>
        
        <div style={{
          backgroundColor: '#fefce8',
          border: '1px solid #fef08a',
          borderRadius: '8px',
          padding: '16px',
        }}>
          <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#ca8a04' }}>
            {report.summary.bySeverity.moderate}
          </div>
          <div style={{ fontSize: '14px', color: '#713f12' }}>Moderate Issues</div>
        </div>
        
        <div style={{
          backgroundColor: '#eff6ff',
          border: '1px solid #bfdbfe',
          borderRadius: '8px',
          padding: '16px',
        }}>
          <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#3b82f6' }}>
            {report.summary.bySeverity.minor}
          </div>
          <div style={{ fontSize: '14px', color: '#1e40af' }}>Minor Issues</div>
        </div>
      </div>
      
      {/* Severity Filter */}
      <div style={{ marginBottom: '16px' }}>
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          {(['all', 'critical', 'serious', 'moderate', 'minor'] as const).map(severity => (
            <button
              key={severity}
              onClick={() => setSelectedSeverity(severity)}
              style={{
                padding: '8px 16px',
                borderRadius: '4px',
                border: selectedSeverity === severity ? 'none' : '1px solid #e5e7eb',
                backgroundColor: selectedSeverity === severity ? '#3b82f6' : 'white',
                color: selectedSeverity === severity ? 'white' : '#374151',
                cursor: 'pointer',
                fontSize: '14px',
                fontWeight: selectedSeverity === severity ? 'bold' : 'normal',
              }}
            >
              {severity === 'all' ? 'All' : `${severity.charAt(0).toUpperCase() + severity.slice(1)}`}
              {severity !== 'all' && ` (${findingsBySeverity[severity].length})`}
            </button>
          ))}
        </div>
      </div>
      
      {/* Findings List */}
      {displayedFindings.length === 0 ? (
        <div style={{
          padding: '48px',
          textAlign: 'center',
          backgroundColor: '#f0fdf4',
          border: '1px solid #86efac',
          borderRadius: '8px',
        }}>
          <div style={{ fontSize: '48px', marginBottom: '16px' }}>✅</div>
          <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#15803d' }}>
            No {selectedSeverity !== 'all' ? selectedSeverity : ''} accessibility issues found!
          </div>
        </div>
      ) : (
        <>
          <div style={{ marginBottom: '16px' }}>
            {displayedFindings.map((finding, _index) => {
              const isExpanded = expandedFindings.has(finding.id);
              
              return (
                <div
                  key={finding.id}
                  style={{
                    backgroundColor: 'white',
                    border: '1px solid #e5e7eb',
                    borderLeft: `4px solid ${getSeverityColor(finding.severity)}`,
                    borderRadius: '8px',
                    marginBottom: '12px',
                    overflow: 'hidden',
                  }}
                >
                  {/* Finding Header */}
                  <div
                    style={{
                      padding: '16px',
                      cursor: 'pointer',
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'flex-start',
                    }}
                    onClick={() => toggleFinding(finding.id)}
                  >
                    <div style={{ flex: 1 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                        <span style={{ fontSize: '16px' }}>
                          {getSeverityEmoji(finding.severity)}
                        </span>
                        <span style={{
                          fontSize: '12px',
                          fontWeight: 'bold',
                          textTransform: 'uppercase',
                          color: getSeverityColor(finding.severity),
                        }}>
                          {finding.severity}
                        </span>
                      </div>
                      
                      <div style={{ fontSize: '16px', fontWeight: 'bold', marginBottom: '4px' }}>
                        {finding.help}
                      </div>
                      
                      <div style={{ fontSize: '14px', color: '#6b7280' }}>
                        {finding.location.selector}
                      </div>
                    </div>
                    
                    <div style={{ fontSize: '20px', color: '#9ca3af', marginLeft: '16px' }}>
                      {isExpanded ? '−' : '+'}
                    </div>
                  </div>
                  
                  {/* Expanded Details */}
                  {isExpanded && (
                    <div style={{
                      padding: '0 16px 16px 16px',
                      borderTop: '1px solid #e5e7eb',
                      backgroundColor: '#f9fafb',
                    }}>
                      <div style={{ marginTop: '16px' }}>
                        <h4 style={{ margin: '0 0 8px 0', fontSize: '14px', fontWeight: 'bold' }}>
                          Description
                        </h4>
                        <p style={{ margin: 0, fontSize: '14px', color: '#374151' }}>
                          {finding.description}
                        </p>
                      </div>
                      
                      <div style={{ marginTop: '16px' }}>
                        <h4 style={{ margin: '0 0 8px 0', fontSize: '14px', fontWeight: 'bold' }}>
                          Code Snippet
                        </h4>
                        <pre style={{
                          backgroundColor: '#1f2937',
                          color: '#e5e7eb',
                          padding: '12px',
                          borderRadius: '4px',
                          fontSize: '12px',
                          overflow: 'auto',
                        }}>
                          {finding.location.snippet}
                        </pre>
                      </div>
                      
                      <div style={{ marginTop: '16px' }}>
                        <h4 style={{ margin: '0 0 8px 0', fontSize: '14px', fontWeight: 'bold' }}>
                          How to Fix
                        </h4>
                        <p style={{ margin: '0 0 8px 0', fontSize: '14px', color: '#374151' }}>
                          {finding.remediation.description}
                        </p>
                        {finding.remediation.steps.length > 0 && (
                          <ol style={{ margin: 0, paddingLeft: '20px', fontSize: '14px' }}>
                            {finding.remediation.steps.map((step, idx) => (
                              <li key={idx} style={{ marginBottom: '4px' }}>{step}</li>
                            ))}
                          </ol>
                        )}
                      </div>
                      
                      {finding.remediation.codeExample && (
                        <div style={{ marginTop: '16px' }}>
                          <h4 style={{ margin: '0 0 8px 0', fontSize: '14px', fontWeight: 'bold' }}>
                            Example Fix
                          </h4>
                          <pre style={{
                            backgroundColor: '#1f2937',
                            color: '#10b981',
                            padding: '12px',
                            borderRadius: '4px',
                            fontSize: '12px',
                            overflow: 'auto',
                          }}>
                            {finding.remediation.codeExample}
                          </pre>
                        </div>
                      )}
                      
                      <div style={{ marginTop: '16px' }}>
                        <h4 style={{ margin: '0 0 8px 0', fontSize: '14px', fontWeight: 'bold' }}>
                          WCAG Information
                        </h4>
                        <div style={{ fontSize: '14px', color: '#374151' }}>
                          <div>Level: WCAG {finding.wcag.level}</div>
                          <div>Criterion: {finding.wcag.criterion}</div>
                          <div>Principle: {finding.wcag.principle}</div>
                        </div>
                      </div>
                      
                      <div style={{ marginTop: '16px', display: 'flex', gap: '8px' }}>
                        <a
                          href={finding.helpUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          style={{
                            display: 'inline-block',
                            padding: '8px 16px',
                            backgroundColor: '#3b82f6',
                            color: 'white',
                            textDecoration: 'none',
                            borderRadius: '4px',
                            fontSize: '14px',
                            fontWeight: 'bold',
                          }}
                        >
                          Learn More
                        </a>
                        
                        {onFindingClick && (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              onFindingClick(finding);
                            }}
                            style={{
                              padding: '8px 16px',
                              backgroundColor: 'white',
                              color: '#3b82f6',
                              border: '1px solid #3b82f6',
                              borderRadius: '4px',
                              fontSize: '14px',
                              fontWeight: 'bold',
                              cursor: 'pointer',
                            }}
                          >
                            View Element
                          </button>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
          
          {!showAllFindings && filteredFindings.length > initialFindingsLimit && !showMore && (
            <button
              onClick={() => setShowMore(true)}
              style={{
                width: '100%',
                padding: '12px',
                backgroundColor: 'white',
                border: '1px solid #e5e7eb',
                borderRadius: '8px',
                fontSize: '14px',
                fontWeight: 'bold',
                cursor: 'pointer',
                color: '#3b82f6',
              }}
            >
              Show {filteredFindings.length - initialFindingsLimit} More
            </button>
          )}
        </>
      )}
      
      {/* Recommendations */}
      {report.recommendations && (
        <div style={{
          marginTop: '24px',
          backgroundColor: '#eff6ff',
          border: '1px solid #bfdbfe',
          borderRadius: '8px',
          padding: '16px',
        }}>
          <h3 style={{ margin: '0 0 16px 0', fontSize: '18px', fontWeight: 'bold' }}>
            💡 Recommendations
          </h3>
          
          {report.recommendations.immediate.length > 0 && (
            <div style={{ marginBottom: '16px' }}>
              <h4 style={{ margin: '0 0 8px 0', fontSize: '14px', fontWeight: 'bold', color: '#dc2626' }}>
                ⚡ Immediate
              </h4>
              <ul style={{ margin: 0, paddingLeft: '20px', fontSize: '14px' }}>
                {report.recommendations.immediate.map((rec, idx) => (
                  <li key={idx}>{rec}</li>
                ))}
              </ul>
            </div>
          )}
          
          {report.recommendations.shortTerm.length > 0 && (
            <div style={{ marginBottom: '16px' }}>
              <h4 style={{ margin: '0 0 8px 0', fontSize: '14px', fontWeight: 'bold', color: '#ea580c' }}>
                📅 Short-term
              </h4>
              <ul style={{ margin: 0, paddingLeft: '20px', fontSize: '14px' }}>
                {report.recommendations.shortTerm.map((rec, idx) => (
                  <li key={idx}>{rec}</li>
                ))}
              </ul>
            </div>
          )}
          
          {report.recommendations.longTerm.length > 0 && (
            <div>
              <h4 style={{ margin: '0 0 8px 0', fontSize: '14px', fontWeight: 'bold', color: '#3b82f6' }}>
                🎯 Long-term
              </h4>
              <ul style={{ margin: 0, paddingLeft: '20px', fontSize: '14px' }}>
                {report.recommendations.longTerm.map((rec, idx) => (
                  <li key={idx}>{rec}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
