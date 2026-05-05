/**
 * Adaptive UI Component
 *
 * Renders UI hints and personalised recommendations based on learned
 * user behaviour. Follows the RecommendationCard composition pattern.
 *
 * @doc.type component
 * @doc.purpose Adaptive, personalised UI based on usage patterns
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { type ReactNode } from 'react';
import {
  Sparkles,
  Layout,
  Navigation,
  Zap,
  Star,
  RotateCcw,
} from 'lucide-react';
import { Typography, Button, Box, Card, CardContent } from '@ghatana/design-system';
import { useLearningEngine } from '../../hooks/useLearningEngine';
import type { UserPreferences } from '../../services/ai/LearningService';

// ============================================================================
// Types
// ============================================================================

export interface AdaptiveUIProps {
  onApplyLayout?: (layout: UserPreferences['preferredLayout']) => void;
  onApplyNavigation?: (style: UserPreferences['navigationStyle']) => void;
  onPinFeature?: (feature: string) => void;
  className?: string;
}

// ============================================================================
// Sub-components
// ============================================================================

interface SuggestionCardProps {
  icon: ReactNode;
  title: string;
  description: string;
  action?: ReactNode;
}

const SuggestionCard: React.FC<SuggestionCardProps> = ({
  icon,
  title,
  description,
  action,
}) => (
  <Card className="mb-2">
    <CardContent className="p-3">
      <Box className="flex items-start gap-3">
        <Box className="mt-0.5 text-info-color">{icon}</Box>
        <Box className="flex-1 min-w-0">
          <Box className="flex items-start justify-between mb-1">
            <Typography className="font-medium text-sm">{title}</Typography>
            {action}
          </Box>
          <Typography className="text-xs text-fg-muted dark:text-fg-muted">
            {description}
          </Typography>
        </Box>
      </Box>
    </CardContent>
  </Card>
);

// ============================================================================
// Main Component
// ============================================================================

export const AdaptiveUI: React.FC<AdaptiveUIProps> = ({
  onApplyLayout,
  onApplyNavigation,
  onPinFeature,
  className = '',
}) => {
  const { preferences, patterns, actionCount, reset } = useLearningEngine();

  const layoutLabels: Record<UserPreferences['preferredLayout'], string> = {
    compact: 'Compact',
    comfortable: 'Comfortable',
    spacious: 'Spacious',
  };

  const navLabels: Record<UserPreferences['navigationStyle'], string> = {
    sidebar: 'Sidebar',
    topbar: 'Top bar',
    command: 'Command palette',
  };

  const hasEnoughData = actionCount >= 10;

  return (
    <Box className={`space-y-4 ${className}`}>
      <Box className="flex items-center justify-between">
        <Box className="flex items-center gap-2">
          <Sparkles className="w-5 h-5 text-info-color" />
          <Typography className="font-semibold">Personalisation</Typography>
        </Box>
        <Button size="sm" variant="text" onClick={reset}>
          <RotateCcw className="w-4 h-4" />
        </Button>
      </Box>

      {!hasEnoughData ? (
        <Typography className="text-sm text-fg-muted">
          Keep using YAPPC — personalised suggestions will appear after more activity.
        </Typography>
      ) : (
        <>
          {/* Layout suggestion */}
          <SuggestionCard
            icon={<Layout className="w-4 h-4" />}
            title={`Switch to ${layoutLabels[preferences.preferredLayout]} layout`}
            description={`Based on your usage pattern we recommend the ${layoutLabels[preferences.preferredLayout].toLowerCase()} layout.`}
            action={
              onApplyLayout ? (
                <Button
                  size="sm"
                  variant="contained"
                  onClick={() => onApplyLayout(preferences.preferredLayout)}
                >
                  Apply
                </Button>
              ) : undefined
            }
          />

          {/* Navigation suggestion */}
          <SuggestionCard
            icon={<Navigation className="w-4 h-4" />}
            title={`Try ${navLabels[preferences.navigationStyle]} navigation`}
            description={`Your search-to-navigation ratio suggests ${navLabels[preferences.navigationStyle].toLowerCase()} may be faster for you.`}
            action={
              onApplyNavigation ? (
                <Button
                  size="sm"
                  variant="contained"
                  onClick={() => onApplyNavigation(preferences.navigationStyle)}
                >
                  Apply
                </Button>
              ) : undefined
            }
          />

          {/* AI level */}
          <SuggestionCard
            icon={<Zap className="w-4 h-4" />}
            title={`Assistant level: ${preferences.aiAssistanceLevel}`}
            description={`We've set assistant suggestions to "${preferences.aiAssistanceLevel}" based on how often you interact with assistant features.`}
          />

          {/* Frequent actions */}
          {preferences.frequentActions.length > 0 && (
            <Box>
              <Typography className="text-xs font-medium text-fg-muted mb-2">
                Your top actions
              </Typography>
              <Box className="flex flex-wrap gap-2">
                {preferences.frequentActions.map((action) => (
                  <Button
                    key={action}
                    size="sm"
                    variant="outlined"
                    onClick={() => onPinFeature?.(action)}
                  >
                    <Star className="w-3 h-3 mr-1" />
                    {action}
                  </Button>
                ))}
              </Box>
            </Box>
          )}
        </>
      )}
    </Box>
  );
};

export default AdaptiveUI;
