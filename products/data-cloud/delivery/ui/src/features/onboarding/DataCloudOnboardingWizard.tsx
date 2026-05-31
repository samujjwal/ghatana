/**
 * DataCloudOnboardingWizard — First-Run Onboarding Wizard (B15).
 *
 * Presents a 5-step guided setup the first time a user loads the Data Cloud UI.
 * Completion state is persisted in localStorage so the wizard never re-triggers
 * for the same browser session.
 *
 * Steps:
 *   1. Welcome          — what Data Cloud is and what to expect
 *   2. Connect          — supply API base URL / tenant ID
 *   3. First Collection — create or name the first data collection
 *   4. Enable AI Assist — optional AI / LLM integration toggle
 *   5. Done             — summary and "Go to Home" CTA
 *
 * @doc.type component
 * @doc.purpose First-run onboarding for Data Cloud (B15)
 * @doc.layer product
 * @doc.pattern Page / Modal
 */
import { Wizard, type WizardStep } from "@ghatana/wizard";
import React, { useCallback, useState } from "react";
import SessionBootstrap from "../../lib/auth/session";

// ─────────────────────────────────────────────────────────────────────────────
// localStorage key used to persist completion state
// ─────────────────────────────────────────────────────────────────────────────

const ONBOARDING_COMPLETE_KEY = "dc:onboarding:complete";

/**
 * Returns `true` when the onboarding wizard has already been completed in
 * this browser.
 */
export function isOnboardingComplete(): boolean {
  try {
    return localStorage.getItem(ONBOARDING_COMPLETE_KEY) === "true";
  } catch {
    // localStorage blocked (private mode, etc.) — skip wizard
    return true;
  }
}

/**
 * Marks the wizard as complete so it is never shown again in this browser.
 */
function markOnboardingComplete(): void {
  try {
    localStorage.setItem(ONBOARDING_COMPLETE_KEY, "true");
  } catch {
    // ignore storage errors
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Wizard step definitions
// ─────────────────────────────────────────────────────────────────────────────

const WIZARD_STEPS: WizardStep[] = [
  {
    id: "welcome",
    title: "Welcome to Data Cloud",
    description: "Learn what Data Cloud can do for you.",
  },
  {
    id: "connect",
    title: "Connect Your Workspace",
    description: "Add your API endpoint and tenant details.",
  },
  {
    id: "collection",
    title: "Create Your First Collection",
    description: "Name the dataset you want to manage.",
  },
  {
    id: "ai",
    title: "Enable Automation Assist",
    description: "Optional — connect a model for natural-language queries.",
  },
  {
    id: "done",
    title: "You're Ready!",
    description: "Take a quick tour or dive straight in.",
  },
];

// ─────────────────────────────────────────────────────────────────────────────
// Per-step content components
// ─────────────────────────────────────────────────────────────────────────────

interface ConnectStepState {
  apiBaseUrl: string;
  tenantId: string;
}

interface CollectionStepState {
  collectionName: string;
}

interface AiStepState {
  enableAi: boolean;
  provider: "openai" | "ollama" | "none";
}

interface OnboardingState {
  connect: ConnectStepState;
  collection: CollectionStepState;
  ai: AiStepState;
}

function WelcomeStep(): React.ReactElement {
  return (
    <div className="space-y-4 py-2">
      <p className="text-gray-700 text-sm leading-relaxed">
        <strong>Data Cloud</strong> is a four-tier event-sourced data platform —
        HOT → WARM → COOL → COLD — built for real-time entity management,
        analytics, and AI-assisted query.
      </p>
      <ul className="list-disc pl-5 text-sm text-gray-600 space-y-1">
        <li>Store and query entities across configurable storage tiers</li>
        <li>Run SQL analytics with time-travel and federated Trino support</li>
        <li>
          Detect anomalies, set alert rules, and manage data lifecycle policies
        </li>
        <li>Use natural-language queries powered by your preferred LLM</li>
      </ul>
      <p className="text-xs text-gray-400">
        This wizard takes about 2 minutes to complete.
      </p>
    </div>
  );
}

interface ConnectStepProps {
  state: ConnectStepState;
  onChange: (state: ConnectStepState) => void;
}

function ConnectStep({
  state,
  onChange,
}: ConnectStepProps): React.ReactElement {
  return (
    <div className="space-y-4 py-2">
      <div>
        <label
          className="block text-sm font-medium text-gray-700 mb-1"
          htmlFor="ob-api-url"
        >
          API Base URL
        </label>
        <input
          id="ob-api-url"
          type="url"
          placeholder="http://localhost:8082"
          value={state.apiBaseUrl}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            onChange({ ...state, apiBaseUrl: e.target.value })
          }
          className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
        <p className="text-xs text-gray-400 mt-1">
          Defaults to <code>http://localhost:8082</code>. Leave blank to use the
          current host.
        </p>
      </div>
      <div>
        <label
          className="block text-sm font-medium text-gray-700 mb-1"
          htmlFor="ob-tenant"
        >
          Tenant ID
        </label>
        <input
          id="ob-tenant"
          type="text"
          placeholder="tenant-acme-prod"
          value={state.tenantId}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            onChange({ ...state, tenantId: e.target.value })
          }
          className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
        <p className="text-xs text-gray-400 mt-1">
          Tenant context is required for runtime-backed Data Cloud workflows.
          Reserved defaults are rejected.
        </p>
      </div>
    </div>
  );
}

interface CollectionStepProps {
  state: CollectionStepState;
  onChange: (state: CollectionStepState) => void;
}

function CollectionStep({
  state,
  onChange,
}: CollectionStepProps): React.ReactElement {
  return (
    <div className="space-y-4 py-2">
      <p className="text-sm text-gray-600">
        A <strong>collection</strong> is a named dataset — similar to a database
        table. You can create more collections later from the Entity Browser.
      </p>
      <div>
        <label
          className="block text-sm font-medium text-gray-700 mb-1"
          htmlFor="ob-collection"
        >
          Collection Name
        </label>
        <input
          id="ob-collection"
          type="text"
          placeholder="my-events"
          value={state.collectionName}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            onChange({ collectionName: e.target.value })
          }
          className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
        <p className="text-xs text-gray-400 mt-1">
          Use lowercase letters, numbers, and hyphens only. Skip to set this up
          later.
        </p>
      </div>
    </div>
  );
}

interface AiStepProps {
  state: AiStepState;
  onChange: (state: AiStepState) => void;
}

function AiStep({ state, onChange }: AiStepProps): React.ReactElement {
  return (
    <div className="space-y-4 py-2">
      <p className="text-sm text-gray-600">
        Data Cloud&apos;s AI Assist lets you query your data in natural
        language. You can always configure this later in{" "}
        <strong>Settings → AI</strong>.
      </p>
      <div className="flex items-center gap-3">
        <input
          id="ob-ai-enable"
          type="checkbox"
          checked={state.enableAi}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            onChange({ ...state, enableAi: e.target.checked })
          }
          className="h-4 w-4 text-indigo-600 border-gray-300 rounded"
        />
        <label
          htmlFor="ob-ai-enable"
          className="text-sm font-medium text-gray-700"
        >
          Enable AI Assist
        </label>
      </div>
      {state.enableAi && (
        <div>
          <label
            className="block text-sm font-medium text-gray-700 mb-1"
            htmlFor="ob-provider"
          >
            Provider
          </label>
          <select
            id="ob-provider"
            value={state.provider}
            onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
              onChange({
                ...state,
                provider: e.target.value as AiStepState["provider"],
              })
            }
            className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
          >
            <option value="openai">
              OpenAI (API key via OPENAI_API_KEY env)
            </option>
            <option value="ollama">Ollama (local, via OLLAMA_HOST env)</option>
            <option value="none">None — configure later</option>
          </select>
        </div>
      )}
    </div>
  );
}

function DoneStep(): React.ReactElement {
  return (
    <div className="space-y-4 py-2 text-center">
      <div className="text-4xl">🎉</div>
      <p className="text-gray-700 font-medium">Data Cloud is ready.</p>
      <p className="text-sm text-gray-500">
        Click <strong>Finish</strong> to go to the Intelligent Hub. You can
        revisit this guide anytime from <strong>Help → Onboarding</strong>.
      </p>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// DataCloudOnboardingWizard
// ─────────────────────────────────────────────────────────────────────────────

export interface DataCloudOnboardingWizardProps {
  /** Called when the wizard is completed or dismissed. */
  onComplete: () => void;
}

/**
 * Renders the onboarding wizard inside a modal overlay.
 *
 * Mount this component only when `!isOnboardingComplete()`.
 */
export function DataCloudOnboardingWizard({
  onComplete,
}: DataCloudOnboardingWizardProps): React.ReactElement {
  const [state, setState] = useState<OnboardingState>({
    connect: {
      apiBaseUrl: SessionBootstrap.getApiBaseUrl() ?? "",
      tenantId: SessionBootstrap.getTenantId() ?? "",
    },
    collection: { collectionName: "" },
    ai: { enableAi: false, provider: "none" },
  });

  const handleComplete = useCallback(() => {
    if (state.connect.apiBaseUrl.trim()) {
      SessionBootstrap.setApiBaseUrl(state.connect.apiBaseUrl);
    }

    if (state.connect.tenantId.trim()) {
      SessionBootstrap.setTenantId(state.connect.tenantId);
    }

    markOnboardingComplete();
    onComplete();
  }, [onComplete, state.connect.apiBaseUrl, state.connect.tenantId]);

  const renderStep = useCallback(
    (stepId: string, _index: number): React.ReactNode => {
      switch (stepId) {
        case "welcome":
          return <WelcomeStep />;
        case "connect":
          return (
            <ConnectStep
              state={state.connect}
              onChange={(connect: ConnectStepState) =>
                setState((s) => ({ ...s, connect }))
              }
            />
          );
        case "collection":
          return (
            <CollectionStep
              state={state.collection}
              onChange={(collection: CollectionStepState) =>
                setState((s) => ({ ...s, collection }))
              }
            />
          );
        case "ai":
          return (
            <AiStep
              state={state.ai}
              onChange={(ai: AiStepState) => setState((s) => ({ ...s, ai }))}
            />
          );
        case "done":
          return <DoneStep />;
        default:
          return null;
      }
    },
    [state],
  );

  return (
    /* Modal overlay */
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
      role="dialog"
      aria-modal="true"
      aria-labelledby="onboarding-title"
    >
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-lg mx-4 p-6">
        <h2
          id="onboarding-title"
          className="text-lg font-semibold text-gray-900 mb-4"
        >
          Getting Started
        </h2>
        <Wizard
          steps={WIZARD_STEPS}
          renderStep={(stepId: string, index: number) =>
            renderStep(stepId, index)
          }
          onComplete={handleComplete}
          onCancel={handleComplete}
          completedText="Go to Home"
          cancelText="Skip Setup"
        />
      </div>
    </div>
  );
}
