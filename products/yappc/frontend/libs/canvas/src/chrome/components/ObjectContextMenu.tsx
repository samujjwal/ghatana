/**
 * ObjectContextMenu Component
 * 
 * Enhanced right-click context menu with search and recent actions
 * 
 * Features:
 * - Search filter input at top
 * - Recent actions section (top 3)
 * - Categorized action groups (Edit, Arrange, Style, Other)
 * - Keyboard shortcuts displayed in right column
 * - Fuzzy search with highlighting
 * - Keyboard navigation (arrow keys)
 * 
 * @doc.type component
 * @doc.purpose Enhanced context menu for canvas objects
 * @doc.layer components
 * @doc.pattern Context Menu
 */

import { Copy as DuplicateIcon, Trash2 as DeleteIcon, Lock as LockIcon, LockOpen as UnlockIcon, BringToFront as BringToFrontIcon, SendToBack as SendToBackIcon, Users as GroupIcon, Ungroup as UngroupIcon, Pencil as EditIcon, Eye as ShowIcon, EyeOff as HideIcon, Search as SearchIcon, History as HistoryIcon } from 'lucide-react';
import { Menu, MenuItem, ListItemIcon, ListItemText, Divider, Box, Typography, Input as InputBase } from '@ghatana/ui';
import { useAtom } from 'jotai';
import React, { useState, useMemo, useEffect, useRef } from 'react';

import { chromeRecentActionsAtom, addRecentActionAtom } from '../state/chrome-atoms';
import { CANVAS_TOKENS } from '../tokens/canvas-tokens';

import type { ContextAction } from '../lib/actions/ContextActionsManager';

const { SPACING, COLORS, TYPOGRAPHY, FONT_WEIGHT, RADIUS } = CANVAS_TOKENS;

export interface ObjectContextMenuProps {
  /** Mouse position for menu */
  position: { x: number; y: number } | null;

  /** Actions to display */
  actions: ContextAction[];

  /** Callback when menu closes */
  onClose: () => void;

  /** Selected object info (for menu header) */
  selectedInfo?: {
    type: string;
    count: number;
  };

  /** Enable search filter */
  enableSearch?: boolean;

  /** Show recent actions */
  showRecentActions?: boolean;

  /** Maximum recent actions to show */
  maxRecentActions?: number;
}

/**
 * Get icon for action
 */
function getActionIcon(actionId: string): React.ReactNode {
  const iconMap: Record<string, React.ReactNode> = {
    duplicate: <DuplicateIcon size={16} />,
    delete: <DeleteIcon size={16} />,
    lock: <LockIcon size={16} />,
    unlock: <UnlockIcon size={16} />,
    'bring-to-front': <BringToFrontIcon size={16} />,
    'send-to-back': <SendToBackIcon size={16} />,
    group: <GroupIcon size={16} />,
    ungroup: <UngroupIcon size={16} />,
    edit: <EditIcon size={16} />,
    show: <ShowIcon size={16} />,
    hide: <HideIcon size={16} />,
  };

  return iconMap[actionId] || null;
}

/**
 * Simple fuzzy search matcher
 */
function fuzzyMatch(text: string, query: string): boolean {
  if (!query) return true;

  const lowerText = text.toLowerCase();
  const lowerQuery = query.toLowerCase();

  let queryIndex = 0;
  for (let i = 0; i < lowerText.length && queryIndex < lowerQuery.length; i++) {
    if (lowerText[i] === lowerQuery[queryIndex]) {
      queryIndex++;
    }
  }

  return queryIndex === lowerQuery.length;
}

/**
 * Highlight matching characters in text
 */
function highlightMatch(text: string, query: string): React.ReactNode {
  if (!query) return text;

  const lowerText = text.toLowerCase();
  const lowerQuery = query.toLowerCase();
  const parts: React.ReactNode[] = [];
  let lastIndex = 0;
  let queryIndex = 0;

  for (let i = 0; i < lowerText.length && queryIndex < lowerQuery.length; i++) {
    if (lowerText[i] === lowerQuery[queryIndex]) {
      if (i > lastIndex) {
        parts.push(text.substring(lastIndex, i));
      }
      parts.push(
        <Box component="span" key={i} style={{ fontWeight: FONT_WEIGHT.BOLD, color: COLORS.PRIMARY }}>
          {text[i]}
        </Box>
      );
      lastIndex = i + 1;
      queryIndex++;
    }
  }

  if (lastIndex < text.length) {
    parts.push(text.substring(lastIndex));
  }

  return parts;
}

/**
 * ObjectContextMenu - Right-click context menu for canvas objects
 * 
 * Provides quick access to common object actions without requiring
 * users to find them in toolbars or menus. Adapts based on selection.
 * 
 * @example
 * ```tsx
 * const [contextMenu, setContextMenu] = useState<{x: number, y: number} | null>(null);
 * 
 * <ObjectContextMenu
 *   position={contextMenu}
 *   actions={contextActions}
 *   onClose={() => setContextMenu(null)}
 *   selectedInfo={{ type: 'sticky-note', count: 3 }}
 * />
 * ```
 */
export function ObjectContextMenu({
  position,
  actions,
  onClose,
  selectedInfo,
  enableSearch = true,
  showRecentActions = true,
  maxRecentActions = 3,
}: ObjectContextMenuProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [recentActions] = useAtom(chromeRecentActionsAtom);
  const [, addRecentAction] = useAtom(addRecentActionAtom);
  const searchInputRef = useRef<HTMLInputElement>(null);

  // Focus search input when menu opens
  useEffect(() => {
    if (position && enableSearch && searchInputRef.current) {
      setTimeout(() => searchInputRef.current?.focus(), 100);
    }
  }, [position, enableSearch]);

  // Filter actions by search query
  const filteredActions = useMemo(() => {
    if (!searchQuery) return actions;
    return actions.filter((action) => fuzzyMatch(action.label, searchQuery));
  }, [actions, searchQuery]);

  // Get recent actions that are in current action list
  const recentActionsList = useMemo(() => {
    if (!showRecentActions) return [];

    return recentActions
      .slice(0, maxRecentActions)
      .map((recent) => actions.find((a) => a.id === recent.id))
      .filter((a): a is ContextAction => a !== undefined);
  }, [recentActions, actions, showRecentActions, maxRecentActions]);

  // Group filtered actions by category
  const actionsByCategory = useMemo(() => {
    const groups: Record<string, ContextAction[]> = {
      edit: [],
      arrange: [],
      style: [],
      other: [],
    };

    filteredActions.forEach((action) => {
      const category = action.category || 'other';
      if (groups[category]) {
        groups[category].push(action);
      } else {
        groups.other.push(action);
      }
    });

    return groups;
  }, [filteredActions]);

  const handleActionExecute = (action: ContextAction) => {
    action.execute();
    addRecentAction({ id: action.id, label: action.label });
    onClose();
  };

  return (
    <Menu
      open={Boolean(position)}
      onClose={onClose}
      anchorReference="anchorPosition"
      anchorPosition={
        position ? { top: position.y, left: position.x } : undefined
      }
      onClick={(e) => e.stopPropagation()}
      onContextMenu={(e) => e.preventDefault()}
      slotProps={{
        paper: {
          style: {
            minWidth: 280,
            maxWidth: 320,
            boxShadow: CANVAS_TOKENS.SHADOWS.LG,
            borderRadius: RADIUS.MD,
            border: `1px solid ${COLORS.BORDER_LIGHT}`,
          },
        },
      }}
    >
      {/* Search filter */}
      {enableSearch && (
        <Box style={{ paddingLeft: SPACING.SM, paddingRight: SPACING.SM, paddingTop: SPACING.SM, paddingBottom: SPACING.SM, borderBottom: `1px solid ${COLORS.BORDER_LIGHT}` }}>
          <Box
            className="flex items-center gap-1 px-2 py-1 rounded-[RADIUS.SMpx]" >
            <SearchIcon style={{ fontSize: TYPOGRAPHY.SM, color: COLORS.TEXT_SECONDARY }} />
            <InputBase
              inputRef={searchInputRef}
              placeholder="Search actions..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="flex-1" style={{ fontSize: TYPOGRAPHY.SM, color: COLORS.TEXT_SECONDARY, backgroundColor: COLORS.NEUTRAL_100 }}
            />
          </Box>
        </Box>
      )}

      {/* Menu header with selection info */}
      {selectedInfo && (
        <>
          <Box style={{ paddingLeft: SPACING.MD, paddingRight: SPACING.MD, paddingTop: SPACING.SM, paddingBottom: SPACING.SM }}>
            <Typography as="span" className="text-xs text-gray-500" style={{ color: COLORS.TEXT_SECONDARY, fontSize: TYPOGRAPHY.XS }}>
              {selectedInfo.count === 1
                ? `1 ${selectedInfo.type}`
                : `${selectedInfo.count} objects`}
            </Typography>
          </Box>
          <Divider />
        </>
      )}

      {/* Recent actions */}
      {!searchQuery && recentActionsList.length > 0 && (
        <>
          <Box style={{ paddingLeft: SPACING.MD, paddingRight: SPACING.MD, paddingTop: SPACING.XS, paddingBottom: SPACING.XS, marginTop: SPACING.XS }}>
            <Typography
              as="span" className="text-xs text-gray-500"
              className="uppercase text-xs font-semibold" >
              Recent
            </Typography>
          </Box>
          {recentActionsList.map((action) => (
            <MenuItem
              key={`recent-${action.id}`}
              onClick={(e) => {
                e.stopPropagation();
                handleActionExecute(action);
              }}
              disabled={!action.isEnabled}
              style={{ paddingLeft: SPACING.MD, paddingRight: SPACING.MD, paddingTop: SPACING.SM, paddingBottom: SPACING.SM }}
            >
              <ListItemIcon className="min-w-[32px]">
                <HistoryIcon style={{ fontSize: TYPOGRAPHY.SM, color: COLORS.TEXT_SECONDARY }} />
              </ListItemIcon>
              <ListItemText
                primary={action.label}
                primaryTypographyProps={{ fontSize: TYPOGRAPHY.SM }}
              />
              {action.shortcut && (
                <Typography
                  as="span" className="text-xs text-gray-500"
                  className="ml-4 text-xs" style={{ color: COLORS.TEXT_SECONDARY }} >
                  {action.shortcut}
                </Typography>
              )}
            </MenuItem>
          ))}
          <Divider style={{ marginTop: SPACING.XS, marginBottom: SPACING.XS }} />
        </>
      )}

      {/* Edit actions */}
      {actionsByCategory.edit.length > 0 && (
        <>
          <Box style={{ paddingLeft: SPACING.MD, paddingRight: SPACING.MD, paddingTop: SPACING.XS, paddingBottom: SPACING.XS, marginTop: SPACING.XS }}>
            <Typography
              as="span" className="text-xs text-gray-500"
              className="uppercase text-xs font-semibold" style={{ color: COLORS.TEXT_SECONDARY }} >
              Edit
            </Typography>
          </Box>
          {actionsByCategory.edit.filter((a) => a.id !== 'delete').map((action) => (
            <MenuItem
              key={action.id}
              onClick={(e) => {
                e.stopPropagation();
                handleActionExecute(action);
              }}
              disabled={!action.isEnabled}
              style={{ paddingLeft: SPACING.MD, paddingRight: SPACING.MD, paddingTop: SPACING.SM, paddingBottom: SPACING.SM }}
            >
              <ListItemIcon className="min-w-[32px]">
                {action.icon || getActionIcon(action.id)}
              </ListItemIcon>
              <ListItemText
                primary={highlightMatch(action.label, searchQuery)}
                primaryTypographyProps={{ fontSize: TYPOGRAPHY.SM }}
              />
              {action.shortcut && (
                <Typography
                  as="span" className="text-xs text-gray-500"
                  className="ml-4 text-xs" style={{ color: COLORS.TEXT_SECONDARY }} >
                  {action.shortcut}
                </Typography>
              )}
            </MenuItem>
          ))}
          <Divider style={{ marginTop: SPACING.XS, marginBottom: SPACING.XS }} />
        </>
      )}

      {/* Arrange actions */}
      {actionsByCategory.arrange.length > 0 && (
        <>
          <Box style={{ paddingLeft: SPACING.MD, paddingRight: SPACING.MD, paddingTop: SPACING.XS, paddingBottom: SPACING.XS }}>
            <Typography
              as="span" className="text-xs text-gray-500"
              className="uppercase text-xs font-semibold" style={{ color: COLORS.TEXT_SECONDARY }} >
              Arrange
            </Typography>
          </Box>
          {actionsByCategory.arrange.map((action) => (
            <MenuItem
              key={action.id}
              onClick={(e) => {
                e.stopPropagation();
                handleActionExecute(action);
              }}
              disabled={!action.isEnabled}
              style={{ paddingLeft: SPACING.MD, paddingRight: SPACING.MD, paddingTop: SPACING.SM, paddingBottom: SPACING.SM }}
            >
              <ListItemIcon className="min-w-[32px]">
                {action.icon || getActionIcon(action.id)}
              </ListItemIcon>
              <ListItemText
                primary={highlightMatch(action.label, searchQuery)}
                primaryTypographyProps={{ fontSize: TYPOGRAPHY.SM }}
              />
              {action.shortcut && (
                <Typography
                  as="span" className="text-xs text-gray-500"
                  className="ml-4 text-xs" style={{ color: COLORS.TEXT_SECONDARY }} >
                  {action.shortcut}
                </Typography>
              )}
            </MenuItem>
          ))}
          <Divider style={{ marginTop: SPACING.XS, marginBottom: SPACING.XS }} />
        </>
      )}

      {/* Style actions */}
      {actionsByCategory.style.length > 0 && (
        <>
          <Box style={{ paddingLeft: SPACING.MD, paddingRight: SPACING.MD, paddingTop: SPACING.XS, paddingBottom: SPACING.XS }}>
            <Typography
              as="span" className="text-xs text-gray-500"
              className="uppercase text-xs font-semibold" style={{ color: COLORS.TEXT_SECONDARY }} >
              Style
            </Typography>
          </Box>
          {actionsByCategory.style.map((action) => (
            <MenuItem
              key={action.id}
              onClick={(e) => {
                e.stopPropagation();
                handleActionExecute(action);
              }}
              disabled={!action.isEnabled}
              style={{ paddingLeft: SPACING.MD, paddingRight: SPACING.MD, paddingTop: SPACING.SM, paddingBottom: SPACING.SM }}
            >
              <ListItemIcon className="min-w-[32px]">
                {action.icon || getActionIcon(action.id)}
              </ListItemIcon>
              <ListItemText
                primary={highlightMatch(action.label, searchQuery)}
                primaryTypographyProps={{ fontSize: TYPOGRAPHY.SM }}
              />
              {action.shortcut && (
                <Typography
                  as="span" className="text-xs text-gray-500"
                  className="ml-4 text-xs" style={{ color: COLORS.TEXT_SECONDARY }} >
                  {action.shortcut}
                </Typography>
              )}
            </MenuItem>
          ))}
          <Divider style={{ marginTop: SPACING.XS, marginBottom: SPACING.XS }} />
        </>
      )}

      {/* Delete action (always at bottom) */}
      {actionsByCategory.edit.find((a) => a.id === 'delete') && (
        <>
          <Divider style={{ marginTop: SPACING.XS, marginBottom: SPACING.XS }} />
          <MenuItem
            onClick={(e) => {
              e.stopPropagation();
              const deleteAction = actionsByCategory.edit.find((a) => a.id === 'delete');
              if (deleteAction) {
                handleActionExecute(deleteAction);
              }
            }}
            style={{
              paddingLeft: SPACING.MD,
              paddingRight: SPACING.MD,
              paddingTop: SPACING.SM,
              paddingBottom: SPACING.SM,
              color: COLORS.ERROR,
            }}
          >
            <ListItemIcon className="min-w-[32px]">
              <DeleteIcon style={{ fontSize: TYPOGRAPHY.SM, color: COLORS.ERROR }} />
            </ListItemIcon>
            <ListItemText
              primary="Delete"
              primaryTypographyProps={{ fontSize: TYPOGRAPHY.SM }}
            />
            <Typography as="span" className="text-xs text-gray-500" className="ml-4 text-xs" style={{ color: COLORS.ERROR }} >
              Del
            </Typography>
          </MenuItem>
        </>
      )}

      {/* No results message */}
      {searchQuery && filteredActions.length === 0 && (
        <Box className="text-center px-4 py-6" >
          <Typography style={{ color: COLORS.TEXT_SECONDARY, fontSize: TYPOGRAPHY.SM }}>
            No actions found for "{searchQuery}"
          </Typography>
        </Box>
      )}
    </Menu>
  );
}

/**
 * Hook for managing context menu state
 */
export function useObjectContextMenu() {
  const [contextMenu, setContextMenu] = useState<{
    x: number;
    y: number;
  } | null>(null);

  const handleContextMenu = React.useCallback((event: React.MouseEvent) => {
    event.preventDefault();
    event.stopPropagation();
    setContextMenu({
      x: event.clientX,
      y: event.clientY,
    });
  }, []);

  const handleClose = React.useCallback(() => {
    setContextMenu(null);
  }, []);

  return {
    contextMenu,
    handleContextMenu,
    handleClose,
  };
}
