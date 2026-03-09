import React, { useState, useRef, useEffect, ReactNode } from 'react';
import { Box, Surface as Paper, Typography, IconButton, Card, CardHeader } from '@ghatana/ui';
import { GripVertical as DragIndicator, X as Close, Minimize2 as Minimize, Maximize as OpenInFull } from 'lucide-react';

export interface DraggablePanelProps {
    title?: string;
    children: ReactNode;
    defaultPosition?: { x: number; y: number };
    width?: number | string;
    onClose?: () => void;
    id: string; // Unique ID for persisting position
    elevation?: number;
    className?: string;
}

export const DraggablePanel: React.FC<DraggablePanelProps> = ({
    title,
    children,
    defaultPosition = { x: 20, y: 20 },
    width = 300,
    onClose,
    id,
    elevation = 4,
}) => {
    // Load position from localStorage or use default
    const [position, setPosition] = useState(() => {
        try {
            const saved = localStorage.getItem(`panel-pos-${id}`);
            if (saved) return JSON.parse(saved);
        } catch (e) {
            // ignore
        }
        return defaultPosition;
    });

    const [isDragging, setIsDragging] = useState(false);
    const [isMinimised, setIsMinimised] = useState(false);
    const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 });
    const panelRef = useRef<HTMLDivElement>(null);
    const theme = useTheme();

    // Handle window resize to keep panel on screen
    useEffect(() => {
        const handleResize = () => {
            setPosition((prev: { x: number; y: number }) => {
                const maxX = window.innerWidth - 50;
                const maxY = window.innerHeight - 50;

                // If panel would be off-screen, adjust position
                const newX = prev.x > maxX ? maxX : prev.x;
                const newY = prev.y > maxY ? maxY : prev.y;

                // Only update if position actually changed
                if (newX !== prev.x || newY !== prev.y) {
                    return { x: newX, y: newY };
                }
                return prev;
            });
        };

        window.addEventListener('resize', handleResize);
        return () => window.removeEventListener('resize', handleResize);
    }, []);

    useEffect(() => {
        if (!isDragging) return;

        const handleMouseMove = (e: MouseEvent) => {
            const newX = e.clientX - dragOffset.x;
            const newY = e.clientY - dragOffset.y;

            // Simple bounds checking (keep roughly on screen)
            const maxX = window.innerWidth - 50;
            const maxY = window.innerHeight - 50;

            const clampedX = Math.max(-100, Math.min(newX, maxX));
            const clampedY = Math.max(0, Math.min(newY, maxY));

            setPosition({ x: clampedX, y: clampedY });
        };

        const handleMouseUp = () => {
            setIsDragging(false);
            // Save final position
            localStorage.setItem(`panel-pos-${id}`, JSON.stringify(position));
        };

        document.addEventListener('mousemove', handleMouseMove);
        document.addEventListener('mouseup', handleMouseUp);

        return () => {
            document.removeEventListener('mousemove', handleMouseMove);
            document.removeEventListener('mouseup', handleMouseUp);
        };
    }, [isDragging, dragOffset, id, position]);

    const handleDragStart = (e: React.MouseEvent) => {
        if (e.target instanceof HTMLButtonElement || e.target instanceof SVGElement) return; // Don't drag if clicking buttons

        setIsDragging(true);
        // Calculate offset from mouse to top-left of panel
        setDragOffset({
            x: e.clientX - position.x,
            y: e.clientY - position.y
        });
    };

    return (
        <Paper
            ref={panelRef}
            elevation={elevation}
            className="fixed"
        >
            {/* Drag Handle Header */}
            <Box
                onMouseDown={handleDragStart}
                className="p-2 flex items-center justify-between border-gray-200 dark:border-gray-700 select-none" style={{ cursor: isDragging ? 'grabbing' : 'grab', backgroundColor: theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.02)', borderBottom: isMinimised ? 'none' : '1px solid' }}
            >
                <Box className="flex items-center gap-2">
                    <DragIndicator className="text-gray-500 dark:text-gray-400 text-base" />
                    {title && (
                        <Typography as="p" className="text-sm font-medium" fontWeight="bold" noWrap className="max-w-[150px]">
                            {title}
                        </Typography>
                    )}
                </Box>
                <Box className="flex items-center">
                    <IconButton
                        size="sm"
                        onClick={() => setIsMinimised(!isMinimised)}
                        className="ml-1 p-1"
                    >
                        {isMinimised ? <OpenInFull size={16} /> : <Minimize size={16} />}
                    </IconButton>
                    {onClose && (
                        <IconButton
                            size="sm"
                            onClick={onClose}
                            className="ml-1 p-1"
                        >
                            <Close size={16} />
                        </IconButton>
                    )}
                </Box>
            </Box>

            {/* Content */}
            {!isMinimised && (
                <Box className="overflow-y-auto max-h-[80vh]" >
                    {children}
                </Box>
            )}
        </Paper>
    );
};
