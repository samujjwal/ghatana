import React, { useState, useEffect, useMemo } from 'react';

import { useKeyboardShortcuts } from '../../hooks/useKeyboardShortcuts';

/**
 *
 */
export interface ShortcutHelperProps {
    isVisible: boolean;
    onClose: () => void;
    context?: string;
    position?: 'center' | 'bottom-right' | 'bottom-left';
    className?: string;
    showCategories?: boolean;
    maxWidth?: string | number;
}

/**
 *
 */
export function ShortcutHelper({
    isVisible,
    onClose,
    context,
    position = 'center',
    className,
    showCategories = true,
    maxWidth = '600px'
}: ShortcutHelperProps) {
    const [searchQuery, setSearchQuery] = useState('');

    const { getShortcuts, formatShortcut } = useKeyboardShortcuts({
        context: context || 'global'
    });

    // Get shortcuts for current context
    const allShortcuts = useMemo(() => {
        return getShortcuts().filter((shortcut: unknown) => !shortcut.disabled);
    }, [getShortcuts, context]);

    // Filter shortcuts based on search
    const filteredShortcuts = useMemo(() => {
        if (!searchQuery.trim()) return allShortcuts;

        const query = searchQuery.toLowerCase();
        return allShortcuts.filter((shortcut: unknown) =>
            (shortcut.description || '').toLowerCase().includes(query) ||
            (shortcut.key || '').toLowerCase().includes(query) ||
            ((shortcut.category || '') && shortcut.category.toLowerCase().includes(query))
        );
    }, [allShortcuts, searchQuery]);

    // Group shortcuts by category
    const groupedShortcuts = useMemo(() => {
        if (!showCategories) {
            return { '': filteredShortcuts };
        }

        const groups: Record<string, unknown[]> = {};
        filteredShortcuts.forEach((shortcut: unknown) => {
            const category = shortcut.category || 'Other';
            if (!groups[category]) groups[category] = [];
            groups[category].push(shortcut);
        });

        return groups;
    }, [filteredShortcuts, showCategories]);

    // Close on escape
    useEffect(() => {
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape' && isVisible) {
                event.preventDefault();
                onClose();
            }
        };

        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [isVisible, onClose]);

    if (!isVisible) return null;

    const getPositionStyles = (): React.CSSProperties => {
        const base: React.CSSProperties = {
            position: 'fixed',
            zIndex: 9999,
            backgroundColor: 'white',
            borderRadius: '12px',
            boxShadow: '0 10px 40px rgba(0, 0, 0, 0.15)',
            border: '1px solid var(--color-border, #e0e0e0)',
            maxWidth,
            maxHeight: '70vh',
            overflow: 'hidden'
        };

        switch (position) {
            case 'bottom-right':
                return {
                    ...base,
                    bottom: '20px',
                    right: '20px',
                    width: '400px'
                };
            case 'bottom-left':
                return {
                    ...base,
                    bottom: '20px',
                    left: '20px',
                    width: '400px'
                };
            case 'center':
            default:
                return {
                    ...base,
                    top: '50%',
                    left: '50%',
                    transform: 'translate(-50%, -50%)',
                    width: '90vw'
                };
        }
    };

    const overlayStyle: React.CSSProperties = position === 'center' ? {
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.3)',
        zIndex: 9998
    } : {};

    const headerStyle: React.CSSProperties = {
        padding: '1.5rem',
        borderBottom: '1px solid var(--color-border, #e0e0e0)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
    };

    const titleStyle: React.CSSProperties = {
        margin: 0,
        fontSize: '1.25rem',
        fontWeight: '600',
        color: 'var(--color-text-primary, #333)'
    };

    const closeButtonStyle: React.CSSProperties = {
        background: 'none',
        border: 'none',
        fontSize: '1.5rem',
        cursor: 'pointer',
        color: 'var(--color-text-secondary, #666)',
        padding: '0.25rem',
        borderRadius: '4px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center'
    };

    const searchStyle: React.CSSProperties = {
        padding: '1rem 1.5rem',
        borderBottom: '1px solid var(--color-border, #e0e0e0)'
    };

    const searchInputStyle: React.CSSProperties = {
        width: '100%',
        padding: '0.5rem 0.75rem',
        border: '1px solid var(--color-border, #e0e0e0)',
        borderRadius: '6px',
        fontSize: '0.875rem',
        outline: 'none'
    };

    const contentStyle: React.CSSProperties = {
        maxHeight: 'calc(70vh - 180px)',
        overflow: 'auto',
        padding: '1rem 0'
    };

    const categoryStyle: React.CSSProperties = {
        padding: '0.75rem 1.5rem 0.5rem',
        fontSize: '0.75rem',
        fontWeight: '600',
        color: 'var(--color-text-secondary, #666)',
        textTransform: 'uppercase',
        letterSpacing: '0.5px',
        backgroundColor: 'var(--color-background-secondary, #f8f9fa)',
        margin: '0.5rem 0',
        borderTop: '1px solid var(--color-border-light, #f0f0f0)'
    };

    const shortcutItemStyle: React.CSSProperties = {
        padding: '0.75rem 1.5rem',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        borderBottom: '1px solid var(--color-border-light, #f0f0f0)'
    };

    const descriptionStyle: React.CSSProperties = {
        fontSize: '0.875rem',
        color: 'var(--color-text-primary, #333)',
        margin: 0
    };

    const keyStyle: React.CSSProperties = {
        fontSize: '0.75rem',
        color: 'var(--color-text-secondary, #666)',
        backgroundColor: 'var(--color-background-secondary, #f8f9fa)',
        padding: '0.25rem 0.5rem',
        borderRadius: '4px',
        fontFamily: 'monospace',
        border: '1px solid var(--color-border, #e0e0e0)'
    };

    const emptyStateStyle: React.CSSProperties = {
        padding: '2rem',
        textAlign: 'center',
        color: 'var(--color-text-secondary, #666)',
        fontSize: '0.875rem'
    };

    return (
        <>
            {position === 'center' && (
                <div style={overlayStyle} onClick={onClose} />
            )}

            <div className={className} style={getPositionStyles()}>
                {/* Header */}
                <div style={headerStyle}>
                    <h2 style={titleStyle}>
                        Keyboard Shortcuts
                        {context && context !== 'global' && (
                            <span style={{
                                fontSize: '0.875rem',
                                fontWeight: '400',
                                color: 'var(--color-text-secondary, #666)',
                                marginLeft: '0.5rem'
                            }}>
                                • {context}
                            </span>
                        )}
                    </h2>
                    <button
                        onClick={onClose}
                        style={closeButtonStyle}
                        title="Close (Escape)"
                    >
                        ✕
                    </button>
                </div>

                {/* Search */}
                <div style={searchStyle}>
                    <input
                        type="text"
                        placeholder="Search shortcuts..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        style={searchInputStyle}
                    />
                </div>

                {/* Content */}
                <div style={contentStyle}>
                    {Object.entries(groupedShortcuts).map(([category, shortcuts]) => {
                        if (shortcuts.length === 0) return null;

                        return (
                            <div key={category}>
                                {showCategories && category && (
                                    <div style={categoryStyle}>{category}</div>
                                )}

                                {shortcuts.map((shortcut, index) => (
                                    <div key={`${shortcut.id}-${index}`} style={shortcutItemStyle}>
                                        <div style={descriptionStyle}>
                                            {shortcut.description}
                                        </div>
                                        <div style={keyStyle}>
                                            {formatShortcut(shortcut.key, shortcut.modifiers)}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        );
                    })}

                    {filteredShortcuts.length === 0 && (
                        <div style={emptyStateStyle}>
                            {searchQuery
                                ? 'No shortcuts found matching your search'
                                : 'No shortcuts available for this context'
                            }
                        </div>
                    )}
                </div>
            </div>
        </>
    );
}