/**
 * Mobile Settings Route
 * 
 * App settings and preferences for mobile.
 * Account, notifications, and app configuration.
 * 
 * @doc.type route
 * @doc.purpose Mobile settings interface
 * @doc.layer product
 */

import React, { useState } from 'react';
import { useNavigate } from 'react-router';
import { Capacitor } from '@capacitor/core';
import { useIsDarkMode } from '@ghatana/theme';
import {
  Box,
  Typography,
  ListItem,
  ListItemText,
  Switch,
  Card,
  Button,
  IconButton,
  Divider,
  Avatar,
  InteractiveList as List,
} from '@ghatana/ui';
import { ListItemSecondaryAction } from '@ghatana/ui';
import { User as Person, Bell as Notifications, Palette, Globe as Language, Shield as Security, Info, ChevronRight, LogOut as ExitToApp, CloudCog as CloudSync, Vibrate as Vibration } from 'lucide-react';

/**
 * Mobile Settings Component
 */
export default function MobileSettingsRoute() {
    const navigate = useNavigate();
    const isDarkMode = useIsDarkMode();
    const [settings, setSettings] = useState({
        notifications: true,
        haptics: true,
        darkMode: isDarkMode,
        offlineMode: false,
        autoSync: true,
    });

    const isNative = Capacitor && typeof Capacitor.isNativePlatform === 'function'
        ? Capacitor.isNativePlatform()
        : false;

    const handleToggle = (key: keyof typeof settings) => {
        setSettings(prev => ({ ...prev, [key]: !prev[key] }));
    };

    const handleLogout = () => {
        if (confirm('Are you sure you want to log out?')) {
            // NOTE: Implement logout logic
            navigate('/login');
        }
    };

    return (
        <Box>
            {/* User Profile Section */}
            <Card className="m-4">
                <Box className="p-4 flex items-center gap-4">
                    <Avatar className="w-[56px] h-[56px] bg-blue-600">
                        <Person />
                    </Avatar>
                    <Box className="flex-1">
                        <Typography as="h6">User Name</Typography>
                        <Typography as="p" className="text-sm" color="text.secondary">
                            user@example.com
                        </Typography>
                    </Box>
                    <IconButton>
                        <ChevronRight />
                    </IconButton>
                </Box>
            </Card>

            {/* General Settings */}
            <Box className="mx-4 mt-6 mb-4">
                <Typography as="p" className="text-sm font-medium" color="text.secondary" className="mb-2">
                    GENERAL
                </Typography>
            </Box>

            <Card className="mx-4">
                <List disablePadding>
                    <ListItem button>
                        <Palette className="mr-4 text-gray-600" />
                        <ListItemText
                            primary="Appearance"
                            secondary={settings.darkMode ? 'Dark' : 'Light'}
                        />
                        <ListItemSecondaryAction>
                            <Switch
                                edge="end"
                                checked={settings.darkMode}
                                onChange={() => handleToggle('darkMode')}
                            />
                        </ListItemSecondaryAction>
                    </ListItem>

                    <Divider />

                    <ListItem button>
                        <Language className="mr-4 text-gray-600" />
                        <ListItemText
                            primary="Language"
                            secondary="English"
                        />
                        <IconButton edge="end">
                            <ChevronRight />
                        </IconButton>
                    </ListItem>
                </List>
            </Card>

            {/* Notifications Settings */}
            <Box className="mx-4 mt-6 mb-4">
                <Typography as="p" className="text-sm font-medium" color="text.secondary" className="mb-2">
                    NOTIFICATIONS
                </Typography>
            </Box>

            <Card className="mx-4">
                <List disablePadding>
                    <ListItem>
                        <Notifications className="mr-4 text-gray-600" />
                        <ListItemText
                            primary="Push Notifications"
                            secondary="Receive build and deploy updates"
                        />
                        <ListItemSecondaryAction>
                            <Switch
                                edge="end"
                                checked={settings.notifications}
                                onChange={() => handleToggle('notifications')}
                            />
                        </ListItemSecondaryAction>
                    </ListItem>

                    {isNative && (
                        <>
                            <Divider />
                            <ListItem>
                                <Vibration className="mr-4 text-gray-600" />
                                <ListItemText
                                    primary="Haptic Feedback"
                                    secondary="Vibrate on interactions"
                                />
                                <ListItemSecondaryAction>
                                    <Switch
                                        edge="end"
                                        checked={settings.haptics}
                                        onChange={() => handleToggle('haptics')}
                                    />
                                </ListItemSecondaryAction>
                            </ListItem>
                        </>
                    )}
                </List>
            </Card>

            {/* Data & Storage */}
            <Box className="mx-4 mt-6 mb-4">
                <Typography as="p" className="text-sm font-medium" color="text.secondary" className="mb-2">
                    DATA & STORAGE
                </Typography>
            </Box>

            <Card className="mx-4">
                <List disablePadding>
                    <ListItem>
                        <CloudSync className="mr-4 text-gray-600" />
                        <ListItemText
                            primary="Auto Sync"
                            secondary="Sync when connected to WiFi"
                        />
                        <ListItemSecondaryAction>
                            <Switch
                                edge="end"
                                checked={settings.autoSync}
                                onChange={() => handleToggle('autoSync')}
                            />
                        </ListItemSecondaryAction>
                    </ListItem>

                    <Divider />

                    <ListItem button>
                        <ListItemText
                            primary="Offline Mode"
                            secondary="Work without internet connection"
                        />
                        <ListItemSecondaryAction>
                            <Switch
                                edge="end"
                                checked={settings.offlineMode}
                                onChange={() => handleToggle('offlineMode')}
                            />
                        </ListItemSecondaryAction>
                    </ListItem>
                </List>
            </Card>

            {/* Security & Privacy */}
            <Box className="mx-4 mt-6 mb-4">
                <Typography as="p" className="text-sm font-medium" color="text.secondary" className="mb-2">
                    SECURITY & PRIVACY
                </Typography>
            </Box>

            <Card className="mx-4">
                <List disablePadding>
                    <ListItem button>
                        <Security className="mr-4 text-gray-600" />
                        <ListItemText
                            primary="Privacy Policy"
                        />
                        <IconButton edge="end">
                            <ChevronRight />
                        </IconButton>
                    </ListItem>

                    <Divider />

                    <ListItem button>
                        <Info className="mr-4 text-gray-600" />
                        <ListItemText
                            primary="Terms of Service"
                        />
                        <IconButton edge="end">
                            <ChevronRight />
                        </IconButton>
                    </ListItem>
                </List>
            </Card>

            {/* About */}
            <Box className="mx-4 mt-6 mb-4">
                <Typography as="p" className="text-sm font-medium" color="text.secondary" className="mb-2">
                    ABOUT
                </Typography>
            </Box>

            <Card className="mx-4 mb-6">
                <List disablePadding>
                    <ListItem button>
                        <ListItemText
                            primary="Version"
                            secondary="1.0.0 (Build 100)"
                        />
                    </ListItem>

                    <Divider />

                    <ListItem button>
                        <ListItemText
                            primary="Check for Updates"
                        />
                        <IconButton edge="end">
                            <ChevronRight />
                        </IconButton>
                    </ListItem>
                </List>
            </Card>

            {/* Logout Button */}
            <Box className="mx-4 mb-6">
                <Button
                    variant="outlined"
                    tone="danger"
                    fullWidth
                    size="lg"
                    startIcon={<ExitToApp />}
                    onClick={handleLogout}
                >
                    Log Out
                </Button>
            </Box>

            {/* Footer */}
            <Box className="text-center pb-6 px-4">
                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                    © 2026 YAPPC. All rights reserved.
                </Typography>
            </Box>
        </Box>
    );
}
