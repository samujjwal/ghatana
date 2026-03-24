/**
 * Accessibility Dashboard Component
 * Real-time accessibility monitoring and reporting interface
 */

import clsx from 'clsx';
import React, { useState, useEffect } from 'react';

import {
  accessibilityAuditor,
} from '../utils/AccessibilityAuditor';

import type {
  AccessibilityReport} from '../utils/AccessibilityAuditor';

/**
 *
 */
export interface AccessibilityDashboardProps {
  className?: string;
  autoRun?: boolean;
  showAutoFix?: boolean;
}

export const AccessibilityDashboard: React.FC<AccessibilityDashboardProps> = ({
  className,
  autoRun = false,
  showAutoFix = true,
}) => {
  const [report, setReport] = useState<AccessibilityReport | null>(null);
  const [isRunning, setIsRunning] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [selectedImpact, setSelectedImpact] = useState<string>('all');
  const [isMonitoring, setIsMonitoring] = useState(false);
  const [autoFixProgress, setAutoFixProgress] = useState<{
    inProgress: boolean;
    fixed: number;
    failed: number;
  } | null>(null);

  useEffect(() => {
    if (autoRun) {
      runAudit();
    }
  }, [autoRun]);

  useEffect(() => {
    let unsubscribe: (() => void) | null = null;
    
    if (isMonitoring) {
      unsubscribe = accessibilityAuditor.startMonitoring(setReport);
    }
    
    return () => {
      if (unsubscribe) {
        unsubscribe();
      }
    };
  }, [isMonitoring]);

  const runAudit = async () => {
    setIsRunning(true);
    try {
      const auditReport = await accessibilityAuditor.auditPage();
      setReport(auditReport);
    } catch (error) {
      console.error('Accessibility audit failed:', error);
    } finally {
      setIsRunning(false);
    }
  };

  const runAutoFix = async () => {
    if (!report) return;
    
    setAutoFixProgress({ inProgress: true, fixed: 0, failed: 0 });
    
    try {
      const { fixed, failed } = await accessibilityAuditor.autoFix(report.issues);
      setAutoFixProgress({ inProgress: false, fixed: fixed.length, failed: failed.length });
      
      // Re-run audit to get updated results
      setTimeout(() => runAudit(), 1000);
    } catch (error) {
      console.error('Auto-fix failed:', error);
      setAutoFixProgress({ inProgress: false, fixed: 0, failed: 0 });
    }
  };

  const filteredIssues = report?.issues.filter(issue => {
    const categoryMatch = selectedCategory === 'all' || issue.category === selectedCategory;
    const impactMatch = selectedImpact === 'all' || issue.impact === selectedImpact;
    return categoryMatch && impactMatch;
  }) || [];

  const getScoreColor = (score: number) => {
    if (score >= 90) return 'text-green-600';
    if (score >= 70) return 'text-yellow-600';
    return 'text-red-600';
  };

  const getScoreBgColor = (score: number) => {
    if (score >= 90) return 'bg-green-100';
    if (score >= 70) return 'bg-yellow-100';
    return 'bg-red-100';
  };

  const getImpactColor = (impact: string) => {
    switch (impact) {
      case 'critical': return 'text-red-700 bg-red-100';
      case 'serious': return 'text-red-600 bg-red-50';
      case 'moderate': return 'text-yellow-600 bg-yellow-50';
      case 'minor': return 'text-blue-600 bg-blue-50';
      default: return 'text-gray-600 bg-gray-50';
    }
  };

  const getCategoryIcon = (category: string) => {
    switch (category) {
      case 'color-contrast': return '🎨';
      case 'keyboard-navigation': return '⌨️';
      case 'screen-reader': return '🔊';
      case 'focus-management': return '🎯';
      case 'semantic-html': return '📝';
      case 'aria': return '🏷️';
      case 'motion': return '🎬';
      default: return '⚠️';
    }
  };

  return (
    <div className={clsx('bg-white rounded-lg shadow-lg border border-gray-200', className)}>
      {/* Header */}
      <div className="px-6 py-4 border-b border-gray-200">
        <div className="flex justify-between items-center">
          <div>
            <h2 className="text-xl font-semibold text-gray-900">Accessibility Dashboard</h2>
            <p className="text-sm text-gray-600 mt-1">
              Monitor and improve accessibility compliance
            </p>
          </div>
          
          <div className="flex space-x-3">
            <button
              onClick={() => setIsMonitoring(!isMonitoring)}
              className={clsx(
                'px-4 py-2 text-sm font-medium rounded-md transition-colors',
                isMonitoring
                  ? 'bg-red-100 text-red-700 hover:bg-red-200'
                  : 'bg-blue-100 text-blue-700 hover:bg-blue-200'
              )}
              data-testid="toggle-monitoring"
            >
              {isMonitoring ? 'Stop Monitoring' : 'Start Monitoring'}
            </button>
            
            <button
              onClick={runAudit}
              disabled={isRunning}
              className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              data-testid="run-audit"
            >
              {isRunning ? 'Running...' : 'Run Audit'}
            </button>
          </div>
        </div>
      </div>

      {/* Report Summary */}
      {report && (
        <div className="px-6 py-4 border-b border-gray-200">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            {/* Score */}
            <div className={clsx('p-4 rounded-lg', getScoreBgColor(report.score))}>
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-gray-600">Accessibility Score</p>
                  <p className={clsx('text-2xl font-bold', getScoreColor(report.score))}>
                    {report.score}/100
                  </p>
                </div>
                <div className="text-2xl">
                  {report.score >= 90 ? '✅' : report.score >= 70 ? '⚠️' : '❌'}
                </div>
              </div>
            </div>

            {/* Total Issues */}
            <div className="p-4 bg-gray-50 rounded-lg">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-gray-600">Total Issues</p>
                  <p className="text-2xl font-bold text-gray-900">{report.totalIssues}</p>
                </div>
                <div className="text-2xl">🔍</div>
              </div>
            </div>

            {/* Critical Issues */}
            <div className="p-4 bg-red-50 rounded-lg">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-gray-600">Critical Issues</p>
                  <p className="text-2xl font-bold text-red-600">
                    {report.issuesByImpact.critical || 0}
                  </p>
                </div>
                <div className="text-2xl">🚨</div>
              </div>
            </div>

            {/* Auto-fixable */}
            <div className="p-4 bg-green-50 rounded-lg">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-gray-600">Auto-fixable</p>
                  <p className="text-2xl font-bold text-green-600">
                    {report.issues.filter(i => i.autoFixable).length}
                  </p>
                </div>
                <div className="text-2xl">🔧</div>
              </div>
            </div>
          </div>

          {/* Auto-fix Actions */}
          {showAutoFix && report.issues.some(i => i.autoFixable) && (
            <div className="mt-4 p-4 bg-blue-50 rounded-lg">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-medium text-blue-900">Auto-fix Available</h3>
                  <p className="text-sm text-blue-700">
                    {report.issues.filter(i => i.autoFixable).length} issues can be automatically fixed
                  </p>
                </div>
                
                <button
                  onClick={runAutoFix}
                  disabled={autoFixProgress?.inProgress}
                  className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                  data-testid="run-autofix"
                >
                  {autoFixProgress?.inProgress ? 'Fixing...' : 'Auto-fix Issues'}
                </button>
              </div>
              
              {autoFixProgress && !autoFixProgress.inProgress && (
                <div className="mt-2 text-sm text-blue-700">
                  Fixed: {autoFixProgress.fixed}, Failed: {autoFixProgress.failed}
                </div>
              )}
            </div>
          )}

          {/* Recommendations */}
          {report.recommendations.length > 0 && (
            <div className="mt-4">
              <h3 className="text-sm font-medium text-gray-900 mb-2">Recommendations</h3>
              <ul className="text-sm text-gray-600 space-y-1">
                {report.recommendations.map((rec, index) => (
                  <li key={index} className="flex items-start">
                    <span className="text-blue-500 mr-2">•</span>
                    {rec}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {/* Filters */}
      {report && report.issues.length > 0 && (
        <div className="px-6 py-4 border-b border-gray-200">
          <div className="flex flex-wrap gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Category
              </label>
              <select
                value={selectedCategory}
                onChange={(e) => setSelectedCategory(e.target.value)}
                className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="all">All Categories</option>
                {Object.keys(report.issuesByCategory).map(category => (
                  <option key={category} value={category}>
                    {getCategoryIcon(category)} {category.replace('-', ' ')} ({report.issuesByCategory[category]})
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Impact
              </label>
              <select
                value={selectedImpact}
                onChange={(e) => setSelectedImpact(e.target.value)}
                className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="all">All Impact Levels</option>
                {Object.keys(report.issuesByImpact).map(impact => (
                  <option key={impact} value={impact}>
                    {impact} ({report.issuesByImpact[impact]})
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>
      )}

      {/* Issues List */}
      <div className="max-h-96 overflow-y-auto">
        {filteredIssues.length > 0 ? (
          <div className="divide-y divide-gray-200">
            {filteredIssues.map((issue) => (
              <div key={issue.id} className="px-6 py-4">
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="flex items-center space-x-2 mb-2">
                      <span className="text-lg">{getCategoryIcon(issue.category)}</span>
                      <span className={clsx(
                        'px-2 py-1 text-xs font-medium rounded-full',
                        getImpactColor(issue.impact)
                      )}>
                        {issue.impact}
                      </span>
                      <span className="text-xs text-gray-500">
                        {issue.category.replace('-', ' ')}
                      </span>
                      {issue.autoFixable && (
                        <span className="px-2 py-1 text-xs font-medium text-green-700 bg-green-100 rounded-full">
                          Auto-fixable
                        </span>
                      )}
                    </div>
                    
                    <h4 className="text-sm font-medium text-gray-900 mb-1">
                      {issue.message}
                    </h4>
                    
                    <p className="text-sm text-gray-600 mb-2">
                      {issue.suggestion}
                    </p>
                    
                    <div className="flex items-center text-xs text-gray-500 space-x-4">
                      <span>WCAG: {issue.wcagCriteria.join(', ')}</span>
                      {issue.documentation && (
                        <a
                          href={issue.documentation}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-blue-600 hover:text-blue-800"
                        >
                          Learn more →
                        </a>
                      )}
                    </div>
                  </div>
                  
                  <button
                    onClick={() => {
                      issue.element.scrollIntoView({ behavior: 'smooth', block: 'center' });
                      issue.element.classList.add('ring-2', 'ring-red-500');
                      setTimeout(() => {
                        issue.element.classList.remove('ring-2', 'ring-red-500');
                      }, 3000);
                    }}
                    className="ml-4 px-3 py-1 text-xs font-medium text-gray-600 bg-gray-100 rounded hover:bg-gray-200 transition-colors"
                  >
                    Locate
                  </button>
                </div>
              </div>
            ))}
          </div>
        ) : report ? (
          <div className="px-6 py-12 text-center">
            <div className="text-4xl mb-4">🎉</div>
            <h3 className="text-lg font-medium text-gray-900 mb-2">
              {selectedCategory === 'all' && selectedImpact === 'all' 
                ? 'No accessibility issues found!'
                : 'No issues match your filters'
              }
            </h3>
            <p className="text-gray-600">
              {selectedCategory === 'all' && selectedImpact === 'all'
                ? 'Your page meets accessibility standards.'
                : 'Try adjusting your filters to see more issues.'
              }
            </p>
          </div>
        ) : (
          <div className="px-6 py-12 text-center">
            <div className="text-4xl mb-4">🔍</div>
            <h3 className="text-lg font-medium text-gray-900 mb-2">
              Run an accessibility audit
            </h3>
            <p className="text-gray-600 mb-4">
              Get insights about your page's accessibility compliance
            </p>
            <button
              onClick={runAudit}
              disabled={isRunning}
              className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {isRunning ? 'Running...' : 'Run Audit'}
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default AccessibilityDashboard;