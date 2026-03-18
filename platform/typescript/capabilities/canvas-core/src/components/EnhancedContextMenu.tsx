/**
 * Enhanced Context Menu Component
 * 
 * Layer and phase-aware right-click context menu.
 * Displays categorized actions based on current context and selection.
 * 
 * @doc.type component
 * @doc.purpose Contextual right-click actions
 * @doc.layer presentation
 */

import React, { useEffect, useRef } from 'react';
import { useAtomValue } from 'jotai';
import {
    chromeSemanticLayerAtom,
    chromeCurrentPhaseAtom,
    chromeActiveRolesAtom,
} from '../chrome';
import { useActionsByCategory, useActionExecutor } from '../hooks/useAvailableActions';

interface EnhancedContextMenuProps {
    isOpen: boolean;
    position: { x: number; y: number };
    selection?: 'none' | 'single' | 'multiple';
    onClose: () => void;
}

export const EnhancedContextMenu: React.FC<EnhancedContextMenuProps> = ({
    isOpen,
    position,
    selection = 'none',
    onClose,
}) => {
    const menuRef = useRef<HTMLDivElement>(null);
    const currentLayer = useAtomValue(chromeSemanticLayerAtom);
    const currentPhase = useAtomValue(chromeCurrentPhaseAtom);
    const activeRoles = useAtomValue(chromeActiveRolesAtom);
    const actionsByCategory = useActionsByCategory();
    const { executeAction } = useActionExecutor();

    // Close on click outside
    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
                onClose();
            }
        };

        if (isOpen) {
            document.addEventListener('mousedown', handleClickOutside);
            return () => document.removeEventListener('mousedown', handleClickOutside);
        }
    }, [isOpen, onClose]);

    // Close on Escape
    useEffect(() => {
        const handleEscape = (e: KeyboardEvent) => {
            if (e.key === 'Escape') {
                onClose();
            }
        };

        if (isOpen) {
            document.addEventListener('keydown', handleEscape);
            return () => document.removeEventListener('keydown', handleEscape);
        }
    }, [isOpen, onClose]);

    // Adjust position to keep menu in viewport
    const adjustedPosition = React.useMemo(() => {
        if (!menuRef.current) return position;

        const menuWidth = 280;
        const menuHeight = 400;
        const padding = 10;

        let x = position.x;
        let y = position.y;

        if (x + menuWidth > window.innerWidth - padding) {
            x = window.innerWidth - menuWidth - padding;
        }

        if (y + menuHeight > window.innerHeight - padding) {
            y = window.innerHeight - menuHeight - padding;
        }

        return { x, y };
    }, [position]);

    const handleActionClick = async (actionId: string) => {
        try {
            await executeAction(actionId, selection);
            console.log(`✅ Context menu action executed: ${actionId}`);
            onClose();
        } catch (error) {
            console.error(`❌ Context menu action failed: ${actionId}`, error);
        }
    };

    if (!isOpen) return null;

    return (
        <>
            {/* Invisible backdrop for click detection */}
            <div
                onClick={onClose}
                style={{
                    position: 'fixed',
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    zIndex: 999,
                }}
            />

            {/* Menu */}
            <div
                ref={menuRef}
                style={{
                    position: 'fixed',
                    top: `${adjustedPosition.y}px`,
                    left: `${adjustedPosition.x}px`,
                    width: '280px',
                    maxHeight: '400px',
                    backgroundColor: '#ffffff',
                    borderRadius: '8px',
                    boxShadow: '0 10px 40px rgba(0, 0, 0, 0.2)',
                    zIndex: 1000,
                    overflow: 'auto',
                    animation: 'contextMenuFadeIn 0.15s ease-out',
                }}
            >
                {/* Context Header */}
                <div
                    style={{
                        padding: '12px 16px',
                        borderBottom: '1px solid #e5e7eb',
                        backgroundColor: '#f9fafb',
                    }}
                >
                    <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '4px' }}>
                        Context
                    </div>
                    <div style={{ fontSize: '12px', color: '#374151', display: 'flex', gap: '8px' }}>
                        <span>📍 {currentLayer}</span>
                        <span>•</span>
                        <span>🎯 {currentPhase}</span>
                    </div>
                </div>

                {/* Actions by Category */}
                {actionsByCategory.map((group, groupIndex) => {
                    // Limit actions per category in context menu
                    const displayActions = group.actions.slice(0, 5);
                    if (displayActions.length === 0) return null;

                    return (
                        <div key={groupIndex} style={{ padding: '8px 0' }}>
                            {/* Category Header */}
                            <div
                                style={{
                                    padding: '8px 16px 4px 16px',
                                    fontSize: '11px',
                                    fontWeight: 600,
                                    color: '#6b7280',
                                    textTransform: 'uppercase',
                                    letterSpacing: '0.5px',
                                }}
                            >
                                {group.label}
                            </div>

                            {/* Actions */}
                            {displayActions.map((action) => (
                                <button
                                    key={action.id}
                                    onClick={() => handleActionClick(action.id)}
                                    style={{
                                        width: '100%',
                                        padding: '8px 16px',
                                        border: 'none',
                                        background: 'transparent',
                                        cursor: 'pointer',
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '12px',
                                        fontSize: '13px',
                                        color: '#374151',
                                        textAlign: 'left',
                                        transition: 'background 0.15s',
                                    }}
                                    onMouseEnter={(e) => {
                                        e.currentTarget.style.background = '#f3f4f6';
                                    }}
                                    onMouseLeave={(e) => {
                                        e.currentTarget.style.background = 'transparent';
                                    }}
                                >
                                    <span style={{ fontSize: '16px' }}>{action.icon}</span>
                                    <span style={{ flex: 1 }}>{action.label}</span>
                                    {action.shortcut && (
                                        <span
                                            style={{
                                                fontSize: '10px',
                                                color: '#9ca3af',
                                                fontFamily: 'monospace',
                                            }}
                                        >
                                            {action.shortcut}
                                        </span>
                                    )}
                                </button>
                            ))}

                            {/* Show more indicator */}
                            {group.actions.length > 5 && (
                                <div
                                    style={{
                                        padding: '4px 16px',
                                        fontSize: '11px',
                                        color: '#9ca3af',
                                        fontStyle: 'italic',
                                    }}
                                >
                                    +{group.actions.length - 5} more actions
                                </div>
                            )}
                        </div>
                    );
                })}

                {/* Universal Actions Separator */}
                <div
                    style={{
                        margin: '8px 16px',
                        borderTop: '1px solid #e5e7eb',
                    }}
                />

                {/* Quick Universal Actions */}
                <div style={{ padding: '8px 0' }}>
                    <div
                        style={{
                            padding: '8px 16px 4px 16px',
                            fontSize: '11px',
                            fontWeight: 600,
                            color: '#6b7280',
                            textTransform: 'uppercase',
                            letterSpacing: '0.5px',
                        }}
                    >
                        Universal
                    </div>
                    {['universal-add-shape', 'universal-add-text', 'universal-add-frame'].map((actionId) => {
                        const action = actionsByCategory
                            .flatMap(g => g.actions)
                            .find(a => a.id === actionId);

                        if (!action) return null;

                        return (
                            <button
                                key={action.id}
                                onClick={() => handleActionClick(action.id)}
                                style={{
                                    width: '100%',
                                    padding: '8px 16px',
                                    border: 'none',
                                    background: 'transparent',
                                    cursor: 'pointer',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '12px',
                                    fontSize: '13px',
                                    color: '#374151',
                                    textAlign: 'left',
                                    transition: 'background 0.15s',
                                }}
                                onMouseEnter={(e) => {
                                    e.currentTarget.style.background = '#f3f4f6';
                                }}
                                onMouseLeave={(e) => {
                                    e.currentTarget.style.background = 'transparent';
                                }}
                            >
                                <span style={{ fontSize: '16px' }}>{action.icon}</span>
                                <span style={{ flex: 1 }}>{action.label}</span>
                                {action.shortcut && (
                                    <span
                                        style={{
                                            fontSize: '10px',
                                            color: '#9ca3af',
                                            fontFamily: 'monospace',
                                        }}
                                    >
                                        {action.shortcut}
                                    </span>
                                )}
                            </button>
                        );
                    })}
                </div>

                {/* Footer Hint */}
                <div
                    style={{
                        padding: '8px 16px',
                        borderTop: '1px solid #e5e7eb',
                        fontSize: '10px',
                        color: '#9ca3af',
                        backgroundColor: '#f9fafb',
                    }}
                >
                    Press Cmd+K for all actions
                </div>
            </div>

            <style>{`
        @keyframes contextMenuFadeIn {
          from {
            opacity: 0;
            transform: scale(0.95);
          }
          to {
            opacity: 1;
            transform: scale(1);
          }
        }
      `}</style>
        </>
    );
};
