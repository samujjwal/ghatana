import { Link } from "react-router-dom";
import { Card } from "@/components/ui";
import { Activity, Brain, CheckCircle2, Route } from "lucide-react";

const diagnosticRows = [
  {
    claim: "Predict motion from initial conditions",
    status: "Ready",
    action: "Run simulation diagnostic",
  },
  {
    claim: "Explain evidence from a visual model",
    status: "Needs check",
    action: "Answer short explanation",
  },
  {
    claim: "Calibrate confidence",
    status: "New",
    action: "Complete CBM warm-up",
  },
];

export function DiagnosticPage() {
  return (
    <main className="p-6">
      <div className="mx-auto max-w-5xl">
        <div className="mb-6">
          <h1 className="text-3xl font-bold text-gray-950 dark:text-white">Diagnostic</h1>
          <p className="mt-2 text-gray-600 dark:text-gray-300">
            Establish a claim-level baseline before TutorPutor recommends your next lesson.
          </p>
        </div>

        <div className="grid gap-4 md:grid-cols-3">
          <Card className="p-4">
            <Brain className="mb-3 h-6 w-6 text-blue-600" aria-hidden="true" />
            <h2 className="font-semibold">Current mastery</h2>
            <p className="mt-1 text-2xl font-bold">42%</p>
          </Card>
          <Card className="p-4">
            <Activity className="mb-3 h-6 w-6 text-emerald-600" aria-hidden="true" />
            <h2 className="font-semibold">Evidence needed</h2>
            <p className="mt-1 text-2xl font-bold">3 claims</p>
          </Card>
          <Card className="p-4">
            <Route className="mb-3 h-6 w-6 text-indigo-600" aria-hidden="true" />
            <h2 className="font-semibold">Next route</h2>
            <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">Simulation warm-up</p>
          </Card>
        </div>

        <Card className="mt-6 p-0">
          <div className="divide-y divide-gray-200 dark:divide-gray-800">
            {diagnosticRows.map((row) => (
              <div key={row.claim} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h2 className="font-medium text-gray-950 dark:text-white">{row.claim}</h2>
                  <p className="text-sm text-gray-600 dark:text-gray-300">{row.action}</p>
                </div>
                <span className="inline-flex items-center gap-2 rounded-md bg-blue-50 px-3 py-1 text-sm text-blue-700">
                  <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
                  {row.status}
                </span>
              </div>
            ))}
          </div>
        </Card>

        <div className="mt-6 flex gap-3">
          <Link className="rounded-md bg-blue-600 px-4 py-2 font-medium text-white" to="/learn/intro-to-motion">
            Start Diagnostic
          </Link>
          <Link className="rounded-md border border-gray-300 px-4 py-2 font-medium" to="/pathways">
            View Pathway
          </Link>
        </div>
      </div>
    </main>
  );
}
