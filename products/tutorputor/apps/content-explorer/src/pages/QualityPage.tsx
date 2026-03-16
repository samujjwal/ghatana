import { useState } from "react";
import { Link } from "react-router-dom";
import { Star, CheckCircle2, XCircle, RefreshCw, Loader2, ChevronRight } from "lucide-react";
import type { ContentItemWithReport, QualityReport } from "@/types/content";
import {
  usePendingReview,
  useApproveContent,
  useRejectContent,
} from "@/hooks/useContent";

function QualityDimBar({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex items-center gap-2">
      <span className="w-32 shrink-0 text-xs text-muted-foreground">{label}</span>
      <div className="flex-1 overflow-hidden rounded-full bg-muted" style={{ height: 6 }}>
        <div
          className="h-full rounded-full bg-primary transition-all"
          style={{ width: `${value * 100}%` }}
        />
      </div>
      <span className="w-10 text-right text-xs font-medium">{Math.round(value * 100)}%</span>
    </div>
  );
}

function RejectModal({
  onConfirm,
  onCancel,
}: {
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}) {
  const [reason, setReason] = useState("");
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
      <div className="w-full max-w-md rounded-xl border border-border bg-card p-6 shadow-xl">
        <h2 className="mb-2 text-base font-semibold">Reject Content</h2>
        <p className="mb-4 text-sm text-muted-foreground">
          Please provide a reason so authors can improve the content.
        </p>
        <textarea
          autoFocus
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="Explain why this content is being rejected…"
          rows={4}
          className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring"
        />
        <div className="mt-4 flex justify-end gap-3">
          <button
            onClick={onCancel}
            className="rounded-md border border-border px-4 py-2 text-sm hover:bg-muted"
          >
            Cancel
          </button>
          <button
            onClick={() => reason.trim() && onConfirm(reason.trim())}
            disabled={!reason.trim()}
            className="rounded-md bg-destructive px-4 py-2 text-sm text-destructive-foreground disabled:opacity-50"
          >
            Reject
          </button>
        </div>
      </div>
    </div>
  );
}

function ReviewCard({ item }: { item: ContentItemWithReport }) {
  const { mutate: approve, isPending: approving } = useApproveContent();
  const { mutate: reject, isPending: rejecting } = useRejectContent();
  const [showRejectModal, setShowRejectModal] = useState(false);
  const qr = item.qualityReport;

  return (
    <>
      <div className="rounded-lg border border-border bg-card p-4 transition-shadow hover:shadow-md">
        <div className="mb-3 flex items-start justify-between">
          <div>
            <div className="flex items-center gap-2">
              <h3 className="font-medium">{item.title}</h3>
              {qr && (
                <span className="flex items-center gap-0.5 rounded-full bg-yellow-50 px-2 py-0.5 text-xs font-medium text-yellow-700">
                  <Star className="h-3 w-3" aria-hidden />
                  {Math.round(qr.overallScore * 100)}
                </span>
              )}
            </div>
            <p className="mt-0.5 text-xs text-muted-foreground">
              {item.type} · {item.subject} · {item.gradeLevel}
            </p>
          </div>
          <Link
            to={`/view/${item.id}`}
            className="flex items-center gap-1 text-xs text-primary hover:underline"
          >
            View <ChevronRight className="h-3.5 w-3.5" aria-hidden />
          </Link>
        </div>

        {qr && (
          <div className="mb-4 space-y-1.5">
            <QualityDimBar label="Accuracy" value={qr.dimensions.accuracy} />
            <QualityDimBar label="Clarity" value={qr.dimensions.clarity} />
            <QualityDimBar label="Engagement" value={qr.dimensions.engagement} />
            <QualityDimBar label="Grade Appropriate" value={qr.dimensions.gradeAppropriateness} />
            <QualityDimBar label="Curriculum Align" value={qr.dimensions.curriculumAlignment} />
          </div>
        )}

        <div className="flex justify-end gap-2">
          <button
            onClick={() => setShowRejectModal(true)}
            disabled={approving || rejecting}
            className="flex items-center gap-1.5 rounded-md border border-destructive/50 px-3 py-1.5 text-xs text-destructive hover:bg-destructive/10 disabled:opacity-50"
          >
            {rejecting ? <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden /> : <XCircle className="h-3.5 w-3.5" aria-hidden />}
            Reject
          </button>
          <button
            onClick={() => approve({ id: item.id })}
            disabled={approving || rejecting}
            className="flex items-center gap-1.5 rounded-md bg-green-600 px-3 py-1.5 text-xs text-white hover:bg-green-700 disabled:opacity-50"
          >
            {approving ? <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden /> : <CheckCircle2 className="h-3.5 w-3.5" aria-hidden />}
            Approve
          </button>
        </div>
      </div>

      {showRejectModal && (
        <RejectModal
          onConfirm={(reason) => {
            reject({ id: item.id, reason });
            setShowRejectModal(false);
          }}
          onCancel={() => setShowRejectModal(false)}
        />
      )}
    </>
  );
}

const TABS = ["Review Queue", "Quality Overview"] as const;
type Tab = (typeof TABS)[number];

export function QualityPage() {
  const [activeTab, setActiveTab] = useState<Tab>("Review Queue");
  const { data: pending, isLoading, refetch, isFetching } = usePendingReview();

  return (
    <div className="flex flex-1 flex-col overflow-hidden p-6">
      {/* Header */}
      <div className="mb-6 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Star className="h-5 w-5 text-yellow-500" aria-hidden />
          <h1 className="text-lg font-semibold">Quality Center</h1>
          {pending && (
            <span className="rounded-full bg-primary px-2 py-0.5 text-xs text-primary-foreground">
              {pending.length} pending
            </span>
          )}
        </div>
        <button
          onClick={() => refetch()}
          disabled={isFetching}
          className="flex items-center gap-1.5 rounded-md border border-border px-3 py-1.5 text-xs hover:bg-muted disabled:opacity-50"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${isFetching ? "animate-spin" : ""}`} aria-hidden />
          Refresh
        </button>
      </div>

      {/* Tabs */}
      <div className="mb-4 flex gap-0 rounded-lg border border-border bg-muted p-1">
        {TABS.map((tab) => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className={`flex-1 rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
              activeTab === tab
                ? "bg-card text-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            {tab}
          </button>
        ))}
      </div>

      {/* Tab content */}
      <div className="flex-1 overflow-y-auto">
        {activeTab === "Review Queue" && (
          <>
            {isLoading ? (
              <div className="flex h-40 items-center justify-center">
                <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
              </div>
            ) : !pending?.length ? (
              <div className="flex h-40 flex-col items-center justify-center gap-2">
                <CheckCircle2 className="h-8 w-8 text-green-500" aria-hidden />
                <p className="text-sm text-muted-foreground">All caught up! No items pending review.</p>
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-4 lg:grid-cols-2 xl:grid-cols-3">
                {pending.map((item) => (
                  <ReviewCard key={item.id} item={item} />
                ))}
              </div>
            )}
          </>
        )}

        {activeTab === "Quality Overview" && (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {[
              { label: "Pending Review", value: pending?.length ?? "—", color: "text-orange-500" },
              {
                label: "Avg Quality Score",
                value: pending?.length
                  ? `${Math.round(
                      (pending.filter((p) => p.qualityReport)
                        .reduce((sum: number, p) => sum + (p.qualityReport?.overallScore ?? 0), 0) /
                        (pending.filter((p) => p.qualityReport).length || 1)) * 100,
                    )}`
                  : "—",
                color: "text-primary",
              },
            ].map(({ label, value, color }) => (
              <div key={label} className="rounded-lg border border-border bg-card p-4">
                <p className="text-xs text-muted-foreground">{label}</p>
                <p className={`mt-1 text-3xl font-bold ${color}`}>{value}</p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
