/**
 * Project Command Center
 *
 * Project overview as command center showing current phase, readiness, next action,
 * blocker, recent activity, suggested improvement, and governance trace link.
 *
 * @doc.type component
 * @doc.purpose Project command center overview
 * @doc.layer product
 * @doc.pattern Command Center
 */

import React from 'react';
import { ArrowRight, AlertTriangle, CheckCircle, Clock, TrendingUp, Link } from 'lucide-react';
import { ProvenanceBadge } from '../shared/ProvenanceBadge';

export interface PhaseInfo {
  name: string;
  description: string;
}

export interface ReadinessInfo {
  level: 'ready' | 'not-ready' | 'blocked';
  message: string;
  blocker?: string;
  evidence?: string;
}

export interface ActivityItem {
  id: string;
  type: 'backed' | 'derived' | 'suggested' | 'preview';
  description: string;
  timestamp: string;
  actor?: string;
}

export interface SuggestedImprovement {
  id: string;
  title: string;
  description: string;
  confidence: number;
  source: string;
}

export interface CommandCenterProps {
  /** Current phase info */
  currentPhase: PhaseInfo;
  /** Current readiness */
  readiness: ReadinessInfo;
  /** Primary next action */
  nextAction: {
    title: string;
    description: string;
    onExecute: () => void;
    targetPhase?: string;
  };
  /** Recent backed activity */
  recentActivity: ActivityItem[];
  /** Suggested improvement */
  suggestedImprovement?: SuggestedImprovement;
  /** Governance trace link */
  governanceTraceUrl?: string;
  /** Loading state */
  loading?: boolean;
}

export const CommandCenter: React.FC<CommandCenterProps> = ({
  currentPhase,
  readiness,
  nextAction,
  recentActivity,
  suggestedImprovement,
  governanceTraceUrl,
  loading = false,
}) => {
  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" />
      </div>
    );
  }

  const getReadinessIcon = () => {
    switch (readiness.level) {
      case 'ready':
        return <CheckCircle className="w-5 h-5 text-green-500" />;
      case 'not-ready':
        return <Clock className="w-5 h-5 text-yellow-500" />;
      case 'blocked':
        return <AlertTriangle className="w-5 h-5 text-red-500" />;
    }
  };

  const getReadinessColor = () => {
    switch (readiness.level) {
      case 'ready':
        return 'text-green-600 bg-green-50 border-green-200';
      case 'not-ready':
        return 'text-yellow-600 bg-yellow-50 border-yellow-200';
      case 'blocked':
        return 'text-red-600 bg-red-50 border-red-200';
    }
  };

  return (
    <div className="space-y-6">
      {/* Current Phase & Readiness */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div
          data-testid="current-phase"
          className="bg-white border border-gray-200 rounded-lg p-4"
        >
          <h3 className="text-sm font-medium text-gray-500 mb-2">Current Phase</h3>
          <div className="text-lg font-semibold text-gray-900">{currentPhase.name}</div>
          <p className="text-sm text-gray-600 mt-1">{currentPhase.description}</p>
        </div>

        <div
          data-testid="readiness-status"
          className={`border rounded-lg p-4 ${getReadinessColor()}`}
        >
          <div className="flex items-center gap-2 mb-2">
            <h3 className="text-sm font-medium">Readiness</h3>
            {getReadinessIcon()}
          </div>
          <div className="text-base font-semibold">{readiness.message}</div>
          {readiness.blocker && (
            <p className="text-sm mt-1">Blocker: {readiness.blocker}</p>
          )}
          {readiness.evidence && (
            <p className="text-sm mt-1 text-gray-600">Evidence: {readiness.evidence}</p>
          )}
        </div>
      </div>

      {/* Primary Next Action */}
      <button
        type="button"
        data-testid="next-action"
        className="w-full bg-primary-50 border border-primary-200 rounded-lg p-6 hover:shadow-md transition-shadow cursor-pointer text-left"
        onClick={nextAction.onExecute}
      >
        <div className="flex items-start gap-4">
          <div className="flex-shrink-0 mt-1">
            <ArrowRight className="w-6 h-6 text-primary-600" />
          </div>
          <div className="flex-1">
            <h2 className="text-lg font-semibold text-primary-900 mb-2">
              Next Action: {nextAction.title}
            </h2>
            <p className="text-primary-700">{nextAction.description}</p>
            {nextAction.targetPhase && (
              <p className="text-sm text-primary-600 mt-2">
                Target phase: {nextAction.targetPhase}
              </p>
            )}
          </div>
        </div>
      </button>

      {/* Recent Backed Activity */}
      <div
        data-testid="recent-activity"
        className="bg-white border border-gray-200 rounded-lg p-4"
      >
        <h3 className="text-sm font-medium text-gray-500 mb-3 flex items-center gap-2">
          <TrendingUp className="w-4 h-4" />
          Recent Backed Activity
        </h3>
        {recentActivity.length > 0 ? (
          <div className="space-y-2">
            {recentActivity.slice(0, 5).map((activity) => (
              <div key={activity.id} className="flex items-start gap-3 text-sm">
                <ProvenanceBadge type={activity.type} size="sm" />
                <div className="flex-1">
                  <span className="text-gray-900">{activity.description}</span>
                  <span className="text-gray-500 ml-2">{activity.timestamp}</span>
                  {activity.actor && (
                    <span className="text-gray-500 ml-2">by {activity.actor}</span>
                  )}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-sm text-gray-500">No recent backed activity</p>
        )}
      </div>

      {/* Suggested Improvement */}
      {suggestedImprovement && (
        <div
          data-testid="suggested-improvement"
          className="bg-orange-50 border border-orange-200 rounded-lg p-4"
        >
          <h3 className="text-sm font-medium text-orange-900 mb-2 flex items-center gap-2">
            <TrendingUp className="w-4 h-4" />
            Suggested Improvement
          </h3>
          <div className="text-base font-medium text-orange-900 mb-1">
            {suggestedImprovement.title}
          </div>
          <p className="text-sm text-orange-700 mb-2">{suggestedImprovement.description}</p>
          <div className="flex items-center gap-4 text-xs text-orange-600">
            <span>Confidence: {Math.round(suggestedImprovement.confidence * 100)}%</span>
            <span>Source: {suggestedImprovement.source}</span>
          </div>
        </div>
      )}

      {/* Governance Trace Link */}
      {governanceTraceUrl && (
        <div
          data-testid="governance-trace"
          className="bg-gray-50 border border-gray-200 rounded-lg p-4"
        >
          <a
            href={governanceTraceUrl}
            className="flex items-center gap-2 text-sm font-medium text-gray-700 hover:text-gray-900"
          >
            <Link className="w-4 h-4" />
            View Governance Trace
          </a>
        </div>
      )}
    </div>
  );
};

export default CommandCenter;
