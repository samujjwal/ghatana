import React, { useState, useRef, useEffect } from 'react';
import { Sparkles, Send, Bot, User, X, Check } from 'lucide-react';

import type { SimulationManifest } from '@ghatana/tutorputor-contracts/v1/simulation';

interface Message {
    id: string;
    role: 'user' | 'ai';
    text: string;
    timestamp: Date;
}

interface Suggestion {
    id: string;
    text: string;
    action?: () => void;
}

interface SimulationAIChatProps {
    manifest: SimulationManifest;
    onApplySuggestion?: (suggestion: string) => void;
    onManifestUpdate: (manifest: SimulationManifest) => void;
}

export function SimulationAIChat({ manifest, onApplySuggestion, onManifestUpdate }: SimulationAIChatProps) {
    const [messages, setMessages] = useState<Message[]>([
        {
            id: 'welcome',
            role: 'ai',
            text: "Hi! I'm your AI co-pilot. Describe what you want to simulate, and I'll help you build it.",
            timestamp: new Date(),
        },
    ]);
    const [input, setInput] = useState('');
    const [isTyping, setIsTyping] = useState(false);
    const messagesEndRef = useRef<HTMLDivElement>(null);

    const suggestions: Suggestion[] = [
        { id: 's1', text: 'Add a timer display to show periods' },
        { id: 's2', text: 'Consider adding a third pendulum for comparison' },
        { id: 's3', text: 'Add annotation: "Notice periods are equal"' },
    ];

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    };

    useEffect(() => {
        scrollToBottom();
    }, [messages]);

    const handleSend = async () => {
        if (!input.trim()) return;

        const userMsg: Message = {
            id: Date.now().toString(),
            role: 'user',
            text: input,
            timestamp: new Date(),
        };

        setMessages(prev => [...prev, userMsg]);
        setInput('');
        setIsTyping(true);

        try {
            // Call API to refine simulation
            const response = await fetch('/api/v1/simulations/refine', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    sessionId: 'session-' + Date.now(), // Simple session ID for now
                    manifest,
                    userInput: userMsg.text,
                }),
            });

            if (!response.ok) {
                throw new Error('Failed to refine simulation');
            }

            const result = await response.json();

            const aiMsg: Message = {
                id: (Date.now() + 1).toString(),
                role: 'ai',
                text: result.response || "I've updated the simulation.",
                timestamp: new Date(),
            };
            setMessages(prev => [...prev, aiMsg]);

            if (result.success && result.manifest) {
                onManifestUpdate(result.manifest);
            }
        } catch (error) {
            console.error('AI Error:', error);
            const errorMsg: Message = {
                id: (Date.now() + 1).toString(),
                role: 'ai',
                text: "Sorry, I encountered an error processing your request.",
                timestamp: new Date(),
            };
            setMessages(prev => [...prev, errorMsg]);
        } finally {
            setIsTyping(false);
        }
    };

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    return (
        <div className="flex flex-col h-full bg-white dark:bg-gray-800 border-l border-gray-200 dark:border-gray-700 w-80">
            {/* Header */}
            <div className="p-4 border-b border-gray-200 dark:border-gray-700 flex items-center gap-2 bg-gray-50 dark:bg-gray-900">
                <Sparkles className="w-5 h-5 text-purple-600" />
                <h3 className="font-semibold text-gray-900 dark:text-white">AI Co-Pilot</h3>
            </div>

            {/* Messages */}
            <div className="flex-1 overflow-y-auto p-4 space-y-4">
                {messages.map((msg) => (
                    <div
                        key={msg.id}
                        className={`flex gap-3 ${msg.role === 'user' ? 'flex-row-reverse' : ''}`}
                    >
                        <div
                            className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0
                                ${msg.role === 'ai' ? 'bg-purple-100 text-purple-600' : 'bg-blue-100 text-blue-600'}`}
                        >
                            {msg.role === 'ai' ? <Bot className="w-5 h-5" /> : <User className="w-5 h-5" />}
                        </div>
                        <div
                            className={`max-w-[80%] rounded-2xl px-4 py-2 text-sm
                                ${msg.role === 'user'
                                    ? 'bg-blue-600 text-white rounded-tr-none'
                                    : 'bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-200 rounded-tl-none'}`}
                        >
                            {msg.text}
                        </div>
                    </div>
                ))}
                {isTyping && (
                    <div className="flex gap-3">
                        <div className="w-8 h-8 rounded-full bg-purple-100 text-purple-600 flex items-center justify-center flex-shrink-0">
                            <Bot className="w-5 h-5" />
                        </div>
                        <div className="bg-gray-100 dark:bg-gray-700 rounded-2xl rounded-tl-none px-4 py-3 flex gap-1">
                            <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                            <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                            <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                        </div>
                    </div>
                )}
                <div ref={messagesEndRef} />
            </div>

            {/* Suggestions */}
            <div className="p-4 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50">
                <div className="text-xs font-medium text-gray-500 mb-2 uppercase tracking-wider">Suggestions</div>
                <div className="space-y-2">
                    {suggestions.map((suggestion) => (
                        <button
                            key={suggestion.id}
                            onClick={() => onApplySuggestion?.(suggestion.text)}
                            className="w-full text-left text-sm p-2 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg hover:border-purple-400 hover:shadow-sm transition-all flex items-start gap-2 group"
                        >
                            <Sparkles className="w-4 h-4 text-purple-500 mt-0.5 flex-shrink-0" />
                            <span className="text-gray-700 dark:text-gray-300 group-hover:text-purple-700 dark:group-hover:text-purple-300">
                                {suggestion.text}
                            </span>
                        </button>
                    ))}
                </div>
            </div>

            {/* Input */}
            <div className="p-4 border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
                <div className="relative">
                    <input
                        type="text"
                        value={input}
                        onChange={(e) => setInput(e.target.value)}
                        onKeyDown={handleKeyDown}
                        placeholder="Ask AI to change simulation..."
                        className="w-full pl-4 pr-10 py-2.5 bg-gray-100 dark:bg-gray-900 border-transparent focus:bg-white dark:focus:bg-gray-800 border focus:border-purple-500 rounded-xl text-sm transition-all"
                    />
                    <button
                        onClick={handleSend}
                        disabled={!input.trim() || isTyping}
                        className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 text-purple-600 hover:bg-purple-100 rounded-lg disabled:opacity-50 disabled:hover:bg-transparent transition-colors"
                    >
                        <Send className="w-4 h-4" />
                    </button>
                </div>
            </div>
        </div>
    );
}
