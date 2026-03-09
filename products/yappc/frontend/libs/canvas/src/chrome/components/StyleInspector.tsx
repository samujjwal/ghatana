/**
 * @doc.type component
 * @doc.purpose Style inspector panel for quick object styling
 * @doc.layer core
 * @doc.pattern Inspector Panel
 */

import { Bold as BoldIcon, Italic as ItalicIcon, Underline as UnderlineIcon, PaintBucket as FillIcon, PenLine as BorderIcon } from 'lucide-react';
import { Box, Typography, Slider, Button, Tooltip, TextField, Select, MenuItem, FormControl, InputLabel, Divider, IconButton } from '@ghatana/ui';
import React, { useMemo, useState } from 'react';

import { CANVAS_TOKENS } from '../tokens/canvas-tokens';

import type { Node } from '@xyflow/react';

/**
 *
 */
export interface StyleInspectorProps {
  /** Selected nodes */
  selectedNodes: Node[];
  
  /** Callback when style changes */
  onStyleChange: (nodeIds: string[], style: Partial<NodeStyle>) => void;
  
  /** Whether the panel is visible */
  visible?: boolean;
}

/**
 *
 */
export interface NodeStyle {
  backgroundColor?: string;
  borderColor?: string;
  borderWidth?: number;
  color?: string;
  fontSize?: number;
  fontWeight?: number;
  fontStyle?: 'normal' | 'italic';
  textDecoration?: 'none' | 'underline';
  opacity?: number;
  borderRadius?: number;
  padding?: number;
}

/**
 * Color preset button
 */
function ColorPreset({
  color,
  label,
  selected,
  onClick,
}: {
  color: string;
  label: string;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <Tooltip title={label}>
      <Box
        onClick={onClick}
        className="w-[32px] h-[32px] rounded cursor-pointer border-[2px_solid]" style={{ transition: 'border-color 200ms', backgroundColor: color, borderColor: selected ? '#1976d2' : 'transparent' }}
      />
    </Tooltip>
  );
}

/**
 * StyleInspector - Quick style panel for selected objects
 * 
 * Provides rapid access to common styling options without requiring
 * users to open full property panels or use complex menus.
 * 
 * @example
 * ```tsx
 * <StyleInspector
 *   selectedNodes={selectedNodes}
 *   onStyleChange={(ids, style) => {
 *     ids.forEach(id => updateNodeStyle(id, style));
 *   }}
 * />
 * ```
 */
export function StyleInspector({
  selectedNodes,
  onStyleChange,
  visible = true,
}: StyleInspectorProps) {
  const [colorPickerOpen, setColorPickerOpen] = useState(false);
  
  // Extract common styles from selection
  const commonStyles = useMemo(() => {
    if (selectedNodes.length === 0) return {};
    
    const first = selectedNodes[0].data as unknown;
    return {
      backgroundColor: first?.backgroundColor || first?.color || CANVAS_TOKENS.COLORS.STICKY_YELLOW,
      borderColor: first?.borderColor || CANVAS_TOKENS.COLORS.BORDER,
      color: first?.textColor || CANVAS_TOKENS.COLORS.TEXT_PRIMARY,
      fontSize: first?.fontSize || CANVAS_TOKENS.TYPOGRAPHY.SIZE.MD,
      opacity: first?.opacity ?? 1,
      borderRadius: first?.borderRadius ?? 8,
    };
  }, [selectedNodes]);
  
  if (!visible || selectedNodes.length === 0) {
    return null;
  }
  
  const handleStyleChange = (style: Partial<NodeStyle>) => {
    const nodeIds = selectedNodes.map((n) => n.id);
    onStyleChange(nodeIds, style);
  };
  
  return (
    <Box
      className="h-full overflow-y-auto" >
      {/* Header */}
      <Box className="mb-6">
        <Typography as="h6" className="mb-1">
          Style
        </Typography>
        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
          {selectedNodes.length === 1
            ? '1 object selected'
            : `${selectedNodes.length} objects selected`}
        </Typography>
      </Box>
      
      {/* Color presets */}
      <Box className="mb-6">
        <Typography as="span" className="text-xs text-gray-500" className="mb-2 block">
          Quick Colors
        </Typography>
        <Box className="flex gap-2 flex-wrap">
          <ColorPreset
            color={CANVAS_TOKENS.COLORS.STICKY_YELLOW}
            label="Yellow"
            selected={commonStyles.backgroundColor === CANVAS_TOKENS.COLORS.STICKY_YELLOW}
            onClick={() => handleStyleChange({ backgroundColor: CANVAS_TOKENS.COLORS.STICKY_YELLOW })}
          />
          <ColorPreset
            color={CANVAS_TOKENS.COLORS.STICKY_PINK}
            label="Pink"
            selected={commonStyles.backgroundColor === CANVAS_TOKENS.COLORS.STICKY_PINK}
            onClick={() => handleStyleChange({ backgroundColor: CANVAS_TOKENS.COLORS.STICKY_PINK })}
          />
          <ColorPreset
            color={CANVAS_TOKENS.COLORS.STICKY_BLUE}
            label="Blue"
            selected={commonStyles.backgroundColor === CANVAS_TOKENS.COLORS.STICKY_BLUE}
            onClick={() => handleStyleChange({ backgroundColor: CANVAS_TOKENS.COLORS.STICKY_BLUE })}
          />
          <ColorPreset
            color={CANVAS_TOKENS.COLORS.STICKY_GREEN}
            label="Green"
            selected={commonStyles.backgroundColor === CANVAS_TOKENS.COLORS.STICKY_GREEN}
            onClick={() => handleStyleChange({ backgroundColor: CANVAS_TOKENS.COLORS.STICKY_GREEN })}
          />
          <ColorPreset
            color={CANVAS_TOKENS.COLORS.STICKY_ORANGE}
            label="Orange"
            selected={commonStyles.backgroundColor === CANVAS_TOKENS.COLORS.STICKY_ORANGE}
            onClick={() => handleStyleChange({ backgroundColor: CANVAS_TOKENS.COLORS.STICKY_ORANGE })}
          />
        </Box>
      </Box>
      
      {/* Lifecycle phase colors */}
      <Box className="mb-6">
        <Typography as="span" className="text-xs text-gray-500" className="mb-2 block">
          Lifecycle Phases
        </Typography>
        <Box className="flex gap-2 flex-wrap">
          {CANVAS_TOKENS.LIFECYCLE_PHASES.map((phase) => (
            <ColorPreset
              key={phase.id}
              color={phase.color}
              label={phase.title}
              selected={commonStyles.backgroundColor === phase.color}
              onClick={() => handleStyleChange({ backgroundColor: phase.color })}
            />
          ))}
        </Box>
      </Box>
      
      <Divider className="my-4" />
      
      {/* Background color with input */}
      <Box className="mb-6">
        <Typography as="span" className="text-xs text-gray-500" className="mb-2 block">
          Background
        </Typography>
        <Box className="flex gap-2 items-center">
          <Box
            className="rounded cursor-pointer w-[40px] h-[40px] border border-solid border-gray-200 dark:border-gray-700" style={{ backgroundColor: 'commonStyles.backgroundColor' }} onClick={() => setColorPickerOpen(!colorPickerOpen)}
          />
          <TextField
            size="sm"
            value={commonStyles.backgroundColor}
            onChange={(e) => handleStyleChange({ backgroundColor: e.target.value })}
            className="flex-1"
          />
        </Box>
      </Box>
      
      {/* Opacity */}
      <Box className="mb-6">
        <Typography as="span" className="text-xs text-gray-500" className="mb-2 block">
          Opacity
        </Typography>
        <Box className="flex gap-4 items-center">
          <Slider
            value={commonStyles.opacity * 100}
            min={0}
            max={100}
            onChange={(e, value) =>
              handleStyleChange({ opacity: (value as number) / 100 })
            }
            className="flex-1"
          />
          <Typography as="span" className="text-xs text-gray-500" className="min-w-[40px]">
            {Math.round(commonStyles.opacity * 100)}%
          </Typography>
        </Box>
      </Box>
      
      {/* Border radius */}
      <Box className="mb-6">
        <Typography as="span" className="text-xs text-gray-500" className="mb-2 block">
          Corner Radius
        </Typography>
        <Box className="flex gap-4 items-center">
          <Slider
            value={commonStyles.borderRadius}
            min={0}
            max={32}
            onChange={(e, value) =>
              handleStyleChange({ borderRadius: value as number })
            }
            className="flex-1"
          />
          <Typography as="span" className="text-xs text-gray-500" className="min-w-[40px]">
            {commonStyles.borderRadius}px
          </Typography>
        </Box>
      </Box>
      
      <Divider className="my-4" />
      
      {/* Typography (if text node) */}
      {selectedNodes.some((n) => n.type === 'text' || n.type === 'sticky-note') && (
        <>
          <Typography as="span" className="text-xs text-gray-500" className="mb-2 block">
            Text Style
          </Typography>
          
          <Box className="flex gap-2 mb-4">
            <IconButton size="sm">
              <BoldIcon size={16} />
            </IconButton>
            <IconButton size="sm">
              <ItalicIcon size={16} />
            </IconButton>
            <IconButton size="sm">
              <UnderlineIcon size={16} />
            </IconButton>
          </Box>
          
          <FormControl fullWidth size="sm" className="mb-4">
            <InputLabel>Font Size</InputLabel>
            <Select
              value={commonStyles.fontSize}
              label="Font Size"
              onChange={(e) =>
                handleStyleChange({ fontSize: e.target.value as number })
              }
            >
              <MenuItem value={12}>12px</MenuItem>
              <MenuItem value={14}>14px</MenuItem>
              <MenuItem value={16}>16px</MenuItem>
              <MenuItem value={18}>18px</MenuItem>
              <MenuItem value={20}>20px</MenuItem>
              <MenuItem value={24}>24px</MenuItem>
            </Select>
          </FormControl>
        </>
      )}
      
      {/* Quick actions */}
      <Box className="mt-6">
        <Typography as="span" className="text-xs text-gray-500" className="mb-2 block">
          Quick Actions
        </Typography>
        <Box className="flex gap-2 flex-col">
          <Button
            size="sm"
            variant="outlined"
            onClick={() =>
              handleStyleChange({
                backgroundColor: '#FFFFFF',
                borderColor: CANVAS_TOKENS.COLORS.BORDER,
              })
            }
          >
            Reset to Default
          </Button>
          <Button
            size="sm"
            variant="outlined"
            onClick={() => {
              // Copy styles from first selected
              const firstStyle = selectedNodes[0].data as unknown;
              console.log('Copied style:', firstStyle);
            }}
          >
            Copy Style
          </Button>
        </Box>
      </Box>
    </Box>
  );
}
