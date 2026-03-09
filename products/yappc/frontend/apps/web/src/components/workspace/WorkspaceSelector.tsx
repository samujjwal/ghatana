/**
 * Workspace Selector Component
 * 
 * Dead simple dropdown to switch between workspaces.
 * Shows AI-generated workspace summaries on hover.
 * 
 * @doc.type component
 * @doc.purpose Workspace switching with AI hints
 * @doc.layer product
 * @doc.pattern Presentational Component
 */
import { useState, useRef, useEffect } from 'react';
import { useAtom, useSetAtom } from 'jotai';

import {
    workspaceAtom,
    setCurrentWorkspaceAtom,
    type Workspace,
} from '../../state/atoms/workspaceAtom';

interface WorkspaceSelectorProps {
    onCreateNew?: () => void;
    className?: string;
}

export function WorkspaceSelector({ onCreateNew, className = '' }: WorkspaceSelectorProps) {
    const [state] = useAtom(workspaceAtom);
    const setCurrentWorkspace = useSetAtom(setCurrentWorkspaceAtom);

    const [isOpen, setIsOpen] = useState(false);
    const [hoveredId, setHoveredId] = useState<string | null>(null);
    const dropdownRef = useRef<HTMLDivElement>(null);

    // Close on outside click
    useEffect(() => {
        function handleClickOutside(event: MouseEvent) {
            if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
                setIsOpen(false);
            }
        }
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    // Keyboard navigation
    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Escape') {
            setIsOpen(false);
        } else if (e.key === 'Enter' || e.key === ' ') {
            setIsOpen(!isOpen);
        }
    };

    const handleSelect = (workspace: Workspace) => {
        setCurrentWorkspace(workspace);
        setIsOpen(false);
    };

    const currentWorkspace = state.currentWorkspace;
    const currentWorkspaceInitial =
        currentWorkspace?.name?.charAt(0).toUpperCase() || 'W';

    return (
        <div ref={dropdownRef} className={`relative ${className}`}>
            {/* Trigger Button */}
            <button
                type="button"
                onClick={() => setIsOpen(!isOpen)}
                onKeyDown={handleKeyDown}
                aria-expanded={isOpen}
                aria-haspopup="listbox"
                data-testid="workspace-selector"
                className="
          flex items-center gap-2 px-3 py-2 
          bg-white dark:bg-grey-800 
          border border-grey-200 dark:border-grey-700 
          rounded-lg shadow-sm
          hover:bg-grey-50 dark:hover:bg-grey-750
          focus:outline-none focus:ring-2 focus:ring-primary-500/20
          transition-colors duration-150
          min-w-[200px]
        "
            >
                {/* Workspace Icon */}
                <span className="flex-shrink-0 w-6 h-6 rounded-md bg-primary-100 dark:bg-primary-900/30 
          flex items-center justify-center text-primary-600 dark:text-primary-400 text-sm">
                    {currentWorkspaceInitial}
                </span>

                {/* Workspace Name */}
                <span className="flex-1 text-left text-sm font-medium text-grey-900 dark:text-grey-100 truncate">
                    {currentWorkspace?.name || 'Select Workspace'}
                </span>

                {/* Chevron */}
                <svg
                    className={`w-4 h-4 text-grey-400 transition-transform duration-200 ${isOpen ? 'rotate-180' : ''}`}
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
            </button>

            {/* Dropdown Menu */}
            {isOpen && (
                <div
                    className="
            absolute top-full left-0 mt-1 w-72 z-50
            bg-white dark:bg-grey-800 
            border border-grey-200 dark:border-grey-700 
            rounded-lg shadow-lg
            py-1
            animate-in fade-in slide-in-from-top-2 duration-150
          "
                    role="listbox"
                >
                    {/* Workspace List */}
                    <div className="max-h-64 overflow-y-auto">
                        {state.availableWorkspaces.map((workspace) => (
                            <div
                                key={workspace.id}
                                role="option"
                                aria-selected={workspace.id === currentWorkspace?.id}
                                onClick={() => handleSelect(workspace)}
                                onMouseEnter={() => setHoveredId(workspace.id)}
                                onMouseLeave={() => setHoveredId(null)}
                                data-testid={`workspace-option-${workspace.id}`}
                                className={`
                  flex items-start gap-3 px-3 py-2.5 cursor-pointer
                  transition-colors duration-100
                  ${workspace.id === currentWorkspace?.id
                                        ? 'bg-primary-50 dark:bg-primary-900/20'
                                        : 'hover:bg-grey-50 dark:hover:bg-grey-750'
                                    }
                `}
                            >
                                {/* Icon */}
                                <span className={`
                  flex-shrink-0 w-8 h-8 rounded-md flex items-center justify-center text-sm
                  ${workspace.id === currentWorkspace?.id
                                        ? 'bg-primary-100 dark:bg-primary-900/40 text-primary-600 dark:text-primary-400'
                                        : 'bg-grey-100 dark:bg-grey-700 text-grey-600 dark:text-grey-400'
                                    }
                `}>
                                    {workspace.name.charAt(0).toUpperCase()}
                                </span>

                                {/* Content */}
                                <div className="flex-1 min-w-0">
                                    <div className="flex items-center gap-2">
                                        <span className="text-sm font-medium text-grey-900 dark:text-grey-100 truncate">
                                            {workspace.name}
                                        </span>
                                        {workspace.isDefault && (
                                            <span className="px-1.5 py-0.5 text-xs bg-primary-100 dark:bg-primary-900/30 
                        text-primary-700 dark:text-primary-300 rounded-full">
                                                default
                                            </span>
                                        )}
                                    </div>

                                    {/* AI Summary on hover */}
                                    {hoveredId === workspace.id && workspace.aiSummary && (
                                        <p className="mt-1 text-xs text-grey-500 dark:text-grey-400 line-clamp-2">
                                            {workspace.aiSummary}
                                        </p>
                                    )}

                                    {/* AI Tags */}
                                    {workspace.aiTags.length > 0 && (
                                        <div className="flex gap-1 mt-1 flex-wrap">
                                            {workspace.aiTags.slice(0, 3).map(tag => (
                                                <span
                                                    key={tag}
                                                    className="px-1.5 py-0.5 text-[10px] bg-grey-100 dark:bg-grey-700 
                            text-grey-600 dark:text-grey-400 rounded"
                                                >
                                                    {tag}
                                                </span>
                                            ))}
                                        </div>
                                    )}
                                </div>

                                {/* Selected Check */}
                                {workspace.id === currentWorkspace?.id && (
                                    <svg className="w-4 h-4 text-primary-600 dark:text-primary-400 flex-shrink-0"
                                        fill="currentColor" viewBox="0 0 20 20">
                                        <path fillRule="evenodd"
                                            d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                                            clipRule="evenodd"
                                        />
                                    </svg>
                                )}
                            </div>
                        ))}
                    </div>

                    {/* Divider */}
                    <div className="border-t border-grey-200 dark:border-grey-700 my-1" />

                    {/* Create New Button */}
                    {onCreateNew && (
                        <button
                            type="button"
                            onClick={() => {
                                setIsOpen(false);
                                onCreateNew();
                            }}
                            data-testid="create-workspace-btn"
                            className="
                w-full flex items-center gap-2 px-3 py-2.5 
                text-sm font-medium text-primary-600 dark:text-primary-400
                hover:bg-primary-50 dark:hover:bg-primary-900/20
                transition-colors duration-100
              "
                        >
                            <span className="w-8 h-8 rounded-md bg-primary-100 dark:bg-primary-900/30 
                flex items-center justify-center text-lg">
                                +
                            </span>
                            <span>Create Workspace</span>
                        </button>
                    )}
                </div>
            )}
        </div>
    );
}
