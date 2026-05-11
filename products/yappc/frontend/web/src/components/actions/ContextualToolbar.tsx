/**
 * Contextual Toolbar Component
 * 
 * A dynamic toolbar that shows context-appropriate actions based on
 * current phase, selection, and capabilities.
 * 
 * @doc.type component
 * @doc.purpose Context-aware action toolbar
 * @doc.layer product
 * @doc.pattern Toolbar Component
 */

import React, { useMemo, useCallback } from 'react';
import { Tooltip, Divider, MenuItem, ListItemIcon, ListItemText, IconButton, ToggleButtonGroup as ButtonGroup, Button } from '@ghatana/design-system';
import { Undo2 as UndoIcon, Redo2 as RedoIcon, Copy as CopyIcon, ClipboardPaste as PasteIcon, Trash2 as DeleteIcon, BoxSelect as SelectAllIcon, ZoomIn as ZoomInIcon, ZoomOut as ZoomOutIcon, Maximize2 as FitScreenIcon, Grid3x3 as GridIcon, Sparkles as AiIcon, Code as CodeIcon, Eye as PreviewIcon, CloudUpload as DeployIcon, MoreVertical as MoreIcon, Keyboard as KeyboardIcon, Save as SaveIcon, Plus as AddIcon, Minus as RemoveIcon, Layers as LayersIcon, AlignLeft as AlignLeftIcon, AlignCenter as AlignCenterIcon, AlignRight as AlignRightIcon, AlignStartVertical as AlignTopIcon, AlignCenterVertical as AlignMiddleIcon, AlignEndVertical as AlignBottomIcon, Lock as LockIcon, LockOpen as UnlockIcon, Users as GroupIcon, GitFork as UngroupIcon } from 'lucide-react';

import { useActions, type ActionDefinition, type ActionCategory, type ActionState } from '../../services/ActionRegistry';
import { useSelectionContext, useCapabilitiesContext } from '../../context/WorkflowContextProvider';
import { LifecyclePhase, PHASE_LABELS } from '../../types/lifecycle';
import { useTranslation } from '@ghatana/i18n';

// ============================================================================
// Types
// ============================================================================

export interface ContextualToolbarProps {
    /** Current action state */
    state: ActionState;
    /** Show phase indicator */
    showPhaseIndicator?: boolean;
    /** Compact mode */
    compact?: boolean;
    /** Additional class names */
    className?: string;
    /** Custom action handlers */
    handlers?: {
        undo?: () => void;
        redo?: () => void;
        save?: () => void;
        copy?: () => void;
        paste?: () => void;
        delete?: () => void;
        selectAll?: () => void;
        zoomIn?: () => void;
        zoomOut?: () => void;
        zoomFit?: () => void;
        toggleGrid?: () => void;
        addNode?: () => void;
        removeNode?: () => void;
        group?: () => void;
        ungroup?: () => void;
        lock?: () => void;
        unlock?: () => void;
        alignLeft?: () => void;
        alignCenter?: () => void;
        alignRight?: () => void;
        alignTop?: () => void;
        alignMiddle?: () => void;
        alignBottom?: () => void;
        openAI?: () => void;
        generate?: () => void;
        preview?: () => void;
        deploy?: () => void;
    };
}

interface ToolbarButtonProps {
    icon: React.ReactNode;
    label: string;
    shortcut?: string;
    onClick: () => void;
    disabled?: boolean;
    active?: boolean;
    variant?: 'default' | 'primary' | 'danger';
}

interface ToolbarSectionProps {
    title?: string;
    children: React.ReactNode;
}

// ============================================================================
// Icon Map
// ============================================================================

const ICON_MAP: Record<string, React.ElementType> = {
    undo: UndoIcon,
    redo: RedoIcon,
    save: SaveIcon,
    copy: CopyIcon,
    paste: PasteIcon,
    delete: DeleteIcon,
    selectAll: SelectAllIcon,
    zoomIn: ZoomInIcon,
    zoomOut: ZoomOutIcon,
    fitScreen: FitScreenIcon,
    grid: GridIcon,
    autoAwesome: AiIcon,
    code: CodeIcon,
    visibility: PreviewIcon,
    cloudUpload: DeployIcon,
    keyboard: KeyboardIcon,
    add: AddIcon,
    remove: RemoveIcon,
    layers: LayersIcon,
    group: GroupIcon,
    ungroup: UngroupIcon,
    lock: LockIcon,
    unlock: UnlockIcon,
    alignLeft: AlignLeftIcon,
    alignCenter: AlignCenterIcon,
    alignRight: AlignRightIcon,
    alignTop: AlignTopIcon,
    alignMiddle: AlignMiddleIcon,
    alignBottom: AlignBottomIcon,
};

// ============================================================================
// Sub-components
// ============================================================================

/**
 * Individual toolbar button
 */
const ToolbarButton: React.FC<ToolbarButtonProps> = ({
    icon,
    label,
    shortcut,
    onClick,
    disabled = false,
    active = false,
    variant = 'default',
}) => {
    const tooltipContent = shortcut ? `${label} (${shortcut})` : label;

    const variantClasses = {
        default: active
            ? 'bg-info-bg dark:bg-info-bg/50 text-info-color dark:text-info-color'
            : 'text-grey-600 dark:text-grey-400 hover:bg-grey-100 dark:hover:bg-grey-700',
        primary: 'bg-info-bg text-white hover:bg-primary',
        danger: 'text-destructive dark:text-destructive hover:bg-destructive-bg dark:hover:bg-destructive-bg/30',
    };

    return (
        <Tooltip title={tooltipContent} placement="bottom">
            <span>
                <IconButton
                    onClick={onClick}
                    disabled={disabled}
                    size="sm"
                    className={`
                        p-1.5 rounded-lg transition-colors
                        ${variantClasses[variant]}
                        ${disabled ? 'opacity-50 cursor-not-allowed' : ''}
                    `}
                    aria-label={label}
                >
                    {icon}
                </IconButton>
            </span>
        </Tooltip>
    );
};

/**
 * Toolbar section with optional title
 */
const ToolbarSection: React.FC<ToolbarSectionProps> = ({ title, children }) => {
    return (
        <div className="flex items-center gap-0.5">
            {title && (
                <span className="text-xs text-grey-400 dark:text-grey-500 mr-1 hidden lg:inline">
                    {title}
                </span>
            )}
            {children}
        </div>
    );
};

/**
 * Phase indicator pill
 */
const PhaseIndicator: React.FC<{ phase: LifecyclePhase }> = ({ phase }) => {
    const phaseColors: Record<LifecyclePhase, string> = {
        [LifecyclePhase.INTENT]: 'bg-info-bg text-info-color dark:bg-info-bg/50 dark:text-info-color',
        [LifecyclePhase.CONTEXT]: 'bg-violet-100 text-violet-700 dark:bg-violet-900/50 dark:text-violet-300',
        [LifecyclePhase.PLAN]: 'bg-warning-bg text-warning-color dark:bg-warning-bg/50 dark:text-warning-color',
        [LifecyclePhase.EXECUTE]: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/50 dark:text-emerald-300',
        [LifecyclePhase.VERIFY]: 'bg-info-bg text-info-color dark:bg-info-bg/50 dark:text-info-color',
        [LifecyclePhase.OBSERVE]: 'bg-info-bg text-info-color dark:bg-info-bg/50 dark:text-info-color',
        [LifecyclePhase.LEARN]: 'bg-info-bg text-info-color dark:bg-info-bg/50 dark:text-info-color',
        [LifecyclePhase.INSTITUTIONALIZE]: 'bg-surface-muted text-fg dark:bg-surface/50 dark:text-fg-muted',
    };

    return (
        <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${phaseColors[phase]}`}>
            {PHASE_LABELS[phase]}
        </span>
    );
};

// ============================================================================
// Main Component
// ============================================================================

/**
 * Contextual Toolbar
 * 
 * A context-aware toolbar that displays relevant actions based on
 * current phase, selection, and capabilities.
 * 
 * @example
 * ```tsx
 * <ContextualToolbar 
 *   state={actionState}
 *   showPhaseIndicator={true}
 *   handlers={{
 *     undo: handleUndo,
 *     redo: handleRedo,
 *     // ...
 *   }}
 * />
 * ```
 */
export const ContextualToolbar: React.FC<ContextualToolbarProps> = ({
    state,
    showPhaseIndicator = true,
    compact = false,
    className = '',
    handlers = {},
}) => {
    const { actions, execute, formatShortcut } = useActions(state);
    const { t } = useTranslation('common');
    const { elements, type: selectionType } = useSelectionContext();
    const capabilities = useCapabilitiesContext();

    const [moreMenuAnchor, setMoreMenuAnchor] = React.useState<null | HTMLElement>(null);

    // Group actions for display
    const primaryActions = useMemo(() =>
        actions.filter(a => ['edit', 'view', 'canvas'].includes(a.category) && (a.priority || 0) >= 50),
        [actions]
    );

    const secondaryActions = useMemo(() =>
        actions.filter(a => ['ai', 'deploy', 'project'].includes(a.category)),
        [actions]
    );

    const overflowActions = useMemo(() =>
        actions.filter(a => (a.priority || 0) < 50 || ['help'].includes(a.category)),
        [actions]
    );

    // Handlers
    const handleMoreClick = useCallback((event: React.MouseEvent<HTMLElement>) => {
        setMoreMenuAnchor(event.currentTarget);
    }, []);

    const handleMoreClose = useCallback(() => {
        setMoreMenuAnchor(null);
    }, []);

    const handleActionClick = useCallback((actionId: string) => {
        execute(actionId);
    }, [execute]);

    // Render icon from action
    const renderIcon = useCallback((iconName?: string) => {
        if (!iconName) return null;
        const IconComponent = ICON_MAP[iconName];
        return IconComponent ? <IconComponent className="w-4 h-4" /> : null;
    }, []);

    // Selection info display
    const selectionInfo = useMemo(() => {
        if (!state.hasSelection) return null;
        const count = elements.length;
        const type = selectionType === 'node' ? 'node' : selectionType === 'edge' ? 'edge' : 'item';
        return `${count} ${type}${count > 1 ? 's' : ''} selected`;
    }, [state.hasSelection, elements, selectionType]);

    return (
        <div
            className={`
                flex items-center gap-2 px-3 py-2
                bg-white dark:bg-grey-800
                border-b border-grey-200 dark:border-grey-700
                ${className}
            `}
            role="toolbar"
            aria-label={t('contextualToolbar.canvasToolbar')}
        >
            {/* Phase Indicator */}
            {showPhaseIndicator && state.currentPhase && (
                <>
                    <PhaseIndicator phase={state.currentPhase} />
                    <Divider orientation="vertical" className="mx-1 self-stretch" />
                </>
            )}

            {/* History Actions */}
            <ToolbarSection>
                <ToolbarButton
                    icon={<UndoIcon className="w-4 h-4" />}
                    label="Undo"
                    shortcut="⌘Z"
                    onClick={() => handlers.undo?.() || handleActionClick('edit.undo')}
                    disabled={!state.canUndo}
                />
                <ToolbarButton
                    icon={<RedoIcon className="w-4 h-4" />}
                    label="Redo"
                    shortcut="⌘⇧Z"
                    onClick={() => handlers.redo?.() || handleActionClick('edit.redo')}
                    disabled={!state.canRedo}
                />
            </ToolbarSection>

            <Divider orientation="vertical" className="mx-1 self-stretch" />

            {/* Selection Actions - Only show when there's a selection */}
            {state.hasSelection && (
                <>
                    <ToolbarSection title={compact ? undefined : 'Selection'}>
                        <ToolbarButton
                            icon={<CopyIcon className="w-4 h-4" />}
                            label="Copy"
                            shortcut="⌘C"
                            onClick={() => handlers.copy?.()}
                        />
                        <ToolbarButton
                            icon={<DeleteIcon className="w-4 h-4" />}
                            label="Delete"
                            shortcut="⌫"
                            onClick={() => handlers.delete?.()}
                            variant="danger"
                        />
                        {elements.length > 1 && (
                            <ToolbarButton
                                icon={<GroupIcon className="w-4 h-4" />}
                                label="Group"
                                shortcut="⌘G"
                                onClick={() => handlers.group?.()}
                            />
                        )}
                        {selectionType === 'group' && (
                            <ToolbarButton
                                icon={<UngroupIcon className="w-4 h-4" />}
                                label="Ungroup"
                                shortcut="⌘⇧G"
                                onClick={() => handlers.ungroup?.()}
                            />
                        )}
                    </ToolbarSection>

                    {/* Alignment Actions - Only for multiple selection */}
                    {elements.length > 1 && !compact && (
                        <>
                            <Divider orientation="vertical" className="mx-1 self-stretch" />
                            <ToolbarSection title="Align">
                                <ToolbarButton
                                    icon={<AlignLeftIcon className="w-4 h-4" />}
                                    label="Align Left"
                                    onClick={() => handlers.alignLeft?.()}
                                />
                                <ToolbarButton
                                    icon={<AlignCenterIcon className="w-4 h-4" />}
                                    label="Align Center"
                                    onClick={() => handlers.alignCenter?.()}
                                />
                                <ToolbarButton
                                    icon={<AlignRightIcon className="w-4 h-4" />}
                                    label="Align Right"
                                    onClick={() => handlers.alignRight?.()}
                                />
                                <ToolbarButton
                                    icon={<AlignTopIcon className="w-4 h-4" />}
                                    label="Align Top"
                                    onClick={() => handlers.alignTop?.()}
                                />
                                <ToolbarButton
                                    icon={<AlignMiddleIcon className="w-4 h-4" />}
                                    label="Align Middle"
                                    onClick={() => handlers.alignMiddle?.()}
                                />
                                <ToolbarButton
                                    icon={<AlignBottomIcon className="w-4 h-4" />}
                                    label="Align Bottom"
                                    onClick={() => handlers.alignBottom?.()}
                                />
                            </ToolbarSection>
                        </>
                    )}

                    <Divider orientation="vertical" className="mx-1 self-stretch" />
                </>
            )}

            {/* Paste - Always show in canvas */}
            {!state.hasSelection && state.currentRoute.includes('/canvas') && (
                <ToolbarButton
                    icon={<PasteIcon className="w-4 h-4" />}
                    label="Paste"
                    shortcut="⌘V"
                    onClick={() => handlers.paste?.()}
                />
            )}

            {/* View Actions */}
            <ToolbarSection title={compact ? undefined : 'View'}>
                <ToolbarButton
                    icon={<ZoomOutIcon className="w-4 h-4" />}
                    label="Zoom Out"
                    shortcut="⌘-"
                    onClick={() => handlers.zoomOut?.()}
                />
                <ToolbarButton
                    icon={<ZoomInIcon className="w-4 h-4" />}
                    label="Zoom In"
                    shortcut="⌘+"
                    onClick={() => handlers.zoomIn?.()}
                />
                <ToolbarButton
                    icon={<FitScreenIcon className="w-4 h-4" />}
                    label="Fit to View"
                    shortcut="⌘0"
                    onClick={() => handlers.zoomFit?.()}
                />
                <ToolbarButton
                    icon={<GridIcon className="w-4 h-4" />}
                    label="Toggle Grid"
                    shortcut="⌘'"
                    onClick={() => handlers.toggleGrid?.()}
                />
            </ToolbarSection>

            {/* Spacer */}
            <div className="flex-1" />

            {/* Selection Info */}
            {selectionInfo && (
                <span className="text-xs text-grey-500 dark:text-grey-400 mr-2">
                    {selectionInfo}
                </span>
            )}

            {/* Assistant Action */}
            <Button
                variant="outlined"
                size="sm"
                startIcon={<AiIcon className="w-4 h-4" />}
                onClick={() => handlers.openAI?.() || handleActionClick('ai.open')}
                className="text-info-color border-info-border hover:bg-info-bg dark:text-info-color dark:border-info-border dark:hover:bg-info-bg/30"
            >
                Assist
            </Button>

            {/* Phase-specific Actions */}
            {state.currentPhase && (state.currentPhase === LifecyclePhase.CONTEXT || state.currentPhase === LifecyclePhase.PLAN) && capabilities.canGenerate && (
                <Button
                    variant="solid"
                    size="sm"
                    startIcon={<CodeIcon className="w-4 h-4" />}
                    onClick={() => handlers.generate?.() || handleActionClick('canvas.generate')}
                    className="bg-emerald-600 hover:bg-emerald-700"
                >
                    Generate
                </Button>
            )}

            {state.currentPhase === LifecyclePhase.EXECUTE && capabilities.canDeploy && (
                <>
                    <Button
                        variant="outlined"
                        size="sm"
                        startIcon={<PreviewIcon className="w-4 h-4" />}
                        onClick={() => handlers.preview?.() || handleActionClick('deploy.preview')}
                    >
                        Preview
                    </Button>
                    <Button
                        variant="solid"
                        size="sm"
                        startIcon={<DeployIcon className="w-4 h-4" />}
                        onClick={() => handlers.deploy?.() || handleActionClick('deploy.deploy')}
                        className="bg-primary hover:bg-info-bg"
                    >
                        Deploy
                    </Button>
                </>
            )}

            {/* More Menu */}
            {overflowActions.length > 0 && (
                <>
                    <IconButton
                        onClick={handleMoreClick}
                        size="sm"
                        aria-label={t('contextualToolbar.moreActions')}
                        aria-haspopup="true"
                    >
                        <MoreIcon className="w-4 h-4" />
                    </IconButton>
                    {moreMenuAnchor ? (
                    <div className="relative">
                    <div className="absolute right-0 top-8 z-20 min-w-[240px] rounded-md border border-border bg-white p-1 shadow-lg dark:border-border dark:bg-surface">
                        {overflowActions.map((action: ActionDefinition) => (
                            <MenuItem
                                key={action.id}
                                onClick={() => {
                                    handleActionClick(action.id);
                                    handleMoreClose();
                                }}
                            >
                                <ListItemIcon>
                                    {renderIcon(action.icon)}
                                </ListItemIcon>
                                <ListItemText
                                    primary={action.label}
                                    secondary={action.shortcut ? formatShortcut(action.shortcut) : undefined}
                                />
                            </MenuItem>
                        ))}
                    </div>
                    {React.createElement('button', {
                        type: 'button',
                        className: 'fixed inset-0 cursor-default',
                        'aria-label': 'Close overflow actions',
                        onClick: handleMoreClose,
                    })}
                    </div>
                    ) : null}
                </>
            )}
        </div>
    );
};

export default ContextualToolbar;
