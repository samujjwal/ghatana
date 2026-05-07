import { describe, expect, it } from 'vitest';

import {
  deriveCapabilities,
  normalizeWorkspaceRole,
  projectCanEdit,
  projectGuidanceRole,
  workspaceIsOwner,
} from '../accessControl';

describe('workspace access control normalization', () => {
  it('does not invent workspace ownership when the server omits role metadata', () => {
    const workspace = { role: undefined, isOwner: undefined };

    expect(normalizeWorkspaceRole(workspace.role, workspace.isOwner)).toBe('VIEWER');
    expect(workspaceIsOwner(workspace)).toBe(false);
    expect(deriveCapabilities(workspace).update).toBe(false);
  });

  it('uses server-backed ownership and capabilities for admin workspaces', () => {
    const workspace = {
      role: 'ADMIN',
      isOwner: false,
      capabilities: { read: true, update: true, delete: false },
    };

    expect(normalizeWorkspaceRole(workspace.role, workspace.isOwner)).toBe('ADMIN');
    expect(workspaceIsOwner(workspace)).toBe(false);
    expect(deriveCapabilities(workspace).update).toBe(true);
  });

  it('treats included projects as read-only even when listed beside owned projects', () => {
    const project = {
      isOwned: false,
      isIncluded: true,
      readOnly: true,
      role: 'VIEWER',
      capabilities: { read: true, update: false },
    };

    expect(projectCanEdit(project)).toBe(false);
    expect(projectGuidanceRole(project)).toBe('viewer');
  });

  it('allows editable owned projects only when server capabilities allow updates', () => {
    const project = {
      isOwned: true,
      role: 'EDITOR',
      capabilities: { read: true, update: true },
    };

    expect(projectCanEdit(project)).toBe(true);
    expect(projectGuidanceRole(project)).toBe('collaborator');
  });
});
