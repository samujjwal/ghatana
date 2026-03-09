import { Home as HomeIcon, Settings as SettingsIcon, User as PersonIcon, Bell as NotificationsIcon, Mail as EmailIcon, HelpCircle as HelpIcon } from 'lucide-react';
import { Box, Typography, InteractiveList as List, ListItem, ListItemText, Divider } from '@ghatana/ui';
import { useState } from 'react';

import { InteractionDrawer } from './Drawer';
import { Button } from '../../components/Button';

import type { Meta, StoryObj } from '@storybook/react';


const meta = {
    title: 'Interactions/Drawer',
    component: InteractionDrawer,
    parameters: {
        layout: 'fullscreen',
        docs: {
            description: {
                component: 'A side panel drawer component with multiple anchors, variants, and animations. Built with Framer Motion for smooth transitions.',
            },
        },
    },
    tags: ['autodocs'],
} satisfies Meta<typeof InteractionDrawer>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

// Interactive wrapper for stories
const DrawerDemo = (props: Partial<React.ComponentProps<typeof InteractionDrawer>>) => {
    const [open, setOpen] = useState(false);

    return (
        <Box className="p-8">
            <Button onClick={() => setOpen(true)} variant="solid">
                Open Drawer
            </Button>
            <InteractionDrawer open={open} onClose={() => setOpen(false)} {...props} />
        </Box>
    );
};

// Default drawer content
const DrawerContent = () => (
    <Box className="p-4 w-[280px]">
        <Typography as="h6" gutterBottom>
            Navigation Menu
        </Typography>
        <Divider className="mb-4" />
        <List>
            <ListItem>
                <HomeIcon className="mr-4" />
                <ListItemText primary="Home" />
            </ListItem>
            <ListItem>
                <PersonIcon className="mr-4" />
                <ListItemText primary="Profile" />
            </ListItem>
            <ListItem>
                <SettingsIcon className="mr-4" />
                <ListItemText primary="Settings" />
            </ListItem>
            <ListItem>
                <NotificationsIcon className="mr-4" />
                <ListItemText primary="Notifications" />
            </ListItem>
            <ListItem>
                <EmailIcon className="mr-4" />
                <ListItemText primary="Messages" />
            </ListItem>
            <ListItem>
                <HelpIcon className="mr-4" />
                <ListItemText primary="Help" />
            </ListItem>
        </List>
    </Box>
);

/**
 * Basic drawer with default settings (left anchor, temporary variant)
 */
export const Basic: Story = {
    render: () => <DrawerDemo><DrawerContent /></DrawerDemo>,
};

/**
 * Left-anchored drawer (default)
 */
export const LeftAnchor: Story = {
    render: () => (
        <DrawerDemo anchor="left">
            <DrawerContent />
        </DrawerDemo>
    ),
};

/**
 * Right-anchored drawer
 */
export const RightAnchor: Story = {
    render: () => (
        <DrawerDemo anchor="right">
            <DrawerContent />
        </DrawerDemo>
    ),
};

/**
 * Top-anchored drawer (full width)
 */
export const TopAnchor: Story = {
    render: () => (
        <DrawerDemo anchor="top">
            <Box className="p-6 w-full">
                <Typography as="h6">Top Notification Bar</Typography>
                <Typography as="p" className="text-sm" color="text.secondary" className="mt-2">
                    This is a full-width drawer anchored to the top. Useful for notifications or banners.
                </Typography>
            </Box>
        </DrawerDemo>
    ),
};

/**
 * Bottom-anchored drawer (full width)
 */
export const BottomAnchor: Story = {
    render: () => (
        <DrawerDemo anchor="bottom">
            <Box className="p-6 w-full">
                <Typography as="h6">Action Sheet</Typography>
                <Typography as="p" className="text-sm" color="text.secondary" className="mt-2">
                    This is a full-width drawer anchored to the bottom. Useful for action sheets or filters.
                </Typography>
            </Box>
        </DrawerDemo>
    ),
};

/**
 * Temporary variant (default) - closes on backdrop click and escape key
 */
export const TemporaryVariant: Story = {
    render: () => (
        <DrawerDemo variant="temporary">
            <Box className="p-6 w-[280px]">
                <Typography as="h6" gutterBottom>
                    Temporary Drawer
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary">
                    Closes when you click outside or press Escape.
                </Typography>
                <DrawerContent />
            </Box>
        </DrawerDemo>
    ),
};

/**
 * Persistent variant - stays open until explicitly closed
 */
export const PersistentVariant: Story = {
    render: () => (
        <DrawerDemo variant="persistent">
            <Box className="p-6 w-[280px]">
                <Typography as="h6" gutterBottom>
                    Persistent Drawer
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary">
                    Stays open until you explicitly close it. No backdrop click or escape.
                </Typography>
                <DrawerContent />
            </Box>
        </DrawerDemo>
    ),
};

/**
 * Permanent variant - always visible (typically controlled externally)
 */
export const PermanentVariant: Story = {
    render: () => {
        const [open, setOpen] = useState(true);

        return (
            <Box className="flex h-screen">
                <InteractionDrawer open={open} onClose={() => { }} variant="permanent" anchor="left">
                    <Box className="p-4 w-[280px]">
                        <Typography as="h6" gutterBottom>
                            Permanent Drawer
                        </Typography>
                        <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                            Always visible. Toggle with button.
                        </Typography>
                        <Button onClick={() => setOpen(!open)} size="sm">
                            {open ? 'Collapse' : 'Expand'}
                        </Button>
                        {open && <DrawerContent />}
                    </Box>
                </InteractionDrawer>
                <Box className="grow p-6">
                    <Typography as="h4">Main Content</Typography>
                    <Typography as="p" className="mt-4">
                        The permanent drawer pushes the content to the side.
                    </Typography>
                </Box>
            </Box>
        );
    },
};

/**
 * Custom width drawer
 */
export const CustomWidth: Story = {
    render: () => (
        <DrawerDemo width={400}>
            <Box className="p-6">
                <Typography as="h6" gutterBottom>
                    Wide Drawer (400px)
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary">
                    Custom width can be specified for left/right anchored drawers.
                </Typography>
                <DrawerContent />
            </Box>
        </DrawerDemo>
    ),
};

/**
 * Custom height drawer (top/bottom anchors)
 */
export const CustomHeight: Story = {
    render: () => (
        <DrawerDemo anchor="bottom" height={300}>
            <Box className="p-6 w-full">
                <Typography as="h6" gutterBottom>
                    Tall Drawer (300px)
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary">
                    Custom height can be specified for top/bottom anchored drawers.
                </Typography>
            </Box>
        </DrawerDemo>
    ),
};

/**
 * Disable backdrop click to close
 */
export const DisableBackdropClick: Story = {
    render: () => (
        <DrawerDemo closeOnBackdrop={false}>
            <Box className="p-6 w-[280px]">
                <Typography as="h6" gutterBottom>
                    No Backdrop Close
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary">
                    Clicking the backdrop won't close this drawer. Use the close button.
                </Typography>
                <DrawerContent />
            </Box>
        </DrawerDemo>
    ),
};

/**
 * Disable escape key to close
 */
export const DisableEscapeKey: Story = {
    render: () => (
        <DrawerDemo closeOnEscape={false}>
            <Box className="p-6 w-[280px]">
                <Typography as="h6" gutterBottom>
                    No Escape Close
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary">
                    Pressing Escape won't close this drawer. Use the close button.
                </Typography>
                <DrawerContent />
            </Box>
        </DrawerDemo>
    ),
};

/**
 * No backdrop overlay
 */
export const NoBackdrop: Story = {
    render: () => (
        <DrawerDemo showBackdrop={false}>
            <Box className="p-6 w-[280px]">
                <Typography as="h6" gutterBottom>
                    No Backdrop
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary">
                    Drawer without a backdrop overlay.
                </Typography>
                <DrawerContent />
            </Box>
        </DrawerDemo>
    ),
};

/**
 * With custom backdrop styles
 */
export const CustomBackdrop: Story = {
    render: () => (
        <DrawerDemo
            backdropProps={{
                sx: {
                    backgroundColor: 'rgba(255, 0, 0, 0.2)',
                    backdropFilter: 'blur(4px)',
                },
            }}
        >
            <Box className="p-6 w-[280px]">
                <Typography as="h6" gutterBottom>
                    Custom Backdrop
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary">
                    Red tinted backdrop with blur effect.
                </Typography>
                <DrawerContent />
            </Box>
        </DrawerDemo>
    ),
};

/**
 * With elevation (shadow depth)
 */
export const WithElevation: Story = {
    render: () => (
        <DrawerDemo elevation={16}>
            <Box className="p-6 w-[280px]">
                <Typography as="h6" gutterBottom>
                    High Elevation
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary">
                    Drawer with elevation={'{16}'} for a dramatic shadow.
                </Typography>
                <DrawerContent />
            </Box>
        </DrawerDemo>
    ),
};

/**
 * Complex drawer with header and footer
 */
export const ComplexLayout: Story = {
    render: () => {
        const [open, setOpen] = useState(false);

        return (
            <Box className="p-8">
                <Button onClick={() => setOpen(true)} variant="solid">
                    Open Complex Drawer
                </Button>
                <InteractionDrawer open={open} onClose={() => setOpen(false)} width={320}>
                    <Box className="flex flex-col h-full">
                        {/* Header */}
                        <Box className="p-4 border-gray-200 dark:border-gray-700 border-b" >
                            <Typography as="h6">Settings</Typography>
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                Manage your preferences
                            </Typography>
                        </Box>

                        {/* Content */}
                        <Box className="grow overflow-auto p-4">
                            <DrawerContent />
                        </Box>

                        {/* Footer */}
                        <Box className="p-4 flex gap-2 border-gray-200 dark:border-gray-700 border-t" >
                            <Button variant="solid" fullWidth onClick={() => setOpen(false)}>
                                Save Changes
                            </Button>
                            <Button variant="outlined" fullWidth onClick={() => setOpen(false)}>
                                Cancel
                            </Button>
                        </Box>
                    </Box>
                </InteractionDrawer>
            </Box>
        );
    },
};

/**
 * Responsive drawer (changes behavior based on screen size)
 */
export const ResponsiveDrawer: Story = {
    render: () => {
        const [open, setOpen] = useState(false);

        return (
            <Box className="p-8">
                <Button onClick={() => setOpen(true)} variant="solid">
                    Open Responsive Drawer
                </Button>
                <Typography as="p" className="text-sm" color="text.secondary" className="mt-4">
                    Try resizing the window to see different behaviors
                </Typography>
                <InteractionDrawer
                    open={open}
                    onClose={() => setOpen(false)}
                    variant={{ xs: 'temporary', sm: 'temporary', md: 'persistent' } as unknown}
                >
                    <Box className="p-6 w-[280px]">
                        <Typography as="h6" gutterBottom>
                            Responsive Drawer
                        </Typography>
                        <Typography as="p" className="text-sm" color="text.secondary">
                            Temporary on mobile, persistent on desktop
                        </Typography>
                        <DrawerContent />
                    </Box>
                </InteractionDrawer>
            </Box>
        );
    },
};
