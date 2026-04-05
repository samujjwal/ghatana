/**
 * Skip Link Component
 * 
 * Provides a skip-to-main-content link for keyboard navigation.
 * Appears when focused, allowing users to skip repetitive navigation.
 * 
 * @doc.type component
 * @doc.purpose Enable keyboard users to skip to main content
 * @doc.layer product
 * @doc.pattern Accessibility Component
 */

import React from 'react';

interface SkipLinkProps {
  /** Target element ID to skip to */
  targetId: string;
  /** Link text (default: "Skip to main content") */
  children?: React.ReactNode;
}

/**
 * Skip Link for keyboard accessibility
 * 
 * Place at the very beginning of the page/layout.
 * Add id={targetId} to the main content area.
 * 
 * @example
 * ```tsx
 * <SkipLink targetId="main-content" />
 * <nav>...</nav>
 * <main id="main-content">...</main>
 * ```
 */
export const SkipLink: React.FC<SkipLinkProps> = ({
  targetId,
  children = 'Skip to main content',
}) => {
  const handleClick = (event: React.MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    const target = document.getElementById(targetId);
    if (target) {
      target.focus();
      target.scrollIntoView({ behavior: 'smooth' });
    }
  };

  return (
    <a
      href={`#${targetId}`}
      onClick={handleClick}
      className="
        absolute left-0 top-0 z-[9999]
        -translate-y-full focus:translate-y-0
        bg-primary-600 text-white px-4 py-2
        font-medium text-sm
        focus:outline-none focus:ring-2 focus:ring-primary-400 focus:ring-offset-2
        transition-transform duration-150 ease-in-out
      "
    >
      {children}
    </a>
  );
};

export default SkipLink;
