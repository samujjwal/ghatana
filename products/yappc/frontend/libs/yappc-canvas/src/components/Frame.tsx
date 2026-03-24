/**
 * Frame Component
 *
 * Visual container for grouping artifacts by lifecycle phase.
 * Frames are first-class citizens on the canvas with:
 * - Phase-specific styling (color, icon)
 * - Collapsible content
 * - Drag handles
 * - Resize handles
 * - Mini toolbar
 *
 * @doc.type component
 * @doc.purpose Frame visualization
 * @doc.layer core
 * @doc.pattern Container
 */

import React, { useState, useCallback } from 'react';
import { useAtom, useSetAtom } from 'jotai';
import { LifecyclePhase } from '../types/lifecycle';
import { getPhaseDefinition } from '../config/phase-colors';
import { CANVAS_Z_INDEX } from '../config/z-index';
import { canvasSelectionAtom } from '../state/atoms';

export interface FrameProps {
  /** Frame ID */
  id: string;
  /** Lifecycle phase */
  phase: LifecyclePhase;
  /** Frame label */
  label: string;
  /** Frame position */
  x: number;
  y: number;
  /** Frame dimensions */
  width: number;
  height: number;
  /** Whether frame is collapsed */
  collapsed?: boolean;
  /** Whether frame is selected */
  selected?: boolean;
  /** Child artifacts */
  children?: React.ReactNode;
  /** Callback when frame is selected */
  onSelect?: (id: string) => void;
  /** Callback when frame is moved */
  onMove?: (id: string, x: number, y: number) => void;
  /** Callback when frame is resized */
  onResize?: (id: string, width: number, height: number) => void;
  /** Callback when frame is collapsed/expanded */
  onToggleCollapsed?: (id: string, collapsed: boolean) => void;
}

/**
 * Frame Component
 */
export const Frame: React.FC<FrameProps> = ({
  id,
  phase,
  label,
  x,
  y,
  width,
  height,
  collapsed = false,
  selected = false,
  children,
  onSelect,
  onMove,
  onResize,
  onToggleCollapsed,
}) => {
  const phaseDefinition = getPhaseDefinition(phase);
  const [isHovered, setIsHovered] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const [isResizing, setIsResizing] = useState(false);

  // Handle selection
  const handleSelect = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onSelect?.(id);
    },
    [id, onSelect]
  );

  // Handle collapse toggle
  const handleToggleCollapsed = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onToggleCollapsed?.(id, !collapsed);
    },
    [id, collapsed, onToggleCollapsed]
  );

  // Computed styles
  const headerHeight = 40;
  const collapsedHeight = headerHeight;
  const actualHeight = collapsed ? collapsedHeight : height;

  const borderColor = selected
    ? phaseDefinition.colors.primary
    : phaseDefinition.colors.border;

  const backgroundColor = selected
    ? `${phaseDefinition.colors.background}88`
    : `${phaseDefinition.colors.background}44`;

  return (
    <div
      className="canvas-frame"
      data-frame-id={id}
      data-phase={phase}
      data-selected={selected}
      data-collapsed={collapsed}
      style={{
        position: 'absolute',
        left: x,
        top: y,
        width,
        height: actualHeight,
        zIndex: CANVAS_Z_INDEX.FRAMES,
        border: `2px solid ${borderColor}`,
        borderRadius: '8px',
        backgroundColor,
        transition: 'all 0.2s ease-in-out',
        cursor: isDragging ? 'grabbing' : 'grab',
        boxShadow: selected
          ? `0 4px 16px ${phaseDefinition.colors.primary}40`
          : isHovered
            ? '0 2px 8px rgba(0,0,0,0.1)'
            : 'none',
      }}
      onClick={handleSelect}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
    >
      {/* Header */}
      <div
        className="frame-header"
        style={{
          height: headerHeight,
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          padding: '0 12px',
          borderBottom: collapsed ? 'none' : `1px solid ${borderColor}`,
          background: `linear-gradient(to bottom, ${phaseDefinition.colors.background}66, ${phaseDefinition.colors.background}33)`,
          borderTopLeftRadius: '6px',
          borderTopRightRadius: '6px',
          cursor: 'grab',
        }}
      >
        {/* Phase Icon */}
        <div
          className="frame-icon"
          style={{
            fontSize: '16px',
            flexShrink: 0,
          }}
        >
          {phaseDefinition.metadata.icon}
        </div>

        {/* Label */}
        <div
          className="frame-label"
          style={{
            flex: 1,
            fontSize: '14px',
            fontWeight: 600,
            color: phaseDefinition.colors.text,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {label}
        </div>

        {/* Collapse Button */}
        <button
          className="frame-collapse-btn"
          onClick={handleToggleCollapsed}
          style={{
            width: '24px',
            height: '24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            border: 'none',
            background: 'transparent',
            cursor: 'pointer',
            borderRadius: '4px',
            color: phaseDefinition.colors.text,
            opacity: 0.7,
            transition: 'all 0.15s ease-in-out',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.opacity = '1';
            e.currentTarget.style.background = phaseDefinition.colors.hover;
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.opacity = '0.7';
            e.currentTarget.style.background = 'transparent';
          }}
        >
          {collapsed ? '▼' : '▲'}
        </button>

        {/* Mini Toolbar */}
        {(isHovered || selected) && !collapsed && (
          <div
            className="frame-toolbar"
            style={{
              display: 'flex',
              gap: '4px',
              marginLeft: '8px',
            }}
          >
            <button
              className="frame-toolbar-btn"
              title="Add artifact"
              style={{
                width: '24px',
                height: '24px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                border: 'none',
                background: phaseDefinition.colors.background,
                cursor: 'pointer',
                borderRadius: '4px',
                color: phaseDefinition.colors.text,
                fontSize: '16px',
                transition: 'all 0.15s ease-in-out',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = phaseDefinition.colors.hover;
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background =
                  phaseDefinition.colors.background;
              }}
            >
              +
            </button>
          </div>
        )}
      </div>

      {/* Content */}
      {!collapsed && (
        <div
          className="frame-content"
          style={{
            position: 'relative',
            width: '100%',
            height: `calc(100% - ${headerHeight}px)`,
            overflow: 'hidden',
            padding: '12px',
          }}
        >
          {children}
        </div>
      )}

      {/* Resize Handle */}
      {!collapsed && (isHovered || selected) && (
        <div
          className="frame-resize-handle"
          style={{
            position: 'absolute',
            right: 0,
            bottom: 0,
            width: '16px',
            height: '16px',
            cursor: 'nwse-resize',
            background: phaseDefinition.colors.primary,
            borderTopLeftRadius: '4px',
            borderBottomRightRadius: '6px',
            opacity: 0.6,
            transition: 'opacity 0.15s ease-in-out',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.opacity = '1';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.opacity = '0.6';
          }}
        />
      )}

      {/* Selection Indicator */}
      {selected && (
        <div
          className="frame-selection-indicator"
          style={{
            position: 'absolute',
            inset: -4,
            border: `2px dashed ${phaseDefinition.colors.primary}`,
            borderRadius: '10px',
            pointerEvents: 'none',
            animation: 'frame-selection-pulse 2s ease-in-out infinite',
          }}
        />
      )}

      <style>{`
        @keyframes frame-selection-pulse {
          0%, 100% {
            opacity: 0.5;
          }
          50% {
            opacity: 1;
          }
        }
      `}</style>
    </div>
  );
};
