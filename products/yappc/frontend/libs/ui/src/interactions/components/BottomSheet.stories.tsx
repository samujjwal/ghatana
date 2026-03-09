import { Share2 as ShareIcon, Heart as FavoriteIcon, BookmarkBorder as BookmarkIcon, Report as ReportIcon } from 'lucide-react';
import { Box, Typography, InteractiveList as List, ListItem, ListItemText, Switch, Slider, Divider } from '@ghatana/ui';
import { useState } from 'react';

import { BottomSheet } from './BottomSheet';
import { Button } from '../../components/Button';

import type { Meta, StoryObj } from '@storybook/react';


const meta = {
    title: 'Interactions/BottomSheet',
    component: BottomSheet,
    parameters: {
        layout: 'fullscreen',
        docs: {
            description: {
                component: 'A mobile-optimized bottom sheet component with snap points and swipe gestures. Built with Framer Motion for smooth animations.',
            },
        },
    },
    tags: ['autodocs'],
} satisfies Meta<typeof BottomSheet>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

// Interactive wrapper for stories
const BottomSheetDemo = (props: Partial<React.ComponentProps<typeof BottomSheet>>) => {
    const [open, setOpen] = useState(false);

    return (
        <Box className="p-8 h-screen">
            <Button onClick={() => setOpen(true)} variant="solid">
                Open Bottom Sheet
            </Button>
            <BottomSheet open={open} onClose={() => setOpen(false)} {...props} />
        </Box>
    );
};

// Default content
const DefaultContent = () => (
    <Box className="p-6">
        <Typography as="h6" gutterBottom>
            Bottom Sheet Content
        </Typography>
        <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
            Swipe down to close or drag to snap points
        </Typography>
        <List>
            <ListItem button>
                <ShareIcon className="mr-4" />
                <ListItemText primary="Share" secondary="Share this content" />
            </ListItem>
            <ListItem button>
                <FavoriteIcon className="mr-4" />
                <ListItemText primary="Favorite" secondary="Add to favorites" />
            </ListItem>
            <ListItem button>
                <BookmarkIcon className="mr-4" />
                <ListItemText primary="Bookmark" secondary="Save for later" />
            </ListItem>
            <ListItem button>
                <ReportIcon className="mr-4" />
                <ListItemText primary="Report" secondary="Report an issue" />
            </ListItem>
        </List>
    </Box>
);

/**
 * Basic bottom sheet with default snap points (50%, 100%)
 */
export const Basic: Story = {
    render: () => <BottomSheetDemo><DefaultContent /></BottomSheetDemo>,
};

/**
 * Single snap point (full height)
 */
export const SingleSnapPoint: Story = {
    render: () => (
        <BottomSheetDemo snapPoints={['100%']}>
            <DefaultContent />
        </BottomSheetDemo>
    ),
};

/**
 * Three snap points (25%, 50%, 100%)
 */
export const ThreeSnapPoints: Story = {
    render: () => (
        <BottomSheetDemo snapPoints={['25%', '50%', '100%']}>
            <Box className="p-6">
                <Typography as="h6" gutterBottom>
                    Three Snap Points
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                    Drag to snap to 25%, 50%, or 100% of screen height
                </Typography>
                <DefaultContent />
            </Box>
        </BottomSheetDemo>
    ),
};

/**
 * Custom snap points with pixels
 */
export const PixelSnapPoints: Story = {
    render: () => (
        <BottomSheetDemo snapPoints={[200, 400, 600]}>
            <Box className="p-6">
                <Typography as="h6" gutterBottom>
                    Pixel-based Snap Points
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                    Snaps to 200px, 400px, and 600px heights
                </Typography>
                <DefaultContent />
            </Box>
        </BottomSheetDemo>
    ),
};

/**
 * Fraction-based snap points (0.25, 0.5, 1.0)
 */
export const FractionSnapPoints: Story = {
    render: () => (
        <BottomSheetDemo snapPoints={[0.25, 0.5, 1.0]}>
            <Box className="p-6">
                <Typography as="h6" gutterBottom>
                    Fraction-based Snap Points
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                    Snaps to 25%, 50%, and 100% using fractions
                </Typography>
                <DefaultContent />
            </Box>
        </BottomSheetDemo>
    ),
};

/**
 * Default snap point (opens at middle snap)
 */
export const DefaultSnapMiddle: Story = {
    render: () => (
        <BottomSheetDemo snapPoints={['25%', '50%', '100%']} defaultSnap={1}>
            <Box className="p-6">
                <Typography as="h6" gutterBottom>
                    Default Middle Snap
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                    Opens at 50% (index 1)
                </Typography>
                <DefaultContent />
            </Box>
        </BottomSheetDemo>
    ),
};

/**
 * Default snap point (opens at full height)
 */
export const DefaultSnapFull: Story = {
    render: () => (
        <BottomSheetDemo snapPoints={['25%', '50%', '100%']} defaultSnap={2}>
            <Box className="p-6">
                <Typography as="h6" gutterBottom>
                    Default Full Height
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                    Opens at 100% (index 2)
                </Typography>
                <DefaultContent />
            </Box>
        </BottomSheetDemo>
    ),
};

/**
 * With drag handle (default)
 */
export const WithDragHandle: Story = {
    render: () => (
        <BottomSheetDemo showHandle={true}>
            <Box className="p-6">
                <Typography as="h6" gutterBottom>
                    With Drag Handle
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                    Visual indicator for dragging
                </Typography>
                <DefaultContent />
            </Box>
        </BottomSheetDemo>
    ),
};

/**
 * Without drag handle
 */
export const WithoutDragHandle: Story = {
    render: () => (
        <BottomSheetDemo showHandle={false}>
            <Box className="p-6">
                <Typography as="h6" gutterBottom>
                    No Drag Handle
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                    Can still drag from anywhere in the sheet
                </Typography>
                <DefaultContent />
            </Box>
        </BottomSheetDemo>
    ),
};

/**
 * Disable swipe to close
 */
export const DisableSwipeToClose: Story = {
    render: () => (
        <BottomSheetDemo swipeToClose={false}>
            <Box className="p-6">
                <Typography as="h6" gutterBottom>
                    No Swipe to Close
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                    Must use the backdrop or a button to close
                </Typography>
                <DefaultContent />
            </Box>
        </BottomSheetDemo>
    ),
};

/**
 * Disable backdrop click to close
 */
export const DisableBackdropClick: Story = {
    render: () => (
        <BottomSheetDemo closeOnBackdrop={false}>
            <Box className="p-6">
                <Typography as="h6" gutterBottom>
                    No Backdrop Close
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                    Clicking the backdrop won't close this sheet
                </Typography>
                <DefaultContent />
            </Box>
        </BottomSheetDemo>
    ),
};

/**
 * No backdrop overlay
 */
export const NoBackdrop: Story = {
    render: () => (
        <BottomSheetDemo showBackdrop={false}>
            <Box className="p-6">
                <Typography as="h6" gutterBottom>
                    No Backdrop
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                    Bottom sheet without backdrop overlay
                </Typography>
                <DefaultContent />
            </Box>
        </BottomSheetDemo>
    ),
};

/**
 * With snap point change callback
 */
export const WithSnapCallback: Story = {
    render: () => {
        const [open, setOpen] = useState(false);
        const [currentSnap, setCurrentSnap] = useState(0);

        return (
            <Box className="p-8">
                <Button onClick={() => setOpen(true)} variant="solid">
                    Open Bottom Sheet
                </Button>
                <Typography as="p" className="text-sm" color="text.secondary" className="mt-4">
                    Current snap point: {currentSnap} ({['25%', '50%', '100%'][currentSnap]})
                </Typography>
                <BottomSheet
                    open={open}
                    onClose={() => setOpen(false)}
                    snapPoints={['25%', '50%', '100%']}
                    onSnapPointChange={(index) => setCurrentSnap(index)}
                >
                    <Box className="p-6">
                        <Typography as="h6" gutterBottom>
                            Snap Point Tracking
                        </Typography>
                        <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                            Current snap: {currentSnap} ({['25%', '50%', '100%'][currentSnap]})
                        </Typography>
                        <DefaultContent />
                    </Box>
                </BottomSheet>
            </Box>
        );
    },
};

/**
 * Settings panel example
 */
export const SettingsPanel: Story = {
    render: () => {
        const [open, setOpen] = useState(false);
        const [notifications, setNotifications] = useState(true);
        const [volume, setVolume] = useState(50);

        return (
            <Box className="p-8">
                <Button onClick={() => setOpen(true)} variant="solid">
                    Open Settings
                </Button>
                <BottomSheet
                    open={open}
                    onClose={() => setOpen(false)}
                    snapPoints={['50%', '100%']}
                >
                    <Box className="p-6">
                        <Typography as="h6" gutterBottom>
                            Settings
                        </Typography>

                        <List>
                            <ListItem>
                                <ListItemText
                                    primary="Notifications"
                                    secondary="Enable push notifications"
                                />
                                <Switch
                                    checked={notifications}
                                    onChange={(e) => setNotifications(e.target.checked)}
                                />
                            </ListItem>

                            <Divider />

                            <ListItem>
                                <Box className="w-full">
                                    <Typography gutterBottom>
                                        Volume: {volume}%
                                    </Typography>
                                    <Slider
                                        value={volume}
                                        onChange={(_, value) => setVolume(value as number)}
                                        valueLabelDisplay="auto"
                                    />
                                </Box>
                            </ListItem>
                        </List>

                        <Box className="mt-4 flex gap-2">
                            <Button variant="solid" fullWidth onClick={() => setOpen(false)}>
                                Save
                            </Button>
                            <Button variant="outlined" fullWidth onClick={() => setOpen(false)}>
                                Cancel
                            </Button>
                        </Box>
                    </Box>
                </BottomSheet>
            </Box>
        );
    },
};

/**
 * Scrollable content
 */
export const ScrollableContent: Story = {
    render: () => (
        <BottomSheetDemo snapPoints={['50%', '100%']}>
            <Box className="p-6 overflow-auto max-h-[80vh]" >
                <Typography as="h6" gutterBottom>
                    Scrollable Content
                </Typography>
                <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                    Long content that scrolls within the bottom sheet
                </Typography>

                {Array.from({ length: 20 }, (_, i) => (
                    <ListItem key={i} button>
                        <ListItemText
                            primary={`Item ${i + 1}`}
                            secondary={`Description for item ${i + 1}`}
                        />
                    </ListItem>
                ))}
            </Box>
        </BottomSheetDemo>
    ),
};

/**
 * Action sheet example (iOS style)
 */
export const ActionSheet: Story = {
    render: () => {
        const [open, setOpen] = useState(false);

        const handleAction = (action: string) => {
            console.log('Action:', action);
            setOpen(false);
        };

        return (
            <Box className="p-8">
                <Button onClick={() => setOpen(true)} variant="solid">
                    Show Actions
                </Button>
                <BottomSheet
                    open={open}
                    onClose={() => setOpen(false)}
                    snapPoints={['auto']}
                    showHandle={false}
                >
                    <Box className="pb-4">
                        <List className="py-0">
                            <ListItem button onClick={() => handleAction('camera')}>
                                <ListItemText
                                    primary="Take Photo"
                                    primaryTypographyProps={{ align: 'center' }}
                                />
                            </ListItem>
                            <Divider />
                            <ListItem button onClick={() => handleAction('library')}>
                                <ListItemText
                                    primary="Choose from Library"
                                    primaryTypographyProps={{ align: 'center' }}
                                />
                            </ListItem>
                            <Divider />
                            <ListItem button onClick={() => handleAction('delete')} className="text-red-600">
                                <ListItemText
                                    primary="Delete Photo"
                                    primaryTypographyProps={{ align: 'center', color: 'error' }}
                                />
                            </ListItem>
                        </List>

                        <Box className="h-[8px] bg-gray-200 dark:bg-gray-700" />

                        <List className="py-0">
                            <ListItem button onClick={() => setOpen(false)}>
                                <ListItemText
                                    primary="Cancel"
                                    primaryTypographyProps={{ align: 'center', fontWeight: 600 }}
                                />
                            </ListItem>
                        </List>
                    </Box>
                </BottomSheet>
            </Box>
        );
    },
};

/**
 * Filter sheet example
 */
export const FilterSheet: Story = {
    render: () => {
        const [open, setOpen] = useState(false);
        const [priceRange, setPriceRange] = useState<number[]>([20, 80]);

        return (
            <Box className="p-8">
                <Button onClick={() => setOpen(true)} variant="solid">
                    Filters
                </Button>
                <BottomSheet
                    open={open}
                    onClose={() => setOpen(false)}
                    snapPoints={['60%', '100%']}
                >
                    <Box className="p-6">
                        <Typography as="h6" gutterBottom>
                            Filters
                        </Typography>

                        <Box className="mb-6">
                            <Typography gutterBottom>
                                Price Range: ${priceRange[0]} - ${priceRange[1]}
                            </Typography>
                            <Slider
                                value={priceRange}
                                onChange={(_, value) => setPriceRange(value as number[])}
                                valueLabelDisplay="auto"
                                valueLabelFormat={(value) => `$${value}`}
                            />
                        </Box>

                        <Typography as="p" className="text-sm font-medium" gutterBottom>
                            Category
                        </Typography>
                        <List>
                            {['Electronics', 'Clothing', 'Books', 'Home & Garden'].map((category) => (
                                <ListItem key={category} button>
                                    <ListItemText primary={category} />
                                </ListItem>
                            ))}
                        </List>

                        <Box className="mt-4 flex gap-2">
                            <Button variant="solid" fullWidth onClick={() => setOpen(false)}>
                                Apply Filters
                            </Button>
                            <Button variant="outlined" fullWidth onClick={() => setOpen(false)}>
                                Reset
                            </Button>
                        </Box>
                    </Box>
                </BottomSheet>
            </Box>
        );
    },
};
