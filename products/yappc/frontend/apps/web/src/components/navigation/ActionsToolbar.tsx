/**
 * Actions Toolbar Component
 * 
 * Context-aware action buttons that adapt based on current view:
 * - Global actions (new project, search, notifications)
 * - Project actions (share, settings, export)
 * - Canvas actions (undo/redo, zoom, export, view modes)
 * - Responsive hiding for smaller screens
 * 
 * @doc.type component
 * @doc.purpose Context-specific action buttons
 * @doc.layer components
 */

import React from 'react';
import { Plus as Add, Search, Bell as Notifications, Share2 as Share, Settings, Download as FileDownload, Undo2 as Undo, Redo2 as Redo, ZoomIn, ZoomOut, Grid3x3 as GridOn, MoreVertical as MoreVert } from 'lucide-react';
import { IconButton, Menu, MenuItem, ListItemIcon, ListItemText, Divider, Tooltip } from '@ghatana/ui';
import { Button, Badge } from '../design-system';
import { cn } from '../../lib/utils';

export type ActionContext = 'global' | 'project' | 'canvas';

export interface Action {
    id: string;
    label: string;
    icon: React.ComponentType;
    onClick: () => void;
    disabled?: boolean;
    tooltip?: string;
    badge?: number;
    shortcut?: string;
    divider?: boolean;
}

export interface ActionsToolbarProps {
    /** Current context */
    context: ActionContext;
    /** Global actions (always visible) */
    globalActions?: Action[];
    /** Context-specific actions */
    contextActions?: Action[];
    /** Show action labels on desktop */
    showLabels?: boolean;
    /** Number of unread notifications */
    notificationCount?: number;
    /** Callback for new project/item */
    onNew?: () => void;
    /** Callback for search */
    onSearch?: () => void;
    /** Callback for notifications */
    onNotifications?: () => void;
    /** Additional CSS classes */
    className?: string;
}

/**
 * Actions Toolbar Component
 */
export function ActionsToolbar({
    globalActions = [],
    contextActions = [],
    showLabels = false,
    notificationCount = 0,
    onNew,
    onSearch,
    onNotifications,
    className,
}: ActionsToolbarProps) {
    const [moreMenuAnchor, setMoreMenuAnchor] = React.useState<null | HTMLElement>(null);

    // Show more actions inline on desktop to match visual guide density
    const inlineActions = contextActions.slice(0, 6);
    const overflowActions = contextActions.slice(6);

    const handleMoreClick = (event: React.MouseEvent<HTMLButtonElement>) => {
        setMoreMenuAnchor(event.currentTarget);
    };

    const handleMoreClose = () => {
        setMoreMenuAnchor(null);
    };

    const handleActionClick = (action: Action) => {
        action.onClick();
        handleMoreClose();
    };

    return (
        <div className={cn('flex items-center gap-2', className)} role="toolbar" aria-label="Actions toolbar">
            {/* Context Actions */}
            {inlineActions.length > 0 && (
                <div className="hidden md:flex items-center gap-1">
                    {inlineActions.map((action) => {
                        const ActionIcon = action.icon;
                        const button = (
                            <Button
                                key={action.id}
                                variant="ghost"
                                size="sm"
                                icon={<ActionIcon />}
                                iconPosition={showLabels ? 'left' : undefined}
                                onClick={action.onClick}
                                disabled={action.disabled}
                                badge={action.badge}
                                aria-label={action.label}
                            >
                                {showLabels && action.label}
                            </Button>
                        );

                        return action.tooltip ? (
                            <Tooltip key={action.id} title={action.tooltip} arrow>
                                <span>{button}</span>
                            </Tooltip>
                        ) : (
                            button
                        );
                    })}
                </div>
            )}

            {/* Overflow Menu */}
            {(overflowActions.length > 0 || contextActions.length > 0) && (
                <>
                    <Tooltip title="More actions" arrow>
                        <IconButton
                            size="sm"
                            onClick={handleMoreClick}
                            className="md:hidden"
                            aria-label="More actions"
                        >
                            <MoreVert />
                        </IconButton>
                    </Tooltip>

                    {overflowActions.length > 0 && (
                        <Tooltip title="More actions" arrow>
                            <IconButton
                                size="sm"
                                onClick={handleMoreClick}
                                className="hidden md:inline-flex"
                                aria-label="More actions"
                            >
                                <MoreVert />
                            </IconButton>
                        </Tooltip>
                    )}

                    <Menu
                        anchorEl={moreMenuAnchor}
                        open={Boolean(moreMenuAnchor)}
                        onClose={handleMoreClose}
                        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
                    >
                        {/* Mobile: Show all context actions */}
                        <div className="md:hidden">
                            {contextActions.map((action, index) => {
                                const ActionIcon = action.icon;
                                return (
                                    <React.Fragment key={action.id}>
                                        {action.divider && index > 0 && <Divider />}
                                        <MenuItem
                                            onClick={() => handleActionClick(action)}
                                            disabled={action.disabled}
                                        >
                                            <ListItemIcon>
                                                <span style={{ fontSize: '20px' }}>{React.createElement(ActionIcon)}</span>
                                            </ListItemIcon>
                                            <ListItemText
                                                primary={action.label}
                                                secondary={action.shortcut}
                                            />
                                            {action.badge !== undefined && action.badge > 0 && (
                                                <Badge variant="error" label={action.badge.toString()} size="sm" className="ml-2" />
                                            )}
                                        </MenuItem>
                                    </React.Fragment>
                                );
                            })}
                        </div>

                        {/* Desktop: Show only overflow actions */}
                        <div className="hidden md:block">
                            {overflowActions.map((action, index) => {
                                const ActionIcon = action.icon;
                                return (
                                    <React.Fragment key={action.id}>
                                        {action.divider && index > 0 && <Divider />}
                                        <MenuItem
                                            onClick={() => handleActionClick(action)}
                                            disabled={action.disabled}
                                        >
                                            <ListItemIcon>
                                                <span style={{ fontSize: '20px' }}>{React.createElement(ActionIcon)}</span>
                                            </ListItemIcon>
                                            <ListItemText
                                                primary={action.label}
                                                secondary={action.shortcut}
                                            />
                                            {action.badge !== undefined && action.badge > 0 && (
                                                <Badge variant="error" label={action.badge.toString()} size="sm" className="ml-2" />
                                            )}
                                        </MenuItem>
                                    </React.Fragment>
                                );
                            })}
                        </div>
                    </Menu>
                </>
            )}

            {/* Divider */}
            {(inlineActions.length > 0 || overflowActions.length > 0) && (
                <div className="w-px h-6 bg-border-subtle mx-1" role="separator" />
            )}

            {/* Global Actions */}
            <div className="flex items-center gap-1">
                {/* New Button */}
                {onNew && (
                    <Tooltip title="Create new" arrow>
                        <Button
                            variant="ghost"
                            size="sm"
                            icon={<Add />}
                            onClick={onNew}
                            aria-label="Create new"
                        />
                    </Tooltip>
                )}

                {/* Search Button */}
                {onSearch && (
                    <Tooltip title="Search" arrow>
                        <Button
                            variant="ghost"
                            size="sm"
                            icon={<Search />}
                            onClick={onSearch}
                            aria-label="Search"
                        />
                    </Tooltip>
                )}

                {/* Notifications Button */}
                {onNotifications && (
                    <Tooltip title="Notifications" arrow>
                        <Button
                            variant="ghost"
                            size="sm"
                            icon={<Notifications />}
                            onClick={onNotifications}
                            badge={notificationCount}
                            aria-label={`Notifications${notificationCount > 0 ? ` (${notificationCount} unread)` : ''}`}
                        />
                    </Tooltip>
                )}
            </div>

            {/* Global Actions from Props */}
            {globalActions.length > 0 && (
                <div className="hidden lg:flex items-center gap-1">
                    {globalActions.map((action) => {
                        const ActionIcon = action.icon;
                        const button = (
                            <Button
                                key={action.id}
                                variant="ghost"
                                size="sm"
                                icon={<ActionIcon />}
                                iconPosition={showLabels ? 'left' : undefined}
                                onClick={action.onClick}
                                disabled={action.disabled}
                                badge={action.badge}
                                aria-label={action.label}
                            >
                                {showLabels && action.label}
                            </Button>
                        );

                        return action.tooltip ? (
                            <Tooltip key={action.id} title={action.tooltip} arrow>
                                <span>{button}</span>
                            </Tooltip>
                        ) : (
                            button
                        );
                    })}
                </div>
            )}
        </div>
    );
}

/**
 * Canvas-specific action helpers
 */
export const canvasActions = {
    undo: (onClick: () => void, disabled = false): Action => ({
        id: 'undo',
        label: 'Undo',
        icon: Undo,
        onClick,
        disabled,
        tooltip: 'Undo',
        shortcut: '⌘Z',
    }),

    redo: (onClick: () => void, disabled = false): Action => ({
        id: 'redo',
        label: 'Redo',
        icon: Redo,
        onClick,
        disabled,
        tooltip: 'Redo',
        shortcut: '⌘⇧Z',
    }),

    zoomIn: (onClick: () => void): Action => ({
        id: 'zoom-in',
        label: 'Zoom In',
        icon: ZoomIn,
        onClick,
        tooltip: 'Zoom in',
        shortcut: '⌘+',
    }),

    zoomOut: (onClick: () => void): Action => ({
        id: 'zoom-out',
        label: 'Zoom Out',
        icon: ZoomOut,
        onClick,
        tooltip: 'Zoom out',
        shortcut: '⌘-',
    }),

    toggleGrid: (onClick: () => void, active = false): Action => ({
        id: 'toggle-grid',
        label: active ? 'Hide Grid' : 'Show Grid',
        icon: GridOn,
        onClick,
        tooltip: active ? 'Hide grid' : 'Show grid',
        shortcut: '⌘\'',
    }),

    export: (onClick: () => void): Action => ({
        id: 'export',
        label: 'Export',
        icon: FileDownload,
        onClick,
        tooltip: 'Export canvas',
        divider: true,
    }),
};

/**
 * Project-specific action helpers
 */
export const projectActions = {
    share: (onClick: () => void): Action => ({
        id: 'share',
        label: 'Share',
        icon: Share,
        onClick,
        tooltip: 'Share project',
    }),

    settings: (onClick: () => void): Action => ({
        id: 'settings',
        label: 'Settings',
        icon: Settings,
        onClick,
        tooltip: 'Project settings',
    }),

    export: (onClick: () => void): Action => ({
        id: 'export',
        label: 'Export',
        icon: FileDownload,
        onClick,
        tooltip: 'Export project',
    }),
};

export default ActionsToolbar;
