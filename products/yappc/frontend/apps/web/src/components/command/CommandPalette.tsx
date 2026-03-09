/**
 * Command Palette Component
 * 
 * Global action discovery with fuzzy search.
 * Triggered by Cmd+K / Ctrl+K
 * 
 * @doc.type component
 * @doc.purpose Action discovery and execution
 * @doc.layer product
 * @doc.pattern Dialog Component
 */

import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { Dialog, Input as InputBase, InteractiveList as List, ListItemButton, ListItemIcon, ListItemText, Box, Chip, Typography } from '@ghatana/ui';
import { Search as SearchIcon, Keyboard as KeyboardIcon } from 'lucide-react';

import { useActions, ActionState } from '../../services/ActionRegistry';

// ============================================================================
// Types
// ============================================================================

export interface CommandPaletteProps {
    /** Action state */
    state: ActionState;
    /** Trigger key binding */
    triggerKey?: string; // default: "mod+k"
    /** Additional class names */
    className?: string;
}

// ============================================================================
// Utilities
// ============================================================================

/**
 * Fuzzy search algorithm - simple but effective
 */
function fuzzySearch(query: string, text: string): number {
    const lowerQuery = query.toLowerCase();
    const lowerText = text.toLowerCase();

    if (!lowerQuery) return 0;
    if (lowerText.includes(lowerQuery)) return 100;

    let score = 0;
    let queryIdx = 0;

    for (let i = 0; i < lowerText.length && queryIdx < lowerQuery.length; i++) {
        if (lowerText[i] === lowerQuery[queryIdx]) {
            score += 1;
            queryIdx++;
        }
    }

    return queryIdx === lowerQuery.length ? score : -1;
}

/**
 * Highlight matched characters
 */
function highlightMatches(text: string, query: string): React.ReactNode {
    if (!query) return text;

    const lowerQuery = query.toLowerCase();
    const lowerText = text.toLowerCase();
    const parts: Array<{ text: string; matched: boolean }> = [];
    let queryIdx = 0;
    let lastIdx = 0;

    for (let i = 0; i < lowerText.length && queryIdx < lowerQuery.length; i++) {
        if (lowerText[i] === lowerQuery[queryIdx]) {
            if (i > lastIdx) {
                parts.push({ text: text.substring(lastIdx, i), matched: false });
            }
            parts.push({ text: text[i], matched: true });
            lastIdx = i + 1;
            queryIdx++;
        }
    }

    if (lastIdx < text.length) {
        parts.push({ text: text.substring(lastIdx), matched: false });
    }

    return parts.map((part, i) => (
        <span
            key={i}
            className={part.matched ? 'font-bold bg-yellow-200 dark:bg-yellow-700' : ''}
        >
            {part.text}
        </span>
    ));
}

// ============================================================================
// Main Component
// ============================================================================

/**
 * Command Palette
 * 
 * Global command discovery and execution interface.
 * Open with Cmd+K (Mac) or Ctrl+K (other platforms).
 * 
 * @example
 * ```tsx
 * <CommandPalette 
 *   state={actionState}
 *   triggerKey="mod+k"
 * />
 * ```
 */
export const CommandPalette: React.FC<CommandPaletteProps> = ({
    state,
    triggerKey = 'mod+k',
    className = '',
}) => {
    const [isOpen, setIsOpen] = useState(false);
    const [query, setQuery] = useState('');
    const [selectedIndex, setSelectedIndex] = useState(0);
    const inputRef = React.useRef<HTMLInputElement>(null);

    const { grouped, execute, formatShortcut } = useActions(state);

    // Flatten grouped actions for search
    const flatActions = useMemo(() => {
        return grouped.flatMap((group: unknown) => group.actions);
    }, [grouped]);

    // Filter and sort actions by search query
    const filteredActions = useMemo(() => {
        if (!query) return flatActions;

        return flatActions
            .map((action: unknown) => ({
                action,
                score: Math.max(
                    fuzzySearch(query, action.label),
                    fuzzySearch(query, action.description || ''),
                    fuzzySearch(query, action.category),
                ),
            }))
            .filter(({ score }: { score: number }) => score > 0)
            .sort((a: unknown, b: unknown) => b.score - a.score)
            .map(({ action }: { action: unknown }) => action);
    }, [flatActions, query]);

    // Keyboard navigation
    const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
        if (e.key === 'Escape') {
            setIsOpen(false);
            setQuery('');
        } else if (e.key === 'ArrowDown') {
            e.preventDefault();
            setSelectedIndex(prev =>
                prev < filteredActions.length - 1 ? prev + 1 : 0
            );
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            setSelectedIndex(prev =>
                prev > 0 ? prev - 1 : filteredActions.length - 1
            );
        } else if (e.key === 'Enter') {
            e.preventDefault();
            if (filteredActions[selectedIndex]) {
                execute(filteredActions[selectedIndex].id);
                setIsOpen(false);
                setQuery('');
                setSelectedIndex(0);
            }
        }
    }, [filteredActions, selectedIndex, execute]);

    // Global keyboard listener
    useEffect(() => {
        const handleGlobalKeyDown = (e: KeyboardEvent) => {
            // Check for trigger key
            const parts = triggerKey.split('+');
            const hasMod = parts.includes('mod');
            const hasAlt = parts.includes('alt');
            const hasShift = parts.includes('shift');
            const key = parts[parts.length - 1].toLowerCase();

            const isMod = (e.metaKey || e.ctrlKey);
            const isAlt = e.altKey;
            const isShift = e.shiftKey;
            const isKey = e.key.toLowerCase() === key;

            if (
                hasMod === isMod &&
                hasAlt === isAlt &&
                hasShift === isShift &&
                isKey
            ) {
                e.preventDefault();
                setIsOpen(true);
                setSelectedIndex(0);
                setQuery('');
            }
        };

        window.addEventListener('keydown', handleGlobalKeyDown);
        return () => window.removeEventListener('keydown', handleGlobalKeyDown);
    }, [triggerKey]);

    // Focus input when opened
    useEffect(() => {
        if (isOpen && inputRef.current) {
            inputRef.current.focus();
        }
    }, [isOpen]);

    return (
        <>
            {/* Keyboard hint (optional) */}
            <style>{`
                body.command-palette-active {
                    overflow: hidden;
                }
            `}</style>

            {/* Command Palette Dialog */}
            <Dialog
                open={isOpen}
                onClose={() => setIsOpen(false)}
                size="sm"
                fullWidth
                className={className}
                PaperProps={{
                    className: 'rounded-lg shadow-2xl',
                    style: { marginTop: '-100px' },
                }}
            >
                {/* Search Input */}
                <Box className="border-b border-grey-200 dark:border-grey-700 p-4">
                    <Box className="flex items-center gap-2">
                        <SearchIcon className="text-grey-400 w-5 h-5" />
                        <InputBase
                            ref={inputRef}
                            placeholder="Search actions..."
                            value={query}
                            onChange={(e) => {
                                setQuery(e.target.value);
                                setSelectedIndex(0);
                            }}
                            onKeyDown={handleKeyDown}
                            fullWidth
                            autoFocus
                            className="text-lg"
                        />
                        <Chip
                            icon={<KeyboardIcon className="w-3 h-3" />}
                            label="ESC"
                            size="sm"
                            variant="outlined"
                            className="ml-2"
                        />
                    </Box>
                </Box>

                {/* Actions List */}
                {filteredActions.length > 0 ? (
                    <List className="max-h-96 overflow-y-auto">
                        {filteredActions.map((action: unknown, index: number) => (
                            <ListItemButton
                                key={action.id}
                                selected={index === selectedIndex}
                                onClick={() => {
                                    execute(action.id);
                                    setIsOpen(false);
                                    setQuery('');
                                    setSelectedIndex(0);
                                }}
                                onMouseEnter={() => setSelectedIndex(index)}
                                className={`
                                    px-4 py-3 transition-colors
                                    ${index === selectedIndex
                                        ? 'bg-blue-50 dark:bg-blue-900/30'
                                        : ''
                                    }
                                `}
                            >
                                <ListItemIcon className="min-w-fit mr-3">
                                    <Box
                                        className={`
                                            w-8 h-8 rounded-lg flex items-center justify-center text-sm
                                            ${action.isDangerous
                                                ? 'bg-red-100 text-red-600 dark:bg-red-900/30 dark:text-red-400'
                                                : 'bg-grey-100 text-grey-600 dark:bg-grey-800 dark:text-grey-400'
                                            }
                                        `}
                                    >
                                        {action.label.charAt(0).toUpperCase()}
                                    </Box>
                                </ListItemIcon>
                                <ListItemText
                                    primary={highlightMatches(action.label, query)}
                                    secondary={
                                        <span className="text-xs text-grey-500 dark:text-grey-400">
                                            {action.description}
                                        </span>
                                    }
                                />
                                {action.shortcut && (
                                    <Typography
                                        as="span"
                                        className="ml-4 whitespace-nowrap text-xs text-grey-400 dark:text-grey-500"
                                    >
                                        {formatShortcut(action.shortcut)}
                                    </Typography>
                                )}
                            </ListItemButton>
                        ))}
                    </List>
                ) : (
                    <Box className="p-8 text-center">
                        <Typography color="textSecondary" as="p" className="text-sm">
                            No actions found for "{query}"
                        </Typography>
                    </Box>
                )}

                {/* Footer with tips */}
                {!query && filteredActions.length > 0 && (
                    <Box className="border-t border-grey-200 dark:border-grey-700 p-3 bg-grey-50 dark:bg-grey-800/50">
                        <div className="flex items-center justify-between text-xs text-grey-500 dark:text-grey-400">
                            <div className="flex gap-4">
                                <span>↑↓ Navigate</span>
                                <span>↵ Execute</span>
                            </div>
                            <span>Type to filter...</span>
                        </div>
                    </Box>
                )}
            </Dialog>
        </>
    );
};

export default CommandPalette;
