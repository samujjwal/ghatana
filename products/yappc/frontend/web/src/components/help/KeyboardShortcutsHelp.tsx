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
import { Modal, Fade, TextField, Box, Typography } from '@ghatana/design-system';
import type { ReactNode } from 'react';

import { TRANSITIONS, RADIUS } from '../../styles/design-tokens';
import ActionRegistry, { type ActionDefinition, type ActionCategory, type ActionState } from '../../services/ActionRegistry';
import { Button } from '../ui/Button';
import { useTranslation } from '@ghatana/i18n';

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

const CATEGORY_ICONS: Record<ActionCategory, ReactNode> = {
    file: <FolderOpen className="w-5 h-5" />,
    edit: <Edit className="w-5 h-5" />,
    view: <Visibility className="w-5 h-5" />,
    canvas: <Dashboard className="w-5 h-5" />,
    selection: <Edit className="w-5 h-5" />,
    ai: <AutoAwesome className="w-5 h-5" />,
    navigation: <Navigation className="w-5 h-5" />,
    project: <FolderOpen className="w-5 h-5" />,
    deploy: <CloudUpload className="w-5 h-5" />,
    connection: <Navigation className="w-5 h-5" />,
    interface: <Dashboard className="w-5 h-5" />,
    help: <HelpOutline className="w-5 h-5" />,
};

const CATEGORY_LABELS: Record<ActionCategory, string> = {
    file: 'File',
    edit: 'Edit',
    view: 'View',
    canvas: 'Canvas',
    selection: 'Selection',
    ai: 'Suggestion Center',
    navigation: 'Navigation',
    project: 'Project',
    deploy: 'Deploy',
    connection: 'Connection',
    interface: 'Interface',
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
    const { t } = useTranslation('common');

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
            'file', 'edit', 'view', 'canvas', 'selection', 'ai', 'navigation', 'project', 'deploy', 'connection', 'interface', 'help'
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
                            <Typography className="font-semibold">
                                {t('keyboardShortcuts.title')}
                            </Typography>
                        </div>
                        <Button
                            onClick={onClose}
                            variant="ghost"
                            size="small"
                            className={`
                                p-2 ${RADIUS.button} ${TRANSITIONS.fast}
                                text-text-tertiary hover:text-text-secondary hover:bg-grey-100 dark:hover:bg-grey-800
                            `}
                            aria-label={t('keyboardShortcuts.close')}
                        >
                            <Close className="w-5 h-5" />
                        </Button>
                    </div>

                    {/* Search */}
                    <div className="px-6 py-3 border-b border-divider">
                        <Box className="flex items-center gap-2">
                            <Search className="h-5 w-5 text-text-tertiary" />
                            <TextField
                                fullWidth
                                size="sm"
                                placeholder={t('keyboardShortcuts.searchPlaceholder')}
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                autoFocus
                            />
                        </Box>
                    </div>

                    {/* Shortcuts List */}
                    <div className="flex-1 overflow-y-auto px-6 py-4">
                        {groupedActions.length === 0 ? (
                            <div className="text-center py-8 text-text-tertiary">
                                {t('keyboardShortcuts.noResults', { query: searchQuery })}
                            </div>
                        ) : (
                            <div className="space-y-6">
                                {groupedActions.map(group => (
                                    <div key={group.category}>
                                        {/* Category Header */}
                                        <div className="flex items-center gap-2 mb-3 text-text-secondary">
                                            {group.icon}
                                            <Typography className="text-xs font-semibold uppercase tracking-wide">
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
