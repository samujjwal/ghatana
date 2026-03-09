import type { Meta, StoryObj } from '@storybook/react';
import { RoleInheritanceTree } from '../RoleInheritanceTree';
import { RoleDefinition } from '@/types/role';

const meta: Meta<typeof RoleInheritanceTree> = {
    title: 'Components/RoleInheritanceTree',
    component: RoleInheritanceTree,
    parameters: {
        layout: 'fullscreen',
        docs: {
            description: {
                component: `
                    Interactive visualization of role inheritance hierarchies using React Flow.
                    
                    ## Features
                    - Automatic hierarchical layout
                    - Permission highlighting
                    - Interactive node selection
                    - Export functionality
                    - Dark mode support
                    - Keyboard navigation
                    
                    ## Performance
                    - Optimized for 50+ nodes
                    - Memoized components
                    - Efficient re-renders
                `,
            },
        },
    },
    tags: ['autodocs'],
    argTypes: {
        personaId: {
            control: 'text',
            description: 'ID of the persona to visualize',
        },
        highlightPermission: {
            control: 'text',
            description: 'Permission to highlight in the tree',
        },
        interactive: {
            control: 'boolean',
            description: 'Enable interactive features',
            defaultValue: true,
        },
        onExport: {
            action: 'exported',
            description: 'Callback when export button clicked',
        },
        onNodeClick: {
            action: 'nodeClicked',
            description: 'Callback when node clicked',
        },
    },
};

export default meta;
type Story = StoryObj<typeof RoleInheritanceTree>;

/**
 * Simple role hierarchy with 3 levels
 */
export const Simple: Story = {
    args: {
        personaId: 'simple-persona',
        interactive: true,
    },
    parameters: {
        docs: {
            description: {
                story: 'Basic role hierarchy with Admin → Manager → Employee chain.',
            },
        },
        mockData: {
            roles: [
                {
                    id: 'admin',
                    name: 'Admin',
                    description: 'System administrator',
                    permissions: ['*'],
                    parentRoles: [],
                },
                {
                    id: 'manager',
                    name: 'Manager',
                    description: 'Team manager',
                    permissions: ['read:users', 'write:users', 'read:projects'],
                    parentRoles: ['admin'],
                },
                {
                    id: 'employee',
                    name: 'Employee',
                    description: 'Regular employee',
                    permissions: ['read:projects'],
                    parentRoles: ['manager'],
                },
            ] as RoleDefinition[],
        },
    },
};

/**
 * Complex multi-branch hierarchy
 */
export const Complex: Story = {
    args: {
        personaId: 'complex-persona',
        interactive: true,
    },
    parameters: {
        docs: {
            description: {
                story: 'Multi-branch hierarchy with multiple inheritance paths.',
            },
        },
        mockData: {
            roles: [
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
                    permissions: ['read:users', 'write:users', 'read:projects'],
                    parentRoles: ['admin'],
                },
                {
                    id: 'tech-lead',
                    name: 'Tech Lead',
                    description: 'Technical leader',
                    permissions: ['read:code', 'write:code', 'review:code', 'deploy:*'],
                    parentRoles: ['developer', 'manager'], // Multiple parents!
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
                    permissions: ['read:projects'],
                    parentRoles: ['manager'],
                },
            ] as RoleDefinition[],
        },
    },
};

/**
 * Permission highlighting example
 */
export const WithPermissionHighlight: Story = {
    args: {
        personaId: 'highlight-persona',
        highlightPermission: 'write:code',
        interactive: true,
    },
    parameters: {
        docs: {
            description: {
                story: 'Highlights all roles that have the "write:code" permission.',
            },
        },
        mockData: {
            roles: [
                {
                    id: 'superadmin',
                    name: 'Super Admin',
                    permissions: ['*'],
                    parentRoles: [],
                },
                {
                    id: 'developer',
                    name: 'Developer',
                    permissions: ['read:code', 'write:code', 'deploy:staging'],
                    parentRoles: ['superadmin'],
                },
                {
                    id: 'senior-dev',
                    name: 'Senior Developer',
                    permissions: ['read:code', 'write:code', 'review:code'],
                    parentRoles: ['developer'],
                },
                {
                    id: 'junior-dev',
                    name: 'Junior Developer',
                    permissions: ['read:code', 'write:code'],
                    parentRoles: ['developer'],
                },
                {
                    id: 'viewer',
                    name: 'Viewer',
                    permissions: ['read:code'],
                    parentRoles: ['superadmin'],
                },
            ] as RoleDefinition[],
        },
    },
};

/**
 * Diamond inheritance pattern
 */
export const DiamondInheritance: Story = {
    args: {
        personaId: 'diamond-persona',
        interactive: true,
    },
    parameters: {
        docs: {
            description: {
                story: 'Classic diamond problem: Employee inherits from both Manager and Developer.',
            },
        },
        mockData: {
            roles: [
                {
                    id: 'root',
                    name: 'Root',
                    permissions: ['*'],
                    parentRoles: [],
                },
                {
                    id: 'manager',
                    name: 'Manager',
                    permissions: ['manage:team', 'view:reports'],
                    parentRoles: ['root'],
                },
                {
                    id: 'developer',
                    name: 'Developer',
                    permissions: ['write:code', 'deploy:app'],
                    parentRoles: ['root'],
                },
                {
                    id: 'tech-manager',
                    name: 'Tech Manager',
                    permissions: ['manage:team', 'write:code', 'view:reports', 'deploy:app'],
                    parentRoles: ['manager', 'developer'], // Diamond!
                },
            ] as RoleDefinition[],
        },
    },
};

/**
 * Large hierarchy (50+ nodes)
 */
export const LargeHierarchy: Story = {
    args: {
        personaId: 'large-persona',
        interactive: true,
    },
    parameters: {
        docs: {
            description: {
                story: 'Performance test with 50+ roles across 5 levels.',
            },
        },
        mockData: {
            roles: generateLargeHierarchy(),
        },
    },
};

/**
 * With export functionality
 */
export const WithExport: Story = {
    args: {
        personaId: 'export-persona',
        interactive: true,
        onExport: (data) => {
            console.log('Export data:', data);
            alert(`Exported ${data.nodes.length} nodes and ${data.edges.length} edges`);
        },
    },
    parameters: {
        docs: {
            description: {
                story: 'Click the export button to download hierarchy data.',
            },
        },
        mockData: {
            roles: [
                {
                    id: 'admin',
                    name: 'Admin',
                    permissions: ['*'],
                    parentRoles: [],
                },
                {
                    id: 'manager',
                    name: 'Manager',
                    permissions: ['manage:team'],
                    parentRoles: ['admin'],
                },
                {
                    id: 'employee',
                    name: 'Employee',
                    permissions: ['read:docs'],
                    parentRoles: ['manager'],
                },
            ] as RoleDefinition[],
        },
    },
};

/**
 * Non-interactive (read-only)
 */
export const ReadOnly: Story = {
    args: {
        personaId: 'readonly-persona',
        interactive: false, // Disable interactions
    },
    parameters: {
        docs: {
            description: {
                story: 'Read-only view with interactions disabled.',
            },
        },
        mockData: {
            roles: [
                {
                    id: 'admin',
                    name: 'Admin',
                    permissions: ['*'],
                    parentRoles: [],
                },
                {
                    id: 'manager',
                    name: 'Manager',
                    permissions: ['manage:team'],
                    parentRoles: ['admin'],
                },
                {
                    id: 'employee',
                    name: 'Employee',
                    permissions: ['read:docs'],
                    parentRoles: ['manager'],
                },
            ] as RoleDefinition[],
        },
    },
};

/**
 * Dark mode example
 */
export const DarkMode: Story = {
    args: {
        personaId: 'dark-persona',
        interactive: true,
    },
    parameters: {
        backgrounds: {
            default: 'dark',
        },
        docs: {
            description: {
                story: 'Automatic dark mode support based on system theme.',
            },
        },
        mockData: {
            roles: [
                {
                    id: 'admin',
                    name: 'Admin',
                    permissions: ['*'],
                    parentRoles: [],
                },
                {
                    id: 'manager',
                    name: 'Manager',
                    permissions: ['manage:team'],
                    parentRoles: ['admin'],
                },
                {
                    id: 'employee',
                    name: 'Employee',
                    permissions: ['read:docs'],
                    parentRoles: ['manager'],
                },
            ] as RoleDefinition[],
        },
    },
};

/**
 * Empty state (no roles)
 */
export const Empty: Story = {
    args: {
        personaId: 'empty-persona',
        interactive: true,
    },
    parameters: {
        docs: {
            description: {
                story: 'Gracefully handles empty role hierarchies.',
            },
        },
        mockData: {
            roles: [] as RoleDefinition[],
        },
    },
};

/**
 * Loading state
 */
export const Loading: Story = {
    args: {
        personaId: 'loading-persona',
        interactive: true,
    },
    parameters: {
        docs: {
            description: {
                story: 'Shows loading spinner while fetching role data.',
            },
        },
        mockData: {
            isLoading: true,
        },
    },
};

/**
 * Error state
 */
export const Error: Story = {
    args: {
        personaId: 'error-persona',
        interactive: true,
    },
    parameters: {
        docs: {
            description: {
                story: 'Displays error message when role data fails to load.',
            },
        },
        mockData: {
            isError: true,
            error: new Error('Failed to fetch role hierarchy'),
        },
    },
};

// Helper function to generate large hierarchy
function generateLargeHierarchy(): RoleDefinition[] {
    const roles: RoleDefinition[] = [];

    // Root
    roles.push({
        id: 'root',
        name: 'Root',
        description: 'Root role',
        permissions: ['*'],
        parentRoles: [],
    });

    // Level 1: 5 top-level roles
    for (let i = 1; i <= 5; i++) {
        roles.push({
            id: `level1-${i}`,
            name: `Level 1 Role ${i}`,
            description: `Top-level role ${i}`,
            permissions: [`level1:${i}`],
            parentRoles: ['root'],
        });
    }

    // Level 2: 15 second-level roles (3 per parent)
    for (let i = 1; i <= 5; i++) {
        for (let j = 1; j <= 3; j++) {
            roles.push({
                id: `level2-${i}-${j}`,
                name: `Level 2 Role ${i}.${j}`,
                description: `Second-level role ${i}.${j}`,
                permissions: [`level2:${i}:${j}`],
                parentRoles: [`level1-${i}`],
            });
        }
    }

    // Level 3: 30 third-level roles (2 per parent)
    for (let i = 1; i <= 5; i++) {
        for (let j = 1; j <= 3; j++) {
            for (let k = 1; k <= 2; k++) {
                roles.push({
                    id: `level3-${i}-${j}-${k}`,
                    name: `Level 3 Role ${i}.${j}.${k}`,
                    description: `Third-level role ${i}.${j}.${k}`,
                    permissions: [`level3:${i}:${j}:${k}`],
                    parentRoles: [`level2-${i}-${j}`],
                });
            }
        }
    }

    return roles; // Total: 1 + 5 + 15 + 30 = 51 roles
}
