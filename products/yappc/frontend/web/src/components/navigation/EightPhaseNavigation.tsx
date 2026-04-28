/**
 * Eight-Phase Navigation Component
 *
 * Primary navigation for YAPPC using the canonical 8-phase IA model:
 * Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve
 *
 * This replaces the old dev/ops/admin page structure with a unified
 * lifecycle-based navigation. Development, operations, and admin pages
 * are demoted to context-sensitive panels within appropriate phases.
 *
 * @doc.type component
 * @doc.purpose Primary 8-phase lifecycle navigation
 * @doc.layer product
 * @doc.pattern Navigation Component
 */

import React, { useState } from 'react';
import { useNavigate, useLocation } from 'react-router';
import {
  Lightbulb as IntentIcon,
  PenTool as ShapeIcon,
  ShieldCheck as ValidateIcon,
  Code as GenerateIcon,
  Play as RunIcon,
  Eye as ObserveIcon,
  BookOpen as LearnIcon,
  RefreshCw as EvolveIcon,
} from 'lucide-react';
import { cn } from '../../lib/utils';
import type { LifecyclePhase } from '@/shared/types/lifecycle';
import { LIFECYCLE_PHASE_ORDER, LIFECYCLE_PHASE_LABELS } from '@/shared/types/lifecycle';

interface PhaseNavigationItem {
  phase: LifecyclePhase;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  description: string;
}

const PHASE_NAVIGATION_ITEMS: PhaseNavigationItem[] = [
  {
    phase: LIFECYCLE_PHASE_ORDER[0],
    label: LIFECYCLE_PHASE_LABELS[LIFECYCLE_PHASE_ORDER[0]],
    icon: IntentIcon,
    description: 'Define problem, users, and value',
  },
  {
    phase: LIFECYCLE_PHASE_ORDER[1],
    label: LIFECYCLE_PHASE_LABELS[LIFECYCLE_PHASE_ORDER[1]],
    icon: ShapeIcon,
    description: 'Requirements, architecture, UX',
  },
  {
    phase: LIFECYCLE_PHASE_ORDER[2],
    label: LIFECYCLE_PHASE_LABELS[LIFECYCLE_PHASE_ORDER[2]],
    icon: ValidateIcon,
    description: 'Security, testing, simulation',
  },
  {
    phase: LIFECYCLE_PHASE_ORDER[3],
    label: LIFECYCLE_PHASE_LABELS[LIFECYCLE_PHASE_ORDER[3]],
    icon: GenerateIcon,
    description: 'Code generation and scaffolding',
  },
  {
    phase: LIFECYCLE_PHASE_ORDER[4],
    label: LIFECYCLE_PHASE_LABELS[LIFECYCLE_PHASE_ORDER[4]],
    icon: RunIcon,
    description: 'Deployment and execution',
  },
  {
    phase: LIFECYCLE_PHASE_ORDER[5],
    label: LIFECYCLE_PHASE_LABELS[LIFECYCLE_PHASE_ORDER[5]],
    icon: ObserveIcon,
    description: 'Monitoring and incident response',
  },
  {
    phase: LIFECYCLE_PHASE_ORDER[6],
    label: LIFECYCLE_PHASE_LABELS[LIFECYCLE_PHASE_ORDER[6]],
    icon: LearnIcon,
    description: 'Analytics and insights',
  },
  {
    phase: LIFECYCLE_PHASE_ORDER[7],
    label: LIFECYCLE_PHASE_LABELS[LIFECYCLE_PHASE_ORDER[7]],
    icon: EvolveIcon,
    description: 'Continuous improvement',
  },
];

interface EightPhaseNavigationProps {
  currentPhase?: LifecyclePhase;
  projectId?: string;
  className?: string;
}

export function EightPhaseNavigation({
  currentPhase,
  projectId,
  className,
}: EightPhaseNavigationProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const [hoveredPhase, setHoveredPhase] = useState<LifecyclePhase | null>(null);

  const handlePhaseClick = (phase: LifecyclePhase) => {
    if (projectId) {
      navigate(`/p/${projectId}?phase=${phase.toLowerCase()}`);
    }
  };

  const getPhaseIndex = (phase: LifecyclePhase): number => {
    return LIFECYCLE_PHASE_ORDER.indexOf(phase);
  };

  const isActive = (phase: LifecyclePhase): boolean => {
    return currentPhase === phase;
  };

  const isCompleted = (phase: LifecyclePhase): boolean => {
    if (!currentPhase) return false;
    return getPhaseIndex(phase) < getPhaseIndex(currentPhase);
  };

  return (
    <nav
      className={cn(
        'flex items-center gap-1 px-4 py-2 bg-bg-paper border-b border-divider',
        className
      )}
      aria-label="Eight-phase lifecycle navigation"
    >
      {PHASE_NAVIGATION_ITEMS.map((item, index) => {
        const Icon = item.icon;
        const active = isActive(item.phase);
        const completed = isCompleted(item.phase);
        const hovered = hoveredPhase === item.phase;

        return (
          <React.Fragment key={item.phase}>
            {index > 0 && (
              <div
                className={cn(
                  'w-8 h-0.5 transition-colors',
                  completed ? 'bg-primary-500' : 'bg-divider'
                )}
                aria-hidden="true"
              />
            )}
            <button
              type="button"
              onClick={() => handlePhaseClick(item.phase)}
              onMouseEnter={() => setHoveredPhase(item.phase)}
              onMouseLeave={() => setHoveredPhase(null)}
              className={cn(
                'flex flex-col items-center gap-1 px-3 py-2 rounded-lg transition-all duration-200',
                'min-w-[80px] group focus:outline-none focus:ring-2 focus:ring-primary-500',
                active
                  ? 'bg-primary-50 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
                  : completed
                    ? 'text-primary-600 hover:bg-primary-50 dark:text-primary-400 dark:hover:bg-primary-900/20'
                    : 'text-text-secondary hover:bg-bg-default'
              )}
              aria-label={`Navigate to ${item.label} phase`}
              aria-current={active ? 'page' : undefined}
            >
              <Icon
                className={cn(
                  'w-5 h-5 transition-transform',
                  active ? 'scale-110' : 'group-hover:scale-105'
                )}
              />
              <span
                className={cn(
                  'text-xs font-medium',
                  active ? 'font-semibold' : ''
                )}
              >
                {item.label}
              </span>
              {(hovered || active) && (
                <div className="absolute top-full mt-2 px-2 py-1 bg-bg-elevated border border-divider rounded-md shadow-lg text-xs text-text-secondary whitespace-nowrap z-50">
                  {item.description}
                </div>
              )}
            </button>
          </React.Fragment>
        );
      })}
    </nav>
  );
}

export default EightPhaseNavigation;
