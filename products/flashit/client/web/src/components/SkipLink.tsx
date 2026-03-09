/**
 * Skip Link Component
 * 
 * Provides a skip navigation link for keyboard and screen reader users
 * to bypass repetitive navigation and jump directly to main content.
 * 
 * WCAG 2.1 Level A requirement (2.4.1 Bypass Blocks)
 */

export default function SkipLink() {
  return (
    <a
      href="#main-content"
      className="sr-only focus:not-sr-only focus:absolute focus:top-4 focus:left-4 focus:z-50 focus:bg-primary-600 focus:text-white focus:px-4 focus:py-2 focus:rounded-md focus:shadow-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2"
    >
      Skip to main content
    </a>
  );
}
