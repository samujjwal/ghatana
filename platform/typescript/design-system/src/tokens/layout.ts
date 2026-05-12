/**
 * Layout Tokens
 *
 * Spacing, sizes, and layout-related tokens for consistent product UI.
 *
 * @doc.type tokens
 * @doc.purpose Unified layout system
 * @doc.layer core
 */

export const spacing = {
  // Sidebar widths
  sidebar: {
    expanded: '16rem', // lg:ml-64 (64 * 0.25rem = 16rem)
    collapsed: '4rem', // lg:ml-16 (16 * 0.25rem = 4rem)
  },
  // Header height
  header: {
    height: '4rem', // h-16 (16 * 0.25rem = 4rem)
  },
  // Content padding
  content: {
    padding: '1.5rem', // p-6 (6 * 0.25rem = 1.5rem)
    paddingTop: '4rem', // pt-16 (16 * 0.25rem = 4rem)
  },
} as const;

export const transitions = {
  duration: {
    default: '300ms',
  },
  timing: {
    default: 'ease-in-out',
  },
} as const;

export const layoutTokens = {
  spacing,
  transitions,
} as const;

// Tailwind class utilities
export const twLayout = {
  // Sidebar
  sidebarExpanded: 'lg:ml-64',
  sidebarCollapsed: 'lg:ml-16',
  transition: 'transition-all duration-300',
  
  // Header
  headerHeight: 'h-16',
  
  // Content
  contentPadding: 'pt-16 p-6',
  
  // Full height
  fullHeight: 'min-h-screen',
  
  // Backgrounds
  bgLight: 'bg-gray-50',
  bgDark: 'dark:bg-gray-950',
} as const;
