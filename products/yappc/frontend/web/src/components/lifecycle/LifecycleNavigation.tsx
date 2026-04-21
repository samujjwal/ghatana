/**
 * Lifecycle Navigation Component
 * 
 * Global navigation for lifecycle views.
 * Responsive sidebar (desktop) / bottom nav (mobile).
 * 
 * @doc.type component
 * @doc.purpose Global lifecycle navigation
 * @doc.layer product
 * @doc.pattern Navigation Component
 */

import { useNavigate, useLocation } from 'react-router';
import {
    Drawer,
    InteractiveList as List,
    ListItem,
    ListItemButton,
    ListItemIcon,
    Box,
    Typography,
} from '@ghatana/design-system';
import { Home as HomeIcon, Activity as TimelineIcon, PenLine as CanvasIcon, ClipboardList as AssignmentIcon, Lightbulb as InsightsIcon, Shield as AuditIcon, Settings as SettingsIcon } from 'lucide-react';
import type { ReactElement } from 'react';

// ============================================================================
// Types
// ============================================================================

interface NavItem {
    id: string;
    label: string;
    icon: ReactElement;
    path: string;
    description?: string;
}

const NAV_ITEMS: NavItem[] = [
    {
        id: 'home',
        label: 'Home',
        icon: <HomeIcon />,
        path: '/journey',
        description: 'Dashboard and overview',
    },
    {
        id: 'setup',
        label: 'Setup',
        icon: <TimelineIcon />,
        path: '/journey/setup',
        description: 'Project setup and configuration',
    },
    {
        id: 'items',
        label: 'DevSecOps Items',
        icon: <AssignmentIcon />,
        path: '/journey/items',
        description: 'Work items and tasks',
    },
    {
        id: 'insights',
        label: 'Insights',
        icon: <InsightsIcon />,
        path: '/journey/insights',
        description: 'AI insights and recommendations',
    },
    {
        id: 'audit',
        label: 'Audit',
        icon: <AuditIcon />,
        path: '/journey/audit',
        description: 'Audit trail and compliance',
    },
    {
        id: 'settings',
        label: 'Settings',
        icon: <SettingsIcon />,
        path: '/journey/settings',
        description: 'Project and workspace settings',
    },
];

// ============================================================================
// Desktop Sidebar Component
// ============================================================================

interface DesktopSidebarProps {
    open: boolean;
    onClose?: () => void;
}

function DesktopSidebar({ open, onClose }: DesktopSidebarProps) {
    const navigate = useNavigate();
    const location = useLocation();

    const handleNavigate = (path: string) => {
        navigate(path);
        onClose?.();
    };

    return (
        <Drawer
            open={open}
            onClose={onClose ?? (() => undefined)}
        >
            <Box className="shrink-0 py-4" style={{ width: open ? 240 : 60 }}>
                <List>
                    {NAV_ITEMS.map((item) => {
                        const isActive = location.pathname.startsWith(item.path);

                        return (
                            <ListItem key={item.id} disablePadding className="block">
                                <ListItemButton
                                    onClick={() => handleNavigate(item.path)}
                                    selected={isActive}
                                    className="min-h-[48px] px-5" style={{ justifyContent: open ? 'initial' : 'center' }}
                                >
                                    <ListItemIcon
                                        className="min-w-0 justify-center" style={{ marginRight: open ? 24 : 'auto', color: isActive ? '#1976d2' : '#666' }}
                                    >
                                        {item.icon}
                                    </ListItemIcon>
                                    {open && (
                                        <Box className="min-w-0">
                                            <Typography className="text-sm" style={{ fontWeight: isActive ? 600 : 400 }}>
                                                {item.label}
                                            </Typography>
                                            {item.description && (
                                                <Typography className="truncate text-xs text-gray-500 dark:text-gray-400">
                                                    {item.description}
                                                </Typography>
                                            )}
                                        </Box>
                                    )}
                                </ListItemButton>
                            </ListItem>
                        );
                    })}
                </List>
            </Box>
        </Drawer>
    );
}

// ============================================================================
// Mobile Bottom Navigation
// ============================================================================

function MobileBottomNav() {
    const navigate = useNavigate();
    const location = useLocation();

    // Find current active tab
    const activeIndex = NAV_ITEMS.findIndex(item => location.pathname.startsWith(item.path));

    // Show only primary items on mobile
    const mobileItems = NAV_ITEMS.slice(0, 5);

    return (
        <Box className="fixed bottom-[0px] left-[0px] right-[0px] z-[1100] flex border-t border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-900">
            {mobileItems.map((item) => (
                <button
                    key={item.id}
                  type="button"
                  onClick={() => navigate(item.path)}
                  className={`flex flex-1 flex-col items-center gap-1 py-2 text-xs ${item === mobileItems[activeIndex] ? 'text-blue-600' : 'text-gray-600 dark:text-gray-300'}`}
                >
                    {item.icon}
                    <span>{item.label}</span>
                </button>
            ))}
        </Box>
    );
}

// ============================================================================
// Main Navigation Component
// ============================================================================

interface LifecycleNavigationProps {
    open?: boolean;
    onClose?: () => void;
}

export function LifecycleNavigation({ open = true, onClose }: LifecycleNavigationProps) {
    const isMobile = typeof window !== 'undefined' ? window.innerWidth < 768 : false;

    if (isMobile) {
        return <MobileBottomNav />;
    }

    return <DesktopSidebar open={open} onClose={onClose} />;
}
