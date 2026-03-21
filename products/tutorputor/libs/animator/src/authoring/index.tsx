/**
 * Animation Authoring Tools - UI Components for Timeline Editing
 * 
 * Provides a complete visual editor for creating and editing animations
 * including timeline scrubber, keyframe editor, property panels, and preview.
 */

import React, { useState, useRef, useCallback, useEffect } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import {
  animationTracksAtom,
  selectedTrackAtom,
  currentTimeAtom,
  totalDurationAtom,
  isPlayingAtom,
  zoomLevelAtom,
  snapToGridAtom,
  gridSizeAtom,
  playbackSpeedAtom,
  undoAtom,
  redoAtom,
  canUndoAtom,
  canRedoAtom,
  addTrackAtom,
  updateTrackAtom,
  deleteTrackAtom,
  duplicateTrackAtom,
  addKeyframeAtom,
  updateKeyframeAtom,
  deleteKeyframeAtom,
  type AnimationTrack,
  type AnimationKeyframe,
} from '../state';

// =============================================================================
// Timeline Scrubber Component
// =============================================================================

interface TimelineScrubberProps {
  height?: number;
  className?: string;
}

export const TimelineScrubber: React.FC<TimelineScrubberProps> = ({
  height = 60,
  className = '',
}) => {
  const [currentTime, setCurrentTime] = useAtom(currentTimeAtom);
  const totalDuration = useAtomValue(totalDurationAtom);
  const [isPlaying] = useAtom(isPlayingAtom);
  const [zoomLevel] = useAtom(zoomLevelAtom);
  const containerRef = useRef<HTMLDivElement>(null);
  const [isDragging, setIsDragging] = useState(false);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    setIsDragging(true);
    updateTimeFromMouse(e);
  }, []);

  const handleMouseMove = useCallback((e: React.MouseEvent) => {
    if (isDragging) {
      updateTimeFromMouse(e);
    }
  }, [isDragging]);

  const handleMouseUp = useCallback(() => {
    setIsDragging(false);
  }, []);

  const updateTimeFromMouse = (e: React.MouseEvent) => {
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const percentage = Math.max(0, Math.min(1, x / rect.width));
    const newTime = percentage * totalDuration;
    setCurrentTime(newTime);
  };

  const formatTime = (ms: number): string => {
    const seconds = Math.floor(ms / 1000);
    const milliseconds = Math.floor((ms % 1000) / 10);
    return `${seconds.toString().padStart(2, '0')}.${milliseconds.toString().padStart(2, '0')}`;
  };

  const ticks = Array.from({ length: Math.ceil(totalDuration / 1000) + 1 }, (_, i) => i * 1000);

  return (
    <div
      ref={containerRef}
      className={`timeline-scrubber ${className}`}
      style={{ height, position: 'relative', cursor: isDragging ? 'grabbing' : 'grab' }}
      onMouseDown={handleMouseDown}
      onMouseMove={handleMouseMove}
      onMouseUp={handleMouseUp}
      onMouseLeave={handleMouseUp}
    >
      {/* Time markers */}
      {ticks.map((tick) => {
        const left = (tick / totalDuration) * 100;
        return (
          <div
            key={tick}
            className="time-marker"
            style={{
              position: 'absolute',
              left: `${left}%`,
              bottom: 0,
              height: '40%',
              width: 1,
              backgroundColor: '#666',
            }}
          >
            <span
              style={{
                position: 'absolute',
                bottom: 4,
                left: 2,
                fontSize: 10,
                color: '#999',
                whiteSpace: 'nowrap',
              }}
            >
              {formatTime(tick)}s
            </span>
          </div>
        );
      })}

      {/* Playhead */}
      <div
        className="playhead"
        style={{
          position: 'absolute',
          left: `${(currentTime / totalDuration) * 100}%`,
          top: 0,
          bottom: 0,
          width: 2,
          backgroundColor: '#ff6b6b',
          transform: 'translateX(-50%)',
          pointerEvents: 'none',
        }}
      >
        <div
          style={{
            position: 'absolute',
            top: -5,
            left: -4,
            width: 10,
            height: 10,
            backgroundColor: '#ff6b6b',
            borderRadius: '50%',
          }}
        />
        <div
          style={{
            position: 'absolute',
            top: 4,
            left: -20,
            padding: '2px 6px',
            backgroundColor: '#ff6b6b',
            color: 'white',
            fontSize: 11,
            borderRadius: 3,
          }}
        >
          {formatTime(currentTime)}
        </div>
      </div>
    </div>
  );
};

// =============================================================================
// Track List Component
// =============================================================================

interface TrackListProps {
  className?: string;
}

export const TrackList: React.FC<TrackListProps> = ({ className = '' }) => {
  const [tracks] = useAtom(animationTracksAtom);
  const [selectedTrack, setSelectedTrack] = useAtom(selectedTrackAtom);
  const deleteTrack = useSetAtom(deleteTrackAtom);
  const duplicateTrack = useSetAtom(duplicateTrackAtom);
  const currentTime = useAtomValue(currentTimeAtom);
  const totalDuration = useAtomValue(totalDurationAtom);

  return (
    <div className={`track-list ${className}`} style={{ overflowY: 'auto' }}>
      {tracks.map((track, index) => (
        <TrackRow
          key={track.id}
          track={track}
          index={index}
          isSelected={selectedTrack?.id === track.id}
          onSelect={() => setSelectedTrack(track)}
          onDelete={() => deleteTrack(track.id)}
          onDuplicate={() => duplicateTrack(track.id)}
          currentTime={currentTime}
          totalDuration={totalDuration}
        />
      ))}
    </div>
  );
};

interface TrackRowProps {
  track: AnimationTrack;
  index: number;
  isSelected: boolean;
  onSelect: () => void;
  onDelete: () => void;
  onDuplicate: () => void;
  currentTime: number;
  totalDuration: number;
}

const TrackRow: React.FC<TrackRowProps> = ({
  track,
  index,
  isSelected,
  onSelect,
  onDelete,
  onDuplicate,
  currentTime,
  totalDuration,
}) => {
  const [isExpanded, setIsExpanded] = useState(false);

  const isActive = currentTime >= (track.delay || 0) && 
    currentTime <= (track.delay || 0) + track.duration;

  const left = ((track.delay || 0) / totalDuration) * 100;
  const width = (track.duration / totalDuration) * 100;

  return (
    <div
      className={`track-row ${isSelected ? 'selected' : ''} ${isActive ? 'active' : ''}`}
      style={{
        display: 'flex',
        alignItems: 'center',
        padding: '8px 12px',
        borderBottom: '1px solid #333',
        backgroundColor: isSelected ? '#2a2a3a' : index % 2 === 0 ? '#1a1a2e' : '#151525',
        cursor: 'pointer',
      }}
      onClick={onSelect}
    >
      {/* Track header */}
      <div style={{ width: 200, flexShrink: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <button
            onClick={(e) => {
              e.stopPropagation();
              setIsExpanded(!isExpanded);
            }}
            style={{
              width: 16,
              height: 16,
              border: 'none',
              background: 'transparent',
              color: '#999',
              cursor: 'pointer',
            }}
          >
            {isExpanded ? '▼' : '▶'}
          </button>
          <span style={{ color: '#fff', fontSize: 13, fontWeight: 500 }}>
            {track.target}
          </span>
        </div>
        <div style={{ color: '#888', fontSize: 11, marginLeft: 24, marginTop: 2 }}>
          {track.property}
        </div>
      </div>

      {/* Track timeline bar */}
      <div style={{ flex: 1, position: 'relative', height: 30 }}>
        <div
          style={{
            position: 'absolute',
            left: `${left}%`,
            width: `${width}%`,
            height: '100%',
            backgroundColor: isActive ? '#4ecdc4' : '#45b7d1',
            borderRadius: 4,
            opacity: 0.8,
          }}
        />
        
        {/* Keyframe markers */}
        {track.keyframes?.map((kf, i) => {
          const kfLeft = ((kf.time / track.duration) * width) + left;
          return (
            <div
              key={i}
              style={{
                position: 'absolute',
                left: `${kfLeft}%`,
                top: '50%',
                transform: 'translate(-50%, -50%)',
                width: 8,
                height: 8,
                backgroundColor: '#fff',
                borderRadius: '50%',
                border: '2px solid #333',
              }}
            />
          );
        })}
      </div>

      {/* Actions */}
      <div style={{ display: 'flex', gap: 8, marginLeft: 12 }}>
        <button
          onClick={(e) => {
            e.stopPropagation();
            onDuplicate();
          }}
          style={{
            padding: '4px 8px',
            fontSize: 11,
            border: '1px solid #555',
            backgroundColor: '#333',
            color: '#fff',
            borderRadius: 4,
            cursor: 'pointer',
          }}
        >
          Duplicate
        </button>
        <button
          onClick={(e) => {
            e.stopPropagation();
            onDelete();
          }}
          style={{
            padding: '4px 8px',
            fontSize: 11,
            border: '1px solid #c0392b',
            backgroundColor: '#c0392b',
            color: '#fff',
            borderRadius: 4,
            cursor: 'pointer',
          }}
        >
          Delete
        </button>
      </div>

      {/* Expanded keyframe editor */}
      {isExpanded && (
        <div
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            top: '100%',
            backgroundColor: '#1a1a2e',
            borderTop: '1px solid #333',
            padding: 12,
            zIndex: 10,
          }}
        >
          <KeyframeEditor track={track} />
        </div>
      )}
    </div>
  );
};

// =============================================================================
// Keyframe Editor Component
// =============================================================================

interface KeyframeEditorProps {
  track: AnimationTrack;
}

const KeyframeEditor: React.FC<KeyframeEditorProps> = ({ track }) => {
  const addKeyframe = useSetAtom(addKeyframeAtom);
  const updateKeyframe = useSetAtom(updateKeyframeAtom);
  const deleteKeyframe = useSetAtom(deleteKeyframeAtom);
  const [snapToGrid] = useAtom(snapToGridAtom);
  const [gridSize] = useAtom(gridSizeAtom);

  const handleAddKeyframe = () => {
    const newKeyframe: AnimationKeyframe = {
      time: 0,
      value: track.from ?? 0,
      easing: 'linear',
    };
    addKeyframe({ trackId: track.id, keyframe: newKeyframe });
  };

  return (
    <div className="keyframe-editor" style={{ padding: 12 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
        <h4 style={{ color: '#fff', margin: 0, fontSize: 14 }}>Keyframes</h4>
        <button
          onClick={handleAddKeyframe}
          style={{
            padding: '4px 12px',
            fontSize: 12,
            backgroundColor: '#4ecdc4',
            color: '#fff',
            border: 'none',
            borderRadius: 4,
            cursor: 'pointer',
          }}
        >
          + Add Keyframe
        </button>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {track.keyframes?.map((kf, index) => (
          <div
            key={index}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              padding: 8,
              backgroundColor: '#252540',
              borderRadius: 4,
            }}
          >
            <span style={{ color: '#888', fontSize: 12, width: 20 }}>{index}</span>
            
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <label style={{ color: '#999', fontSize: 11 }}>Time (ms)</label>
              <input
                type="number"
                value={kf.time}
                onChange={(e) =>
                  updateKeyframe({
                    trackId: track.id,
                    index,
                    keyframe: { ...kf, time: Number(e.target.value) },
                  })
                }
                style={{
                  width: 80,
                  padding: '4px 8px',
                  backgroundColor: '#1a1a2e',
                  border: '1px solid #444',
                  color: '#fff',
                  borderRadius: 3,
                  fontSize: 12,
                }}
              />
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <label style={{ color: '#999', fontSize: 11 }}>Value</label>
              <input
                type="text"
                value={String(kf.value)}
                onChange={(e) =>
                  updateKeyframe({
                    trackId: track.id,
                    index,
                    keyframe: { ...kf, value: e.target.value },
                  })
                }
                style={{
                  width: 120,
                  padding: '4px 8px',
                  backgroundColor: '#1a1a2e',
                  border: '1px solid #444',
                  color: '#fff',
                  borderRadius: 3,
                  fontSize: 12,
                }}
              />
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <label style={{ color: '#999', fontSize: 11 }}>Easing</label>
              <select
                value={kf.easing}
                onChange={(e) =>
                  updateKeyframe({
                    trackId: track.id,
                    index,
                    keyframe: { ...kf, easing: e.target.value },
                  })
                }
                style={{
                  padding: '4px 8px',
                  backgroundColor: '#1a1a2e',
                  border: '1px solid #444',
                  color: '#fff',
                  borderRadius: 3,
                  fontSize: 12,
                }}
              >
                <option value="linear">Linear</option>
                <option value="ease-in">Ease In</option>
                <option value="ease-out">Ease Out</option>
                <option value="ease-in-out">Ease In Out</option>
                <option value="spring">Spring</option>
                <option value="bounce">Bounce</option>
              </select>
            </div>

            <button
              onClick={() => deleteKeyframe({ trackId: track.id, index })}
              style={{
                marginLeft: 'auto',
                padding: '4px 8px',
                fontSize: 11,
                backgroundColor: '#c0392b',
                color: '#fff',
                border: 'none',
                borderRadius: 3,
                cursor: 'pointer',
              }}
            >
              Delete
            </button>
          </div>
        ))}
      </div>
    </div>
  );
};

// =============================================================================
// Property Panel Component
// =============================================================================

interface PropertyPanelProps {
  className?: string;
}

export const PropertyPanel: React.FC<PropertyPanelProps> = ({ className = '' }) => {
  const [selectedTrack, setSelectedTrack] = useAtom(selectedTrackAtom);
  const updateTrack = useSetAtom(updateTrackAtom);

  if (!selectedTrack) {
    return (
      <div
        className={`property-panel ${className}`}
        style={{
          padding: 20,
          color: '#888',
          textAlign: 'center',
        }}
      >
        Select a track to edit properties
      </div>
    );
  }

  const handleChange = (field: keyof AnimationTrack, value: any) => {
    updateTrack({
      ...selectedTrack,
      [field]: value,
    });
  };

  return (
    <div
      className={`property-panel ${className}`}
      style={{
        padding: 16,
        backgroundColor: '#1a1a2e',
        borderLeft: '1px solid #333',
        overflowY: 'auto',
      }}
    >
      <h3 style={{ color: '#fff', margin: '0 0 16px 0', fontSize: 16 }}>Track Properties</h3>

      <PropertyGroup title="Target">
        <PropertyField label="Target ID">
          <input
            type="text"
            value={selectedTrack.target}
            onChange={(e) => handleChange('target', e.target.value)}
            style={propertyInputStyle}
          />
        </PropertyField>

        <PropertyField label="Property">
          <select
            value={selectedTrack.property}
            onChange={(e) => handleChange('property', e.target.value)}
            style={propertyInputStyle}
          >
            <option value="transform">Transform</option>
            <option value="opacity">Opacity</option>
            <option value="width">Width</option>
            <option value="height">Height</option>
            <option value="backgroundColor">Background Color</option>
            <option value="color">Color</option>
            <option value="left">Left</option>
            <option value="top">Top</option>
          </select>
        </PropertyField>
      </PropertyGroup>

      <PropertyGroup title="Timing">
        <PropertyField label="Duration (ms)">
          <input
            type="number"
            value={selectedTrack.duration}
            onChange={(e) => handleChange('duration', Number(e.target.value))}
            style={propertyInputStyle}
          />
        </PropertyField>

        <PropertyField label="Delay (ms)">
          <input
            type="number"
            value={selectedTrack.delay || 0}
            onChange={(e) => handleChange('delay', Number(e.target.value))}
            style={propertyInputStyle}
          />
        </PropertyField>

        <PropertyField label="Easing">
          <select
            value={selectedTrack.easing || 'ease-in-out'}
            onChange={(e) => handleChange('easing', e.target.value)}
            style={propertyInputStyle}
          >
            <option value="linear">Linear</option>
            <option value="ease">Ease</option>
            <option value="ease-in">Ease In</option>
            <option value="ease-out">Ease Out</option>
            <option value="ease-in-out">Ease In Out</option>
            <option value="spring">Spring</option>
            <option value="bounce">Bounce</option>
            <option value="elastic">Elastic</option>
          </select>
        </PropertyField>
      </PropertyGroup>

      <PropertyGroup title="Values">
        <PropertyField label="From">
          <input
            type="text"
            value={String(selectedTrack.from ?? '')}
            onChange={(e) => handleChange('from', e.target.value)}
            style={propertyInputStyle}
          />
        </PropertyField>

        <PropertyField label="To">
          <input
            type="text"
            value={String(selectedTrack.to)}
            onChange={(e) => handleChange('to', e.target.value)}
            style={propertyInputStyle}
          />
        </PropertyField>
      </PropertyGroup>

      <PropertyGroup title="Playback">
        <PropertyField label="Repeat">
          <input
            type="number"
            value={selectedTrack.repeat || 0}
            onChange={(e) => handleChange('repeat', Number(e.target.value))}
            style={propertyInputStyle}
          />
        </PropertyField>

        <PropertyField label="Yoyo (Reverse)">
          <input
            type="checkbox"
            checked={selectedTrack.yoyo || false}
            onChange={(e) => handleChange('yoyo', e.target.checked)}
            style={{ width: 'auto' }}
          />
        </PropertyField>
      </PropertyGroup>
    </div>
  );
};

const PropertyGroup: React.FC<{ title: string; children: React.ReactNode }> = ({
  title,
  children,
}) => (
  <div style={{ marginBottom: 20 }}>
    <h4 style={{ color: '#888', fontSize: 12, textTransform: 'uppercase', margin: '0 0 12px 0' }}>
      {title}
    </h4>
    {children}
  </div>
);

const PropertyField: React.FC<{ label: string; children: React.ReactNode }> = ({
  label,
  children,
}) => (
  <div style={{ marginBottom: 12 }}>
    <label style={{ display: 'block', color: '#aaa', fontSize: 11, marginBottom: 4 }}>
      {label}
    </label>
    {children}
  </div>
);

const propertyInputStyle: React.CSSProperties = {
  width: '100%',
  padding: '8px 12px',
  backgroundColor: '#252540',
  border: '1px solid #444',
  color: '#fff',
  borderRadius: 4,
  fontSize: 13,
};

// =============================================================================
// Playback Controls Component
// =============================================================================

interface PlaybackControlsProps {
  className?: string;
}

export const PlaybackControls: React.FC<PlaybackControlsProps> = ({ className = '' }) => {
  const [isPlaying, setIsPlaying] = useAtom(isPlayingAtom);
  const [currentTime, setCurrentTime] = useAtom(currentTimeAtom);
  const [playbackSpeed, setPlaybackSpeed] = useAtom(playbackSpeedAtom);
  const totalDuration = useAtomValue(totalDurationAtom);
  const canUndo = useAtomValue(canUndoAtom);
  const canRedo = useAtomValue(canRedoAtom);
  const undo = useSetAtom(undoAtom);
  const redo = useSetAtom(redoAtom);

  const handlePlay = () => setIsPlaying(true);
  const handlePause = () => setIsPlaying(false);
  const handleStop = () => {
    setIsPlaying(false);
    setCurrentTime(0);
  };
  const handleSkipBackward = () => setCurrentTime(Math.max(0, currentTime - 1000));
  const handleSkipForward = () => setCurrentTime(Math.min(totalDuration, currentTime + 1000));
  const handleGoToStart = () => setCurrentTime(0);
  const handleGoToEnd = () => setCurrentTime(totalDuration);

  const formatTime = (ms: number): string => {
    const seconds = Math.floor(ms / 1000);
    const milliseconds = Math.floor((ms % 1000) / 10);
    return `${seconds.toString().padStart(2, '0')}.${milliseconds.toString().padStart(2, '0')}`;
  };

  return (
    <div
      className={`playback-controls ${className}`}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: '12px 16px',
        backgroundColor: '#1a1a2e',
        borderTop: '1px solid #333',
      }}
    >
      {/* Transport controls */}
      <div style={{ display: 'flex', gap: 4 }}>
        <ControlButton onClick={handleGoToStart} title="Go to start">
          ⏮
        </ControlButton>
        <ControlButton onClick={handleSkipBackward} title="Skip backward">
          ⏪
        </ControlButton>
        <ControlButton
          onClick={isPlaying ? handlePause : handlePlay}
          title={isPlaying ? 'Pause' : 'Play'}
          primary
        >
          {isPlaying ? '⏸' : '▶'}
        </ControlButton>
        <ControlButton onClick={handleStop} title="Stop">
          ⏹
        </ControlButton>
        <ControlButton onClick={handleSkipForward} title="Skip forward">
          ⏩
        </ControlButton>
        <ControlButton onClick={handleGoToEnd} title="Go to end">
          ⏭
        </ControlButton>
      </div>

      {/* Time display */}
      <div
        style={{
          padding: '6px 12px',
          backgroundColor: '#252540',
          borderRadius: 4,
          color: '#fff',
          fontFamily: 'monospace',
          fontSize: 14,
        }}
      >
        {formatTime(currentTime)} / {formatTime(totalDuration)}
      </div>

      {/* Playback speed */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ color: '#888', fontSize: 12 }}>Speed:</span>
        <select
          value={playbackSpeed}
          onChange={(e) => setPlaybackSpeed(Number(e.target.value))}
          style={{
            padding: '4px 8px',
            backgroundColor: '#252540',
            border: '1px solid #444',
            color: '#fff',
            borderRadius: 3,
            fontSize: 12,
          }}
        >
          <option value={0.25}>0.25x</option>
          <option value={0.5}>0.5x</option>
          <option value={1}>1x</option>
          <option value={1.5}>1.5x</option>
          <option value={2}>2x</option>
        </select>
      </div>

      {/* Undo/Redo */}
      <div style={{ marginLeft: 'auto', display: 'flex', gap: 4 }}>
        <ControlButton onClick={undo} disabled={!canUndo} title="Undo">
          ↩
        </ControlButton>
        <ControlButton onClick={redo} disabled={!canRedo} title="Redo">
          ↪
        </ControlButton>
      </div>
    </div>
  );
};

const ControlButton: React.FC<{
  onClick: () => void;
  children: React.ReactNode;
  title?: string;
  primary?: boolean;
  disabled?: boolean;
}> = ({ onClick, children, title, primary, disabled }) => (
  <button
    onClick={onClick}
    disabled={disabled}
    title={title}
    style={{
      width: 36,
      height: 36,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: primary ? '#4ecdc4' : disabled ? '#333' : '#252540',
      color: disabled ? '#666' : primary ? '#fff' : '#aaa',
      border: 'none',
      borderRadius: 4,
      cursor: disabled ? 'not-allowed' : 'pointer',
      fontSize: 16,
      opacity: disabled ? 0.5 : 1,
    }}
  >
    {children}
  </button>
);

// =============================================================================
// Main Animation Editor Component
// =============================================================================

interface AnimationEditorProps {
  className?: string;
  onSave?: (tracks: AnimationTrack[]) => void;
  onPreview?: () => void;
}

export const AnimationEditor: React.FC<AnimationEditorProps> = ({
  className = '',
  onSave,
  onPreview,
}) => {
  const tracks = useAtomValue(animationTracksAtom);
  const [showPreview, setShowPreview] = useState(false);

  const handleSave = () => {
    onSave?.(tracks);
  };

  return (
    <div
      className={`animation-editor ${className}`}
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100vh',
        backgroundColor: '#0f0f1a',
        color: '#fff',
        fontFamily: 'system-ui, -apple-system, sans-serif',
      }}
    >
      {/* Header */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '12px 16px',
          backgroundColor: '#1a1a2e',
          borderBottom: '1px solid #333',
        }}
      >
        <h2 style={{ margin: 0, fontSize: 18, fontWeight: 600 }}>Animation Editor</h2>
        <div style={{ display: 'flex', gap: 8 }}>
          <button
            onClick={() => setShowPreview(!showPreview)}
            style={{
              padding: '8px 16px',
              backgroundColor: showPreview ? '#4ecdc4' : '#252540',
              color: '#fff',
              border: 'none',
              borderRadius: 4,
              cursor: 'pointer',
              fontSize: 13,
            }}
          >
            {showPreview ? 'Hide Preview' : 'Show Preview'}
          </button>
          <button
            onClick={handleSave}
            style={{
              padding: '8px 16px',
              backgroundColor: '#45b7d1',
              color: '#fff',
              border: 'none',
              borderRadius: 4,
              cursor: 'pointer',
              fontSize: 13,
            }}
          >
            Save Animation
          </button>
        </div>
      </div>

      {/* Main content */}
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {/* Left panel - Tracks */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
          {/* Timeline scrubber */}
          <TimelineScrubber height={60} />

          {/* Track list */}
          <div style={{ flex: 1, overflowY: 'auto' }}>
            <TrackList />
          </div>

          {/* Playback controls */}
          <PlaybackControls />
        </div>

        {/* Right panel - Properties */}
        <div style={{ width: 280, flexShrink: 0 }}>
          <PropertyPanel />
        </div>
      </div>

      {/* Preview panel */}
      {showPreview && (
        <div
          style={{
            height: 300,
            backgroundColor: '#151525',
            borderTop: '1px solid #333',
            padding: 20,
          }}
        >
          <h3 style={{ margin: '0 0 16px 0', fontSize: 14, color: '#888' }}>Preview</h3>
          <AnimationPreview />
        </div>
      )}
    </div>
  );
};

// =============================================================================
// Animation Preview Component
// =============================================================================

const AnimationPreview: React.FC = () => {
  const tracks = useAtomValue(animationTracksAtom);
  const currentTime = useAtomValue(currentTimeAtom);
  const [elements, setElements] = useState<Record<string, any>>({});

  useEffect(() => {
    const newElements: Record<string, any> = {};
    
    tracks.forEach((track) => {
      const delay = track.delay || 0;
      const trackTime = currentTime - delay;
      
      if (trackTime < 0 || trackTime > track.duration) {
        return;
      }

      const progress = trackTime / track.duration;
      const easing = track.easing || 'linear';
      
      // Calculate interpolated value
      const value = interpolateValue(track.from, track.to, progress, easing);
      
      if (!newElements[track.target]) {
        newElements[track.target] = {};
      }
      newElements[track.target][track.property] = value;
    });

    setElements(newElements);
  }, [tracks, currentTime]);

  return (
    <div
      style={{
        position: 'relative',
        width: '100%',
        height: '100%',
        backgroundColor: '#1a1a2e',
        borderRadius: 8,
        overflow: 'hidden',
      }}
    >
      {/* Sample animated elements */}
      {Object.entries(elements).map(([id, styles]) => (
        <div
          key={id}
          id={id}
          style={{
            position: 'absolute',
            width: 50,
            height: 50,
            backgroundColor: '#4ecdc4',
            borderRadius: 8,
            ...styles,
          }}
        />
      ))}
      
      {/* Grid overlay */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          backgroundImage: `
            linear-gradient(to right, #333 1px, transparent 1px),
            linear-gradient(to bottom, #333 1px, transparent 1px)
          `,
          backgroundSize: '20px 20px',
          opacity: 0.3,
          pointerEvents: 'none',
        }}
      />
    </div>
  );
};

function interpolateValue(from: any, to: any, progress: number, easing: string): any {
  // Apply easing
  const easedProgress = applyEasing(progress, easing);
  
  if (typeof from === 'number' && typeof to === 'number') {
    return from + (to - from) * easedProgress;
  }
  
  // CSS value interpolation (simplified)
  return progress < 0.5 ? from : to;
}

function applyEasing(t: number, easing: string): number {
  const easingFunctions: Record<string, (t: number) => number> = {
    linear: (t) => t,
    'ease-in': (t) => t * t,
    'ease-out': (t) => 1 - (1 - t) * (1 - t),
    'ease-in-out': (t) => t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2,
  };
  
  return (easingFunctions[easing] || easingFunctions.linear)(t);
}
