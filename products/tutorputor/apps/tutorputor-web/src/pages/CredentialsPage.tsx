import { Link } from "react-router-dom";
import { Card } from "@/components/ui";
import { Award, ShieldCheck, RotateCcw } from "lucide-react";

const credentialRequirements = [
  "Required claims mastered from assessment evidence",
  "No unresolved micro-viva requirement",
  "Credential verification URL ready after issue",
];

export function CredentialsPage() {
  return (
    <main className="p-6">
      <div className="mx-auto max-w-4xl">
        <div className="mb-6 flex items-start gap-4">
          <Award className="mt-1 h-8 w-8 text-amber-600" aria-hidden="true" />
          <div>
            <h1 className="text-3xl font-bold text-gray-950 dark:text-white">Credentials</h1>
            <p className="mt-2 text-gray-600 dark:text-gray-300">
              Credentials are issued from demonstrated mastery, not module completion alone.
            </p>
          </div>
        </div>

        <Card className="p-6">
          <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
            <div>
              <h2 className="text-xl font-semibold">Evidence-Based Simulation Foundations</h2>
              <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">
                2 of 3 required claims mastered. Remediation is due before this credential can be issued.
              </p>
            </div>
            <span className="rounded-md bg-amber-50 px-3 py-1 text-sm font-medium text-amber-700">
              Not yet eligible
            </span>
          </div>

          <ul className="mt-6 space-y-3">
            {credentialRequirements.map((requirement) => (
              <li key={requirement} className="flex items-center gap-2 text-sm">
                <ShieldCheck className="h-4 w-4 text-emerald-600" aria-hidden="true" />
                {requirement}
              </li>
            ))}
          </ul>

          <div className="mt-6 flex flex-wrap gap-3">
            <Link className="rounded-md bg-blue-600 px-4 py-2 font-medium text-white" to="/analytics">
              Review Mastery
            </Link>
            <Link className="inline-flex items-center gap-2 rounded-md border border-gray-300 px-4 py-2 font-medium" to="/learn/intro-to-motion">
              <RotateCcw className="h-4 w-4" aria-hidden="true" />
              Resume Remediation
            </Link>
          </div>
        </Card>
      </div>
    </main>
  );
}
