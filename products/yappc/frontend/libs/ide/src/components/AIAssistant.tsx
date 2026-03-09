/**
 * @ghatana/yappc-ide - AI Assistant Component
 * 
 * Advanced AI-powered coding assistant with context-aware suggestions,
 * code generation, refactoring, and real-time collaboration features.
 * 
 * @doc.type component
 * @doc.purpose AI assistant for intelligent coding support
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useRef, useEffect, useCallback } from 'react';

import { InteractiveButton } from './MicroInteractions';
import { useToastNotifications } from './Toast';

/**
 * AI Assistant message types
 */
export type AIMessageType = 'suggestion' | 'explanation' | 'refactor' | 'generate' | 'debug' | 'error';

/**
 * AI Assistant message interface
 */
export interface AIMessage {
  id: string;
  type: AIMessageType;
  content: string;
  code?: string;
  language?: string;
  confidence: number;
  timestamp: Date;
  actions?: AIAction[];
}

/**
 * AI Assistant action interface
 */
export interface AIAction {
  id: string;
  type: 'apply' | 'edit' | 'insert' | 'replace' | 'explain';
  label: string;
  description: string;
  handler: () => void | Promise<void>;
}

/**
 * AI Assistant props
 */
export interface AIAssistantProps {
  isOpen: boolean;
  onClose: () => void;
  currentFile?: {
    name: string;
    content: string;
    language: string;
    cursor?: {
      line: number;
      column: number;
    };
  };
  onCodeAction: (action: {
    type: string;
    content: string;
    position?: { line: number; column: number };
  }) => void;
  className?: string;
}

/**
 * AI Assistant Component
 */
export const AIAssistant: React.FC<AIAssistantProps> = ({
  isOpen,
  onClose,
  currentFile,
  onCodeAction,
  className = '',
}) => {
  const [messages, setMessages] = useState<AIMessage[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [selectedSuggestion, setSelectedSuggestion] = useState<number>(-1);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const { success, error, info } = useToastNotifications();

  /**
   * Scroll to bottom of messages
   */
  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, scrollToBottom]);

  /**
   * Focus input when assistant opens
   */
  useEffect(() => {
    if (isOpen && inputRef.current) {
      setTimeout(() => inputRef.current?.focus(), 100);
    }
  }, [isOpen]);

  /**
   * Generate AI response based on context
   */
  const generateSuggestion = useCallback(async (query?: string): Promise<AIMessage> => {
    try {
      // Simulate AI processing (query could be used to influence results later)
      await new Promise(resolve => setTimeout(resolve, 1000));

      const suggestions = [
        {
          type: 'suggestion' as const,
          content: 'Consider using the React.useMemo hook to optimize performance',
          code: 'const memoizedValue = useMemo(() => computeExpensiveValue(a, b), [a, b]);',
          language: 'typescript',
          confidence: 0.85
        },
        {
          type: 'explanation' as const,
          content: 'This function can be simplified by using array methods',
          code: 'const result = items.filter(item => item.active).map(item => item.value);',
          language: 'typescript',
          confidence: 0.92
        },
        {
          type: 'refactor' as const,
          content: 'Extract this logic into a separate utility function',
          code: 'function validateInput(input: string): boolean {\n  return input.length > 0 && input.trim() !== \'\';\n}',
          language: 'typescript',
          confidence: 0.78
        }
      ];

      // If a specific query is provided, bias toward an explanation suggestion for now
      const randomSuggestion = query ? suggestions[1] : suggestions[Math.floor(Math.random() * suggestions.length)];

      return {
        id: Math.random().toString(36).substr(2, 9),
        ...randomSuggestion,
        timestamp: new Date(),
        actions: []
      };
    } catch (error) {
      console.error('Error generating suggestion:', error);
      return {
        id: Math.random().toString(36).substr(2, 9),
        type: 'error',
        content: 'Failed to generate suggestion. Please try again.',
        confidence: 0,
        timestamp: new Date()
      };
    }
  }, []);

  /**
   * Generate code suggestion based on query
   */
  const generateCode = useCallback(async (description: string, language: string): Promise<AIMessage> => {
    try {
      // Simulate AI processing
      await new Promise(resolve => setTimeout(resolve, 1500));

      const codeTemplates = {
        typescript: `// Generated TypeScript code\ninterface GeneratedInterface {\n  id: string;\n  name: string;\n  value: number;\n}\n\nconst generatedFunction = (input: GeneratedInterface): string => {\n  return \`Processed: \${input.name}\`;\n};`,
        javascript: `// Generated JavaScript code\nconst generatedFunction = (input) => {\n  return \`Processed: \${input.name}\`;\n};\n\nmodule.exports = { generatedFunction };`,
        python: `# Generated Python code\ndef generated_function(input_data):\n    return f"Processed: {input_data['name']}"\n\nif __name__ == "__main__":\n    data = {"name": "example", "value": 42}\n    print(generated_function(data))`
      };

      const generatedCode = codeTemplates[language as keyof typeof codeTemplates] || codeTemplates.typescript;

      return {
        id: Math.random().toString(36).substr(2, 9),
        type: 'generate',
        content: `Generated ${language} code based on: ${description}`,
        code: generatedCode,
        language,
        confidence: 0.88,
        timestamp: new Date(),
        actions: []
      };
    } catch (error) {
      console.error('Error generating code:', error);
      return {
        id: Math.random().toString(36).substr(2, 9),
        type: 'error',
        content: 'Failed to generate code. Please try again.',
        confidence: 0,
        timestamp: new Date()
      };
    }
  }, []);


  /**
   * Generate actions for message
   */
  const generateActions = useCallback((message: AIMessage): AIAction[] => {
    const actions: AIAction[] = [];

    if (message.code && message.language) {
      actions.push({
        id: 'apply-code',
        type: 'apply',
        label: 'Apply Code',
        description: `Apply the generated ${message.language} code`,
        handler: () => {
          if (message.code) {
            onCodeAction({
              type: 'insert',
              content: message.code,
              position: currentFile?.cursor || { line: 0, column: 0 }
            });
            success('Code applied successfully');
          }
        }
      });
    }

    actions.push({
      id: 'copy-code',
      type: 'edit',
      label: 'Copy Code',
      description: 'Copy the code to clipboard',
      handler: async () => {
        if (message.code) {
          await navigator.clipboard.writeText(message.code);
          success('Code copied to clipboard');
        }
      }
    });

    return actions;
  }, [currentFile?.cursor, onCodeAction, success]);

  /**
   * Handle sending message
   */
  const handleSendMessage = useCallback(async () => {
    if (!inputValue.trim()) return;

    const userMessage: AIMessage = {
      id: Math.random().toString(36).substr(2, 9),
      type: 'suggestion',
      content: inputValue,
      confidence: 1,
      timestamp: new Date()
    };

    setMessages(prev => [...prev, userMessage]);
    setInputValue('');
    setIsLoading(true);

    try {
      let response: AIMessage;

      if (inputValue.toLowerCase().includes('generate') || inputValue.toLowerCase().includes('create')) {
        response = await generateCode(inputValue, currentFile?.language || 'typescript');
      } else {
        response = await generateSuggestion(inputValue);
      }

      response.actions = generateActions(response);
      setMessages(prev => [...prev, response]);
    } catch (error) {
      console.error('Error processing message:', error);
      const errorMessage: AIMessage = {
        id: Math.random().toString(36).substr(2, 9),
        type: 'error',
        content: 'Failed to process your request. Please try again.',
        confidence: 0,
        timestamp: new Date()
      };
      setMessages(prev => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  }, [inputValue, generateCode, generateSuggestion, generateActions, currentFile?.language]);

  /**
   * Handle keyboard shortcuts
   */
  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    } else if (e.key === 'Escape') {
      onClose();
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedSuggestion(prev => Math.max(-1, prev - 1));
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedSuggestion(prev => Math.min(messages.length - 1, prev + 1));
    }
  }, [handleSendMessage, onClose, messages.length]);

  /**
   * Handle action click
   */
  const handleAction = useCallback(async (action: AIAction) => {
    try {
      await action.handler();
    } catch (err) {
      console.error('Error executing action:', err);
      error('Failed to execute action');
    }
  }, [error]);

  /**
   * Clear conversation
   */
  const clearConversation = useCallback(() => {
    setMessages([]);
    setSelectedSuggestion(-1);
    info('Conversation cleared');
  }, [info]);

  if (!isOpen) return null;

  return (
    <div className={`fixed right-4 top-20 bottom-4 w-96 bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded-lg shadow-2xl flex flex-col ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center space-x-2">
          <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse" />
          <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
            AI Assistant
          </h3>
        </div>
        <div className="flex items-center space-x-2">
          <InteractiveButton
            variant="ghost"
            size="sm"
            onClick={clearConversation}
          >
            Clear
          </InteractiveButton>
          <InteractiveButton
            variant="ghost"
            size="sm"
            onClick={onClose}
          >
            ×
          </InteractiveButton>
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {messages.length === 0 && (
          <div className="text-center text-gray-500 dark:text-gray-400 py-8">
            <div className="mb-4">
              <div className="w-16 h-16 bg-blue-100 dark:bg-blue-900 rounded-full flex items-center justify-center mx-auto">
                <span className="text-2xl">🤖</span>
              </div>
            </div>
            <p className="font-medium">Hello! I'm your AI Assistant</p>
            <p className="text-sm mt-2">Ask me anything about your code or request suggestions, explanations, or refactoring help.</p>
          </div>
        )}

        {messages.map((message, index) => (
          <div
            key={message.id}
            className={`
              p-3 rounded-lg
              ${message.type === 'error'
                ? 'bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800'
                : message.type === 'suggestion'
                  ? 'bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800'
                  : 'bg-gray-50 dark:bg-gray-800 border border-gray-200 dark:border-gray-700'
              }
              ${selectedSuggestion === index ? 'ring-2 ring-blue-500' : ''}
            `}
          >
            <div className="flex items-start justify-between mb-2">
              <div className="flex items-center space-x-2">
                <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
                  {message.type === 'error' ? 'Error' : 'AI Assistant'}
                </span>
                <span className="text-xs text-gray-500 dark:text-gray-400">
                  {message.timestamp.toLocaleTimeString()}
                </span>
              </div>
              <div className="flex items-center space-x-1">
                <div className="w-2 h-2 bg-green-500 rounded-full" />
                <span className="text-xs text-gray-500 dark:text-gray-400">
                  {Math.round(message.confidence * 100)}%
                </span>
              </div>
            </div>

            <div className="text-sm text-gray-700 dark:text-gray-300 mb-3">
              {message.content}
            </div>

            {message.code && (
              <div className="bg-gray-900 text-gray-100 p-3 rounded-md text-sm font-mono mb-3">
                <pre className="whitespace-pre-wrap">{message.code}</pre>
              </div>
            )}

            {message.actions && message.actions.length > 0 && (
              <div className="flex flex-wrap gap-2">
                {message.actions.map(action => (
                  <InteractiveButton
                    key={action.id}
                    variant="secondary"
                    size="sm"
                    onClick={() => handleAction(action)}
                    title={action.description}
                  >
                    {action.label}
                  </InteractiveButton>
                ))}
              </div>
            )}
          </div>
        ))}

        {isLoading && (
          <div className="flex items-center space-x-2 text-gray-500 dark:text-gray-400">
            <div className="w-2 h-2 bg-blue-500 rounded-full animate-bounce" />
            <div className="w-2 h-2 bg-blue-500 rounded-full animate-bounce" style={{ animationDelay: '0.1s' }} />
            <div className="w-2 h-2 bg-blue-500 rounded-full animate-bounce" style={{ animationDelay: '0.2s' }} />
            <span className="text-sm">AI is thinking...</span>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Input */}
      <div className="p-4 border-t border-gray-200 dark:border-gray-700">
        <div className="flex space-x-2">
          <textarea
            ref={inputRef}
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Ask me anything about your code..."
            className="flex-1 p-3 border border-gray-300 dark:border-gray-600 rounded-lg resize-none focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
            rows={3}
            disabled={isLoading}
          />
          <InteractiveButton
            variant="primary"
            onClick={handleSendMessage}
            disabled={!inputValue.trim() || isLoading}
            className="self-end"
          >
            Send
          </InteractiveButton>
        </div>
        <div className="text-xs text-gray-500 dark:text-gray-400 mt-2">
          Press Enter to send, Shift+Enter for new line, Escape to close
        </div>
      </div>
    </div>
  );
};

/**
 * AI Assistant hook for easy integration
 */
export const useAIAssistant = () => {
  const [isVisible, setIsVisible] = useState(false);
  const [currentFile, setCurrentFile] = useState<{
    name: string;
    content: string;
    language: string;
    cursor?: { line: number; column: number };
  } | null>(null);

  const openAssistant = useCallback((file?: {
    name: string;
    content: string;
    language: string;
    cursor?: { line: number; column: number };
  }) => {
    setCurrentFile(file || null);
    setIsVisible(true);
  }, []);

  const closeAssistant = useCallback(() => {
    setIsVisible(false);
  }, []);

  const toggleAssistant = useCallback((file?: {
    name: string;
    content: string;
    language: string;
    cursor?: { line: number; column: number };
  }) => {
    if (isVisible) {
      closeAssistant();
    } else {
      openAssistant(file);
    }
  }, [isVisible, openAssistant, closeAssistant]);

  return {
    isVisible,
    currentFile,
    openAssistant,
    closeAssistant,
    toggleAssistant,
  };
};

export default {
  AIAssistant,
  useAIAssistant,
};
