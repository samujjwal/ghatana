import { useState, useRef, useEffect } from "react";
import { useMutation } from "@tanstack/react-query";
import { Box, Card, Text, Button, Spinner } from "@/components/ui";
import { createLogger } from '../utils/logger.js';
const logger = createLogger('AITutorPage');

interface Message {
  id: string;
  role: "user" | "assistant";
  content: string;
  citations?: Array<{ id: string; label: string; type: string }>;
  followUpQuestions?: string[];
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

/**
 * AI Tutor chat interface page.
 *
 * Now wired to the real backend API at /api/v1/ai/tutor/query
 *
 * @doc.type component
 * @doc.purpose Interactive AI tutor for student questions
 * @doc.layer product
 * @doc.pattern Page
 */
export function AITutorPage() {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: "welcome",
      role: "assistant",
      content:
        "Hello! I'm your AI tutor. Ask me anything about the topics you're learning. I can help explain concepts, work through problems, and suggest related materials.",
      timestamp: new Date(),
    },
  ]);
  const [input, setInput] = useState("");
  const [currentModuleId, setCurrentModuleId] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

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
          moduleId: currentModuleId,
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
        citations: data.response.citations,
        followUpQuestions: data.response.followUpQuestions,
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

  const handleSuggestedQuestion = (question: string) => {
    const userMessage: Message = {
      id: `user-${Date.now()}`,
      role: "user",
      content: question,
      timestamp: new Date(),
    };
    setMessages((prev) => [...prev, userMessage]);
    askTutorMutation.mutate(question);
  };

  return (
    <Box className="flex flex-col h-full">
      {/* Header */}
      <Box className="bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 px-6 py-4">
        <Text
          as="h1"
          className="text-xl font-bold text-gray-900 dark:text-white"
        >
          AI Tutor
        </Text>
        <Text className="text-sm text-gray-500 dark:text-gray-400">
          Ask questions and get personalized help
        </Text>
      </Box>

      {/* Messages */}
      <Box className="flex-1 overflow-y-auto p-6">
        <Box className="max-w-3xl mx-auto space-y-4">
          {messages.map((message) => (
            <Box
              key={message.id}
              className={`flex ${message.role === "user" ? "justify-end" : "justify-start"}`}
            >
              <Card
                className={`p-4 max-w-[80%] ${
                  message.role === "user" ? "bg-blue-600 text-white" : ""
                }`}
              >
                <Text
                  className={
                    message.role === "user"
                      ? "text-white"
                      : "text-gray-900 dark:text-gray-100"
                  }
                >
                  {message.content}
                </Text>

                {/* Citations */}
                {message.citations && message.citations.length > 0 && (
                  <Box className="mt-3 pt-3 border-t border-gray-200 dark:border-gray-700">
                    <Text className="text-xs text-gray-500 dark:text-gray-400 mb-2">
                      Related modules:
                    </Text>
                    <Box className="flex flex-wrap gap-2">
                      {message.citations.map((citation) => (
                        <a
                          key={citation.id}
                          href={`/modules/${citation.id}`}
                          className="text-xs text-blue-600 dark:text-blue-400 hover:underline"
                        >
                          {citation.label}
                        </a>
                      ))}
                    </Box>
                  </Box>
                )}

                {/* Follow-up questions */}
                {message.followUpQuestions &&
                  message.followUpQuestions.length > 0 && (
                    <Box className="mt-3 pt-3 border-t border-gray-200 dark:border-gray-700">
                      <Text className="text-xs text-gray-500 dark:text-gray-400 mb-2">
                        Suggested follow-ups:
                      </Text>
                      <Box className="space-y-2">
                        {message.followUpQuestions.map((q, idx) => (
                          <Button
                            key={idx}
                            variant="outline"
                            size="sm"
                            className="text-left text-xs"
                            onClick={() => handleSuggestedQuestion(q)}
                          >
                            {q}
                          </Button>
                        ))}
                      </Box>
                    </Box>
                  )}
              </Card>
            </Box>
          ))}

          {askTutorMutation.isPending && (
            <Box className="flex justify-start">
              <Card className="p-4">
                <Box className="flex items-center gap-2">
                  <Spinner size="sm" />
                  <Text className="text-gray-500 dark:text-gray-400">
                    Thinking...
                  </Text>
                </Box>
              </Card>
            </Box>
          )}

          <div ref={messagesEndRef} />
        </Box>
      </Box>

      {/* Input */}
      <Box className="bg-white dark:bg-gray-800 border-t border-gray-200 dark:border-gray-700 p-4">
        <form onSubmit={handleSubmit} className="max-w-3xl mx-auto">
          <Box className="flex gap-3">
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Ask a question..."
              className="flex-1 px-4 py-3 border border-gray-200 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-900 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
              disabled={askTutorMutation.isPending}
            />
            <Button
              type="submit"
              disabled={!input.trim() || askTutorMutation.isPending}
            >
              Send
            </Button>
          </Box>
        </form>
      </Box>
    </Box>
  );
}
