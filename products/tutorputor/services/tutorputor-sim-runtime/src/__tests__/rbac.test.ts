/**
 * Simulation RBAC (Role-Based Access Control) Tests
 *
 * @doc.type test
 * @doc.purpose Verify RBAC enforcement on simulation templates and runs
 * @doc.layer product
 * @doc.pattern IntegrationTest
 *
 * Tests RBAC policies for:
 * - Template creation/editing (teacher/admin only)
 * - Template viewing (all roles with tenant isolation)
 * - Simulation run creation (all authenticated)
 * - Run viewing (owner only + tenant isolation)
 * - Admin override capabilities
 */

import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import type { SimulationManifest, SimulationId } from '@ghatana/tutorputor-contracts/v1/simulation/types';
import type { TenantId, UserId, UserRole } from '@ghatana/tutorputor-contracts/v1/types';
// =============================================================================
// Types & Interfaces
// =============================================================================

interface User {
    id: UserId;
    role: UserRole;
    tenantId: TenantId;
}

interface SimulationTemplate {
    id: string;
    manifestId: string;
    tenantId: TenantId;
    createdBy: UserId;
    visibility: 'private' | 'tenant' | 'public';
    allowedRoles: UserRole[];
    manifest: SimulationManifest;
}

interface SimulationRun {
    id: string;
    templateId: string;
    tenantId: TenantId;
    userId: UserId;
    startedAt: Date;
    completedAt?: Date;
    status: 'active' | 'completed' | 'abandoned';
}

interface AuditLogEntry {
    id: string;
    timestamp: Date;
    action: string;
    resource: string;
    resourceId: string;
    userId: UserId;
    tenantId: TenantId;
    outcome: 'allowed' | 'denied';
    reason?: string;
    metadata?: Record<string, unknown>;
}

// =============================================================================
// RBAC Service Implementation (Mock for Testing)
// =============================================================================

class SimulationRBACService {
    private templates: Map<string, SimulationTemplate> = new Map();
    private runs: Map<string, SimulationRun> = new Map();
    private auditLog: AuditLogEntry[] = [];

    // -------------------------------------------------------------------------
    // Template Operations
    // -------------------------------------------------------------------------

    canCreateTemplate(user: User): boolean {
        const allowed = ['teacher', 'admin', 'author'].includes(user.role);
        this.logAccess({
            action: 'CREATE_TEMPLATE',
            resource: 'simulation_template',
            resourceId: 'new',
            user,
            outcome: allowed ? 'allowed' : 'denied',
            reason: allowed ? undefined : `Role ${user.role} cannot create templates`,
        });
        return allowed;
    }

    canEditTemplate(user: User, template: SimulationTemplate): boolean {
        // Must be same tenant
        if (user.tenantId !== template.tenantId) {
            this.logAccess({
                action: 'EDIT_TEMPLATE',
                resource: 'simulation_template',
                resourceId: template.id,
                user,
                outcome: 'denied',
                reason: 'Cross-tenant access denied',
            });
            return false;
        }

        // Admin can edit any template in tenant
        if (user.role === 'admin') {
            this.logAccess({
                action: 'EDIT_TEMPLATE',
                resource: 'simulation_template',
                resourceId: template.id,
                user,
                outcome: 'allowed',
                reason: 'Admin override',
            });
            return true;
        }

        // Creator can edit their own templates
        if (user.id === template.createdBy) {
            const allowed = ['teacher', 'admin', 'author'].includes(user.role);
            this.logAccess({
                action: 'EDIT_TEMPLATE',
                resource: 'simulation_template',
                resourceId: template.id,
                user,
                outcome: allowed ? 'allowed' : 'denied',
                reason: allowed ? 'Owner access' : `Role ${user.role} cannot edit templates`,
            });
            return allowed;
        }

        this.logAccess({
            action: 'EDIT_TEMPLATE',
            resource: 'simulation_template',
            resourceId: template.id,
            user,
            outcome: 'denied',
            reason: 'Not owner or admin',
        });
        return false;
    }

    canViewTemplate(user: User, template: SimulationTemplate): boolean {
        // Public templates are viewable by all
        if (template.visibility === 'public') {
            this.logAccess({
                action: 'VIEW_TEMPLATE',
                resource: 'simulation_template',
                resourceId: template.id,
                user,
                outcome: 'allowed',
                reason: 'Public template',
            });
            return true;
        }

        // Tenant templates require same tenant
        if (template.visibility === 'tenant') {
            const allowed = user.tenantId === template.tenantId;
            this.logAccess({
                action: 'VIEW_TEMPLATE',
                resource: 'simulation_template',
                resourceId: template.id,
                user,
                outcome: allowed ? 'allowed' : 'denied',
                reason: allowed ? 'Tenant match' : 'Cross-tenant access denied',
            });
            return allowed;
        }

        // Private templates: owner or admin only
        if (template.visibility === 'private') {
            const allowed =
                user.id === template.createdBy ||
                (user.role === 'admin' && user.tenantId === template.tenantId);
            this.logAccess({
                action: 'VIEW_TEMPLATE',
                resource: 'simulation_template',
                resourceId: template.id,
                user,
                outcome: allowed ? 'allowed' : 'denied',
                reason: allowed ? 'Owner or admin access' : 'Private template access denied',
            });
            return allowed;
        }

        // Check role-based access
        const roleAllowed = template.allowedRoles.includes(user.role);
        const tenantMatch = user.tenantId === template.tenantId;
        const allowed = roleAllowed && tenantMatch;

        this.logAccess({
            action: 'VIEW_TEMPLATE',
            resource: 'simulation_template',
            resourceId: template.id,
            user,
            outcome: allowed ? 'allowed' : 'denied',
            reason: allowed ? 'Role and tenant match' : 'Role or tenant mismatch',
        });
        return allowed;
    }

    canDeleteTemplate(user: User, template: SimulationTemplate): boolean {
        // Must be same tenant
        if (user.tenantId !== template.tenantId) {
            this.logAccess({
                action: 'DELETE_TEMPLATE',
                resource: 'simulation_template',
                resourceId: template.id,
                user,
                outcome: 'denied',
                reason: 'Cross-tenant access denied',
            });
            return false;
        }

        // Only admin or owner can delete
        const allowed = user.role === 'admin' || user.id === template.createdBy;
        this.logAccess({
            action: 'DELETE_TEMPLATE',
            resource: 'simulation_template',
            resourceId: template.id,
            user,
            outcome: allowed ? 'allowed' : 'denied',
            reason: allowed ? 'Owner or admin' : 'Not authorized to delete',
        });
        return allowed;
    }

    // -------------------------------------------------------------------------
    // Run Operations
    // -------------------------------------------------------------------------

    canCreateRun(user: User, template: SimulationTemplate): boolean {
        // Must be able to view template first
        if (!this.canViewTemplate(user, template)) {
            this.logAccess({
                action: 'CREATE_RUN',
                resource: 'simulation_run',
                resourceId: 'new',
                user,
                outcome: 'denied',
                reason: 'Cannot view template',
            });
            return false;
        }

        // Any authenticated user in tenant can create runs
        const allowed = user.tenantId === template.tenantId;
        this.logAccess({
            action: 'CREATE_RUN',
            resource: 'simulation_run',
            resourceId: 'new',
            user,
            outcome: allowed ? 'allowed' : 'denied',
            reason: allowed ? 'Tenant match' : 'Cross-tenant run creation denied',
        });
        return allowed;
    }

    canViewRun(user: User, run: SimulationRun): boolean {
        // Owner can always view their runs
        if (user.id === run.userId) {
            this.logAccess({
                action: 'VIEW_RUN',
                resource: 'simulation_run',
                resourceId: run.id,
                user,
                outcome: 'allowed',
                reason: 'Owner access',
            });
            return true;
        }

        // Admin and teacher can view runs in their tenant
        if (user.tenantId === run.tenantId && ['admin', 'teacher'].includes(user.role)) {
            this.logAccess({
                action: 'VIEW_RUN',
                resource: 'simulation_run',
                resourceId: run.id,
                user,
                outcome: 'allowed',
                reason: 'Teacher/Admin tenant access',
            });
            return true;
        }

        // Cross-tenant access denied
        if (user.tenantId !== run.tenantId) {
            this.logAccess({
                action: 'VIEW_RUN',
                resource: 'simulation_run',
                resourceId: run.id,
                user,
                outcome: 'denied',
                reason: 'Cross-tenant access denied',
            });
            return false;
        }

        this.logAccess({
            action: 'VIEW_RUN',
            resource: 'simulation_run',
            resourceId: run.id,
            user,
            outcome: 'denied',
            reason: 'Not owner or privileged role',
        });
        return false;
    }

    canDeleteRun(user: User, run: SimulationRun): boolean {
        // Must be same tenant
        if (user.tenantId !== run.tenantId) {
            this.logAccess({
                action: 'DELETE_RUN',
                resource: 'simulation_run',
                resourceId: run.id,
                user,
                outcome: 'denied',
                reason: 'Cross-tenant access denied',
            });
            return false;
        }

        // Only owner or admin can delete
        const allowed = user.id === run.userId || user.role === 'admin';
        this.logAccess({
            action: 'DELETE_RUN',
            resource: 'simulation_run',
            resourceId: run.id,
            user,
            outcome: allowed ? 'allowed' : 'denied',
            reason: allowed ? 'Owner or admin' : 'Not authorized to delete',
        });
        return allowed;
    }

    // -------------------------------------------------------------------------
    // Audit Log
    // -------------------------------------------------------------------------

    private logAccess(entry: {
        action: string;
        resource: string;
        resourceId: string;
        user: User;
        outcome: 'allowed' | 'denied';
        reason?: string;
        metadata?: Record<string, unknown>;
    }): void {
        this.auditLog.push({
            id: `audit-${Date.now()}-${Math.random().toString(36).slice(2)}`,
            timestamp: new Date(),
            action: entry.action,
            resource: entry.resource,
            resourceId: entry.resourceId,
            userId: entry.user.id,
            tenantId: entry.user.tenantId,
            outcome: entry.outcome,
            reason: entry.reason,
            metadata: entry.metadata,
        });
    }

    getAuditLog(): AuditLogEntry[] {
        return [...this.auditLog];
    }

    getAuditLogForUser(userId: UserId): AuditLogEntry[] {
        return this.auditLog.filter((e) => e.userId === userId);
    }

    getAuditLogForResource(resource: string, resourceId: string): AuditLogEntry[] {
        return this.auditLog.filter((e) => e.resource === resource && e.resourceId === resourceId);
    }

    getDeniedAccessAttempts(tenantId: TenantId): AuditLogEntry[] {
        return this.auditLog.filter((e) => e.tenantId === tenantId && e.outcome === 'denied');
    }

    clearAuditLog(): void {
        this.auditLog = [];
    }
}

// =============================================================================
// Test Fixtures
// =============================================================================

function createTestUser(overrides: Partial<User> = {}): User {
    return {
        id: 'user-001' as UserId,
        role: 'student',
        tenantId: 'tenant-001' as TenantId,
        ...overrides,
    };
}

function createTestTemplate(overrides: Partial<SimulationTemplate> = {}): SimulationTemplate {
    return {
        id: 'template-001',
        manifestId: 'manifest-001',
        tenantId: 'tenant-001' as TenantId,
        createdBy: 'user-teacher-001' as UserId,
        visibility: 'tenant',
        allowedRoles: ['student', 'teacher', 'admin'],
        manifest: {
            id: 'manifest-001' as any,
            version: '1.0',
            domain: 'CS_DISCRETE',
            title: 'Test Simulation',
            description: 'Test simulation for RBAC testing',
            authorId: 'user-teacher-001' as UserId,
            tenantId: 'tenant-001' as TenantId,
            canvas: { width: 800, height: 600 },
            playback: { defaultSpeed: 1, autoPlay: false },
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            schemaVersion: '1.0',
            initialEntities: [],
            steps: [],
            domainMetadata: { domain: 'CS_DISCRETE' as any },
        } as SimulationManifest,
        ...overrides,
    };
}

function createTestRun(overrides: Partial<SimulationRun> = {}): SimulationRun {
    return {
        id: 'run-001',
        templateId: 'template-001',
        tenantId: 'tenant-001' as TenantId,
        userId: 'user-student-001' as UserId,
        startedAt: new Date(),
        status: 'active',
        ...overrides,
    };
}

// =============================================================================
// Tests
// =============================================================================

describe('SimulationRBACService', () => {
    let rbac: SimulationRBACService;

    beforeEach(() => {
        rbac = new SimulationRBACService();
    });

    afterEach(() => {
        rbac.clearAuditLog();
    });

    // ---------------------------------------------------------------------------
    // Template Creation Tests
    // ---------------------------------------------------------------------------

    describe('Template Creation', () => {
        it('should allow teachers to create templates', () => {
            const teacher = createTestUser({ role: 'teacher', id: 'teacher-001' as UserId });
            expect(rbac.canCreateTemplate(teacher)).toBe(true);
        });

        it('should allow admins to create templates', () => {
            const admin = createTestUser({ role: 'admin', id: 'admin-001' as UserId });
            expect(rbac.canCreateTemplate(admin)).toBe(true);
        });

        it('should allow authors to create templates', () => {
            const author = createTestUser({ role: 'author' as UserRole, id: 'author-001' as UserId });
            expect(rbac.canCreateTemplate(author)).toBe(true);
        });

        it('should deny students from creating templates', () => {
            const student = createTestUser({ role: 'student', id: 'student-001' as UserId });
            expect(rbac.canCreateTemplate(student)).toBe(false);
        });

        it('should deny parents from creating templates', () => {
            const parent = createTestUser({ role: 'parent' as UserRole, id: 'parent-001' as UserId });
            expect(rbac.canCreateTemplate(parent)).toBe(false);
        });

        it('should log denied template creation attempts', () => {
            const student = createTestUser({ role: 'student', id: 'student-001' as UserId });
            rbac.canCreateTemplate(student);

            const logs = rbac.getAuditLogForUser('student-001' as UserId);
            expect(logs.length).toBe(1);
            expect(logs[0].action).toBe('CREATE_TEMPLATE');
            expect(logs[0].outcome).toBe('denied');
            expect(logs[0].reason).toContain('student');
        });
    });

    // ---------------------------------------------------------------------------
    // Template Editing Tests
    // ---------------------------------------------------------------------------

    describe('Template Editing', () => {
        it('should allow template owner to edit', () => {
            const teacher = createTestUser({ role: 'teacher', id: 'user-teacher-001' as UserId });
            const template = createTestTemplate({ createdBy: 'user-teacher-001' as UserId });

            expect(rbac.canEditTemplate(teacher, template)).toBe(true);
        });

        it('should allow admin to edit any template in tenant', () => {
            const admin = createTestUser({ role: 'admin', id: 'admin-001' as UserId });
            const template = createTestTemplate({ createdBy: 'other-teacher' as UserId });

            expect(rbac.canEditTemplate(admin, template)).toBe(true);
        });

        it('should deny non-owner teacher from editing', () => {
            const teacher = createTestUser({ role: 'teacher', id: 'other-teacher' as UserId });
            const template = createTestTemplate({ createdBy: 'user-teacher-001' as UserId });

            expect(rbac.canEditTemplate(teacher, template)).toBe(false);
        });

        it('should deny student from editing template', () => {
            const student = createTestUser({ role: 'student', id: 'student-001' as UserId });
            const template = createTestTemplate({ createdBy: 'student-001' as UserId }); // Even if they somehow created it

            expect(rbac.canEditTemplate(student, template)).toBe(false);
        });

        it('should deny cross-tenant template editing', () => {
            const admin = createTestUser({
                role: 'admin',
                id: 'admin-001' as UserId,
                tenantId: 'tenant-002' as TenantId,
            });
            const template = createTestTemplate({ tenantId: 'tenant-001' as TenantId });

            expect(rbac.canEditTemplate(admin, template)).toBe(false);
        });

        it('should log cross-tenant edit attempts with reason', () => {
            const admin = createTestUser({
                role: 'admin',
                id: 'admin-001' as UserId,
                tenantId: 'tenant-002' as TenantId,
            });
            const template = createTestTemplate({ tenantId: 'tenant-001' as TenantId });

            rbac.canEditTemplate(admin, template);

            const logs = rbac.getAuditLogForResource('simulation_template', template.id);
            expect(logs.length).toBe(1);
            expect(logs[0].outcome).toBe('denied');
            expect(logs[0].reason).toBe('Cross-tenant access denied');
        });
    });

    // ---------------------------------------------------------------------------
    // Template Viewing Tests
    // ---------------------------------------------------------------------------

    describe('Template Viewing', () => {
        it('should allow anyone to view public templates', () => {
            const student = createTestUser({
                role: 'student',
                tenantId: 'other-tenant' as TenantId,
            });
            const template = createTestTemplate({ visibility: 'public' });

            expect(rbac.canViewTemplate(student, template)).toBe(true);
        });

        it('should allow tenant users to view tenant templates', () => {
            const student = createTestUser({ role: 'student', tenantId: 'tenant-001' as TenantId });
            const template = createTestTemplate({ visibility: 'tenant', tenantId: 'tenant-001' as TenantId });

            expect(rbac.canViewTemplate(student, template)).toBe(true);
        });

        it('should deny cross-tenant access to tenant templates', () => {
            const student = createTestUser({
                role: 'student',
                tenantId: 'tenant-002' as TenantId,
            });
            const template = createTestTemplate({ visibility: 'tenant', tenantId: 'tenant-001' as TenantId });

            expect(rbac.canViewTemplate(student, template)).toBe(false);
        });

        it('should allow owner to view private templates', () => {
            const teacher = createTestUser({
                role: 'teacher',
                id: 'user-teacher-001' as UserId,
            });
            const template = createTestTemplate({
                visibility: 'private',
                createdBy: 'user-teacher-001' as UserId,
            });

            expect(rbac.canViewTemplate(teacher, template)).toBe(true);
        });

        it('should allow admin to view private templates in tenant', () => {
            const admin = createTestUser({
                role: 'admin',
                id: 'admin-001' as UserId,
                tenantId: 'tenant-001' as TenantId,
            });
            const template = createTestTemplate({
                visibility: 'private',
                createdBy: 'other-user' as UserId,
                tenantId: 'tenant-001' as TenantId,
            });

            expect(rbac.canViewTemplate(admin, template)).toBe(true);
        });

        it('should deny other users from viewing private templates', () => {
            const teacher = createTestUser({
                role: 'teacher',
                id: 'other-teacher' as UserId,
            });
            const template = createTestTemplate({
                visibility: 'private',
                createdBy: 'user-teacher-001' as UserId,
            });

            expect(rbac.canViewTemplate(teacher, template)).toBe(false);
        });
    });

    // ---------------------------------------------------------------------------
    // Template Deletion Tests
    // ---------------------------------------------------------------------------

    describe('Template Deletion', () => {
        it('should allow owner to delete template', () => {
            const teacher = createTestUser({
                role: 'teacher',
                id: 'user-teacher-001' as UserId,
            });
            const template = createTestTemplate({ createdBy: 'user-teacher-001' as UserId });

            expect(rbac.canDeleteTemplate(teacher, template)).toBe(true);
        });

        it('should allow admin to delete any template in tenant', () => {
            const admin = createTestUser({ role: 'admin' });
            const template = createTestTemplate({ createdBy: 'other-teacher' as UserId });

            expect(rbac.canDeleteTemplate(admin, template)).toBe(true);
        });

        it('should deny non-owner non-admin from deleting', () => {
            const teacher = createTestUser({
                role: 'teacher',
                id: 'other-teacher' as UserId,
            });
            const template = createTestTemplate({ createdBy: 'user-teacher-001' as UserId });

            expect(rbac.canDeleteTemplate(teacher, template)).toBe(false);
        });

        it('should deny cross-tenant deletion', () => {
            const admin = createTestUser({
                role: 'admin',
                tenantId: 'tenant-002' as TenantId,
            });
            const template = createTestTemplate({ tenantId: 'tenant-001' as TenantId });

            expect(rbac.canDeleteTemplate(admin, template)).toBe(false);
        });
    });

    // ---------------------------------------------------------------------------
    // Run Creation Tests
    // ---------------------------------------------------------------------------

    describe('Run Creation', () => {
        it('should allow any authenticated user to create runs', () => {
            const student = createTestUser({ role: 'student' });
            const template = createTestTemplate({ visibility: 'tenant' });

            expect(rbac.canCreateRun(student, template)).toBe(true);
        });

        it('should deny run creation for non-viewable templates', () => {
            const student = createTestUser({
                role: 'student',
                tenantId: 'tenant-002' as TenantId,
            });
            const template = createTestTemplate({
                visibility: 'private',
                tenantId: 'tenant-001' as TenantId,
            });

            expect(rbac.canCreateRun(student, template)).toBe(false);
        });

        it('should allow run creation for public templates', () => {
            const student = createTestUser({
                role: 'student',
                tenantId: 'tenant-002' as TenantId,
            });
            const template = createTestTemplate({
                visibility: 'public',
                tenantId: 'tenant-001' as TenantId,
            });

            // Can view but not create in different tenant
            expect(rbac.canViewTemplate(student, template)).toBe(true);
            expect(rbac.canCreateRun(student, template)).toBe(false);
        });
    });

    // ---------------------------------------------------------------------------
    // Run Viewing Tests
    // ---------------------------------------------------------------------------

    describe('Run Viewing', () => {
        it('should allow owner to view their run', () => {
            const student = createTestUser({
                role: 'student',
                id: 'user-student-001' as UserId,
            });
            const run = createTestRun({ userId: 'user-student-001' as UserId });

            expect(rbac.canViewRun(student, run)).toBe(true);
        });

        it('should allow teacher to view student runs in tenant', () => {
            const teacher = createTestUser({
                role: 'teacher',
                id: 'teacher-001' as UserId,
            });
            const run = createTestRun({ userId: 'student-001' as UserId });

            expect(rbac.canViewRun(teacher, run)).toBe(true);
        });

        it('should allow admin to view all runs in tenant', () => {
            const admin = createTestUser({ role: 'admin', id: 'admin-001' as UserId });
            const run = createTestRun({ userId: 'student-001' as UserId });

            expect(rbac.canViewRun(admin, run)).toBe(true);
        });

        it('should deny student from viewing other student runs', () => {
            const student = createTestUser({
                role: 'student',
                id: 'student-002' as UserId,
            });
            const run = createTestRun({ userId: 'student-001' as UserId });

            expect(rbac.canViewRun(student, run)).toBe(false);
        });

        it('should deny cross-tenant run viewing', () => {
            const admin = createTestUser({
                role: 'admin',
                tenantId: 'tenant-002' as TenantId,
            });
            const run = createTestRun({ tenantId: 'tenant-001' as TenantId });

            expect(rbac.canViewRun(admin, run)).toBe(false);
        });
    });

    // ---------------------------------------------------------------------------
    // Run Deletion Tests
    // ---------------------------------------------------------------------------

    describe('Run Deletion', () => {
        it('should allow owner to delete their run', () => {
            const student = createTestUser({
                role: 'student',
                id: 'user-student-001' as UserId,
            });
            const run = createTestRun({ userId: 'user-student-001' as UserId });

            expect(rbac.canDeleteRun(student, run)).toBe(true);
        });

        it('should allow admin to delete any run in tenant', () => {
            const admin = createTestUser({ role: 'admin' });
            const run = createTestRun({ userId: 'student-001' as UserId });

            expect(rbac.canDeleteRun(admin, run)).toBe(true);
        });

        it('should deny teacher from deleting student runs', () => {
            const teacher = createTestUser({ role: 'teacher' });
            const run = createTestRun({ userId: 'student-001' as UserId });

            expect(rbac.canDeleteRun(teacher, run)).toBe(false);
        });

        it('should deny cross-tenant run deletion', () => {
            const admin = createTestUser({
                role: 'admin',
                tenantId: 'tenant-002' as TenantId,
            });
            const run = createTestRun({ tenantId: 'tenant-001' as TenantId });

            expect(rbac.canDeleteRun(admin, run)).toBe(false);
        });
    });

    // ---------------------------------------------------------------------------
    // Audit Log Tests
    // ---------------------------------------------------------------------------

    describe('Audit Logging', () => {
        it('should log all access attempts', () => {
            const student = createTestUser({ role: 'student', id: 'student-001' as UserId });
            const template = createTestTemplate();

            rbac.canCreateTemplate(student);
            rbac.canViewTemplate(student, template);

            const logs = rbac.getAuditLog();
            expect(logs.length).toBe(2);
            expect(logs[0].action).toBe('CREATE_TEMPLATE');
            expect(logs[1].action).toBe('VIEW_TEMPLATE');
        });

        it('should capture denied access attempts per tenant', () => {
            const student1 = createTestUser({
                role: 'student',
                id: 'student-001' as UserId,
                tenantId: 'tenant-001' as TenantId,
            });
            const student2 = createTestUser({
                role: 'student',
                id: 'student-002' as UserId,
                tenantId: 'tenant-002' as TenantId,
            });

            rbac.canCreateTemplate(student1);
            rbac.canCreateTemplate(student2);

            const deniedTenant1 = rbac.getDeniedAccessAttempts('tenant-001' as TenantId);
            const deniedTenant2 = rbac.getDeniedAccessAttempts('tenant-002' as TenantId);

            expect(deniedTenant1.length).toBe(1);
            expect(deniedTenant1[0].userId).toBe('student-001');
            expect(deniedTenant2.length).toBe(1);
            expect(deniedTenant2[0].userId).toBe('student-002');
        });

        it('should include reason for denied access', () => {
            const student = createTestUser({
                role: 'student',
                tenantId: 'tenant-002' as TenantId,
            });
            const template = createTestTemplate({
                visibility: 'tenant',
                tenantId: 'tenant-001' as TenantId,
            });

            rbac.canViewTemplate(student, template);

            const logs = rbac.getDeniedAccessAttempts('tenant-002' as TenantId);
            expect(logs.length).toBe(1);
            expect(logs[0].reason).toBe('Cross-tenant access denied');
        });

        it('should track resource access history', () => {
            const teacher = createTestUser({ role: 'teacher', id: 'teacher-001' as UserId });
            const admin = createTestUser({ role: 'admin', id: 'admin-001' as UserId });
            const template = createTestTemplate();

            rbac.canViewTemplate(teacher, template);
            rbac.canEditTemplate(teacher, template);
            rbac.canEditTemplate(admin, template);

            const logs = rbac.getAuditLogForResource('simulation_template', template.id);
            expect(logs.length).toBe(3);
            expect(logs.filter((l) => l.outcome === 'allowed').length).toBe(2);
            expect(logs.filter((l) => l.outcome === 'denied').length).toBe(1);
        });
    });

    // ---------------------------------------------------------------------------
    // Multi-Tenant Isolation Tests
    // ---------------------------------------------------------------------------

    describe('Multi-Tenant Isolation', () => {
        it('should enforce strict tenant boundaries for templates', () => {
            const adminTenant1 = createTestUser({
                role: 'admin',
                tenantId: 'tenant-001' as TenantId,
            });
            const adminTenant2 = createTestUser({
                role: 'admin',
                tenantId: 'tenant-002' as TenantId,
            });
            const templateTenant1 = createTestTemplate({ tenantId: 'tenant-001' as TenantId });

            expect(rbac.canEditTemplate(adminTenant1, templateTenant1)).toBe(true);
            expect(rbac.canEditTemplate(adminTenant2, templateTenant1)).toBe(false);
            expect(rbac.canDeleteTemplate(adminTenant1, templateTenant1)).toBe(true);
            expect(rbac.canDeleteTemplate(adminTenant2, templateTenant1)).toBe(false);
        });

        it('should enforce strict tenant boundaries for runs', () => {
            const teacherTenant1 = createTestUser({
                role: 'teacher',
                tenantId: 'tenant-001' as TenantId,
            });
            const teacherTenant2 = createTestUser({
                role: 'teacher',
                tenantId: 'tenant-002' as TenantId,
            });
            const runTenant1 = createTestRun({ tenantId: 'tenant-001' as TenantId });

            expect(rbac.canViewRun(teacherTenant1, runTenant1)).toBe(true);
            expect(rbac.canViewRun(teacherTenant2, runTenant1)).toBe(false);
        });

        it('should allow public template viewing across tenants but not run creation', () => {
            const student = createTestUser({ tenantId: 'tenant-002' as TenantId });
            const publicTemplate = createTestTemplate({
                visibility: 'public',
                tenantId: 'tenant-001' as TenantId,
            });

            expect(rbac.canViewTemplate(student, publicTemplate)).toBe(true);
            expect(rbac.canCreateRun(student, publicTemplate)).toBe(false);
        });
    });

    // ---------------------------------------------------------------------------
    // Role Hierarchy Tests
    // ---------------------------------------------------------------------------

    describe('Role Hierarchy', () => {
        const roles: UserRole[] = ['student', 'teacher', 'admin'];

        it('should enforce correct role hierarchy for template creation', () => {
            const results = roles.map((role) => {
                const user = createTestUser({ role });
                return { role, canCreate: rbac.canCreateTemplate(user) };
            });

            expect(results.find((r) => r.role === 'student')?.canCreate).toBe(false);
            expect(results.find((r) => r.role === 'teacher')?.canCreate).toBe(true);
            expect(results.find((r) => r.role === 'admin')?.canCreate).toBe(true);
        });

        it('should enforce correct role hierarchy for run viewing', () => {
            const run = createTestRun({ userId: 'student-001' as UserId });

            const results = roles.map((role) => {
                const user = createTestUser({ role, id: `${role}-tester` as UserId });
                return { role, canView: rbac.canViewRun(user, run) };
            });

            expect(results.find((r) => r.role === 'student')?.canView).toBe(false);
            expect(results.find((r) => r.role === 'teacher')?.canView).toBe(true);
            expect(results.find((r) => r.role === 'admin')?.canView).toBe(true);
        });
    });
});
