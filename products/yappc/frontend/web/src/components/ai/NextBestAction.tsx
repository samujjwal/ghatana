/**
 * Next Best Action Component
 *
 * Displays the most relevant next action based on current context and AI analysis.
 * Provides intelligent task prioritization and workflow guidance.
 *
 * @doc.type component
 * @doc.purpose Next best action recommendation
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { ReactNode } from 'react';
import { ArrowRight, Zap as LightningIcon, Target as TargetIcon, TrendingUp as TrendingIcon, CheckCircle2 as CompleteIcon, Clock as TimeIcon } from 'lucide-react';
import { Typography, Button, Chip, Box, Card, CardContent } from '@ghatana/design-system';

// ============================================================================
// Types
// ============================================================================

export interface NextAction {
  id: string;
  title: string;
  description: string;
  type: 'immediate' | 'recommended' | 'suggested';
  impact: 'high' | 'medium' | 'low';
  estimatedTime?: string;
  onAction: () => void;
  onDismiss?: () => void;
  metadata?: Record<string, unknown>;
}

export interface NextBestActionProps {
  action: NextAction;
  position?: 'floating' | 'inline' | 'sidebar';
  showImpact?: boolean;
  showEstimatedTime?: boolean;
  className?: string;
}

// ============================================================================
// Icon Mapping
// ============================================================================

const getActionIcon = (type: NextAction['type']) => {
  switch (type) {
    case 'immediate':
      return <LightningIcon className="w-5 h-5" />;
    case 'recommended':
      return <TargetIcon className="w-5 h-5" />;
    case 'suggested':
      return <TrendingIcon className="w-5 h-5" />;
    default:
      return <CompleteIcon className="w-5 h-5" />;
  }
};

const getActionColor = (type: NextAction['type']) => {
  switch (type) {
    case 'immediate':
      return 'text-orange-600 dark:text-orange-400 bg-orange-50 dark:bg-orange-900/20';
    case 'recommended':
      return 'text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/20';
    case 'suggested':
      return 'text-purple-600 dark:text-purple-400 bg-purple-50 dark:bg-purple-900/20';
    default:
      return 'text-gray-600 dark:text-gray-400 bg-gray-50 dark:bg-gray-900/20';
  }
};

const getImpactColor = (impact: NextAction['impact']) => {
  switch (impact) {
    case 'high':
      return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300';
    case 'medium':
      return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300';
    case 'low':
      return 'bg-gray-100 text-gray-700 dark:bg-gray-900/30 dark:text-gray-300';
    default:
      return 'bg-gray-100 text-gray-700 dark:bg-gray-900/30 dark:text-gray-300';
  }
};

// ============================================================================
// Next Best Action Component
// ============================================================================

/**
 * Next Best Action Component
 */
export function NextBestAction({
  action,
  position = 'inline',
  showImpact = true,
  showEstimatedTime = true,
  className = '',
}: NextBestActionProps): ReactNode {
  const icon = getActionIcon(action.type);
  const colorClass = getActionColor(action.type);
  const impactColor = getImpactColor(action.impact);

  const positionStyles = {
    floating: 'fixed bottom-4 right-4 z-50 max-w-sm',
    inline: 'w-full',
    sidebar: 'w-full',
  };

  return (
    <Card
      variant="outlined"
      className={`${positionStyles[position]} ${colorClass} border-2 transition-all duration-200 hover:shadow-lg ${className}`}
    >
      <CardContent className="p-4">
        {/* Header */}
        <div className="flex items-start gap-3 mb-3">
          <div className={`flex-shrink-0 p-2 rounded-lg ${colorClass}`}>
            {icon}
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-1">
              <Typography className="font-bold text-sm">
                {action.title}
              </Typography>
              <Chip
                size="sm"
                label={action.type}
                className="text-xs"
              />
            </div>
            <Typography className="text-sm opacity-80">
              {action.description}
            </Typography>
          </div>
        </div>

        {/* Metadata */}
        <div className="flex items-center gap-3 mb-3">
          {showImpact && (
            <div className="flex items-center gap-1">
              <Chip
                size="sm"
                label={`${action.impact} impact`}
                className={`text-xs ${impactColor}`}
              />
            </div>
          )}
          {showEstimatedTime && action.estimatedTime && (
            <div className="flex items-center gap-1 text-xs opacity-70">
              <TimeIcon className="w-3 h-3" />
              <Typography className="text-xs">
                {action.estimatedTime}
              </Typography>
            </div>
          )}
        </div>

        {/* Actions */}
        <div className="flex gap-2">
          <Button
            size="sm"
            onClick={action.onAction}
            endIcon={<ArrowRight className="w-4 h-4" />}
            className="flex-1"
          >
            Take Action
          </Button>
          {action.onDismiss && (
            <Button
              size="sm"
              variant="text"
              onClick={action.onDismiss}
              className="text-xs"
            >
              Dismiss
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
