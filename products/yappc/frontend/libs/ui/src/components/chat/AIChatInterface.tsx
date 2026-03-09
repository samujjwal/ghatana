/**
 * AI Chat Interface Component
 *
 * @description Full-featured AI conversation interface with markdown support,
 * code blocks, streaming responses, and action suggestions.
 *
 * @doc.type component
 * @doc.purpose AI conversation UI
 * @doc.layer presentation
 * @doc.phase bootstrapping
 */

import React, {
  useState,
  useRef,
  useEffect,
  useCallback,
  useMemo,
  forwardRef,
} from 'react';
import { useAtomValue, useSetAtom } from 'jotai';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Send,
  Paperclip,
  Mic,
  MicOff,
  RefreshCw,
  Copy,
  Check,
  ThumbsUp,
  ThumbsDown,
  MoreHorizontal,
  Code2,
  FileText,
  Image,
  ChevronDown,
  Sparkles,
  Loader2,
  Bot,
  User,
  AlertCircle,
  X,
} from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism';
import remarkGfm from 'remark-gfm';

import { cn } from '@ghatana/ui';
import { Button } from '@ghatana/ui';
import { Textarea } from '@ghatana/yappc-ui';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@ghatana/yappc-ui';
import { Tooltip } from '@ghatana/ui';
import { TooltipContent, TooltipTrigger } from '@ghatana/yappc-ui';

import {
  conversationHistoryAtom,
  inputModeAtom,
  aiAgentStateAtom,
} from '@ghatana/yappc-canvas';

// =============================================================================
// Types
// =============================================================================

interface ConversationTurn {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: string;
  tokens?: number;
  processingTime?: number;
  metadata?: {
    intent?: string;
    confidence?: number;
    suggestedActions?: string[];
    extractedEntities?: Record<string, string>;
    codeBlocks?: Array<{
      language: string;
      code: string;
      filename?: string;
    }>;
  };
  reactions?: Array<{ userId: string; type: string }>;
}

interface AIChatInterfaceProps {
  sessionId: string;
  onSendMessage: (content: string, attachments?: File[]) => Promise<void>;
  onRegenerateResponse?: (messageId: string) => Promise<void>;
  onReaction?: (messageId: string, type: 'thumbs_up' | 'thumbs_down') => void;
  onSuggestionClick?: (suggestion: string) => void;
  isStreaming?: boolean;
  streamContent?: string;
  placeholder?: string;
  maxLength?: number;
  showVoiceInput?: boolean;
  className?: string;
}

// =============================================================================
// Subcomponents
// =============================================================================

const MessageContent = React.memo(({ content }: { content: string }) => (
  <ReactMarkdown
    remarkPlugins={[remarkGfm]}
    components={{
      code({ node, inline, className, children, ...props }) {
        const match = /language-(\w+)/.exec(className || '');
        const language = match ? match[1] : '';
        
        if (!inline && language) {
          return (
            <CodeBlock
              code={String(children).replace(/\n$/, '')}
              language={language}
            />
          );
        }
        
        return (
          <code
            className={cn(
              'px-1.5 py-0.5 rounded bg-zinc-800 text-zinc-200 text-sm font-mono',
              className
            )}
            {...props}
          >
            {children}
          </code>
        );
      },
      p({ children }) {
        return <p className="mb-3 last:mb-0 leading-relaxed">{children}</p>;
      },
      ul({ children }) {
        return <ul className="mb-3 ml-4 list-disc space-y-1">{children}</ul>;
      },
      ol({ children }) {
        return <ol className="mb-3 ml-4 list-decimal space-y-1">{children}</ol>;
      },
      li({ children }) {
        return <li className="leading-relaxed">{children}</li>;
      },
      a({ href, children }) {
        return (
          <a
            href={href}
            target="_blank"
            rel="noopener noreferrer"
            className="text-blue-400 hover:text-blue-300 underline underline-offset-2"
          >
            {children}
          </a>
        );
      },
      blockquote({ children }) {
        return (
          <blockquote className="border-l-4 border-zinc-600 pl-4 italic text-zinc-400 mb-3">
            {children}
          </blockquote>
        );
      },
      table({ children }) {
        return (
          <div className="overflow-x-auto mb-3">
            <table className="min-w-full divide-y divide-zinc-700 border border-zinc-700 rounded">
              {children}
            </table>
          </div>
        );
      },
      th({ children }) {
        return (
          <th className="px-3 py-2 bg-zinc-800 text-left text-sm font-semibold">
            {children}
          </th>
        );
      },
      td({ children }) {
        return (
          <td className="px-3 py-2 border-t border-zinc-700 text-sm">
            {children}
          </td>
        );
      },
    }}
  >
    {content}
  </ReactMarkdown>
));

MessageContent.displayName = 'MessageContent';

const CodeBlock = React.memo(({
  code,
  language,
  filename,
}: {
  code: string;
  language: string;
  filename?: string;
}) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = useCallback(() => {
    navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }, [code]);

  return (
    <div className="relative group my-3 rounded-lg overflow-hidden border border-zinc-700">
      <div className="flex items-center justify-between px-4 py-2 bg-zinc-800 border-b border-zinc-700">
        <div className="flex items-center gap-2">
          <Code2 className="w-4 h-4 text-zinc-400" />
          <span className="text-sm text-zinc-400">{filename || language}</span>
        </div>
        <Button
          variant="ghost"
          size="sm"
          onClick={handleCopy}
          className="opacity-0 group-hover:opacity-100 transition-opacity"
        >
          {copied ? (
            <Check className="w-4 h-4 text-green-400" />
          ) : (
            <Copy className="w-4 h-4" />
          )}
        </Button>
      </div>
      <SyntaxHighlighter
        language={language}
        style={oneDark}
        customStyle={{
          margin: 0,
          padding: '1rem',
          background: '#18181b',
          fontSize: '0.875rem',
        }}
        showLineNumbers
      >
        {code}
      </SyntaxHighlighter>
    </div>
  );
});

CodeBlock.displayName = 'CodeBlock';

const MessageBubble = React.memo(({
  message,
  onRegenerateResponse,
  onReaction,
  isStreaming,
}: {
  message: ConversationTurn;
  onRegenerateResponse?: (messageId: string) => void;
  onReaction?: (messageId: string, type: 'thumbs_up' | 'thumbs_down') => void;
  isStreaming?: boolean;
}) => {
  const isUser = message.role === 'user';
  const isSystem = message.role === 'system';

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -10 }}
      className={cn(
        'flex gap-3 px-4 py-3',
        isUser && 'flex-row-reverse'
      )}
    >
      {/* Avatar */}
      <div
        className={cn(
          'flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center',
          isUser ? 'bg-blue-600' : 'bg-gradient-to-br from-violet-600 to-purple-600'
        )}
      >
        {isUser ? (
          <User className="w-4 h-4 text-white" />
        ) : (
          <Bot className="w-4 h-4 text-white" />
        )}
      </div>

      {/* Content */}
      <div className={cn('flex-1 max-w-[80%]', isUser && 'text-right')}>
        <div
          className={cn(
            'inline-block rounded-2xl px-4 py-3 text-sm',
            isUser
              ? 'bg-blue-600 text-white rounded-br-md'
              : isSystem
              ? 'bg-amber-500/10 text-amber-200 border border-amber-500/20'
              : 'bg-zinc-800 text-zinc-100 rounded-bl-md'
          )}
        >
          <MessageContent content={message.content} />
          
          {/* Streaming indicator */}
          {isStreaming && !isUser && (
            <span className="inline-block w-2 h-4 ml-1 bg-zinc-400 animate-pulse" />
          )}
        </div>

        {/* Message metadata and actions */}
        {!isUser && !isSystem && (
          <div className="flex items-center gap-2 mt-2 text-xs text-zinc-500">
            <span>
              {new Date(message.timestamp).toLocaleTimeString([], {
                hour: '2-digit',
                minute: '2-digit',
              })}
            </span>
            {message.processingTime && (
              <span>• {(message.processingTime / 1000).toFixed(1)}s</span>
            )}
            {message.tokens && <span>• {message.tokens} tokens</span>}
            
            <div className="flex-1" />
            
            {/* Action buttons */}
            <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-6 w-6"
                    onClick={() => onReaction?.(message.id, 'thumbs_up')}
                  >
                    <ThumbsUp className="w-3 h-3" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Helpful</TooltipContent>
              </Tooltip>
              
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-6 w-6"
                    onClick={() => onReaction?.(message.id, 'thumbs_down')}
                  >
                    <ThumbsDown className="w-3 h-3" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Not helpful</TooltipContent>
              </Tooltip>
              
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-6 w-6"
                    onClick={() => onRegenerateResponse?.(message.id)}
                  >
                    <RefreshCw className="w-3 h-3" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Regenerate</TooltipContent>
              </Tooltip>
            </div>
          </div>
        )}

        {/* Suggested actions */}
        {message.metadata?.suggestedActions && message.metadata.suggestedActions.length > 0 && (
          <div className="flex flex-wrap gap-2 mt-3">
            {message.metadata.suggestedActions.map((action, index) => (
              <Button
                key={index}
                variant="outline"
                size="sm"
                className="text-xs"
              >
                <Sparkles className="w-3 h-3 mr-1" />
                {action}
              </Button>
            ))}
          </div>
        )}
      </div>
    </motion.div>
  );
});

MessageBubble.displayName = 'MessageBubble';

const ThinkingIndicator = React.memo(({
  stage,
  progress,
  currentTask,
}: {
  stage?: string;
  progress?: number;
  currentTask?: string;
}) => (
  <motion.div
    initial={{ opacity: 0, y: 10 }}
    animate={{ opacity: 1, y: 0 }}
    exit={{ opacity: 0, y: -10 }}
    className="flex gap-3 px-4 py-3"
  >
    <div className="flex-shrink-0 w-8 h-8 rounded-full bg-gradient-to-br from-violet-600 to-purple-600 flex items-center justify-center">
      <Loader2 className="w-4 h-4 text-white animate-spin" />
    </div>
    <div className="flex-1">
      <div className="inline-flex items-center gap-2 rounded-2xl rounded-bl-md bg-zinc-800 px-4 py-3">
        <div className="flex gap-1">
          <motion.span
            animate={{ opacity: [0.4, 1, 0.4] }}
            transition={{ duration: 1.5, repeat: Infinity, delay: 0 }}
            className="w-2 h-2 rounded-full bg-violet-400"
          />
          <motion.span
            animate={{ opacity: [0.4, 1, 0.4] }}
            transition={{ duration: 1.5, repeat: Infinity, delay: 0.2 }}
            className="w-2 h-2 rounded-full bg-violet-400"
          />
          <motion.span
            animate={{ opacity: [0.4, 1, 0.4] }}
            transition={{ duration: 1.5, repeat: Infinity, delay: 0.4 }}
            className="w-2 h-2 rounded-full bg-violet-400"
          />
        </div>
        {currentTask && (
          <span className="text-sm text-zinc-400">{currentTask}</span>
        )}
      </div>
      {progress !== undefined && (
        <div className="mt-2 w-48 h-1 rounded-full bg-zinc-700 overflow-hidden">
          <motion.div
            className="h-full bg-violet-500"
            initial={{ width: 0 }}
            animate={{ width: `${progress}%` }}
          />
        </div>
      )}
    </div>
  </motion.div>
));

ThinkingIndicator.displayName = 'ThinkingIndicator';

// =============================================================================
// Main Component
// =============================================================================

export const AIChatInterface = forwardRef<HTMLDivElement, AIChatInterfaceProps>(
  (
    {
      sessionId,
      onSendMessage,
      onRegenerateResponse,
      onReaction,
      onSuggestionClick,
      isStreaming = false,
      streamContent,
      placeholder = 'Describe your project or ask a question...',
      maxLength = 10000,
      showVoiceInput = true,
      className,
    },
    ref
  ) => {
    const conversationHistory = useAtomValue(conversationHistoryAtom);
    const aiAgentState = useAtomValue(aiAgentStateAtom);
    const inputMode = useAtomValue(inputModeAtom);

    const [inputValue, setInputValue] = useState('');
    const [attachments, setAttachments] = useState<File[]>([]);
    const [isRecording, setIsRecording] = useState(false);
    const [isSending, setIsSending] = useState(false);

    const messagesEndRef = useRef<HTMLDivElement>(null);
    const textareaRef = useRef<HTMLTextAreaElement>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);

    // Auto-scroll to bottom
    useEffect(() => {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [conversationHistory, isStreaming, streamContent]);

    // Auto-resize textarea
    useEffect(() => {
      if (textareaRef.current) {
        textareaRef.current.style.height = 'auto';
        textareaRef.current.style.height = `${Math.min(
          textareaRef.current.scrollHeight,
          200
        )}px`;
      }
    }, [inputValue]);

    const handleSubmit = useCallback(async () => {
      const trimmedValue = inputValue.trim();
      if (!trimmedValue && attachments.length === 0) return;
      if (isSending) return;

      setIsSending(true);
      try {
        await onSendMessage(trimmedValue, attachments);
        setInputValue('');
        setAttachments([]);
      } catch (error) {
        console.error('Failed to send message:', error);
      } finally {
        setIsSending(false);
      }
    }, [inputValue, attachments, isSending, onSendMessage]);

    const handleKeyDown = useCallback(
      (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !e.shiftKey) {
          e.preventDefault();
          handleSubmit();
        }
      },
      [handleSubmit]
    );

    const handleFileSelect = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
      const files = Array.from(e.target.files || []);
      setAttachments((prev) => [...prev, ...files]);
    }, []);

    const handleRemoveAttachment = useCallback((index: number) => {
      setAttachments((prev) => prev.filter((_, i) => i !== index));
    }, []);

    const toggleRecording = useCallback(() => {
      setIsRecording((prev) => !prev);
      // Voice recording logic would go here
    }, []);

    const suggestions = useMemo(
      () => [
        'Describe a new web application',
        'Help me design an API',
        'Create a database schema',
        'Set up authentication',
      ],
      []
    );

    return (
      <div ref={ref} className={cn('flex flex-col h-full', className)}>
        {/* Messages area */}
        <div className="flex-1 overflow-y-auto">
          {/* Welcome message for empty state */}
          {conversationHistory.length === 0 && (
            <div className="flex flex-col items-center justify-center h-full p-8 text-center">
              <div className="w-16 h-16 rounded-full bg-gradient-to-br from-violet-600 to-purple-600 flex items-center justify-center mb-6">
                <Sparkles className="w-8 h-8 text-white" />
              </div>
              <h2 className="text-2xl font-semibold text-white mb-2">
                Welcome to YAPPC
              </h2>
              <p className="text-zinc-400 mb-8 max-w-md">
                I'm your AI assistant. Describe your project idea, and I'll help
                you design and bootstrap it.
              </p>
              <div className="flex flex-wrap justify-center gap-2">
                {suggestions.map((suggestion, index) => (
                  <Button
                    key={index}
                    variant="outline"
                    onClick={() => {
                      setInputValue(suggestion);
                      textareaRef.current?.focus();
                    }}
                    className="text-sm"
                  >
                    <Sparkles className="w-4 h-4 mr-2" />
                    {suggestion}
                  </Button>
                ))}
              </div>
            </div>
          )}

          {/* Message list */}
          <AnimatePresence mode="popLayout">
            {conversationHistory.map((message) => (
              <MessageBubble
                key={message.id}
                message={message}
                onRegenerateResponse={onRegenerateResponse}
                onReaction={onReaction}
              />
            ))}
          </AnimatePresence>

          {/* Streaming message */}
          {isStreaming && streamContent && (
            <MessageBubble
              message={{
                id: 'streaming',
                role: 'assistant',
                content: streamContent,
                timestamp: new Date().toISOString(),
              }}
              isStreaming
            />
          )}

          {/* Thinking indicator */}
          {aiAgentState.isProcessing && !streamContent && (
            <ThinkingIndicator
              stage={aiAgentState.currentTask}
              progress={aiAgentState.progress}
              currentTask={aiAgentState.currentTask}
            />
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Input area */}
        <div className="border-t border-zinc-800 p-4">
          {/* Attachments preview */}
          {attachments.length > 0 && (
            <div className="flex flex-wrap gap-2 mb-3">
              {attachments.map((file, index) => (
                <div
                  key={index}
                  className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-zinc-800 text-sm"
                >
                  {file.type.startsWith('image/') ? (
                    <Image className="w-4 h-4 text-zinc-400" />
                  ) : (
                    <FileText className="w-4 h-4 text-zinc-400" />
                  )}
                  <span className="truncate max-w-[120px]">{file.name}</span>
                  <button
                    onClick={() => handleRemoveAttachment(index)}
                    className="text-zinc-400 hover:text-zinc-200"
                  >
                    <X className="w-4 h-4" />
                  </button>
                </div>
              ))}
            </div>
          )}

          {/* Input row */}
          <div className="flex items-end gap-2">
            {/* Attachment button */}
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={isSending}
                >
                  <Paperclip className="w-5 h-5" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>Attach file</TooltipContent>
            </Tooltip>

            <input
              ref={fileInputRef}
              type="file"
              multiple
              accept="image/*,.pdf,.doc,.docx,.txt,.json,.yaml,.yml"
              onChange={handleFileSelect}
              className="hidden"
            />

            {/* Text input */}
            <div className="flex-1 relative">
              <Textarea
                ref={textareaRef}
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={placeholder}
                maxLength={maxLength}
                disabled={isSending}
                className={cn(
                  'min-h-[44px] max-h-[200px] resize-none pr-12',
                  'bg-zinc-800 border-zinc-700 focus:border-violet-500'
                )}
                rows={1}
              />
              <div className="absolute right-3 bottom-2 text-xs text-zinc-500">
                {inputValue.length}/{maxLength}
              </div>
            </div>

            {/* Voice input */}
            {showVoiceInput && (
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant={isRecording ? 'destructive' : 'ghost'}
                    size="icon"
                    onClick={toggleRecording}
                    disabled={isSending}
                  >
                    {isRecording ? (
                      <MicOff className="w-5 h-5" />
                    ) : (
                      <Mic className="w-5 h-5" />
                    )}
                  </Button>
                </TooltipTrigger>
                <TooltipContent>
                  {isRecording ? 'Stop recording' : 'Voice input'}
                </TooltipContent>
              </Tooltip>
            )}

            {/* Send button */}
            <Button
              onClick={handleSubmit}
              disabled={
                isSending || (!inputValue.trim() && attachments.length === 0)
              }
              className="bg-violet-600 hover:bg-violet-700"
            >
              {isSending ? (
                <Loader2 className="w-5 h-5 animate-spin" />
              ) : (
                <Send className="w-5 h-5" />
              )}
            </Button>
          </div>
        </div>
      </div>
    );
  }
);

AIChatInterface.displayName = 'AIChatInterface';

export default AIChatInterface;
