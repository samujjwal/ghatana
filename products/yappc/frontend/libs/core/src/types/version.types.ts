/**
 * Version Control Types
 *
 * Defines versioning for tasks, requirements, and other entities.
 * Supports full history, diffs, and rollback functionality.
 *
 * @doc.type module
 * @doc.purpose Version control types
 * @doc.layer product
 * @doc.pattern Value Object
 */

// ============================================================================
// Version Types
// ============================================================================

/**
 * Entity types that support versioning
 */
export type VersionedEntityType =
    | 'requirement'
    | 'task'
    | 'canvas'
    | 'phase'
    | 'artifact';

/**
 * Version status
 */
export type VersionStatus =
    | 'draft'
    | 'pending-review'
    | 'approved'
    | 'rejected'
    | 'superseded'
    | 'archived';

/**
 * Change type for tracking modifications
 */
export type ChangeType = 'create' | 'update' | 'delete' | 'restore' | 'merge';

// ============================================================================
// Version Entity
// ============================================================================

/**
 * Individual field change
 */
export interface FieldChange<T = unknown> {
    /** Field path (e.g., 'title', 'metadata.tags') */
    field: string;
    /** Value before change */
    oldValue: T;
    /** Value after change */
    newValue: T;
    /** Type of value for display purposes */
    valueType: 'string' | 'number' | 'boolean' | 'array' | 'object' | 'date';
}

/**
 * Version metadata
 */
export interface VersionMetadata {
    /** Source of the change */
    source: 'user' | 'ai' | 'system' | 'import';
    /** AI confidence if source is 'ai' */
    aiConfidence?: number;
    /** Associated workflow ID */
    workflowId?: string;
    /** Associated task ID */
    taskId?: string;
    /** Custom metadata */
    custom?: Record<string, unknown>;
}

/**
 * Entity version record
 */
export interface EntityVersion<T = Record<string, unknown>> {
    /** Unique version ID */
    id: string;
    /** Entity ID this version belongs to */
    entityId: string;
    /** Entity type */
    entityType: VersionedEntityType;
    /** Version number (sequential) */
    versionNumber: number;
    /** Version status */
    status: VersionStatus;
    /** Change type */
    changeType: ChangeType;
    /** User who created this version */
    createdBy: string;
    /** User display name */
    createdByName: string;
    /** When this version was created */
    createdAt: Date;
    /** Change description / commit message */
    changeLog: string;
    /** Parent version ID (for branching support) */
    parentVersionId?: string;
    /** Full snapshot of entity at this version */
    snapshot: T;
    /** List of changes from parent version */
    changes: FieldChange[];
    /** Version metadata */
    metadata: VersionMetadata;
    /** Tags for this version */
    tags?: string[];
    /** Approval information */
    approval?: VersionApproval;
}

/**
 * Version approval record
 */
export interface VersionApproval {
    /** Approval status */
    status: 'pending' | 'approved' | 'rejected';
    /** Approvers required */
    requiredApprovers: string[];
    /** Approvers who have approved */
    approvedBy: ApprovalRecord[];
    /** Approvers who have rejected */
    rejectedBy: ApprovalRecord[];
    /** Approval deadline */
    deadline?: Date;
}

/**
 * Individual approval record
 */
export interface ApprovalRecord {
    /** Approver user ID */
    userId: string;
    /** Approver name */
    userName: string;
    /** Approval decision */
    decision: 'approved' | 'rejected';
    /** Decision timestamp */
    timestamp: Date;
    /** Optional comment */
    comment?: string;
}

// ============================================================================
// Version History
// ============================================================================

/**
 * Version history for an entity
 */
export interface VersionHistory {
    /** Entity ID */
    entityId: string;
    /** Entity type */
    entityType: VersionedEntityType;
    /** Total version count */
    totalVersions: number;
    /** Current/active version number */
    currentVersion: number;
    /** Latest version number */
    latestVersion: number;
    /** Versions (may be paginated) */
    versions: EntityVersion[];
}

/**
 * Version comparison result
 */
export interface VersionDiff {
    /** Source version ID */
    sourceVersionId: string;
    /** Source version number */
    sourceVersionNumber: number;
    /** Target version ID */
    targetVersionId: string;
    /** Target version number */
    targetVersionNumber: number;
    /** List of changes */
    changes: FieldChange[];
    /** Summary statistics */
    summary: {
        added: number;
        modified: number;
        removed: number;
    };
}

// ============================================================================
// Version Service Interface
// ============================================================================

/**
 * Filter options for version queries
 */
export interface VersionFilter {
    /** Entity ID */
    entityId?: string;
    /** Entity type */
    entityType?: VersionedEntityType;
    /** Version status */
    status?: VersionStatus;
    /** Created by user ID */
    createdBy?: string;
    /** Created after date */
    createdAfter?: Date;
    /** Created before date */
    createdBefore?: Date;
    /** Change type */
    changeType?: ChangeType;
    /** Has tag */
    hasTag?: string;
}

/**
 * Version creation input
 */
export interface CreateVersionInput<T = Record<string, unknown>> {
    /** Entity ID */
    entityId: string;
    /** Entity type */
    entityType: VersionedEntityType;
    /** Change log / commit message */
    changeLog: string;
    /** New snapshot */
    snapshot: T;
    /** Version metadata */
    metadata?: Partial<VersionMetadata>;
    /** Tags for this version */
    tags?: string[];
    /** Whether to request approval */
    requestApproval?: boolean;
    /** Required approvers (if requesting approval) */
    requiredApprovers?: string[];
}

/**
 * Version restore input
 */
export interface RestoreVersionInput {
    /** Entity ID */
    entityId: string;
    /** Version ID to restore */
    versionId: string;
    /** Reason for restore */
    reason: string;
}
