import { Box } from '@ghatana/ui';
import { useGlobalStateValue } from '@ghatana/yappc-ui';
import React from 'react';

/**
 * Native matchMedia hook replacing MUI's useTheme + useMediaQuery
 */
const useIsMobile = (): boolean => {
  const [matches, setMatches] = React.useState(() => {
    if (typeof window === 'undefined') return false;
    return window.matchMedia('(max-width: 639px)').matches;
  });

  React.useEffect(() => {
    if (typeof window === 'undefined') return;
    const mql = window.matchMedia('(max-width: 639px)');
    const handler = (e: MediaQueryListEvent) => setMatches(e.matches);
    mql.addEventListener('change', handler);
    setMatches(mql.matches);
    return () => mql.removeEventListener('change', handler);
  }, []);

  return matches;
};

/**
 *
 */
interface ResponsiveLayoutProps {
  children: React.ReactNode;
  sidebarContent?: React.ReactNode;
  headerContent?: React.ReactNode;
  footerContent?: React.ReactNode;
}

/**
 * A responsive layout component that adapts to different platforms
 * - Desktop: Full sidebar + header
 * - Mobile: Bottom navigation + collapsible sidebar
 * - Web: Responsive layout based on screen size
 */
export function ResponsiveLayout({
  children,
  sidebarContent,
  headerContent,
  footerContent,
}: ResponsiveLayoutProps) {
  const platform = useGlobalStateValue<'web' | 'desktop' | 'mobile'>('store:platform');
  const isMobileSize = useIsMobile();
  
  // Determine layout based on platform and screen size
  const showBottomNav = platform === 'mobile' || isMobileSize;
  const showSidebar = (platform === 'desktop' || platform === 'web') && !isMobileSize;
  
  return (
    <Box className="flex flex-col h-screen">
      {/* Header */}
      {headerContent && (
        <Box
          component="header"
          className="w-full z-50"
        >
          {headerContent}
        </Box>
      )}
      
      {/* Main content area with optional sidebar */}
      <Box className="flex flex-1 overflow-hidden">
        {/* Sidebar (desktop/web) */}
        {showSidebar && sidebarContent && (
          <Box
            component="aside"
            className="w-60 shrink-0 hidden sm:block border-r border-gray-200 dark:border-gray-700 overflow-auto"
          >
            {sidebarContent}
          </Box>
        )}
        
        {/* Main content */}
        <Box
          component="main"
          className={`flex-1 p-4 overflow-auto ${showBottomNav ? 'pb-16' : 'pb-4'}`}
        >
          {children}
        </Box>
      </Box>
      
      {/* Footer/Bottom Navigation (mobile) */}
      {showBottomNav && footerContent && (
        <Box
          component="footer"
          className="fixed bottom-0 left-0 right-0 z-50 bg-white dark:bg-gray-900 border-t border-gray-200 dark:border-gray-700"
        >
          {footerContent}
        </Box>
      )}
    </Box>
  );
}
