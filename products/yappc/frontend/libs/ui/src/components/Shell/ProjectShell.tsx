/**
 * Owner: UI Team
 * 
 * ProjectShell - Reusable project layout shell component
 * Provides the main layout structure for project pages with navigation tabs and content area
 */

import { Menu as MenuIcon, ArrowLeft as ArrowBackIcon, Home as HomeIcon } from 'lucide-react';
import { Box, Container, Typography, Breadcrumb as Breadcrumbs, Link as MuiLink, IconButton, Button, Drawer, AppBar, Toolbar } from '@ghatana/ui';
import React from 'react';

import { TabNavigation } from '../Navigation/TabNavigation';

import type { TabNavigationItem } from '../Navigation/TabNavigation';

/** Custom hook to detect mobile viewport (md breakpoint = 768px) */
function useIsMobile(): boolean {
    const [isMobile, setIsMobile] = React.useState(false);

    React.useEffect(() => {
        if (typeof window === 'undefined') return;

        const mql = window.matchMedia('(max-width: 767px)');
        setIsMobile(mql.matches);

        const handler = (e: MediaQueryListEvent) => setIsMobile(e.matches);
        mql.addEventListener('change', handler);
        return () => mql.removeEventListener('change', handler);
    }, []);

    return isMobile;
}

/**
 *
 */
export interface ProjectShellBreadcrumb {
    /**
     * Display label for the breadcrumb
     */
    label: string;

    /**
     * Link path for the breadcrumb
     */
    href?: string;

    /**
     * Icon to display with the breadcrumb
     */
    icon?: React.ReactNode;
}

/**
 *
 */
export interface ProjectShellAction {
    /**
     * Unique identifier for the action
     */
    id: string;

    /**
     * Display label for the action
     */
    label: string;

    /**
     * Icon to display with the action
     */
    icon?: React.ReactNode;

    /**
     * Click handler for the action
     */
    onClick: () => void;

    /**
     * Whether the action is disabled
     */
    disabled?: boolean;

    /**
     * Visual variant of the action
     */
    variant?: 'text' | 'outlined' | 'contained';
}

/**
 *
 */
export interface ProjectShellProps {
    /**
     * Project title to display in the header
     */
    title: string;

    /**
     * Project description or subtitle
     */
    description?: string;

    /**
     * Breadcrumb navigation items
     */
    breadcrumbs?: ProjectShellBreadcrumb[];

    /**
     * Tab navigation items
     */
    tabs: TabNavigationItem[];

    /**
     * Currently active tab id
     */
    activeTab: string;

    /**
     * Callback when tab changes
     */
    onTabChange: (tabId: string) => void;

    /**
     * Action buttons to display in the header
     */
    actions?: ProjectShellAction[];

    /**
     * Main content to display
     */
    children: React.ReactNode;

    /**
     * Whether to show back button
     */
    showBackButton?: boolean;

    /**
     * Callback for back button click
     */
    onBackClick?: () => void;

    /**
     * Whether the shell is in loading state
     */
    isLoading?: boolean;

    /**
     * Custom header content
     */
    headerContent?: React.ReactNode;

    /**
     * Additional CSS class name
     */
    className?: string;
}

/**
 * ProjectShell component for project layout
 */
export const ProjectShell = React.forwardRef<HTMLDivElement, ProjectShellProps>(
    (props, ref) => {
        const {
            title,
            description,
            breadcrumbs,
            tabs,
            activeTab,
            onTabChange,
            actions,
            children,
            showBackButton = false,
            onBackClick,
            isLoading = false,
            headerContent,
            className,
            ...rest
        } = props;

        // Filter rest props so we don't accidentally forward unknown props to DOM elements
        const forwardedProps: Record<string, unknown> = {};
        const allowList = /^(id|role|data-|aria-)/i;
        Object.keys(rest || {}).forEach((key) => {
            if (allowList.test(key)) {
                (forwardedProps as unknown)[key] = (rest as unknown)[key];
            }
        });

        const isMobile = useIsMobile();
        const [mobileMenuOpen, setMobileMenuOpen] = React.useState(false);

        const handleBackClick = () => {
            if (onBackClick) {
                onBackClick();
            } else {
                // Default back behavior
                window.history.back();
            }
        };

        const handleMobileMenuToggle = () => {
            setMobileMenuOpen(!mobileMenuOpen);
        };

        const renderBreadcrumbs = () => {
            if (!breadcrumbs || breadcrumbs.length === 0) return null;

            return (
                <Breadcrumbs
                    aria-label="project breadcrumb"
                    className="mb-2"
                >
                    <MuiLink
                        href="/"
                        underline="hover"
                        tone="neutral"
                        className="flex items-center gap-1"
                    >
                        <HomeIcon size={16} />
                        Home
                    </MuiLink>
                    {breadcrumbs.map((crumb, index) => (
                        <MuiLink
                            key={index}
                            href={crumb.href}
                            underline="hover"
                            color={index === breadcrumbs.length - 1 ? 'text.primary' : 'inherit'}
                            className="flex items-center gap-1"
                        >
                            {crumb.icon}
                            {crumb.label}
                        </MuiLink>
                    ))}
                </Breadcrumbs>
            );
        };

        const renderActions = () => {
            if (!actions || actions.length === 0) return null;

            return (
                <Box className="flex items-center gap-2 ml-auto sm:flex hidden">
                    {actions.map((action) => (
                        <IconButton
                            key={action.id}
                            onClick={action.onClick}
                            disabled={action.disabled || isLoading}
                            tone="primary"
                            aria-label={action.label}
                            title={action.label}
                        >
                            {action.icon}
                        </IconButton>
                    ))}
                </Box>
            );
        };

        return (
            <Box
                ref={ref}
                className={['flex flex-col min-h-screen bg-gray-50 dark:bg-gray-950', className].filter(Boolean).join(' ')}
                {...forwardedProps}
            >
                {/* Header */}
                <Box className="bg-white dark:bg-gray-900 border-b border-gray-200 dark:border-gray-700 sticky top-0 z-50">
                    <Container maxWidth="xl" className="px-6 py-4 md:px-4 md:py-3">
                        {renderBreadcrumbs()}

                        <Box className="flex items-center gap-4 mb-3 md:gap-2 md:mb-2">
                            {showBackButton && (
                                <IconButton
                                    onClick={handleBackClick}
                                    aria-label="Go back"
                                    edge="start"
                                >
                                    <ArrowBackIcon />
                                </IconButton>
                            )}

                            {isMobile && (
                                <IconButton
                                    onClick={handleMobileMenuToggle}
                                    aria-label="Open navigation menu"
                                    edge="start"
                                >
                                    <MenuIcon />
                                </IconButton>
                            )}

                            <Box className="flex-1">
                                <Typography
                                    as="h4"
                                    component="h1"
                                    className="font-bold text-2xl md:text-3xl leading-tight"
                                >
                                    {title}
                                </Typography>

                                {description && (
                                    <Typography
                                        as="p"
                                        color="text.secondary"
                                        className="mt-1 text-sm md:text-base"
                                    >
                                        {description}
                                    </Typography>
                                )}
                            </Box>

                            {!isMobile && renderActions()}
                        </Box>

                        {headerContent}
                    </Container>

                    {!isMobile && (
                        <TabNavigation
                            items={tabs}
                            activeTab={activeTab}
                            onTabChange={onTabChange}
                            variant="scrollable"
                            showBorder={false}
                            aria-label="Tab navigation"
                        />
                    )}
                </Box>

                {/* Mobile Navigation Drawer */}
                {isMobile && (
                    <Drawer
                        anchor="left"
                        open={mobileMenuOpen}
                        onClose={handleMobileMenuToggle}
                    >
                        <AppBar position="static" variant="flat">
                            <Toolbar>
                                <Typography as="h6" noWrap component="div">
                                    Navigation
                                </Typography>
                            </Toolbar>
                        </AppBar>

                        <Box className="p-4">
                            <TabNavigation
                                items={tabs}
                                activeTab={activeTab}
                                onTabChange={(tabId) => {
                                    onTabChange(tabId);
                                    setMobileMenuOpen(false);
                                }}
                                variant="fullWidth"
                                showBorder={false}
                                aria-label="Mobile project navigation"
                            />
                        </Box>

                        {actions && actions.length > 0 && (
                            <Box className="p-4 border-t border-gray-200 dark:border-gray-700">
                                <Typography as="p" className="text-sm font-medium" color="text.secondary" gutterBottom>
                                    Actions
                                </Typography>
                                {actions.map((action) => (
                                    <Box key={action.id} className="mb-2">
                                        <Button
                                            onClick={() => {
                                                action.onClick();
                                                setMobileMenuOpen(false);
                                            }}
                                            disabled={action.disabled || isLoading}
                                            variant={action.variant === 'contained' ? 'solid' : action.variant === 'outlined' ? 'outline' : 'ghost'}
                                            fullWidth
                                            className="justify-start gap-2"
                                            startIcon={action.icon as unknown}
                                        >
                                            {action.label}
                                        </Button>
                                    </Box>
                                ))}
                            </Box>
                        )}
                    </Drawer>
                )}

                {/* Content Area */}
                <Box className="flex-1 flex flex-col p-6 md:p-4 sm:p-2">
                    {isLoading ? (
                        <Box className="flex justify-center items-center min-h-[200px]">
                            <Typography color="text.secondary">Loading...</Typography>
                        </Box>
                    ) : (
                        children
                    )}
                </Box>
            </Box>
        );
    }
);

ProjectShell.displayName = 'ProjectShell';