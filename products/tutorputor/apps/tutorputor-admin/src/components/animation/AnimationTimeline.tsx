/**
 * @doc.type component
 * @doc.purpose Animation Timeline UI for creating and editing keyframe animations
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useRef, useCallback, useEffect } from 'react';
import type { AnimationSpec, AnimationKeyframe } from '../../../../../services/tutorputor-platform/src/modules/animation-runtime/service';
import './AnimationTimeline.css';

interface AnimationTimelineProps {
    animation: AnimationSpec | null;
    onAnimationChange: (animation: AnimationSpec) => void;
    onPlay?: () => void;
    onPause?: () => void;
    onStop?: () => void;
    currentTime?: number;
    isPlaying?: boolean;
}

interface TimelineMarker {
    id: string;
    timeMs: number;
    label: string;
}

export const AnimationTimeline: React.FC<AnimationTimelineProps> = ({
    animation,
    onAnimationChange,
    onPlay,
    onPause,
    onStop,
    currentTime = 0,
    isPlaying = false,
}) => {
    const [selectedKeyframeIndex, setSelectedKeyframeIndex] = useState<number | null>(null);
    const [isDragging, setIsDragging] = useState(false);
    const [zoom, setZoom] = useState(1);
    const timelineRef = useRef<HTMLDivElement>(null);
    const [playheadPosition, setPlayheadPosition] = useState(0);

    const pixelsPerSecond = 100 * zoom;
    const duration = animation?.durationSeconds || 10;
    const timelineWidth = duration * pixelsPerSecond;

    // Update playhead position based on current time
    useEffect(() => {
        if (animation) {
            const position = (currentTime / (animation.durationSeconds * 1000)) * timelineWidth;
            setPlayheadPosition(position);
        }
    }, [currentTime, animation, timelineWidth]);

    const handleTimelineClick = useCallback((e: React.MouseEvent<HTMLDivElement>) => {
        if (!timelineRef.current || !animation) return;

        const rect = timelineRef.current.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const timeMs = (x / timelineWidth) * animation.durationSeconds * 1000;

        // Add new keyframe at clicked position
        const newKeyframe: AnimationKeyframe = {
            timeMs: Math.max(0, Math.min(timeMs, animation.durationSeconds * 1000)),
            description: `Keyframe at ${(timeMs / 1000).toFixed(2)}s`,
            properties: {},
            easing: 'linear',
        };

        const newKeyframes = [...animation.keyframes, newKeyframe].sort((a, b) => a.timeMs - b.timeMs);

        onAnimationChange({
            ...animation,
            keyframes: newKeyframes,
        });
    }, [animation, onAnimationChange, timelineWidth]);

    const handleKeyframeClick = useCallback((index: number, e: React.MouseEvent) => {
        e.stopPropagation();
        setSelectedKeyframeIndex(index);
    }, []);

    const handleKeyframeDelete = useCallback(() => {
        if (!animation || selectedKeyframeIndex === null) return;

        const newKeyframes = animation.keyframes.filter((_, i) => i !== selectedKeyframeIndex);
        onAnimationChange({
            ...animation,
            keyframes: newKeyframes,
        });
        setSelectedKeyframeIndex(null);
    }, [animation, selectedKeyframeIndex, onAnimationChange]);

    const handleKeyframeUpdate = useCallback((index: number, updates: Partial<AnimationKeyframe>) => {
        if (!animation) return;

        const newKeyframes = animation.keyframes.map((kf, i) =>
            i === index ? { ...kf, ...updates } : kf
        );

        onAnimationChange({
            ...animation,
            keyframes: newKeyframes.sort((a, b) => a.timeMs - b.timeMs),
        });
    }, [animation, onAnimationChange]);

    const handlePropertyAdd = useCallback((keyframeIndex: number, property: string, value: any) => {
        if (!animation) return;

        const newKeyframes = animation.keyframes.map((kf, i) =>
            i === keyframeIndex
                ? { ...kf, properties: { ...kf.properties, [property]: value } }
                : kf
        );

        onAnimationChange({
            ...animation,
            keyframes: newKeyframes,
        });
    }, [animation, onAnimationChange]);

    const handlePropertyRemove = useCallback((keyframeIndex: number, property: string) => {
        if (!animation) return;

        const newKeyframes = animation.keyframes.map((kf, i) => {
            if (i === keyframeIndex) {
                const { [property]: _, ...rest } = kf.properties;
                return { ...kf, properties: rest };
            }
            return kf;
        });

        onAnimationChange({
            ...animation,
            keyframes: newKeyframes,
        });
    }, [animation, onAnimationChange]);

    const formatTime = (ms: number): string => {
        const seconds = ms / 1000;
        return `${seconds.toFixed(2)}s`;
    };

    const selectedKeyframe = selectedKeyframeIndex !== null && animation
        ? animation.keyframes[selectedKeyframeIndex]
        : null;

    return (
        <div className="animation-timeline">
            {/* Toolbar */}
            <div className="timeline-toolbar">
                <div className="playback-controls">
                    <button
                        onClick={onPlay}
                        disabled={isPlaying}
                        className="btn-play"
                        title="Play"
                    >
                        ▶
                    </button>
                    <button
                        onClick={onPause}
                        disabled={!isPlaying}
                        className="btn-pause"
                        title="Pause"
                    >
                        ⏸
                    </button>
                    <button
                        onClick={onStop}
                        className="btn-stop"
                        title="Stop"
                    >
                        ⏹
                    </button>
                </div>

                <div className="timeline-info">
                    <span className="current-time">{formatTime(currentTime)}</span>
                    <span className="separator">/</span>
                    <span className="total-time">{formatTime((animation?.durationSeconds || 0) * 1000)}</span>
                </div>

                <div className="zoom-controls">
                    <button onClick={() => setZoom(z => Math.max(0.25, z - 0.25))}>−</button>
                    <span>{Math.round(zoom * 100)}%</span>
                    <button onClick={() => setZoom(z => Math.min(4, z + 0.25))}>+</button>
                </div>
            </div>

            {/* Timeline Track */}
            <div className="timeline-container">
                <div className="timeline-ruler">
                    {Array.from({ length: Math.ceil(duration) + 1 }, (_, i) => (
                        <div
                            key={i}
                            className="ruler-mark"
                            style={{ left: `${i * pixelsPerSecond}px` }}
                        >
                            <span className="ruler-label">{i}s</span>
                        </div>
                    ))}
                </div>

                <div
                    ref={timelineRef}
                    className="timeline-track"
                    onClick={handleTimelineClick}
                    style={{ width: `${timelineWidth}px` }}
                >
                    {/* Playhead */}
                    <div
                        className="playhead"
                        style={{ left: `${playheadPosition}px` }}
                    />

                    {/* Keyframes */}
                    {animation?.keyframes.map((keyframe, index) => {
                        const position = (keyframe.timeMs / (animation.durationSeconds * 1000)) * timelineWidth;
                        const isSelected = selectedKeyframeIndex === index;

                        return (
                            <div
                                key={index}
                                className={`keyframe ${isSelected ? 'selected' : ''}`}
                                style={{ left: `${position}px` }}
                                onClick={(e) => handleKeyframeClick(index, e)}
                                title={keyframe.description}
                            >
                                <div className="keyframe-marker" />
                                <div className="keyframe-label">{formatTime(keyframe.timeMs)}</div>
                            </div>
                        );
                    })}
                </div>
            </div>

            {/* Keyframe Properties Panel */}
            {selectedKeyframe && (
                <div className="keyframe-properties">
                    <div className="properties-header">
                        <h3>Keyframe Properties</h3>
                        <button onClick={handleKeyframeDelete} className="btn-delete">
                            Delete Keyframe
                        </button>
                    </div>

                    <div className="property-group">
                        <label>Time</label>
                        <input
                            type="number"
                            value={selectedKeyframe.timeMs / 1000}
                            onChange={(e) =>
                                handleKeyframeUpdate(selectedKeyframeIndex!, {
                                    timeMs: parseFloat(e.target.value) * 1000,
                                })
                            }
                            step="0.1"
                            min="0"
                            max={animation?.durationSeconds}
                        />
                        <span>seconds</span>
                    </div>

                    <div className="property-group">
                        <label>Description</label>
                        <input
                            type="text"
                            value={selectedKeyframe.description}
                            onChange={(e) =>
                                handleKeyframeUpdate(selectedKeyframeIndex!, {
                                    description: e.target.value,
                                })
                            }
                        />
                    </div>

                    <div className="property-group">
                        <label>Easing</label>
                        <select
                            value={selectedKeyframe.easing || 'linear'}
                            onChange={(e) =>
                                handleKeyframeUpdate(selectedKeyframeIndex!, {
                                    easing: e.target.value,
                                })
                            }
                        >
                            <option value="linear">Linear</option>
                            <option value="easeInQuad">Ease In Quad</option>
                            <option value="easeOutQuad">Ease Out Quad</option>
                            <option value="easeInOutQuad">Ease In Out Quad</option>
                            <option value="easeInCubic">Ease In Cubic</option>
                            <option value="easeOutCubic">Ease Out Cubic</option>
                            <option value="easeInOutCubic">Ease In Out Cubic</option>
                        </select>
                    </div>

                    <div className="properties-section">
                        <h4>Animation Properties</h4>
                        {Object.entries(selectedKeyframe.properties).map(([key, value]) => (
                            <div key={key} className="property-row">
                                <input
                                    type="text"
                                    value={key}
                                    readOnly
                                    className="property-key"
                                />
                                <input
                                    type="text"
                                    value={String(value)}
                                    onChange={(e) =>
                                        handlePropertyAdd(selectedKeyframeIndex!, key, e.target.value)
                                    }
                                    className="property-value"
                                />
                                <button
                                    onClick={() => handlePropertyRemove(selectedKeyframeIndex!, key)}
                                    className="btn-remove"
                                >
                                    ×
                                </button>
                            </div>
                        ))}

                        <button
                            onClick={() => {
                                const propertyName = prompt('Property name:');
                                if (propertyName) {
                                    handlePropertyAdd(selectedKeyframeIndex!, propertyName, '0');
                                }
                            }}
                            className="btn-add-property"
                        >
                            + Add Property
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
};
