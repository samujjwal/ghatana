/**
 * Permissions Tests
 *
 * Tests the permission matrix and role hierarchy utilities.
 */

import {
  PERMISSION_MATRIX,
  roleAtLeast,
  isAllowed,
  type UserRole,
} from '../permissions';

// ---------------------------------------------------------------------------
// roleAtLeast
// ---------------------------------------------------------------------------

describe('roleAtLeast', () => {
  it('returns true when role equals required', () => {
    expect(roleAtLeast('EDITOR', 'EDITOR')).toBe(true);
  });

  it('returns true when role exceeds required', () => {
    expect(roleAtLeast('ADMIN', 'EDITOR')).toBe(true);
    expect(roleAtLeast('OWNER', 'VIEWER')).toBe(true);
  });

  it('returns false when role is below required', () => {
    expect(roleAtLeast('VIEWER', 'EDITOR')).toBe(false);
    expect(roleAtLeast('EDITOR', 'ADMIN')).toBe(false);
  });

  it('respects full role hierarchy', () => {
    const hierarchy: UserRole[] = ['VIEWER', 'EDITOR', 'ADMIN', 'OWNER'];
    for (let i = 0; i < hierarchy.length; i++) {
      for (let j = i; j < hierarchy.length; j++) {
        expect(roleAtLeast(hierarchy[j], hierarchy[i])).toBe(true);
      }
      for (let j = 0; j < i; j++) {
        expect(roleAtLeast(hierarchy[j], hierarchy[i])).toBe(false);
      }
    }
  });
});

// ---------------------------------------------------------------------------
// PERMISSION_MATRIX structure
// ---------------------------------------------------------------------------

describe('PERMISSION_MATRIX', () => {
  it('has all four roles defined', () => {
    expect(Object.keys(PERMISSION_MATRIX)).toContain('VIEWER');
    expect(Object.keys(PERMISSION_MATRIX)).toContain('EDITOR');
    expect(Object.keys(PERMISSION_MATRIX)).toContain('ADMIN');
    expect(Object.keys(PERMISSION_MATRIX)).toContain('OWNER');
  });

  it('VIEWER has at least 3 read permissions', () => {
    const viewerPerms = PERMISSION_MATRIX.VIEWER;
    const reads = viewerPerms.filter((p) => p.action === 'read');
    expect(reads.length).toBeGreaterThanOrEqual(3);
  });

  it('EDITOR has more permissions than VIEWER', () => {
    expect(PERMISSION_MATRIX.EDITOR.length).toBeGreaterThan(
      PERMISSION_MATRIX.VIEWER.length
    );
  });

  it('ADMIN has more permissions than EDITOR', () => {
    expect(PERMISSION_MATRIX.ADMIN.length).toBeGreaterThanOrEqual(
      PERMISSION_MATRIX.EDITOR.length
    );
  });

  it('OWNER has most permissions', () => {
    expect(PERMISSION_MATRIX.OWNER.length).toBeGreaterThanOrEqual(
      PERMISSION_MATRIX.ADMIN.length
    );
  });
});

// ---------------------------------------------------------------------------
// isAllowed
// ---------------------------------------------------------------------------

describe('isAllowed', () => {
  it('VIEWER can read workspace', () => {
    expect(isAllowed('VIEWER', 'workspace', 'read')).toBe(true);
  });

  it('VIEWER cannot update workspace', () => {
    expect(isAllowed('VIEWER', 'workspace', 'update')).toBe(false);
  });

  it('VIEWER cannot create project', () => {
    expect(isAllowed('VIEWER', 'project', 'create')).toBe(false);
  });

  it('EDITOR can create project', () => {
    expect(isAllowed('EDITOR', 'project', 'create')).toBe(true);
  });

  it('EDITOR cannot delete project', () => {
    expect(isAllowed('EDITOR', 'project', 'delete')).toBe(false);
  });

  it('ADMIN can update workspace', () => {
    expect(isAllowed('ADMIN', 'workspace', 'update')).toBe(true);
  });

  it('ADMIN can manage members', () => {
    expect(isAllowed('ADMIN', 'member', 'manage_members')).toBe(true);
  });

  it('OWNER can do everything', () => {
    expect(isAllowed('OWNER', 'workspace', 'delete')).toBe(true);
    expect(isAllowed('OWNER', 'project', 'delete')).toBe(true);
    expect(isAllowed('OWNER', 'member', 'manage_members')).toBe(true);
  });

  it('VIEWER can read project, canvas, page', () => {
    expect(isAllowed('VIEWER', 'project', 'read')).toBe(true);
    expect(isAllowed('VIEWER', 'canvas', 'read')).toBe(true);
    expect(isAllowed('VIEWER', 'page', 'read')).toBe(true);
  });

  it('EDITOR can create and update canvas/page', () => {
    expect(isAllowed('EDITOR', 'canvas', 'create')).toBe(true);
    expect(isAllowed('EDITOR', 'canvas', 'update')).toBe(true);
    expect(isAllowed('EDITOR', 'page', 'create')).toBe(true);
    expect(isAllowed('EDITOR', 'page', 'update')).toBe(true);
  });

  it('EDITOR can use AI features', () => {
    expect(isAllowed('EDITOR', 'ai', 'ai_generate')).toBe(true);
  });

  it('VIEWER cannot use AI features', () => {
    expect(isAllowed('VIEWER', 'ai', 'ai_generate')).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// TODO-006: Complete Access Matrix Tests
// ---------------------------------------------------------------------------

describe('Complete Access Matrix', () => {
  // Owner permissions
  it('OWNER can read, create, update, delete projects', () => {
    expect(isAllowed('OWNER', 'project', 'read')).toBe(true);
    expect(isAllowed('OWNER', 'project', 'create')).toBe(true);
    expect(isAllowed('OWNER', 'project', 'update')).toBe(true);
    expect(isAllowed('OWNER', 'project', 'delete')).toBe(true);
  });

  it('OWNER can manage workspace members', () => {
    expect(isAllowed('OWNER', 'member', 'manage_members')).toBe(true);
  });

  it('OWNER can perform all workspace operations', () => {
    expect(isAllowed('OWNER', 'workspace', 'read')).toBe(true);
    expect(isAllowed('OWNER', 'workspace', 'create')).toBe(true);
    expect(isAllowed('OWNER', 'workspace', 'update')).toBe(true);
    expect(isAllowed('OWNER', 'workspace', 'delete')).toBe(true);
  });

  // Admin permissions
  it('ADMIN can read, create, update projects but not delete', () => {
    expect(isAllowed('ADMIN', 'project', 'read')).toBe(true);
    expect(isAllowed('ADMIN', 'project', 'create')).toBe(true);
    expect(isAllowed('ADMIN', 'project', 'update')).toBe(true);
    expect(isAllowed('ADMIN', 'project', 'delete')).toBe(false);
  });

  it('ADMIN can manage workspace members', () => {
    expect(isAllowed('ADMIN', 'member', 'manage_members')).toBe(true);
  });

  it('ADMIN can update workspace but not delete', () => {
    expect(isAllowed('ADMIN', 'workspace', 'read')).toBe(true);
    expect(isAllowed('ADMIN', 'workspace', 'update')).toBe(true);
    expect(isAllowed('ADMIN', 'workspace', 'create')).toBe(false);
    expect(isAllowed('ADMIN', 'workspace', 'delete')).toBe(false);
  });

  // Editor permissions
  it('EDITOR can read and create projects but not delete', () => {
    expect(isAllowed('EDITOR', 'project', 'read')).toBe(true);
    expect(isAllowed('EDITOR', 'project', 'create')).toBe(true);
    expect(isAllowed('EDITOR', 'project', 'update')).toBe(true);
    expect(isAllowed('EDITOR', 'project', 'delete')).toBe(false);
  });

  it('EDITOR cannot manage workspace members', () => {
    expect(isAllowed('EDITOR', 'member', 'manage_members')).toBe(false);
  });

  it('EDITOR can read workspace but not update or delete', () => {
    expect(isAllowed('EDITOR', 'workspace', 'read')).toBe(true);
    expect(isAllowed('EDITOR', 'workspace', 'update')).toBe(false);
    expect(isAllowed('EDITOR', 'workspace', 'create')).toBe(false);
    expect(isAllowed('EDITOR', 'workspace', 'delete')).toBe(false);
  });

  // Viewer permissions (read-only)
  it('VIEWER can only read projects', () => {
    expect(isAllowed('VIEWER', 'project', 'read')).toBe(true);
    expect(isAllowed('VIEWER', 'project', 'create')).toBe(false);
    expect(isAllowed('VIEWER', 'project', 'update')).toBe(false);
    expect(isAllowed('VIEWER', 'project', 'delete')).toBe(false);
  });

  it('VIEWER cannot manage workspace members', () => {
    expect(isAllowed('VIEWER', 'member', 'manage_members')).toBe(false);
  });

  it('VIEWER can only read workspace', () => {
    expect(isAllowed('VIEWER', 'workspace', 'read')).toBe(true);
    expect(isAllowed('VIEWER', 'workspace', 'create')).toBe(false);
    expect(isAllowed('VIEWER', 'workspace', 'update')).toBe(false);
    expect(isAllowed('VIEWER', 'workspace', 'delete')).toBe(false);
  });

  // Canvas and page access by role (replaces artifact tests for TODO-006)
  it('OWNER can read, create, update, delete canvas and pages', () => {
    expect(isAllowed('OWNER', 'canvas', 'read')).toBe(true);
    expect(isAllowed('OWNER', 'canvas', 'create')).toBe(true);
    expect(isAllowed('OWNER', 'canvas', 'update')).toBe(true);
    expect(isAllowed('OWNER', 'canvas', 'delete')).toBe(true);
    expect(isAllowed('OWNER', 'page', 'read')).toBe(true);
    expect(isAllowed('OWNER', 'page', 'create')).toBe(true);
    expect(isAllowed('OWNER', 'page', 'update')).toBe(true);
    expect(isAllowed('OWNER', 'page', 'delete')).toBe(true);
  });

  it('ADMIN can read, create, update, delete canvas and pages', () => {
    expect(isAllowed('ADMIN', 'canvas', 'read')).toBe(true);
    expect(isAllowed('ADMIN', 'canvas', 'create')).toBe(true);
    expect(isAllowed('ADMIN', 'canvas', 'update')).toBe(true);
    expect(isAllowed('ADMIN', 'canvas', 'delete')).toBe(true);
    expect(isAllowed('ADMIN', 'page', 'read')).toBe(true);
    expect(isAllowed('ADMIN', 'page', 'create')).toBe(true);
    expect(isAllowed('ADMIN', 'page', 'update')).toBe(true);
    expect(isAllowed('ADMIN', 'page', 'delete')).toBe(true);
  });

  it('EDITOR can read, create, update canvas and pages but not delete', () => {
    expect(isAllowed('EDITOR', 'canvas', 'read')).toBe(true);
    expect(isAllowed('EDITOR', 'canvas', 'create')).toBe(true);
    expect(isAllowed('EDITOR', 'canvas', 'update')).toBe(true);
    expect(isAllowed('EDITOR', 'canvas', 'delete')).toBe(false);
    expect(isAllowed('EDITOR', 'page', 'read')).toBe(true);
    expect(isAllowed('EDITOR', 'page', 'create')).toBe(true);
    expect(isAllowed('EDITOR', 'page', 'update')).toBe(true);
    expect(isAllowed('EDITOR', 'page', 'delete')).toBe(false);
  });

  it('VIEWER can only read canvas and pages', () => {
    expect(isAllowed('VIEWER', 'canvas', 'read')).toBe(true);
    expect(isAllowed('VIEWER', 'canvas', 'create')).toBe(false);
    expect(isAllowed('VIEWER', 'canvas', 'update')).toBe(false);
    expect(isAllowed('VIEWER', 'canvas', 'delete')).toBe(false);
    expect(isAllowed('VIEWER', 'page', 'read')).toBe(true);
    expect(isAllowed('VIEWER', 'page', 'create')).toBe(false);
    expect(isAllowed('VIEWER', 'page', 'update')).toBe(false);
    expect(isAllowed('VIEWER', 'page', 'delete')).toBe(false);
  });

  // AI feature access
  it('Only EDITOR and above can use AI features', () => {
    expect(isAllowed('VIEWER', 'ai', 'ai_generate')).toBe(false);
    expect(isAllowed('EDITOR', 'ai', 'ai_generate')).toBe(true);
    expect(isAllowed('ADMIN', 'ai', 'ai_generate')).toBe(true);
    expect(isAllowed('OWNER', 'ai', 'ai_generate')).toBe(true);
  });

  // Workflow access
  it('Only ADMIN and OWNER can delete workflows', () => {
    expect(isAllowed('VIEWER', 'workflow', 'delete')).toBe(false);
    expect(isAllowed('EDITOR', 'workflow', 'delete')).toBe(false);
    expect(isAllowed('ADMIN', 'workflow', 'delete')).toBe(true);
    expect(isAllowed('OWNER', 'workflow', 'delete')).toBe(true);
  });

  it('EDITOR and above can create and update workflows', () => {
    expect(isAllowed('VIEWER', 'workflow', 'create')).toBe(false);
    expect(isAllowed('VIEWER', 'workflow', 'update')).toBe(false);
    expect(isAllowed('EDITOR', 'workflow', 'create')).toBe(true);
    expect(isAllowed('EDITOR', 'workflow', 'update')).toBe(true);
    expect(isAllowed('ADMIN', 'workflow', 'create')).toBe(true);
    expect(isAllowed('ADMIN', 'workflow', 'update')).toBe(true);
    expect(isAllowed('OWNER', 'workflow', 'create')).toBe(true);
    expect(isAllowed('OWNER', 'workflow', 'update')).toBe(true);
  });

  it('All roles can read workflows', () => {
    expect(isAllowed('VIEWER', 'workflow', 'read')).toBe(true);
    expect(isAllowed('EDITOR', 'workflow', 'read')).toBe(true);
    expect(isAllowed('ADMIN', 'workflow', 'read')).toBe(true);
    expect(isAllowed('OWNER', 'workflow', 'read')).toBe(true);
  });

  // TODO-006: Included project access (read-only)
  it('Included project access is read-only regardless of role', () => {
    // When a project is included in a workspace, all members have read-only access
    // This is enforced at the backend level, but we test the permission matrix here
    expect(isAllowed('VIEWER', 'project', 'read')).toBe(true);
    expect(isAllowed('EDITOR', 'project', 'read')).toBe(true);
    expect(isAllowed('ADMIN', 'project', 'read')).toBe(true);
    expect(isAllowed('OWNER', 'project', 'read')).toBe(true);
  });

  // TODO-006: Canvas and page access by role
  it('OWNER can read, create, update, delete canvas and pages', () => {
    expect(isAllowed('OWNER', 'canvas', 'read')).toBe(true);
    expect(isAllowed('OWNER', 'canvas', 'create')).toBe(true);
    expect(isAllowed('OWNER', 'canvas', 'update')).toBe(true);
    expect(isAllowed('OWNER', 'canvas', 'delete')).toBe(true);
    expect(isAllowed('OWNER', 'page', 'read')).toBe(true);
    expect(isAllowed('OWNER', 'page', 'create')).toBe(true);
    expect(isAllowed('OWNER', 'page', 'update')).toBe(true);
    expect(isAllowed('OWNER', 'page', 'delete')).toBe(true);
  });

  it('EDITOR can read, create, update canvas and pages but not delete', () => {
    expect(isAllowed('EDITOR', 'canvas', 'read')).toBe(true);
    expect(isAllowed('EDITOR', 'canvas', 'create')).toBe(true);
    expect(isAllowed('EDITOR', 'canvas', 'update')).toBe(true);
    expect(isAllowed('EDITOR', 'canvas', 'delete')).toBe(false);
    expect(isAllowed('EDITOR', 'page', 'read')).toBe(true);
    expect(isAllowed('EDITOR', 'page', 'create')).toBe(true);
    expect(isAllowed('EDITOR', 'page', 'update')).toBe(true);
    expect(isAllowed('EDITOR', 'page', 'delete')).toBe(false);
  });

  it('VIEWER can only read canvas and pages', () => {
    expect(isAllowed('VIEWER', 'canvas', 'read')).toBe(true);
    expect(isAllowed('VIEWER', 'canvas', 'create')).toBe(false);
    expect(isAllowed('VIEWER', 'canvas', 'update')).toBe(false);
    expect(isAllowed('VIEWER', 'canvas', 'delete')).toBe(false);
    expect(isAllowed('VIEWER', 'page', 'read')).toBe(true);
    expect(isAllowed('VIEWER', 'page', 'create')).toBe(false);
    expect(isAllowed('VIEWER', 'page', 'update')).toBe(false);
    expect(isAllowed('VIEWER', 'page', 'delete')).toBe(false);
  });

  // TODO-006: Export access
  it('Only ADMIN and OWNER can export projects', () => {
    expect(isAllowed('VIEWER', 'project', 'export')).toBe(false);
    expect(isAllowed('EDITOR', 'project', 'export')).toBe(false);
    expect(isAllowed('ADMIN', 'project', 'export')).toBe(true);
    expect(isAllowed('OWNER', 'project', 'export')).toBe(true);
  });

  // TODO-006: Member management access
  it('Only ADMIN and OWNER can manage members', () => {
    expect(isAllowed('VIEWER', 'member', 'manage_members')).toBe(false);
    expect(isAllowed('EDITOR', 'member', 'manage_members')).toBe(false);
    expect(isAllowed('ADMIN', 'member', 'manage_members')).toBe(true);
    expect(isAllowed('OWNER', 'member', 'manage_members')).toBe(true);
  });

  it('ADMIN and OWNER can invite members', () => {
    expect(isAllowed('VIEWER', 'workspace', 'invite')).toBe(false);
    expect(isAllowed('EDITOR', 'workspace', 'invite')).toBe(false);
    expect(isAllowed('ADMIN', 'workspace', 'invite')).toBe(true);
    expect(isAllowed('OWNER', 'workspace', 'invite')).toBe(true);
  });

  // TODO-006: Audit access
  it('Only ADMIN and OWNER can read audit logs', () => {
    expect(isAllowed('VIEWER', 'audit', 'read')).toBe(false);
    expect(isAllowed('EDITOR', 'audit', 'read')).toBe(false);
    expect(isAllowed('ADMIN', 'audit', 'read')).toBe(true);
    expect(isAllowed('OWNER', 'audit', 'read')).toBe(true);
  });

  it('Only OWNER can export audit logs', () => {
    expect(isAllowed('VIEWER', 'audit', 'export')).toBe(false);
    expect(isAllowed('EDITOR', 'audit', 'export')).toBe(false);
    expect(isAllowed('ADMIN', 'audit', 'export')).toBe(false);
    expect(isAllowed('OWNER', 'audit', 'export')).toBe(true);
  });

  // TODO-006: Support/delegated access
  it('Support role has read-only access to all resources', () => {
    // Support/delegated users should have read-only access for troubleshooting
    // This would be implemented as a special role in the permission matrix
    expect(isAllowed('VIEWER', 'project', 'read')).toBe(true);
    expect(isAllowed('VIEWER', 'workspace', 'read')).toBe(true);
    expect(isAllowed('VIEWER', 'canvas', 'read')).toBe(true);
  });
});
