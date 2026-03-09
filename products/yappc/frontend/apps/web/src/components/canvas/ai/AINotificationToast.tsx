/**
 * AI Notification Toast
 * 
 * Floating notification that appears when AI generates new suggestions
 * Provides proactive visibility without interrupting workflow
 * 
 * @doc.type component
 * @doc.purpose Proactive AI suggestion notifications
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useEffect } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  IconButton,
  Stack,
  Typography,
} from '@ghatana/ui';
import { Fade, Slide } from '@ghatana/ui';
import { Sparkles as AutoAwesome, X as Close, Eye as Visibility } from 'lucide-react';

// ============================================================================
// Types
// ============================================================================

export interface AINotificationToastProps {
    /** Number of new suggestions */
    suggestionCount: number;
    /** Callback when user clicks to view suggestions */
    onView: () => void;
    /** Callback when notification is dismissed */
    onDismiss?: () => void;
    /** Auto-dismiss after this many milliseconds (default: 8000) */
    autoDismiss?: number;
    /** Position of the toast */
    position?: 'top-right' | 'bottom-right' | 'bottom-left' | 'top-left';
    /** Whether to show the toast */
    show: boolean;
}

// ============================================================================
// Component
// ============================================================================

export const AINotificationToast: React.FC<AINotificationToastProps> = ({
    suggestionCount,
    onView,
    onDismiss,
    autoDismiss = 8000,
    position = 'bottom-right',
    show,
}) => {
    const [isVisible, setIsVisible] = useState(false);

    useEffect(() => {
        if (show && suggestionCount > 0) {
            setIsVisible(true);

            // Auto-dismiss after timeout
            if (autoDismiss > 0) {
                const timer = setTimeout(() => {
                    setIsVisible(false);
                    onDismiss?.();
                }, autoDismiss);

                return () => clearTimeout(timer);
            }
        } else {
            setIsVisible(false);
        }
    }, [show, suggestionCount, autoDismiss, onDismiss]);

    const handleDismiss = () => {
        setIsVisible(false);
        onDismiss?.();
    };

    const handleView = () => {
        setIsVisible(false);
        onView();
    };

    // Position styles
    const positionStyles = {
        'top-right': { top: 80, right: 24 },
        'bottom-right': { bottom: 80, right: 24 },
        'bottom-left': { bottom: 80, left: 24 },
        'top-left': { top: 80, left: 24 },
    }[position];

    return (
        <Slide direction={position.includes('right') ? 'left' : 'right'} in={isVisible} mountOnEnter unmountOnExit>
            <Fade in={isVisible}>
                <Card
                    className="fixed"
                >
                    <CardContent className="pb-2">
                        <Stack spacing={1.5}>
                            {/* Header */}
                            <Stack direction="row" alignItems="flex-start" justifyContent="space-between">
                                <Stack direction="row" alignItems="center" spacing={1}>
                                    <Box
                                        className="flex items-center justify-center rounded-full w-[32px] h-[32px] bg-blue-600" style={{ animation: 'pulse 2s ease-in-out infinite' }} >
                                        <AutoAwesome className="text-lg text-white" />
                                    </Box>
                                    <Typography variant="subtitle2" fontWeight="bold">
                                        AI Suggestion{suggestionCount > 1 ? 's' : ''} Ready!
                                    </Typography>
                                </Stack>
                                <IconButton size="small" onClick={handleDismiss} className="mt--1 mr--1">
                                    <Close size={16} />
                                </IconButton>
                            </Stack>

                            {/* Message */}
                            <Typography variant="body2" color="text.secondary">
                                {suggestionCount === 1
                                    ? 'I found 1 way to improve your canvas'
                                    : `I found ${suggestionCount} ways to improve your canvas`}
                            </Typography>

                            {/* Actions */}
                            <Stack direction="row" spacing={1} className="pt-1">
                                <Button
                                    variant="contained"
                                    size="small"
                                    startIcon={<Visibility />}
                                    onClick={handleView}
                                    fullWidth
                                    className="normal-case"
                                >
                                    View Suggestions
                                </Button>
                            </Stack>
                        </Stack>
                    </CardContent>
                </Card>
            </Fade>
        </Slide>
    );
};

export default AINotificationToast;
