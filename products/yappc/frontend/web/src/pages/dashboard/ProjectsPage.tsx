import React, { useMemo } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';

interface ProjectSummary {
  id: string;
  name: string;
  key: string;
  status: 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED';
  description?: string;
  updatedAt: string;
}

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'bg-zinc-700 text-zinc-300',
  ACTIVE: 'bg-green-900/60 text-green-300',
  COMPLETED: 'bg-blue-900/60 text-blue-300',
  ARCHIVED: 'bg-zinc-800 text-zinc-500',
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
      if (!res.ok) throw new Error('Failed to load projects');
      return res.json();
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
          <h1 className="text-2xl font-bold text-zinc-100">Projects</h1>
          <p className="mt-1 text-sm text-zinc-400">
            {sorted.length} project{sorted.length !== 1 ? 's' : ''} in this workspace
          </p>
        </div>
        <Link
          to="/projects/new"
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500"
        >
          New project
        </Link>
      </div>

      {/* Content */}
      <div className="mt-6">
        {isLoading && (
          <div className="flex items-center justify-center py-16 text-zinc-500">
            Loading projects…
          </div>
        )}

        {error && (
          <div className="rounded-md border border-red-800 bg-red-950/50 px-4 py-3 text-sm text-red-300">
            {error instanceof Error ? error.message : 'Failed to load projects'}
          </div>
        )}

        {!isLoading && !error && sorted.length === 0 && (
          <div className="flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-zinc-800 py-16 text-center">
            <p className="text-zinc-400">No projects yet</p>
            <Link
              to="/projects/new"
              className="mt-4 rounded-md bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-500"
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
                className="group rounded-lg border border-zinc-800 bg-zinc-900/50 p-5 transition hover:border-zinc-700 hover:bg-zinc-900"
              >
                <div className="flex items-start justify-between">
                  <div className="min-w-0">
                    <h3 className="truncate text-sm font-semibold text-zinc-100 group-hover:text-blue-400">
                      {project.name}
                    </h3>
                    <span className="text-xs font-mono text-zinc-500">{project.key}</span>
                  </div>
                  <span
                    className={`ml-2 shrink-0 rounded-full px-2 py-0.5 text-[10px] font-medium ${STATUS_COLORS[project.status] ?? STATUS_COLORS.DRAFT}`}
                  >
                    {project.status}
                  </span>
                </div>
                {project.description && (
                  <p className="mt-2 line-clamp-2 text-xs text-zinc-400">
                    {project.description}
                  </p>
                )}
                <p className="mt-3 text-[10px] text-zinc-600">
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
