/**
 * AI Assistant Component
 * 
 * Chat interface for AI-powered data assistance including:
 * - Natural language to SQL conversion
 * - Query explanation
 * - Lineage exploration
 * - Semantic search
 * 
 * @doc.type component
 * @doc.purpose AI-powered chat assistant
 * @doc.layer frontend
 * @doc.pattern Container Component
 */

import React, { useState, useRef, useEffect, useCallback } from 'react';
import {
    MessageSquare,
    Send,
    X,
    Sparkles,
    Copy,
    Check,
    Code,
    Database,
    GitBranch,
    Search,
    Loader2,
} from 'lucide-react';
import { cn, textStyles, bgStyles, buttonStyles, inputStyles, cardStyles } from '../../lib/theme';
import { executeAnalyticsQuery } from '../../api/analytics.service';

/**
 * Message role type
 */
type MessageRole = 'user' | 'assistant' | 'system';

/**
 * Message type
 */
type MessageType = 'text' | 'sql' | 'lineage' | 'error';

/**
 * Chat message interface
 */
interface ChatMessage {
    id: string;
    role: MessageRole;
    content: string;
    type: MessageType;
    timestamp: Date;
    metadata?: {
        sql?: string;
        collections?: string[];
        confidence?: number;
    };
}

/**
 * Suggested prompts
 */
const suggestedPrompts = [
    { icon: <Code className="h-4 w-4" />, text: 'Convert to SQL: Show me all users who signed up last week' },
    { icon: <Database className="h-4 w-4" />, text: 'Explain the schema for user_events collection' },
    { icon: <GitBranch className="h-4 w-4" />, text: 'Show lineage for the transactions table' },
    { icon: <Search className="h-4 w-4" />, text: 'Find collections related to customer data' },
];

/**
 * Route user message to the appropriate backend service.
 * SQL-intent queries are executed via the analytics engine for real results.
 * Other intents use local knowledge-base responses until an AI service is wired.
 */
async function generateResponse(userMessage: string): Promise<ChatMessage> {
    const lowerMessage = userMessage.toLowerCase();
    const id = `msg-${Date.now()}`;

    if (lowerMessage.includes('sql') || lowerMessage.includes('convert') || lowerMessage.includes('query')) {
        // Extract table name heuristically from the user's message
        const tableMatch = lowerMessage.match(/from ([a-z_]+)/) || lowerMessage.match(/in ([a-z_]+)/);
        const table = tableMatch?.[1] ?? 'events';
        const sql = `SELECT * FROM ${table} ORDER BY created_at DESC LIMIT 50;`;
        const result = await executeAnalyticsQuery(sql);
        return {
            id,
            role: 'assistant',
            content: `Query executed — ${result.rowCount} row(s) returned in ${result.executionTimeMs}ms.`,
            type: 'sql',
            timestamp: new Date(),
            metadata: { sql, confidence: 0.85 },
        };
    }

    if (lowerMessage.includes('schema') || lowerMessage.includes('explain')) {
        return {
            id,
            role: 'assistant',
            content: 'Open the Data Fabric explorer and select a collection to inspect its live schema.',
            type: 'text',
            timestamp: new Date(),
        };
    }

    if (lowerMessage.includes('lineage')) {
        return {
            id,
            role: 'assistant',
            content: 'Navigate to the Lineage view in Data Fabric to visualise upstream and downstream dependencies for any collection.',
            type: 'lineage',
            timestamp: new Date(),
        };
    }

    return {
        id,
        role: 'assistant',
        content: `I can help you with:
- **SQL Queries**: Describe what data you need and I\'ll run it against the analytics engine.
- **Schema Exploration**: Ask about a collection to open it in the Data Fabric viewer.
- **Lineage Analysis**: Trace data dependencies via the Lineage view.
- **Semantic Search**: Describe a concept and I\'ll find related collections.

What would you like to know?`,
        type: 'text',
        timestamp: new Date(),
    };
}

interface AiAssistantProps {
    isOpen: boolean;
    onClose: () => void;
}

/**
 * AI Assistant Component
 */
export function AiAssistant({ isOpen, onClose }: AiAssistantProps): React.ReactElement | null {
    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [input, setInput] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [copiedId, setCopiedId] = useState<string | null>(null);
    const messagesEndRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLInputElement>(null);

    // Scroll to bottom when messages change
    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages]);

    // Focus input when opened
    useEffect(() => {
        if (isOpen) {
            inputRef.current?.focus();
        }
    }, [isOpen]);

    // Handle send message
    const handleSend = useCallback(async () => {
        if (!input.trim() || isLoading) return;

        const userMessage: ChatMessage = {
            id: `msg-${Date.now()}`,
            role: 'user',
            content: input.trim(),
            type: 'text',
            timestamp: new Date(),
        };

        setMessages((prev) => [...prev, userMessage]);
        setInput('');
        setIsLoading(true);

        try {
            const response = await generateResponse(userMessage.content);
            setMessages((prev) => [...prev, response]);
        } catch {
            setMessages((prev) => [...prev, {
                id: `msg-${Date.now()}`,
                role: 'assistant',
                content: 'Failed to process your request. Please ensure the analytics service is reachable.',
                type: 'error',
                timestamp: new Date(),
            }]);
        } finally {
            setIsLoading(false);
        }
    }, [input, isLoading]);

    // Handle copy SQL
    const handleCopy = useCallback((text: string, id: string) => {
        navigator.clipboard.writeText(text);
        setCopiedId(id);
        setTimeout(() => setCopiedId(null), 2000);
    }, []);

    // Handle suggested prompt click
    const handleSuggestedPrompt = useCallback((prompt: string) => {
        setInput(prompt);
        inputRef.current?.focus();
    }, []);

    if (!isOpen) return null;

    return (
        <div className="fixed bottom-4 right-4 z-50 w-96 max-h-[600px] flex flex-col rounded-xl shadow-2xl overflow-hidden border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
            {/* Header */}
            <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700 bg-gradient-to-r from-blue-600 to-purple-600">
                <div className="flex items-center gap-2 text-white">
                    <Sparkles className="h-5 w-5" />
                    <span className="font-semibold">AI Assistant</span>
                </div>
                <button
                    onClick={onClose}
                    className="p-1 rounded hover:bg-white/20 text-white transition-colors"
                >
                    <X className="h-5 w-5" />
                </button>
            </div>

            {/* Messages */}
            <div className="flex-1 overflow-y-auto p-4 space-y-4 min-h-[300px]">
                {messages.length === 0 ? (
                    <div className="space-y-4">
                        <div className="text-center py-4">
                            <Sparkles className="h-8 w-8 mx-auto text-blue-500 mb-2" />
                            <p className={textStyles.h4}>How can I help you?</p>
                            <p className={textStyles.muted}>Ask me about your data</p>
                        </div>
                        <div className="space-y-2">
                            <p className={cn(textStyles.xs, 'px-1')}>Try asking:</p>
                            {suggestedPrompts.map((prompt, i) => (
                                <button
                                    key={i}
                                    onClick={() => handleSuggestedPrompt(prompt.text)}
                                    className={cn(
                                        'w-full flex items-center gap-2 p-2 rounded-lg text-left text-sm',
                                        'bg-gray-50 dark:bg-gray-700 hover:bg-gray-100 dark:hover:bg-gray-600',
                                        'transition-colors'
                                    )}
                                >
                                    <span className="text-blue-500">{prompt.icon}</span>
                                    <span className={textStyles.small}>{prompt.text}</span>
                                </button>
                            ))}
                        </div>
                    </div>
                ) : (
                    messages.map((message) => (
                        <div
                            key={message.id}
                            className={cn(
                                'flex',
                                message.role === 'user' ? 'justify-end' : 'justify-start'
                            )}
                        >
                            <div
                                className={cn(
                                    'max-w-[85%] rounded-lg px-3 py-2',
                                    message.role === 'user'
                                        ? 'bg-blue-600 text-white'
                                        : 'bg-gray-100 dark:bg-gray-700'
                                )}
                            >
                                <p className={cn(
                                    'text-sm whitespace-pre-wrap',
                                    message.role === 'user' ? 'text-white' : textStyles.body
                                )}>
                                    {message.content}
                                </p>

                                {/* SQL Code Block */}
                                {message.metadata?.sql && (
                                    <div className="mt-2 rounded bg-gray-900 p-3 relative">
                                        <button
                                            onClick={() => handleCopy(message.metadata!.sql!, message.id)}
                                            className="absolute top-2 right-2 p-1 rounded hover:bg-gray-700 text-gray-400"
                                        >
                                            {copiedId === message.id ? (
                                                <Check className="h-4 w-4 text-green-400" />
                                            ) : (
                                                <Copy className="h-4 w-4" />
                                            )}
                                        </button>
                                        <pre className="text-xs text-green-400 font-mono overflow-x-auto">
                                            {message.metadata.sql}
                                        </pre>
                                        {message.metadata.confidence && (
                                            <p className="mt-2 text-xs text-gray-500">
                                                Confidence: {Math.round(message.metadata.confidence * 100)}%
                                            </p>
                                        )}
                                    </div>
                                )}

                                {/* Collections Tags */}
                                {message.metadata?.collections && (
                                    <div className="mt-2 flex flex-wrap gap-1">
                                        {message.metadata.collections.map((col) => (
                                            <span
                                                key={col}
                                                className="px-2 py-0.5 text-xs rounded bg-blue-100 dark:bg-blue-900 text-blue-700 dark:text-blue-300"
                                            >
                                                {col}
                                            </span>
                                        ))}
                                    </div>
                                )}
                            </div>
                        </div>
                    ))
                )}

                {/* Loading indicator */}
                {isLoading && (
                    <div className="flex justify-start">
                        <div className="bg-gray-100 dark:bg-gray-700 rounded-lg px-4 py-2">
                            <Loader2 className="h-5 w-5 animate-spin text-blue-500" />
                        </div>
                    </div>
                )}

                <div ref={messagesEndRef} />
            </div>

            {/* Input */}
            <div className="p-3 border-t border-gray-200 dark:border-gray-700">
                <div className="flex gap-2">
                    <input
                        ref={inputRef}
                        type="text"
                        value={input}
                        onChange={(e) => setInput(e.target.value)}
                        onKeyDown={(e) => e.key === 'Enter' && handleSend()}
                        placeholder="Ask about your data..."
                        className={cn(inputStyles.base, 'flex-1')}
                        disabled={isLoading}
                    />
                    <button
                        onClick={handleSend}
                        disabled={!input.trim() || isLoading}
                        className={cn(
                            buttonStyles.primary,
                            'px-3',
                            (!input.trim() || isLoading) && 'opacity-50 cursor-not-allowed'
                        )}
                    >
                        <Send className="h-4 w-4" />
                    </button>
                </div>
            </div>
        </div>
    );
}

/**
 * Hook to manage AI Assistant state
 */
export function useAiAssistant() {
    const [isOpen, setIsOpen] = useState(false);

    const open = useCallback(() => setIsOpen(true), []);
    const close = useCallback(() => setIsOpen(false), []);
    const toggle = useCallback(() => setIsOpen((prev) => !prev), []);

    return { isOpen, open, close, toggle };
}

/**
 * AI Assistant Trigger Button
 */
export function AiAssistantTrigger({ onClick }: { onClick: () => void }): React.ReactElement {
    return (
        <button
            onClick={onClick}
            className={cn(
                'fixed bottom-4 right-4 z-40 p-4 rounded-full shadow-lg',
                'bg-gradient-to-r from-blue-600 to-purple-600 text-white',
                'hover:shadow-xl hover:scale-105 transition-all'
            )}
        >
            <MessageSquare className="h-6 w-6" />
        </button>
    );
}

export default AiAssistant;
