/**
 * Zoom HUD
 *
 * Floating zoom controls with LOD indicators.
 * Shows current zoom level and semantic zoom mode.
 *
 * Features:
 * - Zoom in/out/reset buttons
 * - Current zoom percentage
 * - LOD mode indicator (Overview/Focus/Detail)
 * - Fit to view button
 *
 * @doc.type component
 * @doc.purpose Zoom control interface
 * @doc.layer core
 * @doc.pattern HUD
 */

import React from 'react';
import { useAtom, useAtomValue } from 'jotai';
import { canvasViewportAtom } from '../state/atoms';
import { CANVAS_Z_INDEX } from '../config/z-index';

export interface ZoomHUDProps {
  /** Callback when zoom changes */
  onZoomChange?: (zoom: number) => void;
  /** Callback for fit-to-view */
  onFitToView?: () => void;
}

/**
 * Get semantic zoom mode from zoom level
 */
function getSemanticZoomMode(zoom: number): {
  mode: 'overview' | 'focus' | 'detail';
  label: string;
  icon: string;
} {
  if (zoom <= 0.5) {
    return { mode: 'overview', label: 'Overview', icon: '🗺️' };
  } else if (zoom <= 1.5) {
    return { mode: 'focus', label: 'Focus', icon: '🎯' };
  } else {
    return { mode: 'detail', label: 'Detail', icon: '🔍' };
  }
}

/**
 * Zoom HUD Component
 */
export const ZoomHUD: React.FC<ZoomHUDProps> = ({
  onZoomChange,
  onFitToView,
}) => {
  const [viewport, setViewport] = useAtom(canvasViewportAtom);

  const currentZoom = viewport.zoom || 1;
  const semanticMode = getSemanticZoomMode(currentZoom);

  // Zoom in
  const handleZoomIn = () => {
    const newZoom = Math.min(currentZoom * 1.2, 4);
    setViewport({ ...viewport, zoom: newZoom });
    onZoomChange?.(newZoom);
  };

  // Zoom out
  const handleZoomOut = () => {
    const newZoom = Math.max(currentZoom / 1.2, 0.1);
    setViewport({ ...viewport, zoom: newZoom });
    onZoomChange?.(newZoom);
  };

  // Reset zoom
  const handleZoomReset = () => {
    setViewport({ ...viewport, zoom: 1 });
    onZoomChange?.(1);
  };

  // Fit to view
  const handleFitToView = () => {
    onFitToView?.();
  };

  return (
    <div
      className="zoom-hud"
      style={{
        position: 'fixed',
        bottom: '16px',
        left: '16px',
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
        padding: '8px',
        background: 'var(--color-surface-elevated, #ffffff)',
        border: '1px solid var(--color-border, #e0e0e0)',
        borderRadius: '8px',
        boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
        zIndex: CANVAS_Z_INDEX.FLOATING_CONTROLS,
      }}
    >
      {/* Semantic Mode Indicator */}
      <div
        className="zoom-mode-indicator"
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '6px',
          padding: '4px 8px',
          background: 'var(--color-surface-paper, #f5f5f5)',
          borderRadius: '6px',
          fontSize: '12px',
          fontWeight: 600,
          color: 'var(--color-text-primary, #212121)',
        }}
      >
        <span>{semanticMode.icon}</span>
        <span>{semanticMode.label}</span>
      </div>

      {/* Divider */}
      <div
        style={{
          width: '1px',
          height: '24px',
          background: 'var(--color-border, #e0e0e0)',
        }}
      />

      {/* Zoom Out */}
      <button
        className="zoom-btn"
        onClick={handleZoomOut}
        disabled={currentZoom <= 0.1}
        title="Zoom Out (Ctrl+-)"
        style={{
          width: '32px',
          height: '32px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          border: 'none',
          background: 'transparent',
          borderRadius: '6px',
          cursor: currentZoom <= 0.1 ? 'not-allowed' : 'pointer',
          opacity: currentZoom <= 0.1 ? 0.4 : 1,
          fontSize: '18px',
          fontWeight: 600,
          color: 'var(--color-text-primary, #212121)',
          transition: 'all 0.15s ease-in-out',
        }}
        onMouseEnter={(e) => {
          if (currentZoom > 0.1) {
            e.currentTarget.style.background =
              'var(--color-hover-background, #f5f5f5)';
          }
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.background = 'transparent';
        }}
      >
        −
      </button>

      {/* Zoom Level Display */}
      <button
        className="zoom-level-display"
        onClick={handleZoomReset}
        title="Reset Zoom (Ctrl+0)"
        style={{
          minWidth: '60px',
          height: '32px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          border: 'none',
          background: 'transparent',
          borderRadius: '6px',
          cursor: 'pointer',
          fontSize: '13px',
          fontWeight: 600,
          color: 'var(--color-text-primary, #212121)',
          transition: 'all 0.15s ease-in-out',
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.background =
            'var(--color-hover-background, #f5f5f5)';
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.background = 'transparent';
        }}
      >
        {Math.round(currentZoom * 100)}%
      </button>

      {/* Zoom In */}
      <button
        className="zoom-btn"
        onClick={handleZoomIn}
        disabled={currentZoom >= 4}
        title="Zoom In (Ctrl++)"
        style={{
          width: '32px',
          height: '32px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          border: 'none',
          background: 'transparent',
          borderRadius: '6px',
          cursor: currentZoom >= 4 ? 'not-allowed' : 'pointer',
          opacity: currentZoom >= 4 ? 0.4 : 1,
          fontSize: '18px',
          fontWeight: 600,
          color: 'var(--color-text-primary, #212121)',
          transition: 'all 0.15s ease-in-out',
        }}
        onMouseEnter={(e) => {
          if (currentZoom < 4) {
            e.currentTarget.style.background =
              'var(--color-hover-background, #f5f5f5)';
          }
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.background = 'transparent';
        }}
      >
        +
      </button>

      {/* Divider */}
      <div
        style={{
          width: '1px',
          height: '24px',
          background: 'var(--color-border, #e0e0e0)',
        }}
      />

      {/* Fit to View */}
      <button
        className="zoom-fit-btn"
        onClick={handleFitToView}
        title="Fit to View (Ctrl+1)"
        style={{
          width: '32px',
          height: '32px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          border: 'none',
          background: 'transparent',
          borderRadius: '6px',
          cursor: 'pointer',
          fontSize: '16px',
          color: 'var(--color-text-primary, #212121)',
          transition: 'all 0.15s ease-in-out',
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.background =
            'var(--color-hover-background, #f5f5f5)';
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.background = 'transparent';
        }}
      >
        ⛶
      </button>
    </div>
  );
};
