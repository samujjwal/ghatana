/**
 * useUrlContext
 *
 * Infers workspace ID, project ID, and sprint/epic context from the current URL
 * parameters so consumers don't need to manually read useParams in every component.
 *
 * @doc.type hook
 * @doc.purpose URL-driven workspace + project context inference
 * @doc.layer product
 * @doc.pattern Context Hook
 */

import { useParams } from 'react-router-dom';

export interface UrlContext {
  /** Workspace slug or ID from the URL, e.g. /w/:workspaceId/... */
  workspaceId: string | undefined;
  /** Project ID from the URL, e.g. /w/:workspaceId/projects/:projectId/... */
  projectId: string | undefined;
  /** Sprint ID from the URL, e.g. .../sprints/:sprintId/... */
  sprintId: string | undefined;
  /** Epic ID from the URL, e.g. .../epics/:epicId/... */
  epicId: string | undefined;
  /** Run ID from the URL, e.g. .../runs/:runId/... */
  runId: string | undefined;
  /** Returns true only when both workspaceId and projectId are present */
  isProjectScoped: boolean;
}

/**
 * Infer workspace, project, sprint, epic, and run context from the URL.
 *
 * Example routes:
 *   /w/:workspaceId/projects/:projectId/sprints/:sprintId
 *   /w/:workspaceId/projects/:projectId/epics/:epicId
 *   /w/:workspaceId/projects/:projectId/runs/:runId
 */
export function useUrlContext(): UrlContext {
  const params = useParams<{
    workspaceId?: string;
    projectId?: string;
    sprintId?: string;
    epicId?: string;
    runId?: string;
  }>();

  const workspaceId = params.workspaceId || undefined;
  const projectId = params.projectId || undefined;
  const sprintId = params.sprintId || undefined;
  const epicId = params.epicId || undefined;
  const runId = params.runId || undefined;

  return {
    workspaceId,
    projectId,
    sprintId,
    epicId,
    runId,
    isProjectScoped: Boolean(workspaceId && projectId),
  };
}
