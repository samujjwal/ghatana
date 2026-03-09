/**
 * HelpTrigger component - Floating help button
 * @module components/ContextualHelp/HelpTrigger
 */

import clsx from 'clsx';
import React, { useState } from 'react';

import { HelpPanel } from './HelpPanel';

import type { HelpTriggerProps } from './types';

/**
 * HelpTrigger component provides a floating help button
 *
 * Shows a help icon button that opens the full help panel when clicked.
 * Useful for adding help access to app headers or floating toolbars.
 *
 * @param className - Additional CSS classes
 * @param context - Optional context for pre-filtered help topics
 *
 * @example
 * ```typescript
 * <HelpTrigger context="canvas" />
 * ```
 */
export const HelpTrigger: React.FC<HelpTriggerProps> = ({ className, context }) => {
    const [showPanel, setShowPanel] = useState(false);

    return (
        <>
            <button
                onClick={() => setShowPanel(true)}
                className={clsx(
                    'inline-flex items-center justify-center w-10 h-10',
                    'text-gray-500 hover:text-gray-700',
                    'hover:bg-gray-100 rounded-full transition-colors',
                    className
                )}
                title="Get help"
                data-testid="help-trigger"
            >
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                    />
                </svg>
            </button>

            <HelpPanel
                isOpen={showPanel}
                onClose={() => setShowPanel(false)}
                context={context}
            />
        </>
    );
};

export default HelpTrigger;
