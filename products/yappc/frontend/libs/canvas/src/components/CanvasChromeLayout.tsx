/**
 * Canvas Chrome Layout
 *
 * Master layout container for the unified canvas.
 * Manages z-index regions, visibility states, and panel coordination.
 *
 * Architecture:
 * - Enforces strict z-index hierarchy
 * - Manages chrome visibility state
 * - Coordinates panel reveal/hide
 * - Provides keyboard shortcuts
 *
 * @doc.type component
 * @doc.purpose Master layout orchestration
 * @doc.layer core
 * @doc.pattern Container
 */

import React, { useCallback } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { CANVAS_Z_INDEX } from '../config/z-index';
import {
  chromeCalmModeAtom,
  chromeLeftRailVisibleAtom,
  chromeContextBarVisibleAtom,
  chromeInspectorVisibleAtom,
  chromeOutlineVisibleAtom,
  chromeMinimapVisibleAtom,
} from '../state/chrome-atoms';

interface CanvasChromeLayoutProps {
  /** Main canvas content */
  children: React.ReactNode;
  /** Left rail content (Palette, Connectors) */
  leftRail?: React.ReactNode;
  /** Context bar content (Frame tools, Element tools) */
  contextBar?: React.ReactNode;
  /** Inspector panel content */
  inspector?: React.ReactNode;
  /** Outline panel content */
  outline?: React.ReactNode;
  /** Minimap panel content */
  minimap?: React.ReactNode;
  /** Whether to enable calm mode by default */
  defaultCalmMode?: boolean;
}

/**
 * Canvas Chrome Layout
 *
 * Manages the visibility and z-index of all chrome elements.
 */
export const CanvasChromeLayout: React.FC<CanvasChromeLayoutProps> = ({
  children,
  leftRail,
  contextBar,
  inspector,
  outline,
  minimap,
  defaultCalmMode = true,
}) => {
  // Chrome state
  const [calmMode, setCalmMode] = useAtom(chromeCalmModeAtom);
  const [leftRailVisible, setLeftRailVisible] = useAtom(
    chromeLeftRailVisibleAtom
  );
  const [contextBarVisible, setContextBarVisible] = useAtom(
    chromeContextBarVisibleAtom
  );
  const [inspectorVisible, setInspectorVisible] = useAtom(
    chromeInspectorVisibleAtom
  );
  const [outlineVisible, setOutlineVisible] = useAtom(chromeOutlineVisibleAtom);
  const [minimapVisible, setMinimapVisible] = useAtom(chromeMinimapVisibleAtom);

  // Initialize calm mode
  React.useEffect(() => {
    setCalmMode(defaultCalmMode);
  }, [defaultCalmMode, setCalmMode]);

  // Keyboard shortcuts
  React.useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Cmd/Ctrl + Shift + ... for chrome toggles
      if ((e.metaKey || e.ctrlKey) && e.shiftKey) {
        switch (e.key.toLowerCase()) {
          case 'l':
            // Toggle left rail
            e.preventDefault();
            setLeftRailVisible((v) => !v);
            break;
          case 'i':
            // Toggle inspector
            e.preventDefault();
            setInspectorVisible((v) => !v);
            break;
          case 'o':
            // Toggle outline
            e.preventDefault();
            setOutlineVisible((v) => !v);
            break;
          case 'm':
            // Toggle minimap
            e.preventDefault();
            setMinimapVisible((v) => !v);
            break;
          case 'c':
            // Toggle calm mode
            e.preventDefault();
            setCalmMode((v) => !v);
            break;
        }
      }

      // Escape to hide context bar
      if (e.key === 'Escape' && contextBarVisible) {
        e.preventDefault();
        setContextBarVisible(false);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [
    setLeftRailVisible,
    setInspectorVisible,
    setOutlineVisible,
    setMinimapVisible,
    setCalmMode,
    setContextBarVisible,
    contextBarVisible,
  ]);

  return (
    <div
      className="canvas-chrome-layout"
      style={{
        position: 'relative',
        width: '100%',
        height: '100%',
        overflow: 'hidden',
        background: 'var(--color-canvas-background, #fafafa)',
      }}
    >
      {/* Canvas Content Layer (z: 10) */}
      <div
        className="canvas-content-layer"
        style={{
          position: 'absolute',
          inset: 0,
          zIndex: CANVAS_Z_INDEX.FRAMES,
        }}
      >
        {children}
      </div>

      {/* Left Rail Layer (z: 50) */}
      {leftRail && (
        <div
          className="canvas-left-rail-layer"
          style={{
            position: 'absolute',
            left: 0,
            top: 0,
            bottom: 0,
            width: leftRailVisible ? '280px' : '0px',
            zIndex: CANVAS_Z_INDEX.LEFT_RAIL,
            transition: 'width 0.2s ease-in-out',
            overflow: 'hidden',
            background: 'var(--color-surface-elevated, #ffffff)',
            borderRight: leftRailVisible
              ? '1px solid var(--color-border, #e0e0e0)'
              : 'none',
            boxShadow: leftRailVisible ? '2px 0 8px rgba(0,0,0,0.05)' : 'none',
          }}
        >
          {leftRailVisible && leftRail}
        </div>
      )}

      {/* Outline Panel Layer (z: 60) */}
      {outline && (
        <div
          className="canvas-outline-layer"
          style={{
            position: 'absolute',
            left: leftRailVisible ? '280px' : '0px',
            top: 0,
            bottom: 0,
            width: outlineVisible ? '240px' : '0px',
            zIndex: CANVAS_Z_INDEX.OUTLINE_PANEL,
            transition: 'width 0.2s ease-in-out, left 0.2s ease-in-out',
            overflow: 'hidden',
            background: 'var(--color-surface-paper, #fafafa)',
            borderRight: outlineVisible
              ? '1px solid var(--color-border, #e0e0e0)'
              : 'none',
          }}
        >
          {outlineVisible && outline}
        </div>
      )}

      {/* Inspector Panel Layer (z: 80) */}
      {inspector && (
        <div
          className="canvas-inspector-layer"
          style={{
            position: 'absolute',
            right: 0,
            top: 0,
            bottom: 0,
            width: inspectorVisible ? '320px' : '0px',
            zIndex: CANVAS_Z_INDEX.INSPECTOR_PANEL,
            transition: 'width 0.2s ease-in-out',
            overflow: 'hidden',
            background: 'var(--color-surface-elevated, #ffffff)',
            borderLeft: inspectorVisible
              ? '1px solid var(--color-border, #e0e0e0)'
              : 'none',
            boxShadow: inspectorVisible
              ? '-2px 0 8px rgba(0,0,0,0.05)'
              : 'none',
          }}
        >
          {inspectorVisible && inspector}
        </div>
      )}

      {/* Minimap Layer (z: 70) */}
      {minimap && (
        <div
          className="canvas-minimap-layer"
          style={{
            position: 'absolute',
            right: inspectorVisible ? '320px' : '0px',
            bottom: '16px',
            width: minimapVisible ? '200px' : '0px',
            height: minimapVisible ? '150px' : '0px',
            zIndex: CANVAS_Z_INDEX.MINIMAP,
            transition:
              'width 0.2s ease-in-out, height 0.2s ease-in-out, right 0.2s ease-in-out',
            overflow: 'hidden',
            background: 'var(--color-surface-elevated, #ffffff)',
            border: minimapVisible
              ? '1px solid var(--color-border, #e0e0e0)'
              : 'none',
            borderRadius: '8px',
            boxShadow: minimapVisible ? '0 4px 12px rgba(0,0,0,0.1)' : 'none',
          }}
        >
          {minimapVisible && minimap}
        </div>
      )}

      {/* Context Bar Layer (z: 100) */}
      {contextBar && contextBarVisible && (
        <div
          className="canvas-context-bar-layer"
          style={{
            position: 'absolute',
            top: '50%',
            left: '50%',
            transform: 'translate(-50%, -50%)',
            zIndex: CANVAS_Z_INDEX.CONTEXT_BAR,
            background: 'var(--color-surface-elevated, #ffffff)',
            border: '1px solid var(--color-border, #e0e0e0)',
            borderRadius: '12px',
            boxShadow: '0 8px 24px rgba(0,0,0,0.15)',
            padding: '8px',
            animation: 'contextBarSlideIn 0.2s ease-out',
          }}
        >
          {contextBar}
        </div>
      )}

      {/* Calm Mode Indicator */}
      {calmMode && (
        <div
          className="canvas-calm-indicator"
          style={{
            position: 'absolute',
            top: '8px',
            right: '8px',
            zIndex: CANVAS_Z_INDEX.FLOATING_CONTROLS,
            padding: '4px 8px',
            background: 'rgba(0,0,0,0.6)',
            color: 'white',
            fontSize: '10px',
            fontWeight: 600,
            borderRadius: '4px',
            pointerEvents: 'none',
            opacity: 0.5,
          }}
        >
          🌙 Calm Mode
        </div>
      )}

      <style>{`
        @keyframes contextBarSlideIn {
          from {
            opacity: 0;
            transform: translate(-50%, -50%) scale(0.95);
          }
          to {
            opacity: 1;
            transform: translate(-50%, -50%) scale(1);
          }
        }
      `}</style>
    </div>
  );
};
