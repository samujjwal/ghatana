/**
 * Action Discovery Palette
 *
 * Command palette as action discovery showing best next actions, recent actions,
 * current selection actions, navigation actions, and advanced search.
 *
 * @doc.type component
 * @doc.purpose Command palette for action discovery
 * @doc.layer product
 * @doc.pattern Command Palette
 */

import { Search, Clock, Navigation, Zap, AlertTriangle, ArrowRight } from 'lucide-react';
import React, { useState, useCallback, useEffect, useRef } from 'react';

import { Alert, Box, Button, Fade, Modal, TextField, Typography } from '@ghatana/design-system';

export interface Action {
  id: string;
  title: string;
  description?: string;
  category: 'next-best' | 'recent' | 'selection' | 'navigation' | 'advanced';
  shortcut?: string;
  disabled?: boolean;
  disabledReason?: string;
  dangerous?: boolean;
  onExecute: () => void;
  icon?: React.ReactNode;
}

export interface ActionGroup {
  title: string;
  actions: Action[];
}

export interface ActionDiscoveryPaletteProps {
  /** Action groups to display */
  actionGroups: ActionGroup[];
  /** Open state */
  open: boolean;
  /** On close callback */
  onClose: () => void;
  /** Initial search query */
  initialQuery?: string;
  /** Loading state */
  loading?: boolean;
}

function getSelectedAction(actions: readonly Action[], index: number): Action | undefined {
  if (index < 0 || index >= actions.length) {
    return undefined;
  }

  return actions.find((_, currentIndex) => currentIndex === index);
}

export const ActionDiscoveryPalette: React.FC<ActionDiscoveryPaletteProps> = ({
  actionGroups,
  open,
  onClose,
  initialQuery = '',
  loading = false,
}) => {
  const [query, setQuery] = useState(initialQuery);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [showConfirmation, setShowConfirmation] = useState(false);
  const [pendingAction, setPendingAction] = useState<Action | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  // Filter actions based on query
  const filteredGroups = React.useMemo<ActionGroup[]>(() => {
    if (!query.trim()) {
      return actionGroups;
    }

    const lowerQuery = query.toLowerCase();
    return actionGroups
      .map((group) => ({
        ...group,
        actions: group.actions.filter(
          (action) =>
            action.title.toLowerCase().includes(lowerQuery) ||
            action.description?.toLowerCase().includes(lowerQuery)
        ),
      }))
      .filter((group) => group.actions.length > 0);
  }, [actionGroups, query]);

  // Flatten actions for keyboard navigation
  const allActions = React.useMemo<Action[]>(
    () => filteredGroups.flatMap((group) => group.actions),
    [filteredGroups]
  );

  // Reset selected index when query changes
  useEffect(() => {
    setSelectedIndex(0);
  }, [query, filteredGroups]);

  // Focus input when opened
  useEffect(() => {
    if (open && inputRef.current) {
      inputRef.current.focus();
    }
  }, [open]);

  // Handle keyboard navigation
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      switch (e.key) {
        case 'ArrowDown':
          e.preventDefault();
          setSelectedIndex((prev) => Math.min(prev + 1, allActions.length - 1));
          break;
        case 'ArrowUp':
          e.preventDefault();
          setSelectedIndex((prev) => Math.max(prev - 1, 0));
          break;
        case 'Enter': {
          e.preventDefault();
          const action = getSelectedAction(allActions, selectedIndex);
          if (action) {
            if (action.disabled) return;
            if (action.dangerous) {
              setPendingAction(action);
              setShowConfirmation(true);
            } else {
              action.onExecute();
              onClose();
            }
          }
          break;
        }
        case 'Escape':
          e.preventDefault();
          if (showConfirmation) {
            setShowConfirmation(false);
            setPendingAction(null);
          } else {
            onClose();
          }
          break;
      }
    },
    [allActions, selectedIndex, onClose, showConfirmation]
  );

  const handleActionClick = (action: Action): void => {
    if (action.disabled) return;
    if (action.dangerous) {
      setPendingAction(action);
      setShowConfirmation(true);
    } else {
      action.onExecute();
      onClose();
    }
  };

  const handleConfirmDangerousAction = (): void => {
    if (pendingAction) {
      pendingAction.onExecute();
      setShowConfirmation(false);
      setPendingAction(null);
      onClose();
    }
  };

  const handleCancelConfirmation = (): void => {
    setShowConfirmation(false);
    setPendingAction(null);
  };

  const getCategoryIcon = (category: Action['category']): React.ReactNode => {
    switch (category) {
      case 'next-best':
        return <Zap className="w-4 h-4 text-warning-color" />;
      case 'recent':
        return <Clock className="w-4 h-4 text-info-color" />;
      case 'selection':
        return <ArrowRight className="w-4 h-4 text-success-color" />;
      case 'navigation':
        return <Navigation className="w-4 h-4 text-info-color" />;
      case 'advanced':
        return <Search className="w-4 h-4 text-muted-foreground" />;
    }
  };

  if (!open) return null;

  return (
    <Modal open={open} onClose={onClose}>
      <Fade in={open}>
        <Box
          data-testid="command-palette"
          className="absolute left-1/2 top-[20vh] w-full max-w-2xl -translate-x-1/2 overflow-hidden rounded-lg bg-surface shadow-2xl"
          onKeyDown={handleKeyDown}
          role="dialog"
          aria-modal="true"
          aria-label="Command palette"
        >
        {/* Search Input */}
        <div className="flex items-center border-b border-border px-4 py-3">
          <Search className="w-5 h-5 text-muted-foreground mr-3" />
          <TextField
            ref={inputRef as unknown as React.Ref<HTMLDivElement>}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search actions"
            aria-label="Search actions"
            className="flex-1"
            data-testid="command-palette-input"
          />
          <span className="text-xs text-muted-foreground ml-2">ESC to close</span>
        </div>

        {/* Action List */}
        <div
          ref={listRef}
          className="max-h-[400px] overflow-y-auto"
          data-testid="command-palette-list"
        >
          {loading ? (
            <div className="flex items-center justify-center py-8">
              <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-primary-600" />
            </div>
          ) : filteredGroups.length === 0 ? (
            <div className="py-8 text-center text-muted-foreground">No actions found</div>
          ) : (
            filteredGroups.map((group, groupIndex) => (
              <div key={group.title} className="border-b border-border last:border-b-0">
                <div className="px-4 py-2 bg-muted/20 text-xs font-medium text-muted-foreground uppercase">
                  {group.title}
                </div>
                {group.actions.map((action, actionIndex) => {
                  const globalIndex = filteredGroups
                    .slice(0, groupIndex)
                    .reduce((sum, g) => sum + g.actions.length, 0) + actionIndex;
                  const isSelected = globalIndex === selectedIndex;

                  return (
                    <Button
                      key={action.id}
                      data-testid={`action-${action.id}`}
                      onClick={() => handleActionClick(action)}
                      disabled={action.disabled}
                      variant={isSelected ? 'outline' : 'ghost'}
                      className={`w-full justify-start rounded-none px-4 py-3 ${
                        action.disabled ? 'opacity-50' : ''
                      }`}
                    >
                      <div className="flex-shrink-0 mt-0.5">
                        {action.icon || getCategoryIcon(action.category)}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="font-medium text-foreground">{action.title}</div>
                        {action.description && (
                          <div className="text-sm text-muted-foreground truncate">
                            {action.description}
                          </div>
                        )}
                        {action.disabled && action.disabledReason && (
                          <div className="text-xs text-red-500 mt-1">
                            {action.disabledReason}
                          </div>
                        )}
                      </div>
                      {action.shortcut && (
                        <div className="flex-shrink-0 text-xs text-muted-foreground bg-muted px-2 py-1 rounded">
                          {action.shortcut}
                        </div>
                      )}
                    </Button>
                  );
                })}
              </div>
            ))
          )}
        </div>

        {/* Footer */}
        <div className="px-4 py-2 bg-muted/20 border-t border-border flex items-center justify-between text-xs text-muted-foreground">
          <div className="flex items-center gap-4">
            <Typography className="text-xs text-muted-foreground">↑↓ to navigate</Typography>
            <Typography className="text-xs text-muted-foreground">Enter to select</Typography>
            <Typography className="text-xs text-muted-foreground">ESC to close</Typography>
          </div>
          <Typography className="text-xs text-muted-foreground">{allActions.length} actions</Typography>
        </div>

        {/* Confirmation Dialog for Dangerous Actions */}
        {showConfirmation && pendingAction && (
          <div
            data-testid="dangerous-action-confirmation"
            className="absolute inset-0 bg-white flex flex-col items-center justify-center p-6"
          >
            <Alert
              severity="warning"
              variant="standard"
              title="Confirm action"
              icon={<AlertTriangle className="w-5 h-5" />}
              action={
                <div className="flex gap-3">
                  <Button
                    onClick={handleCancelConfirmation}
                    variant="ghost"
                    data-testid="cancel-dangerous-action"
                  >
                    Cancel
                  </Button>
                  <Button
                    onClick={handleConfirmDangerousAction}
                    data-testid="confirm-dangerous-action"
                  >
                    Confirm
                  </Button>
                </div>
              }
            >
              {`This action (${pendingAction.title}) cannot be undone. Are you sure you want to proceed?`}
            </Alert>
          </div>
        )}
        </Box>
      </Fade>
    </Modal>
  );
};

export default ActionDiscoveryPalette;
