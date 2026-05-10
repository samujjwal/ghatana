import type { components } from '@/clients/generated/openapi';

export type WorkspaceContract = components['schemas']['Workspace'];
export type ProjectContract = components['schemas']['ProjectWithOwnership'];
export type ProjectTypeContract = components['schemas']['ProjectType'];
export type WorkspaceListResponseContract = components['schemas']['WorkspaceListResponse'];
export type WorkspaceDetailResponseContract = components['schemas']['WorkspaceDetailResponse'];
export type ProjectsResponseContract = components['schemas']['ProjectsResponse'];
export type WorkspaceResponseContract = components['schemas']['WorkspaceResponse'];

export type ProjectResponseContract = components['schemas']['ProjectResponse'];
export type NameSuggestionResponseContract = components['schemas']['NameSuggestionResponse'];
export type ProjectSetupSuggestionContract = components['schemas']['ProjectSetupSuggestion'];
export type CreateWorkspaceRequestContract = components['schemas']['CreateWorkspaceRequest'];
export type CreateProjectRequestContract = components['schemas']['CreateProjectRequest'];
export type UpdateWorkspaceRequestContract = components['schemas']['UpdateWorkspaceRequest'];
export type UpdateProjectRequestContract = components['schemas']['UpdateProjectRequest'];
export type ProjectStatusContract = components['schemas']['ProjectStatus'];
export type SaveSyncStatusContract =
  | 'local-only'
  | 'syncing'
  | 'remote-saved'
  | 'remote-failed'
  | 'stale'
  | 'conflict';
export type ProjectActivitySourceContract = 'lifecycle' | 'audit';
export type PreviewStatusContract =
  | 'unconfigured'
  | 'loading'
  | 'external-ready'
  | 'unavailable'
  | 'error';
export type ReleasePlanningStatusContract =
	| 'final-phase'
	| 'blocked'
	| 'approval-needed'
	| 'planning-ready';
export type LifecycleReviewStatusContract = 'review-required' | 'ready-for-guided-apply';
export interface ProjectActivityEventContract {
	id: string;
	source: ProjectActivitySourceContract;
	action: string;
	summary: string;
	timestamp: string;
	actor: string | null;
	severity?: string | null;
	success?: boolean | null;
}
export interface ProjectActivityResponseContract {
	projectId: string;
	activity: ProjectActivityEventContract[];
}
export interface ReleasePlanningStatusViewContract {
	status: ReleasePlanningStatusContract;
	label: string;
	detail: string;
}
export type ProjectShellContract = {
	name: string;
	type?: ProjectTypeContract;
	workspaceId?: string;
	currentPhase: string;
	phaseProgress?: number;
	// TODO-004: Backend capability contract - authoritative source of truth
	userId?: string;
	role?: string;
	capabilities?: {
		read: boolean;
		create: boolean;
		update: boolean;
		delete: boolean;
		include?: boolean;
		comment?: boolean;
		export?: boolean;
		share?: boolean;
	};
	// Deprecated - use capabilities instead (TODO-004)
	isOwner?: boolean;
	isIncluded?: boolean;
};