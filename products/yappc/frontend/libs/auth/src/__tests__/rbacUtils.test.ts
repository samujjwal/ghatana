import { describe, it, expect } from 'vitest';
import { RBACUtils } from '../rbac/utils.js';
import type {
  Role,
  Permission,
  AuthorizationContext,
  AccessControlList,
} from '../rbac/types.js';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const READ_PERMISSION: Permission = {
  id: 'perm-read',
  name: 'Read Projects',
  description: 'Allows reading projects',
  resource: 'project',
  action: 'read',
};

const WRITE_PERMISSION: Permission = {
  id: 'perm-write',
  name: 'Write Projects',
  description: 'Allows writing projects',
  resource: 'project',
  action: 'write',
};

const ADMIN_ROLE: Role = {
  id: 'role-admin',
  name: 'Admin',
  description: 'Administrator',
  permissions: [READ_PERMISSION, WRITE_PERMISSION],
  isSystem: true,
};

const VIEWER_ROLE: Role = {
  id: 'role-viewer',
  name: 'Viewer',
  description: 'Read-only viewer',
  permissions: [READ_PERMISSION],
  isSystem: false,
};

const BASE_ACL: AccessControlList = {
  resourceId: 'res-1',
  resourceType: 'project',
  ownerId: 'owner-user',
  publicAccess: 'none',
  userPermissions: {},
  rolePermissions: {},
};

// ---------------------------------------------------------------------------
// RBACUtils.authorize()
// ---------------------------------------------------------------------------

describe('RBACUtils.authorize()', () => {
  it('allows access when matching permission exists', () => {
    const ctx: AuthorizationContext = {
      userId: 'u1',
      roles: [VIEWER_ROLE],
      permissions: [READ_PERMISSION],
      resource: 'project',
      action: 'read',
    };
    const decision = RBACUtils.authorize(ctx);
    expect(decision.allowed).toBe(true);
    expect(decision.matchingPermissions).toHaveLength(1);
    expect(decision.matchingPermissions[0]!.id).toBe('perm-read');
  });

  it('denies access when no matching permission exists', () => {
    const ctx: AuthorizationContext = {
      userId: 'u1',
      roles: [VIEWER_ROLE],
      permissions: [READ_PERMISSION],
      resource: 'project',
      action: 'delete',
    };
    const decision = RBACUtils.authorize(ctx);
    expect(decision.allowed).toBe(false);
    expect(decision.matchingPermissions).toHaveLength(0);
  });

  it('returns reason string for both allowed and denied', () => {
    const ctxAllow: AuthorizationContext = {
      userId: 'u',
      roles: [],
      permissions: [WRITE_PERMISSION],
      resource: 'project',
      action: 'write',
    };
    expect(RBACUtils.authorize(ctxAllow).reason).toBeTruthy();

    const ctxDeny: AuthorizationContext = {
      userId: 'u',
      roles: [],
      permissions: [],
      resource: 'project',
      action: 'write',
    };
    expect(RBACUtils.authorize(ctxDeny).reason).toBeTruthy();
  });
});

// ---------------------------------------------------------------------------
// RBACUtils.getPermissionsFromRoles()
// ---------------------------------------------------------------------------

describe('RBACUtils.getPermissionsFromRoles()', () => {
  it('flattens permissions from multiple roles', () => {
    const perms = RBACUtils.getPermissionsFromRoles([ADMIN_ROLE, VIEWER_ROLE]);
    const ids = perms.map(p => p.id);
    expect(ids).toContain('perm-read');
    expect(ids).toContain('perm-write');
  });

  it('deduplicates overlapping permissions', () => {
    // Both roles have READ_PERMISSION — should appear once
    const perms = RBACUtils.getPermissionsFromRoles([ADMIN_ROLE, VIEWER_ROLE]);
    const readPerms = perms.filter(p => p.id === 'perm-read');
    expect(readPerms).toHaveLength(1);
  });

  it('returns empty array for no roles', () => {
    expect(RBACUtils.getPermissionsFromRoles([])).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// RBACUtils.hasRole()
// ---------------------------------------------------------------------------

describe('RBACUtils.hasRole()', () => {
  it('returns true when role is present', () => {
    expect(RBACUtils.hasRole([ADMIN_ROLE, VIEWER_ROLE], 'role-admin')).toBe(true);
  });

  it('returns false when role is absent', () => {
    expect(RBACUtils.hasRole([VIEWER_ROLE], 'role-admin')).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// RBACUtils.hasAnyRole()
// ---------------------------------------------------------------------------

describe('RBACUtils.hasAnyRole()', () => {
  it('returns true when at least one matching role exists', () => {
    expect(RBACUtils.hasAnyRole([VIEWER_ROLE], ['role-admin', 'role-viewer'])).toBe(true);
  });

  it('returns false when none of the roles match', () => {
    expect(RBACUtils.hasAnyRole([VIEWER_ROLE], ['role-admin', 'role-superuser'])).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// RBACUtils.hasAllRoles()
// ---------------------------------------------------------------------------

describe('RBACUtils.hasAllRoles()', () => {
  it('returns true when user has all required roles', () => {
    expect(RBACUtils.hasAllRoles([ADMIN_ROLE, VIEWER_ROLE], ['role-admin', 'role-viewer'])).toBe(true);
  });

  it('returns false when any required role is missing', () => {
    expect(RBACUtils.hasAllRoles([VIEWER_ROLE], ['role-admin', 'role-viewer'])).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// RBACUtils.createRole()
// ---------------------------------------------------------------------------

describe('RBACUtils.createRole()', () => {
  it('creates a role with given name, description, and permissions', () => {
    const role = RBACUtils.createRole('editor', 'Can edit', [WRITE_PERMISSION]);
    expect(role.name).toBe('editor');
    expect(role.description).toBe('Can edit');
    expect(role.permissions).toHaveLength(1);
    expect(role.isSystem).toBe(false);
    expect(role.id).toBeTruthy();
  });

  it('creates a role with unique id', () => {
    const r1 = RBACUtils.createRole('r', 'd', []);
    const r2 = RBACUtils.createRole('r', 'd', []);
    expect(r1.id).not.toBe(r2.id);
  });
});

// ---------------------------------------------------------------------------
// RBACUtils.addPermissionToRole()
// ---------------------------------------------------------------------------

describe('RBACUtils.addPermissionToRole()', () => {
  it('adds a new permission to the role', () => {
    const updated = RBACUtils.addPermissionToRole(VIEWER_ROLE, WRITE_PERMISSION);
    expect(updated.permissions).toHaveLength(2);
    expect(updated.permissions.some(p => p.id === 'perm-write')).toBe(true);
  });

  it('does not duplicate an existing permission', () => {
    const updated = RBACUtils.addPermissionToRole(VIEWER_ROLE, READ_PERMISSION);
    expect(updated.permissions).toHaveLength(1);
  });

  it('returns a new object (immutable)', () => {
    const updated = RBACUtils.addPermissionToRole(VIEWER_ROLE, WRITE_PERMISSION);
    expect(updated).not.toBe(VIEWER_ROLE);
  });
});

// ---------------------------------------------------------------------------
// RBACUtils.removePermissionFromRole()
// ---------------------------------------------------------------------------

describe('RBACUtils.removePermissionFromRole()', () => {
  it('removes the specified permission', () => {
    const updated = RBACUtils.removePermissionFromRole(ADMIN_ROLE, 'perm-write');
    expect(updated.permissions.some(p => p.id === 'perm-write')).toBe(false);
    expect(updated.permissions.some(p => p.id === 'perm-read')).toBe(true);
  });

  it('returns role unchanged when permission not found', () => {
    const updated = RBACUtils.removePermissionFromRole(VIEWER_ROLE, 'nonexistent');
    expect(updated.permissions).toHaveLength(1);
  });
});

// ---------------------------------------------------------------------------
// RBACUtils.checkACLAccess()
// ---------------------------------------------------------------------------

describe('RBACUtils.checkACLAccess()', () => {
  it('grants access to the owner regardless of action', () => {
    expect(RBACUtils.checkACLAccess(BASE_ACL, 'owner-user', 'delete')).toBe(true);
  });

  it('grants access to user with explicit permission in ACL', () => {
    const acl: AccessControlList = {
      ...BASE_ACL,
      userPermissions: { 'user-2': ['read', 'write'] },
    };
    expect(RBACUtils.checkACLAccess(acl, 'user-2', 'write')).toBe(true);
  });

  it('denies access to user without matching permission', () => {
    const acl: AccessControlList = {
      ...BASE_ACL,
      userPermissions: { 'user-2': ['read'] },
    };
    expect(RBACUtils.checkACLAccess(acl, 'user-2', 'delete')).toBe(false);
  });

  it('grants read access with publicAccess="read"', () => {
    const acl: AccessControlList = { ...BASE_ACL, publicAccess: 'read' };
    expect(RBACUtils.checkACLAccess(acl, 'anonymous', 'read')).toBe(true);
  });

  it('denies write access with publicAccess="read"', () => {
    const acl: AccessControlList = { ...BASE_ACL, publicAccess: 'read' };
    expect(RBACUtils.checkACLAccess(acl, 'anonymous', 'write')).toBe(false);
  });

  it('grants all access with publicAccess="write"', () => {
    const acl: AccessControlList = { ...BASE_ACL, publicAccess: 'write' };
    expect(RBACUtils.checkACLAccess(acl, 'anonymous', 'write')).toBe(true);
    expect(RBACUtils.checkACLAccess(acl, 'anonymous', 'read')).toBe(true);
  });

  it('denies access when publicAccess="none" and user unknown', () => {
    expect(RBACUtils.checkACLAccess(BASE_ACL, 'unknown-user', 'read')).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// RBACUtils.grantAccess() / revokeAccess()
// ---------------------------------------------------------------------------

describe('RBACUtils.grantAccess()', () => {
  it('adds action to user permissions', () => {
    const updated = RBACUtils.grantAccess(BASE_ACL, 'user-3', 'read');
    expect(updated.userPermissions['user-3']).toContain('read');
  });

  it('does not duplicate existing permission', () => {
    const acl: AccessControlList = {
      ...BASE_ACL,
      userPermissions: { 'user-3': ['read'] },
    };
    const updated = RBACUtils.grantAccess(acl, 'user-3', 'read');
    expect(updated.userPermissions['user-3']!.filter(a => a === 'read')).toHaveLength(1);
  });

  it('returns a new ACL object (immutable)', () => {
    const updated = RBACUtils.grantAccess(BASE_ACL, 'user-3', 'read');
    expect(updated).not.toBe(BASE_ACL);
  });
});

describe('RBACUtils.revokeAccess()', () => {
  it('removes action from user permissions', () => {
    const acl: AccessControlList = {
      ...BASE_ACL,
      userPermissions: { 'user-4': ['read', 'write'] },
    };
    const updated = RBACUtils.revokeAccess(acl, 'user-4', 'write');
    expect(updated.userPermissions['user-4']).not.toContain('write');
    expect(updated.userPermissions['user-4']).toContain('read');
  });

  it('handles revoke on unknown user gracefully', () => {
    const updated = RBACUtils.revokeAccess(BASE_ACL, 'ghost', 'read');
    expect(updated.userPermissions['ghost']).toHaveLength(0);
  });
});
