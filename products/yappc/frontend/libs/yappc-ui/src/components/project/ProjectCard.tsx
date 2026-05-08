/**
 * ProjectCard
 *
 * Displays a project as a compact summary card.
 *
 * @doc.type component
 * @doc.purpose Display a project summary with name, status, type, and AI health
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import { Brain as BrainIcon, Settings as SettingsIcon } from 'lucide-react';
import React from 'react';

import type { Project, ProjectStatus } from 'yappc-core/types';

const STATUS_CLASSES: Record<ProjectStatus, string> = {
  DRAFT: 'border-slate-200 bg-slate-50 text-slate-700',
  ACTIVE: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  ARCHIVED: 'border-amber-200 bg-amber-50 text-amber-800',
  COMPLETED: 'border-blue-200 bg-blue-50 text-blue-700',
};

export interface ProjectCardProps {
  project: Project;
  isSelected?: boolean;
  onSelect?: (project: Project) => void;
  onSettings?: (project: Project) => void;
  className?: string;
}

function formatProjectLabel(value: string): string {
  return value.replace(/_/g, ' ');
}

function getHealthClass(score: number): string {
  if (score >= 70) {
    return 'bg-emerald-500';
  }
  if (score >= 40) {
    return 'bg-amber-500';
  }
  return 'bg-red-500';
}

/**
 * Card component for a single project.
 */
export const ProjectCard: React.FC<ProjectCardProps> = ({
  project,
  isSelected = false,
  onSelect,
  onSettings,
  className,
}) => {
  const healthScore = project.aiHealthScore ?? null;

  const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>): void => {
    if (!onSelect || (event.key !== 'Enter' && event.key !== ' ')) {
      return;
    }
    event.preventDefault();
    onSelect(project);
  };

  return (
    <article
      className={`rounded-xl border bg-white p-3 shadow-sm transition ${
        isSelected ? 'border-blue-500 ring-2 ring-blue-100' : 'border-slate-200'
      } ${onSelect ? 'cursor-pointer hover:border-blue-300 hover:shadow-md' : ''} ${
        className ?? ''
      }`}
      role={onSelect ? 'button' : undefined}
      tabIndex={onSelect ? 0 : undefined}
      onClick={onSelect ? () => onSelect(project) : undefined}
      onKeyDown={handleKeyDown}
    >
      <div className="flex items-start gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <h3 className="min-w-0 flex-1 truncate text-sm font-semibold text-slate-900">
              {project.name}
            </h3>
            <span
              className={`shrink-0 rounded-full border px-2 py-0.5 text-[0.65rem] font-semibold uppercase tracking-wide ${STATUS_CLASSES[project.status]}`}
            >
              {project.status}
            </span>
          </div>

          {project.description && (
            <p className="mt-1 line-clamp-1 text-xs text-slate-500">
              {project.description}
            </p>
          )}

          <div className="mt-2 flex flex-wrap items-center gap-2">
            <span className="rounded-full border border-slate-200 px-2 py-0.5 text-[0.65rem] font-medium uppercase tracking-wide text-slate-600">
              {formatProjectLabel(project.type)}
            </span>
            {project.lifecyclePhase && (
              <span className="rounded-full border border-slate-200 px-2 py-0.5 text-[0.65rem] font-medium uppercase tracking-wide text-slate-600">
                {formatProjectLabel(project.lifecyclePhase)}
              </span>
            )}
          </div>

          {healthScore !== null && (
            <div
              className="mt-3 flex items-center gap-2"
              title={`AI health score: ${healthScore}%`}
            >
              <BrainIcon
                size={12}
                className="text-slate-500"
                aria-hidden="true"
              />
              <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-100">
                <div
                  className={`h-full rounded-full ${getHealthClass(healthScore)}`}
                  style={{ width: `${Math.max(0, Math.min(100, healthScore))}%` }}
                />
              </div>
              <span className="w-8 text-right text-xs font-medium text-slate-600">
                {healthScore}%
              </span>
            </div>
          )}
        </div>

        {onSettings && (
          <button
            type="button"
            aria-label={`Open settings for ${project.name}`}
            title="Project settings"
            className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-slate-500 transition hover:bg-slate-100 hover:text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
            onClick={(event) => {
              event.stopPropagation();
              onSettings(project);
            }}
          >
            <SettingsIcon size={15} aria-hidden="true" />
          </button>
        )}
      </div>
    </article>
  );
};
