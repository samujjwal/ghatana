/**
 * Keyboard Shortcuts Help Modal
 * 
 * Displays all available keyboard shortcuts in a searchable, categorized modal.
 * Shows context-aware shortcuts based on current application state.
 * 
 * @doc.type component
 * @doc.purpose Keyboard shortcut reference
 * @doc.layer product
 * @doc.pattern Modal Component
 */

import { useState, useMemo, useCallback, useEffect } from 'react';
import { X as Close, Search, Keyboard, Pencil as Edit, Eye as Visibility, LayoutDashboard as Dashboard, Sparkles as AutoAwesome, Navigation, FolderOpen, CloudUpload, HelpCircle as HelpOutline } from 'lucide-react';
import { Modal, Fade, Modal as Backdrop, InputAdornment, TextField, Box, Typography } from '@ghatana/ui';

import { TRANSITIONS, RADIUS } from '../../styles/design-tokens';
import ActionRegistry, { type ActionDefinition, type ActionCategory, type ActionState } from '../../services/ActionRegistry';

// ============================================================================
// Types
// ============================================================================

export interface KeyboardShortcutsHelpProps {
    /** Whether the modal is open */
    open: boolean;
    /** Close handler */
    onClose: () => void;
    /** Current action state for context-aware display */
    actionState?: ActionState;
}

// ============================================================================
// Constants
// ============================================================================

const CATEGORY_ICONS: Record<ActionCategory, React.ReactNode> = {
    file: <FolderOpen className="w-5 h-5" />,
    edit: <Edit className="w-5 h-5" />,
    view: <Visibility className="w-5 h-5" />,
    canvas: <Dashboard className="w-5 h-5" />,
    selection: <Edit className="w-5 h-5" />,
    ai: <AutoAwesome className="w-5 h-5" />,
    navigation: <Navigation className="w-5 h-5" />,
    project: <FolderOpen className="w-5 h-5" />,
    deploy: <CloudUpload className="w-5 h-5" />,
    help: <HelpOutline className="w-5 h-5" />,
};

const CATEGORY_LABELS: Record<ActionCategory, string> = {
    file: 'File',
    edit: 'Edit',
    view: 'View',
    canvas: 'Canvas',
    selection: 'Selection',
    ai: 'AI Assistant',
    navigation: 'Navigation',
    project: 'Project',
    deploy: 'Deploy',
    help: 'Help',
};

// ============================================================================
// Component
// ============================================================================

export function KeyboardShortcutsHelp({
    open,
    onClose,
    actionState,
}: KeyboardShortcutsHelpProps) {
    const [searchQuery, setSearchQuery] = useState('');

    // Reset search when modal closes
    useEffect(() => {
        if (!open) {
            setSearchQuery('');
        }
    }, [open]);

    // Get all actions with shortcuts
    const allActions = useMemo(() => {
        return ActionRegistry.getAll().filter(action => action.shortcut);
    }, []);

    // Filter by search
    const filteredActions = useMemo(() => {
        if (!searchQuery.trim()) return allActions;

        const query = searchQuery.toLowerCase();
        return allActions.filter(action =>
            action.label.toLowerCase().includes(query) ||
            action.description?.toLowerCase().includes(query) ||
            action.shortcut?.toLowerCase().includes(query) ||
            action.category.toLowerCase().includes(query)
        );
    }, [allActions, searchQuery]);

    // Group by category
    const groupedActions = useMemo(() => {
        const groups = new Map<ActionCategory, ActionDefinition[]>();

        filteredActions.forEach(action => {
            const list = groups.get(action.category) || [];
            list.push(action);
            groups.set(action.category, list);
        });

        // Convert to array and sort by category order
        const categoryOrder: ActionCategory[] = [
            'file', 'edit', 'view', 'canvas', 'selection', 'ai', 'navigation', 'project', 'deploy', 'help'
        ];

        return categoryOrder
            .filter(cat => groups.has(cat))
            .map(cat => ({
                category: cat,
                label: CATEGORY_LABELS[cat],
                icon: CATEGORY_ICONS[cat],
                actions: groups.get(cat)!.sort((a, b) => (b.priority || 0) - (a.priority || 0)),
            }));
    }, [filteredActions]);

    // Check if action is available in current context
    const isAvailable = useCallback((action: ActionDefinition) => {
        if (!actionState) return true;
        return ActionRegistry.isAvailable(action, actionState);
    }, [actionState]);

    // Handle escape key
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape' && open) {
                onClose();
            }
        };

        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [open, onClose]);

    return (
        <Modal
            open={open}
            onClose={onClose}
            closeAfterTransition
            slots={{ backdrop: Backdrop }}
            slotProps={{
                backdrop: {
                    timeout: 300,
                    className: 'bg-black/50',
                },
            }}
        >
            <Fade in={open}>
                <Box
                    className={`
                        absolute top-1/2 left-1/2 transform -translate-x-1/2 -translate-y-1/2
                        w-[90vw] max-w-2xl max-h-[80vh]
                        bg-bg-paper border border-divider shadow-xl
                        ${RADIUS.card} overflow-hidden
                        flex flex-col
                    `}
                >
                    {/* Header */}
                    <div className="flex items-center justify-between px-6 py-4 border-b border-divider">
                        <div className="flex items-center gap-3">
                            <Keyboard className="w-6 h-6 text-primary-500" />
                            <Typography as="h6" component="h2" className="font-semibold">
                                Keyboard Shortcuts
                            </Typography>
                        </div>
                        <button
                            onClick={onClose}
                            className={`
                                p-2 ${RADIUS.button} ${TRANSITIONS.fast}
                                text-text-tertiary hover:text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800
                            `}
                            aria-label="Close"
                        >
                            <Close className="w-5 h-5" />
                        </button>
                    </div>

                    {/* Search */}
                    <div className="px-6 py-3 border-b border-divider">
                        <TextField
                            fullWidth
                            size="sm"
                            placeholder="Search shortcuts..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            InputProps={{
                                startAdornment: (
                                    <InputAdornment position="start">
                                        <Search className="w-5 h-5 text-text-tertiary" />
                                    </InputAdornment>
                                ),
                            }}
                            autoFocus
                        />
                    </div>

                    {/* Shortcuts List */}
                    <div className="flex-1 overflow-y-auto px-6 py-4">
                        {groupedActions.length === 0 ? (
                            <div className="text-center py-8 text-text-tertiary">
                                No shortcuts found matching "{searchQuery}"
                            </div>
                        ) : (
                            <div className="space-y-6">
                                {groupedActions.map(group => (
                                    <div key={group.category}>
                                        {/* Category Header */}
                                        <div className="flex items-center gap-2 mb-3 text-text-secondary">
                                            {group.icon}
                                            <Typography as="p" className="text-sm font-medium" className="font-semibold uppercase tracking-wide text-xs">
                                                {group.label}
                                            </Typography>
                                        </div>

                                        {/* Actions */}
                                        <div className="space-y-1">
                                            {group.actions.map(action => {
                                                const available = isAvailable(action);
                                                return (
                                                    <div
                                                        key={action.id}
                                                        className={`
                                                            flex items-center justify-between py-2 px-3
                                                            ${RADIUS.button}
                                                            ${available
                                                                ? 'hover:bg-grey-100 dark:hover:bg-grey-800'
                                                                : 'opacity-50'
                                                            }
                                                            ${TRANSITIONS.fast}
                                                        `}
                                                    >
                                                        <div className="flex flex-col">
                                                            <span className={`text-sm ${available ? 'text-text-primary' : 'text-text-tertiary'}`}>
                                                                {action.label}
                                                            </span>
                                                            {action.description && (
                                                                <span className="text-xs text-text-tertiary">
                                                                    {action.description}
                                                                </span>
                                                            )}
                                                        </div>
                                                        <ShortcutDisplay
                                                            shortcut={action.shortcut!}
                                                            available={available}
                                                        />
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>

                    {/* Footer */}
                    <div className="px-6 py-3 border-t border-divider bg-grey-50 dark:bg-grey-900">
                        <div className="flex items-center justify-between text-xs text-text-tertiary">
                            <span>
                                {filteredActions.length} shortcuts available
                                {actionState && ' (context-aware)'}
                            </span>
                            <span>
                                Press <ShortcutKey>Esc</ShortcutKey> to close
                            </span>
                        </div>
                    </div>
                </Box>
            </Fade>
        </Modal>
    );
}

// ============================================================================
// Sub-Components
// ============================================================================

interface ShortcutDisplayProps {
    shortcut: string;
    available?: boolean;
}

function ShortcutDisplay({ shortcut, available = true }: ShortcutDisplayProps) {
    const formatted = ActionRegistry.formatShortcut(shortcut);
    const keys = formatted.split(/(?=[+⌘⌥⇧])|(?<=[+⌘⌥⇧])/g).filter(k => k && k !== '+');

    return (
        <div className="flex items-center gap-1">
            {keys.map((key, index) => (
                <ShortcutKey key={index} available={available}>
                    {key}
                </ShortcutKey>
            ))}
        </div>
    );
}

interface ShortcutKeyProps {
    children: React.ReactNode;
    available?: boolean;
}

function ShortcutKey({ children, available = true }: ShortcutKeyProps) {
    return (
        <kbd
            className={`
                inline-flex items-center justify-center
                min-w-[24px] h-6 px-1.5
                text-xs font-medium
                ${available
                    ? 'bg-grey-200 dark:bg-grey-700 text-text-primary border-grey-300 dark:border-grey-600'
                    : 'bg-grey-100 dark:bg-grey-800 text-text-tertiary border-grey-200 dark:border-grey-700'
                }
                border rounded shadow-sm
            `}
        >
            {children}
        </kbd>
    );
}

export default KeyboardShortcutsHelp;
