/**
 * SimulationTutorPanel Component
 *
 * Right-side panel overlay for SimulationPlayer with Q&A, hints, and scaffolds.
 * Provides an interactive AI tutor experience during simulations.
 *
 * @doc.type component
 * @doc.purpose Interactive tutor panel for simulation assistance
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useRef, useEffect, useCallback } from "react";
import { Button } from "@ghatana/design-system";
import {
  useSimulationTutor,
  type TutorMessage,
  type SimulationManifest,
  type SimKeyframe,
} from "../hooks/useSimulationTutor";

// =============================================================================
// Types
// =============================================================================

export interface SimulationTutorPanelProps {
  /** Simulation ID */
  simulationId: string;
  /** Simulation manifest */
  manifest: SimulationManifest;
  /** Current simulation keyframe (for context) */
  currentKeyframe?: SimKeyframe;
  /** Whether the panel is open */
  isOpen: boolean;
  /** Callback to close the panel */
  onClose: () => void;
  /** Panel position */
  position?: "right" | "bottom";
  /** Initial width/height (depending on position) */
  size?: number;
}

// =============================================================================
// Sub-components
// =============================================================================

/**
 * Message bubble component.
 */
const MessageBubble: React.FC<{
  message: TutorMessage;
  onFollowUpClick?: (question: string) => void;
}> = ({ message, onFollowUpClick }) => {
  const isUser = message.role === "user";

  return (
    <div
      className={`flex ${isUser ? "justify-end" : "justify-start"} mb-4`}
    >
      <div
        className={`
          max-w-[85%] rounded-2xl px-4 py-3
          ${
            isUser
              ? "bg-blue-500 text-white rounded-br-none"
              : "bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-bl-none"
          }
        `}
      >
        {/* Message content */}
        <p className="text-sm whitespace-pre-wrap">{message.content}</p>

        {/* Streaming indicator */}
        {message.isStreaming && (
          <span className="inline-flex ml-1">
            <span className="animate-bounce">.</span>
            <span className="animate-bounce" style={{ animationDelay: "0.1s" }}>.</span>
            <span className="animate-bounce" style={{ animationDelay: "0.2s" }}>.</span>
          </span>
        )}

        {/* Follow-up questions */}
        {message.followUpQuestions && message.followUpQuestions.length > 0 && (
          <div className="mt-3 pt-3 border-t border-gray-200 dark:border-gray-700">
            <p className="text-xs text-gray-500 dark:text-gray-400 mb-2">
              Related questions:
            </p>
            <div className="flex flex-wrap gap-2">
              {message.followUpQuestions.map((q, i) => (
                <button
                  key={i}
                  onClick={() => onFollowUpClick?.(q)}
                  className="text-xs px-2 py-1 rounded-full bg-white dark:bg-gray-700 text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-gray-600 transition-colors"
                >
                  {q}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Timestamp */}
        <p
          className={`
            text-xs mt-2
            ${isUser ? "text-blue-200" : "text-gray-400 dark:text-gray-500"}
          `}
        >
          {new Date(message.timestamp).toLocaleTimeString([], {
            hour: "2-digit",
            minute: "2-digit",
          })}
        </p>
      </div>
    </div>
  );
};

/**
 * Hint card component.
 */
const HintCard: React.FC<{
  hints: string[];
}> = ({ hints }) => {
  if (hints.length === 0) return null;

  return (
    <div className="mb-4 p-3 rounded-lg bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800">
      <div className="flex items-center gap-2 mb-2">
        <LightbulbIcon className="h-4 w-4 text-yellow-600 dark:text-yellow-400" />
        <span className="text-sm font-medium text-yellow-800 dark:text-yellow-200">
          Hints
        </span>
      </div>
      <ul className="space-y-1">
        {hints.map((hint, i) => (
          <li key={i} className="text-sm text-yellow-700 dark:text-yellow-300">
            • {hint}
          </li>
        ))}
      </ul>
    </div>
  );
};

/**
 * Suggestion card component.
 */
const SuggestionCard: React.FC<{
  suggestions: string[];
  onSuggestionClick?: (suggestion: string) => void;
}> = ({ suggestions, onSuggestionClick }) => {
  if (suggestions.length === 0) return null;

  return (
    <div className="mb-4 p-3 rounded-lg bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800">
      <div className="flex items-center gap-2 mb-2">
        <SparklesIcon className="h-4 w-4 text-blue-600 dark:text-blue-400" />
        <span className="text-sm font-medium text-blue-800 dark:text-blue-200">
          Try Next
        </span>
      </div>
      <div className="flex flex-wrap gap-2">
        {suggestions.map((suggestion, i) => (
          <button
            key={i}
            onClick={() => onSuggestionClick?.(suggestion)}
            className="text-xs px-2 py-1 rounded-full bg-white dark:bg-gray-800 text-blue-600 dark:text-blue-400 hover:bg-blue-100 dark:hover:bg-gray-700 transition-colors"
          >
            {suggestion}
          </button>
        ))}
      </div>
    </div>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const SimulationTutorPanel: React.FC<SimulationTutorPanelProps> = ({
  simulationId,
  manifest,
  currentKeyframe,
  isOpen,
  onClose,
  position = "right",
  size = 380,
}) => {
  const [inputValue, setInputValue] = useState("");
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const {
    messages,
    isLoading,
    isStreaming,
    error,
    hints,
    suggestions,
    askWithStream,
    askFollowUp,
    clearMessages,
    updateKeyframe,
  } = useSimulationTutor({
    simulationId,
    manifest,
    onError: (err) => console.error("Tutor error:", err),
  });

  // Update keyframe when it changes
  useEffect(() => {
    if (currentKeyframe) {
      updateKeyframe(currentKeyframe);
    }
  }, [currentKeyframe, updateKeyframe]);

  // Scroll to bottom on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  // Handle submit
  const handleSubmit = useCallback(
    (e?: React.FormEvent) => {
      e?.preventDefault();
      if (!inputValue.trim() || isLoading || isStreaming) return;

      askWithStream(inputValue.trim());
      setInputValue("");
    },
    [inputValue, isLoading, isStreaming, askWithStream]
  );

  // Handle keyboard shortcuts
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        handleSubmit();
      }
    },
    [handleSubmit]
  );

  // Handle follow-up click
  const handleFollowUpClick = useCallback(
    (question: string) => {
      askFollowUp(question);
    },
    [askFollowUp]
  );

  if (!isOpen) return null;

  const panelStyles =
    position === "right"
      ? {
          width: size,
          height: "100%",
          right: 0,
          top: 0,
        }
      : {
          width: "100%",
          height: size,
          bottom: 0,
          left: 0,
        };

  return (
    <div
      className={`
        fixed z-50 bg-white dark:bg-gray-900 shadow-xl
        flex flex-col
        ${position === "right" ? "border-l" : "border-t"}
        border-gray-200 dark:border-gray-700
      `}
      style={panelStyles}
    >
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center gap-2">
          <BotIcon className="h-5 w-5 text-blue-500" />
          <h3 className="font-semibold text-gray-900 dark:text-gray-100">
            AI Tutor
          </h3>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={clearMessages}
            className="p-1.5 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
            title="Clear chat"
          >
            <TrashIcon className="h-4 w-4 text-gray-500" />
          </button>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
            title="Close"
          >
            <XIcon className="h-4 w-4 text-gray-500" />
          </button>
        </div>
      </div>

      {/* Messages area */}
      <div className="flex-1 overflow-y-auto p-4">
        {/* Welcome message */}
        {messages.length === 0 && (
          <div className="text-center py-8">
            <BotIcon className="h-12 w-12 text-blue-500 mx-auto mb-4" />
            <h4 className="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
              Hi! I'm your AI tutor
            </h4>
            <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
              I'm here to help you understand this {manifest.domain.toLowerCase()}{" "}
              simulation. Ask me anything!
            </p>
            <div className="flex flex-wrap justify-center gap-2">
              {[
                "What should I observe here?",
                "Explain this concept",
                "Why is this happening?",
              ].map((q) => (
                <button
                  key={q}
                  onClick={() => askWithStream(q)}
                  className="text-sm px-3 py-1.5 rounded-full bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors"
                >
                  {q}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Hints */}
        <HintCard hints={hints} />

        {/* Suggestions */}
        <SuggestionCard
          suggestions={suggestions}
          onSuggestionClick={handleFollowUpClick}
        />

        {/* Messages */}
        {messages.map((message) => (
          <MessageBubble
            key={message.id}
            message={message}
            onFollowUpClick={handleFollowUpClick}
          />
        ))}

        {/* Error message */}
        {error && (
          <div className="p-3 rounded-lg bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300 text-sm mb-4">
            {error}
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Input area */}
      <div className="border-t border-gray-200 dark:border-gray-700 p-4">
        <form onSubmit={handleSubmit} className="flex gap-2">
          <textarea
            ref={inputRef}
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Ask a question..."
            className="flex-1 resize-none rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:text-gray-100"
            rows={2}
            disabled={isLoading || isStreaming}
          />
          <Button
            type="submit"
            disabled={!inputValue.trim() || isLoading || isStreaming}
            className="self-end"
          >
            {isLoading || isStreaming ? (
              <LoaderIcon className="h-4 w-4 animate-spin" />
            ) : (
              <SendIcon className="h-4 w-4" />
            )}
          </Button>
        </form>
      </div>
    </div>
  );
};

// =============================================================================
// Icon Components
// =============================================================================

const BotIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"
    />
  </svg>
);

const XIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
  </svg>
);

const TrashIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
    />
  </svg>
);

const SendIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
  </svg>
);

const LoaderIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
    />
  </svg>
);

const LightbulbIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z"
    />
  </svg>
);

const SparklesIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z"
    />
  </svg>
);

export default SimulationTutorPanel;
