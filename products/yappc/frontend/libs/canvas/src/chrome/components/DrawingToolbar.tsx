/**
 * DrawingToolbar - Drawing tool controls
 * 
 * Progressive disclosure: Appears only when draw tool is active
 * Provides controls for:
 * - Tool type (pen, marker, highlighter, eraser)
 * - Color selection
 * - Stroke width
 * - Opacity (for highlighter)
 * 
 * @doc.type component
 * @doc.purpose Drawing tool configuration UI
 * @doc.layer components
 * @doc.pattern Component
 */

import {
  Box,
  Button,
  ToggleButton,
  Slider,
  Typography,
  Divider,
} from '@ghatana/ui';
import { ToggleButtonGroup } from '@ghatana/ui';
import { Popover } from '@ghatana/yappc-ui';
import React, { useState } from 'react';

import { CANVAS_TOKENS } from '../tokens';
import type { DrawingStyle, DrawingToolType } from '../lib/canvas/DrawingManager';

const { SPACING, COLORS, TYPOGRAPHY, FONT_WEIGHT, RADIUS, SHADOWS, Z_INDEX, DRAWING_PRESETS } = CANVAS_TOKENS;

/**
 *
 */
export interface DrawingToolbarProps {
  /** Current drawing style */
  style: DrawingStyle;
  
  /** Callback when style changes */
  onStyleChange: (style: DrawingStyle) => void;
  
  /** Whether toolbar is visible */
  visible?: boolean;
}

const PRESET_COLORS = [
  COLORS.DRAWING_BLACK,
  COLORS.DRAWING_RED,
  COLORS.DRAWING_BLUE,
  COLORS.DRAWING_GREEN,
  COLORS.DRAWING_YELLOW,
  COLORS.DRAWING_ORANGE,
  COLORS.DRAWING_PURPLE,
  COLORS.DRAWING_PINK,
];

const TOOL_LABELS: Record<DrawingToolType, string> = {
  pen: 'Pen',
  marker: 'Marker',
  highlighter: 'Highlighter',
  eraser: 'Eraser',
};

const TOOL_ICONS: Record<DrawingToolType, string> = {
  pen: '✒️',
  marker: '🖊️',
  highlighter: '🖍️',
  eraser: '🧹',
};

/**
 *
 */
export function DrawingToolbar({ style, onStyleChange, visible = true }: DrawingToolbarProps) {
  const [colorAnchor, setColorAnchor] = useState<HTMLElement | null>(null);

  if (!visible) return null;

  const handleToolChange = (newType: DrawingToolType) => {
    // Load preset for tool type
    const preset = DRAWING_PRESETS[newType];
    onStyleChange({
      ...style,
      type: newType,
      color: preset.color,
      width: preset.width,
      opacity: preset.opacity,
    });
  };

  const handleColorChange = (color: string) => {
    onStyleChange({ ...style, color });
    setColorAnchor(null);
  };

  const handleWidthChange = (width: number) => {
    onStyleChange({ ...style, width });
  };

  const handleOpacityChange = (opacity: number) => {
    onStyleChange({ ...style, opacity });
  };

  return (
    <Box
      className="absolute top-[80px] left-[50%] flex items-center" style={{ gap: SPACING.MD, padding: SPACING.MD, backgroundColor: COLORS.PANEL_BG_LIGHT, borderRadius: RADIUS.LG, boxShadow: SHADOWS.LG, border: `1px solid ${COLORS.BORDER_LIGHT}`, transform: 'translateX(-50%)' }}>
      {/* Tool Type Selector */}
      <Box>
        <Typography
          style={{
            fontSize: TYPOGRAPHY.XS,
            color: COLORS.TEXT_SECONDARY,
            marginBottom: SPACING.XS,
            fontWeight: FONT_WEIGHT.MEDIUM,
          }}
        >
          Tool
        </Typography>
        <ToggleButtonGroup
          value={style.type}
          exclusive
          onChange={(_, value) => value && handleToolChange(value)}
          size="small"
        >
          {(Object.keys(TOOL_LABELS) as DrawingToolType[]).map(tool => (
            <ToggleButton
              key={tool}
              value={tool}
              aria-label={TOOL_LABELS[tool]}
              style={{
                paddingLeft: SPACING.MD,
                paddingRight: SPACING.MD,
                paddingTop: SPACING.SM,
                paddingBottom: SPACING.SM,
                fontSize: TYPOGRAPHY.SM,
              }}
            >
              <Box className="flex items-center gap-1" >
                <span style={{ fontSize: '18px' }}>{TOOL_ICONS[tool]}</span>
                <span>{TOOL_LABELS[tool]}</span>
              </Box>
            </ToggleButton>
          ))}
        </ToggleButtonGroup>
      </Box>

      <Divider orientation="vertical" flexItem />

      {/* Color Picker */}
      <Box>
        <Typography
          style={{
            fontSize: TYPOGRAPHY.XS,
            color: COLORS.TEXT_SECONDARY,
            marginBottom: SPACING.XS,
            fontWeight: FONT_WEIGHT.MEDIUM,
          }}
        >
          Color
        </Typography>
        <Button
          onClick={(e) => setColorAnchor(e.currentTarget)}
          aria-label="Select color"
          className="min-w-[40px] w-[40px] h-[40px] p-0" style={{ backgroundColor: style.color, border: `2px solid ${COLORS.BORDER_LIGHT}` }}
        />

        {/* Color Picker Popover */}
        <Popover
          open={Boolean(colorAnchor)}
          anchorEl={colorAnchor}
          onClose={() => setColorAnchor(null)}
          anchorOrigin={{
            vertical: 'bottom',
            horizontal: 'center',
          }}
          transformOrigin={{
            vertical: 'top',
            horizontal: 'center',
          }}
        >
          <Box style={{ padding: SPACING.MD }}>
            <Typography style={{ fontSize: TYPOGRAPHY.SM, marginBottom: SPACING.SM, fontWeight: FONT_WEIGHT.MEDIUM }}>
              Select Color
            </Typography>
            <Box
              className="grid" >
              {PRESET_COLORS.map(color => (
                <Button
                  key={color}
                  onClick={() => handleColorChange(color)}
                  className="min-w-[40px] w-[40px] h-[40px] p-0" style={{ backgroundColor: color, border: style.color === color ? `3px solid ${COLORS.PRIMARY}` : 'none' }}
                  aria-label={`Select color ${color}`}
                />
              ))}
            </Box>
          </Box>
        </Popover>
      </Box>

      <Divider orientation="vertical" flexItem />

      {/* Width Slider */}
      <Box className="min-w-[160px]">
        <Typography
          style={{
            fontSize: TYPOGRAPHY.XS,
            color: COLORS.TEXT_SECONDARY,
            marginBottom: SPACING.XS,
            fontWeight: FONT_WEIGHT.MEDIUM,
          }}
        >
          Width: {style.width}px
        </Typography>
        <Slider
          value={style.width}
          onChange={(_, value) => handleWidthChange(value as number)}
          min={1}
          max={20}
          step={1}
          aria-label="Stroke width"
        />
      </Box>

      {/* Opacity Slider (only for highlighter) */}
      {style.type === 'highlighter' && (
        <>
          <Divider orientation="vertical" flexItem />
          <Box className="min-w-[160px]">
            <Typography
              style={{
                fontSize: TYPOGRAPHY.XS,
                color: COLORS.TEXT_SECONDARY,
                marginBottom: SPACING.XS,
                fontWeight: FONT_WEIGHT.MEDIUM,
              }}
            >
              Opacity: {Math.round(style.opacity * 100)}%
            </Typography>
            <Slider
              value={style.opacity}
              onChange={(_, value) => handleOpacityChange(value as number)}
              min={0.1}
              max={1}
              step={0.1}
              aria-label="Opacity"
            />
          </Box>
        </>
      )}
    </Box>
  );
}
