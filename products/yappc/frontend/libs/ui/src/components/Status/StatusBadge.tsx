import { CheckCircle, AlertCircle as ErrorOutline, AlertTriangle as Warning, Hourglass as HourglassEmpty, HelpCircle as Help, XCircle as Cancel, Hammer as BuildIcon, Play as RunIcon, CloudUpload as DeployIcon, Shield as SecurityIcon } from 'lucide-react';
import { Chip, Tooltip, type ChipProps } from '@ghatana/ui';
import React from 'react';

import { wrapForTooltip } from '../../utils/accessibility';

// (ChipProps imported above)


/**
 *
 */
export type StatusType =
    | 'success'
    | 'error'
    | 'warning'
    | 'pending'
    | 'unknown'
    | 'cancelled'
    | 'running';

/**
 *
 */
export type StatusCategory =
    | 'build'
    | 'test'
    | 'deploy'
    | 'security'
    | 'quality'
    | 'general';

/**
 *
 */
export interface StatusBadgeProps extends Omit<ChipProps, 'color' | 'variant' | 'size'> {
    /** The status type that determines color and icon */
    status: StatusType;

    /** Optional category for additional context and iconography */
    category?: StatusCategory;

    /** Optional tooltip text - if not provided, a default tooltip will be generated */
    tooltip?: string;

    /** Whether to show the status icon */
    showIcon?: boolean;

    /** Custom variant - defaults to 'filled' */
    variant?: 'filled' | 'outlined' | 'soft';

    /** Whether to pulse for running/pending states */
    animated?: boolean;

    /** Size of the badge */
    size?: 'small' | 'medium' | 'large';
}

const statusConfig = {
    success: {
        color: '#2e7d32',
        bgColor: '#e8f5e8',
        borderColor: '#a5d6a7',
        icon: CheckCircle,
        defaultLabel: 'Success',
    },
    error: {
        color: '#d32f2f',
        bgColor: '#fdebee',
        borderColor: '#ef9a9a',
        icon: ErrorOutline,
        defaultLabel: 'Error',
    },
    warning: {
        color: '#ed6c02',
        bgColor: '#fff4e6',
        borderColor: '#ffcc02',
        icon: Warning,
        defaultLabel: 'Warning',
    },
    pending: {
        color: '#1976d2',
        bgColor: '#e3f2fd',
        borderColor: '#90caf9',
        icon: HourglassEmpty,
        defaultLabel: 'Pending',
    },
    running: {
        color: '#1976d2',
        bgColor: '#e3f2fd',
        borderColor: '#90caf9',
        icon: RunIcon,
        defaultLabel: 'Running',
    },
    unknown: {
        color: '#757575',
        bgColor: '#f5f5f5',
        borderColor: '#e0e0e0',
        icon: Help,
        defaultLabel: 'Unknown',
    },
    cancelled: {
        color: '#757575',
        bgColor: '#f5f5f5',
        borderColor: '#e0e0e0',
        icon: Cancel,
        defaultLabel: 'Cancelled',
    },
} as const;

const categoryIcons = {
    build: BuildIcon,
    test: CheckCircle,
    deploy: DeployIcon,
    security: SecurityIcon,
    quality: CheckCircle,
    general: CheckCircle,
} as const;

/** Compute inline styles for variant + status color combination */
const getVariantStyles = (
    variant: 'filled' | 'outlined' | 'soft',
    color: string,
    bgColor: string,
    borderColor: string,
): React.CSSProperties => {
    switch (variant) {
        case 'filled':
            return {
                backgroundColor: color,
                color: '#fff',
                border: `1px solid ${color}`,
            };
        case 'outlined':
            return {
                backgroundColor: 'transparent',
                color: color,
                border: `1px solid ${color}`,
            };
        case 'soft':
            return {
                backgroundColor: bgColor,
                color: color,
                border: `1px solid ${borderColor}`,
            };
    }
};

const sizeClasses: Record<string, string> = {
    small: 'h-5 text-[0.6875rem] px-1.5 [&_svg]:size-3.5',
    medium: 'h-6 text-xs px-2 [&_svg]:size-4',
    large: 'h-7 text-[0.8125rem] px-2.5 [&_svg]:size-[18px]',
};

export const StatusBadge = React.forwardRef<HTMLDivElement, StatusBadgeProps>(
    ({
        status,
        category,
        tooltip,
        showIcon = true,
        variant = 'filled',
        animated = false,
        size = 'medium',
        label,
        ...chipProps
    }, ref) => {
    // theme is intentionally unused in this component's runtime but kept for potential styling hooks
    // const theme = useTheme();

        const config = statusConfig[status];
        const IconComponent = category && categoryIcons[category] ? categoryIcons[category] : config.icon;

    // Helper to capitalise category for labels/tooltips
    const capitalize = (s?: string) => (s ? s.charAt(0).toUpperCase() + s.slice(1) : s);

    // Generate default tooltip if not provided. If consumer explicitly passes an empty string (""),
    // we treat that as 'no tooltip' and must not attach aria-describedby.
    const defaultTooltip = `${category ? capitalize(category) : 'Status'}: ${config.defaultLabel}`;
    const tooltipText = tooltip === undefined ? defaultTooltip : tooltip; // tooltip === '' => explicit no-tooltip

        // Generate default label if not provided
        const displayLabel = label || config.defaultLabel;

        const variantStyles = getVariantStyles(variant, config.color, config.bgColor, config.borderColor);
        const chipClassName = [
            'min-w-0 font-medium transition-all duration-200',
            sizeClasses[size] || sizeClasses.medium,
            'hover:shadow focus-visible:outline-2 focus-visible:outline-blue-600 focus-visible:outline-offset-2',
            animated && (status === 'running' || status === 'pending') ? 'animate-pulse' : '',
            (chipProps as unknown).className,
        ].filter(Boolean).join(' ');

        const chipElement = (
            <Chip
                ref={ref}
                label={displayLabel}
                icon={
                    showIcon ? (
                        <IconComponent aria-hidden={true} />
                    ) : undefined
                }
                size={size === 'large' ? 'medium' : (size as 'small' | 'medium' | undefined)}
                className={chipClassName}
                style={variantStyles}
                role="status"
                aria-label={`${capitalize(category) || 'Status'}: ${displayLabel}`}
                tabIndex={chipProps.tabIndex ?? 0}
                {...chipProps}
            />
        );

        // Wrap with tooltip if tooltip text is provided and the consumer didn't explicitly opt-out via ''
        if (tooltipText !== '') {
            // Provide an aria-describedby on the wrapper so assistive tech can reference the tooltip.
            // Do NOT set a role on the wrapper here because the inner Chip already declares role="status"
            const labelString = String(displayLabel);
            const describedById = `status-badge-tooltip-${labelString.replace(/\s+/g, '-').toLowerCase()}`;
            return (
                <Tooltip title={tooltipText} arrow placement="top">
                    {wrapForTooltip(chipElement, { 'aria-describedby': describedById })}
                </Tooltip>
            );
        }

        return chipElement;
    }
);

StatusBadge.displayName = 'StatusBadge';

export default StatusBadge;
