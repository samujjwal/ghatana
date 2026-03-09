import React, { ReactNode, useState, useEffect } from 'react';

/**
 * Generic Dashboard Layout Component
 * 
 * Provides a flexible, responsive dashboard layout with header, sidebar, and main content area.
 * Supports collapsible sidebar, responsive breakpoints, and custom header/sidebar components.
 * 
 * @example
 * ```tsx
 * <DashboardLayout
 *   header={<AppHeader onMenuClick={toggleSidebar} />}
 *   sidebar={<AppSidebar isOpen={sidebarOpen} onNavigate={handleNav} />}
 *   sidebarCollapsed={!sidebarOpen}
 *   onSidebarToggle={setSidebarOpen}
 * >
 *   <YourPageContent />
 * </DashboardLayout>
 * ```
 */

export interface DashboardLayoutProps {
  /** Main content to render in the dashboard */
  children: ReactNode;

  /** Header component (navigation, user menu, etc.) */
  header?: ReactNode;

  /** Sidebar component (navigation menu) */
  sidebar?: ReactNode;

  /** Whether sidebar is collapsed */
  sidebarCollapsed?: boolean;

  /** Callback when sidebar toggle is requested */
  onSidebarToggle?: (collapsed: boolean) => void;

  /** Maximum width for main content area */
  maxWidth?: 'sm' | 'md' | 'lg' | 'xl' | '2xl' | '3xl' | '4xl' | '5xl' | '6xl' | '7xl' | 'full';

  /** Background color for main content area */
  backgroundColor?: string;

  /** Padding for main content area */
  padding?: 'none' | 'sm' | 'md' | 'lg' | 'xl';

  /** Whether to enable responsive sidebar (auto-collapse on mobile) */
  responsive?: boolean;

  /** Breakpoint for responsive behavior (px) */
  responsiveBreakpoint?: number;

  /** Additional CSS classes for the layout container */
  className?: string;

  /** Additional CSS classes for the main content area */
  contentClassName?: string;
}

const MAX_WIDTH_MAP = {
  sm: 'max-w-screen-sm',
  md: 'max-w-screen-md',
  lg: 'max-w-screen-lg',
  xl: 'max-w-screen-xl',
  '2xl': 'max-w-screen-2xl',
  '3xl': 'max-w-[1920px]',
  '4xl': 'max-w-[2560px]',
  '5xl': 'max-w-[3200px]',
  '6xl': 'max-w-[3840px]',
  '7xl': 'max-w-[4096px]',
  full: 'max-w-full',
};

const PADDING_MAP = {
  none: 'p-0',
  sm: 'p-2 sm:p-3',
  md: 'p-4 sm:p-6',
  lg: 'p-6 sm:p-8',
  xl: 'p-8 sm:p-10',
};

/**
 * DashboardLayout - Generic dashboard layout with header, sidebar, and content area
 * 
 * Features:
 * - Responsive sidebar (auto-collapse on mobile)
 * - Configurable max width and padding
 * - Flexible header and sidebar slots
 * - Smooth transitions
 * - Accessible navigation structure
 */
export const DashboardLayout: React.FC<DashboardLayoutProps> = ({
  children,
  header,
  sidebar,
  sidebarCollapsed: controlledCollapsed,
  onSidebarToggle,
  maxWidth = 'full',
  backgroundColor = 'bg-gray-50',
  padding = 'md',
  responsive = true,
  responsiveBreakpoint = 1024,
  className = '',
  contentClassName = '',
}) => {
  // Internal sidebar state (used when not controlled)
  const [internalCollapsed, setInternalCollapsed] = useState(() => {
    if (!responsive || typeof window === 'undefined') return false;
    return window.innerWidth < responsiveBreakpoint;
  });

  const isCollapsed = controlledCollapsed !== undefined ? controlledCollapsed : internalCollapsed;

  // Debug instrumentation: log render/mount lifecycle for diagnosis
  try {
    console.log('[DashboardLayout] render start - sidebar present=', Boolean(sidebar), 'isCollapsed=', isCollapsed);
  } catch (e) {
    // ignore
  }

  // Handle responsive behavior
  useEffect(() => {
    if (!responsive) return;
    console.log('[DashboardLayout] attaching resize listener, responsiveBreakpoint=', responsiveBreakpoint);

    const handleResize = () => {
      const shouldCollapse = window.innerWidth < responsiveBreakpoint;

      if (onSidebarToggle) {
        onSidebarToggle(!shouldCollapse);
      } else {
        setInternalCollapsed(shouldCollapse);
      }
    };
    console.log('[DashboardLayout] resize handler installed');

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [responsive, responsiveBreakpoint, onSidebarToggle]);

  React.useEffect(() => {
    console.log('[DashboardLayout] mounted');
    return () => {
      console.log('[DashboardLayout] unmounted');
    };
  }, []);

  const maxWidthClass = MAX_WIDTH_MAP[maxWidth];
  const paddingClass = PADDING_MAP[padding];

  return (
    <div className={`flex h-screen overflow-hidden ${backgroundColor} ${className}`}>
      {/* Sidebar */}
      {sidebar && (
        <aside
          className={`
            flex-shrink-0 transition-all duration-300 ease-in-out
            ${isCollapsed ? 'w-0 md:w-16' : 'w-64'}
          `}
          aria-label="Sidebar navigation"
        >
          {sidebar}
        </aside>
      )}

      {/* Main Content Area */}
      <div className="flex flex-1 flex-col overflow-hidden">
        {/* Header */}
        {header && (
          <header className="flex-shrink-0 bg-white shadow-sm" aria-label="Main navigation">
            {header}
          </header>
        )}

        {/* Main Content */}
        <main
          className={`
            flex-1 overflow-auto ${backgroundColor} ${paddingClass}
            ${contentClassName}
          `}
          role="main"
        >
          <div className={`mx-auto w-full ${maxWidthClass}`}>
            {children}
          </div>
        </main>
      </div>
    </div>
  );
};

export default DashboardLayout;
