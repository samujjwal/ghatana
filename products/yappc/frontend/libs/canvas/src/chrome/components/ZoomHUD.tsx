/**
 * ZoomHUD Component
 * 
 * Bottom-right zoom controls with coordinates and minimap toggle
 * Always visible, non-intrusive 160×36px pill
 * 
 * Features:
 * - Current zoom percentage
 * - Fit to view button
 * - Reset to 100% button
 * - Zoom in/out buttons
 * - Minimap toggle (shows thumbnail on hover)
 * - Coordinate display (when zoomed >200%)
 * 
 * @doc.type component
 * @doc.purpose Zoom controls and navigation HUD
 * @doc.layer components
 */

import { Box, IconButton, Tooltip } from '@ghatana/ui';
import { useAtom } from 'jotai';
import React from 'react';

import {
  chromeZoomLevelAtom,
  chromeMinimapVisibleAtom,
  chromeZoomHUDVisibleAtom,
} from '../state/chrome-atoms';
import { CANVAS_TOKENS } from '../tokens/canvas-tokens';

const { SPACING, Z_INDEX, COLORS, TYPOGRAPHY, CANVAS, RADIUS, SHADOWS } = CANVAS_TOKENS;

export interface ZoomHUDProps {
  /** Current viewport position */
  position?: { x: number; y: number };
  
  /** Callback when zoom changes */
  onZoomChange?: (zoom: number) => void;
  
  /** Callback when fit to view clicked */
  onFitToView?: () => void;
  
  /** Show coordinate display */
  showCoordinates?: boolean;
}

export function ZoomHUD({
  position = { x: 0, y: 0 },
  onZoomChange,
  onFitToView,
  showCoordinates = false,
}: ZoomHUDProps) {
  const [zoom, setZoom] = useAtom(chromeZoomLevelAtom);
  const [minimapVisible, setMinimapVisible] = useAtom(chromeMinimapVisibleAtom);
  const [hudVisible] = useAtom(chromeZoomHUDVisibleAtom);

  if (!hudVisible) {
    return null;
  }

  const handleZoomIn = () => {
    const newZoom = Math.min(zoom * 1.2, CANVAS.MAX_ZOOM);
    setZoom(newZoom);
    onZoomChange?.(newZoom);
  };

  const handleZoomOut = () => {
    const newZoom = Math.max(zoom / 1.2, CANVAS.MIN_ZOOM);
    setZoom(newZoom);
    onZoomChange?.(newZoom);
  };

  const handleReset = () => {
    setZoom(1);
    onZoomChange?.(1);
  };

  const handleFitToView = () => {
    onFitToView?.();
  };

  const handleMinimapToggle = () => {
    setMinimapVisible(!minimapVisible);
  };

  const zoomPercentage = Math.round(zoom * 100);
  const showCoords = showCoordinates || zoom > 2;

  return (
    <Box
      className="fixed flex items-center" style={{ bottom: SPACING.LG, right: SPACING.LG, gap: SPACING.XS, backgroundColor: COLORS.PANEL_BG_LIGHT, borderRadius: RADIUS.FULL, boxShadow: SHADOWS.LG, border: `1px solid ${COLORS.BORDER_LIGHT}` }}
    >
      {/* Zoom Out */}
      <Tooltip title="Zoom out (-)">
        <IconButton
          onClick={handleZoomOut}
          disabled={zoom <= CANVAS.MIN_ZOOM}
          size="small"
          aria-label="Zoom out"
          className="w-[28px] h-[28px]" style={{ fontSize: TYPOGRAPHY.SM, color: COLORS.TEXT_PRIMARY }}
        >
          −
        </IconButton>
      </Tooltip>

      {/* Zoom Percentage */}
      <Tooltip title="Click to reset to 100%">
        <Box
          onClick={handleReset}
          className="font-medium cursor-pointer select-none min-w-[45px] text-center" style={{ paddingLeft: SPACING.SM, paddingRight: SPACING.SM, fontSize: TYPOGRAPHY.SM, color: COLORS.TEXT_PRIMARY, backgroundColor: COLORS.BORDER_LIGHT }}
        >
          {zoomPercentage}%
        </Box>
      </Tooltip>

      {/* Zoom In */}
      <Tooltip title="Zoom in (+)">
        <IconButton
          onClick={handleZoomIn}
          disabled={zoom >= CANVAS.MAX_ZOOM}
          size="small"
          aria-label="Zoom in"
          className="w-[28px] h-[28px] mx-1" style={{ fontSize: TYPOGRAPHY.SM, color: COLORS.TEXT_SECONDARY }}
        >
          +
        </IconButton>
      </Tooltip>

      {/* Divider */}
      <Box style={{ backgroundColor: COLORS.BORDER_LIGHT, marginLeft: SPACING.XS, marginRight: SPACING.XS, width: 1, height: 20 }} />

      {/* Fit to View */}
      <Tooltip title="Fit to view (⌘0)">
        <IconButton
          onClick={handleFitToView}
          size="small"
          aria-label="Fit to view"
          className="w-[28px] h-[28px] text-[16px]"
        >
          ⊡
        </IconButton>
      </Tooltip>

      {/* Minimap Toggle */}
      <Tooltip title={minimapVisible ? 'Hide minimap' : 'Show minimap'}>
        <IconButton
          onClick={handleMinimapToggle}
          size="small"
          aria-label="Toggle minimap"
          className="w-[28px] h-[28px] text-[16px] mx-1" style={{ backgroundColor: minimapVisible ? COLORS.SELECTION_BG : 'transparent' }}
        >
          🗺
        </IconButton>
      </Tooltip>

      {/* Coordinates (shown when zoomed >200%) */}
      {showCoords && (
        <>
          <Box style={{ backgroundColor: COLORS.BORDER_LIGHT, marginLeft: SPACING.XS, marginRight: SPACING.XS, width: 1, height: 20 }}
          />
          <Box
            className="whitespace-nowrap font-mono text-xs" >
            {Math.round(position.x)}, {Math.round(position.y)}
          </Box>
        </>
      )}
    </Box>
  );
}
