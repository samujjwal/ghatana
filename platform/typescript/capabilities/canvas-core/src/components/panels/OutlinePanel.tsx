/**
 * Outline Panel Component
 * 
 * Hierarchical frame navigation with phase-based grouping.
 * Supports zoom-to-frame, drag-and-drop reordering, and search.
 * 
 * @doc.type component
 * @doc.purpose Frame navigation and organization
 * @doc.layer presentation
 */

import React, { useState, useMemo } from 'react';
import { useAtomValue } from 'jotai';
import { chromeCurrentPhaseAtom, SemanticLayer } from '../../chrome';
import { getCanvasConfig, hasCanvasConfig } from '../../core/canvas-config';
import { getCanvasState, CanvasElement } from '../../handlers/canvas-handlers';
import { useZoomToLayer } from '../../hooks/useLayerDetection';

interface OutlinePanelProps {
    onClose: () => void;
}

interface FrameGroup {
    phase: string;
    frames: CanvasElement[];
}

export const OutlinePanel: React.FC<OutlinePanelProps> = ({ onClose }) => {
    const currentPhase = useAtomValue(chromeCurrentPhaseAtom);
    const { zoomToLayer } = useZoomToLayer();
    const [searchQuery, setSearchQuery] = useState('');
    const [expandedPhases, setExpandedPhases] = useState<Set<string>>(
        new Set([currentPhase])
    );

    // Get all frames from canvas state
    const frames = useMemo(() => {
        const canvasState = getCanvasState();
        return canvasState.getAllElements().filter(el => el.type === 'frame');
    }, []);

    // Group frames by phase
    const framesByPhase = useMemo(() => {
        const groups: FrameGroup[] = [];
        const phases: string[] = hasCanvasConfig()
            ? Object.keys(getCanvasConfig().phases)
            : [];

        phases.forEach(phase => {
            const phaseFrames = frames.filter(
                frame => frame.data?.phase === phase
            );
            if (phaseFrames.length > 0 || phase === currentPhase) {
                groups.push({ phase, frames: phaseFrames });
            }
        });

        return groups;
    }, [frames, currentPhase]);

    // Filter frames by search query
    const filteredGroups = useMemo(() => {
        if (!searchQuery) return framesByPhase;

        return framesByPhase
            .map(group => ({
                ...group,
                frames: group.frames.filter(frame =>
                    frame.label?.toLowerCase().includes(searchQuery.toLowerCase())
                ),
            }))
            .filter(group => group.frames.length > 0);
    }, [framesByPhase, searchQuery]);

    const togglePhase = (phase: string) => {
        const newExpanded = new Set(expandedPhases);
        if (newExpanded.has(phase)) {
            newExpanded.delete(phase);
        } else {
            newExpanded.add(phase);
        }
        setExpandedPhases(newExpanded);
    };

    const handleFrameClick = (frame: CanvasElement) => {
        // Zoom to frame's layer
        if (frame.data?.layer) {
            zoomToLayer(frame.data.layer as SemanticLayer, true);
        }
        console.log('🎯 Zooming to frame:', frame.label);
    };

    const handleAddFrame = () => {
        console.log('➕ Adding new frame for phase:', currentPhase);
        // This will be connected to the action handler
    };

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
                    Outline
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

            {/* Search */}
            <div style={{ padding: '12px 16px', borderBottom: '1px solid #e0e0e0' }}>
                <input
                    type="text"
                    placeholder="Search frames..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    style={{
                        width: '100%',
                        padding: '8px 12px',
                        border: '1px solid #d1d5db',
                        borderRadius: '6px',
                        fontSize: '14px',
                        outline: 'none',
                    }}
                />
            </div>

            {/* Frame List */}
            <div style={{ flex: 1, overflow: 'auto', padding: '8px' }}>
                {filteredGroups.length === 0 ? (
                    <div
                        style={{
                            padding: '32px 16px',
                            textAlign: 'center',
                            color: '#9ca3af',
                            fontSize: '14px',
                        }}
                    >
                        {searchQuery ? 'No frames found' : 'No frames yet'}
                    </div>
                ) : (
                    filteredGroups.map(group => {
                        const phaseColor = hasCanvasConfig()
                            ? getCanvasConfig().phases[group.phase]?.color
                            : { primary: '#1976d2', background: '#e3f2fd', text: '#1565c0' };
                        const isExpanded = expandedPhases.has(group.phase);

                        return (
                            <div key={group.phase} style={{ marginBottom: '8px' }}>
                                {/* Phase Header */}
                                <button
                                    onClick={() => togglePhase(group.phase)}
                                    style={{
                                        width: '100%',
                                        padding: '8px 12px',
                                        border: 'none',
                                        borderRadius: '6px',
                                        background:
                                            group.phase === currentPhase
                                                ? phaseColor.background
                                                : 'transparent',
                                        cursor: 'pointer',
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'space-between',
                                        fontSize: '13px',
                                        fontWeight: 600,
                                        color: phaseColor.text,
                                        transition: 'background 0.2s',
                                    }}
                                    onMouseEnter={(e) => {
                                        if (group.phase !== currentPhase) {
                                            e.currentTarget.style.background = '#f5f5f5';
                                        }
                                    }}
                                    onMouseLeave={(e) => {
                                        if (group.phase !== currentPhase) {
                                            e.currentTarget.style.background = 'transparent';
                                        }
                                    }}
                                >
                                    <span>
                                        {isExpanded ? '▼' : '▶'} {group.phase} ({group.frames.length})
                                    </span>
                                </button>

                                {/* Frame List */}
                                {isExpanded && (
                                    <div style={{ marginLeft: '16px', marginTop: '4px' }}>
                                        {group.frames.map(frame => (
                                            <button
                                                key={frame.id}
                                                onClick={() => handleFrameClick(frame)}
                                                style={{
                                                    width: '100%',
                                                    padding: '8px 12px',
                                                    border: 'none',
                                                    borderRadius: '6px',
                                                    background: 'transparent',
                                                    cursor: 'pointer',
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    gap: '8px',
                                                    fontSize: '13px',
                                                    color: '#374151',
                                                    textAlign: 'left',
                                                    transition: 'background 0.2s',
                                                    marginBottom: '2px',
                                                }}
                                                onMouseEnter={(e) => {
                                                    e.currentTarget.style.background = '#f5f5f5';
                                                }}
                                                onMouseLeave={(e) => {
                                                    e.currentTarget.style.background = 'transparent';
                                                }}
                                            >
                                                <span>🖼️</span>
                                                <span style={{ flex: 1 }}>{frame.label}</span>
                                            </button>
                                        ))}
                                    </div>
                                )}
                            </div>
                        );
                    })
                )}
            </div>

            {/* Add Frame Button */}
            <div
                style={{
                    padding: '12px 16px',
                    borderTop: '1px solid #e0e0e0',
                }}
            >
                <button
                    onClick={handleAddFrame}
                    style={{
                        width: '100%',
                        padding: '10px 16px',
                        border: '1px solid #1976d2',
                        borderRadius: '6px',
                        background: '#1976d2',
                        color: '#ffffff',
                        cursor: 'pointer',
                        fontSize: '14px',
                        fontWeight: 500,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: '8px',
                    }}
                >
                    <span>➕</span>
                    <span>New Frame</span>
                </button>
            </div>
        </div>
    );
};
