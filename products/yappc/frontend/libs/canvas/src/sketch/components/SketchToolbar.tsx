/**
 * @ghatana/yappc-sketch - SketchToolbar Component
 *
 * Production-grade toolbar for sketch tool selection.
 *
 * @doc.type component
 * @doc.purpose Sketch tool selection toolbar
 * @doc.layer presentation
 * @doc.pattern Component
 */

import { Hand as SelectIcon, Pencil as PenIcon, Wand2 as EraserIcon, Square as RectangleIcon, Circle as EllipseIcon, StickyNote as StickyIcon, Type as TextIcon, Highlighter as HighlighterIcon, MoveRight as LineIcon, MoveRight as ArrowIcon, Image as ImageIcon } from 'lucide-react';
import {
  ToggleButton,
  Tooltip,
  Divider,
  Surface as Paper,
} from '@ghatana/ui';
import { ToggleButtonGroup } from '@ghatana/ui';
import React from 'react';

import type { SketchTool } from '../types';

/**
 * Tool configuration with icon, label, and keyboard shortcut
 */
interface ToolConfig {
  icon: React.ReactElement;
  label: string;
  shortcut: string;
  group: 'selection' | 'drawing' | 'shapes' | 'annotation';
}

/**
 * Configuration for all sketch tools
 */
const TOOL_CONFIG: Record<SketchTool, ToolConfig> = {
  select: { icon: <SelectIcon size={16} />, label: 'Select', shortcut: 'V', group: 'selection' },
  pen: { icon: <PenIcon size={16} />, label: 'Pen', shortcut: 'P', group: 'drawing' },
  highlighter: { icon: <HighlighterIcon size={16} />, label: 'Highlighter', shortcut: 'H', group: 'drawing' },
  eraser: { icon: <EraserIcon size={16} />, label: 'Eraser', shortcut: 'E', group: 'drawing' },
  rectangle: { icon: <RectangleIcon size={16} />, label: 'Rectangle', shortcut: 'R', group: 'shapes' },
  ellipse: { icon: <EllipseIcon size={16} />, label: 'Ellipse', shortcut: 'O', group: 'shapes' },
  line: { icon: <LineIcon size={16} />, label: 'Line', shortcut: 'L', group: 'shapes' },
  arrow: { icon: <ArrowIcon size={16} />, label: 'Arrow', shortcut: 'A', group: 'shapes' },
  text: { icon: <TextIcon size={16} />, label: 'Text', shortcut: 'T', group: 'annotation' },
  sticky: { icon: <StickyIcon size={16} />, label: 'Sticky Note', shortcut: 'S', group: 'annotation' },
  image: { icon: <ImageIcon size={16} />, label: 'Image', shortcut: 'I', group: 'annotation' },
};

/**
 * Props for SketchToolbar component
 */
export interface SketchToolbarProps {
  /** Currently active tool */
  activeTool: SketchTool;
  /** Callback when tool changes */
  onToolChange: (tool: SketchTool) => void;
  /** Whether toolbar is disabled */
  disabled?: boolean;
  /** Toolbar orientation */
  orientation?: 'horizontal' | 'vertical';
  /** Which tools to show (defaults to all) */
  tools?: SketchTool[];
  /** Show tool groups with dividers */
  showGroups?: boolean;
  /** Compact mode (smaller buttons) */
  compact?: boolean;
  /** Custom class name */
  className?: string;
}

/**
 * Sketch toolbar for drawing and annotation tools.
 *
 * Features:
 * - Multiple sketch tools (select, pen, eraser, shapes, sticky notes)
 * - Keyboard shortcut display
 * - Horizontal or vertical orientation
 * - Tool grouping with dividers
 * - Compact mode
 * - Disabled state support
 *
 * @param props - Component properties
 * @returns Rendered sketch toolbar
 *
 * @example
 * ```tsx
 * <SketchToolbar
 *   activeTool="pen"
 *   onToolChange={(tool) => setActiveTool(tool)}
 *   orientation="vertical"
 *   showGroups
 * />
 * ```
 */
export const SketchToolbar: React.FC<SketchToolbarProps> = ({
  activeTool,
  onToolChange,
  disabled = false,
  orientation = 'vertical',
  tools,
  showGroups = false,
  compact = false,
  className,
}) => {
  const handleChange = (_event: React.MouseEvent<HTMLElement>, newTool: SketchTool | null) => {
    if (newTool !== null) {
      onToolChange(newTool);
    }
  };

  // Filter tools if specified
  const visibleTools = tools || (Object.keys(TOOL_CONFIG) as SketchTool[]);

  // Group tools by category
  const groupedTools = showGroups
    ? {
        selection: visibleTools.filter((t) => TOOL_CONFIG[t].group === 'selection'),
        drawing: visibleTools.filter((t) => TOOL_CONFIG[t].group === 'drawing'),
        shapes: visibleTools.filter((t) => TOOL_CONFIG[t].group === 'shapes'),
        annotation: visibleTools.filter((t) => TOOL_CONFIG[t].group === 'annotation'),
      }
    : null;

  const buttonSize = compact ? 32 : 40;

  const renderToolButton = (tool: SketchTool) => {
    const config = TOOL_CONFIG[tool];
    return (
      <Tooltip
        key={tool}
        title={`${config.label} (${config.shortcut})`}
        placement={orientation === 'vertical' ? 'right' : 'bottom'}
      >
        <ToggleButton
          value={tool}
          aria-label={config.label}
          data-testid={`sketch-tool-${tool}`}
          style={{ width: buttonSize, height: buttonSize }}
          className="[&.Mui-selected]:bg-blue-600 [&.Mui-selected]:text-white [&.Mui-selected:hover]:bg-blue-700"
        >
          {config.icon}
        </ToggleButton>
      </Tooltip>
    );
  };

  if (showGroups && groupedTools) {
    return (
      <Paper
        elevation={3}
        className={className}
         style={{ flexDirection: orientation === 'vertical' ? 'column' : 'row' }}
        data-testid="sketch-toolbar"
      >
        {Object.entries(groupedTools).map(([group, groupTools], index) => {
          if (groupTools.length === 0) return null;
          return (
            <React.Fragment key={group}>
              {index > 0 && groupedTools[Object.keys(groupedTools)[index - 1] as keyof typeof groupedTools].length > 0 && (
                <Divider
                  orientation={orientation === 'vertical' ? 'horizontal' : 'vertical'}
                  flexItem
                  style={{ marginTop: orientation === 'vertical' ? 4 : 0, marginBottom: orientation === 'vertical' ? 4 : 0, marginLeft: orientation === 'horizontal' ? 4 : 0, marginRight: orientation === 'horizontal' ? 4 : 0 }}
                />
              )}
              <ToggleButtonGroup
                orientation={orientation}
                value={activeTool}
                exclusive
                onChange={handleChange}
                disabled={disabled}
                size="small"
              >
                {groupTools.map(renderToolButton)}
              </ToggleButtonGroup>
            </React.Fragment>
          );
        })}
      </Paper>
    );
  }

  return (
    <Paper
      elevation={3}
      className={className}
       style={{ flexDirection: orientation === 'vertical' ? 'column' : 'row' }}
      data-testid="sketch-toolbar"
    >
      <ToggleButtonGroup
        orientation={orientation}
        value={activeTool}
        exclusive
        onChange={handleChange}
        disabled={disabled}
        size="small"
      >
        {visibleTools.map(renderToolButton)}
      </ToggleButtonGroup>
    </Paper>
  );
};

export default SketchToolbar;
