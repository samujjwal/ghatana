/**
 * OutlinePanel - Frame & content navigation
 * 
 * Shows:
 * - Hierarchical list of frames
 * - Nested children within frames (optional)
 * - Quick navigation (click to zoom)
 * - Search within outline
 * - Create new frame button
 * 
 * @doc.type component
 * @doc.purpose Navigation panel for frames
 * @doc.layer components
 * @doc.pattern Component
 */

import {
  Box,
  Button,
  IconButton,
  Typography,
  ListItem,
  ListItemText,
  Chip,
  Tooltip,
  InteractiveList as List,
} from '@ghatana/ui';
import { TextField, ListItemButton, Collapse } from '@ghatana/ui';
import React, { useState, useMemo } from 'react';

import { type Frame , FrameManager} from '../lib/canvas/FrameManager';
import { CANVAS_TOKENS } from '../tokens/canvas-tokens';

const { SPACING, COLORS, TYPOGRAPHY, FONT_WEIGHT } = CANVAS_TOKENS;

/**
 *
 */
export interface OutlinePanelProps {
  /** Frame manager instance */
  frameManager: FrameManager;
  
  /** Current viewport for centering new frames */
  viewport?: { x: number; y: number; zoom: number };
  
  /** Callback when frame clicked for navigation */
  onFrameClick?: (frameId: string) => void;
  
  /** Callback when create frame clicked */
  onCreateFrame?: (bounds: { x: number; y: number; width: number; height: number }) => void;
  
  /** Show children nodes within frames */
  showChildren?: boolean;
  
  /** Get node label function (for showing children) */
  getNodeLabel?: (nodeId: string) => string;
}

/**
 *
 */
export function OutlinePanel({
  frameManager,
  viewport,
  onFrameClick,
  onCreateFrame,
  showChildren = false,
  getNodeLabel,
}: OutlinePanelProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [expandedFrames, setExpandedFrames] = useState<Set<string>>(new Set());

  // Get all frames
  const frames = frameManager.getAllFrames();
  const homeFrame = frameManager.getHomeFrame();

  // Filter frames by search query
  const filteredFrames = useMemo(() => {
    if (!searchQuery.trim()) return frames;

    const query = searchQuery.toLowerCase();
    return frames.filter(frame =>
      frame.title.toLowerCase().includes(query) ||
      frame.lifecyclePhase?.toLowerCase().includes(query) ||
      frame.metadata?.description?.toLowerCase().includes(query)
    );
  }, [frames, searchQuery]);

  const handleFrameClick = (frameId: string) => {
    if (onFrameClick) {
      onFrameClick(frameId);
    } else {
      frameManager.focusFrame(frameId);
    }
  };

  const handleToggleExpand = (frameId: string) => {
    setExpandedFrames(prev => {
      const next = new Set(prev);
      if (next.has(frameId)) {
        next.delete(frameId);
      } else {
        next.add(frameId);
      }
      return next;
    });
  };

  const handleCreateFrame = () => {
    if (onCreateFrame && viewport) {
      const centerX = -viewport.x / viewport.zoom;
      const centerY = -viewport.y / viewport.zoom;
      
      onCreateFrame({
        x: centerX - 400, // Half of default width
        y: centerY - 300, // Half of default height
        width: 800,
        height: 600,
      });
    } else {
      // Fallback: create at origin
      frameManager.createFrame({
        title: 'New Frame',
        bounds: {
          x: frames.length * 1000,
          y: 0,
          width: 800,
          height: 600,
        },
      });
    }
  };

  const handleCreateLifecycleFrames = () => {
    frameManager.createLifecycleFrameSet();
  };

  return (
    <Box
      className="h-full flex flex-col" >
      {/* Header */}
      <Box
        style={{
          padding: SPACING.MD,
          borderBottom: `1px solid ${COLORS.BORDER_LIGHT}`,
        }}
      >
        <Typography
          className="flex items-center text-lg font-semibold gap-2" style={{ color: COLORS.TEXT_PRIMARY, backgroundColor: COLORS.PANEL_BG_LIGHT }} >
          <span>📋</span>
          Outline
        </Typography>
      </Box>

      {/* Search */}
      <Box style={{ padding: SPACING.MD, borderBottom: `1px solid ${COLORS.BORDER_LIGHT}` }}>
        <TextField
          fullWidth
          size="small"
          placeholder="Search frames..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          InputProps={{
            startAdornment: <span style={{ marginRight: SPACING.SM, color: COLORS.TEXT_SECONDARY }}>🔍</span>,
            style: {
              fontSize: TYPOGRAPHY.SM,
            },
          }}
        />
      </Box>

      {/* Frame List */}
      <Box
        className="flex-1 overflow-y-auto"
        style={{ paddingTop: SPACING.SM, paddingBottom: SPACING.SM }}
      >
        {filteredFrames.length === 0 && (
          <Box
            className="text-center p-8" >
            {searchQuery ? (
              <>
                <Typography style={{ fontSize: TYPOGRAPHY.SM, marginBottom: SPACING.SM }}>
                  No frames found
                </Typography>
                <Typography style={{ fontSize: TYPOGRAPHY.XS }}>
                  Try a different search term
                </Typography>
              </>
            ) : (
              <>
                <Typography style={{ fontSize: TYPOGRAPHY.SM, marginBottom: SPACING.SM }}>
                  No frames yet
                </Typography>
                <Typography style={{ fontSize: TYPOGRAPHY.XS }}>
                  Create your first frame to get started
                </Typography>
              </>
            )}
          </Box>
        )}

        <List disablePadding>
          {filteredFrames.map(frame => (
            <FrameOutlineItem
              key={frame.id}
              frame={frame}
              isHome={frame.id === homeFrame?.id}
              expanded={expandedFrames.has(frame.id)}
              showChildren={showChildren}
              onToggleExpand={() => handleToggleExpand(frame.id)}
              onClick={() => handleFrameClick(frame.id)}
              getNodeLabel={getNodeLabel}
            />
          ))}
        </List>
      </Box>

      {/* Actions */}
      <Box
        style={{
          padding: SPACING.MD,
          borderTop: `1px solid ${COLORS.BORDER_LIGHT}`,
          display: 'flex',
          flexDirection: 'column',
          gap: SPACING.SM,
        }}
      >
        <Button
          fullWidth
          variant="outlined"
          onClick={handleCreateFrame}
          style={{ fontSize: TYPOGRAPHY.SM }}
        >
          + Add Frame
        </Button>

        {frames.length === 0 && (
          <Button
            fullWidth
            variant="contained"
            onClick={handleCreateLifecycleFrames}
            style={{ fontSize: TYPOGRAPHY.SM }}
          >
            🎯 Create Lifecycle Frames
          </Button>
        )}
      </Box>
    </Box>
  );
}

/**
 * Individual frame item in outline
 */
interface FrameOutlineItemProps {
  frame: Frame;
  isHome: boolean;
  expanded: boolean;
  showChildren: boolean;
  onToggleExpand: () => void;
  onClick: () => void;
  getNodeLabel?: (nodeId: string) => string;
}

/**
 *
 */
function FrameOutlineItem({
  frame,
  isHome,
  expanded,
  showChildren,
  onToggleExpand,
  onClick,
  getNodeLabel,
}: FrameOutlineItemProps) {
  const hasChildren = frame.children.length > 0;

  return (
    <>
      <ListItem disablePadding>
        <ListItemButton
          onClick={onClick}
          className="hover:bg-gray-100"
          style={{
            paddingTop: SPACING.SM,
            paddingBottom: SPACING.SM,
            paddingLeft: SPACING.MD,
            paddingRight: SPACING.MD,
          }}
        >
          {/* Expand/Collapse button */}
          {showChildren && hasChildren && (
            <IconButton
              size="small"
              onClick={(e) => {
                e.stopPropagation();
                onToggleExpand();
              }}
              style={{ marginRight: SPACING.SM }}
            >
              <span style={{ fontSize: '12px', color: COLORS.TEXT_PRIMARY, marginBottom: SPACING.XS / 2 }}>
                {expanded ? '▼' : '▶'}
              </span>
            </IconButton>
          )}

          {/* Frame info */}
          <Box className="flex-1 min-w-0 gap-2">
            <Box className="flex items-center gap-2" style={{ marginBottom: SPACING.XS / 2 }}>
              <Typography
                className="overflow-hidden text-ellipsis whitespace-nowrap text-sm font-medium" >
                {frame.title}
              </Typography>
              
              {isHome && (
                <Tooltip title="Home frame">
                  <span style={{ fontSize: '14px' }}>🏠</span>
                </Tooltip>
              )}
              
              {frame.isLocked && (
                <Tooltip title="Locked">
                  <span style={{ fontSize: '14px' }}>🔒</span>
                </Tooltip>
              )}
            </Box>

            {/* Metadata */}
            <Box className="flex items-center gap-2" >
              {frame.lifecyclePhase && (
                <Chip
                  label={frame.lifecyclePhase}
                  size="small"
                  className="h-[18px] text-white" style={{ fontSize: TYPOGRAPHY.XS, backgroundColor: frame.color || COLORS.NEUTRAL_200 }}
                />
              )}
              
              {hasChildren && (
                <Typography
                  style={{
                    fontSize: TYPOGRAPHY.XS,
                    color: COLORS.TEXT_SECONDARY,
                  }}
                >
                  {frame.children.length} {frame.children.length === 1 ? 'item' : 'items'}
                </Typography>
              )}
            </Box>
          </Box>
        </ListItemButton>
      </ListItem>

      {/* Children (if expanded) */}
      {showChildren && hasChildren && (
        <Collapse in={expanded}>
          <List disablePadding style={{ paddingLeft: SPACING.XL }}>
            {frame.children.map(childId => (
              <ListItem
                key={childId}
                disablePadding
                style={{
                  paddingTop: SPACING.XS,
                  paddingBottom: SPACING.XS,
                  paddingLeft: SPACING.MD,
                  paddingRight: SPACING.MD,
                }}
              >
                <Typography
                  style={{
                    fontSize: TYPOGRAPHY.XS,
                    color: COLORS.TEXT_SECONDARY,
                  }}
                >
                  • {getNodeLabel ? getNodeLabel(childId) : childId}
                </Typography>
              </ListItem>
            ))}
          </List>
        </Collapse>
      )}
    </>
  );
}
