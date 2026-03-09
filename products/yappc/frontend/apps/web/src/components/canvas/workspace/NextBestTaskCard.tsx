/**
 * Next Best Task Card Component
 * 
 * AI-powered floating card showing the recommended next action.
 * Shows persona, task description, impact, time estimate, and collaborators needed.
 * 
 * @doc.type component
 * @doc.purpose Task recommendation display
 * @doc.layer product
 * @doc.pattern Floating Action Card
 */

import React, { useState, useRef, useEffect } from 'react';
import { Card, CardContent, CardActions, Typography, Button, Box, Chip, Avatar, AvatarGroup, Tooltip, IconButton } from '@ghatana/ui';
import { Zap as Bolt, Clock as Schedule, Ban as Block, TrendingUp, X as Close, GripVertical as DragIndicator } from 'lucide-react';

const getInitials = (name: string) =>
    name
        .split(' ')
        .filter(Boolean)
        .map((part) => part[0])
        .join('')
        .slice(0, 2)
        .toUpperCase();

export interface NextBestTaskProps {
    persona: string; // WHO should do it
    taskTitle: string; // WHAT to do
    taskDescription?: string;
    impact: string; // WHY it matters
    estimatedMinutes?: number; // HOW LONG
    collaborators?: string[]; // NEEDS
    blocksCount?: number; // How many items this blocks
    onStartTask: () => void;
    onSkip: () => void;
    priority?: 'critical' | 'high' | 'medium' | 'low';
}

export const NextBestTaskCard: React.FC<NextBestTaskProps> = ({
    persona,
    taskTitle,
    taskDescription,
    impact,
    estimatedMinutes,
    collaborators = [],
    blocksCount,
    onStartTask,
    onSkip,
    priority = 'medium',
}) => {
    const theme = useTheme();
    const [isVisible, setIsVisible] = useState(true);
    // Positioned on the left to avoid collision with Playground/Minimap/Navigation
    const [position, setPosition] = useState({ top: 96, left: 24 });
    const [isDragging, setIsDragging] = useState(false);
    const [dragStart, setDragStart] = useState({ x: 0, y: 0, top: 0, left: 0 });
    const cardRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!isDragging) return;

        const handleMouseMove = (e: MouseEvent) => {
            const deltaX = e.clientX - dragStart.x;
            const deltaY = e.clientY - dragStart.y;

            // Calculate new position based on delta from start
            let newLeft = dragStart.left + deltaX;
            let newTop = dragStart.top + deltaY;

            // Constrain to viewport (card is 280px wide)
            // Allow card to move freely within viewport bounds
            const cardWidth = 280;

            // Viewport constraints
            const minLeft = -(cardWidth - 50); // Keep at least 50px visible on left
            const maxLeft = window.innerWidth - 50; // Keep at least 50px visible on right
            const minTop = 56; // Below header
            const maxTop = window.innerHeight - 50; // Keep at least 50px visible at bottom

            // Apply constraints
            newLeft = Math.max(minLeft, Math.min(newLeft, maxLeft));
            newTop = Math.max(minTop, Math.min(newTop, maxTop));

            setPosition({
                top: newTop,
                left: newLeft,
            });
        };

        const handleMouseUp = () => {
            setIsDragging(false);
        };

        document.addEventListener('mousemove', handleMouseMove);
        document.addEventListener('mouseup', handleMouseUp);

        return () => {
            document.removeEventListener('mousemove', handleMouseMove);
            document.removeEventListener('mouseup', handleMouseUp);
        };
    }, [isDragging, dragStart]);

    const handleDragStart = (e: React.MouseEvent) => {
        e.preventDefault();
        setIsDragging(true);
        setDragStart({
            x: e.clientX,
            y: e.clientY,
            top: position.top,
            left: position.left,
        });
    };

    if (!isVisible) return null;

    const getPriorityColor = () => {
        switch (priority) {
            case 'critical':
                return theme.palette.error.main;
            case 'high':
                return theme.palette.warning.main;
            case 'medium':
                return theme.palette.info.main;
            case 'low':
                return theme.palette.success.main;
        }
    };

    const formatDuration = (minutes: number) => {
        if (minutes < 60) return `~${minutes} min`;
        const hours = Math.floor(minutes / 60);
        const mins = minutes % 60;
        return mins > 0 ? `~${hours}h ${mins}m` : `~${hours}h`;
    };

    return (
        <Card
            ref={cardRef}
            elevation={8}
            className="absolute w-[280px] z-[45]" style={{ top: position.top, left: position.left, borderColor: getPriorityColor(), cursor: isDragging ? 'grabbing' : 'default' }}
        >
            {/* Draggable Header */}
            <Box
                className="flex items-center justify-between px-4 py-2 bg-gray-50 dark:bg-gray-800 border-gray-200 dark:border-gray-700 select-none active:cursor-grabbing border-b" style={{ cursor: isDragging ? 'grabbing' : 'grab', pointerEvents: isDragging ? 'auto' : 'auto' }} onMouseDown={handleDragStart}
            >
                <Box className="flex items-center gap-1">
                    <DragIndicator className="text-gray-500 dark:text-gray-400 text-base" />
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary" fontWeight="medium">
                        Next Best Task
                    </Typography>
                </Box>
                <IconButton
                    size="sm"
                    onClick={() => setIsVisible(false)}
                    className="p-1"
                >
                    <Close className="text-base" />
                </IconButton>
            </Box>
            <CardContent className="pb-2">
                {/* Header */}
                <Box className="flex items-center gap-2 mb-3">
                    <Bolt className="text-2xl" style={{ color: getPriorityColor() }} />
                    <Typography as="span" className="text-xs uppercase tracking-wider" fontWeight="bold" color="text.secondary">
                        Next Best Task
                    </Typography>
                </Box>

                {/* Persona */}
                <Box className="flex items-center gap-2 mb-3">
                    <Typography as="h5" fontWeight="bold">
                        {getInitials(persona)}
                    </Typography>
                    <Chip
                        label={persona}
                        size="sm"
                        tone="primary"
                        variant="outlined"
                    />
                </Box>

                {/* Task Title */}
                <Typography as="h6" gutterBottom className="font-bold">
                    {taskTitle}
                </Typography>

                {/* Task Description */}
                {taskDescription && (
                    <Typography as="p" className="text-sm" color="text.secondary" className="mb-3">
                        {taskDescription}
                    </Typography>
                )}

                {/* Impact/Why */}
                <Box className="flex items-center gap-1 mb-2 p-2 rounded bg-gray-100 dark:bg-gray-800">
                    <TrendingUp className="text-blue-600 text-base" />
                    <Typography as="span" className="text-xs text-gray-500" fontWeight="medium">
                        Why: {impact}
                    </Typography>
                </Box>

                {/* Metadata Row */}
                <Box className="flex gap-4 mt-3 flex-wrap">
                    {/* Time Estimate */}
                    {estimatedMinutes && (
                        <Box className="flex items-center gap-1">
                            <Schedule className="text-gray-500 dark:text-gray-400 text-base" />
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                {formatDuration(estimatedMinutes)}
                            </Typography>
                        </Box>
                    )}

                    {/* Blocks Count */}
                    {blocksCount && blocksCount > 0 && (
                        <Box className="flex items-center gap-1">
                            <Block className="text-red-600 text-base" />
                            <Typography as="span" className="text-xs text-gray-500" color="error.main" fontWeight="medium">
                                Blocks {blocksCount} {blocksCount === 1 ? 'task' : 'tasks'}
                            </Typography>
                        </Box>
                    )}
                </Box>

                {/* Collaborators Needed */}
                {collaborators.length > 0 && (
                    <Box className="mt-3">
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary" display="block" className="mb-1">
                            Needs:
                        </Typography>
                        <AvatarGroup max={4} className="justify-start">
                            {collaborators.map((collab, index) => (
                                <Tooltip key={index} title={collab}>
                                    <Avatar className="w-[24px] h-[24px] text-sm">
                                        {getInitials(collab)}
                                    </Avatar>
                                </Tooltip>
                            ))}
                        </AvatarGroup>
                    </Box>
                )}
            </CardContent>

            <CardActions className="px-4 pb-4 gap-2">
                <Button
                    variant="solid"
                    tone="primary"
                    fullWidth
                    onClick={onStartTask}
                    className="font-bold"
                >
                    Start Task
                </Button>
                <Button
                    variant="outlined"
                    size="sm"
                    onClick={onSkip}
                    className="px-4 min-w-0" >
                    Skip
                </Button>
            </CardActions>
        </Card>
    );
};
