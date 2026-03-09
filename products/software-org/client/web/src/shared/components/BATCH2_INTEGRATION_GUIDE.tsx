import React from 'react';
import { AppLayout } from './AppLayout';
import { CommandPalette } from './CommandPalette';
import { SearchBar } from './SearchBar';
import { NotificationCenter } from './NotificationCenter';
import { NavigationSidebar } from './NavigationSidebar';
import { ErrorBoundary } from './ErrorBoundary';
import { SettingsPanel } from './SettingsPanel';
import { AppHeader } from './AppHeader';

/**
 * BATCH 2 INTEGRATION COMPONENTS - USAGE GUIDE
 *
 * This file demonstrates the complete integration pattern for all 8 Batch 2 components.
 *
 * Components Overview:
 * - CommandPalette: Keyboard-driven command center (⌘+K)
 * - SearchBar: Global search with faceted filtering
 * - NotificationCenter: Alert management and notifications
 * - NavigationSidebar: Main navigation with collapsible menu
 * - ErrorBoundary: Error catching and fallback UI
 * - SettingsPanel: User and app settings modal
 * - AppHeader: Unified header combining search, notifications, settings
 * - AppLayout: Main layout wrapper orchestrating all components
 *
 * @doc.type guide
 * @doc.purpose Usage guide for Batch 2 integration components
 * @doc.layer product
 * @doc.pattern Documentation
 */

/**
 * ============================================
 * PATTERN 1: Using AppLayout (Recommended)
 * ============================================
 *
 * AppLayout combines header, sidebar, and error boundary for complete integration.
 * This is the recommended pattern for main application layout.
 *
 * Usage:
 * ```tsx
 * import { AppLayout } from '@/shared/components';
 *
 * function App() {
 *   const navigate = useNavigate();
 *   const location = useLocation();
 *
 *   return (
 *     <AppLayout
 *       currentPath={location.pathname}
 *       onNavigate={navigate}
 *       userName="John Doe"
 *       onSearch={(query) => {
 *         // Perform search
 *       }}
 *     >
 *       <YourPageContent />
 *     </AppLayout>
 *   );
 * }
 * ```
 *
 * Props:
 * - currentPath: string - Current route for sidebar highlighting
 * - onNavigate: (path: string) => void - Navigation handler
 * - userName: string - Display name in header
 * - userAvatar: string (optional) - Avatar image URL
 * - onSearch: (query: string) => void (optional) - Search handler
 *
 * Features:
 * - Combines AppHeader + NavigationSidebar
 * - Wraps content in ErrorBoundary
 * - Responsive mobile menu
 * - Footer with links
 * - Dark mode support throughout
 */

/**
 * ============================================
 * PATTERN 2: Using AppHeader Standalone
 * ============================================
 *
 * Use AppHeader when you have your own sidebar or layout.
 *
 * ```tsx
 * import { AppHeader } from '@/shared/components';
 *
 * function MyPage() {
 *   return (
 *     <>
 *       <AppHeader
 *         userName="Jane"
 *         onSearch={(query) => console.log(query)}
 *         onThemeChange={(theme) => setTheme(theme)}
 *       />
 *       <main>...</main>
 *     </>
 *   );
 * }
 * ```
 *
 * Props:
 * - userName: string - Display name
 * - userAvatar: string (optional) - Avatar URL
 * - onSearch: (query: string) => void (optional) - Search handler
 * - onThemeChange: (theme: 'light' | 'dark') => void (optional) - Theme handler
 * - onNotificationDismiss: (id: string) => void (optional) - Notification dismiss handler
 *
 * Includes:
 * - SearchBar with filtering
 * - NotificationCenter with bell icon
 * - SettingsPanel trigger
 * - CommandPalette trigger (⌘K)
 * - Theme toggle button
 * - User profile menu
 */

/**
 * ============================================
 * PATTERN 3: Using CommandPalette
 * ============================================
 *
 * Keyboard-driven command center. Automatically integrated in AppHeader.
 * Can be used standalone for advanced scenarios.
 *
 * ```tsx
 * import { CommandPalette } from '@/shared/components';
 *
 * function MyComponent() {
 *   const [showPalette, setShowPalette] = useState(false);
 *
 *   useEffect(() => {
 *     const handleKeyDown = (e: KeyboardEvent) => {
 *       if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
 *         e.preventDefault();
 *         setShowPalette(true);
 *       }
 *     };
 *     window.addEventListener('keydown', handleKeyDown);
 *     return () => window.removeEventListener('keydown', handleKeyDown);
 *   }, []);
 *
 *   return (
 *     <CommandPalette
 *       isOpen={showPalette}
 *       onClose={() => setShowPalette(false)}
 *     />
 *   );
 * }
 * ```
 *
 * Features:
 * - Keyboard shortcut: ⌘+K (Mac) / Ctrl+K (Windows)
 * - Fuzzy search on commands
 * - Category filtering
 * - Arrow key navigation
 * - Enter to execute, Escape to close
 * - Dark mode support
 */

/**
 * ============================================
 * PATTERN 4: Using SearchBar
 * ============================================
 *
 * Global search with faceted filtering. Automatically integrated in AppHeader.
 *
 * ```tsx
 * import { SearchBar } from '@/shared/components';
 *
 * function SearchPage() {
 *   return (
 *     <SearchBar
 *       onSearch={(query) => {
 *         // Perform search across all entities
 *         console.log('Search:', query);
 *       }}
 *       onFilter={(filters) => {
 *         // Apply filters
 *         console.log('Filters:', filters);
 *       }}
 *       placeholder="Search incidents, workflows..."
 *       showFilters={true}
 *     />
 *   );
 * }
 * ```
 *
 * Props:
 * - onSearch: (query: string) => void (optional)
 * - onFilter: (filters: Record<string, string>) => void (optional)
 * - placeholder: string - Search input placeholder
 * - showFilters: boolean - Show filter selects (default: true)
 *
 * Features:
 * - Real-time search input
 * - Recent searches dropdown
 * - Type and Status filter selects
 * - Clear button
 * - Dark mode support
 */

/**
 * ============================================
 * PATTERN 5: Using NotificationCenter
 * ============================================
 *
 * Alert and notification management. Automatically integrated in AppHeader.
 *
 * ```tsx
 * import { NotificationCenter } from '@/shared/components';
 *
 * function MyApp() {
 *   return (
 *     <NotificationCenter
 *       onDismiss={(id) => {
 *         // Handle notification dismissal
 *         console.log('Dismissed:', id);
 *       }}
 *       onMarkAsRead={(id) => {
 *         // Handle mark as read
 *         console.log('Read:', id);
 *       }}
 *     />
 *   );
 * }
 * ```
 *
 * Props:
 * - onDismiss: (id: string) => void (optional) - Dismissal handler
 * - onMarkAsRead: (id: string) => void (optional) - Mark as read handler
 *
 * Features:
 * - Bell icon with unread count badge
 * - Dropdown panel with notifications
 * - Filter by category (All, Alert, Action, Update)
 * - Priority level styling
 * - Mark as read/unread
 * - Clear all button
 * - Dark mode support
 */

/**
 * ============================================
 * PATTERN 6: Using NavigationSidebar
 * ============================================
 *
 * Main navigation menu. Automatically integrated in AppLayout.
 *
 * ```tsx
 * import { NavigationSidebar } from '@/shared/components';
 * import { useNavigate, useLocation } from 'react-router';
 *
 * function Layout() {
 *   const navigate = useNavigate();
 *   const { pathname } = useLocation();
 *
 *   return (
 *     <div className="flex">
 *       <NavigationSidebar
 *         currentPath={pathname}
 *         onNavigate={navigate}
 *         collapsed={false}
 *       />
 *       <main className="flex-1">...</main>
 *     </div>
 *   );
 * }
 * ```
 *
 * Props:
 * - currentPath: string (optional) - Current route for highlighting
 * - onNavigate: (path: string) => void (optional) - Navigation handler
 * - collapsed: boolean (optional) - Initial collapse state
 *
 * Features:
 * - 8 main menu sections
 * - Nested navigation items (15 total)
 * - Collapsible expand/collapse
 * - Current route highlighting
 * - Icon support
 * - Dark mode support
 */

/**
 * ============================================
 * PATTERN 7: Using ErrorBoundary
 * ============================================
 *
 * Error catching and recovery. Automatically wrapped in AppLayout.
 *
 * ```tsx
 * import { ErrorBoundary } from '@/shared/components';
 *
 * function App() {
 *   return (
 *     <ErrorBoundary
 *       onError={(error, errorInfo) => {
 *         // Log error to service
 *         console.error('Caught error:', error, errorInfo);
 *       }}
 *       fallback={<CustomErrorUI />}
 *       resetKeys={['someKey']} // Reset when keys change
 *     >
 *       <YourApp />
 *     </ErrorBoundary>
 *   );
 * }
 * ```
 *
 * Props:
 * - children: ReactNode - Child components
 * - onError: (error, errorInfo) => void (optional) - Error handler
 * - fallback: ReactElement (optional) - Custom fallback UI
 * - resetKeys: Array<string | number> (optional) - Auto-reset on change
 *
 * Features:
 * - Catches JavaScript errors in children
 * - Default error fallback UI with recovery options
 * - Development error details display
 * - Try Again button
 * - Go Home button
 * - Error ID generation
 * - Dark mode support
 */

/**
 * ============================================
 * PATTERN 8: Using SettingsPanel
 * ============================================
 *
 * User and app settings modal. Automatically integrated in AppHeader.
 *
 * ```tsx
 * import { SettingsPanel } from '@/shared/components';
 * import { useState } from 'react';
 *
 * function SettingsButton() {
 *   const [showSettings, setShowSettings] = useState(false);
 *
 *   return (
 *     <>
 *       <button onClick={() => setShowSettings(true)}>
 *         Settings
 *       </button>
 *       <SettingsPanel
 *         isOpen={showSettings}
 *         onClose={() => setShowSettings(false)}
 *         onSettingChange={(key, value) => {
 *           // Handle setting change
 *           console.log(key, value);
 *         }}
 *       />
 *     </>
 *   );
 * }
 * ```
 *
 * Props:
 * - isOpen: boolean (optional) - Panel visibility
 * - onClose: () => void (optional) - Close handler
 * - onSettingChange: (key: string, value: unknown) => void (optional)
 *
 * Settings Tabs:
 * - Appearance: Theme selection (Light, Dark, System)
 * - Notifications: Alert, Update, Weekly Digest toggles
 * - Display: Density, Auto-refresh, Refresh interval
 * - Data: Retention days, Auto-archive toggle
 */

/**
 * ============================================
 * INTEGRATION CHECKLIST
 * ============================================
 *
 * When integrating Batch 2 components:
 *
 * [ ] Import AppLayout at root level
 * [ ] Pass currentPath and onNavigate props
 * [ ] Wrap page content in AppLayout children
 * [ ] Implement search handler (optional but recommended)
 * [ ] Theme handling via HTML dark class
 * [ ] Error boundary fallback UI (if custom needed)
 * [ ] Test keyboard navigation (⌘K, arrows, escape)
 * [ ] Verify dark mode works across all components
 * [ ] Test responsive mobile menu
 * [ ] Verify accessibility with screen reader
 *
 * Dependencies Required:
 * - React 18+
 * - React Router 6+
 * - Tailwind CSS with dark mode plugin
 * - TypeScript 5+
 */

/**
 * ============================================
 * KEYBOARD SHORTCUTS
 * ============================================
 *
 * ⌘+K / Ctrl+K : Open Command Palette
 * ↑ / ↓ : Navigate commands
 * Enter : Execute command
 * Escape : Close Command Palette
 * 
 * In SearchBar:
 * Enter : Search
 * Escape : Clear
 *
 * In SettingsPanel:
 * Escape : Close (optional)
 * Tab : Navigate form fields
 */

export {
    AppLayout,
    CommandPalette,
    SearchBar,
    NotificationCenter,
    NavigationSidebar,
    ErrorBoundary,
    SettingsPanel,
    AppHeader,
};
