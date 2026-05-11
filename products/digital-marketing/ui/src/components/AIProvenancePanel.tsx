/**
 * AIProvenancePanel — collapsible AI reasoning and provenance display.
 *
 * <p>P2-004: Surfaces model version, generation timestamp, assumptions, and
 * rationale to satisfy AI transparency requirements.</p>
 *
 * @doc.type component
 * @doc.purpose AI transparency provenance display for AI-generated artefacts
 * @doc.layer frontend
 * @doc.pattern Presentational
 */
import React, { useState } from 'react';
import { formatDateTime } from '@/lib/i18n/format';

export interface AIProvenancePanelProps {
  /** Provider/service name for the model invocation. */
  provider?: string | null;
  /** Identifier / tag of the model that produced the artefact. */
  modelVersion: string;
  /** ISO-8601 timestamp when the artefact was generated. */
  generatedAt: string;
  /** Identifier of the service or user that triggered generation. */
  generatedBy: string;
  /** Free-text rationale produced by the model. */
  rationale?: string | null;
  /** Free-text assumptions the model relied on. */
  assumptions?: string | null;
  /** Optional risk assessment text. */
  riskAssessment?: string | null;
  /** Optional list of evidence strings / source references. */
  evidence?: string[] | null;
  /** Optional confidence score (0–1). */
  confidence?: number | null;
}

/**
 * Collapsible panel that displays AI provenance metadata for any AI-generated
 * artefact (strategy, budget recommendation, etc.).
 */
export function AIProvenancePanel({
  provider,
  modelVersion,
  generatedAt,
  generatedBy,
  rationale,
  assumptions,
  riskAssessment,
  evidence,
  confidence,
}: AIProvenancePanelProps): React.ReactElement {
  const [open, setOpen] = useState(false);

  const formattedDate = formatDateTime(generatedAt, { fallback: generatedAt });

  return (
    <div
      data-testid="ai-provenance-panel"
      className="mt-4 border border-blue-200 rounded-lg bg-blue-50"
    >
      <button
        type="button"
        data-testid="ai-provenance-toggle"
        onClick={() => setOpen((v) => !v)}
        className="w-full flex items-center justify-between px-4 py-3 text-left"
        aria-expanded={open}
        aria-controls="ai-provenance-body"
      >
        <span className="flex items-center gap-2 text-sm font-medium text-blue-800">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            className="h-4 w-4"
            viewBox="0 0 20 20"
            fill="currentColor"
            aria-hidden="true"
          >
            <path
              fillRule="evenodd"
              d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z"
              clipRule="evenodd"
            />
          </svg>
          AI Reasoning &amp; Provenance
          {confidence != null && (
            <span className="ml-2 text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded-full">
              {Math.round(confidence * 100)}% confidence
            </span>
          )}
        </span>
        <svg
          xmlns="http://www.w3.org/2000/svg"
          className={`h-4 w-4 text-blue-600 transition-transform duration-200 ${open ? 'rotate-180' : ''}`}
          viewBox="0 0 20 20"
          fill="currentColor"
          aria-hidden="true"
        >
          <path
            fillRule="evenodd"
            d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"
            clipRule="evenodd"
          />
        </svg>
      </button>

      {open && (
        <div
          id="ai-provenance-body"
          data-testid="ai-provenance-body"
          className="px-4 pb-4 text-sm text-gray-700 space-y-3 border-t border-blue-200"
        >
          {/* Metadata row */}
          <div className="pt-3 grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
            <div>
              <span className="text-gray-500">Provider</span>
              <p className="font-medium mt-0.5" data-testid="ai-provider">
                {provider && provider.trim().length > 0 ? provider : 'Not provided'}
              </p>
            </div>
            <div>
              <span className="text-gray-500">Model</span>
              <p className="font-mono font-medium mt-0.5" data-testid="ai-model-version">
                {modelVersion}
              </p>
            </div>
            <div>
              <span className="text-gray-500">Generated</span>
              <p className="font-medium mt-0.5" data-testid="ai-generated-at">
                {formattedDate}
              </p>
            </div>
            <div>
              <span className="text-gray-500">By</span>
              <p className="font-medium mt-0.5 truncate" data-testid="ai-generated-by">
                {generatedBy}
              </p>
            </div>
          </div>

          {rationale && (
            <div>
              <p className="font-medium text-gray-800 mb-1">Rationale</p>
              <p data-testid="ai-rationale" className="text-gray-600 whitespace-pre-line">
                {rationale}
              </p>
            </div>
          )}

          {assumptions && (
            <div>
              <p className="font-medium text-gray-800 mb-1">Assumptions</p>
              <p data-testid="ai-assumptions" className="text-gray-600 whitespace-pre-line">
                {assumptions}
              </p>
            </div>
          )}

          {riskAssessment && (
            <div>
              <p className="font-medium text-gray-800 mb-1">Risk Assessment</p>
              <p data-testid="ai-risk-assessment" className="text-gray-600 whitespace-pre-line">
                {riskAssessment}
              </p>
            </div>
          )}

          {evidence && evidence.length > 0 && (
            <div>
              <p className="font-medium text-gray-800 mb-1">Evidence</p>
              <ul
                data-testid="ai-evidence-list"
                className="list-disc list-inside space-y-1 text-gray-600"
              >
                {evidence.map((item, idx) => (
                  <li key={idx}>{item}</li>
                ))}
              </ul>
            </div>
          )}

          <p className="text-xs text-blue-600 italic pt-1">
            This content was generated by AI. Review it carefully before acting on recommendations.
          </p>
        </div>
      )}
    </div>
  );
}
