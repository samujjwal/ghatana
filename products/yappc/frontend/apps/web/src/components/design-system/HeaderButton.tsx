/**
 * HeaderButton - Unified Button System for Top Bar
 * 
 * Consistent button styling across all header contexts.
 * Supports icons, text, badges, and shortcuts.
 * 
 * @doc.type component
 * @doc.purpose Unified header button component
 * @doc.layer components
 */

import React from 'react';
import { IconButton, Tooltip, Badge } from '@ghatana/ui';
import { cn } from '../../lib/utils';

export interface HeaderButtonProps {
    /** Button icon */
    icon?: React.ReactNode;
    /** Button label (for non-icon buttons) */
    label?: string;
    /** Tooltip text */
    tooltip?: string;
    /** Keyboard shortcut (displayed in tooltip) */
    shortcut?: string;
    /** Badge count */
    badgeCount?: number;
    /** Is button active/selected */
    active?: boolean;
    /** Is button disabled */
    disabled?: boolean;
    /** Click handler */
    onClick?: (event: React.MouseEvent<HTMLButtonElement>) => void;
    /** Additional CSS classes */
    className?: string;
    /** Button size */
    size?: 'small' | 'medium';
    /** Button variant */
    variant?: 'icon' | 'text' | 'contained';
    /** ARIA label */
    ariaLabel?: string;
}

/**
 * HeaderButton Component
 */
export function HeaderButton({
    icon,
    label,
    tooltip,
    shortcut,
    badgeCount,
    active = false,
    disabled = false,
    onClick,
    className,
    size = 'small',
    variant = 'icon',
    ariaLabel,
}: HeaderButtonProps) {
    const tooltipTitle = shortcut && tooltip
        ? `${tooltip} (${shortcut})`
        : tooltip || '';

    const buttonContent = variant === 'icon' ? (
        <IconButton
            size={size}
            disabled={disabled}
            onClick={onClick}
            aria-label={ariaLabel || tooltip || label}
            className={cn(
                'transition-colors',
                active && 'text-primary-600 bg-primary-50 dark:bg-primary-900/20',
                !active && 'text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800',
                disabled && 'opacity-50 cursor-not-allowed',
                className
            )}
            className="rounded"
        >
            {icon}
        </IconButton>
    ) : variant === 'text' ? (
        <button
            disabled={disabled}
            onClick={onClick}
            aria-label={ariaLabel || tooltip || label}
            className={cn(
                'px-3 py-1.5 rounded-md text-sm font-medium transition-colors',
                'focus:outline-none focus:ring-2 focus:ring-primary-500',
                active && 'text-primary-600 bg-primary-50 dark:bg-primary-900/20',
                !active && 'text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800',
                disabled && 'opacity-50 cursor-not-allowed',
                className
            )}
        >
            {icon && <span className="mr-1.5 inline-flex">{icon}</span>}
            {label}
        </button>
    ) : (
        <button
            disabled={disabled}
            onClick={onClick}
            aria-label={ariaLabel || tooltip || label}
            className={cn(
                'px-3 py-1.5 rounded-md text-sm font-medium transition-colors',
                'bg-primary-600 text-white hover:bg-primary-700',
                'focus:outline-none focus:ring-2 focus:ring-primary-500',
                disabled && 'opacity-50 cursor-not-allowed',
                className
            )}
        >
            {icon && <span className="mr-1.5 inline-flex">{icon}</span>}
            {label}
        </button>
    );

    const wrappedButton = badgeCount && badgeCount > 0 ? (
        <Badge
            badgeContent={badgeCount}
            tone="danger"
            max={99}
            overlap="circular"
        >
            {buttonContent}
        </Badge>
    ) : (
        buttonContent
    );

    return tooltipTitle ? (
        <Tooltip title={tooltipTitle} arrow placement="bottom">
            {wrappedButton}
        </Tooltip>
    ) : (
        wrappedButton
    );
}

export default HeaderButton;
