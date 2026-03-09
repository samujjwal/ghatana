import { Badge } from "@/components/ui";

/**
 * StatusBadge - Reusable status indicator with predefined mappings
 *
 * <p><b>Purpose</b><br>
 * Provides consistent status badge rendering across the application.
 * Maps common status values to appropriate colors/tones.
 *
 * <p><b>Features</b><br>
 * - Predefined status→tone mappings
 * - Extensible via statusMap prop
 * - Consistent styling via @ghatana/ui Badge
 * - Dark mode support
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Simple usage with predefined mapping
 * <StatusBadge status="active" />
 * <StatusBadge status="failed" />
 * 
 * // Custom mapping for domain-specific status
 * <StatusBadge 
 *   status="investigating"
 *   statusMap={{
 *     investigating: { tone: "warning", label: "Investigating" }
 *   }}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Reusable status badge with predefined mappings
 * @doc.layer product
 * @doc.pattern Utility Component
 */

type BadgeTone = 'neutral' | 'primary' | 'secondary' | 'success' | 'info' | 'warning' | 'danger';

interface StatusConfig {
    tone: BadgeTone;
    label?: string;
}

interface StatusBadgeProps {
    /** Status value to display */
    status: string;

    /** Custom status mappings (overrides defaults) */
    statusMap?: Record<string, StatusConfig>;

    /**
     * Visual variant (compat-only).
     * The underlying local `Badge` component doesn't implement these variants.
     */
    variant?: 'solid' | 'soft' | 'outline';

    /** Additional CSS class */
    className?: string;
}

/**
 * Default status→tone mappings for common status values
 */
const DEFAULT_STATUS_MAP: Record<string, StatusConfig> = {
    // Health/Activity statuses
    'healthy': { tone: 'success', label: 'Healthy' },
    'active': { tone: 'success', label: 'Active' },
    'running': { tone: 'info', label: 'Running' },
    'degraded': { tone: 'warning', label: 'Degraded' },
    'failed': { tone: 'danger', label: 'Failed' },
    'inactive': { tone: 'neutral', label: 'Inactive' },

    // Deployment/Model statuses
    'staged': { tone: 'info', label: 'Staged' },
    'archived': { tone: 'neutral', label: 'Archived' },
    'deployed': { tone: 'success', label: 'Deployed' },

    // Incident/Priority statuses
    'critical': { tone: 'danger', label: 'Critical' },
    'high': { tone: 'danger', label: 'High' },
    'medium': { tone: 'warning', label: 'Medium' },
    'low': { tone: 'info', label: 'Low' },

    // Workflow statuses
    'open': { tone: 'danger', label: 'Open' },
    'investigating': { tone: 'warning', label: 'Investigating' },
    'resolved': { tone: 'success', label: 'Resolved' },
    'acknowledged': { tone: 'info', label: 'Acknowledged' },
    'closed': { tone: 'neutral', label: 'Closed' },

    // HITL/Approval statuses
    'pending': { tone: 'warning', label: 'Pending' },
    'approved': { tone: 'success', label: 'Approved' },
    'rejected': { tone: 'danger', label: 'Rejected' },
    'deferred': { tone: 'neutral', label: 'Deferred' },

    // Generic positive/negative
    'success': { tone: 'success', label: 'Success' },
    'error': { tone: 'danger', label: 'Error' },
    'warning': { tone: 'warning', label: 'Warning' },
    'info': { tone: 'info', label: 'Info' },
};

/**
 * StatusBadge component - maps status strings to styled badges
 */
export function StatusBadge({
    status,
    statusMap,
    variant = 'soft',
    className
}: StatusBadgeProps) {
    // Merge custom mappings with defaults
    const effectiveMap = { ...DEFAULT_STATUS_MAP, ...statusMap };

    // Get config for this status (fallback to neutral if not found)
    const config = effectiveMap[status.toLowerCase()] || {
        tone: 'neutral' as const,
        label: status
    };

    const badgeVariant = ((): React.ComponentProps<typeof Badge>['variant'] => {
        switch (config.tone) {
            case 'success':
            case 'warning':
            case 'danger':
            case 'neutral':
            case 'primary':
                return config.tone;
            case 'secondary':
            case 'info':
            default:
                return 'primary';
        }
    })();

    return (
        <Badge
            variant={badgeVariant}
            className={className}
            data-variant={variant}
        >
            {config.label || status}
        </Badge>
    );
}

export default StatusBadge;
