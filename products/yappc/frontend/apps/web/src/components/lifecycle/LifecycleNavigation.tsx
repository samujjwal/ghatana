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
import { Drawer, InteractiveList as List, ListItem, ListItemButton, ListItemIcon, ListItemText, Box, Typography, Divider, BottomNavigation, BottomNavigationAction } from '@ghatana/ui';
import { Home as HomeIcon, Activity as TimelineIcon, PenLine as CanvasIcon, ClipboardList as AssignmentIcon, Lightbulb as InsightsIcon, Shield as AuditIcon, Settings as SettingsIcon } from 'lucide-react';

// ============================================================================
// Types
// ============================================================================

interface NavItem {
    id: string;
    label: string;
    icon: React.ReactElement;
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
            variant="permanent"
            open={open}
            className="shrink-0" style={{ width: open ? 240 : 60 }}
        >
            <Box className="py-4">
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
                                        <ListItemText
                                            primary={item.label}
                                            secondary={item.description}
                                            primaryTypographyProps={{
                                                fontSize: 14,
                                                fontWeight: isActive ? 600 : 400,
                                            }}
                                            secondaryTypographyProps={{
                                                fontSize: 12,
                                                noWrap: true,
                                            }}
                                        />
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

    const handleChange = (_: React.SyntheticEvent, newValue: number) => {
        navigate(NAV_ITEMS[newValue].path);
    };

    // Show only primary items on mobile
    const mobileItems = NAV_ITEMS.slice(0, 5);

    return (
        <BottomNavigation
            value={activeIndex >= 0 && activeIndex < 5 ? activeIndex : 0}
            onChange={handleChange}
            showLabels
            className="fixed bottom-[0px] left-[0px] right-[0px] border-gray-200 dark:border-gray-700 z-[1100] border-t" >
            {mobileItems.map((item) => (
                <BottomNavigationAction
                    key={item.id}
                    label={item.label}
                    icon={item.icon}
                />
            ))}
        </BottomNavigation>
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
    const theme = useTheme();
    const isMobile = useMediaQuery(theme.breakpoints.down('md'));

    if (isMobile) {
        return <MobileBottomNav />;
    }

    return <DesktopSidebar open={open} onClose={onClose} />;
}
