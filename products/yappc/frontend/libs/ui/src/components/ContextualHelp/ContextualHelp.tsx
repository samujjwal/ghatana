/**
 * ContextualHelp component - Main wrapper component
 * @module components/ContextualHelp/ContextualHelp
 */

import clsx from 'clsx';
import React, { useEffect, useRef, useState } from 'react';

import { helpContentManager } from './manager-singleton';

import type { ContextualHelpProps, HelpContent } from './types';

/**
 * Calculates tooltip positioning styles
 *
 * @param position - Tooltip position
 * @returns CSS style object for positioning
 */
function getTooltipStyle(position: string): React.CSSProperties {
    return {
        top: position === 'bottom' ? '100%' : position === 'top' ? 'auto' : '50%',
        bottom: position === 'top' ? '100%' : 'auto',
        left: position === 'right' ? '100%' : position === 'left' ? 'auto' : '50%',
        right: position === 'left' ? '100%' : 'auto',
        transform: position === 'auto' ? 'translate(-50%, -50%)' : undefined,
    };
}

/**
 * ContextualHelp component provides context-aware help tooltips
 *
 * Displays help content relevant to the current context.
 * Triggers on hover, click, or focus based on configuration.
 *
 * @param context - Context to match for relevant help topics
 * @param trigger - How to trigger the help tooltip (default: 'hover')
 * @param position - Position of tooltip relative to trigger (default: 'auto')
 * @param className - Additional CSS classes
 * @param children - Content to wrap with contextual help
 *
 * @example
 * ```typescript
 * <ContextualHelp context="canvas" trigger="hover">
 *   <button>Draw Shape</button>
 * </ContextualHelp>
 * ```
 */
export const ContextualHelp: React.FC<ContextualHelpProps> = ({
    context,
    trigger = 'hover',
    position = 'auto',
    className,
    children,
}) => {
    const [isVisible, setIsVisible] = useState(false);
    const [helpContent, setHelpContent] = useState<HelpContent[]>([]);
    const containerRef = useRef<HTMLDivElement>(null);
    const tooltipRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (context && isVisible) {
            const content = helpContentManager.getContextualHelp(context);
            setHelpContent(content);
        }
    }, [context, isVisible]);

    const handleTrigger = () => {
        if (trigger === 'click') {
            setIsVisible(!isVisible);
        } else {
            setIsVisible(true);
        }
    };

    const handleMouseLeave = () => {
        if (trigger === 'hover') {
            setIsVisible(false);
        }
    };

    const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            handleTrigger();
        } else if (event.key === 'Escape') {
            setIsVisible(false);
        }
    };

    if (!context || helpContent.length === 0) {
        return <>{children}</>;
    }

    const tooltipClasses = clsx(
        'absolute z-50 w-80 p-4',
        'bg-white border border-gray-200 rounded-lg shadow-lg'
    );

    return (
        <div
            ref={containerRef}
            className={clsx('relative inline-block', className)}
            role="button"
            tabIndex={trigger === 'click' ? 0 : -1}
            onMouseEnter={trigger === 'hover' ? handleTrigger : undefined}
            onMouseLeave={handleMouseLeave}
            onClick={trigger === 'click' ? handleTrigger : undefined}
            onKeyDown={trigger === 'click' ? handleKeyDown : undefined}
            onFocus={trigger === 'focus' ? handleTrigger : undefined}
        >
            {children}

            {isVisible && (
                <div
                    ref={tooltipRef}
                    className={tooltipClasses}
                    style={getTooltipStyle(position)}
                >
                    <div className="mb-2">
                        <h4 className="text-sm font-semibold text-gray-900">💡 Quick Help</h4>
                    </div>

                    <div className="space-y-3 max-h-64 overflow-y-auto">
                        {helpContent.slice(0, 3).map((content) => (
                            <div
                                key={content.id}
                                className="pb-2 border-b border-gray-100 last:border-b-0"
                            >
                                <h5 className="text-sm font-medium text-gray-800 mb-1">
                                    {content.title}
                                </h5>
                                <p className="text-xs text-gray-600 leading-relaxed">
                                    {content.content.length > 100
                                        ? `${content.content.substring(0, 100)}...`
                                        : content.content}
                                </p>
                            </div>
                        ))}
                    </div>

                    <div className="mt-3 pt-2 border-t border-gray-100">
                        <button
                            onClick={() => {
                                setIsVisible(false);
                            }}
                            className="text-xs text-blue-600 hover:text-blue-800 font-medium"
                        >
                            View all help topics →
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
};

export default ContextualHelp;
