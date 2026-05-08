/**
 * ProjectList
 *
 * Renders a scrollable list of ProjectCards.
 *
 * @doc.type component
 * @doc.purpose Display a list of projects with selection, filtering, and actions
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import React from 'react';

import type { Project } from 'yappc-core/types';

import { ProjectCard } from './ProjectCard';

export interface ProjectListProps {
  projects: Project[];
  selectedId?: string | null;
  isLoading?: boolean;
  error?: Error | null;
  onSelect?: (project: Project) => void;
  onSettings?: (project: Project) => void;
  emptyMessage?: string;
  className?: string;
}

/**
 * Renders a list of projects.
 */
export const ProjectList: React.FC<ProjectListProps> = ({
  projects,
  selectedId,
  isLoading,
  error,
  onSelect,
  onSettings,
  emptyMessage = 'No projects found.',
  className,
}) => {
  if (isLoading) {
    return (
      <div className="flex justify-center py-8" aria-label="Loading projects">
        <span
          className="h-8 w-8 animate-spin rounded-full border-2 border-slate-200 border-t-blue-600"
          aria-hidden="true"
        />
      </div>
    );
  }

  if (error) {
    return (
      <div
        role="alert"
        className="m-1 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
      >
        {error.message}
      </div>
    );
  }

  if (projects.length === 0) {
    return (
      <div className="flex justify-center py-8 text-sm text-slate-500">
        {emptyMessage}
      </div>
    );
  }

  return (
    <div className={`flex flex-col gap-2 overflow-y-auto ${className ?? ''}`}>
      {projects.map((project) => (
        <ProjectCard
          key={project.id}
          project={project}
          isSelected={project.id === selectedId}
          onSelect={onSelect}
          onSettings={onSettings}
        />
      ))}
    </div>
  );
};
