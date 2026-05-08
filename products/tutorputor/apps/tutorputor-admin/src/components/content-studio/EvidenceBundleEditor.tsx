/**
 * Evidence Bundle Editor Component
 *
 * Schema-backed component for editing evidence bundles.
 * Provides UI for managing evidence sources, coverage scores,
 * contradiction detection, and validation status.
 *
 * @doc.type component
 * @doc.purpose Edit evidence bundles for claims
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState } from "react";
import { Plus, Trash2, ExternalLink, AlertTriangle, CheckCircle } from "lucide-react";
import { Button } from "@ghatana/design-system";

export interface EvidenceItem {
  id: string;
  sourceType: "textbook" | "research_paper" | "website" | "video" | "database" | "other";
  sourceUrl: string;
  title: string;
  excerpt: string;
  supportKind: "supporting" | "contradicting" | "neutral" | "irrelevant";
  credibilityScore: number; // 0.0 - 1.0
  retrievedAt: string;
  freshnessStatus: "current" | "outdated" | "unknown";
  verificationState: "verified" | "unverified" | "disputed";
}

export interface EvidenceBundle {
  id: string;
  claimId: string;
  experienceId?: string;
  coverageScore: number; // 0.0 - 1.0
  contradictionDetected: boolean;
  confidenceScore: number; // 0.0 - 1.0
  evidenceCount: number;
  evidenceItems: EvidenceItem[];
  validationStatus: "pending" | "valid" | "invalid";
  validationErrors?: Record<string, unknown>;
}

interface EvidenceBundleEditorProps {
  bundle: EvidenceBundle;
  onChange: (bundle: EvidenceBundle) => void;
  readonly?: boolean;
}

const SOURCE_TYPES = [
  "textbook",
  "research_paper",
  "website",
  "video",
  "database",
  "other",
] as const;

const SUPPORT_KINDS = [
  "supporting",
  "contradicting",
  "neutral",
  "irrelevant",
] as const;

const FRESHNESS_STATUSES = ["current", "outdated", "unknown"] as const;

const VERIFICATION_STATES = ["verified", "unverified", "disputed"] as const;

export function EvidenceBundleEditor({
  bundle,
  onChange,
  readonly = false,
}: EvidenceBundleEditorProps) {
  const [expandedItems, setExpandedItems] = useState<Set<string>>(new Set());

  const toggleItemExpansion = (itemId: string) => {
    const newExpanded = new Set(expandedItems);
    if (newExpanded.has(itemId)) {
      newExpanded.delete(itemId);
    } else {
      newExpanded.add(itemId);
    }
    setExpandedItems(newExpanded);
  };

  const addEvidence = () => {
    const newEvidence: EvidenceItem = {
      id: `evidence-${Date.now()}`,
      sourceType: "textbook",
      sourceUrl: "",
      title: "",
      excerpt: "",
      supportKind: "supporting",
      credibilityScore: 0.5,
      retrievedAt: new Date().toISOString(),
      freshnessStatus: "unknown",
      verificationState: "unverified",
    };

    onChange({
      ...bundle,
      evidenceItems: [...bundle.evidenceItems, newEvidence],
      evidenceCount: bundle.evidenceItems.length + 1,
    });
    setExpandedItems(new Set([...expandedItems, newEvidence.id]));
  };

  const updateEvidence = (evidenceId: string, updates: Partial<EvidenceItem>) => {
    onChange({
      ...bundle,
      evidenceItems: bundle.evidenceItems.map((ev) =>
        ev.id === evidenceId ? { ...ev, ...updates } : ev,
      ),
    });
  };

  const deleteEvidence = (evidenceId: string) => {
    onChange({
      ...bundle,
      evidenceItems: bundle.evidenceItems.filter((ev) => ev.id !== evidenceId),
      evidenceCount: bundle.evidenceItems.length - 1,
    });
    const newExpanded = new Set(expandedItems);
    newExpanded.delete(evidenceId);
    setExpandedItems(newExpanded);
  };

  const recalculateCoverage = () => {
    const supporting = bundle.evidenceItems.filter(
      (ev) => ev.supportKind === "supporting" && ev.verificationState === "verified",
    );
    const contradicting = bundle.evidenceItems.filter(
      (ev) => ev.supportKind === "contradicting",
    );

    const coverageScore = bundle.evidenceItems.length > 0
      ? supporting.length / bundle.evidenceItems.length
      : 0;
    const contradictionDetected = contradicting.length > 0;
    const confidenceScore =
      bundle.evidenceItems.length > 0
        ? bundle.evidenceItems.reduce((sum, ev) => sum + ev.credibilityScore, 0) /
          bundle.evidenceItems.length
        : 0;

    onChange({
      ...bundle,
      coverageScore,
      contradictionDetected,
      confidenceScore,
    });
  };

  const supportingCount = bundle.evidenceItems.filter(
    (ev) => ev.supportKind === "supporting",
  ).length;
  const contradictingCount = bundle.evidenceItems.filter(
    (ev) => ev.supportKind === "contradicting",
  ).length;

  return (
    <div className="space-y-4">
      {/* Bundle Summary */}
      <div className="p-4 border rounded-lg bg-gray-50">
        <div className="grid grid-cols-4 gap-4">
          <div>
            <div className="text-sm text-gray-500">Coverage Score</div>
            <div className="text-2xl font-bold">
              {(bundle.coverageScore * 100).toFixed(0)}%
            </div>
          </div>
          <div>
            <div className="text-sm text-gray-500">Confidence</div>
            <div className="text-2xl font-bold">
              {(bundle.confidenceScore * 100).toFixed(0)}%
            </div>
          </div>
          <div>
            <div className="text-sm text-gray-500">Supporting</div>
            <div className="text-2xl font-bold text-green-600">
              {supportingCount}
            </div>
          </div>
          <div>
            <div className="text-sm text-gray-500">Contradicting</div>
            <div className="text-2xl font-bold text-red-600">
              {contradictingCount}
            </div>
          </div>
        </div>

        {bundle.contradictionDetected && (
          <div className="mt-3 flex items-center gap-2 text-red-600 bg-red-50 p-2 rounded">
            <AlertTriangle className="w-4 h-4" />
            <span className="text-sm font-medium">
              Contradictions detected in evidence
            </span>
          </div>
        )}

        {!readonly && (
          <Button
            variant="outline"
            size="sm"
            onClick={recalculateCoverage}
            className="mt-3"
          >
            Recalculate Coverage
          </Button>
        )}
      </div>

      {/* Validation Status */}
      <div className="flex items-center gap-2">
        <span className="text-sm font-medium">Validation Status:</span>
        <span
          className={`px-2 py-1 rounded text-xs font-medium ${
            bundle.validationStatus === "valid"
              ? "bg-green-100 text-green-700"
              : bundle.validationStatus === "invalid"
              ? "bg-red-100 text-red-700"
              : "bg-yellow-100 text-yellow-700"
          }`}
        >
          {bundle.validationStatus.toUpperCase()}
        </span>
        {bundle.validationStatus === "valid" && (
          <CheckCircle className="w-4 h-4 text-green-600" />
        )}
      </div>

      {/* Evidence Items */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-semibold">Evidence Items ({bundle.evidenceItems.length})</h3>
          {!readonly && (
            <Button onClick={addEvidence} size="sm">
              <Plus className="w-4 h-4 mr-2" />
              Add Evidence
            </Button>
          )}
        </div>

        {bundle.evidenceItems.length === 0 ? (
          <div className="text-center py-8 text-gray-500 border-2 border-dashed rounded-lg">
            No evidence items added yet. Add your first evidence source.
          </div>
        ) : (
          <div className="space-y-3">
            {bundle.evidenceItems.map((evidence) => (
              <div
                key={evidence.id}
                className="border rounded-lg overflow-hidden"
              >
                <div className="flex items-center gap-2 p-3 bg-gray-50">
                  {evidence.supportKind === "supporting" && (
                    <CheckCircle className="w-4 h-4 text-green-600" />
                  )}
                  {evidence.supportKind === "contradicting" && (
                    <AlertTriangle className="w-4 h-4 text-red-600" />
                  )}
                  <span className="flex-1 font-medium truncate">{evidence.title || "Untitled Evidence"}</span>
                  <span className="text-xs text-gray-500">{evidence.sourceType}</span>
                  <span
                    className={`px-2 py-1 rounded text-xs ${
                      evidence.verificationState === "verified"
                        ? "bg-green-100 text-green-700"
                        : evidence.verificationState === "disputed"
                        ? "bg-red-100 text-red-700"
                        : "bg-gray-100 text-gray-700"
                    }`}
                  >
                    {evidence.verificationState}
                  </span>
                  {!readonly && (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => deleteEvidence(evidence.id)}
                    >
                      <Trash2 className="w-4 h-4 text-red-500" />
                    </Button>
                  )}
                </div>

                {expandedItems.has(evidence.id) && (
                  <div className="p-4 space-y-3">
                    <div>
                      <label className="block text-sm font-medium mb-1">Title</label>
                      <input
                        type="text"
                        value={evidence.title}
                        onChange={(e) =>
                          updateEvidence(evidence.id, { title: e.target.value })
                        }
                        className="w-full px-3 py-2 border rounded"
                        disabled={readonly}
                      />
                    </div>

                    <div>
                      <label className="block text-sm font-medium mb-1">Source URL</label>
                      <div className="flex gap-2">
                        <input
                          type="url"
                          value={evidence.sourceUrl}
                          onChange={(e) =>
                            updateEvidence(evidence.id, { sourceUrl: e.target.value })
                          }
                          className="flex-1 px-3 py-2 border rounded"
                          disabled={readonly}
                          placeholder="https://..."
                        />
                        {evidence.sourceUrl && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => window.open(evidence.sourceUrl, "_blank")}
                          >
                            <ExternalLink className="w-4 h-4" />
                          </Button>
                        )}
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-sm font-medium mb-1">
                          Source Type
                        </label>
                        <select
                          value={evidence.sourceType}
                          onChange={(e) =>
                            updateEvidence(evidence.id, {
                              sourceType: e.target.value as EvidenceItem["sourceType"],
                            })
                          }
                          className="w-full px-3 py-2 border rounded"
                          disabled={readonly}
                        >
                          {SOURCE_TYPES.map((type) => (
                            <option key={type} value={type}>
                              {type.replace("_", " ").toUpperCase()}
                            </option>
                          ))}
                        </select>
                      </div>

                      <div>
                        <label className="block text-sm font-medium mb-1">Support Kind</label>
                        <select
                          value={evidence.supportKind}
                          onChange={(e) =>
                            updateEvidence(evidence.id, {
                              supportKind: e.target.value as EvidenceItem["supportKind"],
                            })
                          }
                          className="w-full px-3 py-2 border rounded"
                          disabled={readonly}
                        >
                          {SUPPORT_KINDS.map((kind) => (
                            <option key={kind} value={kind}>
                              {kind.toUpperCase()}
                            </option>
                          ))}
                        </select>
                      </div>
                    </div>

                    <div className="grid grid-cols-3 gap-3">
                      <div>
                        <label className="block text-sm font-medium mb-1">
                          Credibility: {(evidence.credibilityScore * 100).toFixed(0)}%
                        </label>
                        <input
                          type="range"
                          min="0"
                          max="1"
                          step="0.1"
                          value={evidence.credibilityScore}
                          onChange={(e) =>
                            updateEvidence(evidence.id, {
                              credibilityScore: parseFloat(e.target.value),
                            })
                          }
                          className="w-full"
                          disabled={readonly}
                        />
                      </div>

                      <div>
                        <label className="block text-sm font-medium mb-1">Freshness</label>
                        <select
                          value={evidence.freshnessStatus}
                          onChange={(e) =>
                            updateEvidence(evidence.id, {
                              freshnessStatus: e.target.value as EvidenceItem["freshnessStatus"],
                            })
                          }
                          className="w-full px-3 py-2 border rounded"
                          disabled={readonly}
                        >
                          {FRESHNESS_STATUSES.map((status) => (
                            <option key={status} value={status}>
                              {status.toUpperCase()}
                            </option>
                          ))}
                        </select>
                      </div>

                      <div>
                        <label className="block text-sm font-medium mb-1">Verification</label>
                        <select
                          value={evidence.verificationState}
                          onChange={(e) =>
                            updateEvidence(evidence.id, {
                              verificationState: e.target.value as EvidenceItem["verificationState"],
                            })
                          }
                          className="w-full px-3 py-2 border rounded"
                          disabled={readonly}
                        >
                          {VERIFICATION_STATES.map((state) => (
                            <option key={state} value={state}>
                              {state.toUpperCase()}
                            </option>
                          ))}
                        </select>
                      </div>
                    </div>

                    <div>
                      <label className="block text-sm font-medium mb-1">Excerpt</label>
                      <textarea
                        value={evidence.excerpt}
                        onChange={(e) =>
                          updateEvidence(evidence.id, { excerpt: e.target.value })
                        }
                        rows={3}
                        className="w-full px-3 py-2 border rounded"
                        disabled={readonly}
                        placeholder="Relevant excerpt from the source..."
                      />
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
