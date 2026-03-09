/**
 * Header With Breadcrumb
 * 
 * Dead simple header with breadcrumb navigation, workspace selector, and user actions.
 * AI-enhanced with contextual tooltips and smart suggestions.
 * 
 * @doc.type component
 * @doc.purpose Application header with contextual navigation
 * @doc.layer product
 * @doc.pattern Layout Component
 */
import { useAtom } from 'jotai';

import { Breadcrumb } from '@ghatana/yappc-ui';
import { breadcrumbItemsAtom, navigationContextAtom } from '../../state/atoms/breadcrumbAtom';
import { WorkspaceSelector } from './WorkspaceSelector';

interface HeaderWithBreadcrumbProps {
    onCreateWorkspace?: () => void;
    onSettings?: () => void;
    onThemeToggle?: () => void;
    className?: string;
}

export function HeaderWithBreadcrumb({
    onCreateWorkspace,
    onSettings,
    onThemeToggle,
    className = '',
}: HeaderWithBreadcrumbProps) {
    const [breadcrumbItems] = useAtom(breadcrumbItemsAtom);
    const [navContext] = useAtom(navigationContextAtom);

    return (
        <header
            role="banner"
            className={`
        flex items-center justify-between
        px-4 py-3
        bg-white dark:bg-grey-900
        border-b border-grey-200 dark:border-grey-800
        ${className}
      `}
        >
            {/* Left: Breadcrumb Navigation */}
            <div className="flex items-center gap-4 min-w-0">
                <Breadcrumb
                    items={breadcrumbItems}
                    showHomeIcon
                    size="medium"
                    maxItems={4}
                />

                {/* Ownership indicator when in project context */}
                {navContext.projectId && (
                    <span className={`
            px-2 py-0.5 text-xs font-medium rounded-full whitespace-nowrap
            ${navContext.projectIsOwned
                            ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400'
                            : 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-400'
                        }
          `}>
                        {navContext.projectIsOwned ? 'Owner' : 'Read-only'}
                    </span>
                )}
            </div>

            {/* Right: Actions */}
            <div className="flex items-center gap-3">
                {/* Workspace Selector */}
                <WorkspaceSelector onCreateNew={onCreateWorkspace} />

                {/* Theme Toggle */}
                {onThemeToggle && (
                    <button
                        type="button"
                        onClick={onThemeToggle}
                        data-testid="theme-toggle"
                        className="
              p-2 rounded-lg
              text-grey-500 dark:text-grey-400
              hover:bg-grey-100 dark:hover:bg-grey-800
              transition-colors
            "
                        aria-label="Toggle theme"
                    >
                        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                                d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z"
                            />
                        </svg>
                    </button>
                )}

                {/* Settings */}
                {onSettings && (
                    <button
                        type="button"
                        onClick={onSettings}
                        data-testid="settings-button"
                        className="
              p-2 rounded-lg
              text-grey-500 dark:text-grey-400
              hover:bg-grey-100 dark:hover:bg-grey-800
              transition-colors
            "
                        aria-label="Settings"
                    >
                        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                                d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
                            />
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                                d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
                            />
                        </svg>
                    </button>
                )}

                {/* User Avatar */}
                <button
                    type="button"
                    data-testid="user-menu"
                    className="
            w-8 h-8 rounded-full
            bg-primary-500 dark:bg-primary-600
            flex items-center justify-center
            text-white text-sm font-semibold
            hover:ring-2 hover:ring-primary-300 dark:hover:ring-primary-700
            transition-all
          "
                >
                    U
                </button>
            </div>
        </header>
    );
}
