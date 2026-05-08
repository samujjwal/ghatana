/**
 * PhaseNav Component
 *
 * Horizontal phase navigation with visual indicators for active and completed phases.
 *
 * @module DevSecOps/PhaseNav
 */

import { CheckCircle as CheckCircleIcon } from 'lucide-react';
import type React from 'react';

import { Chip, Stack } from '@ghatana/design-system';

import type { PhaseNavProps } from './types';

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
      aria-label="DevSecOps phases"
      className="overflow-x-auto py-4 [&::-webkit-scrollbar]:h-1.5 [&::-webkit-scrollbar-thumb]:bg-neutral-300 [&::-webkit-scrollbar-thumb]:rounded"
      data-testid="phase-nav"
      role="group"
      style={{ overflowX: 'auto' }}
    >
      {phases.map((phase, index) => {
        const isActive = phase.id === activePhaseId;
        const isCompleted = completedPhaseIds.includes(phase.id);

        return (
          <Chip
            key={`${phase.id}-${index}`}
            label={phase.title}
            onClick={() => onPhaseClick?.(phase.id)}
            color={isActive ? 'primary' : 'default'}
            variant={isActive ? 'filled' : 'outlined'}
            icon={
              isCompleted ? (
                <CheckCircleIcon
                  aria-hidden="true"
                  data-testid="CheckCircleIcon"
                />
              ) : undefined
            }
            className={`min-w-[120px] h-10 border-2 hover:scale-105 hover:shadow-md ${isActive ? 'font-semibold' : 'font-normal'} ${isCompleted ? 'text-white' : ''}`}
            aria-current={isActive ? 'step' : undefined}
            aria-label={`${phase.title || phase.id}${isActive ? ', active phase' : ''}${isCompleted ? ', completed' : ''}`}
            data-active={isActive ? 'true' : 'false'}
            data-completed={isCompleted ? 'true' : 'false'}
            data-phase-id={phase.id}
            style={{
              borderColor: `var(--ds-phase-${phase.key})`,
              borderWidth: 2,
              fontWeight: isActive ? 600 : 400,
              height: 40,
              minWidth: 120,
              transition: 'all var(--ds-duration-base) var(--ds-ease-in-out)',
              ...(isCompleted
                ? {
                    backgroundColor: `var(--ds-phase-${phase.key})`,
                    color: 'white',
                  }
                : {}),
            }}
          />
        );
      })}
    </Stack>
  );
};
