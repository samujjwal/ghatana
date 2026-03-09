/**
 * Command Palette Component
 * 
 * Fuzzy search across all actions with context-aware suggestions.
 * Supports keyboard navigation, recent actions, and categorized results.
 * 
 * @doc.type component
 * @doc.purpose Command search and execution
 * @doc.layer presentation
 */

import React, { useState, useEffect, useRef, useMemo } from 'react';
import { useAtomValue } from 'jotai';
import {
    chromeSemanticLayerAtom,
    chromeCurrentPhaseAtom,
    chromeActiveRolesAtom,
    Action,
} from '../chrome';
import { useActionSearch, useActionExecutor, useActionsByCategory } from '../hooks/useAvailableActions';

interface CommandPaletteProps {
    isOpen: boolean;
    onClose: () => void;
}

interface RecentAction {
    actionId: string;
    label: string;
    timestamp: number;
}

export const CommandPalette: React.FC<CommandPaletteProps> = ({ isOpen, onClose }) => {
    const [query, setQuery] = useState('');
    const [selectedIndex, setSelectedIndex] = useState(0);
    const [recentActions, setRecentActions] = useState<RecentAction[]>([]);
    const inputRef = useRef<HTMLInputElement>(null);

    const currentLayer = useAtomValue(chromeSemanticLayerAtom);
    const currentPhase = useAtomValue(chromeCurrentPhaseAtom);
    const activeRoles = useAtomValue(chromeActiveRolesAtom);

    const { searchActions } = useActionSearch();
    const { executeAction } = useActionExecutor();
    const actionsByCategory = useActionsByCategory();

    // Search results
    const searchResults = useMemo(() => {
        if (!query) {
            return [];
        }
        return searchActions(query);
    }, [query, searchActions]);

    // Context-aware suggestions (when no query)
    const contextSuggestions = useMemo(() => {
        if (query) return [];

        // Get top actions from each category
        const suggestions: Action[] = [];
        actionsByCategory.forEach(group => {
            group.actions.slice(0, 3).forEach(action => {
                suggestions.push({
                    id: action.id,
                    label: action.label,
                    icon: action.icon,
                    shortcut: action.shortcut,
                    category: action.category,
                    handler: () => action.handler({ layer: currentLayer, phase: currentPhase, roles: activeRoles, selection: 'none' }),
                });
            });
        });

        return suggestions.slice(0, 10);
    }, [actionsByCategory, query, currentLayer, currentPhase, activeRoles]);

    // Combined results
    const displayedActions = useMemo(() => {
        if (query) {
            return searchResults;
        }
        return contextSuggestions;
    }, [query, searchResults, contextSuggestions]);

    // Reset selection when results change
    useEffect(() => {
        setSelectedIndex(0);
    }, [displayedActions]);

    // Focus input when opened
    useEffect(() => {
        if (isOpen && inputRef.current) {
            inputRef.current.focus();
        }
    }, [isOpen]);

    // Keyboard navigation
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (!isOpen) return;

            switch (e.key) {
                case 'ArrowDown':
                    e.preventDefault();
                    setSelectedIndex(prev =>
                        Math.min(prev + 1, displayedActions.length + recentActions.length - 1)
                    );
                    break;
                case 'ArrowUp':
                    e.preventDefault();
                    setSelectedIndex(prev => Math.max(prev - 1, 0));
                    break;
                case 'Enter':
                    e.preventDefault();
                    handleExecuteSelected();
                    break;
                case 'Escape':
                    e.preventDefault();
                    onClose();
                    break;
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, displayedActions, recentActions, selectedIndex]);

    const handleExecuteSelected = async () => {
        const totalActions = displayedActions.length + recentActions.length;
        if (selectedIndex >= totalActions) return;

        let actionToExecute: Action | undefined;

        if (selectedIndex < displayedActions.length) {
            actionToExecute = displayedActions[selectedIndex];
        } else {
            const recentIndex = selectedIndex - displayedActions.length;
            const recent = recentActions[recentIndex];
            actionToExecute = displayedActions.find(a => a.id === recent.actionId);
        }

        if (actionToExecute) {
            try {
                await executeAction(actionToExecute.id);

                // Add to recent actions
                const newRecent: RecentAction = {
                    actionId: actionToExecute.id,
                    label: actionToExecute.label,
                    timestamp: Date.now(),
                };
                setRecentActions(prev => [newRecent, ...prev.filter(r => r.actionId !== actionToExecute!.id)].slice(0, 5));

                console.log(`✅ Executed: ${actionToExecute.label}`);
                onClose();
            } catch (error) {
                console.error(`❌ Failed to execute: ${actionToExecute.label}`, error);
            }
        }
    };

    const handleExecuteAction = async (action: Action) => {
        try {
            await executeAction(action.id);

            // Add to recent actions
            const newRecent: RecentAction = {
                actionId: action.id,
                label: action.label,
                timestamp: Date.now(),
            };
            setRecentActions(prev => [newRecent, ...prev.filter(r => r.actionId !== action.id)].slice(0, 5));

            console.log(`✅ Executed: ${action.label}`);
            onClose();
        } catch (error) {
            console.error(`❌ Failed to execute: ${action.label}`, error);
        }
    };

    if (!isOpen) return null;

    return (
        <>
            {/* Backdrop */}
            <div
                onClick={onClose}
                style={{
                    position: 'fixed',
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    backgroundColor: 'rgba(0, 0, 0, 0.5)',
                    zIndex: 1000,
                    animation: 'fadeIn 0.15s ease-out',
                }}
            />

            {/* Palette */}
            <div
                style={{
                    position: 'fixed',
                    top: '20%',
                    left: '50%',
                    transform: 'translateX(-50%)',
                    width: '600px',
                    maxHeight: '500px',
                    backgroundColor: '#ffffff',
                    borderRadius: '12px',
                    boxShadow: '0 20px 60px rgba(0, 0, 0, 0.3)',
                    zIndex: 1001,
                    display: 'flex',
                    flexDirection: 'column',
                    animation: 'slideDown 0.2s ease-out',
                }}
            >
                {/* Search Input */}
                <div style={{ padding: '20px 20px 16px 20px', borderBottom: '1px solid #e5e7eb' }}>
                    <input
                        ref={inputRef}
                        type="text"
                        placeholder="Search actions... (type to search)"
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        style={{
                            width: '100%',
                            padding: '12px 16px',
                            border: 'none',
                            fontSize: '16px',
                            outline: 'none',
                            backgroundColor: 'transparent',
                        }}
                    />
                </div>

                {/* Context Info */}
                <div
                    style={{
                        padding: '8px 20px',
                        backgroundColor: '#f9fafb',
                        borderBottom: '1px solid #e5e7eb',
                        fontSize: '12px',
                        color: '#6b7280',
                        display: 'flex',
                        gap: '12px',
                    }}
                >
                    <span>Layer: <strong>{currentLayer}</strong></span>
                    <span>•</span>
                    <span>Phase: <strong>{currentPhase}</strong></span>
                    <span>•</span>
                    <span>Roles: <strong>{activeRoles.length}</strong></span>
                </div>

                {/* Results */}
                <div style={{ flex: 1, overflow: 'auto', maxHeight: '400px' }}>
                    {/* Current Context Section */}
                    {!query && displayedActions.length > 0 && (
                        <div style={{ padding: '12px 20px' }}>
                            <div
                                style={{
                                    fontSize: '11px',
                                    fontWeight: 600,
                                    color: '#6b7280',
                                    textTransform: 'uppercase',
                                    letterSpacing: '0.5px',
                                    marginBottom: '8px',
                                }}
                            >
                                📍 Current Context
                            </div>
                            {displayedActions.map((action, index) => (
                                <button
                                    key={action.id}
                                    onClick={() => handleExecuteAction(action)}
                                    style={{
                                        width: '100%',
                                        padding: '10px 12px',
                                        border: 'none',
                                        borderRadius: '6px',
                                        background: selectedIndex === index ? '#e3f2fd' : 'transparent',
                                        cursor: 'pointer',
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '12px',
                                        marginBottom: '4px',
                                        transition: 'background 0.15s',
                                    }}
                                    onMouseEnter={(e) => {
                                        if (selectedIndex !== index) {
                                            e.currentTarget.style.background = '#f5f5f5';
                                        }
                                    }}
                                    onMouseLeave={(e) => {
                                        if (selectedIndex !== index) {
                                            e.currentTarget.style.background = 'transparent';
                                        }
                                    }}
                                >
                                    <span style={{ fontSize: '20px' }}>{action.icon}</span>
                                    <div style={{ flex: 1, textAlign: 'left' }}>
                                        <div style={{ fontSize: '14px', fontWeight: 500, color: '#374151' }}>
                                            {action.label}
                                        </div>
                                        <div style={{ fontSize: '12px', color: '#9ca3af' }}>
                                            {action.category}
                                        </div>
                                    </div>
                                    {action.shortcut && (
                                        <div
                                            style={{
                                                fontSize: '11px',
                                                color: '#9ca3af',
                                                fontFamily: 'monospace',
                                                padding: '2px 6px',
                                                backgroundColor: '#f3f4f6',
                                                borderRadius: '4px',
                                            }}
                                        >
                                            {action.shortcut}
                                        </div>
                                    )}
                                </button>
                            ))}
                        </div>
                    )}

                    {/* Search Results */}
                    {query && searchResults.length > 0 && (
                        <div style={{ padding: '12px 20px' }}>
                            <div
                                style={{
                                    fontSize: '11px',
                                    fontWeight: 600,
                                    color: '#6b7280',
                                    textTransform: 'uppercase',
                                    letterSpacing: '0.5px',
                                    marginBottom: '8px',
                                }}
                            >
                                🔍 Search Results ({searchResults.length})
                            </div>
                            {searchResults.map((action, index) => (
                                <button
                                    key={action.id}
                                    onClick={() => handleExecuteAction(action)}
                                    style={{
                                        width: '100%',
                                        padding: '10px 12px',
                                        border: 'none',
                                        borderRadius: '6px',
                                        background: selectedIndex === index ? '#e3f2fd' : 'transparent',
                                        cursor: 'pointer',
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '12px',
                                        marginBottom: '4px',
                                        transition: 'background 0.15s',
                                    }}
                                    onMouseEnter={(e) => {
                                        if (selectedIndex !== index) {
                                            e.currentTarget.style.background = '#f5f5f5';
                                        }
                                    }}
                                    onMouseLeave={(e) => {
                                        if (selectedIndex !== index) {
                                            e.currentTarget.style.background = 'transparent';
                                        }
                                    }}
                                >
                                    <span style={{ fontSize: '20px' }}>{action.icon}</span>
                                    <div style={{ flex: 1, textAlign: 'left' }}>
                                        <div style={{ fontSize: '14px', fontWeight: 500, color: '#374151' }}>
                                            {action.label}
                                        </div>
                                        <div style={{ fontSize: '12px', color: '#9ca3af' }}>
                                            {action.category}
                                        </div>
                                    </div>
                                    {action.shortcut && (
                                        <div
                                            style={{
                                                fontSize: '11px',
                                                color: '#9ca3af',
                                                fontFamily: 'monospace',
                                                padding: '2px 6px',
                                                backgroundColor: '#f3f4f6',
                                                borderRadius: '4px',
                                            }}
                                        >
                                            {action.shortcut}
                                        </div>
                                    )}
                                </button>
                            ))}
                        </div>
                    )}

                    {/* Recent Actions */}
                    {!query && recentActions.length > 0 && (
                        <div style={{ padding: '12px 20px', borderTop: '1px solid #e5e7eb' }}>
                            <div
                                style={{
                                    fontSize: '11px',
                                    fontWeight: 600,
                                    color: '#6b7280',
                                    textTransform: 'uppercase',
                                    letterSpacing: '0.5px',
                                    marginBottom: '8px',
                                }}
                            >
                                🔄 Recent Actions
                            </div>
                            {recentActions.map((recent, index) => {
                                const globalIndex = displayedActions.length + index;
                                return (
                                    <div
                                        key={recent.actionId}
                                        style={{
                                            padding: '8px 12px',
                                            borderRadius: '6px',
                                            background: selectedIndex === globalIndex ? '#e3f2fd' : 'transparent',
                                            fontSize: '13px',
                                            color: '#6b7280',
                                            marginBottom: '4px',
                                        }}
                                    >
                                        {recent.label}
                                    </div>
                                );
                            })}
                        </div>
                    )}

                    {/* No Results */}
                    {query && searchResults.length === 0 && (
                        <div
                            style={{
                                padding: '48px 20px',
                                textAlign: 'center',
                                color: '#9ca3af',
                                fontSize: '14px',
                            }}
                        >
                            No actions found for "{query}"
                        </div>
                    )}
                </div>

                {/* Footer */}
                <div
                    style={{
                        padding: '12px 20px',
                        borderTop: '1px solid #e5e7eb',
                        fontSize: '11px',
                        color: '#9ca3af',
                        display: 'flex',
                        gap: '16px',
                    }}
                >
                    <span>↑↓ Navigate</span>
                    <span>↵ Execute</span>
                    <span>Esc Close</span>
                </div>
            </div>

            <style>{`
        @keyframes fadeIn {
          from { opacity: 0; }
          to { opacity: 1; }
        }
        @keyframes slideDown {
          from {
            opacity: 0;
            transform: translateX(-50%) translateY(-20px);
          }
          to {
            opacity: 1;
            transform: translateX(-50%) translateY(0);
          }
        }
      `}</style>
        </>
    );
};
