import React, { useState } from 'react';
import { CommandPalette } from './CommandPalette';
import { SearchBar } from './SearchBar';
import { NotificationCenter } from './NotificationCenter';
import { SettingsPanel } from './SettingsPanel';

/**
 * AppHeader - Main application header with integrated controls.
 *
 * <p><b>Purpose</b><br>
 * Provides unified header with search, notifications, settings, and command palette.
 * Serves as main control center for user interactions.
 *
 * <p><b>Features</b><br>
 * - Integrated search bar
 * - Notification center
 * - Settings panel access
 * - Command palette trigger
 * - User profile menu
 * - Theme toggle
 * - Responsive design
 * - Dark mode support
 *
 * <p><b>Usage</b><br>
 * ```tsx
 * <AppHeader
 *   userName="John Doe"
 *   onSearch={(query) => console.log(query)}
 * />
 * ```
 *
 * @doc.type component
 * @doc.purpose Main application header
 * @doc.layer product
 * @doc.pattern Organism
 */

interface AppHeaderProps {
    userName?: string;
    userAvatar?: string;
    onSearch?: (query: string) => void;
    onThemeChange?: (theme: 'light' | 'dark') => void;
    onNotificationDismiss?: (id: string) => void;
}

export const AppHeader = React.memo(function AppHeader({
    userName = 'User',
    userAvatar,
    onSearch,
    onThemeChange,
    onNotificationDismiss,
}: AppHeaderProps) {
    const [showSettings, setShowSettings] = useState(false);
    const [showCommandPalette, setShowCommandPalette] = useState(false);
    const [isDarkMode, setIsDarkMode] = useState(false);
    const [showUserMenu, setShowUserMenu] = useState(false);

    const handleThemeToggle = () => {
        const newMode = !isDarkMode;
        setIsDarkMode(newMode);
        onThemeChange?.(newMode ? 'dark' : 'light');

        // Apply theme to document
        if (newMode) {
            document.documentElement.classList.add('dark');
        } else {
            document.documentElement.classList.remove('dark');
        }
    };

    return (
        <header className="sticky top-0 z-30 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-neutral-600 shadow-sm">
            <div className="flex items-center justify-between gap-4 px-6 py-4">
                {/* Left: Logo/Title */}
                <div className="flex items-center gap-3">
                    <span className="text-2xl font-bold text-blue-600 dark:text-indigo-400">🧠</span>
                    <h1 className="text-lg font-bold text-slate-900 dark:text-neutral-100 hidden sm:block">
                        Ghatana
                    </h1>
                </div>

                {/* Center: Search Bar */}
                <div className="flex-1 max-w-2xl">
                    <SearchBar
                        onSearch={onSearch}
                        placeholder="Search workflows, incidents, models..."
                        showFilters={false}
                    />
                </div>

                {/* Right: Controls */}
                <div className="flex items-center gap-2">
                    {/* Command Palette Trigger */}
                    <button
                        onClick={() => setShowCommandPalette(!showCommandPalette)}
                        className="p-2 text-slate-600 dark:text-neutral-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-700 rounded-lg transition-colors hidden md:flex"
                        title="Command Palette (⌘K)"
                    >
                        ⌨️
                    </button>

                    {/* Theme Toggle */}
                    <button
                        onClick={handleThemeToggle}
                        className="p-2 text-slate-600 dark:text-neutral-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-700 rounded-lg transition-colors"
                        title={isDarkMode ? 'Light mode' : 'Dark mode'}
                    >
                        {isDarkMode ? '☀️' : '🌙'}
                    </button>

                    {/* Notification Center */}
                    <NotificationCenter
                        count={5}
                        onDismiss={onNotificationDismiss}
                    />

                    {/* Settings Button */}
                    <button
                        onClick={() => setShowSettings(true)}
                        className="p-2 text-slate-600 dark:text-neutral-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-700 rounded-lg transition-colors"
                        title="Settings"
                    >
                        ⚙️
                    </button>

                    {/* User Profile Menu */}
                    <div className="relative">
                        <button
                            onClick={() => setShowUserMenu(!showUserMenu)}
                            className="flex items-center gap-2 p-2 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-lg transition-colors"
                            aria-expanded={showUserMenu}
                        >
                            {userAvatar ? (
                                <img src={userAvatar} alt={userName} className="w-6 h-6 rounded-full" />
                            ) : (
                                <div className="w-6 h-6 rounded-full bg-blue-500 flex items-center justify-center text-xs text-white font-bold">
                                    {userName.charAt(0).toUpperCase()}
                                </div>
                            )}
                            <span className="text-sm text-slate-900 dark:text-neutral-100 hidden sm:inline">
                                {userName.split(' ')[0]}
                            </span>
                            <span className="text-xs">▼</span>
                        </button>

                        {/* User Menu Dropdown */}
                        {showUserMenu && (
                            <div className="absolute right-0 top-full mt-2 w-48 bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg shadow-lg">
                                <div className="px-4 py-2 border-b border-slate-200 dark:border-neutral-600">
                                    <p className="text-sm font-medium text-slate-900 dark:text-neutral-100">{userName}</p>
                                    <p className="text-xs text-slate-500 dark:text-neutral-400">user@ghatana.ai</p>
                                </div>

                                <button className="w-full text-left px-4 py-2 text-sm text-slate-700 dark:text-neutral-300 hover:bg-slate-50 dark:hover:bg-slate-700">
                                    👤 Profile
                                </button>
                                <button className="w-full text-left px-4 py-2 text-sm text-slate-700 dark:text-neutral-300 hover:bg-slate-50 dark:hover:bg-slate-700">
                                    🔐 Change Password
                                </button>
                                <button className="w-full text-left px-4 py-2 text-sm text-slate-700 dark:text-neutral-300 hover:bg-slate-50 dark:hover:bg-slate-700">
                                    📋 Activity Log
                                </button>

                                <div className="border-t border-slate-200 dark:border-neutral-600 p-2">
                                    <button className="w-full text-left px-4 py-2 text-sm text-red-600 dark:text-rose-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded">
                                        🚪 Logout
                                    </button>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </div>

            {/* Command Palette Modal */}
            {showCommandPalette && (
                <CommandPalette
                    isOpen={showCommandPalette}
                    onClose={() => setShowCommandPalette(false)}
                />
            )}

            {/* Settings Panel Modal */}
            {showSettings && (
                <SettingsPanel
                    isOpen={showSettings}
                    onClose={() => setShowSettings(false)}
                    onSettingChange={(key) => {
                        if (key === 'theme') {
                            handleThemeToggle();
                        }
                    }}
                />
            )}
        </header>
    );
});

export default AppHeader;
