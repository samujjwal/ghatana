/**
 * STORY-W02-006: Manual KYC Review UI
 * KYC review portal for compliance officers: list pending reviews (doc verification failures,
 * sanctions hits, PEP matches). Review detail: client info, documents (with preview),
 * AI verification results, sanctions screening result.
 * Actions: APPROVE ALL, REJECT DOCUMENT (with reason, request re-upload), APPROVE WITH EDD.
 * SLA: reviews completed within 2 business days.
 */

import React, { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";

// ── Types ─────────────────────────────────────────────────────────────────────

type ReviewStatus = "PENDING" | "IN_REVIEW" | "APPROVED" | "APPROVED_WITH_EDD" | "REJECTED";
type ReviewReason = "DOC_VERIFICATION_FAILURE" | "SANCTIONS_HIT" | "PEP_MATCH" | "AI_LOW_CONFIDENCE" | "COMPLIANCE_MANUAL";
type DocumentStatus = "UPLOADED" | "UNDER_REVIEW" | "APPROVED" | "REJECTED" | "REUPLOAD_REQUESTED";

interface KycDocument {
  documentType: string;
  objectKey: string;
  previewUrl: string;
  uploadedAt: string;
  status: DocumentStatus;
  aiConfidence?: "HIGH" | "MEDIUM" | "LOW";
  aiOutcome?: "APPROVED" | "REQUIRES_REVIEW" | "REJECTED";
  rejectionReason?: string;
}

interface SanctionsResult {
  screened: boolean;
  hit: boolean;
  hitList?: string;
  matchScore?: number;
  reviewedAt?: string;
}

interface KycReview {
  reviewId: string;
  instanceId: string;
  clientId: string;
  clientName: string;
  clientType: "INDIVIDUAL" | "INSTITUTIONAL";
  reviewReason: ReviewReason;
  status: ReviewStatus;
  submittedAt: string;
  slaDeadline: string;
  assignedTo?: string;
  documents: KycDocument[];
  sanctionsResult?: SanctionsResult;
}

// ── API ───────────────────────────────────────────────────────────────────────

const api = {
  listReviews: async (status: string): Promise<KycReview[]> => {
    const r = await fetch(`/api/kyc/reviews?status=${status}`);
    if (!r.ok) throw new Error("Failed to fetch reviews");
    return r.json();
  },
  approveAll: async (reviewId: string): Promise<void> => {
    const r = await fetch(`/api/kyc/reviews/${reviewId}/approve`, { method: "POST" });
    if (!r.ok) throw new Error("Failed to approve");
  },
  approveWithEdd: async (reviewId: string): Promise<void> => {
    const r = await fetch(`/api/kyc/reviews/${reviewId}/approve-edd`, { method: "POST" });
    if (!r.ok) throw new Error("Failed to approve with EDD");
  },
  rejectDocument: async (reviewId: string, documentType: string, reason: string): Promise<void> => {
    const r = await fetch(`/api/kyc/reviews/${reviewId}/reject-document`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ documentType, reason }),
    });
    if (!r.ok) throw new Error("Failed to reject document");
  },
};

// ── Status badges ─────────────────────────────────────────────────────────────

const REVIEW_STATUS_COLOR: Record<ReviewStatus, string> = {
  PENDING: "bg-gray-100 text-gray-600",
  IN_REVIEW: "bg-blue-100 text-blue-700",
  APPROVED: "bg-green-100 text-green-700",
  APPROVED_WITH_EDD: "bg-teal-100 text-teal-700",
  REJECTED: "bg-red-100 text-red-700",
};

const REASON_LABEL: Record<ReviewReason, string> = {
  DOC_VERIFICATION_FAILURE: "Doc Verification Failure",
  SANCTIONS_HIT: "⚠ Sanctions Hit",
  PEP_MATCH: "⚠ PEP Match",
  AI_LOW_CONFIDENCE: "AI Low Confidence",
  COMPLIANCE_MANUAL: "Manual Review",
};

const REASON_COLOR: Record<ReviewReason, string> = {
  DOC_VERIFICATION_FAILURE: "bg-amber-100 text-amber-700",
  SANCTIONS_HIT: "bg-red-200 text-red-800 font-semibold",
  PEP_MATCH: "bg-red-100 text-red-700",
  AI_LOW_CONFIDENCE: "bg-orange-100 text-orange-700",
  COMPLIANCE_MANUAL: "bg-gray-100 text-gray-600",
};

// ── SLA countdown ─────────────────────────────────────────────────────────────

function SlaCountdown({ deadline }: { deadline: string }) {
  const diff = new Date(deadline).getTime() - Date.now();
  if (diff <= 0) return <span className="text-xs text-red-700 font-semibold">OVERDUE</span>;
  const hours = Math.floor(diff / 3600000);
  return (
    <span className={`text-xs ${hours < 8 ? "text-red-600 font-medium" : hours < 24 ? "text-amber-600" : "text-gray-500"}`}>
      {hours}h remaining
    </span>
  );
}

// ── Document card ─────────────────────────────────────────────────────────────

function DocumentCard({
  doc,
  reviewId,
  onReject,
}: {
  doc: KycDocument;
  reviewId: string;
  onReject: (documentType: string) => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const confidenceColor: Record<string, string> = {
    HIGH: "text-green-600",
    MEDIUM: "text-amber-600",
    LOW: "text-red-600",
  };

  return (
    <div className="border rounded-lg overflow-hidden">
      {/* Preview */}
      <div
        className="bg-gray-100 h-32 flex items-center justify-center cursor-pointer relative group"
        onClick={() => setExpanded(!expanded)}
      >
        {doc.previewUrl ? (
          <img src={doc.previewUrl} alt={doc.documentType} className="max-h-32 object-contain" />
        ) : (
          <div className="text-gray-400 text-sm">📄 {doc.documentType}</div>
        )}
        <div className="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors flex items-end justify-end p-2">
          {doc.previewUrl && (
            <a
              href={doc.previewUrl}
              target="_blank"
              rel="noreferrer"
              onClick={(e) => e.stopPropagation()}
              className="text-xs bg-black/60 text-white px-2 py-0.5 rounded"
            >
              Full view ↗
            </a>
          )}
        </div>
      </div>

      <div className="p-3">
        <div className="flex items-center justify-between mb-1">
          <p className="text-sm font-medium">{doc.documentType.replace(/_/g, " ")}</p>
          <span className={`px-1.5 py-0.5 rounded text-xs font-medium
            ${doc.status === "APPROVED" ? "bg-green-100 text-green-700" :
              doc.status === "REJECTED" ? "bg-red-100 text-red-700" :
              doc.status === "REUPLOAD_REQUESTED" ? "bg-amber-100 text-amber-700" :
              "bg-blue-100 text-blue-700"}`}>
            {doc.status}
          </span>
        </div>

        {doc.aiConfidence && (
          <div className="flex items-center gap-2 text-xs">
            <span className="text-gray-500">AI Confidence:</span>
            <span className={confidenceColor[doc.aiConfidence]}>{doc.aiConfidence}</span>
            {doc.aiOutcome && <span className="text-gray-400">→ {doc.aiOutcome}</span>}
          </div>
        )}

        <p className="text-xs text-gray-400 mt-1">
          Uploaded {new Date(doc.uploadedAt).toLocaleDateString()}
        </p>

        {doc.status !== "APPROVED" && doc.status !== "REUPLOAD_REQUESTED" && (
          <button
            onClick={() => onReject(doc.documentType)}
            className="mt-2 text-xs text-red-600 hover:underline"
          >
            Reject &amp; Request Re-upload
          </button>
        )}
      </div>
    </div>
  );
}

// ── Review detail panel ───────────────────────────────────────────────────────

function ReviewPanel({
  review,
  onClose,
}: {
  review: KycReview;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const [rejectDocTarget, setRejectDocTarget] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState("");

  const approveMut = useMutation({
    mutationFn: () => api.approveAll(review.reviewId),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["kyc-reviews"] }); onClose(); },
  });

  const approveEddMut = useMutation({
    mutationFn: () => api.approveWithEdd(review.reviewId),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["kyc-reviews"] }); onClose(); },
  });

  const rejectDocMut = useMutation({
    mutationFn: () => api.rejectDocument(review.reviewId, rejectDocTarget!, rejectReason),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["kyc-reviews"] });
      setRejectDocTarget(null);
      setRejectReason("");
    },
  });

  return (
    <div className="fixed inset-y-0 right-0 w-[580px] bg-white shadow-2xl overflow-y-auto z-40">
      <div className="sticky top-0 bg-white border-b px-5 py-4 flex items-center justify-between">
        <div>
          <p className="font-semibold">{review.clientName}</p>
          <p className="text-xs text-gray-500">{review.clientType} · {review.clientId}</p>
        </div>
        <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl">✕</button>
      </div>

      <div className="p-5 space-y-5">
        {/* Info bar */}
        <div className="flex items-center gap-3 flex-wrap">
          <span className={`px-2 py-0.5 rounded text-xs font-medium ${REASON_COLOR[review.reviewReason]}`}>
            {REASON_LABEL[review.reviewReason]}
          </span>
          <span className={`px-2 py-0.5 rounded text-xs font-medium ${REVIEW_STATUS_COLOR[review.status]}`}>
            {review.status}
          </span>
          <SlaCountdown deadline={review.slaDeadline} />
        </div>

        {/* Sanctions result */}
        {review.sanctionsResult && (
          <div className={`p-3 rounded border ${review.sanctionsResult.hit ? "bg-red-50 border-red-300" : "bg-green-50 border-green-200"}`}>
            <p className="text-sm font-semibold mb-1">Sanctions Screening</p>
            {review.sanctionsResult.hit ? (
              <div className="text-sm text-red-700">
                <p className="font-medium">⚠ HIT — {review.sanctionsResult.hitList}</p>
                {review.sanctionsResult.matchScore && (
                  <p className="text-xs">Match score: {review.sanctionsResult.matchScore}%</p>
                )}
              </div>
            ) : (
              <p className="text-sm text-green-700">✓ No sanctions match found</p>
            )}
          </div>
        )}

        {/* Documents grid */}
        <div>
          <p className="text-sm font-semibold mb-3">Documents</p>
          <div className="grid grid-cols-2 gap-3">
            {review.documents.map((doc) => (
              <DocumentCard
                key={doc.documentType}
                doc={doc}
                reviewId={review.reviewId}
                onReject={(docType) => setRejectDocTarget(docType)}
              />
            ))}
          </div>
        </div>

        {/* Actions */}
        {review.status !== "APPROVED" && review.status !== "APPROVED_WITH_EDD" && (
          <div className="border-t pt-4 flex gap-3">
            <button
              onClick={() => approveMut.mutate()}
              disabled={approveMut.isPending}
              className="flex-1 px-4 py-2 text-sm bg-green-600 text-white rounded hover:bg-green-700 disabled:opacity-50"
            >
              ✓ Approve All
            </button>
            <button
              onClick={() => approveEddMut.mutate()}
              disabled={approveEddMut.isPending}
              className="flex-1 px-4 py-2 text-sm bg-teal-600 text-white rounded hover:bg-teal-700 disabled:opacity-50"
            >
              Approve with EDD
            </button>
          </div>
        )}
      </div>

      {/* Reject document modal */}
      {rejectDocTarget && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h2 className="text-lg font-semibold mb-2">Reject Document: {rejectDocTarget.replace(/_/g, " ")}</h2>
            <p className="text-sm text-gray-500 mb-3">
              The client will be notified to re-upload this document.
            </p>
            <label className="block text-sm font-medium text-gray-700 mb-1">Rejection Reason</label>
            <textarea
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              rows={3}
              placeholder="e.g. Document is expired, photo is blurry, wrong document type…"
              className="w-full border rounded px-3 py-2 text-sm mb-4"
            />
            <div className="flex justify-end gap-2">
              <button onClick={() => setRejectDocTarget(null)} className="px-4 py-2 text-sm border rounded text-gray-600 hover:bg-gray-50">
                Cancel
              </button>
              <button
                onClick={() => rejectDocMut.mutate()}
                disabled={!rejectReason.trim() || rejectDocMut.isPending}
                className="px-4 py-2 text-sm bg-red-600 text-white rounded hover:bg-red-700 disabled:opacity-50"
              >
                {rejectDocMut.isPending ? "Rejecting…" : "Reject & Request Re-upload"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

type StatusTab = "PENDING" | "IN_REVIEW" | "COMPLETED";

export default function KycReviewPortalPage() {
  const [statusTab, setStatusTab] = useState<StatusTab>("PENDING");
  const [selectedReview, setSelectedReview] = useState<KycReview | null>(null);

  const { data: reviews = [], isLoading } = useQuery({
    queryKey: ["kyc-reviews", statusTab],
    queryFn: () => api.listReviews(statusTab),
    refetchInterval: 15000,
  });

  const tabs: Array<{ id: StatusTab; label: string }> = [
    { id: "PENDING", label: "Pending" },
    { id: "IN_REVIEW", label: "In Review" },
    { id: "COMPLETED", label: "Completed" },
  ];

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">KYC Review Portal</h1>
        <p className="text-sm text-gray-500">
          Compliance officer review queue for document verification, sanctions, and PEP matches. SLA: 2 business days.
        </p>
      </div>

      {/* Status tabs */}
      <div className="flex gap-1 border-b mb-6">
        {tabs.map((t) => (
          <button
            key={t.id}
            onClick={() => setStatusTab(t.id)}
            className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px
              ${statusTab === t.id ? "border-blue-600 text-blue-700" : "border-transparent text-gray-500 hover:text-gray-700"}`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <p className="text-sm text-gray-500">Loading reviews…</p>
      ) : reviews.length === 0 ? (
        <div className="text-center py-12 text-gray-400">
          <p className="text-4xl mb-2">✓</p>
          <p className="text-sm">No {statusTab.toLowerCase().replace("_", " ")} reviews.</p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                {["Client", "Type", "Review Reason", "Status", "Submitted", "SLA", "Documents", "Actions"].map((h) => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {reviews.map((review) => {
                const overdue = new Date(review.slaDeadline) < new Date();
                const hasSanctionsHit = review.sanctionsResult?.hit;
                return (
                  <tr
                    key={review.reviewId}
                    className={`hover:bg-gray-50 cursor-pointer
                      ${hasSanctionsHit ? "bg-red-50" : overdue ? "bg-amber-50" : ""}`}
                    onClick={() => setSelectedReview(review)}
                  >
                    <td className="px-4 py-3">
                      <p className="font-medium">{review.clientName}</p>
                      <p className="text-xs text-gray-400">{review.clientId}</p>
                    </td>
                    <td className="px-4 py-3 text-gray-500">{review.clientType}</td>
                    <td className="px-4 py-3">
                      <span className={`px-2 py-0.5 rounded text-xs font-medium ${REASON_COLOR[review.reviewReason]}`}>
                        {REASON_LABEL[review.reviewReason]}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`px-2 py-0.5 rounded text-xs font-medium ${REVIEW_STATUS_COLOR[review.status]}`}>
                        {review.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500">{new Date(review.submittedAt).toLocaleDateString()}</td>
                    <td className="px-4 py-3"><SlaCountdown deadline={review.slaDeadline} /></td>
                    <td className="px-4 py-3 text-xs text-gray-500">{review.documents.length}</td>
                    <td className="px-4 py-3">
                      <button
                        onClick={(e) => { e.stopPropagation(); setSelectedReview(review); }}
                        className="text-xs text-blue-600 hover:underline"
                      >
                        Review
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {selectedReview && (
        <ReviewPanel review={selectedReview} onClose={() => setSelectedReview(null)} />
      )}
    </div>
  );
}
