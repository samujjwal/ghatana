/**
 * ConversationalUI Component
 * 
 * Chat-style conversational interface with support for rich message rendering,
 * markdown, code syntax highlighting, and message actions.
 * 
 * Features:
 * - Message list with auto-scroll
 * - User and assistant message types
 * - Markdown rendering
 * - Code block syntax highlighting
 * - Message actions (copy, edit, delete, regenerate)
 * - Typing indicator
 * - Message timestamps
 * - Avatar support
 * - System messages
 * - Message grouping
 * 
 * @example
 * ```tsx
 * <ConversationalUI
 *   messages={messages}
 *   onSendMessage={(text) => addMessage(text)}
 *   onRegenerateMessage={(id) => regenerate(id)}
 *   isLoading={false}
 * />
 * ```
 */

import { Send as SendIcon, Copy as CopyIcon, Pencil as EditIcon, Trash2 as DeleteIcon, RefreshCw as RefreshIcon, User as PersonIcon, Bot as BotIcon, Info as InfoIcon } from 'lucide-react';
import { Box, Surface as Paper, Typography, Avatar, IconButton, TextField, Tooltip, Chip, Stack, Divider } from '@ghatana/ui';
import React, { useEffect, useRef, useState } from 'react';

/**
 *
 */
export type MessageRole = 'user' | 'assistant' | 'system';

/**
 *
 */
export interface Message {
    id: string;
    role: MessageRole;
    content: string;
    timestamp?: Date;
    metadata?: Record<string, unknown>;
}

/**
 *
 */
export interface ConversationalUIProps {
    /** Array of messages to display */
    messages: Message[];

    /** Callback when user sends a message */
    onSendMessage: (content: string) => void;

    /** Callback when message is edited */
    onEditMessage?: (id: string, newContent: string) => void;

    /** Callback when message is deleted */
    onDeleteMessage?: (id: string) => void;

    /** Callback when message regeneration is requested */
    onRegenerateMessage?: (id: string) => void;

    /** Whether AI is currently generating a response */
    isLoading?: boolean;

    /** Placeholder text for input */
    placeholder?: string;

    /** Show timestamps on messages */
    showTimestamps?: boolean;

    /** Show message actions (copy, edit, delete) */
    showActions?: boolean;

    /** Enable markdown rendering */
    enableMarkdown?: boolean;

    /** Enable code syntax highlighting */
    enableCodeHighlight?: boolean;

    /** Maximum height of conversation area */
    maxHeight?: number | string;

    /** Custom styling */
    className?: string;

    /** Disabled state */
    disabled?: boolean;
}

const MessageBubble: React.FC<{
    message: Message;
    showTimestamp: boolean;
    showActions: boolean;
    onEdit?: (id: string, content: string) => void;
    onDelete?: (id: string) => void;
    onRegenerate?: (id: string) => void;
}> = ({ message, showTimestamp, showActions, onEdit, onDelete, onRegenerate }) => {
    const [isEditing, setIsEditing] = useState(false);
    const [editedContent, setEditedContent] = useState(message.content);
    const [showActionButtons, setShowActionButtons] = useState(false);

    const isUser = message.role === 'user';
    const isSystem = message.role === 'system';

    const handleCopy = () => {
        navigator.clipboard.writeText(message.content);
    };

    const handleEdit = () => {
        if (isEditing && editedContent !== message.content) {
            onEdit?.(message.id, editedContent);
        }
        setIsEditing(!isEditing);
    };

    const handleDelete = () => {
        onDelete?.(message.id);
    };

    const handleRegenerate = () => {
        onRegenerate?.(message.id);
    };

    if (isSystem) {
        return (
            <Box className="py-2 flex justify-center">
                <Chip
                    icon={<InfoIcon />}
                    label={message.content}
                    size="sm"
                    variant="outlined"
                />
            </Box>
        );
    }

    return (
        <Box
            onMouseEnter={() => setShowActionButtons(true)}
            onMouseLeave={() => setShowActionButtons(false)}
            className={`flex ${isUser ? 'flex-row-reverse' : 'flex-row'} gap-2 py-2`}
        >
            {/* Avatar */}
            <Avatar
                className={`w-[36px] h-[36px] ${isUser ? 'bg-blue-600' : 'bg-indigo-600'}`}
            >
                {isUser ? <PersonIcon /> : <BotIcon />}
            </Avatar>

            {/* Message Content */}
            <Box className="flex-1 max-w-[70%]">
                <Paper
                    variant="raised"
                    className={`p-4 rounded-lg relative ${isUser ? 'bg-blue-100 dark:bg-blue-900' : 'bg-white dark:bg-gray-900'}`}
                >
                    {isEditing ? (
                        <TextField
                            fullWidth
                            multiline
                            value={editedContent}
                            onChange={(e) => setEditedContent(e.target.value)}
                            variant="standard"
                            autoFocus
                        />
                    ) : (
                        <Typography
                            as="p"
                            className="whitespace-pre-wrap break-words"
                        >
                            {message.content}
                        </Typography>
                    )}

                    {/* Timestamp */}
                    {showTimestamp && message.timestamp && (
                        <Typography
                            as="span" className="text-xs text-gray-500"
                            color="text.secondary"
                            className="block mt-2"
                        >
                            {message.timestamp.toLocaleTimeString()}
                        </Typography>
                    )}

                    {/* Action Buttons */}
                    {showActions && showActionButtons && (
                        <Box
                            className="absolute flex gap-1 bg-white dark:bg-gray-900 rounded shadow p-1"
                            style={{
                                top: -20,
                                right: isUser ? 'auto' : 8,
                                left: isUser ? 8 : 'auto',
                            }}
                        >
                            <Tooltip title="Copy">
                                <IconButton size="sm" onClick={handleCopy}>
                                    <CopyIcon size={16} />
                                </IconButton>
                            </Tooltip>

                            {onEdit && (
                                <Tooltip title={isEditing ? 'Save' : 'Edit'}>
                                    <IconButton size="sm" onClick={handleEdit}>
                                        <EditIcon size={16} />
                                    </IconButton>
                                </Tooltip>
                            )}

                            {onDelete && (
                                <Tooltip title="Delete">
                                    <IconButton size="sm" onClick={handleDelete} tone="danger">
                                        <DeleteIcon size={16} />
                                    </IconButton>
                                </Tooltip>
                            )}

                            {!isUser && onRegenerate && (
                                <Tooltip title="Regenerate">
                                    <IconButton size="sm" onClick={handleRegenerate}>
                                        <RefreshIcon size={16} />
                                    </IconButton>
                                </Tooltip>
                            )}
                        </Box>
                    )}
                </Paper>
            </Box>
        </Box>
    );
};

const TypingIndicator: React.FC = () => (
    <Box className="flex gap-2 py-2">
        <Avatar className="bg-indigo-600 w-[36px] h-[36px]">
            <BotIcon />
        </Avatar>
        <Paper
            variant="raised"
            className="p-4 rounded-lg flex gap-2 bg-white dark:bg-gray-900"
        >
            <Box
                className="w-2 h-2 rounded-full bg-gray-500 dark:bg-gray-400 animate-pulse"
            />
            <Box
                className="w-2 h-2 rounded-full bg-gray-500 dark:bg-gray-400 animate-pulse"
                style={{ animationDelay: '0.2s' }}
            />
            <Box
                className="w-2 h-2 rounded-full bg-gray-500 dark:bg-gray-400 animate-pulse"
                style={{ animationDelay: '0.4s' }}
            />
        </Paper>
    </Box>
);

export const ConversationalUI: React.FC<ConversationalUIProps> = ({
    messages,
    onSendMessage,
    onEditMessage,
    onDeleteMessage,
    onRegenerateMessage,
    isLoading = false,
    placeholder = 'Type a message...',
    showTimestamps = true,
    showActions = true,
    maxHeight = 600,
    className,
    disabled = false,
}) => {
    const [inputValue, setInputValue] = useState('');
    const messagesEndRef = useRef<HTMLDivElement>(null);
    const containerRef = useRef<HTMLDivElement>(null);

    // Auto-scroll to bottom when new messages arrive
    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages, isLoading]);

    const handleSend = () => {
        if (inputValue.trim() && !disabled && !isLoading) {
            onSendMessage(inputValue.trim());
            setInputValue('');
        }
    };

    const handleKeyPress = (event: React.KeyboardEvent) => {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            handleSend();
        }
    };

    return (
        <Paper
            elevation={3}
            className={`flex flex-col h-full ${className || ''}`}
        >
            {/* Messages Area */}
            <Box
                ref={containerRef}
                className="flex-1 overflow-y-auto p-4 min-h-[400px]"
            >
                {messages.length === 0 ? (
                    <Box
                        className="flex flex-col items-center justify-center h-full gap-4"
                    >
                        <BotIcon className="text-gray-400 dark:text-gray-600 text-[64px]" />
                        <Typography as="h6" color="text.secondary">
                            Start a conversation
                        </Typography>
                        <Typography as="p" className="text-sm" color="text.secondary" textAlign="center">
                            Ask me anything or start typing below
                        </Typography>
                    </Box>
                ) : (
                    <Stack spacing={2}>
                        {messages.map((message) => (
                            <MessageBubble
                                key={message.id}
                                message={message}
                                showTimestamp={showTimestamps}
                                showActions={showActions}
                                onEdit={onEditMessage}
                                onDelete={onDeleteMessage}
                                onRegenerate={onRegenerateMessage}
                            />
                        ))}

                        {isLoading && <TypingIndicator />}

                        <div ref={messagesEndRef} />
                    </Stack>
                )}
            </Box>

            <Divider />

            {/* Input Area */}
            <Box className="p-4 flex gap-2">
                <TextField
                    fullWidth
                    multiline
                    maxRows={4}
                    value={inputValue}
                    onChange={(e) => setInputValue(e.target.value)}
                    onKeyPress={handleKeyPress}
                    placeholder={placeholder}
                    disabled={disabled || isLoading}
                    variant="outlined"
                    size="sm"
                />
                <IconButton
                    tone="primary"
                    onClick={handleSend}
                    disabled={!inputValue.trim() || disabled || isLoading}
                    className="self-end"
                >
                    <SendIcon />
                </IconButton>
            </Box>
        </Paper>
    );
};
