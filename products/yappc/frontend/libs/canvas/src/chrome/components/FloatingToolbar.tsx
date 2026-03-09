/**
 * FloatingToolbar Component
 * 
 * Enhanced Miro-style floating toolbar with context-specific actions
 * 
 * Features:
 * - Smart positioning: flips above/below/left/right based on viewport space
 * - 300ms delay after selection before appearing
 * - Max 6 actions in main toolbar, rest in overflow menu
 * - Context-specific actions based on selection type
 * - Smooth animations and transitions
 * - Keyboard accessible
 * 
 * @doc.type component
 * @doc.purpose Context-aware floating toolbar
 * @doc.layer components
 * @doc.pattern Progressive Disclosure
 */

import { Copy as DuplicateIcon, Trash2 as DeleteIcon, Lock as LockIcon, LockOpen as UnlockIcon, MoreHorizontal as MoreIcon, BringToFront as BringForwardIcon, SendToBack as SendBackwardIcon, AlignStartHorizontal as AlignLeftIcon, AlignStartVertical as AlignTopIcon, PaintBucket as ColorIcon, Link as LinkIcon, Pencil as EditIcon, Users as GroupIcon } from 'lucide-react';
import { Box, IconButton, Tooltip, Divider, Menu, MenuItem, ListItemIcon, ListItemText } from '@ghatana/ui';
import { useAtom } from 'jotai';
import React, { useMemo, useEffect, useState } from 'react';

import { chromeFloatingToolbarVisibleAtom, chromeFloatingToolbarPositionAtom } from '../state/chrome-atoms';
import { CANVAS_TOKENS } from '../tokens/canvas-tokens';

import type { ContextAction } from '../lib/actions/ContextActionsManager';

const { SPACING, Z_INDEX, COLORS, SHADOWS, CANVAS } = CANVAS_TOKENS;

export interface FloatingToolbarProps {
  /** Selection bounding box in canvas coordinates */
  selectionBounds: {
    x: number;
    y: number;
    width: number;
    height: number;
  } | null;

  /** Viewport transformation */
  viewport: {
    x: number;
    y: number;
    zoom: number;
  };

  /** Actions to display */
  actions: ContextAction[];

  /** Whether the toolbar is visible */
  visible?: boolean;

  /** Offset from selection (default: 60px) */
  offset?: number;

  /** Maximum actions in main toolbar (rest go in "More" menu) */
  maxActions?: number;

  /** Delay before showing toolbar (ms, default: 300) */
  showDelay?: number;

  /** Selection type for context-specific actions */
  selectionType?: 'single' | 'multiple' | 'frame' | 'shape' | 'text' | 'connector';
}

/**
 * Toolbar position placement
 */
type ToolbarPlacement = 'top' | 'bottom' | 'left' | 'right';

/**
 * Calculate optimal toolbar placement based on available viewport space
 */
function calculateOptimalPlacement(
  selectionBounds: { x: number; y: number; width: number; height: number },
  viewport: { x: number; y: number; zoom: number },
  offset: number
): { placement: ToolbarPlacement; x: number; y: number } {
  const viewportWidth = window.innerWidth;
  const viewportHeight = window.innerHeight;
  const toolbarHeight = CANVAS.FLOATING_TOOLBAR_HEIGHT;
  const toolbarWidth = 300; // Approximate width

  // Transform canvas coordinates to screen coordinates
  const centerX = (selectionBounds.x + selectionBounds.width / 2 - viewport.x) * viewport.zoom;
  const topY = (selectionBounds.y - viewport.y) * viewport.zoom;
  const bottomY = (selectionBounds.y + selectionBounds.height - viewport.y) * viewport.zoom;
  const leftX = (selectionBounds.x - viewport.x) * viewport.zoom;
  const rightX = (selectionBounds.x + selectionBounds.width - viewport.x) * viewport.zoom;

  // Check space above
  const spaceAbove = topY;
  const spaceBelow = viewportHeight - bottomY;
  const spaceLeft = leftX;
  const spaceRight = viewportWidth - rightX;

  // Prefer top placement if enough space
  if (spaceAbove >= toolbarHeight + offset) {
    return {
      placement: 'top',
      x: centerX,
      y: topY - offset,
    };
  }

  // Try bottom if more space there
  if (spaceBelow >= toolbarHeight + offset) {
    return {
      placement: 'bottom',
      x: centerX,
      y: bottomY + offset,
    };
  }

  // Try right if horizontal space available
  if (spaceRight >= toolbarWidth + offset) {
    return {
      placement: 'right',
      x: rightX + offset,
      y: (topY + bottomY) / 2,
    };
  }

  // Try left as last resort
  if (spaceLeft >= toolbarWidth + offset) {
    return {
      placement: 'left',
      x: leftX - offset,
      y: (topY + bottomY) / 2,
    };
  }

  // Default to top even if not enough space
  return {
    placement: 'top',
    x: centerX,
    y: Math.max(toolbarHeight, topY - offset),
  };
}

/**
 * Get icon for action type
 */
function getActionIcon(actionId: string): React.ReactNode {
  const iconMap: Record<string, React.ReactNode> = {
    duplicate: <DuplicateIcon size={16} />,
    delete: <DeleteIcon size={16} />,
    lock: <LockIcon size={16} />,
    unlock: <UnlockIcon size={16} />,
    'bring-forward': <BringForwardIcon size={16} />,
    'send-backward': <SendBackwardIcon size={16} />,
    'align-left': <AlignLeftIcon size={16} />,
    'align-top': <AlignTopIcon size={16} />,
    link: <LinkIcon size={16} />,
    edit: <EditIcon size={16} />,
    group: <GroupIcon size={16} />,
    color: <ColorIcon size={16} />,
  };

  return iconMap[actionId] || null;
}

/**
 * Get context-specific actions based on selection type
 */
function getContextSpecificActions(
  actions: ContextAction[],
  selectionType?: string
): ContextAction[] {
  if (!selectionType) return actions;

  // Priority order for different selection types
  const priorityMap: Record<string, string[]> = {
    single: ['edit', 'duplicate', 'delete', 'lock', 'color', 'bring-forward'],
    multiple: ['group', 'align-left', 'align-top', 'duplicate', 'delete', 'lock'],
    frame: ['edit', 'duplicate', 'delete', 'bring-forward', 'send-backward', 'lock'],
    shape: ['color', 'duplicate', 'delete', 'bring-forward', 'send-backward', 'lock'],
    text: ['edit', 'color', 'duplicate', 'delete', 'bring-forward', 'lock'],
    connector: ['edit', 'delete', 'bring-forward', 'send-backward', 'lock'],
  };

  const priorities = priorityMap[selectionType] || [];

  // Sort actions by priority
  return [...actions].sort((a, b) => {
    const aPriority = priorities.indexOf(a.id);
    const bPriority = priorities.indexOf(b.id);

    if (aPriority === -1 && bPriority === -1) return 0;
    if (aPriority === -1) return 1;
    if (bPriority === -1) return -1;

    return aPriority - bPriority;
  });
}

/**
 * FloatingToolbar - Miro-style toolbar that floats above selected objects
 * 
 * Automatically positions itself above the selection center and follows
 * as the selection moves. Provides quick access to common actions without
 * requiring users to move their cursor to a fixed toolbar location.
 * 
 * @example
 * ```tsx
 * <FloatingToolbar
 *   selectionBounds={calculateBounds(selectedNodes)}
 *   viewport={viewport}
 *   actions={contextActions}
 *   visible={selectedNodes.length > 0}
 * />
 * ```
 */
export function FloatingToolbar({
  selectionBounds,
  viewport,
  actions,
  visible = true,
  offset = 60,
  maxActions = 6,
  showDelay = 300,
  selectionType,
}: FloatingToolbarProps) {
  const [moreMenuAnchor, setMoreMenuAnchor] = useState<HTMLElement | null>(null);
  const [showToolbar, setShowToolbar] = useState(false);
  const [, setToolbarVisible] = useAtom(chromeFloatingToolbarVisibleAtom);
  const [, setToolbarPosition] = useAtom(chromeFloatingToolbarPositionAtom);

  // Delay showing toolbar after selection
  useEffect(() => {
    if (visible && selectionBounds) {
      const timer = setTimeout(() => {
        setShowToolbar(true);
        setToolbarVisible(true);
      }, showDelay);

      return () => {
        clearTimeout(timer);
        setShowToolbar(false);
        setToolbarVisible(false);
      };
    } else {
      setShowToolbar(false);
      setToolbarVisible(false);
    }
  }, [visible, selectionBounds, showDelay, setToolbarVisible]);

  // Calculate optimal toolbar position with smart placement
  const { position, placement } = useMemo(() => {
    if (!selectionBounds) {
      return { position: null, placement: 'top' as ToolbarPlacement };
    }

    const result = calculateOptimalPlacement(selectionBounds, viewport, offset);

    // Update global position state
    setToolbarPosition({ x: result.x, y: result.y });

    return {
      position: { x: result.x, y: result.y },
      placement: result.placement,
    };
  }, [selectionBounds, viewport, offset, setToolbarPosition]);

  // Get context-specific actions and split into primary and overflow
  const sortedActions = useMemo(
    () => getContextSpecificActions(actions, selectionType),
    [actions, selectionType]
  );

  const primaryActions = useMemo(
    () => sortedActions.slice(0, maxActions),
    [sortedActions, maxActions]
  );

  const overflowActions = useMemo(
    () => sortedActions.slice(maxActions),
    [sortedActions, maxActions]
  );

  // Don't render if not visible, no position, or delayed
  if (!visible || !showToolbar || !position || !selectionBounds || actions.length === 0) {
    return null;
  }

  // Calculate transform based on placement
  const getTransform = () => {
    switch (placement) {
      case 'top':
      case 'bottom':
        return 'translateX(-50%)';
      case 'left':
        return 'translate(-100%, -50%)';
      case 'right':
        return 'translateY(-50%)';
      default:
        return 'translateX(-50%)';
    }
  };

  return (
    <>
      <Box
        className="fixed flex items-center gap-0" style={{ left: position.x, top: position.y, transform: getTransform(), zIndex: Z_INDEX.FLOATING_TOOLBAR || 450, backgroundColor: COLORS.PANEL_BG_LIGHT, borderRadius: SPACING.SM, border: `1px solid ${COLORS.BORDER_LIGHT}` }}
        role="toolbar"
        aria-label="Floating actions"
        onClick={(e) => e.stopPropagation()}
        onMouseDown={(e) => e.stopPropagation()}
      >
        {/* Primary actions */}
        {primaryActions.map((action, index) => (
          <React.Fragment key={action.id}>
            {index > 0 && index % 3 === 0 && (
              <Divider
                orientation="vertical"
                flexItem
                style={{
                  marginLeft: CANVAS_TOKENS.SPACING.XXS,
                  marginRight: CANVAS_TOKENS.SPACING.XXS,
                  marginTop: CANVAS_TOKENS.SPACING.XXS,
                  marginBottom: CANVAS_TOKENS.SPACING.XXS,
                }}
              />
            )}
            <Tooltip
              title={
                <Box>
                  <Box>{action.label}</Box>
                  {action.shortcut && (
                    <Box
                      component="span"
                      className="text-xs block mt-1 opacity-[0.7]"
                    >
                      {action.shortcut}
                    </Box>
                  )}
                </Box>
              }
              placement="top"
              arrow
            >
              <span>
                <IconButton
                  onClick={(e) => {
                    e.stopPropagation();
                    action.execute();
                  }}
                  disabled={!action.isEnabled}
                  size="sm"
                  className="w-[32px] h-[32px]" style={{ color: COLORS.TEXT_PRIMARY }}
                  aria-label={action.label}
                >
                  {action.icon || getActionIcon(action.id)}
                </IconButton>
              </span>
            </Tooltip>
          </React.Fragment>
        ))}

        {/* More menu for overflow actions */}
        {overflowActions.length > 0 && (
          <>
            <Divider
              orientation="vertical"
              flexItem
              style={{
                marginLeft: SPACING.XXS,
                marginRight: SPACING.XXS,
                marginTop: SPACING.XXS,
                marginBottom: SPACING.XXS,
              }}
            />
            <Tooltip title="More actions" placement="top" arrow>
              <IconButton
                onClick={(e) => {
                  e.stopPropagation();
                  setMoreMenuAnchor(e.currentTarget);
                }}
                size="sm"
                className="w-[32px] h-[32px]"
                aria-label="More actions"
              >
                <MoreIcon size={16} />
              </IconButton>
            </Tooltip>
          </>
        )}
      </Box>

      {/* Overflow menu */}
      <Menu
        anchorEl={moreMenuAnchor}
        open={Boolean(moreMenuAnchor)}
        onClose={() => setMoreMenuAnchor(null)}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'center',
        }}
        transformOrigin={{
          vertical: 'top',
          horizontal: 'center',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {overflowActions.map((action) => (
          <MenuItem
            key={action.id}
            onClick={(e) => {
              e.stopPropagation();
              action.execute();
              setMoreMenuAnchor(null);
            }}
            disabled={!action.isEnabled}
          >
            {action.icon && (
              <ListItemIcon>
                {action.icon}
              </ListItemIcon>
            )}
            <ListItemText>{action.label}</ListItemText>
            {action.shortcut && (
              <Box
                component="span"
                className="ml-4 text-xs opacity-[0.6]"
              >
                {action.shortcut}
              </Box>
            )}
          </MenuItem>
        ))}
      </Menu>
    </>
  );
}

/**
 * Calculate bounding box for multiple nodes
 */
export function calculateSelectionBounds(
  nodes: Array<{ position: { x: number; y: number }; width?: number; height?: number }>
): { x: number; y: number; width: number; height: number } | null {
  if (nodes.length === 0) return null;

  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;

  nodes.forEach((node) => {
    const width = node.width || 100;
    const height = node.height || 100;

    minX = Math.min(minX, node.position.x);
    minY = Math.min(minY, node.position.y);
    maxX = Math.max(maxX, node.position.x + width);
    maxY = Math.max(maxY, node.position.y + height);
  });

  return {
    x: minX,
    y: minY,
    width: maxX - minX,
    height: maxY - minY,
  };
}
