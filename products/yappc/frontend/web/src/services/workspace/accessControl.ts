export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'EDITOR' | 'VIEWER';

export interface ResourceCapabilities {
  readonly read: boolean;
  readonly create: boolean;
  readonly update: boolean;
  readonly delete: boolean;
  readonly include?: boolean;
  readonly comment?: boolean;
  readonly reason?: string;
}

export interface WorkspaceAccessFields {
  readonly role?: string;
  readonly isOwner?: boolean;
  readonly capabilities?: Partial<ResourceCapabilities>;
}

export interface ProjectAccessFields {
  readonly role?: string;
  readonly isOwned?: boolean;
  readonly isIncluded?: boolean;
  readonly readOnly?: boolean;
  readonly capabilities?: Partial<ResourceCapabilities>;
}

const READ_ONLY_CAPABILITIES: ResourceCapabilities = {
  read: true,
  create: false,
  update: false,
  delete: false,
  include: false,
  comment: true,
};

export function normalizeWorkspaceRole(role: string | undefined, isOwner?: boolean): WorkspaceRole {
  if (isOwner) return 'OWNER';
  if (role === 'OWNER' || role === 'ADMIN' || role === 'EDITOR' || role === 'VIEWER') {
    return role;
  }
  return 'VIEWER';
}

export function deriveCapabilities(
  access: WorkspaceAccessFields | ProjectAccessFields,
  fallback: ResourceCapabilities = READ_ONLY_CAPABILITIES
): ResourceCapabilities {
  const provided = access.capabilities;
  if (!provided) return fallback;

  return {
    read: provided.read ?? fallback.read,
    create: provided.create ?? fallback.create,
    update: provided.update ?? fallback.update,
    delete: provided.delete ?? fallback.delete,
    include: provided.include ?? fallback.include,
    comment: provided.comment ?? fallback.comment,
    reason: provided.reason ?? fallback.reason,
  };
}

export function workspaceIsOwner(workspace: WorkspaceAccessFields): boolean {
  return workspace.isOwner === true || normalizeWorkspaceRole(workspace.role, workspace.isOwner) === 'OWNER';
}

export function projectCanEdit(project: ProjectAccessFields): boolean {
  return project.readOnly !== true && deriveCapabilities(project).update;
}

export function projectGuidanceRole(project: ProjectAccessFields | undefined): 'owner' | 'collaborator' | 'viewer' {
  if (!project) return 'viewer';
  const role = normalizeWorkspaceRole(project.role);
  if (project.isOwned === true && (role === 'OWNER' || role === 'ADMIN')) return 'owner';
  if (projectCanEdit(project)) return 'collaborator';
  return 'viewer';
}
