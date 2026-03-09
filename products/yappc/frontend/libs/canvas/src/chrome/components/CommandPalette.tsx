/**
 * Command Palette Component
 * 
 * Cmd/Ctrl+K command palette for keyboard-first interaction
 * Implements fuzzy search, keyboard navigation, and recent commands
 * 
 * @doc.type component
 * @doc.purpose Universal command interface
 * @doc.layer components
 * @doc.pattern Component
 */

import {
  Dialog,
  Box,
  ListItem,
  ListItemText,
  Chip,
  Typography,
  Divider,
  InteractiveList as List,
} from '@ghatana/ui';
import { TextField, ListItemButton } from '@ghatana/ui';
import { useAtom } from 'jotai';
import React, { useState, useEffect, useRef, useCallback } from 'react';

import { chromeCommandPaletteOpenAtom } from '../state/chrome-atoms';
import { CANVAS_TOKENS } from '../tokens/canvas-tokens';

import type { CommandRegistry, Command, CommandContext } from '../lib/commands/CommandRegistry';

const { SPACING, COLORS, TYPOGRAPHY, Z_INDEX, SHADOWS, RADIUS } = CANVAS_TOKENS;

/**
 *
 */
export interface CommandPaletteProps {
  /** Command registry to use */
  registry: CommandRegistry;
  
  /** Current context for command filtering */
  context: CommandContext;
  
  /** Callback when command executed */
  onExecute?: (commandId: string) => void;
  
  /** Callback when palette closed */
  onClose?: () => void;
}

/**
 *
 */
export function CommandPalette({
  registry,
  context,
  onExecute,
  onClose,
}: CommandPaletteProps) {
  const [open, setOpen] = useAtom(chromeCommandPaletteOpenAtom);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<Command[]>([]);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  // Search commands when query changes
  useEffect(() => {
    if (open) {
      const searchResults = registry.search(query, context, 10);
      setResults(searchResults);
      setSelectedIndex(0);
    }
  }, [query, open, registry, context]);

  // Focus input when opened
  useEffect(() => {
    if (open) {
      setTimeout(() => {
        inputRef.current?.focus();
      }, 50);
      setQuery('');
    }
  }, [open]);

  // Keyboard navigation
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      switch (e.key) {
        case 'ArrowDown':
          e.preventDefault();
          setSelectedIndex(prev => Math.min(prev + 1, results.length - 1));
          break;
        
        case 'ArrowUp':
          e.preventDefault();
          setSelectedIndex(prev => Math.max(prev - 1, 0));
          break;
        
        case 'Enter':
          e.preventDefault();
          if (results[selectedIndex]) {
            handleExecute(results[selectedIndex].id);
          }
          break;
        
        case 'Escape':
          e.preventDefault();
          handleClose();
          break;
      }
    },
    [results, selectedIndex]
  );

  // Scroll selected item into view
  useEffect(() => {
    if (listRef.current) {
      const selectedElement = listRef.current.children[selectedIndex] as HTMLElement;
      if (selectedElement) {
        selectedElement.scrollIntoView({
          block: 'nearest',
          behavior: 'smooth',
        });
      }
    }
  }, [selectedIndex]);

  const handleExecute = async (commandId: string) => {
    try {
      await registry.execute(commandId, context);
      onExecute?.(commandId);
      handleClose();
    } catch (error) {
      console.error('Command execution failed:', error);
    }
  };

  const handleClose = () => {
    setOpen(false);
    setQuery('');
    setSelectedIndex(0);
    onClose?.();
  };

  const getCategoryColor = (category: Command['category']): string => {
    const colors: Record<Command['category'], string> = {
      create: COLORS.SUCCESS,
      navigate: COLORS.INFO,
      edit: COLORS.PRIMARY,
      view: COLORS.PHASE_VISION,
      help: COLORS.WARNING,
      arrange: COLORS.PHASE_ARCHITECTURE,
    };
    return colors[category];
  };

  const getCategoryIcon = (category: Command['category']): string => {
    const icons: Record<Command['category'], string> = {
      create: '✨',
      navigate: '🧭',
      edit: '✏️',
      view: '👁️',
      help: '❓',
      arrange: '📐',
    };
    return icons[category];
  };

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      maxWidth="md"
      fullWidth
      PaperProps={{
        style: {
          borderRadius: RADIUS.LG,
          boxShadow: SHADOWS.XL,
          overflow: 'hidden',
          maxHeight: '70vh',
        },
      }}
    >
      {/* Search Input */}
      <Box
        style={{
          padding: SPACING.MD,
          borderBottom: `1px solid ${COLORS.BORDER_LIGHT}`,
        }}
      >
        <TextField
          ref={inputRef}
          fullWidth
          placeholder="Type a command or search..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={handleKeyDown}
          autoComplete="off"
          InputProps={{
            startAdornment: (
              <span style={{ fontSize: '20px', marginRight: SPACING.SM, color: COLORS.TEXT_SECONDARY }}>🔍</span>
            ),
            style: {
              fontSize: TYPOGRAPHY.LG,
            },
          }}
        />
      </Box>

      {/* Results List */}
      <Box
        ref={listRef}
        className="overflow-y-auto"
        style={{ maxHeight: '50vh' }}
      >
        {results.length === 0 ? (
          <Box
            className="text-center p-8" >
            {query ? (
              <>
                <Typography variant="body1" style={{ marginBottom: SPACING.SM }}>
                  No commands found
                </Typography>
                <Typography variant="body2" style={{ fontSize: TYPOGRAPHY.SM }}>
                  Try a different search term
                </Typography>
              </>
            ) : (
              <>
                <Typography variant="body1" style={{ marginBottom: SPACING.SM }}>
                  Type to search commands
                </Typography>
                <Typography variant="body2" style={{ fontSize: TYPOGRAPHY.SM }}>
                  {registry.getRecentCommands(context).length > 0
                    ? 'Or browse recent commands below'
                    : 'Start typing to see available commands'}
                </Typography>
              </>
            )}
          </Box>
        ) : (
          <List style={{ paddingTop: SPACING.XS, paddingBottom: SPACING.XS }}>
            {results.map((command, index) => (
              <ListItem
                key={command.id}
                disablePadding
                style={{
                  backgroundColor: index === selectedIndex ? COLORS.SELECTION_BG : 'transparent',
                  borderLeft: index === selectedIndex ? `3px solid ${COLORS.PRIMARY}` : '3px solid transparent',
                }}
              >
                <ListItemButton
                  onClick={() => handleExecute(command.id)}
                  onMouseEnter={() => setSelectedIndex(index)}
                  style={{
                    paddingTop: SPACING.SM,
                    paddingBottom: SPACING.SM,
                    paddingLeft: SPACING.MD,
                    paddingRight: SPACING.MD,
                    gap: SPACING.MD,
                  }}
                >
                  {/* Icon */}
                  <Box
                    className="text-2xl flex items-center justify-center w-[32px] h-[32px]"
                  >
                    {command.icon || getCategoryIcon(command.category)}
                  </Box>

                  {/* Label and Description */}
                  <ListItemText
                    primary={
                      <Box className="flex items-center gap-2" >
                        <Typography
                          style={{
                            fontSize: TYPOGRAPHY.BASE,
                            fontWeight: CANVAS_TOKENS.FONT_WEIGHT.MEDIUM,
                            color: COLORS.TEXT_PRIMARY,
                          }}
                        >
                          {command.label}
                        </Typography>
                        <Chip
                          label={command.category}
                          size="small"
                          className="h-[20px] text-white text-xs" style={{ backgroundColor: getCategoryColor(command.category), fontWeight: CANVAS_TOKENS.FONT_WEIGHT.MEDIUM }} />
                      </Box>
                    }
                    secondary={
                      command.description && (
                        <Typography
                          style={{
                            fontSize: TYPOGRAPHY.SM,
                            color: COLORS.TEXT_SECONDARY,
                            marginTop: SPACING.XS / 2,
                          }}
                        >
                          {command.description}
                        </Typography>
                      )
                    }
                  />

                  {/* Keyboard Shortcut */}
                  {command.shortcut && (
                    <Box
                      className="flex items-center ml-auto"
                      style={{ gap: SPACING.XS }}
                    >
                      {command.shortcut.split('+').map((key, i) => (
                        <React.Fragment key={i}>
                          {i > 0 && <span style={{ color: COLORS.TEXT_SECONDARY, paddingTop: SPACING.XS / 2, paddingBottom: SPACING.XS / 2, backgroundColor: COLORS.NEUTRAL_100, fontWeight: CANVAS_TOKENS.FONT_WEIGHT.MEDIUM }}>+</span>}
                          <Box
                            className="inline-flex items-center justify-center min-w-[24px] h-[24px] rounded font-mono text-xs"
                            style={{ padding: '2px 6px', backgroundColor: COLORS.NEUTRAL_100, border: `1px solid ${COLORS.BORDER_LIGHT}`, color: COLORS.TEXT_SECONDARY }}
                          >
                            {key.replace('mod', '⌘')}
                          </Box>
                        </React.Fragment>
                      ))}
                    </Box>
                  )}
                </ListItemButton>
              </ListItem>
            ))}
          </List>
        )}
      </Box>

      {/* Footer */}
      <Box
        style={{
          padding: SPACING.SM,
          backgroundColor: COLORS.NEUTRAL_50,
          borderTop: `1px solid ${COLORS.BORDER_LIGHT}`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <Box className="flex gap-4 text-xs" >
          <Box className="flex items-center gap-1" >
            <kbd style={{ padding: '2px 6px', backgroundColor: COLORS.NEUTRAL_200, borderRadius: RADIUS.SM }}>↑↓</kbd>
            <span>Navigate</span>
          </Box>
          <Box className="flex items-center gap-1" >
            <kbd style={{ padding: '2px 6px', backgroundColor: COLORS.NEUTRAL_200, borderRadius: RADIUS.SM }}>↵</kbd>
            <span>Execute</span>
          </Box>
          <Box className="flex items-center gap-1" >
            <kbd style={{ padding: '2px 6px', backgroundColor: COLORS.NEUTRAL_200, borderRadius: RADIUS.SM }}>esc</kbd>
            <span>Close</span>
          </Box>
        </Box>
        <Typography style={{ fontSize: TYPOGRAPHY.XS, color: COLORS.TEXT_SECONDARY }}>
          {results.length} {results.length === 1 ? 'command' : 'commands'}
        </Typography>
      </Box>
    </Dialog>
  );
}

/**
 * Hook to setup global keyboard shortcut listener
 */
export function useCommandPaletteShortcut() {
  const [, setOpen] = useAtom(chromeCommandPaletteOpenAtom);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Cmd/Ctrl + K
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setOpen(true);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [setOpen]);
}
