import React, { useState, ReactNode } from 'react';
import { AppHeader } from './AppHeader';
import { NavigationSidebar } from './NavigationSidebar';
import { ErrorBoundary } from './ErrorBoundary';

/**
 * AppLayout - Main application layout wrapper.
 *
 * <p><b>Purpose</b><br>
 * Provides unified layout combining header, sidebar, and main content area.
 * Handles responsive behavior and error boundaries.
 *
 * <p><b>Features</b><br>
 * - Responsive header and sidebar
 * - Error boundary wrapping
 * - Mobile navigation collapse
 * - Current route tracking
 * - Content area with proper spacing
 * - Dark mode support
 *
 * <p><b>Usage</b><br>
 * ```tsx
 * <AppLayout
 *   currentPath="/dashboard"
 *   onNavigate={(path) => navigate(path)}
 * >
 *   <MyPageContent />
 * </AppLayout>
 * ```
 *
 * @doc.type component
 * @doc.purpose Main application layout container
 * @doc.layer product
 * @doc.pattern Container
 */

interface AppLayoutProps {
    children: ReactNode;
    currentPath?: string;
    onNavigate?: (path: string) => void;
    userName?: string;
    userAvatar?: string;
    onSearch?: (query: string) => void;
}

export const AppLayout = React.memo(function AppLayout({
    children,
    currentPath = '/',
    onNavigate = () => { },
    userName = 'User',
    userAvatar,
    onSearch,
}: AppLayoutProps) {
    const [sidebarCollapsed] = useState(false);
    const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

    return (
        <ErrorBoundary
            fallback={
                <div className="min-h-screen bg-white dark:bg-slate-900 flex items-center justify-center">
                    <div className="text-center">
                        <p className="text-red-600 dark:text-rose-400 mb-4">Application Error</p>
                        <button
                            onClick={() => window.location.reload()}
                            className="px-4 py-2 bg-blue-500 hover:bg-blue-600 text-white rounded-lg font-medium"
                        >
                            Reload Application
                        </button>
                    </div>
                </div>
            }
        >
            <div className="flex flex-col h-screen bg-slate-50 dark:bg-slate-950">
                {/* Header */}
                <AppHeader
                    userName={userName}
                    userAvatar={userAvatar}
                    onSearch={onSearch}
                />

                {/* Main Content Area */}
                <div className="flex flex-1 overflow-hidden">
                    {/* Desktop Sidebar */}
                    <div className="hidden lg:flex lg:flex-col">
                        <NavigationSidebar
                            currentPath={currentPath}
                            onNavigate={onNavigate}
                            collapsed={sidebarCollapsed}
                        />
                    </div>

                    {/* Mobile Menu Overlay */}
                    {mobileMenuOpen && (
                        <div
                            className="fixed inset-0 bg-black/50 z-20 lg:hidden"
                            onClick={() => setMobileMenuOpen(false)}
                        />
                    )}

                    {/* Mobile Sidebar */}
                    {mobileMenuOpen && (
                        <div className="absolute left-0 top-16 bottom-0 w-64 bg-white dark:bg-slate-900 border-r border-slate-200 dark:border-neutral-600 z-30 lg:hidden overflow-y-auto">
                            <NavigationSidebar
                                currentPath={currentPath}
                                onNavigate={(path) => {
                                    onNavigate(path);
                                    setMobileMenuOpen(false);
                                }}
                            />
                        </div>
                    )}

                    {/* Main Content */}
                    <main className="flex-1 overflow-y-auto">
                        <div className="lg:hidden flex items-center gap-2 px-4 py-3 border-b border-slate-200 dark:border-neutral-600">
                            <button
                                onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
                                className="p-2 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-lg transition-colors"
                                aria-label="Toggle mobile menu"
                            >
                                ☰
                            </button>
                            <span className="text-sm text-slate-600 dark:text-neutral-400">
                                {currentPath === '/' ? 'Dashboard' : currentPath.split('/')[1]}
                            </span>
                        </div>

                        {/* Page Content with Proper Spacing */}
                        <div className="p-4 md:p-6 max-w-7xl mx-auto w-full">
                            {children}
                        </div>
                    </main>
                </div>

                {/* Footer */}
                <footer className="hidden md:flex items-center justify-between px-6 py-3 border-t border-slate-200 dark:border-neutral-600 bg-white dark:bg-slate-900 text-sm text-slate-500 dark:text-neutral-400">
                    <p>© 2024 Ghatana - AI-First Event Processing Platform</p>
                    <div className="flex gap-4">
                        <a href="#" className="hover:text-slate-700 dark:hover:text-slate-300">
                            Docs
                        </a>
                        <a href="#" className="hover:text-slate-700 dark:hover:text-slate-300">
                            Support
                        </a>
                        <a href="#" className="hover:text-slate-700 dark:hover:text-slate-300">
                            Status
                        </a>
                    </div>
                </footer>
            </div>
        </ErrorBoundary>
    );
});

export default AppLayout;
