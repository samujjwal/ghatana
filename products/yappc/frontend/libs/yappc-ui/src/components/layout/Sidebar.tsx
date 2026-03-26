/**
 * Sidebar
 *
 * Application sidebar with workspace-scoped project navigation
 * and quick links (Canvas, Copilot, Settings).
 *
 * @doc.type component
 * @doc.purpose Application sidebar navigation
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import React from 'react';
import {
    Box,
    Drawer,
    List,
    ListItemButton,
    ListItemIcon,
    ListItemText,
    Divider,
    Typography,
    Skeleton,
} from '@mui/material';
import {
    LayoutDashboard as DashboardIcon,
    Layers as CanvasIcon,
    Bot as CopilotIcon,
    Settings as SettingsIcon,
    FolderOpen as ProjectIcon,
} from 'lucide-react';

import type { Project } from '@yappc/core/types';

export interface NavItem {
    id: string;
    label: string;
    icon: React.ReactNode;
    path?: string;
    onClick?: () => void;
}

export interface SidebarProps {
    open: boolean;
    drawerWidth?: number;
    /** MUI responsive variant — 'temporary' for mobile, 'permanent' for desktop */
    variant?: 'temporary' | 'permanent' | 'persistent';
    onClose?: () => void;
    projects?: Project[];
    projectsLoading?: boolean;
    activeProjectId?: string | null;
    activePath?: string;
    onProjectSelect?: (project: Project) => void;
    onNavItemClick?: (item: NavItem) => void;
}

const DEFAULT_NAV_ITEMS: NavItem[] = [
    {
        id: 'dashboard',
        label: 'Dashboard',
        icon: <DashboardIcon size={18} />,
        path: '/',
    },
    {
        id: 'canvas',
        label: 'Canvas',
        icon: <CanvasIcon size={18} />,
        path: '/canvas',
    },
    {
        id: 'copilot',
        label: 'AI Copilot',
        icon: <CopilotIcon size={18} />,
        path: '/copilot',
    },
    {
        id: 'settings',
        label: 'Settings',
        icon: <SettingsIcon size={18} />,
        path: '/settings',
    },
];

/**
 * Application drawer sidebar with project list and nav links.
 */
export const Sidebar: React.FC<SidebarProps> = ({
    open,
    drawerWidth = 240,
    variant = 'permanent',
    onClose,
    projects = [],
    projectsLoading = false,
    activeProjectId,
    activePath,
    onProjectSelect,
    onNavItemClick,
}) => {
    const drawerContent = (
        <Box
            sx={{ display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden' }}
        >
            {/* App brand */}
            <Box sx={{ px: 2, py: 1.5 }}>
                <Typography variant="subtitle2" fontWeight={700} letterSpacing={0.5}>
                    YAPPC
                </Typography>
            </Box>

            <Divider />

            {/* Primary nav */}
            <List dense disablePadding sx={{ py: 0.5 }}>
                {DEFAULT_NAV_ITEMS.map((item) => (
                    <ListItemButton
                        key={item.id}
                        selected={activePath === item.path}
                        onClick={() => onNavItemClick?.(item)}
                        sx={{ borderRadius: 1, mx: 0.5, my: 0.25 }}
                    >
                        <ListItemIcon sx={{ minWidth: 32, color: 'inherit' }}>
                            {item.icon}
                        </ListItemIcon>
                        <ListItemText
                            primary={item.label}
                            primaryTypographyProps={{ variant: 'body2' }}
                        />
                    </ListItemButton>
                ))}
            </List>

            <Divider sx={{ mt: 0.5 }} />

            {/* Projects section */}
            <Box sx={{ flexGrow: 1, overflowY: 'auto', py: 0.5 }}>
                <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ px: 2, py: 0.5, display: 'block', textTransform: 'uppercase', letterSpacing: 0.8 }}
                >
                    Projects
                </Typography>

                {projectsLoading ? (
                    <Box sx={{ px: 2 }}>
                        {[1, 2, 3].map((i) => (
                            <Skeleton key={i} variant="rounded" height={32} sx={{ mb: 0.5 }} />
                        ))}
                    </Box>
                ) : projects.length === 0 ? (
                    <Typography variant="caption" color="text.secondary" sx={{ px: 2 }}>
                        No projects yet
                    </Typography>
                ) : (
                    <List dense disablePadding>
                        {projects.map((project) => (
                            <ListItemButton
                                key={project.id}
                                selected={activeProjectId === project.id}
                                onClick={() => onProjectSelect?.(project)}
                                sx={{ borderRadius: 1, mx: 0.5, my: 0.25 }}
                            >
                                <ListItemIcon sx={{ minWidth: 32, color: 'inherit' }}>
                                    <ProjectIcon size={16} />
                                </ListItemIcon>
                                <ListItemText
                                    primary={project.name}
                                    primaryTypographyProps={{
                                        variant: 'body2',
                                        noWrap: true,
                                        title: project.name,
                                    }}
                                />
                            </ListItemButton>
                        ))}
                    </List>
                )}
            </Box>
        </Box>
    );

    return (
        <Box component="nav" sx={{ width: { sm: drawerWidth }, flexShrink: { sm: 0 } }}>
            {/* Mobile temporary drawer */}
            <Drawer
                variant="temporary"
                open={open}
                onClose={onClose}
                ModalProps={{ keepMounted: true }}
                sx={{
                    display: { xs: 'block', sm: 'none' },
                    '& .MuiDrawer-paper': { width: drawerWidth, boxSizing: 'border-box' },
                }}
            >
                {drawerContent}
            </Drawer>

            {/* Desktop permanent drawer */}
            <Drawer
                variant={variant === 'temporary' ? 'temporary' : 'permanent'}
                open={variant === 'temporary' ? open : true}
                sx={{
                    display: { xs: 'none', sm: 'block' },
                    '& .MuiDrawer-paper': {
                        width: drawerWidth,
                        boxSizing: 'border-box',
                        borderRight: 1,
                        borderColor: 'divider',
                    },
                }}
            >
                {drawerContent}
            </Drawer>
        </Box>
    );
};
