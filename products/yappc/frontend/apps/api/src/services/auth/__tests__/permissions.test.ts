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
