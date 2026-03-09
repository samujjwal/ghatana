/**
 * Mock implementation of PersonaRoleDomainClient for development.
 *
 * Purpose:
 * Provides realistic role definitions and validation without requiring
 * Java HTTP server. Mirrors Java domain logic exactly.
 *
 * When to Use:
 * - Development: Set JAVA_API_MOCK=true in .env
 * - Testing: Always use mock for unit/integration tests
 * - Production: Set JAVA_API_MOCK=false to call real Java service
 *
 * Implementation Notes:
 * - Role definitions match PersonaRoleDefinition.java exactly
 * - Validation rules match PersonaRoleService.java exactly
 * - Permission resolution matches Java logic
 * - Synchronous (no network calls), fast for tests
 */

import {
    RoleDefinition,
    ValidationResult,
    EffectivePermissions,
    ValidationError,
} from './persona-role-domain.client.js';

export class MockPersonaRoleService {
    private roles: Map<string, RoleDefinition> = new Map();

    constructor() {
        this.registerDefaultRoles();
    }

    /**
     * Register all 14 default roles matching Java implementation
     */
    private registerDefaultRoles(): void {
        // Base roles (4)
        this.roles.set('admin', {
            roleId: 'admin',
            displayName: 'Administrator',
            description: 'Full system access with all permissions',
            type: 'BASE',
            permissions: [
                'workspace.manage',
                'team.manage',
                'project.manage',
                'code.approve',
                'deployment.production',
                'analytics.full',
                'settings.modify',
                'user.manage',
            ],
            capabilities: [
                'viewAllProjects',
                'approveCodeReviews',
                'deployProduction',
                'manageTeam',
                'viewAnalytics',
            ],
            parentRoles: [],
        });

        this.roles.set('tech-lead', {
            roleId: 'tech-lead',
            displayName: 'Tech Lead',
            description: 'Technical leadership with code approval and architecture decisions',
            type: 'BASE',
            permissions: [
                'code.approve',
                'architecture.review',
                'deployment.staging',
                'analytics.team',
                'project.plan',
            ],
            capabilities: [
                'viewTeamProjects',
                'approveCodeReviews',
                'reviewArchitecture',
                'deployStaging',
                'viewTeamAnalytics',
            ],
            parentRoles: [],
        });

        this.roles.set('developer', {
            roleId: 'developer',
            displayName: 'Developer',
            description: 'Standard development access with code submission',
            type: 'BASE',
            permissions: ['code.write', 'code.review', 'deployment.dev', 'project.view'],
            capabilities: ['viewAssignedProjects', 'submitCode', 'reviewCode', 'runTests'],
            parentRoles: [],
        });

        this.roles.set('viewer', {
            roleId: 'viewer',
            displayName: 'Viewer',
            description: 'Read-only access to project information',
            type: 'BASE',
            permissions: ['project.view', 'analytics.basic'],
            capabilities: ['viewPublicProjects', 'viewBasicAnalytics'],
            parentRoles: [],
        });

        // Specialized roles (10)
        this.roles.set('fullstack-developer', {
            roleId: 'fullstack-developer',
            displayName: 'Full-Stack Developer',
            description: 'Full-stack development with frontend and backend expertise',
            type: 'SPECIALIZED',
            permissions: ['code.write', 'code.review', 'deployment.dev', 'project.view'],
            capabilities: [
                'viewAssignedProjects',
                'submitCode',
                'reviewCode',
                'runTests',
                'debugProduction',
            ],
            parentRoles: ['developer'],
        });

        this.roles.set('backend-developer', {
            roleId: 'backend-developer',
            displayName: 'Backend Developer',
            description: 'Backend services, APIs, and database development',
            type: 'SPECIALIZED',
            permissions: ['code.write', 'code.review', 'database.read', 'api.test'],
            capabilities: ['viewAssignedProjects', 'submitCode', 'reviewCode', 'queryDatabase'],
            parentRoles: ['developer'],
        });

        this.roles.set('frontend-developer', {
            roleId: 'frontend-developer',
            displayName: 'Frontend Developer',
            description: 'UI/UX implementation and frontend architecture',
            type: 'SPECIALIZED',
            permissions: ['code.write', 'code.review', 'design.view', 'analytics.user'],
            capabilities: ['viewAssignedProjects', 'submitCode', 'reviewCode', 'viewDesigns'],
            parentRoles: ['developer'],
        });

        this.roles.set('devops-engineer', {
            roleId: 'devops-engineer',
            displayName: 'DevOps Engineer',
            description: 'Infrastructure, deployment, and monitoring',
            type: 'SPECIALIZED',
            permissions: [
                'deployment.staging',
                'deployment.production',
                'monitoring.full',
                'infrastructure.manage',
            ],
            capabilities: [
                'deployStaging',
                'deployProduction',
                'manageInfrastructure',
                'viewLogs',
            ],
            parentRoles: ['developer'],
        });

        this.roles.set('qa-engineer', {
            roleId: 'qa-engineer',
            displayName: 'QA Engineer',
            description: 'Quality assurance, testing, and bug tracking',
            type: 'SPECIALIZED',
            permissions: ['test.execute', 'bug.report', 'test.plan', 'project.view'],
            capabilities: [
                'viewAssignedProjects',
                'executTests',
                'reportBugs',
                'createTestPlans',
            ],
            parentRoles: ['developer'],
        });

        this.roles.set('product-manager', {
            roleId: 'product-manager',
            displayName: 'Product Manager',
            description: 'Product strategy, roadmap, and requirements',
            type: 'SPECIALIZED',
            permissions: [
                'product.plan',
                'requirements.define',
                'analytics.product',
                'project.view',
            ],
            capabilities: [
                'viewAllProjects',
                'defineRequirements',
                'viewProductAnalytics',
                'manageProdBacklog',
            ],
            parentRoles: ['viewer'],
        });

        this.roles.set('designer', {
            roleId: 'designer',
            displayName: 'UX/UI Designer',
            description: 'User experience and interface design',
            type: 'SPECIALIZED',
            permissions: ['design.create', 'design.review', 'prototype.create', 'user.research'],
            capabilities: [
                'viewAssignedProjects',
                'createDesigns',
                'createPrototypes',
                'conductResearch',
            ],
            parentRoles: ['viewer'],
        });

        this.roles.set('data-analyst', {
            roleId: 'data-analyst',
            displayName: 'Data Analyst',
            description: 'Data analysis, reporting, and insights',
            type: 'SPECIALIZED',
            permissions: [
                'analytics.full',
                'database.read',
                'report.create',
                'dashboard.create',
            ],
            capabilities: [
                'viewAllProjects',
                'queryDatabase',
                'createReports',
                'createDashboards',
            ],
            parentRoles: ['viewer'],
        });

        this.roles.set('security-engineer', {
            roleId: 'security-engineer',
            displayName: 'Security Engineer',
            description: 'Security audits, vulnerability management, compliance',
            type: 'SPECIALIZED',
            permissions: [
                'security.audit',
                'vulnerability.scan',
                'compliance.check',
                'code.review',
            ],
            capabilities: [
                'viewAllProjects',
                'auditSecurity',
                'scanVulnerabilities',
                'reviewSecurityCode',
            ],
            parentRoles: ['developer'],
        });

        this.roles.set('architect', {
            roleId: 'architect',
            displayName: 'Software Architect',
            description: 'System architecture, design patterns, technical decisions',
            type: 'SPECIALIZED',
            permissions: [
                'architecture.review',
                'architecture.design',
                'code.approve',
                'project.view',
            ],
            capabilities: [
                'viewAllProjects',
                'reviewArchitecture',
                'designArchitecture',
                'approveCodeReviews',
            ],
            parentRoles: ['tech-lead'],
        });
    }

    /**
     * Get all available role definitions
     */
    async getAllRoles(): Promise<RoleDefinition[]> {
        return Array.from(this.roles.values());
    }

    /**
     * Get specific role definition by ID
     */
    async getRoleDefinition(roleId: string): Promise<RoleDefinition | null> {
        return this.roles.get(roleId) || null;
    }

    /**
     * Validate role activation combination
     *
     * Business rules (matches Java):
     * - At least 1 role required
     * - Maximum 5 roles allowed
     * - Admin + Viewer incompatible
     * - All roles must exist
     */
    async validateRoleActivation(roleIds: string[]): Promise<ValidationResult> {
        // Rule 1: At least one role
        if (!roleIds || roleIds.length === 0) {
            return {
                isValid: false,
                errorMessage: 'At least one role must be activated',
            };
        }

        // Rule 2: Maximum 5 roles
        if (roleIds.length > 5) {
            return {
                isValid: false,
                errorMessage: 'Maximum 5 roles can be activated',
            };
        }

        // Rule 3: All roles must exist
        for (const roleId of roleIds) {
            if (!this.roles.has(roleId)) {
                return {
                    isValid: false,
                    errorMessage: `Unknown role: ${roleId}`,
                };
            }
        }

        // Rule 4: Admin + Viewer incompatible
        if (roleIds.includes('admin') && roleIds.includes('viewer')) {
            return {
                isValid: false,
                errorMessage: 'Admin and Viewer roles are incompatible',
            };
        }

        return { isValid: true };
    }

    /**
     * Resolve effective permissions from active roles
     *
     * Logic (matches Java):
     * - Union of all permissions from all roles
     * - Include inherited permissions from parent roles
     */
    async resolveEffectivePermissions(
        roleIds: string[]
    ): Promise<EffectivePermissions> {
        const permissions: Record<string, boolean> = {};
        const capabilities: Record<string, boolean> = {};

        for (const roleId of roleIds) {
            const role = this.roles.get(roleId);
            if (!role) continue;

            // Add direct permissions and capabilities
            role.permissions.forEach((perm) => (permissions[perm] = true));
            role.capabilities.forEach((cap) => (capabilities[cap] = true));

            // Add inherited permissions from parent roles
            for (const parentId of role.parentRoles) {
                const parent = this.roles.get(parentId);
                if (parent) {
                    parent.permissions.forEach((perm) => (permissions[perm] = true));
                    parent.capabilities.forEach((cap) => (capabilities[cap] = true));
                }
            }
        }

        return { permissions, capabilities };
    }

    /**
     * Check if user has specific permission
     */
    async hasPermission(roleIds: string[], permission: string): Promise<boolean> {
        const permissions = await this.resolveEffectivePermissions(roleIds);
        return permissions.permissions[permission] === true;
    }

    /**
     * Check if user has specific capability
     */
    async hasCapability(roleIds: string[], capability: string): Promise<boolean> {
        const permissions = await this.resolveEffectivePermissions(roleIds);
        return permissions.capabilities[capability] === true;
    }
}
