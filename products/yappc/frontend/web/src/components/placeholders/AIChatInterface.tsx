/**
 * AIChatInterface Component
 *
 * Chat interface for AI assistant interactions within the YAPPC bootstrapping
 * and project workspace flows.
 *
 * @doc.type component
 * @doc.purpose AI assistant chat panel with message history and streaming support
 * @doc.layer product
 * @doc.pattern UI Component
 */

import { Bot, User, Send, Loader2, Sparkles } from 'lucide-react';
import React, { useRef, useEffect } from 'react';
import { useI18n } from '../../i18n/I18nProvider';

import { Button } from '@ghatana/design-system';
import { Textarea } from '../ui/Textarea';

// =============================================================================
// Types
// =============================================================================

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp?: Date;
}

interface AIChatInterfaceProps {
  messages?: ChatMessage[];
  onSendMessage?: (message: string) => void;
  onNodeClick?: (nodeId: string) => void;
  onValidationRequest?: (nodes: Record<string, unknown>) => void;
  isLoading?: boolean;
  className?: string;
}

// =============================================================================
// Message Bubble Component
// =============================================================================

const MessageBubble: React.FC<{ message: ChatMessage }> = ({ message }) => {
  const isUser = message.role === 'user';

  return (
    <div className={`flex gap-3 ${isUser ? 'flex-row-reverse' : 'flex-row'}`}>
      {/* Avatar */}
      <div
        className={`flex-shrink-0 w-7 h-7 rounded-full flex items-center justify-center ${
          isUser ? 'bg-info-bg text-info-color' : 'bg-surface-muted text-fg-muted'
        }`}
      >
        {isUser ? (
          <User className="w-4 h-4 text-white" />
        ) : (
          <Bot className="w-4 h-4 text-fg-muted" />
        )}
      </div>

      {/* Bubble */}
      <div
        className={`max-w-[80%] px-4 py-2.5 rounded-2xl text-sm leading-relaxed whitespace-pre-wrap break-words ${
          isUser
            ? 'bg-info-bg text-info-color rounded-tr-sm'
            : 'bg-surface-raised text-fg rounded-tl-sm'
        }`}
      >
        {message.content}
        {message.timestamp && (
          <div className="text-[10px] mt-1 text-fg-muted">
            {message.timestamp.toLocaleTimeString([], {
              hour: '2-digit',
              minute: '2-digit',
            })}
          </div>
        )}
      </div>
    </div>
  );
};

// =============================================================================
// Thinking Indicator
// =============================================================================

const ThinkingIndicator: React.FC = () => (
  <div className="flex gap-3">
    <div className="flex-shrink-0 w-7 h-7 rounded-full bg-surface-muted flex items-center justify-center">
      <Bot className="w-4 h-4 text-fg-muted" />
    </div>
    <div className="px-4 py-3 bg-surface rounded-2xl rounded-tl-sm flex items-center gap-1.5">
      <span className="w-1.5 h-1.5 rounded-full bg-surface-muted animate-bounce [animation-delay:0ms]" />
      <span className="w-1.5 h-1.5 rounded-full bg-surface-muted animate-bounce [animation-delay:150ms]" />
      <span className="w-1.5 h-1.5 rounded-full bg-surface-muted animate-bounce [animation-delay:300ms]" />
    </div>
  </div>
);

// =============================================================================
// Empty State
// =============================================================================

const EmptyState: React.FC = () => (
  <div className="flex flex-col items-center justify-center h-full gap-3 text-center px-6">
    <div className="w-12 h-12 rounded-full bg-info-bg flex items-center justify-center">
      <Sparkles className="w-6 h-6 text-info-color" />
    </div>
    <div>
      <p className="text-sm font-medium text-fg-muted">Guided Assistant</p>
      <p className="text-xs text-fg-muted mt-1">
        Ask me to help design, validate, or explain your project architecture.
      </p>
    </div>
  </div>
);

// =============================================================================
// AIChatInterface Component
// =============================================================================

export const AIChatInterface: React.FC<AIChatInterfaceProps> = ({
    const { t } = useI18n();
  messages = [],
  onSendMessage,
  isLoading = false,
  className = '',
}) => {
  const [input, setInput] = React.useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Auto-scroll to bottom when messages change
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isLoading]);

  const handleSend = () => {
    const trimmed = input.trim();
    if (!trimmed || isLoading) return;
    onSendMessage?.(trimmed);
    setInput('');
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleTextareaChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInput(e.target.value);
    // Auto-resize textarea
    e.target.style.height = 'auto';
    e.target.style.height = `${Math.min(e.target.scrollHeight, 120)}px`;
  };

  const visibleMessages = messages.filter((m) => m.role !== 'system');

  return (
    <div
      className={`flex flex-col h-full bg-surface border border-border rounded-lg overflow-hidden ${className}`}
    >
      {/* Header */}
      <div className="flex items-center gap-2 px-4 py-3 border-b border-border flex-shrink-0">
        <div className="w-7 h-7 rounded-full bg-info-bg flex items-center justify-center">
          <Sparkles className="w-4 h-4 text-info-color" />
        </div>
        <div>
          <h3 className="text-sm font-semibold text-fg">Assistant</h3>
          <p className="text-[10px] text-fg-muted">Powered by YAPPC Agent</p>
        </div>
        <div className="ml-auto flex items-center gap-1.5">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
          <span className="text-[10px] text-fg-muted">Ready</span>
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {visibleMessages.length === 0 && !isLoading ? (
          <EmptyState />
        ) : (
          <>
            {visibleMessages.map((msg, idx) => (
              <MessageBubble key={idx} message={msg} />
            ))}
            {isLoading && <ThinkingIndicator />}
            <div ref={messagesEndRef} />
          </>
        )}
      </div>

      {/* Input */}
      <div className="flex-shrink-0 p-3 border-t border-border">
        <div className="flex items-end gap-2 bg-surface rounded-xl px-3 py-2 border border-border focus-within:border-violet-500 transition-colors">
          <Textarea
            ref={textareaRef}
            rows={1}
            value={input}
            onChange={handleTextareaChange}
            onKeyDown={handleKeyDown}
            placeholder={t('aiChat.placeholder')}
            className="flex-1 bg-transparent text-sm text-fg placeholder-fg-muted focus:outline-none resize-none min-h-[24px] max-h-[120px] leading-6"
            resize="none"
          />
          <Button
            onClick={handleSend}
            disabled={!input.trim() || isLoading}
            aria-label={t('aiChat.sendMessage')}
            variant="primary"
            className="flex-shrink-0 h-8 min-w-8 px-2"
          >
            {isLoading ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <Send className="w-4 h-4" />
            )}
          </Button>
        </div>
        <p className="text-[10px] text-fg-muted mt-1.5 px-1">
          Shift+Enter for new line · Enter to send
        </p>
      </div>
    </div>
  );
};
