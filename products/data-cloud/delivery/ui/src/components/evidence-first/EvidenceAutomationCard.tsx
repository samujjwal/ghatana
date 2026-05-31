import type { ReactElement } from "react";

export interface EvidenceAutomationCardProps {
  title: string;
  why: string;
  dataUsed: string[];
  confidence: number;
  policy: string;
  audit: string;
  overrideControl: ReactElement;
}

function confidenceLabel(confidence: number): string {
  if (confidence >= 0.9) {
    return "high";
  }
  if (confidence >= 0.7) {
    return "medium";
  }
  return "low";
}

export function EvidenceAutomationCard(
  props: EvidenceAutomationCardProps,
): ReactElement {
  const confidencePercent = Math.round(props.confidence * 100);

  return (
    <section
      className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm"
      aria-label={props.title}
    >
      <header className="mb-3 flex items-center justify-between gap-3">
        <h3 className="text-sm font-semibold text-slate-900">{props.title}</h3>
        <span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-medium text-slate-700">
          Confidence: {confidencePercent}% ({confidenceLabel(props.confidence)})
        </span>
      </header>

      <dl className="grid grid-cols-1 gap-3 text-sm text-slate-700">
        <div>
          <dt className="font-medium text-slate-900">Why</dt>
          <dd>{props.why}</dd>
        </div>
        <div>
          <dt className="font-medium text-slate-900">Data Used</dt>
          <dd>
            <ul className="list-disc pl-5">
              {props.dataUsed.map((entry) => (
                <li key={entry}>{entry}</li>
              ))}
            </ul>
          </dd>
        </div>
        <div>
          <dt className="font-medium text-slate-900">Policy</dt>
          <dd>{props.policy}</dd>
        </div>
        <div>
          <dt className="font-medium text-slate-900">Audit</dt>
          <dd>{props.audit}</dd>
        </div>
        <div>
          <dt className="font-medium text-slate-900">Override Control</dt>
          <dd className="mt-1">{props.overrideControl}</dd>
        </div>
      </dl>
    </section>
  );
}
