import { RoleDefinition } from '@/types/role';

/**
 * Mock role hierarchies for demos and testing
 */

export const simpleHierarchy: RoleDefinition[] = [
    {
        id: 'admin',
        name: 'Admin',
        description: 'System administrator with full access',
        permissions: ['*'],
        parentRoles: [],
    },
    {
        id: 'manager',
        name: 'Manager',
        description: 'Team manager with user and project management',
        permissions: ['read:users', 'write:users', 'read:projects', 'write:projects'],
        parentRoles: ['admin'],
    },
    {
        id: 'employee',
        name: 'Employee',
        description: 'Regular employee with basic access',
        permissions: ['read:projects', 'read:docs'],
        parentRoles: ['manager'],
    },
];

export const complexHierarchy: RoleDefinition[] = [
    {
        id: 'superadmin',
        name: 'Super Admin',
        description: 'Root administrator',
        permissions: ['*'],
        parentRoles: [],
    },
    {
        id: 'admin',
        name: 'Admin',
        description: 'Administrator',
        permissions: ['admin:*', 'users:*', 'projects:*'],
        parentRoles: ['superadmin'],
    },
    {
        id: 'developer',
        name: 'Developer',
        description: 'Software developer',
        permissions: ['read:code', 'write:code', 'deploy:staging'],
        parentRoles: ['superadmin'],
    },
    {
        id: 'manager',
        name: 'Manager',
        description: 'Team manager',
        permissions: ['read:users', 'write:users', 'read:projects', 'write:projects'],
        parentRoles: ['admin'],
    },
    {
        id: 'tech-lead',
        name: 'Tech Lead',
        description: 'Technical leader',
        permissions: ['read:code', 'write:code', 'review:code', 'deploy:*'],
        parentRoles: ['developer', 'manager'],
    },
    {
        id: 'senior-dev',
        name: 'Senior Developer',
        description: 'Experienced developer',
        permissions: ['read:code', 'write:code', 'review:code'],
        parentRoles: ['developer'],
    },
    {
        id: 'junior-dev',
        name: 'Junior Developer',
        description: 'Entry-level developer',
        permissions: ['read:code', 'write:code'],
        parentRoles: ['developer'],
    },
    {
        id: 'employee',
        name: 'Employee',
        description: 'Regular employee',
        permissions: ['read:projects', 'read:docs'],
        parentRoles: ['manager'],
    },
];

export const diamondHierarchy: RoleDefinition[] = [
    {
        id: 'root',
        name: 'Root',
        description: 'Root role',
        permissions: ['*'],
        parentRoles: [],
    },
    {
        id: 'manager',
        name: 'Manager',
        description: 'Management role',
        permissions: ['manage:team', 'view:reports'],
        parentRoles: ['root'],
    },
    {
        id: 'developer',
        name: 'Developer',
        description: 'Development role',
        permissions: ['write:code', 'deploy:app'],
        parentRoles: ['root'],
    },
    {
        id: 'tech-manager',
        name: 'Tech Manager',
        description: 'Technical manager',
        permissions: ['manage:team', 'write:code', 'view:reports', 'deploy:app'],
        parentRoles: ['manager', 'developer'],
    },
];

export function generateLargeHierarchy(levels: number = 4, branchFactor: number = 3): RoleDefinition[] {
    const roles: RoleDefinition[] = [];

    // Root
    roles.push({
        id: 'root',
        name: 'Root',
        description: 'Root role',
        permissions: ['*'],
        parentRoles: [],
    });

    // Generate levels
    for (let level = 1; level <= levels; level++) {
        const parentLevel = level - 1;
        const parentsAtLevel = parentLevel === 0 ? 1 : Math.pow(branchFactor, parentLevel);

        for (let parentIdx = 0; parentIdx < parentsAtLevel; parentIdx++) {
            const parentId = parentLevel === 0 ? 'root' : `level${parentLevel}-${parentIdx}`;

            for (let childIdx = 0; childIdx < branchFactor; childIdx++) {
                const roleId = `level${level}-${parentIdx * branchFactor + childIdx}`;
                roles.push({
                    id: roleId,
                    name: `Level ${level} Role ${parentIdx * branchFactor + childIdx}`,
                    description: `Role at level ${level}`,
                    permissions: [`level${level}:${parentIdx}:${childIdx}`],
                    parentRoles: [parentId],
                });
            }
        }
    }

    return roles;
}

export const allMockHierarchies = {
    simple: simpleHierarchy,
    complex: complexHierarchy,
    diamond: diamondHierarchy,
    large: generateLargeHierarchy(),
};
