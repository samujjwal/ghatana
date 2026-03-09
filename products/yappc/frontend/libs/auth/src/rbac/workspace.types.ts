/**
 * Workspace Governance Types
 *
 * Defines workspace-level access control, membership, and governance.
 * Integrates with RBAC for fine-grained permission management.
 *
 * @doc.type module
 * @doc.purpose Workspace governance types
 * @doc.layer product
 * @doc.pattern Value Object
 */

// ============================================================================
// Workspace Roles
// ============================================================================

/**
 * Workspace-level roles (not to be confused with persona)
 * Role = permission level (what you CAN do)
 * Persona = behavior intent (what you WANT to see)
 */
export enum WorkspaceRole {
    /** Full control over workspace including deletion */
    OWNER = 'owner',
    /** Can manage members, settings, but not delete workspace */
    ADMIN = 'admin',
    /** Can execute based on persona, cannot manage workspace */
    MEMBER = 'member',
    /** Read-only access to workspace resources */
    VIEWER = 'viewer',
}

// ============================================================================
// Persona Types - Comprehensive DevSecOps Lifecycle Personas
// ============================================================================

/**
 * Persona categories for grouping and UI organization
 */
export type PersonaCategory =
    | 'execution' // Direct execution roles (build, test, deploy)
    | 'governance' // Quality, security, compliance
    | 'strategic' // Planning, roadmap, business
    | 'operations' // Runtime, support, monitoring
    | 'administrative'; // Workspace and user management

/**
 * Persona types for dashboard customization.
 * Covers all phases of DevSecOps lifecycle as defined in devsecops-phases-persona.md
 *
 * Categories:
 * - Execution: developer, tech-lead, devops-engineer, qa-engineer, sre
 * - Governance: security-engineer, compliance-officer, architect
 * - Strategic: product-manager, product-owner, program-manager, business-analyst, executive
 * - Operations: release-manager, infrastructure-architect, customer-success, support-lead
 * - Administrative: workspace-admin, stakeholder (read-only)
 */
export type PersonaType =
    // ========== Execution Personas ==========
    /** Software developer focused on feature implementation */
    | 'developer'
    /** Technical leader overseeing team and architecture decisions */
    | 'tech-lead'
    /** DevOps engineer managing pipelines, deployments, and infrastructure */
    | 'devops-engineer'
    /** Quality assurance engineer focused on testing and quality metrics */
    | 'qa-engineer'
    /** Site Reliability Engineer focused on system reliability and observability */
    | 'sre'

    // ========== Governance Personas ==========
    /** Security engineer focused on vulnerabilities, compliance, and threat modeling */
    | 'security-engineer'
    /** Compliance/Risk officer ensuring regulatory and audit requirements */
    | 'compliance-officer'
    /** System/solution architect focused on design coherence and feasibility */
    | 'architect'

    // ========== Strategic Personas ==========
    /** Product manager focused on roadmap, prioritization, and stakeholder alignment */
    | 'product-manager'
    /** Product owner focused on backlog, acceptance criteria, and sprint scope */
    | 'product-owner'
    /** Program manager coordinating multiple projects and cross-team dependencies */
    | 'program-manager'
    /** Business analyst focused on requirement quality, normalization, and gaps */
    | 'business-analyst'
    /** Engineering manager focused on team health, delivery, and resource allocation */
    | 'engineering-manager'
    /** Executive/CISO with strategic overview, risk posture, and business impact */
    | 'executive'

    // ========== Operations Personas ==========
    /** Release manager coordinating release schedules, go/no-go, and rollout strategy */
    | 'release-manager'
    /** Infrastructure/cloud architect designing environments and scaling strategy */
    | 'infrastructure-architect'
    /** Customer success lead managing customer readiness and post-release feedback */
    | 'customer-success'
    /** Support lead managing knowledge base, escalations, and issue triage */
    | 'support-lead'

    // ========== Administrative Personas ==========
    /** Workspace administrator managing members, roles, and permissions */
    | 'workspace-admin'
    /** Read-only stakeholder viewing approved content and reports */
    | 'stakeholder';

/**
 * Persona hierarchy levels for authority comparison
 * Higher number = more authority for approval workflows
 */
export const PERSONA_HIERARCHY: Record<PersonaType, number> = {
    // Level 5: Strategic/Executive
    executive: 5,
    // Level 4: Program/Management
    'program-manager': 4,
    'engineering-manager': 4,
    'product-manager': 4,
    // Level 3: Technical Leadership
    'tech-lead': 3,
    architect: 3,
    'release-manager': 3,
    'infrastructure-architect': 3,
    'compliance-officer': 3,
    // Level 2: Specialized Contributors
    'security-engineer': 2,
    'devops-engineer': 2,
    sre: 2,
    'qa-engineer': 2,
    'product-owner': 2,
    'business-analyst': 2,
    'customer-success': 2,
    'support-lead': 2,
    'workspace-admin': 2,
    // Level 1: Contributors
    developer: 1,
    stakeholder: 1,
};

/**
 * Persona to category mapping for UI grouping
 */
export const PERSONA_CATEGORIES: Record<PersonaType, PersonaCategory> = {
    // Execution
    developer: 'execution',
    'tech-lead': 'execution',
    'devops-engineer': 'execution',
    'qa-engineer': 'execution',
    sre: 'execution',
    // Governance
    'security-engineer': 'governance',
    'compliance-officer': 'governance',
    architect: 'governance',
    // Strategic
    'product-manager': 'strategic',
    'product-owner': 'strategic',
    'program-manager': 'strategic',
    'business-analyst': 'strategic',
    'engineering-manager': 'strategic',
    executive: 'strategic',
    // Operations
    'release-manager': 'operations',
    'infrastructure-architect': 'operations',
    'customer-success': 'operations',
    'support-lead': 'operations',
    // Administrative
    'workspace-admin': 'administrative',
    stakeholder: 'administrative',
};

/**
 * Persona metadata for UI display
 */
export interface PersonaMetadata {
    id: PersonaType;
    name: string;
    shortName: string;
    description: string;
    category: PersonaCategory;
    level: number;
    primaryFocus: string[];
    iconKey: string; // MUI icon key
    color: string; // Theme color
    defaultPhases: string[]; // DevSecOps phases this persona primarily works in
}

/**
 * Complete persona definitions with UI metadata
 */
export const PERSONA_DEFINITIONS: Record<PersonaType, PersonaMetadata> = {
    // ========== Execution Personas ==========
    developer: {
        id: 'developer',
        name: 'Developer',
        shortName: 'Dev',
        description: 'Focus on code quality, feature delivery, and technical debt',
        category: 'execution',
        level: 1,
        primaryFocus: ['Active Tasks', 'Code Reviews', 'Build Status', 'Test Results'],
        iconKey: 'Code',
        color: '#2196F3',
        defaultPhases: ['build', 'test'],
    },
    'tech-lead': {
        id: 'tech-lead',
        name: 'Tech Lead',
        shortName: 'TL',
        description: 'Oversee team progress, technical decisions, and architecture',
        category: 'execution',
        level: 3,
        primaryFocus: ['Team Progress', 'Technical Blockers', 'Architecture Decisions', 'PR Cycle Time'],
        iconKey: 'SupervisorAccount',
        color: '#9C27B0',
        defaultPhases: ['design', 'build', 'test'],
    },
    'devops-engineer': {
        id: 'devops-engineer',
        name: 'DevOps Engineer',
        shortName: 'DevOps',
        description: 'Manage deployments, infrastructure, and operational health',
        category: 'execution',
        level: 2,
        primaryFocus: ['Pipeline Health', 'Deployment Status', 'Infrastructure Metrics', 'DORA Metrics'],
        iconKey: 'Cloud',
        color: '#4CAF50',
        defaultPhases: ['build', 'deploy', 'operate'],
    },
    'qa-engineer': {
        id: 'qa-engineer',
        name: 'QA Engineer',
        shortName: 'QA',
        description: 'Track test coverage, defects, and quality metrics',
        category: 'execution',
        level: 2,
        primaryFocus: ['Test Results', 'Bug Reports', 'Coverage Metrics', 'Regression Rate'],
        iconKey: 'BugReport',
        color: '#FF9800',
        defaultPhases: ['test', 'release'],
    },
    sre: {
        id: 'sre',
        name: 'Site Reliability Engineer',
        shortName: 'SRE',
        description: 'Ensure system reliability, observability, and incident response',
        category: 'execution',
        level: 2,
        primaryFocus: ['SLO/SLI Metrics', 'Incident Management', 'Error Budgets', 'On-Call Status'],
        iconKey: 'MonitorHeart',
        color: '#00BCD4',
        defaultPhases: ['operate', 'monitor'],
    },

    // ========== Governance Personas ==========
    'security-engineer': {
        id: 'security-engineer',
        name: 'Security Engineer',
        shortName: 'Sec',
        description: 'Monitor vulnerabilities, compliance, and security gates',
        category: 'governance',
        level: 2,
        primaryFocus: ['Critical Vulnerabilities', 'Security Scan Results', 'Compliance Status', 'Threat Models'],
        iconKey: 'Security',
        color: '#F44336',
        defaultPhases: ['secure', 'build', 'deploy'],
    },
    'compliance-officer': {
        id: 'compliance-officer',
        name: 'Compliance Officer',
        shortName: 'Compliance',
        description: 'Ensure regulatory compliance, audit readiness, and risk governance',
        category: 'governance',
        level: 3,
        primaryFocus: ['Audit Status', 'Regulatory Compliance', 'Risk Register', 'Policy Adherence'],
        iconKey: 'Gavel',
        color: '#795548',
        defaultPhases: ['release', 'operate'],
    },
    architect: {
        id: 'architect',
        name: 'Architect',
        shortName: 'Arch',
        description: 'Design system architecture, ensure coherence and feasibility',
        category: 'governance',
        level: 3,
        primaryFocus: ['System Context', 'Dependency Graphs', 'Architecture Decisions', 'Technical Feasibility'],
        iconKey: 'Architecture',
        color: '#673AB7',
        defaultPhases: ['plan', 'design'],
    },

    // ========== Strategic Personas ==========
    'product-manager': {
        id: 'product-manager',
        name: 'Product Manager',
        shortName: 'PM',
        description: 'Drive product strategy, roadmap, and stakeholder alignment',
        category: 'strategic',
        level: 4,
        primaryFocus: ['Roadmap Progress', 'Feature Adoption', 'Stakeholder Feedback', 'Market Alignment'],
        iconKey: 'TrendingUp',
        color: '#E91E63',
        defaultPhases: ['plan', 'release'],
    },
    'product-owner': {
        id: 'product-owner',
        name: 'Product Owner',
        shortName: 'PO',
        description: 'Manage backlog, define acceptance criteria, and sprint scope',
        category: 'strategic',
        level: 2,
        primaryFocus: ['Backlog Health', 'Sprint Scope', 'Acceptance Criteria', 'Story Points'],
        iconKey: 'Assignment',
        color: '#FF5722',
        defaultPhases: ['plan', 'build'],
    },
    'program-manager': {
        id: 'program-manager',
        name: 'Program Manager',
        shortName: 'PgM',
        description: 'Coordinate multiple projects, manage dependencies and timelines',
        category: 'strategic',
        level: 4,
        primaryFocus: ['Program Timeline', 'Cross-Team Dependencies', 'Milestone Progress', 'Risk Mitigation'],
        iconKey: 'AccountTree',
        color: '#3F51B5',
        defaultPhases: ['plan', 'release'],
    },
    'business-analyst': {
        id: 'business-analyst',
        name: 'Business Analyst',
        shortName: 'BA',
        description: 'Analyze requirements, ensure quality and consistency',
        category: 'strategic',
        level: 2,
        primaryFocus: ['Requirement Quality', 'Gap Analysis', 'Taxonomy Coverage', 'Duplication Alerts'],
        iconKey: 'Analytics',
        color: '#009688',
        defaultPhases: ['plan', 'design'],
    },
    'engineering-manager': {
        id: 'engineering-manager',
        name: 'Engineering Manager',
        shortName: 'EM',
        description: 'Manage team health, delivery velocity, and resource allocation',
        category: 'strategic',
        level: 4,
        primaryFocus: ['Team Velocity', 'Sprint Progress', 'Resource Utilization', 'Team Health'],
        iconKey: 'Groups',
        color: '#607D8B',
        defaultPhases: ['plan', 'build', 'release'],
    },
    executive: {
        id: 'executive',
        name: 'Executive / CISO',
        shortName: 'Exec',
        description: 'High-level metrics, risk posture, and strategic insights',
        category: 'strategic',
        level: 5,
        primaryFocus: ['Risk Score', 'Compliance Status', 'Business Impact', 'Portfolio Health'],
        iconKey: 'BusinessCenter',
        color: '#1A237E',
        defaultPhases: ['plan', 'release', 'operate'],
    },

    // ========== Operations Personas ==========
    'release-manager': {
        id: 'release-manager',
        name: 'Release Manager',
        shortName: 'RM',
        description: 'Coordinate release schedules, go/no-go decisions, and rollout',
        category: 'operations',
        level: 3,
        primaryFocus: ['Release Schedule', 'Gate Status', 'Rollout Progress', 'Rollback Readiness'],
        iconKey: 'RocketLaunch',
        color: '#8BC34A',
        defaultPhases: ['release', 'deploy'],
    },
    'infrastructure-architect': {
        id: 'infrastructure-architect',
        name: 'Infrastructure Architect',
        shortName: 'Infra',
        description: 'Design and manage cloud infrastructure, scaling, and environments',
        category: 'operations',
        level: 3,
        primaryFocus: ['Infrastructure Health', 'Cost Optimization', 'Environment Status', 'Scaling Metrics'],
        iconKey: 'Storage',
        color: '#00ACC1',
        defaultPhases: ['design', 'deploy', 'operate'],
    },
    'customer-success': {
        id: 'customer-success',
        name: 'Customer Success',
        shortName: 'CS',
        description: 'Manage customer readiness, adoption, and post-release feedback',
        category: 'operations',
        level: 2,
        primaryFocus: ['Customer Readiness', 'Adoption Metrics', 'NPS/CSAT', 'Feature Requests'],
        iconKey: 'SupportAgent',
        color: '#FFC107',
        defaultPhases: ['release', 'operate'],
    },
    'support-lead': {
        id: 'support-lead',
        name: 'Support Lead',
        shortName: 'Support',
        description: 'Manage support operations, knowledge base, and escalations',
        category: 'operations',
        level: 2,
        primaryFocus: ['Ticket Volume', 'Resolution Time', 'Escalation Rate', 'Knowledge Base Coverage'],
        iconKey: 'Headset',
        color: '#FF7043',
        defaultPhases: ['operate', 'feedback'],
    },

    // ========== Administrative Personas ==========
    'workspace-admin': {
        id: 'workspace-admin',
        name: 'Workspace Admin',
        shortName: 'Admin',
        description: 'Manage workspace members, roles, and access permissions',
        category: 'administrative',
        level: 2,
        primaryFocus: ['Member Management', 'Role Assignments', 'Permission Audit', 'Activity Log'],
        iconKey: 'AdminPanelSettings',
        color: '#546E7A',
        defaultPhases: [],
    },
    stakeholder: {
        id: 'stakeholder',
        name: 'Stakeholder',
        shortName: 'View',
        description: 'Read-only access to approved content, reports, and dashboards',
        category: 'administrative',
        level: 1,
        primaryFocus: ['Approved Requirements', 'Release Notes', 'Project Status', 'Exported Reports'],
        iconKey: 'Visibility',
        color: '#78909C',
        defaultPhases: [],
    },
};

/**
 * Get all personas in a specific category
 */
export function getPersonasByCategory(category: PersonaCategory): PersonaType[] {
    return (Object.keys(PERSONA_CATEGORIES) as PersonaType[]).filter(
        (persona) => PERSONA_CATEGORIES[persona] === category
    );
}

/**
 * Get personas at or above a certain hierarchy level
 */
export function getPersonasAtLevel(minLevel: number): PersonaType[] {
    return (Object.keys(PERSONA_HIERARCHY) as PersonaType[]).filter(
        (persona) => PERSONA_HIERARCHY[persona] >= minLevel
    );
}

/**
 * Check if persona A has higher authority than persona B
 */
export function hasHigherAuthority(
    personaA: PersonaType,
    personaB: PersonaType
): boolean {
    return PERSONA_HIERARCHY[personaA] > PERSONA_HIERARCHY[personaB];
}

// ============================================================================
// Workspace Membership
// ============================================================================

/**
 * Represents a user's membership in a workspace
 */
export interface WorkspaceMember {
    /** Unique member ID */
    id: string;
    /** User ID (from auth system) */
    userId: string;
    /** User display name */
    displayName: string;
    /** User email */
    email: string;
    /** User avatar URL */
    avatarUrl?: string;
    /** Workspace ID */
    workspaceId: string;
    /** Role in this workspace (determines permissions) */
    role: WorkspaceRole;
    /** Active personas (determines UI views) */
    personas: PersonaType[];
    /** Currently selected persona */
    activePersona: PersonaType;
    /** When the user joined this workspace */
    joinedAt: Date;
    /** Who invited this user */
    invitedBy?: string;
    /** When membership expires (if applicable) */
    expiresAt?: Date;
    /** Last activity in this workspace */
    lastActiveAt?: Date;
    /** Whether this member is active */
    isActive: boolean;
}

/**
 * Workspace invitation
 */
export interface WorkspaceInvitation {
    /** Unique invitation ID */
    id: string;
    /** Workspace ID */
    workspaceId: string;
    /** Invitee email */
    email: string;
    /** Proposed role for invitee */
    role: WorkspaceRole;
    /** Proposed personas for invitee */
    personas: PersonaType[];
    /** Who sent the invitation */
    invitedBy: string;
    /** When invitation was sent */
    sentAt: Date;
    /** When invitation expires */
    expiresAt: Date;
    /** Invitation status */
    status: 'pending' | 'accepted' | 'declined' | 'expired';
    /** Token for accepting invitation */
    token: string;
}

// ============================================================================
// Workspace Entity
// ============================================================================

/**
 * Workspace settings
 */
export interface WorkspaceSettings {
    /** AI assistance enabled */
    aiEnabled: boolean;
    /** AI confidence threshold (0-1) */
    aiConfidenceThreshold: number;
    /** Data exposure level for AI */
    aiDataExposure: 'full' | 'limited' | 'minimal';
    /** Whether audit log is enabled */
    auditEnabled: boolean;
    /** Audit log retention days */
    auditRetentionDays: number;
    /** Require approval for requirement changes */
    requireApprovalForRequirements: boolean;
    /** Minimum approvers for requirements */
    minimumApprovers: number;
    /** Allowed personas in this workspace */
    allowedPersonas: PersonaType[];
}

/**
 * Workspace entity
 */
export interface Workspace {
    /** Unique workspace ID */
    id: string;
    /** Workspace name */
    name: string;
    /** Workspace description */
    description?: string;
    /** Workspace slug (URL-friendly name) */
    slug: string;
    /** Owner user ID */
    ownerId: string;
    /** Creation timestamp */
    createdAt: Date;
    /** Last update timestamp */
    updatedAt: Date;
    /** Whether workspace is archived */
    isArchived: boolean;
    /** Workspace settings */
    settings: WorkspaceSettings;
    /** Member count (denormalized for display) */
    memberCount: number;
    /** Project count (denormalized for display) */
    projectCount: number;
}

// ============================================================================
// Permission Checks
// ============================================================================

/**
 * Available permissions in the system
 */
export type Permission =
    // Workspace management
    | 'workspace:read'
    | 'workspace:update'
    | 'workspace:delete'
    | 'workspace:archive'
    // Member management
    | 'member:invite'
    | 'member:remove'
    | 'member:update-role'
    | 'member:update-persona'
    // Project management
    | 'project:create'
    | 'project:read'
    | 'project:update'
    | 'project:delete'
    // Requirement management
    | 'requirement:create'
    | 'requirement:read'
    | 'requirement:update'
    | 'requirement:delete'
    | 'requirement:approve'
    // Task management
    | 'task:create'
    | 'task:read'
    | 'task:update'
    | 'task:delete'
    // AI features
    | 'ai:configure'
    | 'ai:use'
    | 'ai:review-suggestions'
    // Audit
    | 'audit:read'
    | 'audit:export'
    // Version control
    | 'version:read'
    | 'version:restore';

/**
 * Role-to-permission mapping
 */
export const ROLE_PERMISSIONS: Record<WorkspaceRole, Permission[]> = {
    [WorkspaceRole.OWNER]: [
        'workspace:read',
        'workspace:update',
        'workspace:delete',
        'workspace:archive',
        'member:invite',
        'member:remove',
        'member:update-role',
        'member:update-persona',
        'project:create',
        'project:read',
        'project:update',
        'project:delete',
        'requirement:create',
        'requirement:read',
        'requirement:update',
        'requirement:delete',
        'requirement:approve',
        'task:create',
        'task:read',
        'task:update',
        'task:delete',
        'ai:configure',
        'ai:use',
        'ai:review-suggestions',
        'audit:read',
        'audit:export',
        'version:read',
        'version:restore',
    ],
    [WorkspaceRole.ADMIN]: [
        'workspace:read',
        'workspace:update',
        'workspace:archive',
        'member:invite',
        'member:remove',
        'member:update-role',
        'member:update-persona',
        'project:create',
        'project:read',
        'project:update',
        'project:delete',
        'requirement:create',
        'requirement:read',
        'requirement:update',
        'requirement:delete',
        'requirement:approve',
        'task:create',
        'task:read',
        'task:update',
        'task:delete',
        'ai:use',
        'ai:review-suggestions',
        'audit:read',
        'audit:export',
        'version:read',
        'version:restore',
    ],
    [WorkspaceRole.MEMBER]: [
        'workspace:read',
        'project:read',
        'project:update',
        'requirement:create',
        'requirement:read',
        'requirement:update',
        'task:create',
        'task:read',
        'task:update',
        'task:delete',
        'ai:use',
        'version:read',
    ],
    [WorkspaceRole.VIEWER]: [
        'workspace:read',
        'project:read',
        'requirement:read',
        'task:read',
        'version:read',
    ],
};

/**
 * Check if a role has a specific permission
 */
export function hasPermission(
    role: WorkspaceRole,
    permission: Permission
): boolean {
    return ROLE_PERMISSIONS[role].includes(permission);
}

/**
 * Get all permissions for a role
 */
export function getPermissions(role: WorkspaceRole): Permission[] {
    return [...ROLE_PERMISSIONS[role]];
}

/**
 * Check if role A can manage role B (for role assignment)
 */
export function canManageRole(
    managerRole: WorkspaceRole,
    targetRole: WorkspaceRole
): boolean {
    // Role hierarchy for reference (unused but documents the logic)
    // OWNER: 4, ADMIN: 3, MEMBER: 2, VIEWER: 1

    // Owner can manage everyone except other owners
    if (managerRole === WorkspaceRole.OWNER) {
        return targetRole !== WorkspaceRole.OWNER;
    }

    // Admin can manage members and viewers
    if (managerRole === WorkspaceRole.ADMIN) {
        return (
            targetRole === WorkspaceRole.MEMBER ||
            targetRole === WorkspaceRole.VIEWER
        );
    }

    // Members and viewers cannot manage roles
    return false;
}

// ============================================================================
// Persona Permissions (what each persona can VIEW and DO)
// ============================================================================

/**
 * Persona-specific capabilities define what views and actions are available
 * to each persona. This is separate from role permissions which control
 * CRUD access. Persona capabilities control UI visibility and workflow access.
 */
export type PersonaCapability =
    // ========== View Capabilities ==========
    | 'view:my-tasks' // Personal task list
    | 'view:team-tasks' // Team/project tasks
    | 'view:all-tasks' // All workspace tasks
    | 'view:code-reviews' // PR/code review queue
    | 'view:build-status' // CI/CD build status
    | 'view:test-results' // Test execution results
    | 'view:coverage-metrics' // Code coverage
    | 'view:security-scans' // SAST/DAST/SCA results
    | 'view:vulnerabilities' // Vulnerability dashboard
    | 'view:compliance' // Compliance status
    | 'view:audit-log' // Audit trail
    | 'view:architecture' // Architecture diagrams
    | 'view:dependencies' // Dependency graphs
    | 'view:roadmap' // Product roadmap
    | 'view:backlog' // Product backlog
    | 'view:sprint' // Sprint board
    | 'view:velocity' // Team velocity
    | 'view:burndown' // Sprint burndown
    | 'view:release-calendar' // Release schedule
    | 'view:deployment-status' // Deployment pipeline
    | 'view:infrastructure' // Infrastructure health
    | 'view:incidents' // Incident dashboard
    | 'view:slo-sli' // SLO/SLI metrics
    | 'view:customer-feedback' // NPS/CSAT
    | 'view:support-tickets' // Support queue
    | 'view:executive-summary' // Executive dashboard
    | 'view:portfolio' // Portfolio overview
    | 'view:risk-register' // Risk management
    | 'view:member-list' // Workspace members
    | 'view:permission-matrix' // Role/permission mapping

    // ========== Action Capabilities ==========
    | 'action:create-task' // Create tasks
    | 'action:assign-task' // Assign to others
    | 'action:approve-task' // Approve completion
    | 'action:review-code' // Perform code review
    | 'action:merge-pr' // Merge pull requests
    | 'action:trigger-build' // Trigger CI builds
    | 'action:trigger-deploy' // Trigger deployments
    | 'action:rollback' // Rollback deployment
    | 'action:run-tests' // Execute test suites
    | 'action:run-security-scan' // Trigger security scans
    | 'action:acknowledge-vuln' // Acknowledge vulnerabilities
    | 'action:waive-vuln' // Waive/accept risk
    | 'action:approve-release' // Release go/no-go
    | 'action:manage-backlog' // Prioritize backlog
    | 'action:manage-sprint' // Sprint planning
    | 'action:manage-roadmap' // Roadmap editing
    | 'action:invite-member' // Invite users
    | 'action:manage-roles' // Assign roles
    | 'action:manage-personas' // Assign personas
    | 'action:export-reports' // Export data
    | 'action:configure-ai' // Configure AI settings
    | 'action:manage-policies' // Manage compliance policies
    | 'action:escalate' // Escalate issues
    | 'action:resolve-incident'; // Close incidents

/**
 * Persona capability mappings
 * Defines what each persona can view and do regardless of role
 * (Role still controls CRUD permissions)
 */
export const PERSONA_CAPABILITIES: Record<PersonaType, PersonaCapability[]> = {
    developer: [
        'view:my-tasks',
        'view:team-tasks',
        'view:code-reviews',
        'view:build-status',
        'view:test-results',
        'view:coverage-metrics',
        'view:security-scans',
        'view:sprint',
        'action:create-task',
        'action:review-code',
        'action:trigger-build',
        'action:run-tests',
    ],
    'tech-lead': [
        'view:my-tasks',
        'view:team-tasks',
        'view:all-tasks',
        'view:code-reviews',
        'view:build-status',
        'view:test-results',
        'view:coverage-metrics',
        'view:security-scans',
        'view:architecture',
        'view:dependencies',
        'view:sprint',
        'view:velocity',
        'view:burndown',
        'action:create-task',
        'action:assign-task',
        'action:approve-task',
        'action:review-code',
        'action:merge-pr',
        'action:trigger-build',
        'action:run-tests',
    ],
    'devops-engineer': [
        'view:my-tasks',
        'view:build-status',
        'view:deployment-status',
        'view:infrastructure',
        'view:incidents',
        'view:slo-sli',
        'view:security-scans',
        'action:trigger-build',
        'action:trigger-deploy',
        'action:rollback',
        'action:run-security-scan',
        'action:resolve-incident',
    ],
    'qa-engineer': [
        'view:my-tasks',
        'view:team-tasks',
        'view:test-results',
        'view:coverage-metrics',
        'view:sprint',
        'view:backlog',
        'action:create-task',
        'action:run-tests',
    ],
    sre: [
        'view:deployment-status',
        'view:infrastructure',
        'view:incidents',
        'view:slo-sli',
        'view:audit-log',
        'action:trigger-deploy',
        'action:rollback',
        'action:resolve-incident',
        'action:escalate',
    ],
    'security-engineer': [
        'view:security-scans',
        'view:vulnerabilities',
        'view:compliance',
        'view:audit-log',
        'view:dependencies',
        'action:run-security-scan',
        'action:acknowledge-vuln',
        'action:waive-vuln',
        'action:manage-policies',
    ],
    'compliance-officer': [
        'view:compliance',
        'view:audit-log',
        'view:risk-register',
        'view:security-scans',
        'view:vulnerabilities',
        'action:manage-policies',
        'action:approve-release',
        'action:export-reports',
    ],
    architect: [
        'view:architecture',
        'view:dependencies',
        'view:roadmap',
        'view:backlog',
        'view:security-scans',
        'action:create-task',
        'action:approve-task',
    ],
    'product-manager': [
        'view:roadmap',
        'view:backlog',
        'view:sprint',
        'view:velocity',
        'view:release-calendar',
        'view:customer-feedback',
        'view:executive-summary',
        'action:manage-backlog',
        'action:manage-roadmap',
        'action:approve-release',
        'action:export-reports',
    ],
    'product-owner': [
        'view:backlog',
        'view:sprint',
        'view:velocity',
        'view:burndown',
        'view:team-tasks',
        'action:create-task',
        'action:manage-backlog',
        'action:manage-sprint',
    ],
    'program-manager': [
        'view:all-tasks',
        'view:roadmap',
        'view:release-calendar',
        'view:velocity',
        'view:risk-register',
        'view:executive-summary',
        'view:portfolio',
        'action:manage-roadmap',
        'action:approve-release',
        'action:escalate',
        'action:export-reports',
    ],
    'business-analyst': [
        'view:backlog',
        'view:roadmap',
        'view:dependencies',
        'view:customer-feedback',
        'action:create-task',
        'action:manage-backlog',
    ],
    'engineering-manager': [
        'view:team-tasks',
        'view:all-tasks',
        'view:velocity',
        'view:burndown',
        'view:sprint',
        'view:release-calendar',
        'view:member-list',
        'action:assign-task',
        'action:manage-sprint',
        'action:invite-member',
        'action:export-reports',
    ],
    executive: [
        'view:executive-summary',
        'view:portfolio',
        'view:risk-register',
        'view:compliance',
        'view:roadmap',
        'view:release-calendar',
        'view:customer-feedback',
        'view:audit-log',
        'action:approve-release',
        'action:export-reports',
        'action:configure-ai',
    ],
    'release-manager': [
        'view:release-calendar',
        'view:deployment-status',
        'view:test-results',
        'view:security-scans',
        'view:compliance',
        'view:all-tasks',
        'action:approve-release',
        'action:trigger-deploy',
        'action:rollback',
        'action:export-reports',
    ],
    'infrastructure-architect': [
        'view:infrastructure',
        'view:deployment-status',
        'view:architecture',
        'view:dependencies',
        'view:slo-sli',
        'action:trigger-deploy',
    ],
    'customer-success': [
        'view:customer-feedback',
        'view:support-tickets',
        'view:release-calendar',
        'view:roadmap',
        'action:escalate',
        'action:export-reports',
    ],
    'support-lead': [
        'view:support-tickets',
        'view:customer-feedback',
        'view:incidents',
        'view:release-calendar',
        'action:create-task',
        'action:escalate',
        'action:resolve-incident',
    ],
    'workspace-admin': [
        'view:member-list',
        'view:permission-matrix',
        'view:audit-log',
        'action:invite-member',
        'action:manage-roles',
        'action:manage-personas',
        'action:export-reports',
    ],
    stakeholder: [
        'view:roadmap',
        'view:release-calendar',
        'view:executive-summary',
        'action:export-reports',
    ],
};

/**
 * Check if a persona has a specific capability
 */
export function hasCapability(
    persona: PersonaType,
    capability: PersonaCapability
): boolean {
    return PERSONA_CAPABILITIES[persona].includes(capability);
}

/**
 * Get all capabilities for a persona
 */
export function getCapabilities(persona: PersonaType): PersonaCapability[] {
    return [...PERSONA_CAPABILITIES[persona]];
}

/**
 * Get all personas that have a specific capability
 */
export function getPersonasWithCapability(
    capability: PersonaCapability
): PersonaType[] {
    return (Object.keys(PERSONA_CAPABILITIES) as PersonaType[]).filter((persona) =>
        PERSONA_CAPABILITIES[persona].includes(capability)
    );
}
