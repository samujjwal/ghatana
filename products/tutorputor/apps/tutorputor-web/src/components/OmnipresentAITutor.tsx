/**
 * Omnipresent AI Tutor Component
 *
 * A floating AI assistant available on all pages of the student app.
 * Provides contextual help without leaving the current page.
 *
 * Features:
 * - Floating button always visible (except on AI Tutor page)
 * - Slide-out panel with chat interface
 * - Context-aware responses based on current page/module/lesson
 * - Proactive help detection
 * - Quick action suggestions
 *
 * @doc.type component
 * @doc.purpose Omnipresent AI assistant for students with context awareness
 * @doc.layer product
 * @doc.pattern Component
 */
import React, { useState, useRef, useEffect } from "react";
import { useMutation } from "@tanstack/react-query";
import { useLocation, useParams } from "react-router-dom";
import { useProactiveHelp } from "../hooks/useProactiveHelp";
import { createLogger } from '../utils/logger.js';
const logger = createLogger('OmnipresentAITutor');

interface Message {
  id: string;
  role: "user" | "assistant";
  content: string;
  timestamp: Date;
}

interface TutorResponse {
  response: {
    answer: string;
    citations?: Array<{ id: string; label: string; type: string }>;
    followUpQuestions?: string[];
    safety?: { blocked: boolean };
  };
}

// Icons
const SparklesIcon = () => (
  <svg
    className="w-6 h-6"
    fill="none"
    stroke="currentColor"
    viewBox="0 0 24 24"
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z"
    />
  </svg>
);

const CloseIcon = () => (
  <svg
    className="w-5 h-5"
    fill="none"
    stroke="currentColor"
    viewBox="0 0 24 24"
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M6 18L18 6M6 6l12 12"
    />
  </svg>
);

const SendIcon = () => (
  <svg
    className="w-5 h-5"
    fill="none"
    stroke="currentColor"
    viewBox="0 0 24 24"
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"
    />
  </svg>
);

const MinimizeIcon = () => (
  <svg
    className="w-5 h-5"
    fill="none"
    stroke="currentColor"
    viewBox="0 0 24 24"
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M19 9l-7 7-7-7"
    />
  </svg>
);

export function OmnipresentAITutor() {
  const location = useLocation();
  const params = useParams<{ slug?: string; assessmentId?: string }>();
  const [isOpen, setIsOpen] = useState(false);
  const [hasUserInteracted, setHasUserInteracted] = useState(false);
  const [messages, setMessages] = useState<Message[]>([
    {
      id: "welcome",
      role: "assistant",
      content:
        "Hi! I'm your AI tutor. I can help you understand concepts, answer questions about your current lesson, or explore new topics. What would you like to learn?",
      timestamp: new Date(),
    },
  ]);
  const [input, setInput] = useState("");
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Determine current context from router
  const isAITutorPage = location.pathname === "/ai-tutor";
  const isModulePage = location.pathname.includes("/modules/");
  const isAssessmentPage = location.pathname.includes("/assessments/");
  const isPathwaysPage = location.pathname.includes("/pathways");
  const isDashboardPage = location.pathname === "/" || location.pathname === "/dashboard";

  // Get task type for proactive help
  const getTaskType = (): "lesson" | "quiz" | "exercise" | "general" => {
    if (isAssessmentPage) return "quiz";
    if (isModulePage) return "lesson";
    if (isPathwaysPage) return "exercise";
    return "general";
  };

  // Initialize proactive help detection
  const proactiveHelp = useProactiveHelp({
    enabled: !isOpen && !isAITutorPage, // Only detect when panel is closed
    taskType: getTaskType(),
    moduleId: isModulePage ? params.slug : undefined,
    lessonId: isAssessmentPage ? params.assessmentId : undefined,
  });

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    if (isOpen) {
      scrollToBottom();
      inputRef.current?.focus();
    }
  }, [isOpen, messages]);

  // Handle proactive help trigger
  useEffect(() => {
    if (proactiveHelp.shouldShowHelp && !hasUserInteracted) {
      // Open the AI tutor with a proactive message
      setIsOpen(true);
      setHasUserInteracted(true);

      const proactiveMessage: Message = {
        id: `proactive-${Date.now()}`,
        role: "assistant",
        content: proactiveHelp.suggestedAction,
        timestamp: new Date(),
      };

      setMessages(prev => [...prev, proactiveMessage]);
      proactiveHelp.acceptHelp();

      logger.info("Proactive help triggered", { pattern: proactiveHelp.pattern });
    }
  }, [proactiveHelp.shouldShowHelp, proactiveHelp.suggestedAction, hasUserInteracted]);

  // Enhanced context detection
  const getContextHint = () => {
    const contexts: string[] = [];

    // Page type context
    if (isModulePage) {
      contexts.push(`module "${params.slug || "unknown"}"`);
    } else if (isAssessmentPage) {
      contexts.push(`assessment "${params.assessmentId || "unknown"}"`);
    } else if (isPathwaysPage) {
      contexts.push("learning pathways");
    } else if (isDashboardPage) {
      contexts.push("dashboard");
    } else if (location.pathname.includes("/search")) {
      contexts.push("content search");
    } else if (location.pathname.includes("/teacher")) {
      contexts.push("teacher console");
    } else if (location.pathname.includes("/analytics")) {
      contexts.push("learning analytics");
    }

    // Add proactive pattern context if available
    if (proactiveHelp.pattern) {
      contexts.push(`showing signs of ${proactiveHelp.pattern.type.replace(/_/g, " ")}`);
    }

    return contexts.length > 0
      ? `User is currently on ${contexts.join(", ")}`
      : "User is browsing the learning platform";
  };

  // Get contextual quick actions based on current page
  const getQuickActions = () => {
    if (isModulePage) {
      return [
        "Explain this concept",
        "Give me an example",
        "What are the key takeaways?",
        "Help me understand this topic",
      ];
    }
    if (isAssessmentPage) {
      return [
        "Help me with this question",
        "Give me a hint",
        "Explain the concept being tested",
        "Walk me through the solution",
      ];
    }
    if (isPathwaysPage) {
      return [
        "What should I learn next?",
        "Suggest a learning path",
        "How do I reach my goal?",
        "What topics am I missing?",
      ];
    }
    return [
      "Explain this concept",
      "Give me an example",
      "What should I learn next?",
    ];
  };

  const askTutorMutation = useMutation<TutorResponse, Error, string>({
    mutationFn: async (question: string): Promise<TutorResponse> => {
      const response = await fetch("/api/v1/ai/tutor/query", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-tenant-id": "default",
        },
        body: JSON.stringify({
          question,
          context: `User is currently viewing ${getContextHint()}`,
          locale: navigator.language || "en",
        }),
      });

      if (!response.ok) {
        const error = await response
          .json()
          .catch(() => ({ error: "Unknown error" }));
        throw new Error(error.error || `HTTP ${response.status}`);
      }

      return response.json();
    },
    onSuccess: (data: TutorResponse) => {
      const assistantMessage: Message = {
        id: `assistant-${Date.now()}`,
        role: "assistant",
        content: data.response.answer,
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, assistantMessage]);
    },
    onError: (error: Error) => {
      const errorMessage: Message = {
        id: `error-${Date.now()}`,
        role: "assistant",
        content: `I'm sorry, I encountered an error: ${error.message}. Please try again.`,
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, errorMessage]);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || askTutorMutation.isPending) return;

    const userMessage: Message = {
      id: `user-${Date.now()}`,
      role: "user",
      content: input.trim(),
      timestamp: new Date(),
    };
    setMessages((prev) => [...prev, userMessage]);
    askTutorMutation.mutate(input.trim());
    setInput("");
  };

  const quickActions = getQuickActions();

  // Handle manual open to mark user interaction
  const handleOpen = () => {
    setIsOpen(true);
    setHasUserInteracted(true);
  };

  // Handle close
  const handleClose = () => {
    setIsOpen(false);
    proactiveHelp.dismissHelp();
  };

  // Don't render on the dedicated AI Tutor page
  if (isAITutorPage) {
    return null;
  }

  return (
    <>
      {/* Floating Button */}
      <button
        onClick={handleOpen}
        className={`fixed bottom-6 right-6 z-40 w-14 h-14 bg-gradient-to-br from-purple-600 to-indigo-600 text-white rounded-full shadow-lg shadow-purple-500/30 flex items-center justify-center hover:scale-110 transition-all duration-200 ${
          isOpen ? "scale-0 opacity-0" : "scale-100 opacity-100"
        }`}
        title="Ask AI Tutor"
      >
        <SparklesIcon />
        {/* Pulsing indicator - show when proactive help is available */}
        {proactiveHelp.shouldShowHelp && (
          <span className="absolute -top-1 -right-1 w-4 h-4 bg-red-500 rounded-full border-2 border-white animate-ping" />
        )}
        {/* Online indicator */}
        <span className="absolute top-0 right-0 w-3 h-3 bg-green-400 rounded-full border-2 border-white" />
      </button>

      {/* Slide-out Panel */}
      <div
        className={`fixed bottom-0 right-0 z-50 w-full sm:w-96 h-[70vh] sm:h-[600px] sm:bottom-6 sm:right-6 bg-white dark:bg-gray-800 rounded-t-2xl sm:rounded-2xl shadow-2xl transform transition-all duration-300 ease-out flex flex-col ${
          isOpen
            ? "translate-y-0 opacity-100"
            : "translate-y-full sm:translate-y-8 opacity-0 pointer-events-none"
        }`}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700 bg-gradient-to-r from-purple-600 to-indigo-600 rounded-t-2xl">
          <div className="flex items-center gap-2 text-white">
            <SparklesIcon />
            <span className="font-semibold">AI Tutor</span>
          </div>
          <div className="flex items-center gap-1">
            <button
              onClick={handleClose}
              className="p-1.5 hover:bg-white/20 rounded-lg transition-colors text-white"
              title="Minimize"
            >
              <MinimizeIcon />
            </button>
            <button
              onClick={handleClose}
              className="p-1.5 hover:bg-white/20 rounded-lg transition-colors text-white"
              title="Close"
            >
              <CloseIcon />
            </button>
          </div>
        </div>

        {/* Messages */}
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {messages.map((message) => (
            <div
              key={message.id}
              className={`flex ${message.role === "user" ? "justify-end" : "justify-start"}`}
            >
              <div
                className={`max-w-[85%] rounded-2xl px-4 py-2.5 ${
                  message.role === "user"
                    ? "bg-gradient-to-r from-purple-600 to-indigo-600 text-white rounded-br-md"
                    : "bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-bl-md"
                }`}
              >
                <p className="text-sm whitespace-pre-wrap">{message.content}</p>
              </div>
            </div>
          ))}

          {/* Loading indicator */}
          {askTutorMutation.isPending && (
            <div className="flex justify-start">
              <div className="bg-gray-100 dark:bg-gray-700 rounded-2xl rounded-bl-md px-4 py-3">
                <div className="flex gap-1">
                  <span className="w-2 h-2 bg-purple-500 rounded-full animate-bounce" />
                  <span
                    className="w-2 h-2 bg-purple-500 rounded-full animate-bounce"
                    style={{ animationDelay: "0.1s" }}
                  />
                  <span
                    className="w-2 h-2 bg-purple-500 rounded-full animate-bounce"
                    style={{ animationDelay: "0.2s" }}
                  />
                </div>
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Quick Actions */}
        {messages.length <= 1 && (
          <div className="px-4 pb-2">
            <div className="flex flex-wrap gap-2">
              {quickActions.map((action) => (
                <button
                  key={action}
                  onClick={() => {
                    setInput(action);
                    inputRef.current?.focus();
                  }}
                  className="text-xs px-3 py-1.5 bg-purple-50 dark:bg-purple-900/30 text-purple-600 dark:text-purple-300 rounded-full hover:bg-purple-100 dark:hover:bg-purple-900/50 transition-colors"
                >
                  {action}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Input */}
        <form
          onSubmit={handleSubmit}
          className="p-4 border-t border-gray-200 dark:border-gray-700"
        >
          <div className="flex gap-2">
            <input
              ref={inputRef}
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Ask me anything..."
              className="flex-1 px-4 py-2.5 bg-gray-100 dark:bg-gray-700 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-purple-500 dark:text-white placeholder-gray-500"
              disabled={askTutorMutation.isPending}
            />
            <button
              type="submit"
              disabled={!input.trim() || askTutorMutation.isPending}
              className="px-4 py-2.5 bg-gradient-to-r from-purple-600 to-indigo-600 text-white rounded-xl hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed transition-opacity"
            >
              <SendIcon />
            </button>
          </div>
        </form>
      </div>

      {/* Backdrop for mobile */}
      {isOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/30 sm:hidden"
          onClick={handleClose}
        />
      )}
    </>
  );
}
