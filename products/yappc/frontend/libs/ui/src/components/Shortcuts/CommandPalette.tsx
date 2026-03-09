/**
 * Command Palette Component
 * 
 * Advanced command palette with fuzzy search, keyboard navigation, and contextual actions.
 * Integrates with the keyboard shortcuts system to provide a comprehensive command interface.
 */

import React, { useState, useEffect, useMemo, useRef } from 'react';

import { useKeyboardShortcuts } from '../../hooks/useKeyboardShortcuts';

/**
 *
 */
export interface Command {
    id: string;
    title: string;
    description?: string;
    category?: string;
    icon?: string;
    shortcut?: {
        key: string;
        modifiers?: string[];
    };
    action: () => void;
    context?: string;
    disabled?: boolean;
    priority?: number;
}

/**
 *
 */
export interface CommandPaletteProps {
    isOpen: boolean;
    onClose: () => void;
    commands: Command[];
    placeholder?: string;
    maxResults?: number;
    showCategories?: boolean;
    showShortcuts?: boolean;
    className?: string;
    width?: string | number;
    height?: string | number;
}

// Fuzzy search implementation
/**
 *
 */
function fuzzyMatch(pattern: string, text: string): { score: number; matches: number[] } {
    const patternLower = pattern.toLowerCase();
    const textLower = text.toLowerCase();

    let patternIndex = 0;
    let score = 0;
    const matches: number[] = [];

    for (let i = 0; i < textLower.length && patternIndex < patternLower.length; i++) {
        if (textLower[i] === patternLower[patternIndex]) {
            matches.push(i);
            score += 1;

            // Bonus for consecutive matches
            if (matches.length > 1 && matches[matches.length - 1] === matches[matches.length - 2] + 1) {
                score += 0.5;
            }

            // Bonus for start of word matches
            if (i === 0 || textLower[i - 1] === ' ') {
                score += 2;
            }

            patternIndex++;
        }
    }

    return patternIndex === patternLower.length ? { score, matches } : { score: 0, matches: [] };
}

// Highlight matched characters
/**
 *
 */
function highlightMatches(text: string, matches: number[]): React.ReactNode {
    if (matches.length === 0) return text;

    const parts: React.ReactNode[] = [];
    let lastIndex = 0;

    matches.forEach((matchIndex, i) => {
        if (matchIndex > lastIndex) {
            parts.push(text.slice(lastIndex, matchIndex));
        }
        parts.push(
            <mark key={`match-${i}`} style={{
                backgroundColor: 'var(--color-primary-light, #e3f2fd)',
                color: 'var(--color-primary, #1976d2)',
                fontWeight: '600',
                padding: 0
            }}>
                {text[matchIndex]}
            </mark>
        );
        lastIndex = matchIndex + 1;
    });

    if (lastIndex < text.length) {
        parts.push(text.slice(lastIndex));
    }

    return parts;
}

/**
 *
 */
export function CommandPalette({
    isOpen,
    onClose,
    commands,
    placeholder = 'Type a command or search...',
    maxResults = 50,
    showCategories = true,
    showShortcuts = true,
    className,
    width = '600px',
    height = '400px'
}: CommandPaletteProps) {
    const [query, setQuery] = useState('');
    const [selectedIndex, setSelectedIndex] = useState(0);
    const inputRef = useRef<HTMLInputElement>(null);
    const listRef = useRef<HTMLDivElement>(null);

    const { registerShortcut, formatShortcut } = useKeyboardShortcuts({
        context: 'command-palette'
    });

    // Filter and sort commands based on query
    const filteredCommands = useMemo(() => {
        if (!query.trim()) {
            return commands
                .filter(cmd => !cmd.disabled)
                .sort((a, b) => (b.priority || 0) - (a.priority || 0))
                .slice(0, maxResults)
                .map(cmd => ({
                    command: cmd,
                    score: cmd.priority || 0,
                    titleMatches: [] as number[],
                    descMatches: [] as number[]
                }));
        }

        const results = commands
            .filter(cmd => !cmd.disabled)
            .map(cmd => {
                const titleMatch = fuzzyMatch(query, cmd.title);
                const descMatch = cmd.description ? fuzzyMatch(query, cmd.description) : { score: 0, matches: [] };
                const score = titleMatch.score * 2 + descMatch.score + (cmd.priority || 0);

                return {
                    command: cmd,
                    score,
                    titleMatches: titleMatch.matches,
                    descMatches: descMatch.matches
                };
            })
            .filter(result => result.score > 0)
            .sort((a, b) => b.score - a.score)
            .slice(0, maxResults);

        return results;
    }, [query, commands, maxResults]);

    // Group commands by category
    const groupedCommands = useMemo(() => {
        if (!showCategories) {
            return { '': filteredCommands };
        }

        const groups: Record<string, typeof filteredCommands> = {};
        filteredCommands.forEach(item => {
            const category = item.command?.category || 'Other';
            if (!groups[category]) groups[category] = [];
            groups[category].push(item);
        });

        return groups;
    }, [filteredCommands, showCategories]);

    // Reset selection when results change
    useEffect(() => {
        setSelectedIndex(0);
    }, [query]);

    // Focus input when opened
    useEffect(() => {
        if (isOpen && inputRef.current) {
            inputRef.current.focus();
        }
    }, [isOpen]);

    // Keyboard navigation
    useEffect(() => {
        if (!isOpen) return;

        const handleKeyDown = (event: KeyboardEvent) => {
            const totalCommands = filteredCommands.length;

            switch (event.key) {
                case 'ArrowDown':
                    event.preventDefault();
                    setSelectedIndex(prev => (prev + 1) % totalCommands);
                    break;
                case 'ArrowUp':
                    event.preventDefault();
                    setSelectedIndex(prev => (prev - 1 + totalCommands) % totalCommands);
                    break;
                case 'Enter':
                    event.preventDefault();
                    if (filteredCommands[selectedIndex]) {
                        filteredCommands[selectedIndex].command.action();
                        onClose();
                    }
                    break;
                case 'Escape':
                    event.preventDefault();
                    onClose();
                    break;
            }
        };

        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, selectedIndex, filteredCommands, onClose]);

    // Register command palette toggle shortcut
    useEffect(() => {
        registerShortcut({
            key: 'k',
            modifiers: ['cmd'],
            description: 'Open command palette',
            category: 'Global',
            handler: () => {
                if (!isOpen) {
                    // Will be handled by parent component
                }
            }
        });

        return () => {
            // Cleanup handled by hook
        };
    }, [registerShortcut, isOpen]);

    // Scroll selected item into view
    useEffect(() => {
        if (listRef.current && selectedIndex >= 0) {
            const selectedElement = listRef.current.children[selectedIndex] as HTMLElement;
            if (selectedElement) {
                selectedElement.scrollIntoView({
                    behavior: 'smooth',
                    block: 'nearest'
                });
            }
        }
    }, [selectedIndex]);

    if (!isOpen) return null;

    const overlayStyle: React.CSSProperties = {
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.5)',
        zIndex: 10000,
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'center',
        paddingTop: '10vh'
    };

    const paletteStyle: React.CSSProperties = {
        width,
        maxWidth: '90vw',
        height,
        maxHeight: '80vh',
        backgroundColor: 'white',
        borderRadius: '12px',
        boxShadow: '0 20px 60px rgba(0, 0, 0, 0.2)',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden'
    };

    const inputStyle: React.CSSProperties = {
        width: '100%',
        padding: '1rem 1.5rem',
        border: 'none',
        fontSize: '1.125rem',
        backgroundColor: 'transparent',
        outline: 'none',
        borderBottom: '1px solid var(--color-border, #e0e0e0)'
    };

    const listStyle: React.CSSProperties = {
        flex: 1,
        overflow: 'auto',
        padding: '0.5rem 0'
    };

    const categoryStyle: React.CSSProperties = {
        padding: '0.5rem 1.5rem',
        fontSize: '0.75rem',
        fontWeight: '600',
        color: 'var(--color-text-secondary, #666)',
        textTransform: 'uppercase',
        letterSpacing: '0.5px',
        backgroundColor: 'var(--color-background-secondary, #f8f9fa)',
        borderBottom: '1px solid var(--color-border, #e0e0e0)'
    };

    const itemStyle = (index: number): React.CSSProperties => ({
        padding: '0.75rem 1.5rem',
        cursor: 'pointer',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        backgroundColor: selectedIndex === index ? 'var(--color-primary-light, #e3f2fd)' : 'transparent',
        borderLeft: selectedIndex === index ? '3px solid var(--color-primary, #1976d2)' : '3px solid transparent',
        transition: 'all 0.15s ease'
    });

    const contentStyle: React.CSSProperties = {
        flex: 1,
        minWidth: 0
    };

    const titleStyle: React.CSSProperties = {
        fontSize: '0.875rem',
        fontWeight: '500',
        margin: 0,
        display: 'flex',
        alignItems: 'center',
        gap: '0.5rem'
    };

    const descriptionStyle: React.CSSProperties = {
        fontSize: '0.75rem',
        color: 'var(--color-text-secondary, #666)',
        margin: '0.25rem 0 0 0'
    };

    const shortcutStyle: React.CSSProperties = {
        fontSize: '0.75rem',
        color: 'var(--color-text-secondary, #666)',
        backgroundColor: 'var(--color-background-secondary, #f8f9fa)',
        padding: '0.25rem 0.5rem',
        borderRadius: '4px',
        fontFamily: 'monospace'
    };

    let currentIndex = 0;

    return (
        <div style={overlayStyle} onClick={onClose}>
            <div className={className} style={paletteStyle} onClick={e => e.stopPropagation()}>
                <input
                    ref={inputRef}
                    type="text"
                    placeholder={placeholder}
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    style={inputStyle}
                />

                <div ref={listRef} style={listStyle}>
                    {Object.entries(groupedCommands).map(([category, items]) => {
                        if (items.length === 0) return null;

                        return (
                            <div key={category}>
                                {showCategories && category && (
                                    <div style={categoryStyle}>{category}</div>
                                )}
                                {items.map((item) => {
                                    const itemIndex = currentIndex++;
                                    const { command, titleMatches, descMatches } = item;

                                    return (
                                        <div
                                            key={command.id}
                                            style={itemStyle(itemIndex)}
                                            onClick={() => {
                                                command.action();
                                                onClose();
                                            }}
                                            onMouseEnter={() => setSelectedIndex(itemIndex)}
                                        >
                                            <div style={contentStyle}>
                                                <div style={titleStyle}>
                                                    {command.icon && (
                                                        <span style={{ fontSize: '1rem' }}>{command.icon}</span>
                                                    )}
                                                    {highlightMatches(command.title, titleMatches)}
                                                </div>
                                                {command.description && (
                                                    <div style={descriptionStyle}>
                                                        {highlightMatches(command.description, descMatches)}
                                                    </div>
                                                )}
                                            </div>

                                            {showShortcuts && command.shortcut && (
                                                <div style={shortcutStyle}>
                                                    {formatShortcut(command.shortcut.key, command.shortcut.modifiers)}
                                                </div>
                                            )}
                                        </div>
                                    );
                                })}
                            </div>
                        );
                    })}

                    {filteredCommands.length === 0 && (
                        <div style={{
                            padding: '2rem',
                            textAlign: 'center',
                            color: 'var(--color-text-secondary, #666)'
                        }}>
                            {query ? 'No matching commands found' : 'No commands available'}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}