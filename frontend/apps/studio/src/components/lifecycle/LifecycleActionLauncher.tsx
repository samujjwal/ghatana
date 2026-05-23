/**
 * Lifecycle Action Launcher - UI for executing lifecycle actions (plan, explain, execute, recover).
 *
 * @doc.type component
 * @doc.purpose Provide lifecycle action launcher with plan/explain/execute/recover flows
 * @doc.layer studio
 */

import React, { useState } from "react";

interface LifecycleAction {
  id: string;
  name: string;
  description: string;
  type: "plan" | "explain" | "execute" | "recover";
  requiresConfirmation: boolean;
}

interface LifecycleActionLauncherProps {
  productId: string;
  productUnitId: string;
  onActionComplete: (actionId: string, result: any) => void;
}

const ACTIONS: LifecycleAction[] = [
  {
    id: "plan",
    name: "Plan",
    description: "Generate a lifecycle plan for the product unit",
    type: "plan",
    requiresConfirmation: false,
  },
  {
    id: "explain",
    name: "Explain",
    description: "Explain the current lifecycle state and next steps",
    type: "explain",
    requiresConfirmation: false,
  },
  {
    id: "execute",
    name: "Execute",
    description: "Execute the lifecycle plan",
    type: "execute",
    requiresConfirmation: true,
  },
  {
    id: "recover",
    name: "Recover",
    description: "Recover from a failed lifecycle operation",
    type: "recover",
    requiresConfirmation: true,
  },
] as const;

type ActionId = (typeof ACTIONS)[number]["id"];

export function LifecycleActionLauncher({
  productId,
  productUnitId,
  onActionComplete,
}: LifecycleActionLauncherProps) {
  const [selectedAction, setSelectedAction] = useState<ActionId | null>(null);
  const [isExecuting, setIsExecuting] = useState(false);
  const [result, setResult] = useState<any>(null);
  const [showConfirmation, setShowConfirmation] = useState(false);

  const handleActionSelect = (actionId: ActionId) => {
    const action = ACTIONS.find((a) => a.id === actionId);
    if (action?.requiresConfirmation) {
      setSelectedAction(actionId);
      setShowConfirmation(true);
    } else {
      executeAction(actionId);
    }
  };

  const executeAction = async (actionId: ActionId) => {
    setIsExecuting(true);
    setResult(null);

    // Simulate API call
    await new Promise((resolve) => setTimeout(resolve, 2000));

    const mockResult = {
      actionId,
      productId,
      productUnitId,
      status: "success",
      timestamp: new Date().toISOString(),
      output: generateMockOutput(actionId),
    };

    setResult(mockResult);
    setIsExecuting(false);
    setShowConfirmation(false);
    onActionComplete(actionId, mockResult);
  };

  const generateMockOutput = (actionId: ActionId) => {
    switch (actionId) {
      case "plan":
        return {
          steps: [
            { id: "validate", name: "Validate configuration", estimatedDuration: "30s" },
            { id: "preflight", name: "Run preflight checks", estimatedDuration: "1m" },
            { id: "build", name: "Build artifacts", estimatedDuration: "2m" },
            { id: "test", name: "Run tests", estimatedDuration: "3m" },
            { id: "package", name: "Package artifacts", estimatedDuration: "1m" },
          ],
          totalEstimatedDuration: "7m 30s",
        };
      case "explain":
        return {
          currentState: "ready",
          lastExecution: "2026-05-22T10:00:00Z",
          nextSteps: ["Validation pending", "Preflight checks required"],
          affectedSurfaces: ["web", "api"],
        };
      case "execute":
        return {
          stepsCompleted: 5,
          stepsTotal: 5,
          duration: "7m 45s",
          artifacts: ["app.jar", "docker-image.tar"],
        };
      case "recover":
        return {
          recoveryPoint: "2026-05-22T09:30:00Z",
          actionsTaken: ["Rollback to previous version", "Clean up failed artifacts"],
          status: "recovered",
        };
      default:
        return {};
    }
  };

  return (
    <div className="lifecycle-action-launcher">
      <h2 className="text-2xl font-bold text-gray-900 mb-6">Lifecycle Actions</h2>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
        {ACTIONS.map((action) => (
          <button
            key={action.id}
            onClick={() => handleActionSelect(action.id)}
            disabled={isExecuting}
            className={`p-4 border rounded-lg text-left transition-colors ${
              isExecuting
                ? "bg-gray-100 cursor-not-allowed"
                : "bg-white hover:bg-gray-50 hover:border-blue-300"
            }`}
          >
            <div className="flex items-center justify-between mb-2">
              <h3 className="font-semibold text-gray-900">{action.name}</h3>
              {action.requiresConfirmation && (
                <span className="px-2 py-1 bg-yellow-100 text-yellow-800 rounded text-xs">
                  Requires confirmation
                </span>
              )}
            </div>
            <p className="text-sm text-gray-600">{action.description}</p>
          </button>
        ))}
      </div>

      {showConfirmation && selectedAction && (
        <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4 mb-6">
          <h3 className="font-semibold text-yellow-900 mb-2">Confirm Action</h3>
          <p className="text-sm text-yellow-800 mb-4">
            Are you sure you want to execute the "{selectedAction}" action? This action may modify the product unit state.
          </p>
          <div className="flex gap-2">
            <button
              onClick={() => executeAction(selectedAction)}
              className="px-4 py-2 bg-yellow-600 text-white rounded-md hover:bg-yellow-700"
            >
              Confirm
            </button>
            <button
              onClick={() => {
                setShowConfirmation(false);
                setSelectedAction(null);
              }}
              className="px-4 py-2 bg-gray-200 text-gray-700 rounded-md hover:bg-gray-300"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {isExecuting && (
        <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 mb-6">
          <div className="flex items-center">
            <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-blue-600 mr-2"></div>
            <p className="text-sm text-blue-900">Executing action...</p>
          </div>
        </div>
      )}

      {result && (
        <div className="bg-white border border-gray-200 rounded-lg p-6">
          <h3 className="font-semibold text-gray-900 mb-4">Action Result</h3>
          <pre className="bg-gray-50 p-4 rounded-md overflow-x-auto text-sm">
            {JSON.stringify(result, null, 2)}
          </pre>
        </div>
      )}
    </div>
  );
}
