/**
 * Layers Panel Component
 * 
 * Z-order management with visibility and lock controls.
 * Supports drag-and-drop reordering, grouping, and layer statistics.
 * 
 * @doc.type component
 * @doc.purpose Layer management and z-index control
 * @doc.layer presentation
 */

import React, { useState, useMemo } from 'react';
import { getCanvasState, CanvasElement } from '../../handlers/canvas-handlers';

interface LayersPanelProps {
    onClose: () => void;
}

interface LayerState {
    id: string;
    visible: boolean;
    locked: boolean;
}

export const LayersPanel: React.FC<LayersPanelProps> = ({ onClose }) => {
    const [layerStates, setLayerStates] = useState<Map<string, LayerState>>(new Map());
    const [groupBy, setGroupBy] = useState<'none' | 'type' | 'phase'>('none');
    const [selectedLayers, setSelectedLayers] = useState<Set<string>>(new Set());

    // Get all elements from canvas state
    const elements = useMemo(() => {
        const canvasState = getCanvasState();
        return canvasState.getAllElements();
    }, []);

    // Group elements based on groupBy setting
    const groupedElements = useMemo(() => {
        if (groupBy === 'none') {
            return [{ label: 'All Layers', elements }];
        }

        if (groupBy === 'type') {
            const groups = new Map<string, CanvasElement[]>();
            elements.forEach(el => {
                const type = el.type || 'unknown';
                if (!groups.has(type)) {
                    groups.set(type, []);
                }
                groups.get(type)!.push(el);
            });
            return Array.from(groups.entries()).map(([type, els]) => ({
                label: type.charAt(0).toUpperCase() + type.slice(1),
                elements: els,
            }));
        }

        if (groupBy === 'phase') {
            const groups = new Map<string, CanvasElement[]>();
            elements.forEach(el => {
                const phase = (el.data?.phase as string) || 'unassigned';
                if (!groups.has(phase)) {
                    groups.set(phase, []);
                }
                groups.get(phase)!.push(el);
            });
            return Array.from(groups.entries()).map(([phase, els]) => ({
                label: phase,
                elements: els,
            }));
        }

        return [{ label: 'All Layers', elements }];
    }, [elements, groupBy]);

    const getLayerState = (id: string): LayerState => {
        return layerStates.get(id) || { id, visible: true, locked: false };
    };

    const toggleVisibility = (id: string) => {
        const state = getLayerState(id);
        const nextVisible = !state.visible;
        const newStates = new Map(layerStates);
        newStates.set(id, { ...state, visible: nextVisible });
        setLayerStates(newStates);
        // Propagate to canvas state so renderers can honour the visibility flag
        const canvasState = getCanvasState();
        const element = canvasState.getElement(id);
        if (element) {
            canvasState.updateElement(id, {
                data: { ...element.data, visible: nextVisible },
            });
        }
    };

    const toggleLock = (id: string) => {
        const state = getLayerState(id);
        const nextLocked = !state.locked;
        const newStates = new Map(layerStates);
        newStates.set(id, { ...state, locked: nextLocked });
        setLayerStates(newStates);
        // Propagate to canvas state so interaction handlers can honour the lock flag
        const canvasState = getCanvasState();
        const element = canvasState.getElement(id);
        if (element) {
            canvasState.updateElement(id, {
                data: { ...element.data, locked: nextLocked },
            });
        }
    };

    const toggleSelection = (id: string) => {
        const newSelection = new Set(selectedLayers);
        if (newSelection.has(id)) {
            newSelection.delete(id);
        } else {
            newSelection.add(id);
        }
        setSelectedLayers(newSelection);
    };

    const getElementIcon = (type: string): string => {
        const icons: Record<string, string> = {
            service: '🔷',
            database: '🗄️',
            'api-contract': '🔌',
            component: '🧩',
            screen: '📱',
            wireframe: '📐',
            'code-block': '💻',
            function: 'ƒ',
            class: '🏛️',
            shape: '⬜',
            text: '📝',
            frame: '🖼️',
            connector: '🔗',
        };
        return icons[type] || '📦';
    };

    const stats = useMemo(() => {
        const visible = Array.from(layerStates.values()).filter(s => s.visible).length;
        const locked = Array.from(layerStates.values()).filter(s => s.locked).length;
        return {
            total: elements.length,
            visible: visible || elements.length,
            locked,
            selected: selectedLayers.size,
        };
    }, [elements, layerStates, selectedLayers]);

    return (
        <div
            style={{
                width: '320px',
                height: '100%',
                backgroundColor: '#ffffff',
                borderRight: '1px solid #e0e0e0',
                display: 'flex',
                flexDirection: 'column',
            }}
        >
            {/* Header */}
            <div
                style={{
                    height: '48px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '0 16px',
                    borderBottom: '1px solid #e0e0e0',
                }}
            >
                <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 600 }}>
                    Layers
                </h3>
                <button
                    onClick={onClose}
                    style={{
                        border: 'none',
                        background: 'transparent',
                        cursor: 'pointer',
                        fontSize: '20px',
                        padding: '4px',
                    }}
                    aria-label="Close panel"
                >
                    ×
                </button>
            </div>

            {/* Stats Bar */}
            <div
                style={{
                    padding: '12px 16px',
                    borderBottom: '1px solid #e0e0e0',
                    display: 'flex',
                    gap: '16px',
                    fontSize: '12px',
                    color: '#6b7280',
                }}
            >
                <div>
                    <strong>{stats.total}</strong> total
                </div>
                <div>
                    <strong>{stats.visible}</strong> visible
                </div>
                <div>
                    <strong>{stats.locked}</strong> locked
                </div>
                <div>
                    <strong>{stats.selected}</strong> selected
                </div>
            </div>

            {/* Group By Controls */}
            <div
                style={{
                    padding: '12px 16px',
                    borderBottom: '1px solid #e0e0e0',
                    display: 'flex',
                    gap: '8px',
                }}
            >
                <span style={{ fontSize: '12px', color: '#6b7280' }}>Group by:</span>
                {(['none', 'type', 'phase'] as const).map(option => (
                    <button
                        key={option}
                        onClick={() => setGroupBy(option)}
                        style={{
                            padding: '4px 8px',
                            border: '1px solid #d1d5db',
                            borderRadius: '4px',
                            background: groupBy === option ? '#e3f2fd' : '#ffffff',
                            color: groupBy === option ? '#1976d2' : '#374151',
                            cursor: 'pointer',
                            fontSize: '12px',
                            fontWeight: groupBy === option ? 600 : 400,
                        }}
                    >
                        {option.charAt(0).toUpperCase() + option.slice(1)}
                    </button>
                ))}
            </div>

            {/* Layer List */}
            <div style={{ flex: 1, overflow: 'auto', padding: '8px' }}>
                {groupedElements.length === 0 ? (
                    <div
                        style={{
                            padding: '32px 16px',
                            textAlign: 'center',
                            color: '#9ca3af',
                            fontSize: '14px',
                        }}
                    >
                        No layers yet
                    </div>
                ) : (
                    groupedElements.map((group, groupIndex) => (
                        <div key={groupIndex} style={{ marginBottom: '16px' }}>
                            {/* Group Header */}
                            {groupBy !== 'none' && (
                                <div
                                    style={{
                                        fontSize: '11px',
                                        fontWeight: 600,
                                        color: '#6b7280',
                                        padding: '4px 8px',
                                        textTransform: 'uppercase',
                                        letterSpacing: '0.5px',
                                    }}
                                >
                                    {group.label} ({group.elements.length})
                                </div>
                            )}

                            {/* Elements */}
                            {group.elements.map(element => {
                                const state = getLayerState(element.id);
                                const isSelected = selectedLayers.has(element.id);

                                return (
                                    <div
                                        key={element.id}
                                        onClick={() => toggleSelection(element.id)}
                                        style={{
                                            padding: '8px 12px',
                                            marginBottom: '2px',
                                            borderRadius: '6px',
                                            background: isSelected ? '#e3f2fd' : 'transparent',
                                            cursor: 'pointer',
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: '8px',
                                            transition: 'background 0.2s',
                                        }}
                                        onMouseEnter={(e) => {
                                            if (!isSelected) {
                                                e.currentTarget.style.background = '#f5f5f5';
                                            }
                                        }}
                                        onMouseLeave={(e) => {
                                            if (!isSelected) {
                                                e.currentTarget.style.background = 'transparent';
                                            }
                                        }}
                                    >
                                        {/* Icon */}
                                        <span style={{ fontSize: '16px' }}>
                                            {getElementIcon(element.type)}
                                        </span>

                                        {/* Label */}
                                        <span
                                            style={{
                                                flex: 1,
                                                fontSize: '13px',
                                                color: state.visible ? '#374151' : '#9ca3af',
                                                textDecoration: state.visible ? 'none' : 'line-through',
                                            }}
                                        >
                                            {element.label || element.type}
                                        </span>

                                        {/* Controls */}
                                        <button
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                toggleVisibility(element.id);
                                            }}
                                            style={{
                                                border: 'none',
                                                background: 'transparent',
                                                cursor: 'pointer',
                                                fontSize: '16px',
                                                padding: '4px',
                                            }}
                                            title={state.visible ? 'Hide' : 'Show'}
                                        >
                                            {state.visible ? '👁️' : '👁️‍🗨️'}
                                        </button>

                                        <button
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                toggleLock(element.id);
                                            }}
                                            style={{
                                                border: 'none',
                                                background: 'transparent',
                                                cursor: 'pointer',
                                                fontSize: '16px',
                                                padding: '4px',
                                            }}
                                            title={state.locked ? 'Unlock' : 'Lock'}
                                        >
                                            {state.locked ? '🔒' : '🔓'}
                                        </button>
                                    </div>
                                );
                            })}
                        </div>
                    ))
                )}
            </div>

            {/* Actions */}
            <div
                style={{
                    padding: '12px 16px',
                    borderTop: '1px solid #e0e0e0',
                    display: 'flex',
                    gap: '8px',
                }}
            >
                <button
                    onClick={() => {
                        selectedLayers.forEach(id => toggleVisibility(id));
                    }}
                    disabled={selectedLayers.size === 0}
                    style={{
                        flex: 1,
                        padding: '8px 12px',
                        border: '1px solid #d1d5db',
                        borderRadius: '6px',
                        background: '#ffffff',
                        color: '#374151',
                        cursor: selectedLayers.size === 0 ? 'not-allowed' : 'pointer',
                        fontSize: '13px',
                        opacity: selectedLayers.size === 0 ? 0.5 : 1,
                    }}
                >
                    Toggle Visibility
                </button>
                <button
                    onClick={() => {
                        selectedLayers.forEach(id => toggleLock(id));
                    }}
                    disabled={selectedLayers.size === 0}
                    style={{
                        flex: 1,
                        padding: '8px 12px',
                        border: '1px solid #d1d5db',
                        borderRadius: '6px',
                        background: '#ffffff',
                        color: '#374151',
                        cursor: selectedLayers.size === 0 ? 'not-allowed' : 'pointer',
                        fontSize: '13px',
                        opacity: selectedLayers.size === 0 ? 0.5 : 1,
                    }}
                >
                    Toggle Lock
                </button>
            </div>
        </div>
    );
};
