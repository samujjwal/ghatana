/**
 * NL-to-Manifest Author Panel
 * 
 * Natural language interface for generating simulation manifests.
 * Integrates with tutorputor-sim-author service for AI-powered generation.
 * 
 * @doc.type component
 * @doc.purpose UI for natural language to simulation manifest conversion
 * @doc.layer product
 * @doc.pattern Container
 */

import { useState, useCallback, useMemo, useRef } from "react";
import { useMutation } from "@tanstack/react-query";
import { Button, Badge, Tooltip } from "@ghatana/design-system";
import type {
  SimulationManifest,
  SimulationDomain,
} from "../hooks/useSimulationTimeline";
import type {
  GenerateManifestRequest,
  GenerateManifestResult,
  RefineManifestRequest,
} from "@ghatana/tutorputor-contracts/v1/simulation/types";

// =============================================================================
// Client Types (derived from contracts)
// =============================================================================

type ClientGenerateManifestRequest = Omit<GenerateManifestRequest, "tenantId" | "userId">;
type ClientRefineManifestRequest = Omit<RefineManifestRequest, "tenantId" | "userId">;

// =============================================================================
// Types
// =============================================================================

// =============================================================================
// Types
// =============================================================================

export interface NLAuthorPanelProps {
  /**
   * Initial domain selection.
   */
  initialDomain?: SimulationDomain;

  /**
   * Callback when a manifest is successfully generated.
   */
  onManifestGenerated: (manifest: SimulationManifest) => void;

  /**
   * Optional existing manifest for refinement mode.
   */
  existingManifest?: SimulationManifest;

  /**
   * API base URL for sim-author service.
   */
  apiBaseUrl?: string;

  /**
   * Enable example prompts display.
   */
  showExamples?: boolean;

  /**
   * Custom class name.
   */
  className?: string;
}

interface ConversationMessage {
  id: string;
  role: "user" | "assistant" | "system";
  content: string;
  timestamp: Date;
  manifest?: SimulationManifest;
  confidence?: number;
  error?: string;
}

interface GenerationConstraints {
  maxSteps: number;
  maxEntities: number;
  targetDuration: number;
}

// =============================================================================
// Domain Metadata
// =============================================================================

const DOMAIN_OPTIONS: Array<{
  value: SimulationDomain;
  label: string;
  icon: string;
  description: string;
  examplePrompts: string[];
}> = [
  {
    value: "CS_DISCRETE",
    label: "Computer Science",
    icon: "🖥️",
    description: "Algorithms, data structures, sorting, graphs",
    examplePrompts: [
      "Show bubble sort step by step on array [5, 2, 8, 1, 9]",
      "Demonstrate BFS traversal on a 4-node graph",
      "Visualize binary search finding 7 in sorted array",
      "Show linked list insertion at position 2",
    ],
  },
  {
    value: "PHYSICS",
    label: "Physics",
    icon: "⚛️",
    description: "Mechanics, forces, energy, waves",
    examplePrompts: [
      "Projectile motion with 45 degree angle and 20 m/s velocity",
      "Spring oscillation with mass 2kg and k=100 N/m",
      "Two bodies colliding elastically",
      "Pendulum swinging with damping",
    ],
  },
  {
    value: "CHEMISTRY",
    label: "Chemistry",
    icon: "🧪",
    description: "Reactions, molecules, bonds, equilibrium",
    examplePrompts: [
      "SN2 reaction of bromoethane with hydroxide",
      "Acid-base titration of HCl with NaOH",
      "Le Chatelier's principle with N2 + 3H2 ⇌ 2NH3",
      "Oxidation of ethanol to acetaldehyde",
    ],
  },
  {
    value: "BIOLOGY",
    label: "Biology",
    icon: "🧬",
    description: "Cells, genetics, metabolism, ecosystems",
    examplePrompts: [
      "DNA transcription to mRNA",
      "Krebs cycle showing ATP production",
      "Enzyme kinetics with substrate concentration",
      "Predator-prey population dynamics",
    ],
  },
  {
    value: "MEDICINE",
    label: "Medicine",
    icon: "💊",
    description: "Pharmacology, physiology, pathology",
    examplePrompts: [
      "Drug absorption and distribution (2-compartment PK)",
      "Insulin signaling pathway",
      "SIR model of disease spread",
      "Drug-receptor binding kinetics",
    ],
  },
  {
    value: "ECONOMICS",
    label: "Economics",
    icon: "📈",
    description: "Markets, supply/demand, policy, macro",
    examplePrompts: [
      "Supply and demand reaching equilibrium",
      "Effect of per-unit tax on market",
      "Price ceiling causing shortage",
      "IS-LM model response to monetary policy",
    ],
  },
  {
    value: "MATHEMATICS",
    label: "Mathematics",
    icon: "📐",
    description: "Geometry, algebra, calculus, linear algebra",
    examplePrompts: [
      "Graphing y = x^2 + 2x - 3",
      "Matrix multiplication step by step",
      "Rotating a triangle 45 degrees",
      "Finding limits using epsilon-delta",
    ],
  },
  {
    value: "ENGINEERING",
    label: "Engineering",
    icon: "⚙️",
    description: "Circuits, structures, control systems",
    examplePrompts: [
      "RC circuit charging with 5V source",
      "Truss force analysis",
      "PID controller tuning",
      "Heat transfer through wall layers",
    ],
  },
];

// =============================================================================
// API Client Hooks
// =============================================================================

interface SimAuthorAPI {
  generateManifest: (req: ClientGenerateManifestRequest) => Promise<GenerateManifestResult>;
  refineManifest: (req: ClientRefineManifestRequest) => Promise<GenerateManifestResult>;
}

function useSimAuthorAPI(baseUrl: string): SimAuthorAPI {
  const generateManifest = useCallback(
    async (req: ClientGenerateManifestRequest): Promise<GenerateManifestResult> => {
      const response = await fetch(`${baseUrl}/api/sim-author/generate`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(req),
      });

      if (!response.ok) {
        const error = await response.json().catch(() => ({ message: "Unknown error" }));
        throw new Error(error.message || `HTTP ${response.status}`);
      }

      return response.json();
    },
    [baseUrl]
  );

  const refineManifest = useCallback(
    async (req: ClientRefineManifestRequest): Promise<GenerateManifestResult> => {
      const response = await fetch(`${baseUrl}/api/sim-author/refine`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(req),
      });

      if (!response.ok) {
        const error = await response.json().catch(() => ({ message: "Unknown error" }));
        throw new Error(error.message || `HTTP ${response.status}`);
      }

      return response.json();
    },
    [baseUrl]
  );

  return { generateManifest, refineManifest };
}

// =============================================================================
// Component
// =============================================================================

export const NLAuthorPanel = ({
  initialDomain = "CS_DISCRETE",
  onManifestGenerated,
  existingManifest,
  apiBaseUrl = "/api",
  showExamples = true,
  className = "",
}: NLAuthorPanelProps) => {
  // State
  const [domain, setDomain] = useState<SimulationDomain>(
    existingManifest?.domain || initialDomain
  );
  const [prompt, setPrompt] = useState("");
  const [conversation, setConversation] = useState<ConversationMessage[]>([]);
  const [constraints, setConstraints] = useState<GenerationConstraints>({
    maxSteps: 10,
    maxEntities: 8,
    targetDuration: 30,
  });
  const [showConstraints, setShowConstraints] = useState(false);
  const [activeTab, setActiveTab] = useState<"compose" | "refine">(
    existingManifest ? "refine" : "compose"
  );

  const conversationEndRef = useRef<HTMLDivElement>(null);
  const api = useSimAuthorAPI(apiBaseUrl);

  // Current domain metadata
  const domainMeta = useMemo(
    () => DOMAIN_OPTIONS.find((d) => d.value === domain),
    [domain]
  );

  // Generate manifest mutation
  const generateMutation = useMutation({
    mutationFn: async (userPrompt: string) => {
      const request: GenerateManifestRequest = {
        domain,
        prompt: userPrompt,
        constraints: {
          maxSteps: constraints.maxSteps,
          maxEntities: constraints.maxEntities,
          targetDuration: constraints.targetDuration,
        },
      };

      return api.generateManifest(request);
    },
    onMutate: (userPrompt) => {
      // Add user message to conversation
      const userMessage: ConversationMessage = {
        id: `msg_${Date.now()}`,
        role: "user",
        content: userPrompt,
        timestamp: new Date(),
      };
      setConversation((prev) => [...prev, userMessage]);
    },
    onSuccess: (result) => {
      // Add assistant message with result
      const assistantMessage: ConversationMessage = {
        id: `msg_${Date.now()}`,
        role: "assistant",
        content: result.manifest
          ? `Generated "${result.manifest.title}" with ${result.manifest.steps.length} steps.`
          : "Failed to generate manifest.",
        timestamp: new Date(),
        manifest: result.manifest,
        confidence: result.confidence,
      };
      setConversation((prev) => [...prev, assistantMessage]);

      if (result.manifest) {
        onManifestGenerated(result.manifest);
      }

      // Scroll to bottom
      setTimeout(() => {
        conversationEndRef.current?.scrollIntoView({ behavior: "smooth" });
      }, 100);
    },
    onError: (error: Error) => {
      const errorMessage: ConversationMessage = {
        id: `msg_${Date.now()}`,
        role: "assistant",
        content: "Sorry, I couldn't generate the simulation.",
        timestamp: new Date(),
        error: error.message,
      };
      setConversation((prev) => [...prev, errorMessage]);
    },
  });

  // Refine manifest mutation
  const refineMutation = useMutation({
    mutationFn: async (feedback: string) => {
      if (!existingManifest && conversation.length === 0) {
        throw new Error("No manifest to refine");
      }

      const manifestToRefine =
        existingManifest ||
        [...conversation].reverse().find((m) => m.manifest)?.manifest;

      if (!manifestToRefine) {
        throw new Error("No manifest to refine");
      }

      const request: RefineManifestRequest = {
        manifest: manifestToRefine,
        feedback,
      };

      return api.refineManifest(request);
    },
    onMutate: (feedback) => {
      const userMessage: ConversationMessage = {
        id: `msg_${Date.now()}`,
        role: "user",
        content: `Refine: ${feedback}`,
        timestamp: new Date(),
      };
      setConversation((prev) => [...prev, userMessage]);
    },
    onSuccess: (result) => {
      const assistantMessage: ConversationMessage = {
        id: `msg_${Date.now()}`,
        role: "assistant",
        content: result.manifest
          ? `Refined to "${result.manifest.title}" with ${result.manifest.steps.length} steps.`
          : "Failed to refine manifest.",
        timestamp: new Date(),
        manifest: result.manifest,
        confidence: result.confidence,
      };
      setConversation((prev) => [...prev, assistantMessage]);

      if (result.manifest) {
        onManifestGenerated(result.manifest);
      }

      setTimeout(() => {
        conversationEndRef.current?.scrollIntoView({ behavior: "smooth" });
      }, 100);
    },
    onError: (error: Error) => {
      const errorMessage: ConversationMessage = {
        id: `msg_${Date.now()}`,
        role: "assistant",
        content: "Sorry, I couldn't refine the simulation.",
        timestamp: new Date(),
        error: error.message,
      };
      setConversation((prev) => [...prev, errorMessage]);
    },
  });

  // Handlers
  const handleSubmit = useCallback(() => {
    if (!prompt.trim()) return;

    if (activeTab === "compose") {
      generateMutation.mutate(prompt);
    } else {
      refineMutation.mutate(prompt);
    }

    setPrompt("");
  }, [prompt, activeTab, generateMutation, refineMutation]);

  const handleExampleClick = useCallback((examplePrompt: string) => {
    setPrompt(examplePrompt);
  }, []);

  const handleClearConversation = useCallback(() => {
    setConversation([]);
  }, []);

  const handleUseManifest = useCallback((manifest: SimulationManifest) => {
    onManifestGenerated(manifest);
  }, [onManifestGenerated]);

  // Loading state
  const isLoading = generateMutation.isPending || refineMutation.isPending;

  // Has generated manifest
  const latestManifest = useMemo(() => {
    return [...conversation].reverse().find((m) => m.manifest)?.manifest;
  }, [conversation]);

  return (
    <div className={`flex flex-col h-full bg-gray-50 dark:bg-gray-900 ${className}`}>
      {/* Header */}
      <div className="flex-shrink-0 px-4 py-3 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
            AI Simulation Author
          </h2>
          {latestManifest && (
            <Badge tone="success">
              ✓ Manifest Ready
            </Badge>
          )}
        </div>

        {/* Domain Selector */}
        <div className="flex items-center gap-3">
          <label className="text-sm text-gray-500">Domain:</label>
          <select
            value={domain}
            onChange={(e) => setDomain(e.target.value as SimulationDomain)}
            disabled={isLoading}
            className="flex-1 px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700"
          >
            {DOMAIN_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.icon} {opt.label}
              </option>
            ))}
          </select>
        </div>

        {/* Domain description */}
        {domainMeta && (
          <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
            {domainMeta.description}
          </p>
        )}
      </div>

      {/* Tabs */}
      <div className="flex-shrink-0 px-4 pt-3">
        <div className="flex gap-1 p-1 bg-gray-100 dark:bg-gray-800 rounded-lg">
          <button
            onClick={() => setActiveTab("compose")}
            className={`
              flex-1 px-3 py-2 text-sm font-medium rounded-md transition-colors
              ${
                activeTab === "compose"
                  ? "bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm"
                  : "text-gray-600 dark:text-gray-400 hover:text-gray-900"
              }
            `}
          >
            ✨ Compose New
          </button>
          <button
            onClick={() => setActiveTab("refine")}
            disabled={!latestManifest && !existingManifest}
            className={`
              flex-1 px-3 py-2 text-sm font-medium rounded-md transition-colors
              ${
                activeTab === "refine"
                  ? "bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm"
                  : "text-gray-600 dark:text-gray-400 hover:text-gray-900"
              }
              ${!latestManifest && !existingManifest ? "opacity-50 cursor-not-allowed" : ""}
            `}
          >
            🔄 Refine
          </button>
        </div>
      </div>

      {/* Conversation Area */}
      <div className="flex-1 overflow-y-auto px-4 py-3 space-y-3">
        {conversation.length === 0 && showExamples && domainMeta && (
          <div className="space-y-3">
            <p className="text-sm text-gray-500 dark:text-gray-400">
              {activeTab === "compose"
                ? "Describe the simulation you want to create:"
                : "Describe how to improve the simulation:"}
            </p>

            {activeTab === "compose" && (
              <div className="space-y-2">
                <p className="text-xs text-gray-400">Try an example:</p>
                <div className="flex flex-wrap gap-2">
                  {domainMeta.examplePrompts.slice(0, 3).map((example, index) => (
                    <button
                      key={index}
                      onClick={() => handleExampleClick(example)}
                      className="px-3 py-1.5 text-xs bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 rounded-full hover:bg-blue-100 dark:hover:bg-blue-900/50 transition-colors"
                    >
                      {example.length > 40 ? example.substring(0, 40) + "..." : example}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {/* Messages */}
        {conversation.map((message) => (
          <div
            key={message.id}
            className={`flex ${message.role === "user" ? "justify-end" : "justify-start"}`}
          >
            <div
              className={`
                max-w-[85%] px-4 py-2 rounded-2xl
                ${
                  message.role === "user"
                    ? "bg-blue-500 text-white rounded-br-sm"
                    : message.error
                    ? "bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-200 rounded-bl-sm"
                    : "bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-bl-sm shadow-sm"
                }
              `}
            >
              <p className="text-sm">{message.content}</p>

              {/* Confidence Badge */}
              {message.confidence !== undefined && (
                <div className="mt-2 flex items-center gap-2">
                  <span className="text-xs opacity-75">Confidence:</span>
                  <Badge
                    tone={
                      message.confidence > 0.7
                        ? "success"
                        : message.confidence > 0.4
                        ? "warning"
                        : "danger"
                    }
                  >
                    {(message.confidence * 100).toFixed(0)}%
                  </Badge>
                </div>
              )}

              {/* Error Details */}
              {message.error && (
                <p className="mt-1 text-xs opacity-75">{message.error}</p>
              )}

              {/* Manifest Preview */}
              {message.manifest && (
                <div className="mt-3 p-2 bg-gray-50 dark:bg-gray-900 rounded-lg">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-medium">{message.manifest.title}</span>
                    <Badge tone="secondary">
                      {message.manifest.steps.length} steps
                    </Badge>
                  </div>
                  <div className="text-xs text-gray-500 space-y-1">
                    <p>Domain: {message.manifest.domain}</p>
                    <p>Entities: {message.manifest.initialEntities?.length || 0}</p>
                  </div>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => handleUseManifest(message.manifest!)}
                    className="mt-2 w-full"
                  >
                    Use This Manifest
                  </Button>
                </div>
              )}

              {/* Timestamp */}
              <div className="mt-1 text-xs opacity-50">
                {message.timestamp.toLocaleTimeString()}
              </div>
            </div>
          </div>
        ))}

        {/* Loading Indicator */}
        {isLoading && (
          <div className="flex justify-start">
            <div className="px-4 py-3 bg-white dark:bg-gray-800 rounded-2xl rounded-bl-sm shadow-sm">
              <div className="flex items-center gap-2">
                <div className="flex gap-1">
                  <div className="w-2 h-2 bg-blue-500 rounded-full animate-bounce" />
                  <div className="w-2 h-2 bg-blue-500 rounded-full animate-bounce delay-100" />
                  <div className="w-2 h-2 bg-blue-500 rounded-full animate-bounce delay-200" />
                </div>
                <span className="text-sm text-gray-500">Generating simulation...</span>
              </div>
            </div>
          </div>
        )}

        <div ref={conversationEndRef} />
      </div>

      {/* Constraints Panel (Collapsible) */}
      {showConstraints && (
        <div className="flex-shrink-0 px-4 py-3 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800">
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="block text-xs text-gray-500 mb-1">Max Steps</label>
              <input
                type="number"
                value={constraints.maxSteps}
                onChange={(e) =>
                  setConstraints({ ...constraints, maxSteps: Number(e.target.value) })
                }
                min={1}
                max={50}
                className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">Max Entities</label>
              <input
                type="number"
                value={constraints.maxEntities}
                onChange={(e) =>
                  setConstraints({ ...constraints, maxEntities: Number(e.target.value) })
                }
                min={1}
                max={20}
                className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">Duration (s)</label>
              <input
                type="number"
                value={constraints.targetDuration}
                onChange={(e) =>
                  setConstraints({ ...constraints, targetDuration: Number(e.target.value) })
                }
                min={5}
                max={120}
                className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
              />
            </div>
          </div>
        </div>
      )}

      {/* Input Area */}
      <div className="flex-shrink-0 p-4 border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
        <div className="flex items-center gap-2 mb-2">
          <button
            onClick={() => setShowConstraints(!showConstraints)}
            className={`
              p-1.5 rounded transition-colors
              ${showConstraints ? "bg-blue-100 text-blue-600" : "text-gray-400 hover:text-gray-600"}
            `}
          >
            <Tooltip content="Constraints">
              <span>⚙️</span>
            </Tooltip>
          </button>
          {conversation.length > 0 && (
            <button
              onClick={handleClearConversation}
              className="p-1.5 text-gray-400 hover:text-gray-600 rounded transition-colors"
            >
              <Tooltip content="Clear conversation">
                <span>🗑️</span>
              </Tooltip>
            </button>
          )}
        </div>

        <div className="flex gap-2">
          <textarea
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            placeholder={
              activeTab === "compose"
                ? "Describe your simulation..."
                : "How should I improve it?"
            }
            disabled={isLoading}
            rows={2}
            className="flex-1 px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg resize-none focus:ring-2 focus:ring-blue-500 focus:border-transparent bg-white dark:bg-gray-700"
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                handleSubmit();
              }
            }}
          />
          <Button
            onClick={handleSubmit}
            disabled={isLoading || !prompt.trim()}
            className="self-end"
          >
            {isLoading ? "..." : activeTab === "compose" ? "Generate" : "Refine"}
          </Button>
        </div>

        <p className="mt-2 text-xs text-gray-400">
          Press Enter to send, Shift+Enter for new line
        </p>
      </div>
    </div>
  );
};

export default NLAuthorPanel;
