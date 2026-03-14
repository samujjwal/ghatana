/**
 * Data Cloud Theme Configuration
 *
 * Re-exports centralized theme styles from @ghatana/design-system for consistency
 * across the platform, plus Data-Cloud specific extensions.
 *
 * MIGRATION: Common styles now imported from @ghatana/design-system.
 * Only product-specific extensions are defined locally.
 *
 * @doc.type utility
 * @doc.purpose Centralized theme management for data-cloud
 * @doc.layer frontend
 * @doc.pattern Design System
 */

// =============================================================================
// RE-EXPORTS FROM @ghatana/design-system (SINGLE SOURCE OF TRUTH)
// =============================================================================

export {
    // Style objects
    cardStyles,
    textStyles,
    bgStyles,
    borderStyles,
    inputStyles,
    buttonStyles,
    tableStyles,
    statusStyles,
    // Keep the legacy alias for downstream compatibility.
    metricCardStyles,
    metricCardStyles as metricStyles,
    badgeStyles,
    modalStyles,
    toastStyles,
    navStyles,
} from '@ghatana/design-system';

export { cn } from '@ghatana/utils';

// =============================================================================
// DATA-CLOUD SPECIFIC STYLES
// =============================================================================

/**
 * Data Cloud node type colors for topology visualization.
 */
export const dataCloudColors = {
    /** Source node colors */
    source: {
        bg: 'bg-blue-100 dark:bg-blue-900',
        text: 'text-blue-800 dark:text-blue-200',
        border: 'border-blue-500',
    },
    /** Processor node colors */
    processor: {
        bg: 'bg-purple-100 dark:bg-purple-900',
        text: 'text-purple-800 dark:text-purple-200',
        border: 'border-purple-500',
    },
    /** Sink node colors */
    sink: {
        bg: 'bg-green-100 dark:bg-green-900',
        text: 'text-green-800 dark:text-green-200',
        border: 'border-green-500',
    },
    /** Router node colors */
    router: {
        bg: 'bg-orange-100 dark:bg-orange-900',
        text: 'text-orange-800 dark:text-orange-200',
        border: 'border-orange-500',
    },
    /** Storage node colors */
    storage: {
        bg: 'bg-gray-100 dark:bg-gray-700',
        text: 'text-gray-800 dark:text-gray-300',
        border: 'border-gray-500',
    },
} as const;

/**
 * Collection-specific status styles
 */
export const collectionStatusStyles = {
    /** Active collection */
    active: 'bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200',

    /** Draft collection */
    draft: 'bg-yellow-100 dark:bg-yellow-900 text-yellow-800 dark:text-yellow-200',

    /** Archived collection */
    archived: 'bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-300',

    /** Processing collection */
    processing: 'bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200',
} as const;

/**
 * Workflow-specific status styles
 */
export const workflowStatusStyles = {
    /** Running workflow */
    running: 'bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200',

    /** Completed workflow */
    completed: 'bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200',

    /** Failed workflow */
    failed: 'bg-red-100 dark:bg-red-900 text-red-800 dark:text-red-200',

    /** Pending workflow */
    pending: 'bg-yellow-100 dark:bg-yellow-900 text-yellow-800 dark:text-yellow-200',

    /** Paused workflow */
    paused: 'bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-300',
} as const;

/**
 * Data quality indicator styles
 */
export const dataQualityStyles = {
    /** High quality (90%+) */
    high: 'bg-green-500',

    /** Medium quality (70-89%) */
    medium: 'bg-yellow-500',

    /** Low quality (<70%) */
    low: 'bg-red-500',
} as const;

/**
 * Schema type badge styles
 */
export const schemaTypeStyles = {
    /** Entity schema */
    entity: 'bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200',

    /** Event schema */
    event: 'bg-purple-100 dark:bg-purple-900 text-purple-800 dark:text-purple-200',

    /** TimeSeries schema */
    timeseries: 'bg-orange-100 dark:bg-orange-900 text-orange-800 dark:text-orange-200',

    /** Graph schema */
    graph: 'bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200',

    /** Document schema */
    document: 'bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-300',
} as const;

/**
 * Storage tier styles
 */
export const storageTierStyles = {
    /** Hot tier */
    hot: 'bg-red-100 dark:bg-red-900 text-red-800 dark:text-red-200 border-red-500',

    /** Warm tier */
    warm: 'bg-orange-100 dark:bg-orange-900 text-orange-800 dark:text-orange-200 border-orange-500',

    /** Cool tier */
    cool: 'bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200 border-blue-500',

    /** Cold tier */
    cold: 'bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-300 border-gray-500',
} as const;

/**
 * Governance policy type styles
 */
export const policyTypeStyles = {
    /** Data protection policy */
    'data-protection': 'bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200',

    /** Retention policy */
    retention: 'bg-purple-100 dark:bg-purple-900 text-purple-800 dark:text-purple-200',

    /** Compliance policy */
    compliance: 'bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200',

    /** Access control policy */
    access: 'bg-orange-100 dark:bg-orange-900 text-orange-800 dark:text-orange-200',
} as const;

// =============================================================================
// HELPER FUNCTIONS
// =============================================================================

/**
 * Get data quality style based on percentage
 */
export function getDataQualityStyle(quality: number): string {
    if (quality >= 90) return dataQualityStyles.high;
    if (quality >= 70) return dataQualityStyles.medium;
    return dataQualityStyles.low;
}

/**
 * Get collection status style
 */
export function getCollectionStatusStyle(status: string): string {
    return collectionStatusStyles[status as keyof typeof collectionStatusStyles]
        ?? collectionStatusStyles.draft;
}

/**
 * Get workflow status style
 */
export function getWorkflowStatusStyle(status: string): string {
    return workflowStatusStyles[status as keyof typeof workflowStatusStyles]
        ?? workflowStatusStyles.pending;
}

// =============================================================================
// TYPE EXPORTS
// =============================================================================

export type CollectionStatusKey = keyof typeof collectionStatusStyles;
export type WorkflowStatusKey = keyof typeof workflowStatusStyles;
export type DataQualityKey = keyof typeof dataQualityStyles;
export type SchemaTypeKey = keyof typeof schemaTypeStyles;
export type StorageTierKey = keyof typeof storageTierStyles;
export type PolicyTypeKey = keyof typeof policyTypeStyles;
