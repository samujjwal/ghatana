import React from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import {
  AppBar,
  Box,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import Avatar from '@mui/material/Avatar';
import ListSubheader from '@mui/material/ListSubheader';
import {
  Dashboard as DashboardIcon,
  Timeline as MetricsIcon,
  ListAlt as EventsIcon,
  Terminal as CommandsIcon,
  SmartToy as CopilotIcon,
  Settings as SettingsIcon,
  Security as PoliciesIcon,
  ManageHistory as ControlIcon,
  BugReport as DiagnosticsIcon,
  Assessment as AuditIcon,
  NotificationsActive as NotificationsIcon,
  Description as ReportsIcon,
  Menu as MenuIcon,
} from '@mui/icons-material';
import StatusBadge from '../common/StatusBadge';
import { useDashboardData } from '../../hooks/useDashboardData';

const drawerWidth = 240;

const navSections = [
  {
    key: 'monitor',
    label: 'Monitor',
    items: [
      { name: 'Dashboard', icon: <DashboardIcon />, path: '/dashboard' },
      { name: 'Metrics', icon: <MetricsIcon />, path: '/metrics' },
      { name: 'Events', icon: <EventsIcon />, path: '/events' },
      { name: 'Diagnostics', icon: <DiagnosticsIcon />, path: '/diagnostics' },
      { name: 'Reports', icon: <ReportsIcon />, path: '/reports' },
    ],
  },
  {
    key: 'control',
    label: 'Control',
    items: [
      { name: 'Commands', icon: <CommandsIcon />, path: '/commands' },
      { name: 'Control Hub', icon: <ControlIcon />, path: '/control' },
      { name: 'Audit Log', icon: <AuditIcon />, path: '/audit' },
    ],
  },
  {
    key: 'assist',
    label: 'Assist',
    items: [{ name: 'Copilot', icon: <CopilotIcon />, path: '/copilot' }],
  },
  {
    key: 'configure',
    label: 'Configure',
    items: [
      { name: 'Policies', icon: <PoliciesIcon />, path: '/policies' },
      { name: 'Settings', icon: <SettingsIcon />, path: '/settings' },
    ],
  },
];

export default function MainLayout() {
  const theme = useTheme();
  const isLargeUp = useMediaQuery(theme.breakpoints.up('lg'));
  const { data: overview } = useDashboardData();
  const [mobileOpen, setMobileOpen] = React.useState(false);

  const handleDrawerToggle = () => {
    setMobileOpen((prev) => !prev);
  };

  const closeDrawer = () => {
    if (!isLargeUp) {
      setMobileOpen(false);
    }
  };

  const drawerContent = (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
      }}
    >
      <Toolbar
        sx={{
          px: 2,
          borderBottom: '1px solid',
          borderColor: 'rgba(148, 163, 184, 0.12)',
          minHeight: 72,
        }}
      >
        <Stack direction="row" alignItems="center" gap={1.5}>
          <Avatar
            sx={{
              bgcolor: theme.palette.primary.main,
              color: theme.palette.primary.contrastText,
              width: 40,
              height: 40,
              fontWeight: 700,
              letterSpacing: 0.5,
            }}
          >
            DC
          </Avatar>
          <Box>
            <Typography variant="subtitle1">DCMAAR Desktop</Typography>
            <Typography variant="caption" color="text.secondary">
              Operations Console
            </Typography>
          </Box>
        </Stack>
      </Toolbar>

      <List
        sx={{
          flexGrow: 1,
          px: 1,
          '& .MuiListSubheader-root': {
            color: 'text.secondary',
            letterSpacing: 0.6,
            fontSize: 12,
          },
        }}
      >
        {navSections.map((section) => (
          <Box key={section.key} component="nav" sx={{ mt: 2 }}>
            <ListSubheader disableSticky sx={{ lineHeight: 1.6 }}>
              {section.label}
            </ListSubheader>
            {section.items.map((item) => (
              <ListItemButton
                key={item.name}
                component={NavLink}
                to={item.path}
                sx={{
                  borderRadius: 2,
                  color: theme.palette.text.secondary,
                  mb: 0.5,
                  '&.active, &:hover': {
                    backgroundColor: 'rgba(56, 189, 248, 0.12)',
                    color: theme.palette.primary.main,
                  },
                }}
                onClick={closeDrawer}
              >
                <ListItemIcon
                  sx={{
                    minWidth: 36,
                    color: 'inherit',
                  }}
                >
                  {item.icon}
                </ListItemIcon>
                <ListItemText
                  primary={item.name}
                  primaryTypographyProps={{ fontWeight: 600, fontSize: 14 }}
                />
              </ListItemButton>
            ))}
          </Box>
        ))}
      </List>

      <Box
        sx={{
          px: 2,
          py: 2,
          borderTop: '1px solid',
          borderColor: 'rgba(148, 163, 184, 0.12)',
        }}
      >
        <Typography variant="caption" color="text.secondary">
          © {new Date().getFullYear()} DCMAAR
        </Typography>
      </Box>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <Drawer
        variant="temporary"
        open={mobileOpen}
        onClose={handleDrawerToggle}
        ModalProps={{ keepMounted: true }}
        sx={{
          display: { xs: 'block', lg: 'none' },
          '& .MuiDrawer-paper': {
            width: drawerWidth,
            boxSizing: 'border-box',
            backgroundColor: theme.palette.background.paper,
            borderRight: '1px solid',
            borderColor: 'rgba(148, 163, 184, 0.18)',
          },
        }}
      >
        {drawerContent}
      </Drawer>

      <Drawer
        variant="permanent"
        open
        sx={{
          display: { xs: 'none', lg: 'block' },
          '& .MuiDrawer-paper': {
            width: drawerWidth,
            boxSizing: 'border-box',
            backgroundColor: theme.palette.background.paper,
            borderRight: '1px solid',
            borderColor: 'rgba(148, 163, 184, 0.18)',
          },
        }}
      >
        {drawerContent}
      </Drawer>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          width: '100%',
          ml: { lg: `${drawerWidth}px` },
          bgcolor: theme.palette.background.default,
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <AppBar
          position="sticky"
          elevation={0}
          sx={{
            background: 'linear-gradient(90deg, rgba(30,41,59,0.9), rgba(15,23,42,0.9))',
            borderBottom: '1px solid rgba(148, 163, 184, 0.16)',
          }}
        >
          <Toolbar sx={{ minHeight: 72, px: { xs: 2, lg: 4 } }}>
            {!isLargeUp ? (
              <IconButton
                aria-label="Open navigation"
                onClick={handleDrawerToggle}
                edge="start"
                sx={{ mr: 2 }}
                color="inherit"
              >
                <MenuIcon />
              </IconButton>
            ) : null}
            <Stack direction="row" alignItems="center" spacing={2} flexGrow={1}>
              <Stack spacing={0.5}>
                <Typography variant="h6" fontWeight={600}>
                  Unified Operations
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Monitor, remediate, and govern from a single console.
                </Typography>
              </Stack>

              <Divider
                orientation="vertical"
                flexItem
                sx={{ borderColor: 'rgba(148, 163, 184, 0.2)', display: { xs: 'none', md: 'block' } }}
              />

              <Stack direction="row" spacing={2} alignItems="center" sx={{ display: { xs: 'none', md: 'flex' } }}>
                <Tooltip title="Agent Bridge">
                  <Box>
                    <StatusBadge status={overview?.agent.connected ? 'healthy' : 'disconnected'} />
                  </Box>
                </Tooltip>
                <Tooltip title="Extension Bridge">
                  <Box>
                    <StatusBadge
                      status={overview?.extension.connected ? 'active' : 'disconnected'}
                      label={`EXT ${overview?.extension.connected ? 'OK' : 'OFFLINE'}`}
                    />
                  </Box>
                </Tooltip>
                <Tooltip title="Unacknowledged alerts">
                  <Box
                    sx={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 1,
                      px: 2,
                      py: 1,
                      borderRadius: 999,
                      backgroundColor: 'rgba(56,189,248,0.12)',
                    }}
                  >
                    <NotificationsIcon fontSize="small" />
                    <Typography variant="body2" fontWeight={600}>
                      {overview?.events.length ?? 0}
                    </Typography>
                  </Box>
                </Tooltip>
              </Stack>

              <Divider
                orientation="vertical"
                flexItem
                sx={{ borderColor: 'rgba(148, 163, 184, 0.2)', display: { xs: 'none', md: 'block' } }}
              />

              <Stack direction="row" spacing={1.5} alignItems="center">
                <Box textAlign="right" sx={{ display: { xs: 'none', sm: 'block' } }}>
                  <Typography variant="subtitle2">Anna Ops</Typography>
                  <Typography variant="caption" color="text.secondary">
                    Ops Administrator
                  </Typography>
                </Box>
                <Avatar
                  sx={{
                    width: 40,
                    height: 40,
                    bgcolor: theme.palette.custom?.main || theme.palette.secondary.main,
                    color: theme.palette.getContrastText(
                      theme.palette.custom?.main || theme.palette.secondary.main,
                    ),
                    fontWeight: 700,
                  }}
                >
                  AO
                </Avatar>
              </Stack>
            </Stack>
          </Toolbar>
        </AppBar>

        <Box component="section" sx={{ px: { xs: 2, lg: 4 }, py: 4, flexGrow: 1 }}>
          <Outlet />
        </Box>
      </Box>
    </Box>
  );
}
