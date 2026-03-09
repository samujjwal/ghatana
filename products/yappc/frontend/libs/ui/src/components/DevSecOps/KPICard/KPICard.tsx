/**
 * KPICard Component
 *
 * A card component for displaying Key Performance Indicators with optional
 * trend indicators and progress tracking.
 *
 * @module DevSecOps/KPICard
 */

import { TrendingDown as TrendingDownIcon } from 'lucide-react';
import { TrendingUp as TrendingUpIcon } from 'lucide-react';
import { Box, Card, CardContent, LinearProgress, Typography } from '@ghatana/ui';

import type { KPICardProps } from './types';
import type React from 'react';

/**
 * KPICard - Display Key Performance Indicators
 *
 * A versatile card component for displaying KPIs with trend visualization,
 * progress tracking, and responsive design. Follows the DevSecOps design system.
 *
 * @param props - KPICard component props
 * @returns Rendered KPICard component
 *
 * @example
 * ```tsx
 * <KPICard
 *   title="Deployment Frequency"
 *   value={24}
 *   unit=" per week"
 *   trend={{ direction: 'up', value: 12.5 }}
 * />
 * ```
 */
export const KPICard: React.FC<KPICardProps> = ({
  title,
  value,
  target,
  unit = '',
  trend,
  showProgress = false,
}) => {
  const progress = target ? (Number(value) / target) * 100 : 0;
  const trendColor =
    trend?.direction === 'up'
      ? 'success.main'
      : trend?.direction === 'down'
        ? 'error.main'
        : 'text.secondary';

  const TrendIcon =
    trend?.direction === 'up'
      ? TrendingUpIcon
      : trend?.direction === 'down'
        ? TrendingDownIcon
        : null;

  return (
    <Card
      className="h-full min-w-0 transition-all hover:shadow-xl hover:-translate-y-1 hover:bg-gray-50 dark:hover:bg-gray-800"
      style={{
        transitionDuration: 'var(--ds-duration-base)',
        transitionTimingFunction: 'var(--ds-ease-in-out)',
        borderRadius: 'var(--ds-radius-full)',
      }}
    >
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="flex-start">
          <Typography as="h2" fontWeight={700} component="div">
            {value}
            {unit}
          </Typography>
          {trend && (
            <Box display="flex" alignItems="center" color={trendColor}>
              {TrendIcon && <TrendIcon size={16} />}
              <Typography as="p" className="text-sm ml-1">
                {trend.value > 0 ? '+' : ''}
                {trend.value}%
              </Typography>
            </Box>
          )}
        </Box>

        <Typography as="p" className="text-sm mt-2" color="text.secondary">
          {title}
        </Typography>

        {showProgress && target && (
          <>
            <LinearProgress
              variant="determinate"
              value={Math.min(progress, 100)}
              className="mt-4 h-[8px]" />
            <Typography
              as="span"
              color="text.secondary"
              className="mt-2 block text-xs text-gray-500"
            >
              Target: {target}
              {unit}
            </Typography>
          </>
        )}
      </CardContent>
    </Card>
  );
};
