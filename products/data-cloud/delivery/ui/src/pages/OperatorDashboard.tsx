/**
 * Operator Dashboard
 *
 * Consolidated operator workspace with first-class diagnostics.
 * Features: alerts triage, event stream, system diagnostics, plugin health.
 *
 * @doc.type page
 * @doc.purpose Operator-specific workspace with diagnostics consolidation
 * @doc.layer frontend
 */

import {
  Activity,
  AlertTriangle,
  ArrowRight,
  Layers,
  Scale,
  ShieldCheck,
  Wrench,
  Zap,
} from "lucide-react";
import React from "react";
import { useNavigate } from "react-router";
import { cardStyles, cn, textStyles } from "../lib/theme";

interface OperatorCard {
  id: string;
  title: string;
  description: string;
  icon: React.ReactElement;
  path: string;
  count?: number;
}

/**
 * Operator Dashboard Page
 */
export function OperatorDashboard(): React.ReactElement {
  const navigate = useNavigate();

  const cards: OperatorCard[] = [
    {
      id: "alerts",
      title: "Alert Triage",
      description: "Review, acknowledge, and resolve active incidents.",
      icon: <AlertTriangle className="h-6 w-6 text-red-500" />,
      path: "/alerts",
      count: undefined,
    },
    {
      id: "events",
      title: "Event Stream",
      description: "Live-tail and inspect events across the fabric.",
      icon: <Activity className="h-6 w-6 text-blue-500" />,
      path: "/events",
      count: undefined,
    },
    {
      id: "insights",
      title: "System Diagnostics",
      description: "Runtime health, operator diagnostics, and cost.",
      icon: <Zap className="h-6 w-6 text-amber-500" />,
      path: "/insights",
      count: undefined,
    },
    {
      id: "operations",
      title: "Operations Console",
      description: "Real admin diagnostics and runtime inspection.",
      icon: <Wrench className="h-6 w-6 text-purple-500" />,
      path: "/operations",
      count: undefined,
    },
    // DC-P3-002: Runtime Truth plane/surface/dependency drilldown
    {
      id: "runtime-truth",
      title: "Runtime Truth",
      description: "Plane and surface health with dependency drilldown.",
      icon: <Layers className="h-6 w-6 text-teal-500" />,
      path: "/operations/runtime-truth",
      count: undefined,
    },
    {
      id: "data-quality-trust",
      title: "Data Quality Trust",
      description:
        "Canonical Data Plane quality and trust score contract view.",
      icon: <ShieldCheck className="h-6 w-6 text-emerald-500" />,
      path: "/insights/data-quality-trust",
      count: undefined,
    },
    {
      id: "policy-simulation",
      title: "Policy Simulation",
      description: "Dry-run governance policy changes before mutation.",
      icon: <Scale className="h-6 w-6 text-indigo-500" />,
      path: "/trust/simulation",
      count: undefined,
    },
    {
      id: "tenant-governance",
      title: "Tenant Governance",
      description: "Tenant-level cost and resource governance snapshot.",
      icon: <ShieldCheck className="h-6 w-6 text-cyan-500" />,
      path: "/insights/tenant-governance",
      count: undefined,
    },
  ];

  return (
    <div
      className="min-h-screen bg-gray-50 dark:bg-gray-900 p-6"
      data-testid="operator-dashboard"
    >
      <div className="mb-8">
        <h1 className={textStyles.h1}>Operator Workspace</h1>
        <p className={textStyles.muted}>
          All operator diagnostics in one place - alerts, events, runtime
          health, and operations.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 mb-8 md:grid-cols-2 xl:grid-cols-4">
        {cards.map((card) => (
          <button
            key={card.id}
            onClick={() => {
              navigate(card.path);
            }}
            className={cn(
              cardStyles.base,
              cardStyles.padded,
              "text-left transition-all hover:border-primary-300 dark:hover:border-primary-700",
            )}
            data-testid={`operator-card-${card.id}`}
          >
            <div className="flex items-start justify-between mb-3">
              <div className="p-2 bg-gray-100 rounded-lg dark:bg-gray-700">
                {card.icon}
              </div>
              <ArrowRight className="w-4 h-4 text-gray-400" />
            </div>
            <h3 className="font-semibold text-gray-900 dark:text-gray-100">
              {card.title}
            </h3>
            <p className="mt-1 text-sm text-gray-500">{card.description}</p>
          </button>
        ))}
      </div>

      <div
        className={cn(cardStyles.base, cardStyles.padded)}
        data-testid="operator-context-note"
      >
        <h3 className={textStyles.h4}>Context</h3>
        <p className={textStyles.muted}>
          This workspace consolidates operator-facing surfaces. If you need
          deeper controls (settings, user management), switch to the admin
          workspace via the role selector.
        </p>
      </div>
    </div>
  );
}

export default OperatorDashboard;
