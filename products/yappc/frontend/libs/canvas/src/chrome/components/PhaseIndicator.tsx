/**
 * PhaseIndicator Component
 * 
 * Phase indicator with quick jump menu
 * Shows current phase with dot + label
 * Click to open phase jump menu
 * 
 * Features:
 * - Current phase display with emoji and color
 * - Click to show all phases with jump navigation
 * - Shows frame count and completion % per phase
 * - Smooth animated pan+zoom to selected phase
 * 
 * @doc.type component
 * @doc.purpose Phase navigation and indication
 * @doc.layer components
 */

import { Box, Menu } from '@ghatana/ui';
import { MenuItem } from '@ghatana/ui';
import { useAtom } from 'jotai';
import React, { useState } from 'react';

import {
  chromeCurrentPhaseAtom,
  chromePhaseIndicatorVisibleAtom,
} from '../state/chrome-atoms';
import { CANVAS_TOKENS, LIFECYCLE_PHASES, type LifecyclePhase } from '../tokens/canvas-tokens';

const { SPACING, COLORS, TYPOGRAPHY, FONT_WEIGHT, RADIUS, SHADOWS } = CANVAS_TOKENS;

export interface PhaseIndicatorProps {
  /** Callback when phase selected */
  onPhaseSelect?: (phase: LifecyclePhase) => void;
  
  /** Phase statistics */
  phaseStats?: Record<string, { frameCount: number; completion: number }>;
}

export function PhaseIndicator({
  onPhaseSelect,
  phaseStats = {},
}: PhaseIndicatorProps) {
  const [currentPhase] = useAtom(chromeCurrentPhaseAtom);
  const [visible] = useAtom(chromePhaseIndicatorVisibleAtom);
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

  if (!visible) {
    return null;
  }

  const phase = currentPhase ? LIFECYCLE_PHASES[currentPhase as LifecyclePhase] : null;
  const menuOpen = Boolean(anchorEl);

  const handleClick = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handlePhaseSelect = (phaseKey: LifecyclePhase) => {
    onPhaseSelect?.(phaseKey);
    handleClose();
  };

  return (
    <>
      {/* Phase Indicator Button */}
      <Box
        onClick={handleClick}
        className="flex items-center cursor-pointer" style={{ gap: SPACING.SM, paddingLeft: SPACING.MD, paddingRight: SPACING.MD, paddingTop: SPACING.SM, paddingBottom: SPACING.SM, borderRadius: RADIUS.MD, transition: 'background-color 150ms ease' }} >
        {/* Phase dot */}
        <Box
          className="w-[8px] h-[8px] rounded-full" style={{ backgroundColor: phase?.color || COLORS.TEXT_DISABLED }}
        />

        {/* Phase label */}
        <Box
          style={{
            fontSize: TYPOGRAPHY.SM,
            fontWeight: FONT_WEIGHT.MEDIUM,
            color: COLORS.TEXT_PRIMARY,
          }}
        >
          {phase ? `${phase.emoji} ${phase.title}` : 'Select Phase'}
        </Box>

        {/* Dropdown arrow */}
        <Box
          style={{
            fontSize: TYPOGRAPHY.XS,
            color: COLORS.TEXT_SECONDARY,
          }}
        >
          ▼
        </Box>
      </Box>

      {/* Phase Jump Menu */}
      <Menu
        anchorEl={anchorEl}
        open={menuOpen}
        onClose={handleClose}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'center',
        }}
        transformOrigin={{
          vertical: 'top',
          horizontal: 'center',
        }}
        PaperProps={{
          style: {
            minWidth: 280,
            marginTop: SPACING.XS,
            boxShadow: SHADOWS.LG,
            border: `1px solid ${COLORS.BORDER_LIGHT}`,
            borderRadius: RADIUS.MD,
          },
        }}
      >
        {/* Menu Header */}
        <Box
          style={{
            paddingLeft: SPACING.MD,
            paddingRight: SPACING.MD,
            paddingTop: SPACING.SM,
            paddingBottom: SPACING.SM,
            borderBottom: `1px solid ${COLORS.BORDER_LIGHT}`,
            fontSize: TYPOGRAPHY.XS,
            fontWeight: FONT_WEIGHT.SEMIBOLD,
            color: COLORS.TEXT_SECONDARY,
            textTransform: 'uppercase' as const,
          }}
        >
          Jump to Phase
        </Box>

        {/* Phase Options */}
        {(Object.keys(LIFECYCLE_PHASES) as LifecyclePhase[]).map((phaseKey) => {
          const phaseData = LIFECYCLE_PHASES[phaseKey];
          const stats = phaseStats[phaseKey];
          const isCurrent = currentPhase === phaseKey;

          return (
            <MenuItem
              key={phaseKey}
              onClick={() => handlePhaseSelect(phaseKey)}
              className="flex items-center" style={{ gap: SPACING.MD, paddingLeft: SPACING.MD, paddingRight: SPACING.MD, paddingTop: SPACING.SM, paddingBottom: SPACING.SM, backgroundColor: isCurrent ? COLORS.SELECTION_BG : 'transparent' }}
            >
              {/* Phase color dot */}
              <Box
                className="rounded-full shrink-0 w-[12px] h-[12px]" />

              {/* Phase info */}
              <Box className="flex-1">
                <Box
                  style={{
                    fontSize: TYPOGRAPHY.SM,
                    fontWeight: FONT_WEIGHT.MEDIUM,
                    color: COLORS.TEXT_PRIMARY,
                  }}
                >
                  {phaseData.emoji} {phaseData.title}
                </Box>
                {stats && (
                  <Box
                    style={{
                      fontSize: TYPOGRAPHY.XS,
                      color: COLORS.TEXT_SECONDARY,
                      marginTop: SPACING.XS / 2,
                    }}
                  >
                    {stats.frameCount} frames • {Math.round(stats.completion)}% complete
                  </Box>
                )}
              </Box>

              {/* Current indicator */}
              {isCurrent && (
                <Box
                  style={{
                    fontSize: TYPOGRAPHY.SM,
                    color: COLORS.PRIMARY,
                  }}
                >
                  ✓
                </Box>
              )}
            </MenuItem>
          );
        })}
      </Menu>
    </>
  );
}
