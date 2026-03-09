/**
 * AI Content Assistant Component
 *
 * Conversational AI assistant for content creation and management
 * Reuses existing UI patterns and integrates with AI service manager
 *
 * @doc.type component
 * @doc.purpose AI-powered content assistance
 * @doc.layer component
 * @doc.pattern Conversational UI, AI Assistant
 */

import { useState, useRef, useEffect } from "react";
import { Button, Card, Input } from "@ghatana/ui";
import {
  Sparkles,
  Send,
  Bot,
  User,
  Lightbulb,
  Zap,
  TrendingUp,
  Settings,
  ChevronDown,
  ChevronUp,
} from "lucide-react";
import {
  aiServiceManager,
  type AIInteraction,
} from "../../services/aiServiceManager";

interface AIAssistantProps {
  onActionSuggestion?: (action: string) => void;
  onContentGenerated?: (content: unknown) => void;
  className?: string;
  initialContext?: string;
}

interface ChatMessage {
  id: string;
  type: "user" | "ai";
  content: string;
  timestamp: Date;
  suggestions?: string[];
  isLoading?: boolean;
}

interface SmartSuggestion {
  id: string;
  text: string;
  icon: React.ReactNode;
  action: () => void;
  category: "create" | "analyze" | "optimize" | "troubleshoot";
}

export function AIAssistant({
  onActionSuggestion,
  onContentGenerated,
  className = "",
  initialContext = "content_creation",
}: AIAssistantProps) {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: "1",
      type: "ai",
      content: `Hi! I'm your AI Content Assistant. I can help you create, analyze, and optimize educational content. What would you like to work on today?`,
      timestamp: new Date(),
      suggestions: [
        "Create physics simulations for grade 10",
        "Analyze content quality",
        "Optimize learning materials",
        "Troubleshoot content issues",
      ],
    },
  ]);
  const [inputValue, setInputValue] = useState("");
  const [isTyping, setIsTyping] = useState(false);
  const [isMinimized, setIsMinimized] = useState(false);
  const [smartSuggestions, setSmartSuggestions] = useState<SmartSuggestion[]>(
    [],
  );

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Initialize smart suggestions based on context
  useEffect(() => {
    const suggestions: SmartSuggestion[] = [
      {
        id: "1",
        text: "Generate new content",
        icon: <Sparkles className="h-4 w-4" />,
        action: () => handleQuickAction("generate"),
        category: "create",
      },
      {
        id: "2",
        text: "Analyze quality",
        icon: <TrendingUp className="h-4 w-4" />,
        action: () => handleQuickAction("analyze"),
        category: "analyze",
      },
      {
        id: "3",
        text: "Optimize content",
        icon: <Zap className="h-4 w-4" />,
        action: () => handleQuickAction("optimize"),
        category: "optimize",
      },
      {
        id: "4",
        text: "Fix issues",
        icon: <Settings className="h-4 w-4" />,
        action: () => handleQuickAction("troubleshoot"),
        category: "troubleshoot",
      },
    ];

    setSmartSuggestions(suggestions);
  }, [initialContext]);

  // Auto-scroll to bottom when new messages arrive
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  // Handle quick action buttons
  const handleQuickAction = async (action: string) => {
    const actionMessages = {
      generate:
        "I'll help you create new educational content. What subject and grade level are you working with?",
      analyze:
        "I'll analyze your content for quality, engagement, and optimization opportunities. Please share the content you'd like me to review.",
      optimize:
        "I'll suggest improvements for your content. What specific aspects would you like to optimize?",
      troubleshoot:
        "I'll help you identify and fix content issues. What problems are you experiencing?",
    };

    const userMessage: ChatMessage = {
      id: Date.now().toString(),
      type: "user",
      content: actionMessages[action],
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setIsTyping(true);

    // Simulate AI response
    setTimeout(() => {
      const aiResponse: ChatMessage = {
        id: (Date.now() + 1).toString(),
        type: "ai",
        content: actionMessages[action],
        timestamp: new Date(),
        suggestions: getSuggestionsForAction(action),
      };

      setMessages((prev) => [...prev, aiResponse]);
      setIsTyping(false);
    }, 1000);
  };

  // Get contextual suggestions based on action
  const getSuggestionsForAction = (action: string): string[] => {
    const suggestionMap = {
      generate: [
        "Physics simulations for high school",
        "Math practice worksheets",
        "Science interactive examples",
        "English reading comprehension",
      ],
      analyze: [
        "Review current content quality",
        "Check engagement predictions",
        "Analyze learning outcomes",
        "Assess difficulty level",
      ],
      optimize: [
        "Improve content structure",
        "Add interactive elements",
        "Enhance visual aids",
        "Update assessments",
      ],
      troubleshoot: [
        "Fix formatting issues",
        "Resolve broken links",
        "Correct content errors",
        "Improve loading performance",
      ],
    };

    return suggestionMap[action as keyof typeof suggestionMap] || [];
  };

  // Handle message sending
  const handleSendMessage = async () => {
    if (!inputValue.trim()) return;

    const userMessage: ChatMessage = {
      id: Date.now().toString(),
      type: "user",
      content: inputValue,
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInputValue("");
    setIsTyping(true);

    try {
      // Get AI response from service manager
      const aiResponse = await aiServiceManager.chatWithAI(inputValue);

      const aiMessage: ChatMessage = {
        id: (Date.now() + 1).toString(),
        type: "ai",
        content: aiResponse,
        timestamp: new Date(),
        suggestions: extractSuggestionsFromResponse(aiResponse),
      };

      setMessages((prev) => [...prev, aiMessage]);

      // Track interaction
      const interaction: AIInteraction = {
        id: Date.now().toString(),
        timestamp: new Date(),
        type: "query",
        content: inputValue,
        outcome: "success",
        aiResponse,
      };

      aiServiceManager.addToHistory(interaction);
    } catch (error) {
      const errorMessage: ChatMessage = {
        id: (Date.now() + 1).toString(),
        type: "ai",
        content:
          "I'm having trouble processing your request right now. Please try again or rephrase your question.",
        timestamp: new Date(),
      };

      setMessages((prev) => [...prev, errorMessage]);
    } finally {
      setIsTyping(false);
    }
  };

  // Extract suggestions from AI response
  const extractSuggestionsFromResponse = (response: string): string[] => {
    // Simple suggestion extraction (can be enhanced with NLP)
    const suggestions: string[] = [];

    if (response.includes("create") || response.includes("generate")) {
      suggestions.push("Create new content", "Generate examples");
    }
    if (response.includes("analyze") || response.includes("review")) {
      suggestions.push("Analyze quality", "Review structure");
    }
    if (response.includes("optimize") || response.includes("improve")) {
      suggestions.push("Optimize content", "Improve engagement");
    }

    return suggestions.slice(0, 3); // Limit to 3 suggestions
  };

  // Handle suggestion click
  const handleSuggestionClick = (suggestion: string) => {
    setInputValue(suggestion);
    inputRef.current?.focus();
  };

  // Handle keyboard events
  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  return (
    <div className={`fixed bottom-4 right-4 z-50 w-96 ${className}`}>
      <Card className="bg-white dark:bg-gray-800 border-2 border-purple-200 dark:border-purple-800 shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-gray-200 dark:border-gray-700">
          <div className="flex items-center gap-2">
            <div className="flex items-center justify-center w-8 h-8 bg-gradient-to-r from-purple-500 to-blue-500 rounded-full">
              <Bot className="h-4 w-4 text-white" />
            </div>
            <div>
              <h3 className="font-semibold text-gray-900 dark:text-white">
                AI Assistant
              </h3>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                Always here to help
              </p>
            </div>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setIsMinimized(!isMinimized)}
            className="h-8 w-8 p-0"
          >
            {isMinimized ? (
              <ChevronUp className="h-4 w-4" />
            ) : (
              <ChevronDown className="h-4 w-4" />
            )}
          </Button>
        </div>

        {!isMinimized && (
          <>
            {/* Smart Suggestions */}
            <div className="p-3 border-b border-gray-200 dark:border-gray-700">
              <div className="flex items-center gap-2 mb-2">
                <Lightbulb className="h-4 w-4 text-yellow-500" />
                <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
                  Quick Actions
                </span>
              </div>
              <div className="grid grid-cols-2 gap-2">
                {smartSuggestions.map((suggestion) => (
                  <Button
                    key={suggestion.id}
                    variant="outline"
                    size="sm"
                    onClick={suggestion.action}
                    className="flex items-center gap-1 text-xs h-8"
                  >
                    {suggestion.icon}
                    {suggestion.text}
                  </Button>
                ))}
              </div>
            </div>

            {/* Messages */}
            <div className="h-96 overflow-y-auto p-4 space-y-4">
              {messages.map((message) => (
                <div
                  key={message.id}
                  className={`flex gap-3 ${message.type === "user" ? "justify-end" : "justify-start"}`}
                >
                  {message.type === "ai" && (
                    <div className="flex-shrink-0 w-6 h-6 bg-gradient-to-r from-purple-500 to-blue-500 rounded-full flex items-center justify-center">
                      <Bot className="h-3 w-3 text-white" />
                    </div>
                  )}

                  <div
                    className={`max-w-[80%] ${message.type === "user" ? "order-1" : ""}`}
                  >
                    <div
                      className={`p-3 rounded-lg ${
                        message.type === "user"
                          ? "bg-purple-500 text-white"
                          : "bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white"
                      }`}
                    >
                      <p className="text-sm">{message.content}</p>
                      {message.isLoading && (
                        <div className="flex items-center gap-1 mt-2">
                          <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" />
                          <div
                            className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"
                            style={{ animationDelay: "0.1s" }}
                          />
                          <div
                            className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"
                            style={{ animationDelay: "0.2s" }}
                          />
                        </div>
                      )}
                    </div>

                    {/* Suggestions */}
                    {message.suggestions && message.suggestions.length > 0 && (
                      <div className="mt-2 space-y-1">
                        {message.suggestions.map((suggestion, index) => (
                          <Button
                            key={index}
                            variant="ghost"
                            size="sm"
                            onClick={() => handleSuggestionClick(suggestion)}
                            className="text-xs h-6 p-1 text-purple-600 dark:text-purple-400 hover:bg-purple-50 dark:hover:bg-purple-900/20"
                          >
                            {suggestion}
                          </Button>
                        ))}
                      </div>
                    )}

                    <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                      {message.timestamp.toLocaleTimeString()}
                    </p>
                  </div>

                  {message.type === "user" && (
                    <div className="flex-shrink-0 w-6 h-6 bg-gray-300 dark:bg-gray-600 rounded-full flex items-center justify-center order-2">
                      <User className="h-3 w-3 text-gray-700 dark:text-gray-300" />
                    </div>
                  )}
                </div>
              ))}

              {isTyping && (
                <div className="flex gap-3 justify-start">
                  <div className="flex-shrink-0 w-6 h-6 bg-gradient-to-r from-purple-500 to-blue-500 rounded-full flex items-center justify-center">
                    <Bot className="h-3 w-3 text-white" />
                  </div>
                  <div className="bg-gray-100 dark:bg-gray-700 p-3 rounded-lg">
                    <div className="flex items-center gap-1">
                      <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" />
                      <div
                        className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"
                        style={{ animationDelay: "0.1s" }}
                      />
                      <div
                        className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"
                        style={{ animationDelay: "0.2s" }}
                      />
                    </div>
                  </div>
                </div>
              )}

              <div ref={messagesEndRef} />
            </div>

            {/* Input */}
            <div className="p-4 border-t border-gray-200 dark:border-gray-700">
              <div className="flex gap-2">
                <Input
                  ref={inputRef}
                  value={inputValue}
                  onChange={(e) => setInputValue(e.target.value)}
                  onKeyPress={handleKeyPress}
                  placeholder="Ask me anything about content creation..."
                  className="flex-1"
                  disabled={isTyping}
                />
                <Button
                  onClick={handleSendMessage}
                  disabled={!inputValue.trim() || isTyping}
                  size="sm"
                  className="bg-gradient-to-r from-purple-500 to-blue-500 hover:from-purple-600 hover:to-blue-600"
                >
                  <Send className="h-4 w-4" />
                </Button>
              </div>
            </div>
          </>
        )}
      </Card>
    </div>
  );
}

// Export for use in other components
export default AIAssistant;
