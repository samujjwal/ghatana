/**
 * CopilotChat
 *
 * AI copilot conversation panel. Renders the message thread and input box.
 * Delegates data-fetching and sending to `useCopilot` from `@yappc/state`.
 *
 * @doc.type component
 * @doc.purpose AI copilot chat interface
 * @doc.layer product
 * @doc.pattern Container Component
 */

import React, { useEffect, useRef, useState } from 'react';
import {
    Box,
    Typography,
    TextField,
    IconButton,
    Avatar,
    Tooltip,
    CircularProgress,
    Divider,
    Paper,
    Alert,
} from '@mui/material';
import {
    Send as SendIcon,
    Bot as BotIcon,
    User as UserIcon,
    Trash2 as ClearIcon,
    BrainCircuit as BrainIcon,
} from 'lucide-react';

import type { CopilotMessage, CopilotSession } from '@yappc/state/aiAtoms';

export interface CopilotChatProps {
    /** Current copilot session (managed by useCopilot hook) */
    session?: CopilotSession | null;
    /** Whether the copilot is generating a response */
    isSending?: boolean;
    /** Last error from the copilot */
    error?: Error | null;
    /** Project context for the copilot */
    projectId?: string;
    /** Called when user sends a message */
    onSendMessage: (input: { message: string; projectId?: string }) => Promise<void>;
    /** Called when user clears the session */
    onClearSession?: () => void;
    /** Maximum height of the message list */
    maxHeight?: string | number;
    className?: string;
}

const MessageBubble: React.FC<{ message: CopilotMessage }> = ({ message }) => {
    const isUser = message.role === 'user';
    return (
        <Box
            display="flex"
            flexDirection={isUser ? 'row-reverse' : 'row'}
            alignItems="flex-end"
            gap={0.75}
            mb={1}
        >
            <Avatar
                sx={{
                    width: 26,
                    height: 26,
                    bgcolor: isUser ? 'primary.main' : 'secondary.main',
                    flexShrink: 0,
                }}
            >
                {isUser ? <UserIcon size={14} /> : <BotIcon size={14} />}
            </Avatar>

            <Paper
                variant="outlined"
                sx={{
                    maxWidth: '75%',
                    px: 1.25,
                    py: 0.75,
                    borderRadius: isUser ? '12px 12px 2px 12px' : '12px 12px 12px 2px',
                    bgcolor: isUser ? 'primary.50' : 'background.paper',
                    borderColor: isUser ? 'primary.200' : 'divider',
                    whiteSpace: 'pre-wrap',
                }}
            >
                <Typography variant="body2">{message.content}</Typography>
                <Typography variant="caption" color="text.disabled" sx={{ display: 'block', mt: 0.25 }}>
                    {new Date(message.createdAt).toLocaleTimeString([], {
                        hour: '2-digit',
                        minute: '2-digit',
                    })}
                </Typography>
            </Paper>
        </Box>
    );
};

/**
 * Full-featured copilot chat panel.
 */
export const CopilotChat: React.FC<CopilotChatProps> = ({
    session,
    isSending = false,
    error,
    projectId,
    onSendMessage,
    onClearSession,
    maxHeight = 420,
    className,
}) => {
    const [input, setInput] = useState('');
    const listRef = useRef<HTMLDivElement>(null);

    // Auto-scroll to bottom on new messages
    useEffect(() => {
        if (listRef.current) {
            listRef.current.scrollTop = listRef.current.scrollHeight;
        }
    }, [session?.messages.length]);

    const handleSend = async () => {
        const text = input.trim();
        if (!text || isSending) return;
        setInput('');
        await onSendMessage({ message: text, projectId: projectId ?? undefined });
    };

    const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            void handleSend();
        }
    };

    const messages = session?.messages ?? [];

    return (
        <Box
            className={className}
            display="flex"
            flexDirection="column"
            height="100%"
            sx={{ minHeight: 300 }}
        >
            {/* Header */}
            <Box
                display="flex"
                alignItems="center"
                justifyContent="space-between"
                px={1.5}
                py={1}
            >
                <Box display="flex" alignItems="center" gap={0.75}>
                    <BrainIcon size={16} />
                    <Typography variant="subtitle2" fontWeight={600}>
                        AI Copilot
                    </Typography>
                </Box>
                {onClearSession && messages.length > 0 && (
                    <Tooltip title="Clear conversation">
                        <IconButton size="small" onClick={onClearSession}>
                            <ClearIcon size={14} />
                        </IconButton>
                    </Tooltip>
                )}
            </Box>

            <Divider />

            {/* Message list */}
            <Box
                ref={listRef}
                flexGrow={1}
                px={1.5}
                py={1}
                sx={{
                    overflowY: 'auto',
                    maxHeight,
                    minHeight: 120,
                }}
            >
                {messages.length === 0 && !isSending && !error && (
                    <Box display="flex" flexDirection="column" alignItems="center" py={4} gap={1}>
                        <BotIcon size={32} opacity={0.3} />
                        <Typography variant="body2" color="text.secondary" textAlign="center">
                            Ask me anything about your project — architecture, code, planning or deployment.
                        </Typography>
                    </Box>
                )}

                {messages.map((msg) => (
                    <MessageBubble key={msg.id} message={msg} />
                ))}

                {isSending && (
                    <Box display="flex" alignItems="center" gap={0.75} mb={1}>
                        <Avatar
                            sx={{ width: 26, height: 26, bgcolor: 'secondary.main', flexShrink: 0 }}
                        >
                            <BotIcon size={14} />
                        </Avatar>
                        <Typography variant="caption" color="text.secondary">
                            Thinking…
                        </Typography>
                        <CircularProgress size={12} />
                    </Box>
                )}

                {error && (
                    <Alert severity="error" sx={{ mt: 1 }}>
                        {error.message}
                    </Alert>
                )}
            </Box>

            <Divider />

            {/* Input */}
            <Box display="flex" alignItems="flex-end" gap={0.5} px={1.5} py={1}>
                <TextField
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    onKeyDown={handleKeyDown}
                    placeholder="Ask the AI copilot…"
                    multiline
                    maxRows={4}
                    fullWidth
                    size="small"
                    disabled={isSending}
                    inputProps={{ 'aria-label': 'Copilot message input' }}
                />
                <Tooltip title="Send (Enter)">
                    <span>
                        <IconButton
                            color="primary"
                            onClick={handleSend}
                            disabled={!input.trim() || isSending}
                            size="small"
                        >
                            <SendIcon size={18} />
                        </IconButton>
                    </span>
                </Tooltip>
            </Box>
        </Box>
    );
};
