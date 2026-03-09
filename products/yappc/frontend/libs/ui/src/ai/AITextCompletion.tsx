import { X as CloseIcon, Check as CheckIcon } from 'lucide-react';
import { TextField, Box, Spinner as CircularProgress, Chip, IconButton, Typography } from '@ghatana/ui';
import React, { useState, useCallback, useEffect, useRef } from 'react';

import { useAICompletion } from './hooks/useAICompletion';

import type { IAIService } from '@ghatana/yappc-ai/core';

/**
 * AITextCompletion props
 */
export interface AITextCompletionProps {
    /** AI service instance */
    aiService: IAIService;
    /** Input value */
    value: string;
    /** Change handler */
    onChange: (value: string) => void;
    /** Placeholder text */
    placeholder?: string;
    /** Minimum characters before triggering suggestions */
    minLength?: number;
    /** Debounce delay in ms */
    debounceMs?: number;
    /** Enable streaming */
    stream?: boolean;
    /** Custom prompt prefix */
    promptPrefix?: string;
    /** Label */
    label?: string;
    /** Helper text */
    helperText?: string;
    /** Disabled state */
    disabled?: boolean;
    /** Multiline */
    multiline?: boolean;
    /** Rows (if multiline) */
    rows?: number;
    /** Max rows (if multiline) */
    maxRows?: number;
    /** Full width */
    fullWidth?: boolean;
    /** Show suggestion inline */
    inlineSuggestion?: boolean;
    /** Trigger key (default: Tab) */
    triggerKey?: string;
    /** Custom className */
    className?: string;
    /** Custom style */
    style?: React.CSSProperties;
}

/**
 * AI-powered text completion component
 * Shows inline suggestions as user types
 */
export const AITextCompletion: React.FC<AITextCompletionProps> = ({
    aiService,
    value,
    onChange,
    placeholder = 'Start typing...',
    minLength = 10,
    debounceMs = 500,
    stream = false,
    promptPrefix,
    label,
    helperText,
    disabled = false,
    multiline = false,
    rows = 4,
    maxRows = 10,
    fullWidth = true,
    inlineSuggestion = true,
    triggerKey = 'Tab',
    className,
    style,
}) => {
    const [showSuggestion, setShowSuggestion] = useState(false);
    const [acceptedCount, setAcceptedCount] = useState(0);
    const [isPending, setIsPending] = useState(false);
    const debounceTimerRef = useRef<NodeJS.Timeout | null>(null);

    const {
        completion,
        isLoading,
        error,
        isStreaming,
        complete,
        streamComplete,
        cancel,
        reset,
        accept,
    } = useAICompletion(aiService, {
        minLength,
        debounceMs,
        stream,
    });

    /**
     * Trigger completion
     */
    const triggerCompletion = useCallback(
        async (text: string) => {
            // Clear pending flag - we are about to start the actual request
            setIsPending(false);
            if (text.length < minLength) {
                reset();
                return;
            }

            const prompt = promptPrefix
                ? `${promptPrefix}\n\nContinue this text: "${text}"`
                : `Continue this text naturally: "${text}"`;

            try {
                if (stream) {
                    await streamComplete(prompt, {
                        maxTokens: 100,
                        temperature: 0.7,
                    });
                } else {
                    await complete(prompt, {
                        maxTokens: 50,
                        temperature: 0.7,
                    });
                }
                setShowSuggestion(true);
            } catch (err) {
                console.error('Completion error:', err);
            }
        },
        [minLength, promptPrefix, stream, streamComplete, complete, reset]
    );

    /**
     * Handle input change
     */
    const handleChange = useCallback(
        (event: React.ChangeEvent<HTMLInputElement>) => {
            const newValue = event.target.value;
            onChange(newValue);

            // Cancel existing timer
            if (debounceTimerRef.current) {
                clearTimeout(debounceTimerRef.current);
            }

            // Debounce completion
            debounceTimerRef.current = setTimeout(() => {
                triggerCompletion(newValue);
            }, debounceMs);
            // Indicate that a completion is pending (waiting for debounce)
            setIsPending(true);
        },
        [onChange, debounceMs, triggerCompletion]
    );

    /**
     * Handle key down
     */
    const handleKeyDown = useCallback(
        (event: React.KeyboardEvent) => {
            // Accept suggestion with Tab or custom key
            if (event.key === triggerKey && showSuggestion && completion) {
                event.preventDefault();
                const accepted = accept();
                onChange(`${value} ${accepted}`);
                setShowSuggestion(false);
                setAcceptedCount((c) => c + 1);
            }

            // Cancel suggestion with Escape
            if (event.key === 'Escape' && showSuggestion) {
                event.preventDefault();
                setShowSuggestion(false);
                reset();
            }
        },
        [triggerKey, showSuggestion, completion, accept, onChange, value, reset]
    );

    /**
     * Accept suggestion
     */
    const handleAccept = useCallback(() => {
        const accepted = accept();
        onChange(`${value} ${accepted}`);
        setShowSuggestion(false);
        setAcceptedCount((c) => c + 1);
    }, [accept, onChange, value]);

    /**
     * Reject suggestion
     */
    const handleReject = useCallback(() => {
        setShowSuggestion(false);
        reset();
    }, [reset]);

    // Cleanup on unmount
    useEffect(() => {
        return () => {
            if (debounceTimerRef.current) {
                clearTimeout(debounceTimerRef.current);
            }
            cancel();
        };
    }, [cancel]);

    return (
        <Box className={className} style={style}>
            <TextField
                label={label}
                value={value}
                onChange={handleChange}
                onKeyDown={handleKeyDown}
                placeholder={placeholder}
                helperText={helperText}
                disabled={disabled}
                multiline={multiline}
                rows={rows}
                maxRows={maxRows}
                fullWidth={fullWidth}
                variant="outlined"
                InputProps={{
                    endAdornment: (isLoading || isPending) && (
                        <CircularProgress size={20} className="mr-2" />
                    ),
                }}
            />

            {/* Inline suggestion */}
            {showSuggestion && completion && inlineSuggestion && (
                <Box
                    className="mt-2 p-4 rounded relative bg-gray-100 dark:bg-gray-800 border border-solid border-gray-200 dark:border-gray-700"
                >
                    <Typography
                        as="p" className="text-sm"
                        className="text-gray-500 dark:text-gray-400 italic"
                    >
                        {completion}
                    </Typography>

                    <Box className="mt-2 flex gap-2 items-center">
                        <Chip
                            label={`Press ${triggerKey} to accept`}
                            size="sm"
                            tone="primary"
                            variant="outlined"
                        />
                        <IconButton
                            size="sm"
                            onClick={handleAccept}
                            tone="success"
                            title="Accept suggestion"
                            aria-label="Accept suggestion"
                        >
                            <CheckIcon size={16} />
                        </IconButton>
                        <IconButton
                            size="sm"
                            onClick={handleReject}
                            tone="danger"
                            title="Reject suggestion"
                            aria-label="Reject suggestion"
                        >
                            <CloseIcon size={16} />
                        </IconButton>
                        {isStreaming && (
                            <Chip
                                label="Generating..."
                                size="sm"
                                tone="primary"
                                icon={<CircularProgress size={12} />}
                            />
                        )}
                    </Box>
                </Box>
            )}

            {/* Additional accessible progress indicator for tests */}
            {(isLoading || isPending) && (
                <div
                    role="progressbar"
                    aria-label="AI completion in progress"
                    style={{ width: 1, height: 1, opacity: 0 }}
                />
            )}

            {/* Error display */}
            {error && (
                <Typography as="span" className="text-xs text-gray-500" tone="danger" className="mt-2 block">
                    {error.message}
                </Typography>
            )}

            {/* Stats */}
            {acceptedCount > 0 && (
                <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mt-2 block">
                    {acceptedCount} suggestion{acceptedCount > 1 ? 's' : ''} accepted
                </Typography>
            )}
        </Box>
    );
};
