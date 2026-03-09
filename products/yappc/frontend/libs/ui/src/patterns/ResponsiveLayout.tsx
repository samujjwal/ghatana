/**
 * Responsive Layout Pattern
 * 
 * Demonstrates responsive design patterns for YAPPC.
 * Mobile-first approach with progressive enhancement.
 * 
 * @module ui/patterns
 */

import React, { useState, useEffect } from 'react';

export interface ResponsiveLayoutProps {
  /** Children to render */
  children: React.ReactNode;
  
  /** Sidebar content */
  sidebar?: React.ReactNode;
  
  /** Header content */
  header?: React.ReactNode;
  
  /** Show sidebar on mobile */
  mobileSidebar?: boolean;
}

/**
 * Responsive Layout Component
 * 
 * Provides responsive layout with mobile, tablet, and desktop breakpoints.
 * 
 * Breakpoints:
 * - Mobile: < 640px
 * - Tablet: 640px - 1024px
 * - Desktop: > 1024px
 * 
 * @example
 * ```tsx
 * <ResponsiveLayout
 *   header={<Header />}
 *   sidebar={<Sidebar />}
 *   mobileSidebar={true}
 * >
 *   <MainContent />
 * </ResponsiveLayout>
 * ```
 */
export const ResponsiveLayout: React.FC<ResponsiveLayoutProps> = ({
  children,
  sidebar,
  header,
  mobileSidebar = false,
}) => {
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [screenSize, setScreenSize] = useState<'mobile' | 'tablet' | 'desktop'>('desktop');

  useEffect(() => {
    const handleResize = () => {
      const width = window.innerWidth;
      if (width < 640) {
        setScreenSize('mobile');
      } else if (width < 1024) {
        setScreenSize('tablet');
      } else {
        setScreenSize('desktop');
      }
    };

    handleResize();
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  return (
    <div className="min-h-screen bg-zinc-950">
      {/* Header */}
      {header && (
        <header className="sticky top-0 z-40 bg-zinc-900 border-b border-zinc-800">
          <div className="flex items-center justify-between px-4 py-3">
            {header}
            
            {/* Mobile menu button */}
            {sidebar && mobileSidebar && screenSize === 'mobile' && (
              <button
                onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
                className="p-2 text-zinc-400 hover:text-white"
                aria-label="Toggle menu"
              >
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
                </svg>
              </button>
            )}
          </div>
        </header>
      )}

      <div className="flex">
        {/* Sidebar */}
        {sidebar && (
          <>
            {/* Desktop sidebar */}
            <aside className={`
              ${screenSize === 'mobile' ? 'hidden' : 'block'}
              w-64 bg-zinc-900 border-r border-zinc-800 min-h-screen
            `}>
              {sidebar}
            </aside>

            {/* Mobile sidebar overlay */}
            {screenSize === 'mobile' && mobileSidebar && isMobileMenuOpen && (
              <>
                <div
                  className="fixed inset-0 bg-black/50 z-40"
                  onClick={() => setIsMobileMenuOpen(false)}
                />
                <aside className="fixed inset-y-0 left-0 w-64 bg-zinc-900 z-50 transform transition-transform">
                  {sidebar}
                </aside>
              </>
            )}
          </>
        )}

        {/* Main content */}
        <main className="flex-1 min-h-screen">
          <div className={`
            ${screenSize === 'mobile' ? 'p-4' : 'p-6'}
            max-w-7xl mx-auto
          `}>
            {children}
          </div>
        </main>
      </div>
    </div>
  );
};

/**
 * Responsive Grid Component
 * 
 * Auto-responsive grid that adapts to screen size.
 */
export interface ResponsiveGridProps {
  children: React.ReactNode;
  /** Columns on mobile */
  mobileCols?: 1 | 2;
  /** Columns on tablet */
  tabletCols?: 2 | 3 | 4;
  /** Columns on desktop */
  desktopCols?: 2 | 3 | 4 | 5 | 6;
  /** Gap between items */
  gap?: 2 | 4 | 6 | 8;
}

export const ResponsiveGrid: React.FC<ResponsiveGridProps> = ({
  children,
  mobileCols = 1,
  tabletCols = 2,
  desktopCols = 3,
  gap = 4,
}) => {
  const gridClasses = `
    grid
    grid-cols-${mobileCols}
    md:grid-cols-${tabletCols}
    lg:grid-cols-${desktopCols}
    gap-${gap}
  `;

  return <div className={gridClasses}>{children}</div>;
};

/**
 * Responsive Utilities
 */
export const useMediaQuery = (query: string): boolean => {
  const [matches, setMatches] = useState(false);

  useEffect(() => {
    const media = window.matchMedia(query);
    setMatches(media.matches);

    const listener = (e: MediaQueryListEvent) => setMatches(e.matches);
    media.addEventListener('change', listener);
    return () => media.removeEventListener('change', listener);
  }, [query]);

  return matches;
};

export const useIsMobile = () => useMediaQuery('(max-width: 639px)');
export const useIsTablet = () => useMediaQuery('(min-width: 640px) and (max-width: 1023px)');
export const useIsDesktop = () => useMediaQuery('(min-width: 1024px)');

export default ResponsiveLayout;
