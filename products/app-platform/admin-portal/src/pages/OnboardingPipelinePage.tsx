/**
 * STORY-W02-011: Onboarding Pipeline Dashboard
 * Kanban-style pipeline view showing clients in each onboarding stage.
 * Stats: average time per stage, completion rate, rejection rate, SLA compliance.
 * Filter by risk tier, client type, date. Drilldown to individual KYC instance.
 */

import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";

// ── Types ─────────────────────────────────────────────────────────────────────

type OnboardingStage =
  | "DOCUMENT_COLLECTION"
  | "UNDER_REVIEW"
  | "EDD"
  | "ACCOUNT_SETUP"
  | "COMPLETED"
  | "REJECTED";

type RiskTier = "LOW" | "MEDIUM" | "HIGH";
type ClientType = "INDIVIDUAL" | "INSTITUTIONAL";

interface ClientCard {
  instanceId: string;
  clientId: string;
  clientName: string;
  clientType: ClientType;
  riskTier: RiskTier;
  stage: OnboardingStage;
  stageEnteredAt: string;
  slaDeadline?: string;
  primaryRejectionReason?: string;
}

interface StageStats {
  stage: OnboardingStage;
  count: number;
  avgDaysInStage: number;
  slaCompliancePercent: number;
}

interface PipelineStats {
  totalActive: number;
  completionRate: number;
  rejectionRate: number;
  overallSlaCompliance: number;
  avgOnboardingDays: number;
  stageStats: StageStats[];
}

// ── API ───────────────────────────────────────────────────────────────────────

const api = {
  getCards: async (filters: Record<string, string>): Promise<ClientCard[]> => {
    const q = new URLSearchParams(filters).toString();
    const r = await fetch(`/api/onboarding/pipeline/cards?${q}`);
    if (!r.ok) throw new Error("Failed to fetch pipeline cards");
    return r.json();
  },
  getStats: async (filters: Record<string, string>): Promise<PipelineStats> => {
    const q = new URLSearchParams(filters).toString();
    const r = await fetch(`/api/onboarding/pipeline/stats?${q}`);
    if (!r.ok) throw new Error("Failed to fetch pipeline stats");
    return r.json();
  },
};

// ── Constants ─────────────────────────────────────────────────────────────────

const STAGES: OnboardingStage[] = [
  "DOCUMENT_COLLECTION",
  "UNDER_REVIEW",
  "EDD",
  "ACCOUNT_SETUP",
  "COMPLETED",
  "REJECTED",
];

const STAGE_LABEL: Record<OnboardingStage, string> = {
  DOCUMENT_COLLECTION: "Document Collection",
  UNDER_REVIEW: "Under Review",
  EDD: "Enhanced Due Diligence",
  ACCOUNT_SETUP: "Account Setup",
  COMPLETED: "Completed",
  REJECTED: "Rejected",
};

const STAGE_COLOR: Record<OnboardingStage, string> = {
  DOCUMENT_COLLECTION: "border-gray-300 bg-gray-50",
  UNDER_REVIEW: "border-blue-300 bg-blue-50",
  EDD: "border-orange-300 bg-orange-50",
  ACCOUNT_SETUP: "border-teal-300 bg-teal-50",
  COMPLETED: "border-green-300 bg-green-50",
  REJECTED: "border-red-300 bg-red-50",
};

const STAGE_HEADER_COLOR: Record<OnboardingStage, string> = {
  DOCUMENT_COLLECTION: "bg-gray-100 text-gray-700",
  UNDER_REVIEW: "bg-blue-100 text-blue-700",
  EDD: "bg-orange-100 text-orange-700",
  ACCOUNT_SETUP: "bg-teal-100 text-teal-700",
  COMPLETED: "bg-green-100 text-green-700",
  REJECTED: "bg-red-100 text-red-700",
};

const RISK_COLOR: Record<RiskTier, string> = {
  LOW: "bg-green-100 text-green-700",
  MEDIUM: "bg-amber-100 text-amber-700",
  HIGH: "bg-red-100 text-red-700",
};

// ── Helpers ───────────────────────────────────────────────────────────────────

function daysSince(isoDate: string): number {
  return Math.floor((Date.now() - new Date(isoDate).getTime()) / 86400000);
}

function slaColor(deadline: string | undefined): string {
  if (!deadline) return "";
  const diff = new Date(deadline).getTime() - Date.now();
  if (diff < 0) return "border-l-4 border-l-red-500";
  if (diff < 86400000) return "border-l-4 border-l-amber-500";
  return "";
}

// ── Stats bar ─────────────────────────────────────────────────────────────────

function PipelineStatsBar({ stats }: { stats: PipelineStats }) {
  return (
    <div className="grid grid-cols-5 gap-3 mb-6">
      {[
        { label: "Active Clients", value: stats.totalActive.toString() },
        { label: "Completion Rate", value: `${stats.completionRate.toFixed(1)}%` },
        { label: "Rejection Rate", value: `${stats.rejectionRate.toFixed(1)}%` },
        { label: "SLA Compliance", value: `${stats.overallSlaCompliance.toFixed(1)}%` },
        { label: "Avg. Onboarding Days", value: `${stats.avgOnboardingDays.toFixed(1)}d` },
      ].map((s) => (
        <div key={s.label} className="bg-white border rounded-lg p-4">
          <p className="text-2xl font-bold text-gray-900">{s.value}</p>
          <p className="text-xs text-gray-500 mt-0.5">{s.label}</p>
        </div>
      ))}
    </div>
  );
}

// ── Client card ───────────────────────────────────────────────────────────────

function OnboardingClientCard({ card, onClick }: { card: ClientCard; onClick: () => void }) {
  return (
    <div
      onClick={onClick}
      className={`bg-white border rounded-lg p-3 cursor-pointer hover:shadow-sm transition-shadow text-sm ${slaColor(card.slaDeadline)}`}
    >
      <div className="flex items-start justify-between mb-1">
        <div>
          <p className="font-medium truncate max-w-[140px]">{card.clientName}</p>
          <p className="text-xs text-gray-400">{card.clientType}</p>
        </div>
        <span className={`px-1.5 py-0.5 rounded text-xs font-medium flex-shrink-0 ${RISK_COLOR[card.riskTier]}`}>
          {card.riskTier}
        </span>
      </div>
      <div className="flex items-center justify-between text-xs text-gray-400 mt-2">
        <span>{daysSince(card.stageEnteredAt)}d in stage</span>
        {card.slaDeadline && new Date(card.slaDeadline) < new Date() && (
          <span className="text-red-600 font-medium">OVERDUE</span>
        )}
      </div>
      {card.primaryRejectionReason && (
        <p className="text-xs text-red-600 mt-1 truncate">{card.primaryRejectionReason}</p>
      )}
    </div>
  );
}

// ── Stage column ──────────────────────────────────────────────────────────────

function StageColumn({
  stage,
  cards,
  stageStats,
  onCardClick,
}: {
  stage: OnboardingStage;
  cards: ClientCard[];
  stageStats: StageStats | undefined;
  onCardClick: (card: ClientCard) => void;
}) {
  return (
    <div className={`flex flex-col border-2 rounded-xl overflow-hidden ${STAGE_COLOR[stage]} w-56 flex-shrink-0`}>
      {/* Header */}
      <div className={`p-3 ${STAGE_HEADER_COLOR[stage]}`}>
        <div className="flex items-center justify-between mb-0.5">
          <p className="text-xs font-semibold">{STAGE_LABEL[stage]}</p>
          <span className="text-xs font-bold">{cards.length}</span>
        </div>
        {stageStats && (
          <div className="flex items-center gap-2 text-xs opacity-70">
            <span>Avg: {stageStats.avgDaysInStage.toFixed(1)}d</span>
            <span>SLA: {stageStats.slaCompliancePercent.toFixed(0)}%</span>
          </div>
        )}
      </div>

      {/* Cards */}
      <div className="flex-1 p-2 flex flex-col gap-2 max-h-[calc(100vh-280px)] overflow-y-auto">
        {cards.map((card) => (
          <OnboardingClientCard key={card.instanceId} card={card} onClick={() => onCardClick(card)} />
        ))}
        {cards.length === 0 && (
          <p className="text-xs text-gray-400 text-center py-4">No clients</p>
        )}
      </div>
    </div>
  );
}

// ── Instance drilldown panel ──────────────────────────────────────────────────

function InstanceDrilldownPanel({ card, onClose }: { card: ClientCard; onClose: () => void }) {
  const navigate = useNavigate();
  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={onClose}>
      <div
        className="bg-white rounded-xl shadow-2xl w-full max-w-sm p-6"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold">{card.clientName}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">✕</button>
        </div>
        <dl className="space-y-2 text-sm mb-5">
          {[
            ["Client ID", card.clientId],
            ["Type", card.clientType],
            ["Risk Tier", card.riskTier],
            ["Stage", STAGE_LABEL[card.stage]],
            ["Days in Current Stage", `${daysSince(card.stageEnteredAt)}d`],
            ["SLA Deadline", card.slaDeadline ? new Date(card.slaDeadline).toLocaleDateString() : "—"],
          ].map(([label, value]) => (
            <div key={label as string} className="flex justify-between">
              <dt className="text-gray-500">{label}</dt>
              <dd className="font-medium">{value}</dd>
            </div>
          ))}
        </dl>
        <button
          onClick={() => navigate(`/onboarding/instances/${card.instanceId}`)}
          className="w-full px-4 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700"
        >
          Open Full KYC Instance →
        </button>
      </div>
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function OnboardingPipelinePage() {
  const [riskTierFilter, setRiskTierFilter] = useState("ALL");
  const [clientTypeFilter, setClientTypeFilter] = useState("ALL");
  const [dateFilter, setDateFilter] = useState("30d");
  const [selectedCard, setSelectedCard] = useState<ClientCard | null>(null);

  const filters: Record<string, string> = {};
  if (riskTierFilter !== "ALL") filters.riskTier = riskTierFilter;
  if (clientTypeFilter !== "ALL") filters.clientType = clientTypeFilter;
  filters.period = dateFilter;

  const { data: cards = [] } = useQuery({
    queryKey: ["onboarding-cards", filters],
    queryFn: () => api.getCards(filters),
    refetchInterval: 30000,
  });

  const { data: stats } = useQuery({
    queryKey: ["onboarding-stats", filters],
    queryFn: () => api.getStats(filters),
    refetchInterval: 60000,
  });

  const cardsByStage = STAGES.reduce<Record<OnboardingStage, ClientCard[]>>(
    (acc, stage) => {
      acc[stage] = cards.filter((c) => c.stage === stage);
      return acc;
    },
    {} as Record<OnboardingStage, ClientCard[]>
  );

  return (
    <div className="p-6">
      <div className="mb-4">
        <h1 className="text-2xl font-bold text-gray-900">Onboarding Pipeline</h1>
        <p className="text-sm text-gray-500">Clients across every stage of the KYC onboarding process</p>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-3 mb-4 flex-wrap">
        <div className="flex items-center gap-1 text-sm">
          <label className="text-gray-500">Risk Tier:</label>
          {["ALL", "LOW", "MEDIUM", "HIGH"].map((t) => (
            <button
              key={t}
              onClick={() => setRiskTierFilter(t)}
              className={`px-2 py-1 rounded text-xs font-medium
                ${riskTierFilter === t ? "bg-blue-600 text-white" : "border text-gray-600 hover:bg-gray-50"}`}
            >
              {t}
            </button>
          ))}
        </div>

        <div className="flex items-center gap-1 text-sm">
          <label className="text-gray-500">Client Type:</label>
          {["ALL", "INDIVIDUAL", "INSTITUTIONAL"].map((t) => (
            <button
              key={t}
              onClick={() => setClientTypeFilter(t)}
              className={`px-2 py-1 rounded text-xs font-medium
                ${clientTypeFilter === t ? "bg-blue-600 text-white" : "border text-gray-600 hover:bg-gray-50"}`}
            >
              {t === "ALL" ? "All" : t === "INDIVIDUAL" ? "Individual" : "Institutional"}
            </button>
          ))}
        </div>

        <div className="flex items-center gap-1 text-sm">
          <label className="text-gray-500">Period:</label>
          {["7d", "30d", "90d"].map((p) => (
            <button
              key={p}
              onClick={() => setDateFilter(p)}
              className={`px-2 py-1 rounded text-xs font-medium
                ${dateFilter === p ? "bg-blue-600 text-white" : "border text-gray-600 hover:bg-gray-50"}`}
            >
              {p}
            </button>
          ))}
        </div>
      </div>

      {/* Stats */}
      {stats && <PipelineStatsBar stats={stats} />}

      {/* Kanban board */}
      <div className="flex gap-3 overflow-x-auto pb-4">
        {STAGES.map((stage) => (
          <StageColumn
            key={stage}
            stage={stage}
            cards={cardsByStage[stage]}
            stageStats={stats?.stageStats.find((s) => s.stage === stage)}
            onCardClick={(card) => setSelectedCard(card)}
          />
        ))}
      </div>

      {selectedCard && (
        <InstanceDrilldownPanel card={selectedCard} onClose={() => setSelectedCard(null)} />
      )}
    </div>
  );
}
