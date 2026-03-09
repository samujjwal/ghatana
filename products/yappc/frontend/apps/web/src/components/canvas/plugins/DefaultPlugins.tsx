/**
 * Canvas Plugin System Examples
 * Shows how to create reusable plugins for canvas functionality
 */

import { Accessibility as AccessibilityIcon, MessageSquare as CommentIcon, History as HistoryIcon, Bug as ValidationIcon, X as CloseIcon } from 'lucide-react';
// Core UI components from @ghatana/yappc-ui
import {
    Box,
    Typography,
    List,
    ListItem,
    ListItemText,
    Chip,
    Alert,
    IconButton,
    Paper,
} from '@ghatana/ui';

import { Badge } from '@ghatana/ui';
import React from 'react';

import type { CanvasPlugin, BaseItem, CanvasAPI } from '../core/types';

// Accessibility Plugin - adds a11y checks and indicators
export const createAccessibilityPlugin = <TItem extends BaseItem>(): CanvasPlugin<TItem> => ({
    id: 'accessibility',
    name: 'Accessibility Tools',
    version: '1.0.0',

    // Add toolbar action for accessibility check
    toolbarActions: [{
        id: 'run-a11y-check',
        label: 'Check Accessibility',
        icon: <AccessibilityIcon />,
        onClick: (context) => {
            // Run accessibility checks on all items
            console.log('Running accessibility checks...', context);
            // In a real implementation, this would analyze items and show results
        }
    }],

    // Add context menu item for individual items
    contextMenuItems: [{
        id: 'check-item-a11y',
        label: 'Check A11y',
        icon: <AccessibilityIcon />,
        onClick: (item, context) => {
            console.log('Checking accessibility for item:', item.id);
            // Check individual item accessibility
        }
    }],

    // Add accessibility panel
    panels: [{
        id: 'a11y-panel',
        title: 'Accessibility Issues',
        icon: <AccessibilityIcon />,
        position: 'right',
        component: ({ items, canvasAPI }: { items: TItem[], canvasAPI: CanvasAPI<TItem> }) => (
            <AccessibilityPanel items={items} canvasAPI={canvasAPI} />
        )
    }],

    // Add decorators to show accessibility issues
    itemDecorators: [{
        id: 'a11y-indicator',
        priority: 100,
        render: (item) => {
            // Mock accessibility issues check
            const hasIssues = Math.random() > 0.7; // 30% chance of issues

            if (!hasIssues) return null;

            return (
                <Badge
                    badgeContent="!"
                    tone="danger"
                    className="absolute top-[-8px] right-[-8px]"
                >
                    <AccessibilityIcon tone="danger" size={16} />
                </Badge>
            );
        },
        shouldRender: (item) => {
            // Only show for certain item types
            return item.type !== 'container';
        }
    }]
});

// Accessibility Panel Component
const AccessibilityPanel: React.FC<{
    items: BaseItem[];
    canvasAPI: CanvasAPI<BaseItem>;
}> = ({ items }) => {

    // Mock accessibility issues
    const issues = [
        { id: 'missing-alt', severity: 'error', message: 'Image missing alt text', itemId: items[0]?.id },
        { id: 'low-contrast', severity: 'warning', message: 'Low color contrast ratio', itemId: items[1]?.id },
        { id: 'missing-label', severity: 'error', message: 'Form field missing label', itemId: items[2]?.id }
    ].filter(issue => issue.itemId);

    return (
        <Box className="p-4 h-full">
            <Typography as="h6" gutterBottom>
                Accessibility Issues
            </Typography>

            {issues.length === 0 ? (
                <Alert severity="success">
                    No accessibility issues found!
                </Alert>
            ) : (
                <List>
                    {issues.map(issue => (
                        <ListItem key={issue.id}>
                            <ListItemText
                                primary={issue.message}
                                secondary={`Item: ${issue.itemId}`}
                            />
                            <Chip
                                size="sm"
                                label={issue.severity}
                                color={issue.severity === 'error' ? 'error' : 'warning'}
                            />
                        </ListItem>
                    ))}
                </List>
            )}
        </Box>
    );
};

// Comments Plugin - adds commenting system
export const createCommentsPlugin = <TItem extends BaseItem>(): CanvasPlugin<TItem> => ({
    id: 'comments',
    name: 'Comments & Collaboration',
    version: '1.0.0',

    toolbarActions: [{
        id: 'toggle-comments',
        label: 'Comments',
        icon: <CommentIcon />,
        onClick: (context) => {
            console.log('Toggling comments panel');
        }
    }],

    contextMenuItems: [{
        id: 'add-comment',
        label: 'Add Comment',
        icon: <CommentIcon />,
        onClick: (item, context) => {
            console.log('Adding comment to item:', item.id);
        }
    }],

    panels: [{
        id: 'comments-panel',
        title: 'Comments',
        icon: <CommentIcon />,
        position: 'right',
        component: ({ items, selectedItems }) => (
            <CommentsPanel selectedItems={selectedItems} />
        )
    }],

    itemDecorators: [{
        id: 'comment-indicator',
        priority: 90,
        render: (item) => {
            // Mock comment count
            const commentCount = Math.floor(Math.random() * 3);

            if (commentCount === 0) return null;

            return (
                <Badge
                    badgeContent={commentCount}
                    tone="primary"
                    className="absolute top-[-8px] left-[-8px]"
                >
                    <CommentIcon tone="primary" size={16} />
                </Badge>
            );
        }
    }]
});

// Comments Panel Component
const CommentsPanel: React.FC<{
    selectedItems: string[];
}> = ({ selectedItems }) => {

    // Mock comments
    const comments = [
        { id: '1', author: 'John Doe', text: 'This needs more work', timestamp: '2 hours ago' },
        { id: '2', author: 'Jane Smith', text: 'Looks good to me!', timestamp: '1 hour ago' }
    ];

    return (
        <Box className="p-4 h-full">
            <Typography as="h6" gutterBottom>
                Comments
            </Typography>

            {selectedItems.length === 0 ? (
                <Typography as="p" className="text-sm" color="text.secondary">
                    Select an item to see comments
                </Typography>
            ) : (
                <List>
                    {comments.map(comment => (
                        <ListItem key={comment.id} className="px-0">
                            <Paper className="p-4 w-full">
                                <Typography as="p" className="text-sm">
                                    {comment.text}
                                </Typography>
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                    {comment.author} • {comment.timestamp}
                                </Typography>
                            </Paper>
                        </ListItem>
                    ))}
                </List>
            )}
        </Box>
    );
};

// History Plugin - adds undo/redo and history tracking
export const createHistoryPlugin = <TItem extends BaseItem>(): CanvasPlugin<TItem> => ({
    id: 'history',
    name: 'History & Undo',
    version: '1.0.0',

    toolbarActions: [
        {
            id: 'undo',
            label: 'Undo',
            icon: <HistoryIcon />,
            onClick: (context) => {
                console.log('Undo action');
                // In real implementation, would call canvasAPI undo method
            }
        },
        {
            id: 'show-history',
            label: 'History',
            icon: <HistoryIcon />,
            onClick: (context) => {
                console.log('Show history panel');
            }
        }
    ],

    panels: [{
        id: 'history-panel',
        title: 'History',
        icon: <HistoryIcon />,
        position: 'left',
        component: () => <HistoryPanel />
    }]
});

// History Panel Component
const HistoryPanel: React.FC = () => {

    // Mock history entries
    const history = [
        { id: '1', action: 'Created item', timestamp: '10:30 AM' },
        { id: '2', action: 'Updated position', timestamp: '10:32 AM' },
        { id: '3', action: 'Deleted item', timestamp: '10:35 AM' }
    ];

    return (
        <Box className="p-4 h-full">
            <Typography as="h6" gutterBottom>
                Action History
            </Typography>

            <List>
                {history.map((entry, index) => (
                    <ListItem
                        key={entry.id}
                        className="px-0" style={{ opacity: index === 0 ? 1 : 0.7 // Latest action is fully opaque }}
                    >
                        <ListItemText
                            primary={entry.action}
                            secondary={entry.timestamp}
                        />
                    </ListItem>
                ))}
            </List>
        </Box>
    );
};

// Validation Plugin - adds data validation
export const createValidationPlugin = <TItem extends BaseItem>(): CanvasPlugin<TItem> => ({
    id: 'validation',
    name: 'Data Validation',
    version: '1.0.0',

    toolbarActions: [{
        id: 'validate-all',
        label: 'Validate Canvas',
        icon: <ValidationIcon />,
        onClick: (context) => {
            console.log('Running validation on all items');
        }
    }],

    panels: [{
        id: 'validation-panel',
        title: 'Validation Errors',
        icon: <ValidationIcon />,
        position: 'bottom',
        component: ({ items }) => <ValidationPanel items={items} />
    }],

    itemDecorators: [{
        id: 'validation-indicator',
        priority: 110,
        render: (item) => {
            // Mock validation check
            const hasErrors = Math.random() > 0.8; // 20% chance of validation errors

            if (!hasErrors) return null;

            return (
                <Badge
                    badgeContent="⚠"
                    tone="warning"
                    className="absolute top-[8px] right-[-8px]"
                />
            );
        }
    }]
});

// Validation Panel Component
const ValidationPanel: React.FC<{
    items: BaseItem[];
}> = ({ items }) => {

    // Mock validation errors
    const errors = [
        { id: '1', type: 'error', message: 'Required field missing', itemId: items[0]?.id },
        { id: '2', type: 'warning', message: 'Deprecated property used', itemId: items[1]?.id }
    ].filter(error => error.itemId);

    const [isMinimized, setIsMinimized] = React.useState(false);

    return (
        <Paper className="transition-all duration-300 flex flex-col" style={{ height: isMinimized ? 40 : 200 }}>
            <Box className="flex items-center justify-between p-2 border-gray-200 dark:border-gray-700 border-b" >
                <Typography as="h6">
                    Validation ({errors.length} issues)
                </Typography>
                <IconButton
                    size="sm"
                    onClick={() => setIsMinimized(!isMinimized)}
                >
                    <CloseIcon />
                </IconButton>
            </Box>

            {!isMinimized && (
                <Box className="flex-1 overflow-auto p-2">
                    {errors.length === 0 ? (
                        <Alert severity="success">
                            All validations passed!
                        </Alert>
                    ) : (
                        <List dense>
                            {errors.map(error => (
                                <ListItem key={error.id}>
                                    <ListItemText
                                        primary={error.message}
                                        secondary={`Item: ${error.itemId}`}
                                    />
                                    <Chip
                                        size="sm"
                                        label={error.type}
                                        color={error.type === 'error' ? 'error' : 'warning'}
                                    />
                                </ListItem>
                            ))}
                        </List>
                    )}
                </Box>
            )}
        </Paper>
    );
};

// Export all plugins as a collection
export const DEFAULT_CANVAS_PLUGINS = {
    accessibility: createAccessibilityPlugin,
    comments: createCommentsPlugin,
    history: createHistoryPlugin,
    validation: createValidationPlugin
};