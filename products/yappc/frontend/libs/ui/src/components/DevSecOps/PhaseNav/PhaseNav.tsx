/**
 * PhaseNav Component
 *
 * Horizontal phase navigation with visual indicators for active and completed phases.
 *
 * @module DevSecOps/PhaseNav
 */

import { CheckCircle as CheckCircleIcon } from 'lucide-react';
import { Chip, Stack } from '@ghatana/ui';

import type { PhaseNavProps } from './types';
import type React from 'react';

/**
 * PhaseNav - Phase navigation pills
 *
 * Displays DevSecOps phases as interactive pills with status indicators.
 *
 * @param props - PhaseNav component props
 * @returns Rendered PhaseNav component
 *
 * @example
 * ```tsx
 * <PhaseNav
 *   phases={phases}
 *   activePhaseId="development"
 *   completedPhaseIds={['ideation', 'planning']}
 *   onPhaseClick={handlePhaseChange}
 * />
 * ```
 */
export const PhaseNav: React.FC<PhaseNavProps> = ({
  phases,
  activePhaseId,
  completedPhaseIds = [],
  onPhaseClick,
}) => {
  return (
    <Stack
      direction="row"
      spacing={1}
      className="overflow-x-auto py-4 [&::-webkit-scrollbar]:h-1.5 [&::-webkit-scrollbar-thumb]:bg-neutral-300 [&::-webkit-scrollbar-thumb]:rounded"
    >
      {phases.map((phase) => {
        const isActive = phase.id === activePhaseId;
        const isCompleted = completedPhaseIds.includes(phase.id);

        return (
          <Chip
            key={phase.id}
            label={phase.title}
            onClick={() => onPhaseClick?.(phase.id)}
            color={isActive ? 'primary' : 'default'}
            variant={isActive ? 'filled' : 'outlined'}
            icon={isCompleted ? <CheckCircleIcon /> : undefined}
            className={`min-w-[120px] h-10 border-2 hover:scale-105 hover:shadow-md ${isActive ? 'font-semibold' : 'font-normal'} ${isCompleted ? 'text-white' : ''}`}
            style={{
              borderColor: `var(--ds-phase-${phase.key})`,
              transition: 'all var(--ds-duration-base) var(--ds-ease-in-out)',
              ...(isCompleted ? {
                backgroundColor: `var(--ds-phase-${phase.key})`,
                color: 'white',
              } : {}),
            }}
          />
        );
      })}
    </Stack>
  );
};
