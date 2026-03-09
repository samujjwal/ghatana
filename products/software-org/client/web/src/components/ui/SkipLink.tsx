/**
 * SkipLink Component
 *
 * Provides accessible skip navigation for keyboard and screen reader users.
 * Allows users to bypass repetitive navigation and jump directly to main content.
 *
 * WCAG 2.1 Level A - Bypass Blocks (2.4.1)
 *
 * @doc.type component
 * @doc.purpose Accessibility - Skip Navigation
 * @doc.layer infrastructure
 * @doc.pattern Accessibility Component
 *
 * @example
 * ```tsx
 * // In your root layout or App component:
 * <SkipLink />
 * <header>...</header>
 * <nav>...</nav>
 * <main id="main-content">
 *   <!-- Main content here -->
 * </main>
 * ```
 */

import React from 'react';

export interface SkipLinkProps {
    /** Target element ID to skip to (without #) */
    targetId?: string;
    /** Label text for the skip link */
    label?: string;
    /** Additional CSS classes */
    className?: string;
}

/**
 * SkipLink Component
 *
 * Visually hidden until focused, then slides into view at the top of the page.
 * When clicked or Enter is pressed, focuses the main content area.
 */
export function SkipLink({
    targetId = 'main-content',
    label = 'Skip to main content',
    className = '',
}: SkipLinkProps) {
    const handleClick = (e: React.MouseEvent<HTMLAnchorElement>) => {
        e.preventDefault();
        const target = document.getElementById(targetId);
        if (target) {
            // Set tabindex to make element focusable if it isn't already
            if (!target.hasAttribute('tabindex')) {
                target.setAttribute('tabindex', '-1');
            }
            target.focus();
            // Scroll to the element
            target.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    };

    return (
        <a
            href={`#${targetId}`}
            onClick={handleClick}
            className={`
                sr-only focus:not-sr-only
                focus:fixed focus:top-4 focus:left-4 focus:z-[9999]
                focus:px-4 focus:py-2 focus:min-h-[44px]
                focus:bg-primary-600 focus:text-white
                focus:font-medium focus:rounded-md
                focus:shadow-lg focus:outline-none
                focus:ring-2 focus:ring-primary-400 focus:ring-offset-2
                transition-all duration-200
                ${className}
            `}
        >
            {label}
        </a>
    );
}

/**
 * SkipLinks Component
 *
 * Multiple skip links for complex pages with multiple content areas.
 *
 * @example
 * ```tsx
 * <SkipLinks links={[
 *   { targetId: 'main-content', label: 'Skip to main content' },
 *   { targetId: 'search', label: 'Skip to search' },
 *   { targetId: 'footer', label: 'Skip to footer' },
 * ]} />
 * ```
 */
export interface SkipLinksProps {
    links: Array<{
        targetId: string;
        label: string;
    }>;
    className?: string;
}

export function SkipLinks({ links, className = '' }: SkipLinksProps) {
    return (
        <nav
            aria-label="Skip links"
            className={`skip-links ${className}`}
        >
            {links.map((link, index) => (
                <SkipLink
                    key={link.targetId}
                    targetId={link.targetId}
                    label={link.label}
                    className={index > 0 ? 'focus:left-auto focus:ml-2' : ''}
                />
            ))}
        </nav>
    );
}

export default SkipLink;
