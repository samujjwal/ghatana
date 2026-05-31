/**
 * Policy Simulation Page (DC-P3-004)
 *
 * Governance what-if simulation mode for policy changes.
 *
 * @doc.type page
 * @doc.purpose Simulate governance policy impact before mutation
 * @doc.layer frontend
 * @doc.pattern Form + Simulation Result
 */

import { useMutation } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { useState } from "react";
import {
  governanceService,
  type Policy,
  type PolicySimulationResult,
} from "../api/governance.service";

interface SimulationFormState {
  name: string;
  type: Policy["type"];
  description: string;
  datasets: string;
}

const DEFAULT_FORM: SimulationFormState = {
  name: "Proposed policy",
  type: "QUALITY",
  description: "",
  datasets: "",
};

function parseDatasets(value: string): string[] {
  return value
    .split(",")
    .map((item) => item.trim())
    .filter((item) => item.length > 0);
}

export function PolicySimulationPage(): ReactElement {
  const [form, setForm] = useState<SimulationFormState>(DEFAULT_FORM);
  const [result, setResult] = useState<PolicySimulationResult | null>(null);

  const simulateMutation = useMutation({
    mutationFn: () =>
      governanceService.simulatePolicy({
        name: form.name,
        type: form.type,
        enabled: true,
        scope: {
          datasets: parseDatasets(form.datasets),
        },
        rules: [
          {
            condition: "default-condition",
            action: "DENY",
            severity: "WARNING",
          },
        ],
        metadata: {
          source: "policy-simulation-page",
          description: form.description,
        },
      }),
    onSuccess: (simulationResult) => {
      setResult(simulationResult);
    },
  });

  return (
    <main
      className="min-h-screen bg-gray-50 dark:bg-gray-950 p-6"
      data-testid="policy-simulation-page"
    >
      <div className="mx-auto max-w-4xl space-y-6">
        <header>
          <h1 className="text-xl font-bold text-gray-900 dark:text-white">
            Policy Simulation
          </h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            Dry-run governance changes before creating or updating policies.
          </p>
        </header>

        <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <label className="text-sm text-gray-700 dark:text-gray-300">
              Policy name
              <input
                className="mt-1 w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-900"
                value={form.name}
                onChange={(event) => {
                  setForm((current) => ({
                    ...current,
                    name: event.target.value,
                  }));
                }}
              />
            </label>

            <label className="text-sm text-gray-700 dark:text-gray-300">
              Policy type
              <select
                className="mt-1 w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-900"
                value={form.type}
                onChange={(event) => {
                  setForm((current) => ({
                    ...current,
                    type: event.target.value as Policy["type"],
                  }));
                }}
              >
                <option value="QUALITY">QUALITY</option>
                <option value="PRIVACY">PRIVACY</option>
                <option value="SECURITY">SECURITY</option>
                <option value="RETENTION">RETENTION</option>
                <option value="ACCESS">ACCESS</option>
              </select>
            </label>

            <label className="text-sm text-gray-700 dark:text-gray-300 md:col-span-2">
              Description
              <input
                className="mt-1 w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-900"
                value={form.description}
                onChange={(event) => {
                  setForm((current) => ({
                    ...current,
                    description: event.target.value,
                  }));
                }}
              />
            </label>

            <label className="text-sm text-gray-700 dark:text-gray-300 md:col-span-2">
              Scoped datasets (comma-separated)
              <input
                className="mt-1 w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-900"
                placeholder="orders, customers, payments"
                value={form.datasets}
                onChange={(event) => {
                  setForm((current) => ({
                    ...current,
                    datasets: event.target.value,
                  }));
                }}
              />
            </label>
          </div>

          <div className="mt-4">
            <button
              type="button"
              onClick={() => {
                void simulateMutation.mutateAsync();
              }}
              disabled={simulateMutation.isPending}
              className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-60"
            >
              {simulateMutation.isPending ? "Simulating..." : "Run simulation"}
            </button>
          </div>
        </section>

        {simulateMutation.isError && (
          <section className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700 dark:border-rose-900/40 dark:bg-rose-900/10 dark:text-rose-300">
            {simulateMutation.error instanceof Error
              ? simulateMutation.error.message
              : "Failed to simulate policy."}
          </section>
        )}

        {result && (
          <section
            className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900"
            data-testid="policy-simulation-result"
          >
            <h2 className="text-sm font-semibold text-gray-900 dark:text-gray-100">
              Simulation Result
            </h2>
            <div className="mt-3 grid grid-cols-2 gap-3 text-sm md:grid-cols-3">
              <div>
                <span className="text-gray-500">Risk</span>
                <div className="font-medium">
                  {result.riskLevel.toUpperCase()}
                </div>
              </div>
              <div>
                <span className="text-gray-500">Affected collections</span>
                <div className="font-medium">{result.affectedCollections}</div>
              </div>
              <div>
                <span className="text-gray-500">Policy conflicts</span>
                <div className="font-medium">{result.policyConflicts}</div>
              </div>
              <div>
                <span className="text-gray-500">Estimated blocked ops</span>
                <div className="font-medium">
                  {result.estimatedBlockedOperations}
                </div>
              </div>
              <div>
                <span className="text-gray-500">Scope size</span>
                <div className="font-medium">
                  {result.sampleCollections.length}
                </div>
              </div>
              <div>
                <span className="text-gray-500">Simulated at</span>
                <div className="font-medium">
                  {new Date(result.simulatedAt).toLocaleString()}
                </div>
              </div>
            </div>

            <div className="mt-4">
              <h3 className="text-xs uppercase tracking-wide text-gray-500">
                Recommendations
              </h3>
              <ul className="mt-2 space-y-1 text-sm text-gray-700 dark:text-gray-300">
                {result.recommendations.map((recommendation) => (
                  <li key={recommendation}>• {recommendation}</li>
                ))}
              </ul>
            </div>
          </section>
        )}
      </div>
    </main>
  );
}
