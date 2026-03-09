/**
 * Organization Domain Types
 *
 * Core type definitions for the organization structure system.
 * These types represent the hierarchical organization model.
 *
 * @package @ghatana/software-org-web
 */

/**
 * Organization node types
 */
export type OrgNodeType = 'organization' | 'department' | 'team' | 'role' | 'person';

/**
 * Organization structure
 */
export interface Organization {
  id: string;
  name: string;
  description?: string;
  departments: Department[];
  metadata?: Record<string, unknown>;
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Department within an organization
 */
export interface Department {
  id: string;
  name: string;
  description?: string;
  head?: string; // Person ID
  teams: Team[];
  metadata?: Record<string, unknown>;
  organizationId: string;
}

/**
 * Team within a department
 */
export interface Team {
  id: string;
  name: string;
  description?: string;
  manager?: string; // Person ID
  members: TeamMember[];
  departmentId: string;
  metadata?: Record<string, unknown>;
}

/**
 * Team member assignment
 */
export interface TeamMember {
  personId: string;
  roleId: string;
  isPrimary: boolean;
  allocationPercent: number; // 0-100
  startDate: Date;
  endDate?: Date;
}

/**
 * Role definition
 */
export interface Role {
  id: string;
  name: string;
  description?: string;
  level: string; // e.g., "IC1", "IC2", "M1", "M2"
  responsibilities: string[];
  requiredSkills: string[];
  permissions: string[];
}

/**
 * Person in the organization
 */
export interface Person {
  id: string;
  name: string;
  email: string;
  avatarUrl?: string;
  title?: string;
  department?: string;
  team?: string;
  manager?: string;
  reports?: string[]; // Person IDs
  metadata?: Record<string, unknown>;
}

/**
 * Org tree node (unified view for tree display)
 */
export interface OrgNode {
  id: string;
  type: OrgNodeType;
  name: string;
  description?: string;
  children: OrgNode[];
  metadata?: {
    headcount?: number;
    capacity?: number;
    status?: 'optimal' | 'high-load' | 'overloaded';
    [key: string]: unknown;
  };
  parent?: string; // Parent node ID
}

/**
 * Restructure proposal
 */
export interface RestructureProposal {
  id: string;
  title: string;
  description: string;
  proposedBy: string; // Person ID
  status: 'draft' | 'pending' | 'approved' | 'rejected';
  changes: OrgChange[];
  approvalChain: ApprovalStep[];
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Organization change
 */
export interface OrgChange {
  id: string;
  type: 'create' | 'update' | 'delete' | 'move';
  nodeType: OrgNodeType;
  nodeId: string;
  before?: Partial<OrgNode>;
  after?: Partial<OrgNode>;
  impact: ChangeImpact;
}

/**
 * Change impact analysis
 */
export interface ChangeImpact {
  affectedPeople: number;
  affectedTeams: number;
  affectedDepartments: number;
  riskLevel: 'low' | 'medium' | 'high';
  dependencies: string[];
}

/**
 * Approval step in workflow
 */
export interface ApprovalStep {
  id: string;
  order: number;
  approverRole: string; // e.g., "manager", "director", "owner"
  approver?: string; // Person ID (assigned)
  status: 'pending' | 'approved' | 'rejected';
  comments?: string;
  approvedAt?: Date;
}

/**
 * Audit log entry
 */
export interface AuditEntry {
  id: string;
  timestamp: Date;
  action: string;
  actor: string; // Person ID
  target: {
    type: OrgNodeType;
    id: string;
    name: string;
  };
  changes: Record<string, { before: unknown; after: unknown }>;
  metadata?: Record<string, unknown>;
}

/**
 * Search filters for org tree
 */
export interface OrgSearchFilter {
  query?: string;
  nodeTypes?: OrgNodeType[];
  departments?: string[];
  teams?: string[];
  status?: Array<'optimal' | 'high-load' | 'overloaded'>;
}

/**
 * Tree view options
 */
export interface TreeViewOptions {
  expandedNodes: Set<string>;
  selectedNode?: string;
  showMetrics: boolean;
  showPeople: boolean;
  groupBy?: 'department' | 'function' | 'location';
}

