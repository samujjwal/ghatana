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

import React, { useState, useCallback, useEffect, useRef } from 'react';
import { Search, Clock, Navigation, Zap, AlertTriangle, ArrowRight } from 'lucide-react';

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
  const filteredGroups = React.useMemo(() => {
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
  const allActions = React.useMemo(
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
        case 'Enter':
          e.preventDefault();
          if (allActions[selectedIndex]) {
            const action = allActions[selectedIndex];
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

  const handleActionClick = (action: Action) => {
    if (action.disabled) return;
    if (action.dangerous) {
      setPendingAction(action);
      setShowConfirmation(true);
    } else {
      action.onExecute();
      onClose();
    }
  };

  const handleConfirmDangerousAction = () => {
    if (pendingAction) {
      pendingAction.onExecute();
      setShowConfirmation(false);
      setPendingAction(null);
      onClose();
    }
  };

  const handleCancelConfirmation = () => {
    setShowConfirmation(false);
    setPendingAction(null);
  };

  const getCategoryIcon = (category: Action['category']) => {
    switch (category) {
      case 'next-best':
        return <Zap className="w-4 h-4 text-yellow-500" />;
      case 'recent':
        return <Clock className="w-4 h-4 text-blue-500" />;
      case 'selection':
        return <ArrowRight className="w-4 h-4 text-green-500" />;
      case 'navigation':
        return <Navigation className="w-4 h-4 text-purple-500" />;
      case 'advanced':
        return <Search className="w-4 h-4 text-gray-500" />;
    }
  };

  if (!open) return null;

  return (
    <div
      data-testid="command-palette"
      className="fixed inset-0 z-50 flex items-start justify-center pt-[20vh] bg-black/50"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label="Command palette"
    >
      <div
        className="bg-white rounded-lg shadow-2xl w-full max-w-2xl mx-4 overflow-hidden"
        onClick={(e) => e.stopPropagation()}
        onKeyDown={handleKeyDown}
      >
        {/* Search Input */}
        <div className="flex items-center border-b border-gray-200 px-4 py-3">
          <Search className="w-5 h-5 text-gray-400 mr-3" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search actions..."
            className="flex-1 outline-none text-gray-900 placeholder-gray-400"
            data-testid="command-palette-input"
          />
          <span className="text-xs text-gray-400 ml-2">ESC to close</span>
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
            <div className="py-8 text-center text-gray-500">No actions found</div>
          ) : (
            filteredGroups.map((group, groupIndex) => (
              <div key={group.title} className="border-b border-gray-100 last:border-b-0">
                <div className="px-4 py-2 bg-gray-50 text-xs font-medium text-gray-500 uppercase">
                  {group.title}
                </div>
                {group.actions.map((action, actionIndex) => {
                  const globalIndex = filteredGroups
                    .slice(0, groupIndex)
                    .reduce((sum, g) => sum + g.actions.length, 0) + actionIndex;
                  const isSelected = globalIndex === selectedIndex;

                  return (
                    <button
                      key={action.id}
                      data-testid={`action-${action.id}`}
                      onClick={() => handleActionClick(action)}
                      disabled={action.disabled}
                      className={`w-full flex items-start gap-3 px-4 py-3 text-left hover:bg-gray-50 transition-colors ${
                        isSelected ? 'bg-blue-50' : ''
                      } ${action.disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
                    >
                      <div className="flex-shrink-0 mt-0.5">
                        {action.icon || getCategoryIcon(action.category)}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="font-medium text-gray-900">{action.title}</div>
                        {action.description && (
                          <div className="text-sm text-gray-500 truncate">
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
                        <div className="flex-shrink-0 text-xs text-gray-400 bg-gray-100 px-2 py-1 rounded">
                          {action.shortcut}
                        </div>
                      )}
                    </button>
                  );
                })}
              </div>
            ))
          )}
        </div>

        {/* Footer */}
        <div className="px-4 py-2 bg-gray-50 border-t border-gray-200 flex items-center justify-between text-xs text-gray-500">
          <div className="flex items-center gap-4">
            <span>↑↓ to navigate</span>
            <span>Enter to select</span>
            <span>ESC to close</span>
          </div>
          <div>{allActions.length} actions</div>
        </div>

        {/* Confirmation Dialog for Dangerous Actions */}
        {showConfirmation && pendingAction && (
          <div
            data-testid="dangerous-action-confirmation"
            className="absolute inset-0 bg-white flex flex-col items-center justify-center p-6"
          >
            <AlertTriangle className="w-12 h-12 text-red-500 mb-4" />
            <h3 className="text-lg font-semibold text-gray-900 mb-2">
              Confirm Action
            </h3>
            <p className="text-gray-600 text-center mb-6">
              This action ({pendingAction.title}) cannot be undone. Are you sure you want to proceed?
            </p>
            <div className="flex gap-3">
              <button
                onClick={handleCancelConfirmation}
                className="px-4 py-2 bg-gray-200 text-gray-900 rounded-lg hover:bg-gray-300 transition-colors"
                data-testid="cancel-dangerous-action"
              >
                Cancel
              </button>
              <button
                onClick={handleConfirmDangerousAction}
                className="px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors"
                data-testid="confirm-dangerous-action"
              >
                Confirm
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default ActionDiscoveryPalette;
