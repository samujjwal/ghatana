/**
 * AI Copilot Panel Component
 *
 * Conversational AI interface for interacting with the copilot agent.
 * Provides chat-like experience with context-aware suggestions.
 *
 * Features:
 * - Chat message history
 * - Suggested actions
 * - Code snippet rendering
 * - Context indicators
 * - Session management
 *
 * @doc.type component
 * @doc.purpose AI Copilot chat interface
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useRef, useEffect, useCallback } from 'react';
import { Box, Button, IconButton, Surface as Paper, TextField, Typography, Chip, Avatar, Collapse, Spinner as CircularProgress, Tooltip, Divider } from '@ghatana/ui';
import { Send as SendIcon, Bot as AIIcon, User as UserIcon, RefreshCw as RefreshIcon, Trash2 as ClearIcon, Copy as CopyIcon, Check as CheckIcon, Code as CodeIcon, Lightbulb as SuggestionIcon, X as CloseIcon } from 'lucide-react';

import { useAICopilot, type CopilotMessage } from '../hooks/useAICopilot';

/**
 * AICopilotPanel props
 */
export interface AICopilotPanelProps {
    /** Workspace ID for context */
    workspaceId: string;
    /** Optional item ID for context */
    itemId?: string;
    /** Panel open state */
    open: boolean;
    /** Callback when panel closes */
    onClose: () => void;
    /** Position on screen */
    position?: 'right' | 'bottom' | 'floating';
    /** Custom system prompt */
    systemPrompt?: string;
}

/**
 * Message bubble component
 */
const MessageBubble: React.FC<{
    message: CopilotMessage;
    onCopy: (text: string) => void;
}> = ({ message, onCopy }) => {
    const [copied, setCopied] = useState(false);
    const isUser = message.role === 'user';
    const isSystem = message.role === 'system';

    const handleCopy = useCallback(() => {
        onCopy(message.content);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    }, [message.content, onCopy]);

    if (isSystem) return null;

    return (
        <Box
            className="flex mb-4" style={{ justifyContent: isUser ? 'flex-end' : 'flex-start' }}
        >
            {!isUser && (
                <Avatar className="mr-2 bg-blue-600 w-[32px] h-[32px]">
                    <AIIcon size={16} />
                </Avatar>
            )}
            <Paper
                variant="raised"
                className="p-4 max-w-[80%] rounded-lg relative" style={{ backgroundColor: isUser ? 'primary.light' : 'grey.100' }}
            >
                <Typography as="p" className="text-sm" className="whitespace-pre-wrap">
                    {message.content}
                </Typography>

                {/* Code snippets */}
                {message.codeSnippets && message.codeSnippets.length > 0 && (
                    <Box className="mt-4">
                        {message.codeSnippets.map((snippet, idx) => (
                            <Paper
                                key={idx}
                                className="p-3 mt-2 rounded bg-gray-900" >
                                <Box className="flex justify-between mb-2">
                                    <Chip
                                        size="sm"
                                        icon={<CodeIcon />}
                                        label={snippet.language}
                                        style={{ backgroundColor: '#424242', color: '#e0e0e0' }}
                                    />
                                    <IconButton
                                        size="sm"
                                        onClick={() => onCopy(snippet.code)}
                                        style={{ color: '#bdbdbd' }}
                                    >
                                        <CopyIcon size={16} />
                                    </IconButton>
                                </Box>
                                <Typography
                                    component="pre"
                                    className="text-[0.85rem] overflow-auto m-0 font-mono text-gray-300" >
                                    {snippet.code}
                                </Typography>
                            </Paper>
                        ))}
                    </Box>
                )}

                {/* Suggestions */}
                {message.suggestions && message.suggestions.length > 0 && (
                    <Box className="mt-4 flex flex-wrap gap-2">
                        {message.suggestions.map((suggestion, idx) => (
                            <Chip
                                key={idx}
                                size="sm"
                                icon={<SuggestionIcon />}
                                label={suggestion}
                                variant="outlined"
                                clickable
                            />
                        ))}
                    </Box>
                )}

                {/* Copy button */}
                <IconButton
                    size="sm"
                    onClick={handleCopy}
                    className="absolute top-[4px] right-[4px] opacity-[0.6] hover:opacity-100"
                >
                    {copied ? <CheckIcon size={16} /> : <CopyIcon size={16} />}
                </IconButton>

                {/* Timestamp */}
                <Typography
                    as="span" className="text-xs text-gray-500"
                    className="block mt-2 opacity-[0.6]"
                >
                    {message.timestamp.toLocaleTimeString()}
                </Typography>
            </Paper>
            {isUser && (
                <Avatar className="ml-2 bg-indigo-600 w-[32px] h-[32px]">
                    <UserIcon size={16} />
                </Avatar>
            )}
        </Box>
    );
};

/**
 * Suggested actions component
 */
const SuggestedActions: React.FC<{
    suggestions: string[];
    onSelect: (suggestion: string) => void;
}> = ({ suggestions, onSelect }) => {
    if (suggestions.length === 0) return null;

    return (
        <Box className="p-2 border-gray-200 dark:border-gray-700 border-t" >
            <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mb-2 block">
                Suggestions:
            </Typography>
            <Box className="flex flex-wrap gap-2">
                {suggestions.slice(0, 4).map((suggestion, idx) => (
                    <Chip
                        key={idx}
                        size="sm"
                        label={suggestion}
                        onClick={() => onSelect(suggestion)}
                        variant="outlined"
                        tone="primary"
                    />
                ))}
            </Box>
        </Box>
    );
};

/**
 * AICopilotPanel Component
 */
export const AICopilotPanel: React.FC<AICopilotPanelProps> = ({
    workspaceId,
    itemId,
    open,
    onClose,
    position = 'right',
    systemPrompt,
}) => {
    const [inputValue, setInputValue] = useState('');
    const messagesEndRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLInputElement>(null);

    const {
        session,
        sendMessage,
        clearHistory,
        resetSession,
        setItemContext,
        getSuggestions,
        isReady,
        error,
    } = useAICopilot({
        workspaceId,
        itemId,
        systemPrompt,
    });

    const [suggestions, setSuggestions] = useState<string[]>([]);

    // Scroll to bottom on new messages
    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [session.messages]);

    // Update item context
    useEffect(() => {
        setItemContext(itemId || null);
    }, [itemId, setItemContext]);

    // Fetch suggestions periodically
    useEffect(() => {
        const fetchSuggestions = async () => {
            const newSuggestions = await getSuggestions();
            setSuggestions(newSuggestions);
        };
        if (open && isReady) {
            fetchSuggestions();
        }
    }, [open, isReady, getSuggestions]);

    // Handle send message
    const handleSend = useCallback(async () => {
        if (!inputValue.trim() || session.isLoading) return;

        const message = inputValue;
        setInputValue('');
        await sendMessage(message);
    }, [inputValue, session.isLoading, sendMessage]);

    // Handle key press
    const handleKeyPress = useCallback(
        (e: React.KeyboardEvent) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                handleSend();
            }
        },
        [handleSend]
    );

    // Handle copy
    const handleCopy = useCallback((text: string) => {
        navigator.clipboard.writeText(text);
    }, []);

    // Handle suggestion select
    const handleSuggestionSelect = useCallback((suggestion: string) => {
        setInputValue(suggestion);
        inputRef.current?.focus();
    }, []);

    // Position styles
    const positionStyles = {
        right: {
            width: 400,
            height: '100vh',
            position: 'fixed' as const,
            right: 0,
            top: 0,
        },
        bottom: {
            width: '100%',
            height: 400,
            position: 'fixed' as const,
            bottom: 0,
            left: 0,
        },
        floating: {
            width: 400,
            height: 500,
            position: 'fixed' as const,
            right: 20,
            bottom: 20,
            borderRadius: 2,
        },
    };

    return (
        <Collapse in={open} orientation={position === 'bottom' ? 'vertical' : 'horizontal'}>
            <Paper
                elevation={4}
                style={{
                    ...positionStyles[position],
                    display: 'flex',
                    flexDirection: 'column',
                    zIndex: 1300,
                }}
            >
                {/* Header */}
                <Box
                    className="p-4 flex items-center border-gray-200 dark:border-gray-700 bg-blue-600 text-white border-b" >
                    <AIIcon className="mr-2" />
                    <Typography as="h6" className="grow">
                        AI Copilot
                    </Typography>
                    <Tooltip title="Clear History">
                        <IconButton size="sm" onClick={clearHistory} className="text-inherit">
                            <ClearIcon />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="New Session">
                        <IconButton size="sm" onClick={resetSession} className="text-inherit">
                            <RefreshIcon />
                        </IconButton>
                    </Tooltip>
                    <IconButton size="sm" onClick={onClose} className="text-inherit">
                        <CloseIcon />
                    </IconButton>
                </Box>

                {/* Context indicator */}
                {itemId && (
                    <Box className="p-2 bg-gray-100" >
                        <Chip
                            size="sm"
                            label={`Context: Item ${itemId}`}
                            onDelete={() => setItemContext(null)}
                        />
                    </Box>
                )}

                {/* Messages area */}
                <Box
                    className="grow overflow-auto p-4 bg-gray-50 dark:bg-gray-950"
                >
                    {session.messages.length === 0 ? (
                        <Box className="text-center py-8">
                            <AIIcon className="mb-4 text-5xl text-gray-400" />
                            <Typography as="p" color="text.secondary">
                                Start a conversation with your AI assistant
                            </Typography>
                            <Typography as="p" className="text-sm" color="text.secondary" className="mt-2">
                                Ask questions, get suggestions, or generate code
                            </Typography>
                        </Box>
                    ) : (
                        session.messages.map((msg) => (
                            <MessageBubble key={msg.id} message={msg} onCopy={handleCopy} />
                        ))
                    )}

                    {session.isLoading && (
                        <Box className="flex items-center gap-2">
                            <Avatar className="bg-blue-600 w-[32px] h-[32px]">
                                <AIIcon size={16} />
                            </Avatar>
                            <CircularProgress size={20} />
                        </Box>
                    )}

                    {error && (
                        <Paper className="p-4 mt-4 bg-red-400" >
                            <Typography color="error.contrastText">{error}</Typography>
                        </Paper>
                    )}

                    <div ref={messagesEndRef} />
                </Box>

                {/* Suggestions */}
                <SuggestedActions suggestions={suggestions} onSelect={handleSuggestionSelect} />

                <Divider />

                {/* Input area */}
                <Box className="p-4 flex gap-2">
                    <TextField
                        fullWidth
                        size="sm"
                        placeholder="Ask AI Copilot..."
                        value={inputValue}
                        onChange={(e) => setInputValue(e.target.value)}
                        onKeyPress={handleKeyPress}
                        inputRef={inputRef}
                        disabled={!isReady || session.isLoading}
                        multiline
                        maxRows={3}
                    />
                    <Button
                        variant="solid"
                        onClick={handleSend}
                        disabled={!inputValue.trim() || !isReady || session.isLoading}
                        className="px-4 min-w-0" >
                        <SendIcon />
                    </Button>
                </Box>
            </Paper>
        </Collapse>
    );
};

export default AICopilotPanel;
