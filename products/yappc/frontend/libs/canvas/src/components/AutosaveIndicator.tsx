/**
 * Autosave Indicator Component
 * Feature 1.4: Document Management - UI Component
 * 
 * Displays autosave status with toast notifications.
 * Integrates with historyManager autosave utilities.
 */

import { Check as CheckIcon, CloudCheck as SavedIcon, Cloud as SavingIcon, AlertCircle as ErrorIcon, Clock as PendingIcon } from 'lucide-react';
import { Typography, Spinner as CircularProgress, Toast as Snackbar, Alert, Stack, Chip } from '@ghatana/ui';
import React from 'react';

import type { AutosaveState } from '../history/historyManager';

/**
 *
 */
export interface AutosaveIndicatorProps {
    autosaveState: AutosaveState;
    onClose?: () => void;
    showToast?: boolean;
    position?: 'top-right' | 'top-left' | 'bottom-right' | 'bottom-left';
}

/**
 * Autosave Indicator
 * 
 * Visual indicator showing current autosave status.
 * Features:
 * - Shows saving/saved/error states
 * - Displays time since last save
 * - Toast notifications for save events
 * - Inline status badge
 */
export function AutosaveIndicator({
    autosaveState,
    onClose,
    showToast = true,
    position = 'bottom-right',
}: AutosaveIndicatorProps) {
    const [showSaveToast, setShowSaveToast] = React.useState(false);
    const [lastSaveMessage, setLastSaveMessage] = React.useState('');

    // Track when autosave completes to show toast
    const prevIsPending = React.useRef(autosaveState.isPending);
    React.useEffect(() => {
        if (prevIsPending.current && !autosaveState.isPending && autosaveState.lastSaved) {
            // Save just completed
            setLastSaveMessage(getTimeSinceLastSave());
            setShowSaveToast(true);
        }
        prevIsPending.current = autosaveState.isPending;
    }, [autosaveState.isPending, autosaveState.lastSaved]);

    const getTimeSinceLastSave = () => {
        if (!autosaveState.lastSaved) return 'Never saved';

        const now = Date.now();
        const diff = now - autosaveState.lastSaved;

        if (diff < 1000) return 'Just now';
        if (diff < 60000) return `${Math.floor(diff / 1000)}s ago`;
        if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
        return `${Math.floor(diff / 3600000)}h ago`;
    };

    const getStatusIcon = () => {
        if (autosaveState.isPending) {
            return <SavingIcon className="text-base" />;
        }
        if (autosaveState.isDirty) {
            return <PendingIcon className="text-base" />;
        }
        if (!autosaveState.enabled) {
            return <ErrorIcon className="text-base" />;
        }
        return <SavedIcon className="text-base" />;
    };

    const getStatusColor = () => {
        if (autosaveState.isPending) return 'info';
        if (autosaveState.isDirty) return 'warning';
        if (!autosaveState.enabled) return 'error';
        return 'success';
    };

    const getStatusText = () => {
        if (autosaveState.isPending) return 'Saving...';
        if (autosaveState.isDirty) return 'Unsaved changes';
        if (!autosaveState.enabled) return 'Autosave disabled';
        return 'All changes saved';
    };

    const handleToastClose = () => {
        setShowSaveToast(false);
        onClose?.();
    };

    return (
        <>
            {/* Inline Status Badge */}
            <Chip
                icon={
                    autosaveState.isPending ? (
                        <CircularProgress size={12} className="ml-2" />
                    ) : (
                        getStatusIcon()
                    )
                }
                label={
                    <Stack direction="row" spacing={0.5} alignItems="center">
                        <Typography as="span" className="text-xs text-gray-500">{getStatusText()}</Typography>
                        {autosaveState.lastSaved && !autosaveState.isPending && (
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                • {getTimeSinceLastSave()}
                            </Typography>
                        )}
                    </Stack>
                }
                size="sm"
                color={getStatusColor()}
                variant="outlined"
                className="[&_.MuiChip-label]:flex [&_.MuiChip-label]:items-center"
                data-testid="autosave-indicator"
            />

            {/* Toast Notification */}
            {showToast && (
                <Snackbar
                    open={showSaveToast}
                    autoHideDuration={3000}
                    onClose={handleToastClose}
                    anchorOrigin={{
                        vertical: position.startsWith('top') ? 'top' : 'bottom',
                        horizontal: position.endsWith('right') ? 'right' : 'left',
                    }}
                    data-testid="autosave-toast"
                >
                    <Alert
                        onClose={handleToastClose}
                        severity="success"
                        icon={<CheckIcon size={undefined} />}
                        className="w-full"
                    >
                        <Stack spacing={0.5}>
                            <Typography as="p" className="text-sm" fontWeight="medium">
                                Changes saved
                            </Typography>
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                Last saved {lastSaveMessage}
                            </Typography>
                        </Stack>
                    </Alert>
                </Snackbar>
            )}
        </>
    );
}

/**
 * Simple inline autosave status text (no toast)
 */
export function AutosaveStatus({ autosaveState }: { autosaveState: AutosaveState }) {
    const getTimeSinceLastSave = () => {
        if (!autosaveState.lastSaved) return '';

        const now = Date.now();
        const diff = now - autosaveState.lastSaved;

        if (diff < 1000) return 'just now';
        if (diff < 60000) return `${Math.floor(diff / 1000)}s ago`;
        if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
        return `${Math.floor(diff / 3600000)}h ago`;
    };

    return (
        <Typography
            as="span" className="text-xs text-gray-500"
            color="text.secondary"
            className="flex items-center gap-1"
            data-testid="autosave-status-text"
        >
            {autosaveState.isPending && (
                <>
                    <CircularProgress size={10} />
                    Saving...
                </>
            )}
            {!autosaveState.isPending && autosaveState.isDirty && (
                <>
                    <PendingIcon className="text-xs" />
                    Unsaved changes
                </>
            )}
            {!autosaveState.isPending && !autosaveState.isDirty && autosaveState.lastSaved && (
                <>
                    <CheckIcon className="text-xs" />
                    Saved {getTimeSinceLastSave()}
                </>
            )}
        </Typography>
    );
}
