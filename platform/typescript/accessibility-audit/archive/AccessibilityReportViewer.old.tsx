import React, { useEffect, useState } from 'react';
import type { AccessibilityReport, Finding } from './types';

interface AccessibilityReportViewerProps {
  /** The accessibility report to display */
  report: AccessibilityReport;
  
  /** Whether to show all findings or just a summary */
  showAllFindings?: boolean;
  
  /** Callback when a finding is clicked */
  onFindingClick?: (finding: Finding) => void;
  
  /** Custom class name for the container */
  className?: string;
}

/**
 * A component that displays accessibility audit results in a user-friendly way
 */
export const AccessibilityReportViewer: React.FC<AccessibilityReportViewerProps> = ({
  report,
  showAllFindings = true,
  onFindingClick,
  className = '',
}) => {
  const [expandedFindings, setExpandedFindings] = useState<Set<string>>(new Set());
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  
  // Get unique categories from findings
  const categories = React.useMemo(() => {
    const cats = new Set<string>();
    report.findings.forEach(finding => {
      finding.tags.forEach(tag => cats.add(tag));
    });
    return Array.from(cats).sort();
  }, [report.findings]);
  
  // Filter findings by selected category
  const filteredFindings = React.useMemo(() => {
    if (!selectedCategory) return report.findings;
    return report.findings.filter(finding => 
      finding.tags.includes(selectedCategory)
    );
  }, [report.findings, selectedCategory]);
  
  // Toggle expansion of a finding
  const toggleFinding = (id: string) => {
    setExpandedFindings(prev => {
      const newSet = new Set(prev);
      if (newSet.has(id)) {
        newSet.delete(id);
      } else {
        newSet.add(id);
      }
      return newSet;
    });
  };
  
  // Get impact badge color
  const getImpactColor = (impact: string) => {
    switch (impact) {
      case 'critical':
        return 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200';
      case 'serious':
        return 'bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-200';
      case 'moderate':
        return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200';
      case 'minor':
      default:
        return 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200';
    }
  };
  
  // Get impact icon
  const getImpactIcon = (impact: string) => {
    switch (impact) {
      case 'critical':
        return '🚨';
      case 'serious':
        return '⚠️';
      case 'moderate':
        return 'ℹ️';
      case 'minor':
      default:
        return '🔍';
    }
  };

  return (
    <div className={`accessibility-report ${className} bg-white dark:bg-gray-900 rounded-lg shadow overflow-hidden`}>
      {/* Header */}
      <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
              Accessibility Audit Results
            </h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">
              {new Date(report.timestamp).toLocaleString()}
            </p>
          </div>
          <div className="flex items-center space-x-2">
            <span className="px-2 py-1 text-xs font-medium rounded-full bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200">
              {report.summary.critical} Critical
            </span>
            <span className="px-2 py-1 text-xs font-medium rounded-full bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-200">
              {report.summary.serious} Serious
            </span>
            <span className="px-2 py-1 text-xs font-medium rounded-full bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200">
              {report.summary.moderate} Moderate
            </span>
            <span className="px-2 py-1 text-xs font-medium rounded-full bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200">
              {report.summary.minor} Minor
            </span>
          </div>
        </div>
      </div>
      
      {/* Category Filter */}
      {categories.length > 0 && (
        <div className="px-6 py-3 bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
          <div className="flex flex-wrap gap-2">
            <button
              onClick={() => setSelectedCategory(null)}
              className={`px-3 py-1 text-sm rounded-full ${
                selectedCategory === null
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-200 text-gray-700 hover:bg-gray-300 dark:bg-gray-700 dark:text-gray-200 dark:hover:bg-gray-600'
              }`}
            >
              All ({report.violations.length})
            </button>
            {categories.map(category => {
              const count = report.violations.filter(v => v.tags.includes(category)).length;
              return (
                <button
                  key={category}
                  onClick={() => setSelectedCategory(category)}
                  className={`px-3 py-1 text-sm rounded-full ${
                    selectedCategory === category
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-200 text-gray-700 hover:bg-gray-300 dark:bg-gray-700 dark:text-gray-200 dark:hover:bg-gray-600'
                  }`}
                >
                  {category} ({count})
                </button>
              );
            })}
          </div>
        </div>
      )}
      
      {/* Violations List */}
      <div className="divide-y divide-gray-200 dark:divide-gray-700">
        {filteredFindings.length === 0 ? (
          <div className="p-6 text-center text-gray-500 dark:text-gray-400">
            No accessibility violations found in this category.
          </div>
        ) : (
          filteredFindings.map((violation, index) => (
            <div 
              key={`${violation.id}-${index}`}
              className="p-4 hover:bg-gray-50 dark:hover:bg-gray-800 cursor-pointer"
              onClick={() => onFindingClick?.(violation)}
            >
              <div 
                className="flex items-start justify-between"
                onClick={(e) => {
                  e.stopPropagation();
                  toggleFinding(violation.id);
                }}
              >
                <div className="flex-1">
                  <div className="flex items-center space-x-2">
                    <span className="text-lg">{getImpactIcon(violation.impact || 'minor')}</span>
                    <h3 className="text-sm font-medium text-gray-900 dark:text-white">
                      {violation.help}
                    </h3>
                    {violation.impact && (
                      <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${getImpactColor(violation.impact)}`}>
                        {violation.impact}
                      </span>
                    )}
                  </div>
                  <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                    {violation.description}
                  </p>
                  <div className="mt-1 flex flex-wrap gap-2">
                    {violation.tags.map(tag => (
                      <span 
                        key={tag} 
                        className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-200"
                      >
                        {tag}
                      </span>
                    ))}
                  </div>
                </div>
                <button 
                  className="ml-4 flex-shrink-0 p-1 text-gray-400 hover:text-gray-500 dark:text-gray-500 dark:hover:text-gray-400"
                  onClick={(e) => {
                    e.stopPropagation();
                    toggleFinding(violation.id);
                  }}
                >
                  {expandedFindings.has(violation.id) ? '▼' : '▶'}
                </button>
              </div>
              
              {/* Expanded content */}
              {expandedFindings.has(violation.id) && (
                <div className="mt-3 pl-6 border-l-2 border-gray-200 dark:border-gray-700">
                  <h4 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                    How to fix ({violation.nodes.length} instances)
                  </h4>
                  <p className="text-sm text-gray-600 dark:text-gray-400 mb-3">
                    {violation.help} <a 
                      href={violation.helpUrl} 
                      target="_blank" 
                      rel="noopener noreferrer"
                      className="text-blue-600 hover:underline dark:text-blue-400"
                      onClick={e => e.stopPropagation()}
                    >
                      Learn more
                    </a>
                  </p>
                  
                  <div className="space-y-4">
                    {violation.nodes.slice(0, showAllFindings ? undefined : 3).map((node, nodeIndex) => (
                      <div 
                        key={nodeIndex} 
                        className="p-3 bg-gray-50 dark:bg-gray-800 rounded-md border border-gray-200 dark:border-gray-700"
                        onClick={e => e.stopPropagation()}
                      >
                        <div className="font-mono text-xs text-gray-800 dark:text-gray-200 mb-2 overflow-x-auto">
                          {node.html}
                        </div>
                        {node.failureSummary && (
                          <div className="mt-1 p-2 text-xs bg-yellow-50 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-300 rounded">
                            {node.failureSummary}
                          </div>
                        )}
                      </div>
                    ))}
                    
                    {!showAllFindings && violation.nodes.length > 3 && (
                      <div className="text-center text-sm text-gray-500 dark:text-gray-400">
                        ...and {violation.nodes.length - 3} more instances
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          ))
        )}
      </div>
      
      {/* Footer */}
      <div className="px-6 py-3 bg-gray-50 dark:bg-gray-800 border-t border-gray-200 dark:border-gray-700 text-right">
        <a
          href="https://dequeuniversity.com/rules/axe/"
          target="_blank"
          rel="noopener noreferrer"
          className="text-xs text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300"
          onClick={e => e.stopPropagation()}
        >
          Powered by axe-core
        </a>
      </div>
    </div>
  );
};

export default AccessibilityReportViewer;
