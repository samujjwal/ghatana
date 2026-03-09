/**
 * PhaseSwimLanes Component
 * 
 * Lifecycle phase swim lanes for spatial organization
 * 
 * Features:
 * - Horizontal swim lane layout (2000px width each)
 * - Phase boundaries with visual delimiters (100px separator)
 * - Phase labels visible at overview zoom
 * - Connectors between phases
 * - Auto-layout within lanes
 * - Drag-and-drop between lanes
 * 
 * @doc.type component
 * @doc.purpose Phase-based spatial organization
 * @doc.layer components
 */

import { Box, Typography } from '@ghatana/ui';
import React, { useMemo } from 'react';

import { CANVAS_TOKENS, LIFECYCLE_PHASES } from '../tokens/canvas-tokens';

const { SPACING, COLORS, TYPOGRAPHY, FONT_WEIGHT, CANVAS } = CANVAS_TOKENS;

export interface PhaseSwimLanesProps {
  /** Current zoom level */
  zoom: number;
  
  /** Viewport position */
  viewportX: number;
  viewportY: number;
  
  /** Viewport dimensions */
  viewportWidth: number;
  viewportHeight: number;
  
  /** Callback when lane clicked */
  onLaneClick?: (phaseId: string) => void;
  
  /** Show connectors between phases */
  showConnectors?: boolean;
  
  /** Lane height (default: canvas height) */
  laneHeight?: number;
}

const LANE_WIDTH = CANVAS.PHASE_LANE_WIDTH; // 2000px
const SEPARATOR_WIDTH = CANVAS.PHASE_SEPARATOR_WIDTH; // 100px

/**
 * PhaseSwimLanes - Horizontal swim lanes for lifecycle phases
 * 
 * Provides spatial organization by lifecycle phase with visual
 * delimiters and phase labels. Visible at overview zoom levels.
 */
export function PhaseSwimLanes({
  zoom,
  viewportX,
  viewportY,
  viewportWidth,
  viewportHeight,
  onLaneClick,
  showConnectors = true,
  laneHeight = 10000,
}: PhaseSwimLanesProps) {
  // Only show swim lanes at overview zoom levels (<1x)
  const visible = zoom < 1;

  // Calculate which lanes are visible in viewport
  const visibleLanes = useMemo(() => {
    if (!visible) return [];

    const phases = Object.entries(LIFECYCLE_PHASES);
    const lanes: Array<{
      id: string;
      phase: typeof LIFECYCLE_PHASES[keyof typeof LIFECYCLE_PHASES];
      x: number;
      index: number;
    }> = [];

    phases.forEach(([id, phase], index) => {
      const laneX = index * (LANE_WIDTH + SEPARATOR_WIDTH);
      const laneEndX = laneX + LANE_WIDTH;

      // Check if lane is in viewport
      if (laneEndX >= viewportX && laneX <= viewportX + viewportWidth) {
        lanes.push({ id, phase, x: laneX, index });
      }
    });

    return lanes;
  }, [visible, viewportX, viewportWidth]);

  if (!visible || visibleLanes.length === 0) {
    return null;
  }

  return (
    <Box
      className="absolute w-full h-full pointer-events-none top-[0px] left-[0px]" >
      {visibleLanes.map(({ id, phase, x, index }) => (
        <React.Fragment key={id}>
          {/* Lane Background */}
          <Box
            onClick={() => onLaneClick?.(id)}
            className="absolute top-[0px]" style={{ left: x, width: LANE_WIDTH, height: laneHeight, backgroundColor: `${phase.color}11`, pointerEvents: 'auto' as const }}
          />

          {/* Phase Label (visible at low zoom) */}
          {zoom < 0.5 && (
            <Box
              className="absolute pointer-events-none text-center top-[40px]" style={{ left: x + LANE_WIDTH / 2, transform: 'translateX(-50%)', zIndex: CANVAS_TOKENS.Z_INDEX.BACKGROUND }} >
              {/* Emoji Icon */}
              <Box
                style={{
                  fontSize: zoom < 0.25 ? 48 : 32,
                  marginBottom: SPACING.SM,
                  filter: 'drop-shadow(0 2px 4px rgba(0,0,0,0.1))',
                }}
              >
                {phase.emoji}
              </Box>

              {/* Phase Title */}
              <Typography
                style={{
                  fontSize: zoom < 0.25 ? TYPOGRAPHY.XXL : TYPOGRAPHY.LG,
                  fontWeight: FONT_WEIGHT.BOLD,
                  color: phase.color,
                  textShadow: '0 1px 2px rgba(255,255,255,0.8)',
                  marginBottom: SPACING.XS,
                }}
              >
                {phase.title}
              </Typography>

              {/* Phase Description (only at very low zoom) */}
              {zoom < 0.25 && (
                <Typography
                  style={{
                    fontSize: TYPOGRAPHY.SM,
                    color: COLORS.TEXT_SECONDARY,
                    maxWidth: LANE_WIDTH - 100,
                    textShadow: '0 1px 2px rgba(255,255,255,0.8)',
                  }}
                >
                  {phase.description}
                </Typography>
              )}
            </Box>
          )}

          {/* Separator (between lanes) */}
          {index < Object.keys(LIFECYCLE_PHASES).length - 1 && (
            <Box
              className="absolute top-[0px]" style={{ left: x + LANE_WIDTH, width: SEPARATOR_WIDTH, height: laneHeight, backgroundColor: COLORS.NEUTRAL_100, borderLeft: `1px dashed ${COLORS.BORDER_LIGHT}`, borderRight: `1px dashed ${COLORS.BORDER_LIGHT}` }}
            >
              {/* Connector Arrow (if enabled) */}
              {showConnectors && zoom < 0.5 && (
                <Box
                  className="absolute top-[50%] left-[50%] text-[32px] opacity-[0.5]" >
                  →
                </Box>
              )}
            </Box>
          )}
        </React.Fragment>
      ))}
    </Box>
  );
}

/**
 * Get phase lane X position for a given phase
 */
export function getPhaseLaneX(phaseId: string): number {
  const phases = Object.keys(LIFECYCLE_PHASES);
  const index = phases.indexOf(phaseId);
  
  if (index === -1) return 0;
  
  return index * (LANE_WIDTH + SEPARATOR_WIDTH);
}

/**
 * Get phase ID from X coordinate
 */
export function getPhaseFromX(x: number): string | null {
  const phases = Object.keys(LIFECYCLE_PHASES);
  const totalWidth = LANE_WIDTH + SEPARATOR_WIDTH;
  const index = Math.floor(x / totalWidth);
  
  if (index < 0 || index >= phases.length) return null;
  
  // Check if in separator
  const laneX = index * totalWidth;
  if (x >= laneX + LANE_WIDTH && x < laneX + LANE_WIDTH + SEPARATOR_WIDTH) {
    return null; // In separator
  }
  
  return phases[index];
}

/**
 * Snap X coordinate to nearest lane center
 */
export function snapToLaneCenter(x: number): number {
  const phases = Object.keys(LIFECYCLE_PHASES);
  const totalWidth = LANE_WIDTH + SEPARATOR_WIDTH;
  const index = Math.floor(x / totalWidth);
  
  if (index < 0) return LANE_WIDTH / 2;
  if (index >= phases.length) return (phases.length - 1) * totalWidth + LANE_WIDTH / 2;
  
  return index * totalWidth + LANE_WIDTH / 2;
}

/**
 * Get all phase lane bounds
 */
export function getAllPhaseLaneBounds(): Array<{
  phaseId: string;
  minX: number;
  maxX: number;
  centerX: number;
}> {
  const phases = Object.keys(LIFECYCLE_PHASES);
  
  return phases.map((phaseId, index) => {
    const minX = index * (LANE_WIDTH + SEPARATOR_WIDTH);
    const maxX = minX + LANE_WIDTH;
    const centerX = minX + LANE_WIDTH / 2;
    
    return { phaseId, minX, maxX, centerX };
  });
}
