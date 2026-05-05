import React, { useMemo } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { parseJsonResponse, readErrorResponse } from '@/lib/http';

interface ProjectSummary {
  id: string;
  name: string;
  key: string;
  status: 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED';
  description?: string;
  updatedAt: string;
}

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'bg-surface-muted text-fg-muted',
  ACTIVE: 'bg-success-bg/60 text-success-color',
  COMPLETED: 'bg-info-bg/60 text-info-color',
  ARCHIVED: 'bg-surface text-fg-muted',
};

/**
 * ProjectsPage — lists all projects in the current workspace.
 *
 * @doc.type component
 * @doc.purpose Dashboard project listing page
 * @doc.layer product
 */
const ProjectsPage: React.FC = () => {
  const { data: projects, isLoading, error } = useQuery<ProjectSummary[]>({
    queryKey: ['projects'],
    queryFn: async () => {
      const res = await fetch('/api/projects', {
        headers: { Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}` },
      });
      if (!res.ok) {
        throw new Error(await readErrorResponse(res, 'Failed to load projects'));
      }
      return parseJsonResponse<ProjectSummary[]>(res, 'dashboard projects');
    },
  });

  const sorted = useMemo(
    () =>
      (projects ?? []).slice().sort(
        (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
      ),
    [projects],
  );

  return (
    <div className="mx-auto max-w-5xl px-6 py-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-fg-muted">Projects</h1>
          <p className="mt-1 text-sm text-fg-muted">
            {sorted.length} project{sorted.length !== 1 ? 's' : ''} in this workspace
          </p>
        </div>
        <Link
          to="/projects/new"
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-info-bg"
        >
          New project
        </Link>
      </div>

      {/* Content */}
      <div className="mt-6">
        {isLoading && (
          <div className="flex items-center justify-center py-16 text-fg-muted">
            Loading projects…
          </div>
        )}

        {error && (
          <div className="rounded-md border border-destructive-border bg-destructive-bg/50 px-4 py-3 text-sm text-destructive">
            {error instanceof Error ? error.message : 'Failed to load projects'}
          </div>
        )}

        {!isLoading && !error && sorted.length === 0 && (
          <div className="flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-border py-16 text-center">
            <p className="text-fg-muted">No projects yet</p>
            <Link
              to="/projects/new"
              className="mt-4 rounded-md bg-primary px-4 py-2 text-sm text-white hover:bg-info-bg"
            >
              Create your first project
            </Link>
          </div>
        )}

        {sorted.length > 0 && (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {sorted.map((project) => (
              <Link
                key={project.id}
                to={`/projects/${project.id}`}
                className="group rounded-lg border border-border bg-surface/50 p-5 transition hover:border-border hover:bg-surface"
              >
                <div className="flex items-start justify-between">
                  <div className="min-w-0">
                    <h3 className="truncate text-sm font-semibold text-fg-muted group-hover:text-info-color">
                      {project.name}
                    </h3>
                    <span className="text-xs font-mono text-fg-muted">{project.key}</span>
                  </div>
                  <span
                    className={`ml-2 shrink-0 rounded-full px-2 py-0.5 text-[10px] font-medium ${STATUS_COLORS[project.status] ?? STATUS_COLORS.DRAFT}`}
                  >
                    {project.status}
                  </span>
                </div>
                {project.description && (
                  <p className="mt-2 line-clamp-2 text-xs text-fg-muted">
                    {project.description}
                  </p>
                )}
                <p className="mt-3 text-[10px] text-fg-muted">
                  Updated {new Date(project.updatedAt).toLocaleDateString()}
                </p>
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default ProjectsPage;
