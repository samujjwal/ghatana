/**
 * Auto-Save Indicator Component
 * 
 * Displays auto-save status with visual feedback.
 * Shows: Saving, Saved, Error states.
 * 
 * @doc.type component
 * @doc.purpose Auto-save status indicator
 * @doc.layer product
 * @doc.pattern Presentation Component
 */

import React, { useEffect, useState } from 'react';
import {
  Box,
  Chip,
  Tooltip,
  Spinner as CircularProgress,
} from '@ghatana/ui';
import { CheckCircle, AlertCircle as ErrorIcon, CloudCheck as CloudDone, CloudOff } from 'lucide-react';

/**
 * Auto-save status
 */
export type AutoSaveStatus = 'idle' | 'saving' | 'saved' | 'error' | 'disabled';

/**
 * Props for AutoSaveIndicator
 */
export interface AutoSaveIndicatorProps {
    status: AutoSaveStatus;
    lastSaveTime?: number;
    errorMessage?: string;
    onRetry?: () => void;
    className?: string;
}

/**
 * Format time since last save
 */
function formatTimeSince(timestamp: number): string {
    const now = Date.now();
    const diff = now - timestamp;

    if (diff < 5000) {
        return 'Just now';
    }

    if (diff < 60000) {
        return `${Math.floor(diff / 1000)}s ago`;
    }

    if (diff < 3600000) {
        return `${Math.floor(diff / 60000)}m ago`;
    }

    if (diff < 86400000) {
        return `${Math.floor(diff / 3600000)}h ago`;
    }

    return new Date(timestamp).toLocaleDateString();
}

/**
 * Get status configuration
 */
function getStatusConfig(status: AutoSaveStatus, lastSaveTime?: number) {
    switch (status) {
        case 'saving':
            return {
                icon: <CircularProgress size={16} thickness={5} />,
                label: 'Saving...',
                color: 'default' as const,
                tooltip: 'Saving changes to server',
            };

        case 'saved':
            return {
                icon: <CheckCircle className="text-base" />,
                label: lastSaveTime ? `Saved ${formatTimeSince(lastSaveTime)}` : 'Saved',
                color: 'success' as const,
                tooltip: lastSaveTime
                    ? `Last saved: ${new Date(lastSaveTime).toLocaleString()}`
                    : 'All changes saved',
            };

        case 'error':
            return {
                icon: <ErrorIcon className="text-base" />,
                label: 'Save failed',
                color: 'error' as const,
                tooltip: 'Failed to save changes. Click to retry.',
            };

        case 'disabled':
            return {
                icon: <CloudOff className="text-base" />,
                label: 'Auto-save off',
                color: 'default' as const,
                tooltip: 'Auto-save is disabled',
            };

        default:
            return {
                icon: <CloudDone className="text-base" />,
                label: 'Ready',
                color: 'default' as const,
                tooltip: 'Ready to save',
            };
    }
}

/**
 * Auto-Save Indicator Component
 * 
 * Usage:
 * ```tsx
 * <AutoSaveIndicator 
 *     status="saving" 
 *     lastSaveTime={Date.now()} 
 * />
 * ```
 */
export const AutoSaveIndicator: React.FC<AutoSaveIndicatorProps> = ({
    status,
    lastSaveTime,
    errorMessage,
    onRetry,
    className = '',
}) => {
    const [currentTime, setCurrentTime] = useState(Date.now());

    // Update time every 10 seconds for relative timestamps
    useEffect(() => {
        const interval = setInterval(() => {
            setCurrentTime(Date.now());
        }, 10000);

        return () => clearInterval(interval);
    }, []);

    const config = getStatusConfig(status, lastSaveTime);

    const handleClick = () => {
        if (status === 'error' && onRetry) {
            onRetry();
        }
    };

    return (
        <Tooltip title={errorMessage || config.tooltip} arrow>
            <Box
                className={className}
                 style={{ cursor: status === 'error' && onRetry ? 'pointer' : 'default', transition: 'background-color 0.2s' }}
                onClick={handleClick}
            >
                <Chip
                    icon={config.icon}
                    label={config.label}
                    color={config.color}
                    size="small"
                    variant="outlined"
                    className="text-xs h-[24px] ml-[6px]"
                />
            </Box>
        </Tooltip>
    );
};

/**
 * Compact version showing only icon
 */
export const AutoSaveIndicatorCompact: React.FC<AutoSaveIndicatorProps> = ({
    status,
    lastSaveTime,
    errorMessage,
    onRetry,
    className = '',
}) => {
    const config = getStatusConfig(status, lastSaveTime);

    const handleClick = () => {
        if (status === 'error' && onRetry) {
            onRetry();
        }
    };

    return (
        <Tooltip title={errorMessage || config.tooltip} arrow>
            <Box
                className={className}
                 style={{ backgroundColor: status === 'error' ? 'error.light' : 'transparent', cursor: status === 'error' && onRetry ? 'pointer' : 'default' }} onClick={handleClick}
            >
                {config.icon}
            </Box>
        </Tooltip>
    );
};

export default AutoSaveIndicator;
