import React, { useCallback } from 'react';
import { Link, useNavigate } from 'react-router';
import { useNavigation } from '@/context/NavigationContext';
// NOTE: `TopNavigation` no longer invalidates queries - hooks should include role in query keys
import { PersonaSelector } from './PersonaSelector';
import { Tooltip } from './Tooltip';
import { useAtom } from 'jotai';
import { userRoleAtom, userProfileAtom, personaConfigAtom } from '@/state/jotai/atoms';
import { useEffect } from 'react';

/**
 * Props for TopNavigation component
 *
 * @doc.type interface
 * @doc.purpose TopNavigation component props
 * @doc.layer product
 * @doc.pattern Props
 */
export interface TopNavigationProps {
    /** Additional CSS classes */
    className?: string;
    /** Show back button (default: true) */
    showBackButton?: boolean;
    /** Show home button (default: true) */
    showHomeButton?: boolean;
    /** Optional home path override (defaults to '/') */
    homePath?: string;
}

/**
 * Top navigation bar component
 *
 * <p><b>Purpose</b><br>
 * Provides consistent top navigation with home link and back button.
 * Allows users to quickly navigate to home from any page.
 *
 * <p><b>Features</b><br>
 * - Home button with app logo/icon (always visible)
 * - Back button to go to previous page (smart - disabled on first page)
 * - Responsive design
 * - Dark mode support
 * - Fixed positioning with proper z-index
 * - Page title display
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <TopNavigation showBackButton showHomeButton />
 * }</pre>
 *
 * <p><b>Props</b><br>
 * - `homePath`: Optional path to navigate to when Home is clicked (defaults to `/`).
 *
 * <p><b>Component Hierarchy</b><br>
 * TopNavigation
 * ├── BackButton (conditional)
 * ├── HomeButton
 * └── PageTitle
 *
 * @doc.type component
 * @doc.purpose Top navigation bar
 * @doc.layer product
 * @doc.pattern Navigation
 */
export const TopNavigation: React.FC<TopNavigationProps> = ({
    className = '',
    showBackButton = true,
    showHomeButton = true,
    homePath = '/',
}) => {
    const navigate = useNavigate();
    const { pageTitle, isHomePage } = useNavigation();
    const [selectedPersona, setSelectedPersona] = useAtom(userRoleAtom);
    const [userProfile, setUserProfile] = useAtom(userProfileAtom);
    const [personaConfig] = useAtom(personaConfigAtom);

    const handleBack = useCallback(() => {
        navigate(-1);
    }, [navigate]);

    // Wrapper to properly set the persona in Jotai atom
    const handlePersonaChange = useCallback((role: typeof selectedPersona) => {
        // Update lightweight user role atom (used for quick global checks)
        setSelectedPersona(role);

        // If we have a user profile, update it so derived atoms (personaConfig) react to the change
        if (userProfile) {
            setUserProfile({ ...userProfile, role });
        } else {
            // Create a minimal dev profile if none exists
            setUserProfile({
                userId: 'dev-user',
                name: `${role.charAt(0).toUpperCase() + role.slice(1)} User`,
                email: `${role}@example.com`,
                role,
                permissions: ['all'],
            });
        }

        // Navigate to persona dashboard so persona-specific pages are shown there
        navigate('/persona-dashboard');
    }, [setSelectedPersona, setUserProfile, userProfile, navigate]);
    // No explicit invalidation here - hooks include role in query keys so react-query refetches automatically

    useEffect(() => {
        // Debugging: log persona changes to confirm handler fires and atoms update (dev only)
        if (import.meta.env.DEV) {
            console.debug('[TopNavigation] Selected persona is now:', selectedPersona);
            console.debug('[TopNavigation] Derived personaConfig role:', personaConfig?.role);
        }
    }, [selectedPersona, personaConfig]);

    // Determine if back button should be disabled (no history to go back to)
    const canGoBack = window.history.length > 1;

    return (
        <nav className={`
            fixed top-0 left-0 right-0 z-50
            bg-white dark:bg-slate-900
            border-b border-slate-200 dark:border-slate-800
            shadow-sm dark:shadow-md
            h-16
            ${className}
        `}>
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-full">
                <div className="h-full flex items-center justify-between gap-4">
                    {/* Left section: Back + Home buttons */}
                    <div className="flex items-center gap-2">
                        {showBackButton && (
                            <Tooltip
                                content="Go back to previous page (← key)"
                                position="bottom"
                            >
                                <button
                                    onClick={handleBack}
                                    disabled={!canGoBack || isHomePage}
                                    className={`
                                        p-2 rounded-lg transition-all duration-200
                                        flex items-center justify-center
                                        ${canGoBack && !isHomePage
                                            ? 'hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-700 dark:text-neutral-300'
                                            : 'opacity-50 cursor-not-allowed text-slate-400'
                                        }
                                    `}
                                    aria-label="Go back to previous page"
                                >
                                    <span className="text-xl">←</span>
                                </button>
                            </Tooltip>
                        )}

                        {showHomeButton && (
                            <Tooltip
                                content="Go to home page (Home key)"
                                position="bottom"
                            >
                                <Link
                                    to={homePath}
                                    onClick={(e) => {
                                        e.preventDefault();
                                        navigate(homePath);
                                    }}
                                    className="p-2 rounded-lg transition-all duration-200 hover:bg-slate-100 dark:hover:bg-slate-800 flex items-center justify-center"
                                    aria-label="Go to home page"
                                >
                                    <span className="text-xl">🏠</span>
                                </Link>
                            </Tooltip>
                        )}
                    </div>

                    {/* Center section: Page title */}
                    <div className="flex-1 text-center hidden sm:block">
                        <h1 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 truncate">
                            {pageTitle}
                        </h1>
                    </div>

                    {/* Right section: Persona selector */}
                    <div className="flex items-center gap-3">
                        <Tooltip
                            content="Switch your persona for different views and features (Admin, Lead, Engineer, Viewer)"
                            position="left"
                        >
                            <div className="text-xs font-medium text-slate-600 dark:text-neutral-400 px-2 py-1 rounded bg-slate-100 dark:bg-neutral-800">
                                Persona
                            </div>
                        </Tooltip>
                        <div className="flex items-center gap-2">
                            <span className="hidden sm:inline text-sm text-slate-700 dark:text-neutral-300">{selectedPersona?.toUpperCase()}</span>
                            <PersonaSelector
                                selectedPersona={selectedPersona}
                                onSelectPersona={handlePersonaChange}
                                variant="inline"
                                showLabel={false}
                            />
                        </div>
                    </div>
                </div>
            </div>
        </nav>
    );
};

export default TopNavigation;
